package com.eter.salud.data.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.eter.salud.domain.nfc.DisponibilidadNfc
import com.eter.salud.domain.nfc.ErrorLecturaNfc
import com.eter.salud.domain.nfc.LectorTarjetaNfc

/**
 * Antena NFC de Android en modo lector.
 *
 * Usa `enableReaderMode` y no el despacho por intents a proposito: el modo
 * lector mantiene la lectura dentro de la pantalla que ya esta abierta, en vez
 * de relanzar la Activity con un intent nuevo. En una atencion de emergencia,
 * reiniciar la pantalla al acercar la tarjeta seria inaceptable.
 *
 * Requiere la Activity porque asi lo exige la API de Android. Se recibe por
 * constructor para que el ViewModel siga sin conocer nada del sistema.
 */
class LectorNfcAndroid(
    private val actividad: Activity,
) : LectorTarjetaNfc {

    private val adaptador: NfcAdapter? = NfcAdapter.getDefaultAdapter(actividad)

    private val hiloPrincipal = Handler(Looper.getMainLooper())

    override val disponibilidad: DisponibilidadNfc
        get() = when {
            adaptador == null -> DisponibilidadNfc.NO_SOPORTADA
            !adaptador.isEnabled -> DisponibilidadNfc.DESACTIVADA
            else -> DisponibilidadNfc.DISPONIBLE
        }

    override fun iniciar(alLeer: (String) -> Unit, alFallar: (ErrorLecturaNfc) -> Unit) {
        val adaptador = adaptador ?: return
        val opciones = Bundle().apply {
            // Un retardo alto de comprobacion de presencia evita que la lectura
            // se cancele por el pulso de una mano que sostiene el telefono.
            putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, RETARDO_PRESENCIA_MS)
        }
        adaptador.enableReaderMode(
            actividad,
            { tag -> despacharTag(tag, alLeer, alFallar) },
            BANDERAS_LECTURA,
            opciones,
        )
    }

    override fun detener() {
        adaptador?.disableReaderMode(actividad)
    }

    /**
     * La devolucion de llamada de NFC llega en un hilo de binder; el estado de
     * la interfaz solo se toca desde el hilo principal.
     */
    private fun despacharTag(
        tag: Tag?,
        alLeer: (String) -> Unit,
        alFallar: (ErrorLecturaNfc) -> Unit,
    ) {
        val identificador = tag?.id?.aHexadecimal()
        hiloPrincipal.post {
            if (identificador.isNullOrBlank()) {
                alFallar(ErrorLecturaNfc.TARJETA_NO_VALIDA)
            } else {
                alLeer(identificador)
            }
        }
    }

    /** UID en hexadecimal y mayusculas, el formato que espera el backend. */
    private fun ByteArray.aHexadecimal(): String =
        joinToString(separator = "") { byte ->
            val valor = byte.toInt() and 0xFF
            DIGITOS[valor shr 4].toString() + DIGITOS[valor and 0x0F]
        }

    private companion object {
        const val DIGITOS = "0123456789ABCDEF"
        const val RETARDO_PRESENCIA_MS = 500

        /**
         * Tecnologias de las tarjetas de paciente. Se desactiva el sonido del
         * sistema: en una ambulancia el aviso util es la vibracion, no un pitido.
         */
        const val BANDERAS_LECTURA = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
    }
}
