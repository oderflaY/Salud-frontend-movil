package com.eter.salud.ui.login

import com.eter.salud.presentation.login.ErrorAutenticacion
import com.eter.salud.presentation.login.ErrorCampoLogin
import org.jetbrains.compose.resources.StringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.error_contrasena_corta
import salud.shared.generated.resources.error_contrasena_vacia
import salud.shared.generated.resources.error_correo_formato
import salud.shared.generated.resources.error_correo_vacio
import salud.shared.generated.resources.error_credenciales_invalidas
import salud.shared.generated.resources.error_cuenta_bloqueada
import salud.shared.generated.resources.error_login_sin_conexion

/**
 * Puente entre las claves de error del acceso y el archivo central de textos.
 * El ViewModel nunca conoce cadenas; la traduccion vive aqui, en la Vista.
 */
internal fun ErrorCampoLogin.recurso(): StringResource = when (this) {
    ErrorCampoLogin.CORREO_VACIO -> Res.string.error_correo_vacio
    ErrorCampoLogin.CORREO_FORMATO -> Res.string.error_correo_formato
    ErrorCampoLogin.CONTRASENA_VACIA -> Res.string.error_contrasena_vacia
    ErrorCampoLogin.CONTRASENA_CORTA -> Res.string.error_contrasena_corta
}

internal fun ErrorAutenticacion.recurso(): StringResource = when (this) {
    ErrorAutenticacion.CREDENCIALES_INVALIDAS -> Res.string.error_credenciales_invalidas
    ErrorAutenticacion.CUENTA_BLOQUEADA -> Res.string.error_cuenta_bloqueada
    ErrorAutenticacion.SIN_CONEXION -> Res.string.error_login_sin_conexion
}
