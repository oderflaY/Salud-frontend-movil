package com.eter.salud.domain.dictado

import androidx.compose.runtime.Composable

/** Por que se detuvo el dictado sin que la persona lo pidiera. */
enum class ErrorDictado {
    /** No hay permiso de micrófono o de reconocimiento de voz. */
    SIN_PERMISO,

    /** El teléfono no tiene reconocedor de voz para este idioma. */
    NO_DISPONIBLE,

    /** Sin modelo en el dispositivo, el reconocedor necesita internet y no hay. */
    SIN_CONEXION,

    /** Otra app está usando el micrófono. */
    MICROFONO_OCUPADO,

    DESCONOCIDO,
}

/**
 * Lo que el reconocedor avisa mientras escucha. Todas las llamadas llegan en el
 * hilo principal.
 */
interface EscuchaDeDictado {
    /** El micrófono ya está abierto: se puede hablar. */
    fun alListo()

    /** Transcripción provisional del segmento en curso; se reemplaza en cada llamada. */
    fun alParcial(texto: String)

    /** Segmento terminado (tras una pausa). Ya no cambia. */
    fun alSegmento(texto: String)

    /** Volumen de la voz de 0 a 1, para animar el botón. */
    fun alNivel(nivel: Float)

    /** El reconocedor se detuvo del todo tras [ReconocedorDeVoz.detener]. */
    fun alTerminar()

    /** Se detuvo por un error. Lo dictado hasta ahí se conserva. */
    fun alFallar(error: ErrorDictado)
}

/**
 * Micrófono con reconocimiento de voz del teléfono.
 *
 * Interfaz de dominio, igual que [com.eter.salud.domain.nfc.LectorTarjetaNfc]:
 * el controlador se prueba con un doble y cada plataforma usa su API nativa.
 *
 * ## Velocidad y privacidad
 *
 * Las implementaciones reconocen EN EL TELÉFONO cuando el sistema lo permite
 * (Android 12+ con modelo local, iOS 13+ con `requiresOnDeviceRecognition`):
 * el texto aparece mientras se habla, sin ida y vuelta a un servidor, y la voz
 * de un paciente describiendo sus síntomas no sale del dispositivo.
 *
 * ## Dictado continuo
 *
 * El reconocedor del sistema corta en cada pausa. La implementación vuelve a
 * escuchar sola hasta que se llama a [detener]: una pausa para pensar no termina
 * el dictado.
 */
interface ReconocedorDeVoz {
    /** Hay reconocedor de voz en este teléfono. Si no, no se muestra el botón. */
    val disponible: Boolean

    /** Pide los permisos que falten y empieza a escuchar. */
    fun iniciar(escucha: EscuchaDeDictado)

    /** Deja de escuchar y entrega lo último reconocido; luego llama a [EscuchaDeDictado.alTerminar]. */
    fun detener()

    /** Corta en seco, sin entregar nada más. */
    fun cancelar()
}

/** Reconocedor real del teléfono, en el idioma de la app. */
@Composable
expect fun rememberReconocedorDeVoz(): ReconocedorDeVoz
