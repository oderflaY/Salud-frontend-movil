package com.eter.salud.data.dictado

import com.eter.salud.domain.dictado.ErrorDictado
import com.eter.salud.domain.dictado.EscuchaDeDictado
import com.eter.salud.domain.dictado.ReconocedorDeVoz
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDuckOthers
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVAudioSessionModeMeasurement
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.setActive
import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages
import platform.Speech.SFSpeechAudioBufferRecognitionRequest
import platform.Speech.SFSpeechRecognitionTask
import platform.Speech.SFSpeechRecognizer
import platform.Speech.SFSpeechRecognizerAuthorizationStatus
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.math.sqrt

/**
 * Dictado con `SFSpeechRecognizer` + `AVAudioEngine`.
 *
 *  - **En el dispositivo** (`requiresOnDeviceRecognition`) cuando el iPhone
 *    tiene el modelo del idioma: más rápido y la voz no sale del teléfono.
 *  - **Parciales en vivo** (`shouldReportPartialResults`).
 *  - **Continuo**: iOS entrega un resultado final por tarea (y corta las tareas
 *    largas); si la persona sigue dictando se abre otra tarea sobre el mismo
 *    audio, sin cerrar el micrófono.
 *
 * Las respuestas de Speech llegan en hilos arbitrarios: todo se reenvía al hilo
 * principal antes de tocar la escucha.
 */
@OptIn(ExperimentalForeignApi::class)
internal class ReconocedorDeVozIos : ReconocedorDeVoz {

    private val reconocedor: SFSpeechRecognizer? = SFSpeechRecognizer(locale = NSLocale(localeIdentifier = idiomaPreferido()))
    private val motor = AVAudioEngine()
    private var solicitud: SFSpeechAudioBufferRecognitionRequest? = null
    private var tarea: SFSpeechRecognitionTask? = null
    private var escucha: EscuchaDeDictado? = null
    private var continuo = false
    private var ultimoParcial = ""
    private var tapInstalado = false

    override val disponible: Boolean
        get() = reconocedor?.isAvailable() == true

    override fun iniciar(escucha: EscuchaDeDictado) {
        this.escucha = escucha
        continuo = true
        SFSpeechRecognizer.requestAuthorization { estado ->
            enPrincipal {
                if (estado != SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized) {
                    fallar(ErrorDictado.SIN_PERMISO)
                } else {
                    AVAudioSession.sharedInstance().requestRecordPermission { concedido ->
                        enPrincipal { if (concedido) arrancar() else fallar(ErrorDictado.SIN_PERMISO) }
                    }
                }
            }
        }
    }

    override fun detener() {
        continuo = false
        if (tarea == null) {
            terminar()
            return
        }
        detenerMotor()
        // El resultado final llega por la tarea y ahí se llama a terminar().
        solicitud?.endAudio()
    }

    override fun cancelar() {
        continuo = false
        escucha = null
        tarea?.cancel()
        liberar()
    }

    private fun arrancar() {
        val rec = reconocedor
        if (escucha == null) return
        if (rec == null || !rec.isAvailable()) {
            fallar(ErrorDictado.NO_DISPONIBLE)
            return
        }

        val sesion = AVAudioSession.sharedInstance()
        sesion.setCategory(AVAudioSessionCategoryRecord, mode = AVAudioSessionModeMeasurement, options = AVAudioSessionCategoryOptionDuckOthers, error = null)
        sesion.setActive(true, withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, error = null)

        nuevaTarea(rec)

        val entrada = motor.inputNode
        if (!tapInstalado) {
            entrada.installTapOnBus(0u, bufferSize = TAMANO_BUFFER, format = entrada.outputFormatForBus(0u)) { buffer, _ ->
                if (buffer != null) {
                    solicitud?.appendAudioPCMBuffer(buffer)
                    val nivel = nivelDe(buffer)
                    enPrincipal { escucha?.alNivel(nivel) }
                }
            }
            tapInstalado = true
        }
        motor.prepare()
        if (!motor.startAndReturnError(null)) {
            fallar(ErrorDictado.MICROFONO_OCUPADO)
            return
        }
        escucha?.alListo()
    }

