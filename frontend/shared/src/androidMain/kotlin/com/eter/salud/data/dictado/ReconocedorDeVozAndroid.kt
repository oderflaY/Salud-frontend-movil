package com.eter.salud.data.dictado

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.eter.salud.domain.dictado.ErrorDictado
import com.eter.salud.domain.dictado.EscuchaDeDictado
import com.eter.salud.domain.dictado.ReconocedorDeVoz
import java.util.Locale

/**
 * [SpeechRecognizer] del sistema, afinado para dictar rápido.
 *
 *  - **En el dispositivo** cuando hay modelo (Android 12+,
 *    `createOnDeviceSpeechRecognizer`): sin ida y vuelta a un servidor, el texto
 *    aparece mientras se habla. Si el modelo local no tiene el idioma, se pasa
 *    una sola vez al reconocedor normal. En versiones anteriores se pide
 *    `EXTRA_PREFER_OFFLINE`.
 *  - **Resultados parciales** activados: la pantalla no espera a la pausa.
 *  - **Una instancia reutilizada**: crear el reconocedor cuesta; se crea al
 *    entrar a la pantalla y cada nuevo segmento solo vuelve a `startListening`.
 *  - **Continuo**: el sistema corta en cada pausa (o con "no te escuché"); si la
 *    persona no pidió detener, se vuelve a escuchar sin avisar.
 *
 * Todo ocurre en el hilo principal: `SpeechRecognizer` lo exige.
 */
internal class ReconocedorDeVozAndroid(
    private val contexto: Context,
) : ReconocedorDeVoz {

    private var reconocedor: SpeechRecognizer? = null
    private var usaModeloLocal = false
    private var escucha: EscuchaDeDictado? = null

    /** La persona quiere seguir dictando: una pausa no termina el dictado. */
    private var continuo = false

    private val hiloPrincipal = Handler(Looper.getMainLooper())
    private var reintentosOcupado = 0

    /** Hay texto parcial sin confirmar que se entrega si el sistema corta sin resultado final. */
    private var ultimoParcial = ""

    override val disponible: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(contexto) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(contexto))

    private val intencion: Intent by lazy {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Pausas cortas para pensar no cierran el segmento.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCIO_FIN_MS)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCIO_POSIBLE_FIN_MS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_LATENCY)
            }
        }
    }

    override fun iniciar(escucha: EscuchaDeDictado) {
        this.escucha = escucha
        continuo = true
        ultimoParcial = ""
        escuchar()
    }

    override fun detener() {
        continuo = false
        val activo = reconocedor
        if (activo == null) terminar() else activo.stopListening()
    }

    override fun cancelar() {
        continuo = false
        escucha = null
        hiloPrincipal.removeCallbacksAndMessages(null)
        reconocedor?.cancel()
    }

    /** Libera el servicio de reconocimiento al salir de la pantalla. */
    fun destruir() {
        cancelar()
        reconocedor?.destroy()
        reconocedor = null
    }

    private fun escuchar() {
        val instancia = reconocedor ?: crear(preferirLocal = true).also { reconocedor = it }
        instancia.startListening(intencion)
    }

    private fun crear(preferirLocal: Boolean): SpeechRecognizer {
        usaModeloLocal = preferirLocal &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(contexto)
        val nuevo = if (usaModeloLocal) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(contexto)
        } else {
            SpeechRecognizer.createSpeechRecognizer(contexto)
        }
        nuevo.setRecognitionListener(oyente)
        return nuevo
    }

    /** El modelo local no trae este idioma: se cambia al reconocedor normal y se sigue. */
    private fun pasarAReconocedorNormal() {
        reconocedor?.destroy()
        reconocedor = crear(preferirLocal = false)
        escuchar()
    }

    private fun terminar() {
        val destino = escucha ?: return
        escucha = null
        continuo = false
        destino.alTerminar()
    }

    private fun fallar(error: ErrorDictado) {
        val destino = escucha ?: return
        escucha = null
        continuo = false
        destino.alFallar(error)
    }

    private fun Bundle?.primerTexto(): String =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

    private val oyente = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            reintentosOcupado = 0
            escucha?.alListo()
        }

        override fun onRmsChanged(rmsdB: Float) {
            // rmsdB va de ~-2 (silencio) a ~10 (voz fuerte).
            escucha?.alNivel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val texto = partialResults.primerTexto()
            if (texto.isNotBlank()) {
                ultimoParcial = texto
                escucha?.alParcial(texto)
            }
        }

        override fun onResults(results: Bundle?) {
            val texto = results.primerTexto().ifBlank { ultimoParcial }
            ultimoParcial = ""
            if (texto.isNotBlank()) escucha?.alSegmento(texto)
            if (continuo) escuchar() else terminar()
        }

        override fun onError(error: Int) {
            val pendiente = ultimoParcial
            ultimoParcial = ""
            when (error) {
                // Silencio o "no te entendí": en dictado continuo solo es una pausa.
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    if (pendiente.isNotBlank()) escucha?.alSegmento(pendiente)
                    if (continuo) escuchar() else terminar()
                }
                // `stopListening`/`cancel` mientras se procesaba: no es un fallo.
                SpeechRecognizer.ERROR_CLIENT -> {
                    if (pendiente.isNotBlank()) escucha?.alSegmento(pendiente)
                    terminar()
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> fallar(ErrorDictado.SIN_PERMISO)
                // Algunos equipos siguen "ocupados" un instante al encadenar segmentos.
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                    if (continuo && escucha != null && reintentosOcupado++ < MAXIMO_REINTENTOS_OCUPADO) {
                        hiloPrincipal.postDelayed({ if (continuo) escuchar() }, REINTENTO_OCUPADO_MS)
                    } else {
                        fallar(ErrorDictado.MICROFONO_OCUPADO)
                    }
                SpeechRecognizer.ERROR_AUDIO -> fallar(ErrorDictado.MICROFONO_OCUPADO)
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER, ERROR_SERVER_DISCONNECTED,
                -> fallar(ErrorDictado.SIN_CONEXION)
                ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE ->
                    if (usaModeloLocal && continuo) pasarAReconocedorNormal() else fallar(ErrorDictado.NO_DISPONIBLE)
                else -> fallar(ErrorDictado.DESCONOCIDO)
            }
        }

        override fun onBeginningOfSpeech() = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private companion object {
        const val SILENCIO_FIN_MS = 2500L
        const val SILENCIO_POSIBLE_FIN_MS = 2000L
        const val REINTENTO_OCUPADO_MS = 150L
        const val MAXIMO_REINTENTOS_OCUPADO = 5

        // Constantes de API 31+: se usan por valor para compilar contra minSdk 24.
        const val ERROR_SERVER_DISCONNECTED = 11
        const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        const val ERROR_LANGUAGE_UNAVAILABLE = 13
    }
}
