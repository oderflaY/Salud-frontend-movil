package com.eter.salud.presentation.expediente

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eter.salud.data.expediente.ExpedienteCompartidoRepositorio
import com.eter.salud.domain.model.ExpedienteCompartido
import com.eter.salud.domain.model.PacienteDto
import com.eter.salud.domain.model.paraElMedico
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
    /**
     * Lo que el paciente decidio compartir. `null` muestra el expediente
     * completo, como antes de que existiera esta decision.
     */
    private val compartido: ExpedienteCompartidoRepositorio? = null,
) : ViewModel() {

    /** El expediente tal como llego, antes de aplicar lo que el paciente oculto. */
    private var completo: PacienteDto? = null
    private var decision = ExpedienteCompartido()

    private val _estado = MutableStateFlow(ExpedienteMedicoUiState())
    val estado: StateFlow<ExpedienteMedicoUiState> = _estado.asStateFlow()

    init {
        cargar()
        compartido?.let { repositorioCompartido ->
            viewModelScope.launch {
                // Si el paciente cambia de opinion, la pantalla del medico lo
                // refleja sin tener que salir y volver a entrar.
                repositorioCompartido.de(idPaciente).collect { actual ->
                    decision = actual
                    publicar()
                }
            }
        }
    }

    /** Vuelve a armar lo que el medico ve con el expediente y la decision actuales. */
    private fun publicar() {
        _estado.update {
            it.copy(
                paciente = completo?.paraElMedico(decision),
                bloquesOcultos = decision.bloquesOcultos,
                documentos = decision.documentosVisibles,
            )
        }
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
                    onSuccess = { dto ->
                        completo = dto
                        previo.copy(cargando = false, paciente = dto.paraElMedico(decision), errorCarga = false)
                    },
                    onFailure = { previo.copy(cargando = false, errorCarga = true) },
                )
            }
        }
    }
}
