package com.eter.salud.presentation.profesional

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eter.salud.domain.model.EstadoVerificacionCedula
import com.eter.salud.domain.repository.DiarioRepositorio
import com.eter.salud.domain.repository.PacientesVinculadosRepositorio
import com.eter.salud.presentation.comun.ejecutarSeguro
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel del panel principal del personal medico.
 *
 * Administra el estado de la sesion (identidad y verificacion de la Cedula
 * Profesional, ya conocidas al momento del acceso) y la cartera de pacientes
 * vinculados, que si exige una llamada de red.
 *
 * El escaner de emergencia NO se lanza desde aqui: es pura navegacion de la
 * Vista (igual que el resto de accesos de esta app, ver
 * [com.eter.salud.presentation.home.HomeViewModel]), y este ViewModel no
 * conoce Compose, ni recursos, ni el modulo NFC -- solo el contrato
 * [PacientesVinculadosRepositorio].
 */
class HomeProfesionalViewModel(
    private val pacientesVinculados: PacientesVinculadosRepositorio,
    private val diario: DiarioRepositorio,
    idMedico: String,
    tratamiento: String,
    apellidos: String,
    estadoVerificacion: EstadoVerificacionCedula,
) : ViewModel() {

    private val _estado = MutableStateFlow(
        HomeProfesionalUiState(
            idMedico = idMedico,
            tratamiento = tratamiento,
            apellidos = apellidos,
            estadoVerificacion = estadoVerificacion,
        ),
    )
    val estado: StateFlow<HomeProfesionalUiState> = _estado.asStateFlow()

    /**
     * Trae la severidad de la ultima entrada del diario de cada paciente.
     *
     * Se pide SOLO la ultima entrada de cada uno, nunca la bitacora completa: el
     * punto de la tarjeta necesita un enum, y descargar meses de texto clinico
     * de veinte pacientes para pintar veinte circulos seria gastar red y memoria
     * en datos que esta pantalla no muestra.
     *
     * Su fallo NO tumba el panel: la cartera es lo que el medico viene a ver, y
     * un punto que falta es peor que una pantalla de error sobre la lista entera.
     * Los pacientes sin entrada quedan fuera del mapa, que es distinto de estar
     * en verde: no han escrito, no es que esten bien.
     */
    private suspend fun cargarSeveridades(pacientes: List<com.eter.salud.domain.model.PacienteVinculado>) {
        val severidades = pacientes.mapNotNull { paciente ->
            val entrada = ejecutarSeguro { diario.ultimaEntradaDe(paciente.idPaciente) }
                .getOrNull() ?: return@mapNotNull null
            paciente.idPaciente to entrada.severidad
        }.toMap()
        _estado.update { it.copy(severidadDelDiario = severidades) }
    }

    /** Descarga la cartera de pacientes vinculados al profesional. */
    fun cargar() {
        val actual = _estado.value
        if (actual.cargando) return
        _estado.update { it.copy(cargando = true, errorCarga = false) }
        viewModelScope.launch {
            val resultado = ejecutarSeguro { pacientesVinculados.obtenerPacientesVinculados(actual.idMedico) }
            _estado.update { previo ->
                resultado.fold(
                    onSuccess = { pacientes ->
                        previo.copy(
                            cargando = false,
                            errorCarga = false,
                            pacientesVinculados = pacientes,
                        )
                    },
                    onFailure = { previo.copy(cargando = false, errorCarga = true) },
                )
            }
            resultado.getOrNull()?.let { cargarSeveridades(it) }
        }
    }
}
