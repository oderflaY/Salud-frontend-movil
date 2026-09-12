package com.eter.salud.presentation.expediente

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eter.salud.domain.repository.HistorialMedicoRepositorio
import com.eter.salud.presentation.comun.ejecutarSeguro
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Expediente clinico de un paciente vinculado, en solo lectura, para el medico.
 *
 * Es el mismo contrato [HistorialMedicoRepositorio] que usa
 * [com.eter.salud.presentation.perfil.PerfilMedicoViewModel] para que el
 * paciente edite su propio historial -- DM_HistorialMedico.md, seccion 2,
 * exige que el permiso de LECTURA de un medico sobre un paciente vinculado y el
 * de EDICION del propio paciente vivan detras del mismo dato, nunca de una
 * copia -- pero aqui no hay borrador ni guardado: el medico consulta, no
 * corrige.
 *
 * Carga en el `init` y no en un efecto de la Vista porque [idPaciente] ya llega
 * fijo en el constructor: es el mismo motivo por el que
 * [com.eter.salud.presentation.agenda.AgendaMedicoViewModel] carga su agenda
 * en el `init`.
 */
class ExpedienteMedicoViewModel(
    private val repositorio: HistorialMedicoRepositorio,
    private val idPaciente: String,
) : ViewModel() {

    private val _estado = MutableStateFlow(ExpedienteMedicoUiState())
    val estado: StateFlow<ExpedienteMedicoUiState> = _estado.asStateFlow()

    init {
        cargar()
    }

    /** Reintenta tras un fallo de carga. No hace nada si ya hay una en curso. */
    fun reintentar() {
        if (_estado.value.cargando) return
        cargar()
    }

    private fun cargar() {
        _estado.update { it.copy(cargando = true, errorCarga = false) }
        viewModelScope.launch {
            val resultado = ejecutarSeguro { repositorio.obtenerPaciente(idPaciente) }
            _estado.update { previo ->
                resultado.fold(
                    onSuccess = { dto -> previo.copy(cargando = false, paciente = dto, errorCarga = false) },
                    onFailure = { previo.copy(cargando = false, errorCarga = true) },
                )
            }
        }
    }
}
