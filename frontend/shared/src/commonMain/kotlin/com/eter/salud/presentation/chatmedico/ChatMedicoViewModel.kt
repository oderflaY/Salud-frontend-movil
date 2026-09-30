package com.eter.salud.presentation.chatmedico

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eter.salud.domain.model.Adjunto
import com.eter.salud.domain.model.AutorMensaje
import com.eter.salud.domain.model.MensajeChat
import com.eter.salud.domain.model.ResumenClinicoIa
import com.eter.salud.domain.model.RiesgoPaciente
import com.eter.salud.domain.repository.ChatRepositorio
import com.eter.salud.data.preferencias.PreferenciasDeLectura
import com.eter.salud.domain.repository.TraduccionRepositorio
import com.eter.salud.domain.time.RelojSalud
import com.eter.salud.domain.time.relojDelSistema
import com.eter.salud.presentation.comun.EstadoTraduccion
import com.eter.salud.presentation.comun.alternando
import com.eter.salud.presentation.comun.mostrandoOriginales
import com.eter.salud.presentation.comun.ejecutarSeguro
import com.eter.salud.presentation.comun.traducirParaLaVista
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * ViewModel del chat visto desde el perfil del medico.
 *
 * Comparte [ChatRepositorio] y conversacion con el chat del paciente: son dos
 * caras del mismo canal, no dos historiales distintos. Por eso el autor viaja
 * explicito en cada envio ([AutorMensaje.MEDICO] aqui) en lugar de darse por
 * supuesto en el repositorio.
 *
 * El envio es optimista: la burbuja aparece al instante y se reconcilia con el
 * id definitivo del backend, o se retira si falla, devolviendo el texto al
 * campo para que el medico no lo pierda.
 *
 * A diferencia del lado del paciente, aqui NO hay respuesta automatica: el
 * medico escribe de verdad, y simular un interlocutor seria enganoso.
 *
 * No conoce Compose ni recursos: solo el contrato [ChatRepositorio].
 */
