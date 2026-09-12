package com.eter.salud.presentation.emergencia

import com.eter.salud.domain.model.PerfilSupervivencia
import com.eter.salud.domain.nfc.DisponibilidadNfc

/** Momento del escaneo. La Vista pinta una pantalla distinta por fase. */
enum class FaseEscaneo {
    /** La pantalla existe pero la antena esta apagada. */
    INACTIVO,

    ESPERANDO_TARJETA,

    /** Tarjeta leida; se esta pidiendo el perfil al backend. */
    CONSULTANDO,

    PERFIL_DISPONIBLE,

    ERROR,

    ANTENA_NO_DISPONIBLE,
}

/** Fallos que la pantalla de emergencia sabe explicar. */
enum class ErrorEscaneo {
    LECTURA_INTERRUMPIDA,
    TARJETA_NO_VALIDA,
    TARJETA_DESCONOCIDA,
    TARJETA_REVOCADA,
    SIN_CONEXION,
}

/**
 * Estado unico del escaner de emergencia. La Vista solo pinta esto
 * (DM_Arquitectura_App.md, seccion 2: la Vista es pasiva).
 */
data class EscanerUiState(
    val fase: FaseEscaneo = FaseEscaneo.INACTIVO,
    val disponibilidad: DisponibilidadNfc = DisponibilidadNfc.DISPONIBLE,
    val perfil: PerfilSupervivencia? = null,
    /** Edad cumplida; nula si la tarjeta no trae una fecha de nacimiento util. */
    val edadPaciente: Int? = null,
    val errorConsulta: ErrorEscaneo? = null,
    /**
     * Evento de un solo uso: la Vista dispara la vibracion de exito y avisa con
     * `vibracionConsumida()`. El ViewModel no toca el hardware de la interfaz.
     */
    val confirmarConVibracion: Boolean = false,
) {
    val estaEscuchando: Boolean get() = fase == FaseEscaneo.ESPERANDO_TARJETA

    val estaConsultando: Boolean get() = fase == FaseEscaneo.CONSULTANDO
}
