package com.eter.salud.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eter.salud.domain.mapper.aPacienteDto
import com.eter.salud.domain.model.Alergia
import com.eter.salud.domain.model.ContactoEmergencia
import com.eter.salud.domain.model.PacienteDto
import com.eter.salud.domain.repository.PacienteRepositorio
import com.eter.salud.domain.time.RelojSalud
import com.eter.salud.domain.time.relojDelSistema
import com.eter.salud.presentation.comun.ejecutarSeguro
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel de la Fase 1 del onboarding del paciente.
 *
 * Responsabilidades (MVVM estricto):
 *  - Gobernar la maquina de estados de [PasoOnboarding].
 *  - Validar cada respuesta antes de dejar avanzar.
 *  - Acumular las respuestas en [BorradorPaciente] y, al cerrar el flujo,
 *    proyectarlas al DTO del DM_HistorialMedico.md.
 *  - Alertar cuando falte un dato critico de supervivencia antes de enviar.
 *
 * No conoce Compose, ni recursos, ni red: solo el contrato [PacienteRepositorio].
 */
class OnboardingPacienteViewModel(
    private val repositorio: PacienteRepositorio,
    private val reloj: RelojSalud = relojDelSistema(),
) : ViewModel() {

    private val _estado = MutableStateFlow(
        OnboardingUiState(
            camposCriticosOmitidos = ValidadorOnboarding.camposCriticosOmitidos(BorradorPaciente()),
            fechaDeHoy = reloj.fechaHoy(),
        ),
    )
    val estado: StateFlow<OnboardingUiState> = _estado.asStateFlow()

    // ------------------------------------------------------------- Navegacion

    /** Valida el paso actual y, si es correcto, avanza al siguiente. */
    fun avanzar() {
        val actual = _estado.value
        if (actual.enviando) return
        val errores = ValidadorOnboarding.validarPaso(
            paso = actual.paso,
            borrador = actual.borrador,
            hoy = reloj.fechaHoy(),
        )
        if (errores.isNotEmpty()) {
            _estado.update { it.copy(erroresPaso = errores) }
            return
        }
        moverA(actual.paso.siguiente ?: return)
    }

    fun retroceder() {
        val anterior = _estado.value.paso.anterior ?: return
        moverA(anterior)
    }

    /** Pospone un paso omitible; el dato faltante queda registrado como critico. */
    fun omitirPaso() {
        val actual = _estado.value
        if (!actual.paso.esOmitible) return
        moverA(actual.paso.siguiente ?: return)
    }

    private fun moverA(destino: PasoOnboarding) {
        _estado.update {
            it.copy(
                paso = destino,
                erroresPaso = emptyList(),
                erroresFormulario = emptyList(),
            )
        }
    }

    // -------------------------------------------------- Pregunta 1 a 5: identidad

    fun actualizarNombre(valor: String) = editarBorrador { it.copy(nombre = valor) }

    fun actualizarApellidos(valor: String) = editarBorrador { it.copy(apellidos = valor) }

    fun actualizarFechaNacimiento(valor: String) =
        editarBorrador { it.copy(fechaNacimiento = valor) }

    fun actualizarGenero(valor: String) = editarBorrador { it.copy(genero = valor) }

    fun actualizarTelefono(valor: String) = editarBorrador { it.copy(telefono = valor) }

    fun seleccionarTipoSangre(valor: String) =
        editarBorrador { it.copy(tipoSangre = valor, desconoceTipoSangre = false) }

    fun marcarDesconoceTipoSangre(desconoce: Boolean) =
        editarBorrador { it.copy(desconoceTipoSangre = desconoce, tipoSangre = "") }

    // ------------------------------------------------------- Pregunta 6: alergias

    fun agregarAlergia(alergeno: String, severidad: String, reaccion: String) {
        val errores = ValidadorOnboarding.validarAlergia(alergeno, severidad, reaccion)
        if (errores.isNotEmpty()) {
            _estado.update { it.copy(erroresFormulario = errores) }
            return
        }
        editarBorrador { borrador ->
            borrador.copy(
                alergias = borrador.alergias + Alergia(
                    alergeno = alergeno.trim(),
                    severidad = severidad.trim(),
                    reaccion = reaccion.trim(),
                ),
                sinAlergiasConocidas = false,
            )
        }
    }

    fun eliminarAlergia(indice: Int) = editarBorrador { borrador ->
        borrador.copy(alergias = borrador.alergias.sinElementoEn(indice))
    }

    fun marcarSinAlergiasConocidas(sinAlergias: Boolean) = editarBorrador {
        it.copy(sinAlergiasConocidas = sinAlergias, alergias = if (sinAlergias) emptyList() else it.alergias)
    }

    // -------------------------------------------- Pregunta 7: condiciones criticas

    fun agregarCondicionCritica(condicion: String) {
        if (condicion.isBlank()) {
            _estado.update {
                it.copy(erroresFormulario = listOf(ErrorCampoOnboarding.CONDICION_VACIA))
            }
            return
        }
        editarBorrador { borrador ->
            borrador.copy(
                condicionesCriticas = borrador.condicionesCriticas + condicion.trim(),
                sinCondicionesCriticas = false,
            )
        }
    }

    fun eliminarCondicionCritica(indice: Int) = editarBorrador { borrador ->
        borrador.copy(condicionesCriticas = borrador.condicionesCriticas.sinElementoEn(indice))
    }

    fun marcarSinCondicionesCriticas(sinCondiciones: Boolean) = editarBorrador {
        it.copy(
            sinCondicionesCriticas = sinCondiciones,
            condicionesCriticas = if (sinCondiciones) emptyList() else it.condicionesCriticas,
        )
    }

    // ------------------------------------------ Pregunta 8: medicacion de rescate

    fun agregarMedicacionRescate(medicamento: String) {
        if (medicamento.isBlank()) {
            _estado.update {
                it.copy(erroresFormulario = listOf(ErrorCampoOnboarding.RESCATE_VACIO))
            }
            return
        }
        editarBorrador { borrador ->
            borrador.copy(
                medicacionRescate = borrador.medicacionRescate + medicamento.trim(),
                sinMedicacionRescate = false,
            )
        }
    }

    fun eliminarMedicacionRescate(indice: Int) = editarBorrador { borrador ->
        borrador.copy(medicacionRescate = borrador.medicacionRescate.sinElementoEn(indice))
    }

    fun marcarSinMedicacionRescate(sinMedicacion: Boolean) = editarBorrador {
        it.copy(
            sinMedicacionRescate = sinMedicacion,
            medicacionRescate = if (sinMedicacion) emptyList() else it.medicacionRescate,
        )
    }

    // ----------------------------------------- Pregunta 9: tratamientos e inventario

    fun agregarTratamiento(
        medicamento: String,
        dosis: String,
        frecuenciaHoras: String,
        cantidadRestante: String,
        viaAdministracion: String,
    ) {
        val errores = ValidadorOnboarding.validarTratamiento(
            medicamento = medicamento,
            dosis = dosis,
            frecuenciaHoras = frecuenciaHoras,
            cantidadRestante = cantidadRestante,
        )
        if (errores.isNotEmpty()) {
            _estado.update { it.copy(erroresFormulario = errores) }
            return
        }
        editarBorrador { borrador ->
            borrador.copy(
                tratamientos = borrador.tratamientos + BorradorTratamiento(
                    medicamento = medicamento.trim(),
                    dosis = dosis.trim(),
                    frecuenciaHoras = frecuenciaHoras.trim(),
                    cantidadRestante = cantidadRestante.trim(),
                    viaAdministracion = viaAdministracion.trim(),
                ),
                sinTratamientos = false,
            )
        }
    }

    fun eliminarTratamiento(indice: Int) = editarBorrador { borrador ->
        borrador.copy(tratamientos = borrador.tratamientos.sinElementoEn(indice))
    }

    fun marcarSinTratamientos(sinTratamientos: Boolean) = editarBorrador {
        it.copy(
            sinTratamientos = sinTratamientos,
            tratamientos = if (sinTratamientos) emptyList() else it.tratamientos,
        )
    }

    // ------------------------------------ Pregunta 10: contactos de emergencia

    fun agregarContacto(nombre: String, relacion: String, telefono: String, prioridad: Int) {
        val errores = ValidadorOnboarding.validarContacto(nombre, relacion, telefono)
        if (errores.isNotEmpty()) {
            _estado.update { it.copy(erroresFormulario = errores) }
            return
        }
        editarBorrador { borrador ->
            borrador.copy(
                contactos = borrador.contactos + ContactoEmergencia(
                    nombre = nombre.trim(),
                    relacion = relacion.trim(),
                    telefono = telefono.trim(),
                    prioridad = prioridad,
                ),
            )
        }
    }

    fun eliminarContacto(indice: Int) = editarBorrador { borrador ->
        borrador.copy(contactos = borrador.contactos.sinElementoEn(indice))
    }

    // ------------------------------------------------------------------ Envio

    /**
     * Cierra el flujo. Si falta un dato critico de supervivencia no llama a la
     * red: levanta la alerta para que la Vista la muestre en un action sheet.
     */
    fun enviar() {
        val actual = _estado.value
        if (actual.enviando) return
        if (actual.camposCriticosOmitidos.isNotEmpty()) {
            _estado.update { it.copy(mostrarAlertaCriticos = true) }
            return
        }
        ejecutarEnvio()
    }

    /** El paciente entiende el riesgo y decide enviar el perfil incompleto. */
    fun confirmarEnvioConCamposCriticosOmitidos() {
        _estado.update { it.copy(mostrarAlertaCriticos = false) }
        ejecutarEnvio()
    }

    /** Lleva al paciente al paso donde se captura el dato critico que falta. */
    fun irAPasoDelCampoCritico(campo: CampoCriticoSupervivencia) {
        val destino = when (campo) {
            CampoCriticoSupervivencia.TIPO_SANGRE -> PasoOnboarding.TIPO_SANGRE
            CampoCriticoSupervivencia.ALERGIAS -> PasoOnboarding.ALERGIAS
            CampoCriticoSupervivencia.CONDICIONES_CRITICAS -> PasoOnboarding.CONDICIONES_CRITICAS
            CampoCriticoSupervivencia.MEDICACION_RESCATE -> PasoOnboarding.MEDICACION_RESCATE
            CampoCriticoSupervivencia.CONTACTOS_EMERGENCIA -> PasoOnboarding.CONTACTOS_EMERGENCIA
        }
        _estado.update { it.copy(mostrarAlertaCriticos = false) }
        moverA(destino)
    }

    /** Cierra la alerta para volver a capturar los datos faltantes. */
    fun descartarAlertaCriticos() {
        _estado.update { it.copy(mostrarAlertaCriticos = false) }
    }

    private fun ejecutarEnvio() {
        _estado.update { it.copy(enviando = true, errorEnvio = null) }
        viewModelScope.launch {
            val resultado = ejecutarSeguro { repositorio.registrarPaciente(construirDto()) }
            _estado.update { previo ->
                resultado.fold(
                    onSuccess = { idPaciente ->
                        previo.copy(
                            enviando = false,
                            envioExitoso = true,
                            idPacienteRegistrado = idPaciente,
                            errorEnvio = null,
                        )
                    },
                    onFailure = {
                        previo.copy(
                            enviando = false,
                            envioExitoso = false,
                            errorEnvio = ErrorEnvio.SIN_CONEXION,
                        )
                    },
                )
            }
        }
    }

    /** Proyeccion del borrador al contrato de red. Expuesta para la vista de resumen. */
    fun construirDto(): PacienteDto = _estado.value.borrador.aPacienteDto(reloj)

    // ------------------------------------------------------------- Utilidades

    /**
     * Punto unico de escritura del borrador: recalcula los campos criticos
     * pendientes y baja la alerta en cuanto el paciente completa lo que faltaba.
     */
    private fun editarBorrador(bloque: (BorradorPaciente) -> BorradorPaciente) {
        _estado.update { previo ->
            val borrador = bloque(previo.borrador)
            val criticos = ValidadorOnboarding.camposCriticosOmitidos(borrador)
            previo.copy(
                borrador = borrador,
                camposCriticosOmitidos = criticos,
                mostrarAlertaCriticos = previo.mostrarAlertaCriticos && criticos.isNotEmpty(),
                erroresFormulario = emptyList(),
            )
        }
    }

    private fun <T> List<T>.sinElementoEn(indice: Int): List<T> =
        if (indice in indices) filterIndexed { posicion, _ -> posicion != indice } else this
}
