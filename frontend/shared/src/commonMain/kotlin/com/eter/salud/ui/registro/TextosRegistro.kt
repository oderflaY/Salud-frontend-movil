package com.eter.salud.ui.registro

import com.eter.salud.presentation.registro.ErrorCampoRegistro
import com.eter.salud.presentation.registro.ErrorRegistro
import org.jetbrains.compose.resources.StringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.error_aviso_no_aceptado
import salud.shared.generated.resources.error_confirmacion_no_coincide
import salud.shared.generated.resources.error_contrasena_corta
import salud.shared.generated.resources.error_contrasena_debil
import salud.shared.generated.resources.error_contrasena_vacia
import salud.shared.generated.resources.error_correo_formato
import salud.shared.generated.resources.error_correo_vacio
import salud.shared.generated.resources.error_correo_ya_registrado
import salud.shared.generated.resources.error_registro_sin_conexion

/** Puente entre las claves del alta de cuenta y el archivo central de textos. */
internal fun ErrorCampoRegistro.recurso(): StringResource = when (this) {
    ErrorCampoRegistro.CORREO_VACIO -> Res.string.error_correo_vacio
    ErrorCampoRegistro.CORREO_FORMATO -> Res.string.error_correo_formato
    ErrorCampoRegistro.CONTRASENA_VACIA -> Res.string.error_contrasena_vacia
    ErrorCampoRegistro.CONTRASENA_CORTA -> Res.string.error_contrasena_corta
    ErrorCampoRegistro.CONTRASENA_DEBIL -> Res.string.error_contrasena_debil
    ErrorCampoRegistro.CONFIRMACION_NO_COINCIDE -> Res.string.error_confirmacion_no_coincide
    ErrorCampoRegistro.AVISO_NO_ACEPTADO -> Res.string.error_aviso_no_aceptado
}

internal fun ErrorRegistro.recurso(): StringResource = when (this) {
    ErrorRegistro.CORREO_YA_REGISTRADO -> Res.string.error_correo_ya_registrado
    ErrorRegistro.SIN_CONEXION -> Res.string.error_registro_sin_conexion
}
