package com.eter.salud.presentation.citas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eter.salud.domain.model.DatosContactoCita
import com.eter.salud.domain.model.Especialidad
import com.eter.salud.domain.model.FranjaAgenda
import com.eter.salud.domain.model.MedicoVinculado
import com.eter.salud.domain.model.MotivoFalloCita
import com.eter.salud.domain.model.PerfilDoctorDirectorio
import com.eter.salud.domain.repository.CitasRepositorio
import com.eter.salud.domain.repository.DirectorioMedicoRepositorio
import com.eter.salud.domain.repository.FalloCita
import com.eter.salud.domain.time.RelojSalud
import com.eter.salud.domain.time.relojDelSistema
import com.eter.salud.presentation.comun.ejecutarSeguro
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Flujo conversacional de agendamiento, montado sobre el chat del paciente.
 *
 * Es un ViewModel aparte y no un anadido a `ChatViewModel` por una razon de
 * responsabilidad: el chat transporta mensajes y el agendamiento es una maquina
 * de estados con reglas de negocio propias (retencion de la franja, validacion
 * de contacto, confirmacion). Fundirlos daria una clase que cambia por dos
 * motivos distintos, y una conversacion que no se puede probar sin agenda.
 *
 * Depende de dos contratos:
 *  - [DirectorioMedicoRepositorio] para el catalogo de doctores, que ya existe.
 *    Duplicarlo en [CitasRepositorio] permitiria que el chat ofreciera doctores
 *    que el directorio no lista.
 *  - [CitasRepositorio] para disponibilidad, retencion y alta de la cita.
 *
 * La franja se retiene en cuanto el paciente la elige, ANTES de capturar sus
 * datos, que es justo la ventana en la que dos pacientes podrian pisarse. Si
 * abandona la conversacion sin cancelar, la retencion caduca sola en el backend:
 * por eso no hace falta soltarla desde `onCleared`, donde el `viewModelScope` ya
 * esta cancelado y ninguna llamada de red llegaria a salir.
 */
