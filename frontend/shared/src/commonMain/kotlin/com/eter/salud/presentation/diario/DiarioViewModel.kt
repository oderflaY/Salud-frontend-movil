package com.eter.salud.presentation.diario

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eter.salud.domain.diario.SeveridadDiario
import com.eter.salud.domain.diario.TriageDelDiario
import com.eter.salud.domain.model.EntradaDiario
import com.eter.salud.domain.diario.PreguntaGuiada
import com.eter.salud.domain.diario.PreguntasPorCondicion
import com.eter.salud.domain.repository.HistorialMedicoRepositorio
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
    /**
     * Preguntas guiadas para ESTE paciente, segun sus condiciones. Vacio
     * mientras se carga el expediente o si no se pudo leer: el diario sigue
     * sirviendo como hoja en blanco, que es como funcionaba antes.
     */
    val preguntas: List<PreguntaGuiada> = emptyList(),
    /** Las condiciones que dieron pie a esas preguntas, para decirlo en pantalla. */
    val condiciones: List<String> = emptyList(),
    /** Preguntas que el paciente ya uso en este borrador: no se vuelven a ofrecer. */
    val preguntasUsadas: Set<String> = emptySet(),
) {
    /** Lo que queda por ofrecer: se van consumiendo conforme responde. */
    val preguntasPendientes: List<PreguntaGuiada>
        get() = preguntas.filterNot { it.clave in preguntasUsadas }

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
    /**
     * De donde salen las condiciones del paciente para adaptar las preguntas.
     * `null` deja el diario como hoja en blanco, sin preguntas.
     */
    private val historial: HistorialMedicoRepositorio? = null,
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
        cargarPreguntas()
    }

    /**
     * Lee las condiciones del expediente y arma las preguntas de este paciente.
     * Si el expediente no se puede leer no se avisa de nada: la ausencia de
     * preguntas no rompe el diario, y un error aqui no debe tapar la pantalla
     * donde alguien viene a anotar un sintoma.
     */
    private fun cargarPreguntas() {
        val repositorioHistorial = historial ?: return
        viewModelScope.launch {
            val paciente = ejecutarSeguro { repositorioHistorial.obtenerPaciente(idPaciente) }.getOrNull() ?: return@launch
            val condiciones = paciente.perfilEmergenciaReducido?.condicionesCriticas.orEmpty()
            _estado.update {
                it.copy(
                    preguntas = PreguntasPorCondicion.para(condiciones),
                    condiciones = PreguntasPorCondicion.condicionesReconocidas(condiciones),
                )
            }
        }
    }

    /**
     * El paciente toco una pregunta: su inicio de respuesta se anade al
     * borrador y la pregunta deja de ofrecerse.
     *
     * Se ANADE, nunca se reemplaza: quien ya escribio medio parrafo no debe
     * perderlo por tocar una pregunta.
     */
    fun responder(pregunta: PreguntaGuiada) {
        _estado.update { actual ->
            val separador = if (actual.borrador.isBlank()) "" else "\n"
            actual.copy(
                borrador = actual.borrador + separador + pregunta.inicioDeRespuesta,
                preguntasUsadas = actual.preguntasUsadas + pregunta.clave,
                errorGuardado = false,
            )
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

        // Entrada guardada, preguntas de vuelta: manana toca contar lo de
        // manana, y las mismas preguntas vuelven a tener sentido.
        _estado.update { it.copy(borrador = "", errorGuardado = false, preguntasUsadas = emptySet()) }
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
