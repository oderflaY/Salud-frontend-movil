package com.eter.salud.domain.nfc

/** Estado de la antena del dispositivo antes de intentar leer. */
enum class DisponibilidadNfc {
    DISPONIBLE,

    /** El dispositivo tiene antena pero el usuario la tiene apagada. */
    DESACTIVADA,

    NO_SOPORTADA,
}

/** Fallos de la lectura fisica de la tarjeta, previos a cualquier consulta. */
enum class ErrorLecturaNfc {
    /** La tarjeta se alejo antes de completar la lectura. */
    LECTURA_INTERRUMPIDA,

    /** El tag responde pero no es una tarjeta del sistema. */
    TARJETA_NO_VALIDA,
}

/**
 * Antena NFC del dispositivo, vista desde la capa de presentacion.
 *
 * Es una interfaz de dominio y no un `expect class` a proposito: asi el
 * ViewModel se prueba con un doble en `commonTest` sin arrastrar el ciclo de
 * vida de una Activity ni una sesion de CoreNFC, y cada plataforma implementa
 * lo suyo con su API nativa.
 *
 * El identificador que entrega [alLeer] es el UID del tag en hexadecimal y en
 * mayusculas, por ejemplo `C38610A8`.
 */
interface LectorTarjetaNfc {

    val disponibilidad: DisponibilidadNfc

    /**
     * Activa la antena y queda a la espera. Debe poder llamarse de nuevo tras
     * [detener] sin recrear el lector: un paramedico escanea varias tarjetas
     * seguidas en un mismo incidente.
     */
    fun iniciar(alLeer: (String) -> Unit, alFallar: (ErrorLecturaNfc) -> Unit)

    /** Apaga la escucha. Obligatorio al salir de la pantalla: consume bateria. */
    fun detener()
}
