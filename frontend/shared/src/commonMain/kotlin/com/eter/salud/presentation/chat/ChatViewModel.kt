package com.eter.salud.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eter.salud.domain.model.Adjunto
import com.eter.salud.domain.model.AutorMensaje
import com.eter.salud.domain.model.MensajeChat
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
 * ViewModel del chat de orientacion de primera vista.
 *
 * Dos decisiones de diseno responden al contexto clinico:
 *  - El envio es optimista: la burbuja del paciente aparece al instante y se
 *    reconcilia con el id definitivo del backend, o se retira si el envio
 *    falla. Una app de salud no debe sentirse mas lenta que un chat comun.
 *  - Solo el primer mensaje del paciente en la conversacion dispara la
 *    respuesta automatica de cortesia. No es un bot conversando: es un unico
 *    acuse de recibo del sistema, y fingir mas seria enganoso.
 *
 * No conoce Compose, ni recursos, ni el hardware: solo el contrato
 * [ChatRepositorio].
 */
class ChatViewModel(
    private val repositorio: ChatRepositorio,
    private val idConversacion: String,
    nombreMedico: String,
    private val reloj: RelojSalud = relojDelSistema(),
    /** Null con el backend apagado o sin traduccion: la accion no se ofrece. */
    private val traduccion: TraduccionRepositorio? = null,
    /** Idioma de quien lee, no del mensaje: a eso se traduce. */
    private val idiomaDeLectura: String = IDIOMA_POR_DEFECTO,
    /** Donde vive "traducir siempre"; null en modo local o en las pruebas. */
    private val preferencias: PreferenciasDeLectura? = null,
) : ViewModel() {

    private val _estado = MutableStateFlow(
        ChatUiState(
            idConversacion = idConversacion,
            nombreMedico = nombreMedico,
            puedeTraducir = traduccion != null,
        ),
    )
    val estado: StateFlow<ChatUiState> = _estado.asStateFlow()

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
     * Traduce los mensajes del medico que aun no tienen traduccion. Se llama al
     * encender el interruptor y cada vez que llegan mensajes nuevos, y va de
     * uno en uno: diez peticiones a la vez no llegarian antes, solo saturarian
     * al modelo.
     */
    private fun traducirLoQueFalta() {
        if (!_estado.value.traduccionAutomatica) return
        _estado.value.mensajes
            .filter { it.autor != AutorMensaje.PACIENTE && it.texto.isNotBlank() }
            .filter { it.idMensaje !in _estado.value.traducciones }
            .forEach { traducir(it.idMensaje) }
    }

    /** Enciende o apaga la traduccion automatica; queda guardada en el telefono. */
    fun cambiarTraduccionAutomatica(activa: Boolean) {
        val almacen = preferencias ?: return
        viewModelScope.launch { almacen.cambiarTraducirSiempre(activa) }
    }

    fun cargar() {
        if (_estado.value.cargando) return
        _estado.update { it.copy(cargando = true, errorCarga = false) }
        viewModelScope.launch {
            val resultado = ejecutarSeguro { repositorio.obtenerHistorial(idConversacion) }
            _estado.update { previo ->
                resultado.fold(
                    onSuccess = { mensajes -> previo.copy(cargando = false, mensajes = mensajes) },
                    onFailure = { previo.copy(cargando = false, errorCarga = true) },
                )
            }
            traducirLoQueFalta()
            // Abrir la conversacion ES leerla: el punto rojo de la barra baja
            // aqui, sin pedirle al paciente ningun gesto adicional.
            if (resultado.isSuccess) {
                ejecutarSeguro { Result.success(repositorio.marcarConversacionLeida(idConversacion)) }
            }
        }
    }

    /**
     * Mantiene la conversacion al dia mientras la pantalla este visible: la
     * llama la Vista y se cancela sola al salir. Va aparte de [cargar] a
     * proposito: si escuchara desde `init`, el ViewModel -- que sigue vivo con
     * el chat cerrado -- marcaria como leidos mensajes que nadie esta viendo.
     */
    suspend fun mantenerAlDia() {
        refrescar()
        repositorio.cambiosEn(idConversacion).collect { refrescar() }
    }

    /**
     * Vuelve a pedir el historial sin mostrar "cargando" y sin tocar lo que el
     * paciente esta escribiendo. Los mensajes suyos que aun no confirma el
     * servidor se quedan al final.
     */
    private suspend fun refrescar() {
        if (refrescoEnCurso || _estado.value.cargando) return
        refrescoEnCurso = true
        try {
            val delServidor = ejecutarSeguro { repositorio.obtenerHistorial(idConversacion) }.getOrNull() ?: return
            var llegoAlgoNuevo = false
            _estado.update { previo ->
                val conocidos = previo.mensajes.mapTo(HashSet()) { it.idMensaje }
                llegoAlgoNuevo = delServidor.any { it.idMensaje !in conocidos && it.autor != AutorMensaje.PACIENTE }
                val pendientes = previo.mensajes.filter { it.idMensaje.startsWith(PREFIJO_ID_PROVISIONAL) }
                val alDia = delServidor + pendientes
                if (alDia == previo.mensajes && !previo.errorCarga) previo else previo.copy(mensajes = alDia, errorCarga = false)
            }
            // Con la traduccion automatica encendida, lo que acaba de llegar
            // se traduce sin que el paciente tenga que pedirlo.
            traducirLoQueFalta()
            // El chat esta a la vista: lo que acaba de llegar ya se leyo.
            if (llegoAlgoNuevo) {
                ejecutarSeguro { Result.success(repositorio.marcarConversacionLeida(idConversacion)) }
            }
        } finally {
            refrescoEnCurso = false
        }
    }

    private var refrescoEnCurso = false

    fun actualizarTexto(valor: String) {
        _estado.update { it.copy(textoEnCurso = valor, errorEnvio = false) }
    }

    /**
     * Deja lista una foto, un archivo o una pagina escaneada para el proximo
     * envio. Quien la consiguio (galeria, selector de archivos, camara del
     * escaner) ya la entrega como [Adjunto] resuelto -- copiada al
     * almacenamiento propio de la app -- porque de ahi para abajo los tres
     * casos son identicos.
     *
     * Solo uno a la vez: elegir un segundo adjunto reemplaza al primero, igual
     * que en cualquier chat comun. Obligar a enviar o quitar el primero antes
     * de poder elegir otro seria un tramite que ningun chat le pide al usuario.
     */
    fun adjuntar(adjunto: Adjunto) {
        _estado.update { it.copy(adjuntoEnCurso = adjunto, errorEnvio = false) }
    }

    fun quitarAdjunto() {
        _estado.update { it.copy(adjuntoEnCurso = null) }
    }

    /**
     * Envia el mensaje en curso (texto, adjunto, o los dos). La burbuja aparece
     * de inmediato con un id provisional; cuando el backend confirma, ese id se
     * reemplaza por el definitivo, y si falla, el mensaje se retira y tanto el
     * texto como el adjunto vuelven al campo -- perder un archivo ya elegido
     * por un fallo de red obligaria a buscarlo otra vez.
     */
    fun enviarMensaje() {
        val actual = _estado.value
        val texto = actual.textoEnCurso.trim()
        val adjunto = actual.adjuntoEnCurso
        if (texto.isBlank() && adjunto == null) return

        val esPrimerMensajeDelPaciente = actual.mensajes.none { it.autor == AutorMensaje.PACIENTE }
        val instante = reloj.instanteActual()
        val provisional = MensajeChat(
            idMensaje = "$PREFIJO_ID_PROVISIONAL${actual.mensajes.size}_$instante",
            autor = AutorMensaje.PACIENTE,
            texto = texto,
            instante = instante,
            adjunto = adjunto,
        )

        _estado.update {
            it.copy(
                mensajes = it.mensajes + provisional,
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
                    autor = AutorMensaje.PACIENTE,
                    adjunto = adjunto,
                )
            }
            resultado.fold(
                onSuccess = { confirmado ->
                    _estado.update { previo ->
                        previo.copy(mensajes = previo.mensajes.reemplazarPor(provisional.idMensaje, confirmado))
                    }
                    if (esPrimerMensajeDelPaciente) enviarRespuestaAutomatica()
                },
                onFailure = {
                    _estado.update { previo ->
                        previo.copy(
                            mensajes = previo.mensajes.filterNot { m -> m.idMensaje == provisional.idMensaje },
                            errorEnvio = true,
                            textoEnCurso = texto,
                            adjuntoEnCurso = adjunto,
                        )
                    }
                },
            )
        }
    }

    /**
     * Traduce un mensaje al idioma de quien lee. Se pide mensaje por mensaje y
     * a peticion: traducir toda la conversacion de oficio costaria diez
     * segundos por burbuja para textos que quiza ya se entienden.
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

    /** Cruza entre la traduccion y el mensaje tal como lo escribio su autor. */
    fun alternarTraduccion(idMensaje: String) {
        _estado.update { it.copy(traducciones = it.traducciones.alternando(idMensaje)) }
    }

    fun descartarErrorEnvio() {
        _estado.update { it.copy(errorEnvio = false) }
    }

    private suspend fun enviarRespuestaAutomatica() {
        _estado.update { it.copy(medicoEscribiendo = true) }
        val resultado = ejecutarSeguro { repositorio.obtenerRespuestaAutomatica(idConversacion, reloj.instanteActual()) }
        _estado.update { previo ->
            resultado.fold(
                onSuccess = { respuesta ->
                    previo.copy(medicoEscribiendo = false, mensajes = previo.mensajes + respuesta)
                },
                onFailure = { previo.copy(medicoEscribiendo = false) },
            )
        }
    }

    /** Si un refresco ya trajo el definitivo, solo se retira el provisional: nunca dos burbujas iguales. */
    private fun List<MensajeChat>.reemplazarPor(idProvisional: String, definitivo: MensajeChat) =
        if (any { it.idMensaje == definitivo.idMensaje }) {
            filterNot { it.idMensaje == idProvisional }
        } else {
            map { if (it.idMensaje == idProvisional) definitivo else it }
        }

    private companion object {
        const val PREFIJO_ID_PROVISIONAL = "local_"
        const val IDIOMA_POR_DEFECTO = "es"
    }
}
