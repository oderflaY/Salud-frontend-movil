package com.eter.salud.presentation.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eter.salud.domain.model.Cita
import com.eter.salud.domain.model.EstadoCita
import com.eter.salud.domain.model.FranjaAgenda
import com.eter.salud.domain.model.HorarioConsultorio
import com.eter.salud.domain.repository.CitasRepositorio
import com.eter.salud.domain.time.CalendarioSalud
import com.eter.salud.domain.time.RelojSalud
import com.eter.salud.domain.time.relojDelSistema
import com.eter.salud.presentation.comun.ejecutarSeguro
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Calendario de citas del medico.
 *
 * La sincronizacion en tiempo real que pide el requerimiento se resuelve
 * suscribiendose al `Flow` de [CitasRepositorio.agendaDelMedico] en el `init`, no
 * recargando a mano: cuando un paciente confirma una cita desde el chat, la
 * agenda emite y la pantalla se repinta sin que nadie pulse nada. Que hoy la
 * emision venga de memoria y manana de un WebSocket no cambia una linea de aqui.
 *
 * La suscripcion vive en el `init` y no en un `LaunchedEffect` de la Vista por la
 * misma razon que en el directorio: la clave del efecto no cambiaria al
 * recrearse el ViewModel y la agenda se quedaria vacia para siempre.
 *
 * No conoce Compose ni recursos: solo el contrato [CitasRepositorio].
 */
