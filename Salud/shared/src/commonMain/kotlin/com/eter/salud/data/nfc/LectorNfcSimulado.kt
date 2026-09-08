package com.eter.salud.data.nfc

import com.eter.salud.domain.nfc.DisponibilidadNfc
import com.eter.salud.domain.nfc.ErrorLecturaNfc
import com.eter.salud.domain.nfc.LectorTarjetaNfc

/**
 * Antena simulada para desarrollo.
 *
 * Los emuladores de Android y el simulador de iOS no tienen NFC, asi que sin
 * esto la pantalla de emergencia no se podria ver sin un telefono fisico y una
 * tarjeta real. [simularAcercarTarjeta] hace las veces del gesto del paramedico.
 */
class LectorNfcSimulado(
    override val disponibilidad: DisponibilidadNfc = DisponibilidadNfc.DISPONIBLE,
) : LectorTarjetaNfc {

    private var alLeer: ((String) -> Unit)? = null
    private var alFallar: ((ErrorLecturaNfc) -> Unit)? = null

    val escuchando: Boolean get() = alLeer != null

    override fun iniciar(alLeer: (String) -> Unit, alFallar: (ErrorLecturaNfc) -> Unit) {
        this.alLeer = alLeer
        this.alFallar = alFallar
    }

    override fun detener() {
        alLeer = null
        alFallar = null
    }

    fun simularAcercarTarjeta(idTarjeta: String) {
        alLeer?.invoke(idTarjeta)
    }

    fun simularFallo(error: ErrorLecturaNfc) {
        alFallar?.invoke(error)
    }
}