class AgendaCitaViewModel(
    private val citas: CitasRepositorio,
    private val directorio: DirectorioMedicoRepositorio,
    private val idPaciente: String,
    private val medicoVinculado: MedicoVinculado? = null,
    private val reloj: RelojSalud = relojDelSistema(),
) : ViewModel() {

    private val _estado = MutableStateFlow(AgendaCitaUiState())
    val estado: StateFlow<AgendaCitaUiState> = _estado.asStateFlow()

    /** El atajo "con mi medico de siempre" solo se ofrece si de verdad hay uno. */
    val tieneMedicoVinculado: Boolean get() = medicoVinculado != null

    val nombreMedicoVinculado: String get() = medicoVinculado?.nombreCompleto.orEmpty()

    // ------------------------------------------------------------------ Apertura

    /**
     * Abre el flujo si el mensaje que el paciente acaba de enviar pide una cita.
     *
     * Se llama con el texto YA enviado al chat, no en su lugar: el paciente debe
     * ver su propia frase en la conversacion. El panel de agendamiento aparece
     * despues, como respuesta.
     */
    fun evaluarMensaje(texto: String) {
        if (_estado.value.activo) return
        if (DetectorIntencionCita.quiereAgendar(texto)) iniciar()
    }

    /** Apertura explicita, desde el boton de accion rapida del chat. */
    fun iniciar() {
        if (_estado.value.activo) return
        _estado.value = AgendaCitaUiState(paso = PasoAgenda.ESPECIALIDAD)
    }

    // ------------------------------------------------- Especialidad y medico

    fun elegirEspecialidad(especialidad: Especialidad) {
        _estado.update {
            it.copy(especialidad = especialidad, cargando = true, error = null, medicos = emptyList())
        }
        viewModelScope.launch {
            val resultado = ejecutarSeguro { directorio.buscarDirectorio(especialidad) }
            resultado.fold(
                onSuccess = { encontrados ->
                    _estado.update {
                        it.copy(cargando = false, medicos = encontrados, paso = PasoAgenda.MEDICO)
                    }
                },
                onFailure = { fallo ->
                    _estado.update { it.copy(cargando = false, error = fallo.aErrorDeAgenda()) }
                },
            )
        }
    }

    /** Atajo que salta la eleccion de especialidad y de doctor. */
    fun usarMedicoVinculado() {
        val vinculado = medicoVinculado ?: return
        elegirMedico(
            MedicoElegido(
                idMedico = vinculado.idMedico,
                nombreCompleto = vinculado.nombreCompleto,
                especialidad = vinculado.especialidad,
            ),
        )
    }

    fun elegirMedicoDelDirectorio(perfil: PerfilDoctorDirectorio) {
        elegirMedico(
            MedicoElegido(
                idMedico = perfil.idMedico,
                nombreCompleto = perfil.nombreCompleto,
                especialidad = perfil.especialidad,
            ),
        )
    }

    private fun elegirMedico(elegido: MedicoElegido) {
        _estado.update { it.copy(medico = elegido, especialidad = elegido.especialidad) }
        cargarFranjas()
    }

    // -------------------------------------------------------------- Horarios

    fun cargarFranjas() {
        val idMedico = _estado.value.medico?.idMedico ?: return
        _estado.update { it.copy(cargando = true, error = null, paso = PasoAgenda.FRANJA) }
        viewModelScope.launch {
            val resultado = ejecutarSeguro {
                citas.franjasLibres(
                    idMedico = idMedico,
                    desde = reloj.fechaHoy(),
                    ahora = reloj.instanteActual(),
                )
            }
            resultado.fold(
                onSuccess = { libres ->
                    _estado.update {
                        it.copy(
                            cargando = false,
                            franjas = libres,
                            // Una agenda sin huecos no es un fallo, pero tampoco
                            // una lista vacia muda: el paciente tiene que saber
                            // que no hay nada que elegir.
                            error = if (libres.isEmpty()) ErrorAgendaCita.SIN_HORARIOS else null,
                        )
                    }
                },
                onFailure = { fallo ->
                    _estado.update { it.copy(cargando = false, error = fallo.aErrorDeAgenda()) }
                },
            )
        }
    }

    /**
     * Elige el horario y lo retiene de inmediato. La retencion es lo unico que
     * impide que otro paciente agende esta misma hora mientras se capturan los
     * datos, asi que ocurre antes de pedirlos, no despues.
     */
    fun elegirFranja(franja: FranjaAgenda) {
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            val resultado = ejecutarSeguro {
                citas.reservarTemporalmente(franja.idFranja, reloj.instanteActual())
            }
            resultado.fold(
                onSuccess = { reserva ->
                    _estado.update {
                        it.copy(
                            cargando = false,
                            franja = franja,
                            reserva = reserva,
                            paso = PasoAgenda.DATOS,
                        )
                    }
                },
                onFailure = { fallo ->
                    _estado.update { it.copy(cargando = false, error = fallo.aErrorDeAgenda()) }
                    // Otro se adelanto: se vuelve a pedir la disponibilidad para
                    // que la lista deje de mostrar una hora que ya no existe.
                    if (fallo.motivoDeCita() == MotivoFalloCita.FRANJA_OCUPADA) cargarFranjas()
                },
            )
        }
    }

    // ------------------------------------------------------ Datos de contacto

    fun actualizarNombre(valor: String) = actualizarCampo(ErrorCampoCita.NOMBRE_VACIO) {
        it.copy(nombreCompleto = valor)
    }

    fun actualizarTelefono(valor: String) {
        _estado.update {
            it.copy(
                telefono = valor,
                errores = it.errores - ErrorCampoCita.TELEFONO_VACIO - ErrorCampoCita.TELEFONO_FORMATO,
                error = null,
            )
        }
    }

    fun actualizarCorreo(valor: String) {
        _estado.update {
            it.copy(
                correo = valor,
                errores = it.errores - ErrorCampoCita.CORREO_VACIO - ErrorCampoCita.CORREO_FORMATO,
                error = null,
            )
        }
    }

    fun actualizarMotivo(valor: String) = actualizarCampo(ErrorCampoCita.MOTIVO_VACIO) {
        it.copy(motivo = valor)
    }

    /** Valida y, solo si todo esta bien, pasa al resumen previo a confirmar. */
    fun revisarResumen() {
        val actual = _estado.value
        val errores = ValidadorCita.validar(
            nombreCompleto = actual.nombreCompleto,
            telefono = actual.telefono,
            correo = actual.correo,
            motivo = actual.motivo,
        )
        _estado.update {
            it.copy(
                errores = errores,
                paso = if (errores.isEmpty()) PasoAgenda.RESUMEN else PasoAgenda.DATOS,
            )
        }
    }

    // ---------------------------------------------------------- Confirmacion

    fun confirmar() {
        val actual = _estado.value
        val reserva = actual.reserva ?: return
        _estado.update { it.copy(cargando = true, error = null) }

        viewModelScope.launch {
            val resultado = ejecutarSeguro {
                citas.confirmarCita(
                    idReserva = reserva.idReserva,
                    idPaciente = idPaciente,
                    contacto = DatosContactoCita(
                        nombreCompleto = actual.nombreCompleto.trim(),
                        telefono = ValidadorCita.soloDigitos(actual.telefono),
                        correo = actual.correo.trim(),
                        motivo = actual.motivo.trim(),
                    ),
                    ahora = reloj.instanteActual(),
                )
            }
            resultado.fold(
                onSuccess = { confirmacion ->
                    _estado.update {
                        it.copy(
                            cargando = false,
                            confirmacion = confirmacion,
                            reserva = null,
                            paso = PasoAgenda.CONFIRMADA,
                        )
                    }
                },
                onFailure = { fallo ->
                    // La franja se perdio: se devuelve al paciente a la lista de
                    // horarios con el motivo a la vista, en vez de dejarlo en un
                    // resumen que ya no se puede confirmar.
                    _estado.update {
                        it.copy(
                            cargando = false,
                            error = fallo.aErrorDeAgenda(),
                            franja = null,
                            reserva = null,
                        )
                    }
                    cargarFranjas()
                },
            )
        }
    }

    // ------------------------------------------------------ Salidas del flujo

    /** Cancela y suelta la retencion para que la franja vuelva a estar libre ya. */
    fun cancelar() {
        val reserva = _estado.value.reserva
        _estado.value = AgendaCitaUiState()
        if (reserva != null) {
            viewModelScope.launch { ejecutarSeguro { citas.liberarReserva(reserva.idReserva) } }
        }
    }

    /** Cierra el acuse de la cita agendada y devuelve el chat a la conversacion. */
    fun cerrarConfirmacion() {
        _estado.value = AgendaCitaUiState()
    }

    fun volverAHorarios() {
        _estado.update { it.copy(paso = PasoAgenda.FRANJA, errores = emptyList(), error = null) }
    }

    fun volverADatos() {
        _estado.update { it.copy(paso = PasoAgenda.DATOS, error = null) }
    }

    fun descartarError() {
        _estado.update { it.copy(error = null) }
    }

    // ------------------------------------------------------------- Internos

    private fun actualizarCampo(
        errorALimpiar: ErrorCampoCita,
        cambio: (AgendaCitaUiState) -> AgendaCitaUiState,
    ) {
        _estado.update { cambio(it).copy(errores = it.errores - errorALimpiar, error = null) }
    }

    private fun Throwable.motivoDeCita(): MotivoFalloCita? = (this as? FalloCita)?.motivo

    /**
     * Traduce el fallo del repositorio a la clave que la Vista sabe explicar.
     * Lo que no viene tipado se cuenta como falta de conexion, que es el
     * supuesto honesto cuando no se sabe que paso.
     */
    private fun Throwable.aErrorDeAgenda(): ErrorAgendaCita = when (motivoDeCita()) {
        MotivoFalloCita.RESERVA_EXPIRADA -> ErrorAgendaCita.RESERVA_EXPIRADA
        MotivoFalloCita.FRANJA_OCUPADA -> ErrorAgendaCita.FRANJA_OCUPADA
        MotivoFalloCita.SIN_CONEXION, null -> ErrorAgendaCita.SIN_CONEXION
    }
}