    private fun nuevaTarea(rec: SFSpeechRecognizer) {
        val nueva = SFSpeechAudioBufferRecognitionRequest()
        nueva.shouldReportPartialResults = true
        if (rec.supportsOnDeviceRecognition) nueva.requiresOnDeviceRecognition = true
        solicitud = nueva
        ultimoParcial = ""
        tarea = rec.recognitionTaskWithRequest(nueva) { resultado, error ->
            enPrincipal {
                // Una tarea anterior que responde tarde no toca el dictado actual.
                if (solicitud !== nueva) return@enPrincipal
                if (resultado != null) {
                    val texto = resultado.bestTranscription.formattedString
                    if (resultado.isFinal()) {
                        ultimoParcial = ""
                        if (texto.isNotBlank()) escucha?.alSegmento(texto)
                        if (continuo) nuevaTarea(rec) else terminar()
                    } else {
                        ultimoParcial = texto
                        escucha?.alParcial(texto)
                    }
                } else if (error != null) {
                    val pendiente = ultimoParcial
                    ultimoParcial = ""
                    if (pendiente.isNotBlank()) escucha?.alSegmento(pendiente)
                    when {
                        !continuo || error.code in CODIGOS_CANCELADO -> terminar()
                        error.code in CODIGOS_SILENCIO -> nuevaTarea(rec)
                        nueva.requiresOnDeviceRecognition -> fallar(ErrorDictado.NO_DISPONIBLE)
                        else -> fallar(ErrorDictado.SIN_CONEXION)
                    }
                }
            }
        }
    }

    private fun nivelDe(buffer: AVAudioPCMBuffer): Float {
        val canales = buffer.floatChannelData ?: return 0f
        val datos = canales[0] ?: return 0f
        val muestras = buffer.frameLength.toInt()
        if (muestras == 0) return 0f
        var suma = 0.0
        var contadas = 0
        var i = 0
        // Una de cada cuatro muestras basta para animar el botón.
        while (i < muestras) {
            val v = datos[i].toDouble()
            suma += v * v
            contadas++
            i += 4
        }
        return (sqrt(suma / contadas) * AMPLIFICACION_NIVEL).toFloat().coerceIn(0f, 1f)
    }

    private fun detenerMotor() {
        if (motor.isRunning()) motor.stop()
        if (tapInstalado) {
            motor.inputNode.removeTapOnBus(0u)
            tapInstalado = false
        }
    }

    private fun liberar() {
        detenerMotor()
        tarea = null
        solicitud = null
        AVAudioSession.sharedInstance().setActive(false, withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, error = null)
    }

    private fun terminar() {
        liberar()
        val destino = escucha ?: return
        escucha = null
        destino.alTerminar()
    }

    private fun fallar(error: ErrorDictado) {
        continuo = false
        liberar()
        val destino = escucha ?: return
        escucha = null
        destino.alFallar(error)
    }

    private fun enPrincipal(bloque: () -> Unit) {
        dispatch_async(dispatch_get_main_queue()) { bloque() }
    }

    private companion object {
        const val TAMANO_BUFFER: UInt = 1024u
        const val AMPLIFICACION_NIVEL = 8.0

        /** kAFAssistantErrorDomain: sin voz detectada / reintentar. */
        val CODIGOS_SILENCIO = setOf(1110L, 203L, 1101L)

        /** Tarea cancelada (por `cancel` o `endAudio` sin audio). */
        val CODIGOS_CANCELADO = setOf(216L, 301L)

        fun idiomaPreferido(): String = NSLocale.preferredLanguages.firstOrNull() as? String ?: "es-MX"
    }
}
