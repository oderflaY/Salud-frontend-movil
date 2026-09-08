package com.eter.salud.data.nfc

import com.eter.salud.domain.nfc.DisponibilidadNfc
import com.eter.salud.domain.nfc.ErrorLecturaNfc
import com.eter.salud.domain.nfc.LectorTarjetaNfc
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreNFC.NFCISO15693TagProtocol
import platform.CoreNFC.NFCISO7816TagProtocol
import platform.CoreNFC.NFCMiFareTagProtocol
import platform.CoreNFC.NFCPollingISO14443
import platform.CoreNFC.NFCPollingISO15693
import platform.CoreNFC.NFCTagProtocol
import platform.CoreNFC.NFCTagReaderSession
import platform.CoreNFC.NFCTagReaderSessionDelegateProtocol
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.darwin.NSObject
import platform.posix.memcpy

/**
 * Antena NFC de iOS mediante CoreNFC.
 *
 * Se usa `NFCTagReaderSession` y no la lectura de NDEF porque lo que identifica
 * a la tarjeta del paciente es el UID del tag, que la sesion de NDEF no expone.
 *
 * A diferencia de Android, iOS presenta su propia hoja del sistema durante la
 * lectura y la cierra al detectar el tag; por eso el mensaje de esa hoja se
 * recibe ya traducido desde la Vista, que es quien tiene acceso a `strings.xml`.
 */
@OptIn(ExperimentalForeignApi::class)
class LectorNfcIos(
    private val mensajeDeLaHoja: String,
) : LectorTarjetaNfc {

    private var sesion: NFCTagReaderSession? = null
    private var delegado: DelegadoLectura? = null

    override val disponibilidad: DisponibilidadNfc
        get() = if (NFCTagReaderSession.readingAvailable) {
            DisponibilidadNfc.DISPONIBLE
        } else {
            // iOS no distingue "apagado" de "no soportado": no deja consultar ni
            // cambiar el estado de la antena, solo si se puede leer ahora mismo.
            DisponibilidadNfc.NO_SOPORTADA
        }

    override fun iniciar(alLeer: (String) -> Unit, alFallar: (ErrorLecturaNfc) -> Unit) {
        detener()
        val delegadoLectura = DelegadoLectura(
            alLeer = { identificador ->
                sesion?.invalidateSession()
                alLeer(identificador)
            },
            alFallar = alFallar,
        )
        delegado = delegadoLectura

        val nuevaSesion = NFCTagReaderSession(
            pollingOption = NFCPollingISO14443 or NFCPollingISO15693,
            delegate = delegadoLectura,
            queue = null,
        )
        nuevaSesion?.alertMessage = mensajeDeLaHoja
        nuevaSesion?.beginSession()
        sesion = nuevaSesion
    }

    override fun detener() {
        sesion?.invalidateSession()
        sesion = null
        delegado = null
    }

    /** Delegado de CoreNFC. Traduce los tags detectados a un UID hexadecimal. */
    private class DelegadoLectura(
        private val alLeer: (String) -> Unit,
        private val alFallar: (ErrorLecturaNfc) -> Unit,
    ) : NSObject(), NFCTagReaderSessionDelegateProtocol {

        override fun tagReaderSessionDidBecomeActive(session: NFCTagReaderSession) = Unit

        override fun tagReaderSession(session: NFCTagReaderSession, didInvalidateWithError: NSError) {
            alFallar(ErrorLecturaNfc.LECTURA_INTERRUMPIDA)
        }

        override fun tagReaderSession(session: NFCTagReaderSession, didDetectTags: List<*>) {
            val identificador = didDetectTags
                .filterIsInstance<NFCTagProtocol>()
                .firstNotNullOfOrNull { it.identificador() }

            if (identificador == null) {
                session.invalidateSessionWithErrorMessage(MENSAJE_TAG_NO_VALIDO)
                alFallar(ErrorLecturaNfc.TARJETA_NO_VALIDA)
                return
            }
            alLeer(identificador)
        }
    }
}

/**
 * Fuera de cualquier clase a proposito: Kotlin/Native no admite campos en el
 * companion object de una subclase de un tipo Objective-C (aqui, `NSObject`),
 * asi que las constantes de [LectorNfcIos] y de su delegado viven a nivel de
 * archivo.
 */
private const val MENSAJE_TAG_NO_VALIDO = "Tarjeta no valida"
private const val DIGITOS_HEXADECIMALES = "0123456789ABCDEF"

/**
 * El UID vive en una propiedad distinta segun la familia del tag, asi que hay
 * que probar las tres que emiten las tarjetas de paciente.
 */
@OptIn(ExperimentalForeignApi::class)
private fun NFCTagProtocol.identificador(): String? = when (this) {
    is NFCMiFareTagProtocol -> identifier.aHexadecimal()
    is NFCISO7816TagProtocol -> identifier.aHexadecimal()
    is NFCISO15693TagProtocol -> identifier.aHexadecimal()
    else -> null
}

/** UID en hexadecimal y mayusculas, el formato que espera el backend. */
@OptIn(ExperimentalForeignApi::class)
private fun NSData.aHexadecimal(): String? {
    val longitud = length.toInt()
    if (longitud == 0) return null
    val bytes = ByteArray(longitud)
    bytes.usePinned { fijado ->
        memcpy(fijado.addressOf(0), this.bytes, this.length)
    }
    return bytes.joinToString(separator = "") { byte ->
        val valor = byte.toInt() and 0xFF
        DIGITOS_HEXADECIMALES[valor shr 4].toString() + DIGITOS_HEXADECIMALES[valor and 0x0F]
    }
}
