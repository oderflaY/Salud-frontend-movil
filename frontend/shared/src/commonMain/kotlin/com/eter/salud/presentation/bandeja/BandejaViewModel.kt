package com.eter.salud.presentation.bandeja

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eter.salud.domain.model.AutorMensaje
import com.eter.salud.domain.model.ConversacionResumen
import com.eter.salud.domain.model.MedicoVinculado
import com.eter.salud.domain.repository.ChatRepositorio
import com.eter.salud.domain.repository.DirectorioMedicoRepositorio
import com.eter.salud.presentation.comun.ejecutarSeguro
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Bandeja de conversaciones del paciente.
 *
 * Cruza dos fuentes que hasta ahora nadie juntaba: el directorio sabe CON QUIEN
 * esta vinculado el paciente, y el chat sabe QUE se dijo la ultima vez. La
 * bandeja necesita las dos para pintar una fila, y ese cruce es logica de
 * presentacion -- no de red -- asi que vive aqui y no en un repositorio.
 *
 * ## Por que la bandeja y el directorio son pantallas distintas
 *
 * Antes, "Mi medico" mostraba el catalogo completo de especialistas. Eso mezcla
 * dos intenciones que no se parecen: hablar con quien ya te atiende (algo
 * diario) y buscar un especialista nuevo (algo excepcional). Poner el catalogo
 * por delante obligaba a atravesar una lista de desconocidos para llegar a tu
 * propia cardiologa.
 */
class BandejaViewModel(
    private val directorio: DirectorioMedicoRepositorio,
    private val chat: ChatRepositorio,
    private val idPaciente: String,
) : ViewModel() {

    private val _estado = MutableStateFlow<BandejaUiState>(BandejaUiState.Cargando)
    val estado: StateFlow<BandejaUiState> = _estado.asStateFlow()

    private var consultaEnCurso = false

    /** Colectores vivos del contador de sin leer, uno por conversacion. */
    private var vigilanciaDeSinLeer: List<Job> = emptyList()

    init {
        // Atada al ciclo de vida del ViewModel, no a un `LaunchedEffect` de la
        // Vista: la clave del efecto no cambiaria al recrearse y la bandeja se
        // quedaria vacia para siempre.
        cargar()
    }

    fun cargar() = leer(mostrarCarga = true)

    private fun leer(mostrarCarga: Boolean) {
        if (consultaEnCurso) return
        consultaEnCurso = true
        if (mostrarCarga) _estado.value = BandejaUiState.Cargando

        viewModelScope.launch {
            try {
                val vinculados = ejecutarSeguro { directorio.obtenerMedicosVinculados(idPaciente) }
                if (vinculados.isFailure) {
                    _estado.value = BandejaUiState.Error
                    return@launch
                }

                val medicos = vinculados.getOrThrow()
                if (medicos.isEmpty()) {
                    _estado.value = BandejaUiState.SinConversaciones
                    return@launch
                }

                val resumenes = medicos.mapNotNull { medico -> resumirConversacion(medico) }
                _estado.value = if (resumenes.isEmpty()) {
                    BandejaUiState.SinConversaciones
                } else {
                    // Mas reciente arriba, como cualquier bandeja: lo que acaba
                    // de pasar es lo que se busca al abrirla.
                    BandejaUiState.ConConversaciones(resumenes.sortedByDescending { it.claveOrden })
                }
                observarSinLeer(resumenes.map { it.idConversacion })
            } finally {
                consultaEnCurso = false
            }
        }
    }

    /**
     * Arma la fila de un medico.
     *
     * Devuelve nulo si su historial no se pudo leer o esta vacio: una fila sin
     * ultimo mensaje seria un hueco mudo en la lista, y es preferible mostrar
     * las conversaciones que si tienen contenido que una bandeja a medio pintar.
     */
    private suspend fun resumirConversacion(medico: MedicoVinculado): ConversacionResumen? {
        val historial = ejecutarSeguro { chat.obtenerHistorial(medico.idConversacion) }
        val ultimo = historial.getOrNull()?.lastOrNull() ?: return null

        return ConversacionResumen(
            idConversacion = medico.idConversacion,
            idMedico = medico.idMedico,
            nombreMedico = medico.nombreCompleto,
            especialidad = medico.especialidad,
            ultimoMensaje = ultimo.texto,
            instanteUltimoMensaje = ultimo.instante,
            autorUltimoMensaje = ultimo.autor,
            // Arranca en cero y lo rellena `observarSinLeer` en cuanto el
            // repositorio emite: el historial no sabe cuantos quedan sin ver.
            mensajesSinLeer = 0,
        )
    }

    /**
     * Mantiene vivo el contador de sin leer de cada conversacion.
     *
     * Una suscripcion POR conversacion, no una lectura unica: el punto rojo
     * tiene que aparecer en el momento en que llega el mensaje, con el paciente
     * mirando esta misma pantalla. Leer el valor una vez al cargar dejaria la
     * bandeja congelada hasta que alguien la abandonara y volviera.
     *
     * Las suscripciones anteriores se cancelan antes de abrir las nuevas: sin
     * eso, cada recarga dejaria una capa mas de colectores escribiendo sobre el
     * mismo estado.
     */
    private fun observarSinLeer(conversaciones: List<String>) {
        vigilanciaDeSinLeer.forEach { it.cancel() }
        vigilanciaDeSinLeer = conversaciones.map { idConversacion ->
            viewModelScope.launch {
                chat.mensajesSinLeer(idConversacion).collect { pendientes ->
                    _estado.update { actual ->
                        if (actual !is BandejaUiState.ConConversaciones) return@update actual
                        actual.copy(
                            conversaciones = actual.conversaciones.map { fila ->
                                if (fila.idConversacion == idConversacion) {
                                    fila.copy(mensajesSinLeer = pendientes)
                                } else {
                                    fila
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    /**
     * Relee la bandeja al volver a ella: de un chat (ultimo mensaje nuevo) o del
     * directorio (un medico recien escogido). Si ya habia filas no pasa por
     * "cargando": un parpadeo de esqueleto cada vez que se cambia de pestana
     * haria dudar de si se perdieron las conversaciones.
     */
    fun refrescar() {
        leer(mostrarCarga = _estado.value !is BandejaUiState.ConConversaciones)
    }

    /** El medico elegido, para que la Vista sepa a que conversacion navegar. */
    fun medicoDe(resumen: ConversacionResumen): MedicoVinculado = MedicoVinculado(
        idMedico = resumen.idMedico,
        nombreCompleto = resumen.nombreMedico,
        especialidad = resumen.especialidad,
        idConversacion = resumen.idConversacion,
    )
}
