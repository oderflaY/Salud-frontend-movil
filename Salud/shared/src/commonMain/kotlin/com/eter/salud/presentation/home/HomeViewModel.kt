package com.eter.salud.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eter.salud.domain.model.EstadoToma
import com.eter.salud.domain.model.TomaDelDia
import com.eter.salud.domain.repository.AdherenciaRepositorio
import com.eter.salud.domain.repository.HistorialMedicoRepositorio
import com.eter.salud.domain.time.RelojSalud
import com.eter.salud.domain.time.relojDelSistema
import com.eter.salud.presentation.comun.ejecutarSeguro
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel del panel principal del paciente.
 *
 * Junta dos fuentes que se mueven a ritmos distintos: el expediente (nombre y
 * estado de la tarjeta RFID) y el seguimiento de tomas del dia.
 *
 * No conoce Compose, ni recursos, ni red: solo los contratos
 * [HistorialMedicoRepositorio] y [AdherenciaRepositorio].
 */
class HomeViewModel(
    private val historial: HistorialMedicoRepositorio,
    private val adherencia: AdherenciaRepositorio,
    private val idPaciente: String,
    perfilEmergenciaPendiente: Boolean,
    private val reloj: RelojSalud = relojDelSistema(),
) : ViewModel() {

    private val _estado = MutableStateFlow(
        HomeUiState(
            idPaciente = idPaciente,
            perfilEmergenciaPendiente = perfilEmergenciaPendiente,
        ),
    )
    val estado: StateFlow<HomeUiState> = _estado.asStateFlow()

    /** Descarga identidad, tomas del dia y cumplimiento de la semana. */
    fun cargar() {
        if (_estado.value.cargando) return
        _estado.update { it.copy(cargando = true, errorCarga = false) }
        viewModelScope.launch {
            val hoy = reloj.fechaHoy()
            val perfil = ejecutarSeguro { historial.obtenerPaciente(idPaciente) }
            val tomas = ejecutarSeguro { adherencia.obtenerTomasDelDia(idPaciente, hoy) }
            val resumen = ejecutarSeguro { adherencia.obtenerResumenSemanal(idPaciente, hoy) }

            if (perfil.isFailure || tomas.isFailure || resumen.isFailure) {
                _estado.update { it.copy(cargando = false, errorCarga = true) }
                return@launch
            }

            val paciente = perfil.getOrThrow()
            _estado.update { previo ->
                previo.copy(
                    cargando = false,
                    errorCarga = false,
                    nombrePaciente = paciente.datosPersonales?.nombre.orEmpty(),
                    tarjetaRfidActiva = paciente.dispositivosRfid.any { it.estado == RFID_ACTIVA },
                    tomasDelDia = tomas.getOrThrow().ordenadasPorHora(),
                    resumenSemanal = resumen.getOrThrow(),
                )
            }
        }
    }

    fun marcarTomada(idToma: String) = registrar(idToma, EstadoToma.TOMADO)

    fun omitirToma(idToma: String) = registrar(idToma, EstadoToma.OMITIDO)

    /** Al volver del onboarding, retira el aviso sin recrear la pantalla. */
    fun marcarPerfilCompletado() {
        _estado.update { it.copy(perfilEmergenciaPendiente = false) }
    }

    fun descartarErrorRegistro() {
        _estado.update { it.copy(errorRegistroToma = false) }
    }

    /**
     * Escritura optimista: la tarjeta cambia al instante y, si el backend
     * rechaza el registro, se revierte. Un paciente que acaba de tomar su
     * medicamento no debe quedarse mirando un indicador de carga.
     */
    private fun registrar(idToma: String, estado: EstadoToma) {
        val actual = _estado.value
        if (actual.tomaEnCurso != null) return
        val previa = actual.tomasDelDia.firstOrNull { it.idToma == idToma } ?: return
        if (previa.estado.estaRegistrada) return

        _estado.update {
            it.copy(
                tomasDelDia = it.tomasDelDia.conEstado(idToma, estado),
                tomaEnCurso = idToma,
                errorRegistroToma = false,
            )
        }

        viewModelScope.launch {
            val resultado = ejecutarSeguro {
                adherencia.registrarToma(
                    idPaciente = idPaciente,
                    idToma = idToma,
                    estado = estado,
                    instante = reloj.instanteActual(),
                )
            }
            if (resultado.isFailure) {
                _estado.update {
                    it.copy(
                        tomasDelDia = it.tomasDelDia.conEstado(idToma, previa.estado),
                        tomaEnCurso = null,
                        errorRegistroToma = true,
                    )
                }
                return@launch
            }

            // El cumplimiento semanal lo calcula el backend; se relee en vez de
            // deducirlo aqui para no divergir de lo que ve el medico.
            val resumen = ejecutarSeguro { adherencia.obtenerResumenSemanal(idPaciente, reloj.fechaHoy()) }
            _estado.update { previo ->
                previo.copy(
                    tomaEnCurso = null,
                    resumenSemanal = resumen.getOrNull() ?: previo.resumenSemanal,
                )
            }
        }
    }

    private fun List<TomaDelDia>.ordenadasPorHora(): List<TomaDelDia> =
        sortedBy { it.horaProgramada }

    private fun List<TomaDelDia>.conEstado(idToma: String, estado: EstadoToma): List<TomaDelDia> =
        map { if (it.idToma == idToma) it.copy(estado = estado) else it }

    private companion object {
        const val RFID_ACTIVA = "activa"
    }
}