class ChatMedicoViewModel(
    private val repositorio: ChatRepositorio,
    private val idConversacion: String,
    nombrePaciente: String,
    riesgoPaciente: RiesgoPaciente,
    private val reloj: RelojSalud = relojDelSistema(),
    /** Null con el backend apagado o sin traduccion: la accion no se ofrece. */
    private val traduccion: TraduccionRepositorio? = null,
    /** Idioma de quien lee, no del mensaje: a eso se traduce. */
    private val idiomaDeLectura: String = IDIOMA_POR_DEFECTO,
    /** Donde vive "traducir siempre"; null en modo local o en las pruebas. */
    private val preferencias: PreferenciasDeLectura? = null,
) : ViewModel() {

    private val _estado = MutableStateFlow(
        ChatMedicoUiState(
            idConversacion = idConversacion,
            nombrePaciente = nombrePaciente,
            riesgoPaciente = riesgoPaciente,
            puedeTraducir = traduccion != null,
        ),
    )
    val estado: StateFlow<ChatMedicoUiState> = _estado.asStateFlow()

    /**
     * Guardia de reentrada propio y no `historial == Cargando`: `Cargando` es
     * tambien el estado inicial, asi que mirarlo bloquearia la primera carga.
     */
    private var consultaEnCurso = false

    /** Las traducciones se piden de una en una: el modelo atiende una a la vez. */
    private val candadoDeTraduccion = Mutex()

    init {
        // Atada al ciclo de vida del ViewModel, no a un `LaunchedEffect`: ver
        // la nota en `DescubrimientoMedicoViewModel`.
        cargar()
        if (traduccion != null) {
            viewModelScope.launch {
                preferencias?.traducirSiempre?.collect { activa ->
                    _estado.update {
                        it.copy(
                            traduccionAutomatica = activa,
                            traducciones = it.traducciones.mostrandoOriginales(!activa),
                        )
                    }
                    if (activa) traducirLoQueFalta()
                }
            }
        }
    }

    /**
     * Traduce los mensajes del paciente que aun no tienen traduccion, de uno en
     * uno. Se llama al encender el interruptor y cuando llegan mensajes nuevos.
     */
    private fun traducirLoQueFalta() {
        if (!_estado.value.traduccionAutomatica) return
        _estado.value.mensajes
            .filter { !it.esPropio && it.texto.isNotBlank() }
            .filter { it.idMensaje !in _estado.value.traducciones }
            .forEach { traducir(it.idMensaje) }
    }

    /** Enciende o apaga la traduccion automatica; queda guardada en este equipo. */
    fun cambiarTraduccionAutomatica(activa: Boolean) {
        val almacen = preferencias ?: return
        viewModelScope.launch { almacen.cambiarTraducirSiempre(activa) }
    }

    fun cargar() {
        if (consultaEnCurso) return
        consultaEnCurso = true
        _estado.update { it.copy(historial = HistorialChatUiState.Cargando) }
        viewModelScope.launch {
            try {
                val resultado = ejecutarSeguro { repositorio.obtenerHistorial(idConversacion) }
                _estado.update { previo ->
                    resultado.fold(
                        onSuccess = { mensajes ->
                            previo.copy(
                                historial = HistorialChatUiState.ConMensajes(
                                    mensajes.map { it.aVisible() },
                                ),
                            )
                        },
                        onFailure = {
                            previo.copy(
                                historial = HistorialChatUiState.Error(ErrorChatMedico.SIN_CONEXION),
                            )
                        },
                    )
                }
                resultado.getOrNull()?.forEach { mensaje ->
                    if (mensaje.autor == AutorMensaje.PACIENTE && esMensajeLargo(mensaje.texto)) {
                        solicitarResumen(mensaje.idMensaje, mensaje.texto)
                    }
                }
                traducirLoQueFalta()
            } finally {
                consultaEnCurso = false
            }
        }
    }

    /**
     * Mantiene la conversacion al dia mientras la pantalla este visible (la
     * llama la Vista y se cancela al salir), igual que en el chat del
     * paciente: los mensajes llegan sin tener que salir y volver a entrar.
     */
    suspend fun mantenerAlDia() {
        refrescar()
        repositorio.cambiosEn(idConversacion).collect { refrescar() }
    }

    private var refrescoEnCurso = false

    /** Historial nuevo sin esqueleto de carga; lo que aun no confirma el servidor se queda al final. */
    private suspend fun refrescar() {
        if (refrescoEnCurso || consultaEnCurso) return
        refrescoEnCurso = true
        try {
            val delServidor = ejecutarSeguro { repositorio.obtenerHistorial(idConversacion) }.getOrNull() ?: return
            var llegoAlgoNuevo = false
            _estado.update { previo ->
                val actuales = (previo.historial as? HistorialChatUiState.ConMensajes)?.mensajes.orEmpty()
                val conocidos = actuales.mapTo(HashSet()) { it.idMensaje }
                llegoAlgoNuevo = delServidor.any { it.idMensaje !in conocidos && it.autor != AutorMensaje.MEDICO }
                val pendientes = actuales.filter { it.idMensaje.startsWith(PREFIJO_ID_PROVISIONAL) }
                val alDia = delServidor.map { it.aVisible() } + pendientes
                if (previo.historial is HistorialChatUiState.ConMensajes && alDia == actuales) {
                    previo
                } else {
                    previo.copy(historial = HistorialChatUiState.ConMensajes(alDia))
                }
            }
            delServidor.forEach { mensaje ->
                if (mensaje.autor == AutorMensaje.PACIENTE && esMensajeLargo(mensaje.texto)) {
                    solicitarResumen(mensaje.idMensaje, mensaje.texto)
                }
            }
            // Con la traduccion automatica encendida, lo nuevo del paciente
            // llega ya traducido sin que el medico lo pida.
            traducirLoQueFalta()
            if (llegoAlgoNuevo) {
                ejecutarSeguro { Result.success(repositorio.marcarConversacionLeida(idConversacion)) }
            }
        } finally {
            refrescoEnCurso = false
        }
    }

    /**
     * Alterna entre el resumen de IA y el mensaje tal como lo escribio el
     * paciente, bajo la misma tarjeta.
     */
    fun alternarOriginal(idMensaje: String) {
        _estado.update { previo ->
            previo.copy(
                originalExpandido = if (idMensaje in previo.originalExpandido) {
                    previo.originalExpandido - idMensaje
                } else {
                    previo.originalExpandido + idMensaje
                },
            )
        }
    }

    /**
     * Pide el resumen clinico de un mensaje del paciente. Se llama sola desde
     * [cargar] para los mensajes que ya califican, pero no vuelve a pedirse dos
     * veces para el mismo mensaje: un resumen ya en curso, listo, o ya fallido
     * no cambia con un segundo intento automatico.
     */
    private fun solicitarResumen(idMensaje: String, texto: String) {
        if (idMensaje in _estado.value.resumenesIa) return
        _estado.update { it.copy(resumenesIa = it.resumenesIa + (idMensaje to EstadoResumenIa.Cargando)) }
        viewModelScope.launch {
            val resultado = ejecutarSeguro { repositorio.obtenerResumenClinico(idMensaje) }
            val nuevoEstado = resultado.fold(
                onSuccess = { resumen -> EstadoResumenIa.Disponible(resumen) },
                onFailure = { EstadoResumenIa.Fallido(texto) },
            )
            _estado.update { it.copy(resumenesIa = it.resumenesIa + (idMensaje to nuevoEstado)) }
        }
    }

    /**
     * Umbral a partir del cual un mensaje del paciente se ofrece resumido en
     * vez de leerse entero de corrido. Cuarenta palabras es, mas o menos, un
     * parrafo corto -- suficiente para que un relato de sintomas empiece a
     * costar barrer con la vista, pero no tan bajo como para resumir un simple
     * "Sigo con dolor de cabeza".
     */
    private fun esMensajeLargo(texto: String): Boolean =
        texto.trim().split(EXPRESION_ESPACIOS).count { it.isNotBlank() } > UMBRAL_PALABRAS_RESUMEN_IA

    /**
     * Traduce un mensaje al idioma del medico. Un paciente que escribe en otro
     * idioma no deberia esperar a que alguien traduzca por fuera; se pide a
     * peticion, porque cada traduccion tarda segundos.
     */
    fun traducir(idMensaje: String) {
        val repositorioTraduccion = traduccion ?: return
        val texto = _estado.value.mensajes.firstOrNull { it.idMensaje == idMensaje }?.texto ?: return
        if (texto.isBlank() || _estado.value.traducciones[idMensaje] == EstadoTraduccion.Cargando) return

        _estado.update { it.copy(traducciones = it.traducciones + (idMensaje to EstadoTraduccion.Cargando)) }
        viewModelScope.launch {
            val resultado = candadoDeTraduccion.withLock {
                traducirParaLaVista(repositorioTraduccion, texto, idiomaDeLectura)
            }
            _estado.update { it.copy(traducciones = it.traducciones + (idMensaje to resultado)) }
        }
    }

    /** Cruza entre la traduccion y el mensaje tal como lo escribio el paciente. */
    fun alternarTraduccion(idMensaje: String) {
        _estado.update { it.copy(traducciones = it.traducciones.alternando(idMensaje)) }
    }

    fun actualizarTexto(valor: String) {
        _estado.update { it.copy(textoEnCurso = valor, errorEnvio = false) }
    }

    /** Una respuesta rapida no se envia sola: llena el campo para poder editarla antes de mandarla. */
    fun usarRespuestaRapida(texto: String) {
        _estado.update { it.copy(textoEnCurso = texto, errorEnvio = false) }
    }

    /**
     * El adjunto llega ya resuelto desde el selector (galeria, archivo o
     * escaner). Uno a la vez, igual que en el chat del paciente.
     */
    fun adjuntar(adjunto: Adjunto) {
        _estado.update { it.copy(adjuntoEnCurso = adjunto, errorEnvio = false) }
    }

    fun quitarAdjunto() {
        _estado.update { it.copy(adjuntoEnCurso = null) }
    }

    fun enviarMensaje() {
        val actual = _estado.value
        val texto = actual.textoEnCurso.trim()
        val adjunto = actual.adjuntoEnCurso
        if (texto.isBlank() && adjunto == null) return
        // Sin historial descargado no hay a que anexar: se evita perder el texto.
        val mensajesActuales = (actual.historial as? HistorialChatUiState.ConMensajes)?.mensajes
            ?: return

        val instante = reloj.instanteActual()
        val provisional = MensajeChat(
            idMensaje = "$PREFIJO_ID_PROVISIONAL${mensajesActuales.size}_$instante",
            autor = AutorMensaje.MEDICO,
            texto = texto,
            instante = instante,
            adjunto = adjunto,
        ).aVisible()

        _estado.update {
            it.copy(
                historial = HistorialChatUiState.ConMensajes(mensajesActuales + provisional),
                textoEnCurso = "",
                adjuntoEnCurso = null,
                errorEnvio = false,
            )
        }

        viewModelScope.launch {
            val resultado = ejecutarSeguro {
                repositorio.enviarMensaje(
                    idConversacion = idConversacion,
                    texto = texto,
                    instante = instante,
                    autor = AutorMensaje.MEDICO,
                    adjunto = adjunto,
                )
            }
            resultado.fold(
                onSuccess = { confirmado ->
                    _estado.update { previo ->
                        previo.conMensajes { mensajes ->
                            if (mensajes.any { it.idMensaje == confirmado.idMensaje }) {
                                mensajes.filterNot { it.idMensaje == provisional.idMensaje }
                            } else {
                                mensajes.map {
                                    if (it.idMensaje == provisional.idMensaje) confirmado.aVisible() else it
                                }
                            }
                        }
                    }
                },
                onFailure = {
                    _estado.update { previo ->
                        previo
                            .conMensajes { mensajes ->
                                mensajes.filterNot { it.idMensaje == provisional.idMensaje }
                            }
                            // Texto y adjunto vuelven al campo: perder un archivo
                            // ya elegido por un fallo de red obligaria a buscarlo.
                            .copy(errorEnvio = true, textoEnCurso = texto, adjuntoEnCurso = adjunto)
                    }
                },
            )
        }
    }

    fun descartarErrorEnvio() {
        _estado.update { it.copy(errorEnvio = false) }
    }

    /**
     * Traduce el mensaje de dominio al que pinta la Vista: resuelve el lado de
     * la burbuja y la hora local, para que la Vista no toque zonas horarias.
     */
    private fun MensajeChat.aVisible() = MensajeVisibleChat(
        idMensaje = idMensaje,
        texto = texto,
        autor = autor,
        esPropio = autor == AutorMensaje.MEDICO,
        horaLocal = reloj.horaLocal(instante),
        adjunto = adjunto,
        fechaLocal = reloj.fechaLocal(instante),
    )

    /** Reescribe la lista solo si el historial sigue descargado. */
    private fun ChatMedicoUiState.conMensajes(
        bloque: (List<MensajeVisibleChat>) -> List<MensajeVisibleChat>,
    ): ChatMedicoUiState {
        val actuales = (historial as? HistorialChatUiState.ConMensajes)?.mensajes ?: return this
        return copy(historial = HistorialChatUiState.ConMensajes(bloque(actuales)))
    }

    private companion object {
        const val PREFIJO_ID_PROVISIONAL = "local_medico_"
        const val IDIOMA_POR_DEFECTO = "es"
        const val UMBRAL_PALABRAS_RESUMEN_IA = 40
        val EXPRESION_ESPACIOS = Regex("\\s+")
    }
}
