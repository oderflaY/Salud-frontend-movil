package com.eter.salud.ui.emergencia

import com.eter.salud.domain.nfc.DisponibilidadNfc
import com.eter.salud.presentation.emergencia.ErrorEscaneo
import org.jetbrains.compose.resources.StringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.nfc_error_lectura
import salud.shared.generated.resources.nfc_error_revocada
import salud.shared.generated.resources.nfc_error_sin_conexion
import salud.shared.generated.resources.nfc_error_tarjeta_desconocida
import salud.shared.generated.resources.nfc_estado_desactivada
import salud.shared.generated.resources.nfc_estado_no_soportada

/**
 * Puente entre las claves de la capa de presentacion del escaner de emergencia
 * y el archivo central de textos. El ViewModel nunca conoce cadenas.
 */
internal fun ErrorEscaneo.recurso(): StringResource = when (this) {
    ErrorEscaneo.LECTURA_INTERRUMPIDA -> Res.string.nfc_error_lectura
    ErrorEscaneo.TARJETA_NO_VALIDA -> Res.string.nfc_error_lectura
    ErrorEscaneo.TARJETA_DESCONOCIDA -> Res.string.nfc_error_tarjeta_desconocida
    ErrorEscaneo.TARJETA_REVOCADA -> Res.string.nfc_error_revocada
    ErrorEscaneo.SIN_CONEXION -> Res.string.nfc_error_sin_conexion
}

/**
 * Solo tiene texto propio para los dos motivos por los que la antena no
 * escucha; [DisponibilidadNfc.DISPONIBLE] no entra nunca aqui.
 */
internal fun DisponibilidadNfc.recurso(): StringResource = when (this) {
    DisponibilidadNfc.DESACTIVADA -> Res.string.nfc_estado_desactivada
    DisponibilidadNfc.NO_SOPORTADA -> Res.string.nfc_estado_no_soportada
    DisponibilidadNfc.DISPONIBLE -> Res.string.nfc_estado_no_soportada
}