class AgendaMedicoViewModel(
    private val repositorio: CitasRepositorio,
    private val idMedico: String,
    private val reloj: RelojSalud = relojDelSistema(),
) : ViewModel() {

    private val _estado = MutableStateFlow(
        AgendaMedicoUiState(idMedico = idMedico, fechaAncla = reloj.fechaHoy()),
    )
    val estado: StateFlow<AgendaMedicoUiState> = _estado.asStateFlow()

    init {
        observarAgenda()
    }

    private fun observarAgenda() {
        viewModelScope.launch {
            val inicial = ejecutarSeguro { repositorio.cargarAgenda(idMedico) }
            _estado.update { previo ->
                inicial.fold(
                    onSuccess = { citas -> previo.copy(cargando = false, citas = citas, errorCarga = false) },
                    onFailure = { previo.copy(cargando = false, errorCarga = true) },
                )
            }
        }
        viewModelScope.launch {
            // `collect` no vuelve nunca: se queda escuchando mientras el
            // ViewModel viva y se cancela solo con el, sin necesidad de soltarlo
            // a mano en `onCleared`.
            repositorio.agendaDelMedico(idMedico).collect { citas ->
                _estado.update { it.copy(citas = citas, cargando = false, errorCarga = false) }
            }
        }
    }

    // ------------------------------------------------------------ Navegacion

    fun cambiarVista(vista: VistaCalendario) {
        _estado.update { it.copy(vista = vista, inicioDeBloqueo = null) }
    }

    fun irAlPeriodoSiguiente() = desplazar(1)

    fun irAlPeriodoAnterior() = desplazar(-1)

    /**
     * Avanza o retrocede un periodo del tamano de la vista actual: un dia, una
     * semana o un mes. Mover siempre un dia obligaria a pulsar treinta veces
     * para cambiar de mes.
     */
    private fun desplazar(signo: Int) {
        _estado.update { previo ->
            val nueva = when (previo.vista) {
                VistaCalendario.DIARIA -> CalendarioSalud.sumarDias(previo.fechaAncla, signo)
                VistaCalendario.SEMANAL -> CalendarioSalud.sumarDias(previo.fechaAncla, signo * DIAS_DE_LA_SEMANA)
                VistaCalendario.MENSUAL -> saltarUnMes(previo.fechaAncla, signo)
            }
            previo.copy(fechaAncla = nueva, inicioDeBloqueo = null)
        }
    }

    /**
     * Salta al mes contiguo apoyandose en los extremos del mes actual, no
     * sumando treinta dias: con meses de 28 y de 31 dias, una suma fija se
     * saltaria febrero o repetiria el mismo mes.
     */
    private fun saltarUnMes(fecha: String, signo: Int): String {
        val primero = CalendarioSalud.primerDiaDelMes(fecha) ?: return fecha
        return if (signo > 0) {
            val ultimo = CalendarioSalud.ultimoDiaDelMes(fecha) ?: return fecha
            CalendarioSalud.sumarDias(ultimo, 1)
        } else {
            CalendarioSalud.restarDias(primero, 1)
        }
    }

    fun irAHoy() {
        _estado.update { it.copy(fechaAncla = reloj.fechaHoy(), inicioDeBloqueo = null) }
    }

    /** Tocar un dia del mes abre ese dia: la rejilla mensual es un indice, no un fin. */
    fun abrirDia(fecha: String) {
        if (!CalendarioSalud.esFechaValida(fecha)) return
        _estado.update {
            it.copy(fechaAncla = fecha, vista = VistaCalendario.DIARIA, inicioDeBloqueo = null)
        }
    }

    // --------------------------------------------------------------- Detalle

    fun abrirCita(cita: Cita) {
        _estado.update {
            it.copy(citaSeleccionada = cita, errorAccion = false, reprogramando = false)
        }
    }

    fun cerrarDetalle() {
        _estado.update {
            it.copy(
                citaSeleccionada = null,
                reprogramando = false,
                franjasParaReprogramar = emptyList(),
                errorAccion = false,
            )
        }
    }

    fun confirmarCita(cita: Cita) = cambiarEstado(cita, EstadoCita.CONFIRMADA)

    /**
     * Marca la consulta como iniciada. El salto al expediente lo hace la Vista
     * con su propia devolucion de llamada: navegar no es asunto del ViewModel.
     */
    fun iniciarConsulta(cita: Cita) = cambiarEstado(cita, EstadoCita.EN_CURSO)

    fun cancelarCita(cita: Cita) = cambiarEstado(cita, EstadoCita.CANCELADA)

    fun marcarInasistencia(cita: Cita) = cambiarEstado(cita, EstadoCita.NO_ASISTIO)

    private fun cambiarEstado(cita: Cita, nuevo: EstadoCita) {
        _estado.update { it.copy(errorAccion = false) }
        viewModelScope.launch {
            val resultado = ejecutarSeguro { repositorio.cambiarEstado(cita.idCita, nuevo) }
            resultado.fold(
                // La lista no se toca aqui: la reescribe el `Flow` de la agenda.
                // Escribirla ademas a mano crearia dos fuentes de verdad que se
                // pisarian en cuanto una fallara.
                onSuccess = { actualizada ->
                    _estado.update { previo ->
                        previo.copy(
                            citaSeleccionada = if (previo.citaSeleccionada?.idCita == actualizada.idCita) {
                                actualizada
                            } else {
                                previo.citaSeleccionada
                            },
                        )
                    }
                },
                onFailure = { _estado.update { it.copy(errorAccion = true) } },
            )
        }
    }

    // ---------------------------------------------------------- Reprogramar

    fun abrirReprogramacion() {
        if (_estado.value.citaSeleccionada == null) return
        _estado.update { it.copy(reprogramando = true, errorAccion = false) }
        viewModelScope.launch {
            val resultado = ejecutarSeguro {
                repositorio.franjasLibres(
                    idMedico = idMedico,
                    desde = reloj.fechaHoy(),
                    ahora = reloj.instanteActual(),
                )
            }
            resultado.fold(
                onSuccess = { libres -> _estado.update { it.copy(franjasParaReprogramar = libres) } },
                onFailure = { _estado.update { it.copy(errorAccion = true, reprogramando = false) } },
            )
        }
    }

    fun reprogramarEn(franja: FranjaAgenda) {
        val cita = _estado.value.citaSeleccionada ?: return
        viewModelScope.launch {
            val resultado = ejecutarSeguro {
                repositorio.reprogramar(cita.idCita, franja.idFranja, reloj.instanteActual())
            }
            resultado.fold(
                onSuccess = {
                    _estado.update {
                        it.copy(
                            reprogramando = false,
                            franjasParaReprogramar = emptyList(),
                            citaSeleccionada = null,
                        )
                    }
                },
                onFailure = { _estado.update { it.copy(errorAccion = true) } },
            )
        }
    }

    fun cancelarReprogramacion() {
        _estado.update { it.copy(reprogramando = false, franjasParaReprogramar = emptyList()) }
    }

    // ------------------------------------------------------------- Bloqueos

    /**
     * Marca el inicio de un bloqueo. En pantalla tactil el arrastre del
     * calendario de escritorio se sustituye por dos toques: uno abre el rango y
     * el siguiente lo cierra. Arrastrar sobre una lista que scrollea pelearia
     * con el gesto de desplazamiento y produciria bloqueos accidentales, que en
     * una agenda clinica significan citas que nunca se ofrecen.
     */
    fun iniciarBloqueo(horaInicio: String) {
        if (HorarioConsultorio.aMinutos(horaInicio) == null) return
        _estado.update { it.copy(inicioDeBloqueo = horaInicio, errorAccion = false) }
    }

    fun actualizarNotaDeBloqueo(valor: String) {
        _estado.update { it.copy(notaDeBloqueo = valor) }
    }

    fun cancelarBloqueo() {
        _estado.update { it.copy(inicioDeBloqueo = null, notaDeBloqueo = "") }
    }

    /**
     * Cierra el rango y lo bloquea. Acepta los dos toques en cualquier orden:
     * si el segundo es anterior al primero, se intercambian en vez de rechazar
     * el gesto.
     */
    fun completarBloqueo(horaFinal: String) {
        val inicio = _estado.value.inicioDeBloqueo ?: return
        val minutosInicio = HorarioConsultorio.aMinutos(inicio) ?: return
        val minutosFinal = HorarioConsultorio.aMinutos(horaFinal) ?: return

        val desde = if (minutosInicio <= minutosFinal) inicio else horaFinal
        // El bloqueo llega hasta el FINAL de la franja tocada, no hasta su hora
        // de inicio: tocar solo las 13:00 debe bloquear las 13:00-13:30, no un
        // rango de duracion cero que no impediria nada.
        val hasta = HorarioConsultorio.finDe(
            if (minutosInicio <= minutosFinal) horaFinal else inicio,
        )
        val fecha = _estado.value.fechaAncla
        val nota = _estado.value.notaDeBloqueo.trim()

        _estado.update { it.copy(inicioDeBloqueo = null, notaDeBloqueo = "", errorAccion = false) }
        viewModelScope.launch {
            val resultado = ejecutarSeguro {
                repositorio.bloquearHorario(
                    idMedico = idMedico,
                    fecha = fecha,
                    horaInicio = desde,
                    horaFin = hasta,
                    nota = nota,
                )
            }
            if (resultado.isFailure) _estado.update { it.copy(errorAccion = true) }
        }
    }

    /** Libera un bloqueo: se retira de la agenda como una cita cancelada. */
    fun liberarBloqueo(bloqueo: Cita) = cambiarEstado(bloqueo, EstadoCita.CANCELADA)

    fun descartarErrorAccion() {
        _estado.update { it.copy(errorAccion = false) }
    }

    private companion object {
        const val DIAS_DE_LA_SEMANA = 7
    }
}
