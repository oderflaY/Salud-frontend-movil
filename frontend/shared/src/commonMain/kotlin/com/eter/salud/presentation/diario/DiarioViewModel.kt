package com.eter.salud.presentation.diario

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eter.salud.domain.diario.SeveridadDiario
import com.eter.salud.domain.diario.TriageDelDiario
import com.eter.salud.domain.model.EntradaDiario
import com.eter.salud.domain.repository.DiarioRepositorio
import com.eter.salud.domain.time.RelojSalud
import com.eter.salud.domain.time.relojDelSistema
import com.eter.salud.presentation.comun.ejecutarSeguro
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado unico del diario de sintomas. */
data class DiarioUiState(
    val entradas: List<EntradaDiario> = emptyList(),
    val borrador: String = "",
    val cargando: Boolean = true,
    val errorGuardado: Boolean = false,
) {
    val puedeGuardar: Boolean get() = borrador.isNotBlank()

    /**
     * Severidad del borrador MIENTRAS se escribe.
     *
     * Se muestra en vivo a proposito: el paciente ve que lo que acaba de teclear
     * va a llegarle al medico marcado como urgente, y puede matizarlo antes de
     * enviarlo. Calcularlo solo al guardar convertiria el semaforo en una
     * sorpresa.
     */
    val severidadDelBorrador: SeveridadDiario
        get() = TriageDelDiario.clasificar(borrador)
}

/**
 * Diario de sintomas del paciente.
 *
 * El triage corre en el dispositivo, en el momento de escribir, sin red. Eso no
 * es una limitacion de esta fase: el paciente anota sintomas donde sea -- en una
 * sala de espera sin cobertura, de madrugada -- y ademas el texto clinico crudo
 * que no necesita salir del telefono, no sale.
 */
class DiarioViewModel(
    private val repositorio: DiarioRepositorio,
    private val idPaciente: String,
    private val reloj: RelojSalud = relojDelSistema(),
) : ViewModel() {

    private val _estado = MutableStateFlow(DiarioUiState())
    val estado: StateFlow<DiarioUiState> = _estado.asStateFlow()

    init {
        viewModelScope.launch {
            // `collect` no vuelve nunca: se queda escuchando mientras el
            // ViewModel viva y se cancela solo con el.
            repositorio.entradasDe(idPaciente).collect { entradas ->
                _estado.update { it.copy(entradas = entradas, cargando = false) }
            }
        }
    }

    fun actualizarBorrador(texto: String) {
        _estado.update { it.copy(borrador = texto, errorGuardado = false) }
    }

    /**
     * Guarda la entrada con su severidad ya calculada.
     *
     * El campo se limpia de forma OPTIMISTA y se restaura si el guardado falla:
     * perder un parrafo de sintomas que el paciente acaba de describir es peor
     * que verlo un segundo de mas en pantalla.
     */
    fun guardar() {
        val actual = _estado.value
        val texto = actual.borrador.trim()
        if (texto.isBlank()) return

        val instante = reloj.instanteActual()
        val entrada = EntradaDiario(
            idEntrada = "diario_${instante}_${texto.hashCode()}",
            idPaciente = idPaciente,
            instante = instante,
            fecha = reloj.fechaHoy(),
            texto = texto,
            severidad = TriageDelDiario.clasificar(texto),
            terminosDetectados = TriageDelDiario.terminosDetectados(texto),
        )

        _estado.update { it.copy(borrador = "", errorGuardado = false) }
        viewModelScope.launch {
            val resultado = ejecutarSeguro { repositorio.guardar(entrada) }
            if (resultado.isFailure) {
                _estado.update { it.copy(borrador = texto, errorGuardado = true) }
            }
        }
    }

    fun eliminar(idEntrada: String) {
        viewModelScope.launch { ejecutarSeguro { repositorio.eliminar(idEntrada) } }
    }

    fun descartarError() {
        _estado.update { it.copy(errorGuardado = false) }
    }
}
