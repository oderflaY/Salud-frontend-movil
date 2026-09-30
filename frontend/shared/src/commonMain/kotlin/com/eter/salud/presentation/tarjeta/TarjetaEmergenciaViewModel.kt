package com.eter.salud.presentation.tarjeta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eter.salud.data.preferencias.PreferenciasDeTarjeta
import com.eter.salud.domain.model.DatoDeTarjeta
import com.eter.salud.domain.model.PerfilEmergenciaReducido
import com.eter.salud.domain.model.VisibilidadDeTarjeta
import com.eter.salud.domain.model.segun
import com.eter.salud.domain.repository.HistorialMedicoRepositorio
import com.eter.salud.presentation.comun.ejecutarSeguro
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado de la pantalla "Mi tarjeta de emergencia". */
data class TarjetaEmergenciaUiState(
    val cargando: Boolean = true,
    val errorCarga: Boolean = false,
    /** El paciente no tiene una tarjeta activa: no hay nada que administrar. */
    val sinTarjeta: Boolean = false,
    val idTarjeta: String = "",
    val nombreCompleto: String = "",
    val fechaNacimiento: String = "",
    /** Todo lo que hay en el expediente, para mostrarle al paciente QUE comparte. */
    val perfil: PerfilEmergenciaReducido = PerfilEmergenciaReducido(),
    val visibilidad: VisibilidadDeTarjeta = VisibilidadDeTarjeta.TODO_VISIBLE,
) {
    /** Lo que vera un paramedico al escanear, con lo elegido ya aplicado. */
    val vistaDelParamedico: PerfilEmergenciaReducido get() = perfil.segun(visibilidad)
}

/**
 * El paciente decide que muestra su tarjeta de emergencia.
 *
 * Cada interruptor se guarda al instante, sin boton de "guardar": es una
 * preferencia, y una que se olvida guardar deja la tarjeta mostrando lo que el
 * paciente creia haber ocultado.
 */
class TarjetaEmergenciaViewModel(
    private val historial: HistorialMedicoRepositorio,
    private val preferencias: PreferenciasDeTarjeta,
    private val idPaciente: String,
) : ViewModel() {

    private val _estado = MutableStateFlow(TarjetaEmergenciaUiState())
    val estado: StateFlow<TarjetaEmergenciaUiState> = _estado.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _estado.update { it.copy(cargando = true, errorCarga = false) }
        viewModelScope.launch {
            val paciente = ejecutarSeguro { historial.obtenerPaciente(idPaciente) }.getOrNull()
            if (paciente == null) {
                _estado.update { it.copy(cargando = false, errorCarga = true) }
                return@launch
            }
            val tarjeta = paciente.dispositivosRfid.firstOrNull { it.estado == TARJETA_ACTIVA }
            if (tarjeta == null) {
                _estado.update { it.copy(cargando = false, sinTarjeta = true) }
                return@launch
            }
            val personales = paciente.datosPersonales
            _estado.update {
                it.copy(
                    cargando = false,
                    idTarjeta = tarjeta.idTarjetaRfid,
                    nombreCompleto = personales?.let { d -> "${d.nombre} ${d.apellidos}" }.orEmpty(),
                    fechaNacimiento = personales?.fechaNacimiento.orEmpty(),
                    perfil = paciente.perfilEmergenciaReducido ?: PerfilEmergenciaReducido(),
                )
            }
            // Se queda escuchando: si la cambia otra pantalla, esta se entera.
            preferencias.visibilidad(tarjeta.idTarjetaRfid).collect { visibilidad ->
                _estado.update { it.copy(visibilidad = visibilidad) }
            }
        }
    }

    fun cambiar(dato: DatoDeTarjeta, visible: Boolean) {
        val actual = _estado.value
        if (actual.idTarjeta.isBlank()) return
        val nueva = actual.visibilidad.conDato(dato, visible)
        // Optimista: el interruptor responde al toque, sin esperar al disco.
        _estado.update { it.copy(visibilidad = nueva) }
        viewModelScope.launch { preferencias.guardar(actual.idTarjeta, nueva) }
    }

    private companion object {
        const val TARJETA_ACTIVA = "activa"
    }
}
