package com.eter.salud.presentation.profesional

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eter.salud.domain.model.EstadoVerificacionCedula
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
        }
    }
}
