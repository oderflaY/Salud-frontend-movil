package com.eter.salud.presentation.emergencia

import com.eter.salud.domain.model.PerfilSupervivencia
import com.eter.salud.domain.nfc.DisponibilidadNfc
import com.eter.salud.domain.nfc.ErrorLecturaNfc
import com.eter.salud.domain.nfc.LectorTarjetaNfc
import com.eter.salud.domain.repository.PerfilEmergenciaRepositorio

/**
 * Antena falsa. Guarda las devoluciones de llamada para poder simular desde la
 * prueba tanto una lectura correcta como un fallo de la antena.
 */
class LectorNfcFalso(
    override val disponibilidad: DisponibilidadNfc = DisponibilidadNfc.DISPONIBLE,
) : LectorTarjetaNfc {

    private var alLeer: ((String) -> Unit)? = null
    private var alFallar: ((ErrorLecturaNfc) -> Unit)? = null

    var vecesIniciado: Int = 0
        private set

    var vecesDetenido: Int = 0
        private set

    val escuchando: Boolean get() = alLeer != null

    override fun iniciar(alLeer: (String) -> Unit, alFallar: (ErrorLecturaNfc) -> Unit) {
        vecesIniciado++
        this.alLeer = alLeer
        this.alFallar = alFallar
    }

    override fun detener() {
        vecesDetenido++
        alLeer = null
        alFallar = null
    }

    /** Simula que el paramedico acerco una tarjeta. */
    fun simularLectura(idTarjeta: String) {
        alLeer?.invoke(idTarjeta)
    }

    fun simularFallo(error: ErrorLecturaNfc) {
        alFallar?.invoke(error)
    }
}

/** Repositorio de emergencia falso. */
class PerfilEmergenciaRepositorioFalso(
    private val resultado: Result<PerfilSupervivencia>,
) : PerfilEmergenciaRepositorio {

    var consultas: MutableList<String> = mutableListOf()
        private set

    override suspend fun consultarPorTarjeta(
        idTarjetaRfid: String,
    ): Result<PerfilSupervivencia> {
        consultas += idTarjetaRfid
        return resultado
    }
}
