package com.eter.salud.ui.profesional

import com.eter.salud.domain.model.RiesgoPaciente
import com.eter.salud.presentation.profesional.ErrorAutenticacionProfesional
import com.eter.salud.presentation.profesional.ErrorCampoLoginProfesional
import com.eter.salud.presentation.profesional.ErrorCampoRegistroProfesional
import com.eter.salud.presentation.profesional.ErrorRegistroProfesional
import org.jetbrains.compose.resources.StringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.error_aviso_no_aceptado
import salud.shared.generated.resources.error_cedula_formato
import salud.shared.generated.resources.error_cedula_vacia
import salud.shared.generated.resources.error_cedula_ya_registrada
import salud.shared.generated.resources.error_confirmacion_no_coincide
import salud.shared.generated.resources.error_contrasena_corta
import salud.shared.generated.resources.error_contrasena_debil
import salud.shared.generated.resources.error_contrasena_vacia
import salud.shared.generated.resources.error_correo_formato
import salud.shared.generated.resources.error_correo_vacio
import salud.shared.generated.resources.error_correo_ya_registrado
import salud.shared.generated.resources.error_credenciales_invalidas
import salud.shared.generated.resources.error_cuenta_bloqueada
import salud.shared.generated.resources.error_login_sin_conexion
import salud.shared.generated.resources.error_nombre_vacio
import salud.shared.generated.resources.error_apellidos_vacio
import salud.shared.generated.resources.error_registro_sin_conexion
import salud.shared.generated.resources.error_tratamiento_vacio
import salud.shared.generated.resources.profesional_home_riesgo_alto
import salud.shared.generated.resources.profesional_home_riesgo_bajo
import salud.shared.generated.resources.profesional_home_riesgo_medio

/**
 * Puente entre las claves de la capa de presentacion del portal de personal
 * medico y el archivo central de textos. El ViewModel nunca conoce cadenas.
 *
 * Los mensajes de forma (correo, contrasena, aviso) son literalmente los mismos
 * que en el portal de paciente: se reutiliza el recurso en vez de duplicar el
 * texto, aunque las claves de error vivan en enums separados por bounded
 * context.
 */
internal fun ErrorCampoLoginProfesional.recurso(): StringResource = when (this) {
    ErrorCampoLoginProfesional.CORREO_VACIO -> Res.string.error_correo_vacio
    ErrorCampoLoginProfesional.CORREO_FORMATO -> Res.string.error_correo_formato
    ErrorCampoLoginProfesional.CONTRASENA_VACIA -> Res.string.error_contrasena_vacia
    ErrorCampoLoginProfesional.CONTRASENA_CORTA -> Res.string.error_contrasena_corta
}

internal fun ErrorAutenticacionProfesional.recurso(): StringResource = when (this) {
    ErrorAutenticacionProfesional.CREDENCIALES_INVALIDAS -> Res.string.error_credenciales_invalidas
    ErrorAutenticacionProfesional.CUENTA_BLOQUEADA -> Res.string.error_cuenta_bloqueada
    ErrorAutenticacionProfesional.SIN_CONEXION -> Res.string.error_login_sin_conexion
}

internal fun ErrorCampoRegistroProfesional.recurso(): StringResource = when (this) {
    ErrorCampoRegistroProfesional.CORREO_VACIO -> Res.string.error_correo_vacio
    ErrorCampoRegistroProfesional.CORREO_FORMATO -> Res.string.error_correo_formato
    ErrorCampoRegistroProfesional.CONTRASENA_VACIA -> Res.string.error_contrasena_vacia
    ErrorCampoRegistroProfesional.CONTRASENA_CORTA -> Res.string.error_contrasena_corta
    ErrorCampoRegistroProfesional.CONTRASENA_DEBIL -> Res.string.error_contrasena_debil
    ErrorCampoRegistroProfesional.CONFIRMACION_NO_COINCIDE ->
        Res.string.error_confirmacion_no_coincide

    ErrorCampoRegistroProfesional.NOMBRE_VACIO -> Res.string.error_nombre_vacio
    ErrorCampoRegistroProfesional.APELLIDOS_VACIO -> Res.string.error_apellidos_vacio
    ErrorCampoRegistroProfesional.TRATAMIENTO_VACIO -> Res.string.error_tratamiento_vacio
    ErrorCampoRegistroProfesional.CEDULA_VACIA -> Res.string.error_cedula_vacia
    ErrorCampoRegistroProfesional.CEDULA_FORMATO -> Res.string.error_cedula_formato
    ErrorCampoRegistroProfesional.AVISO_NO_ACEPTADO -> Res.string.error_aviso_no_aceptado
}

internal fun ErrorRegistroProfesional.recurso(): StringResource = when (this) {
    ErrorRegistroProfesional.CORREO_YA_REGISTRADO -> Res.string.error_correo_ya_registrado
    ErrorRegistroProfesional.CEDULA_YA_REGISTRADA -> Res.string.error_cedula_ya_registrada
    ErrorRegistroProfesional.SIN_CONEXION -> Res.string.error_registro_sin_conexion
}

internal fun RiesgoPaciente.recurso(): StringResource = when (this) {
    RiesgoPaciente.ALTO -> Res.string.profesional_home_riesgo_alto
    RiesgoPaciente.MEDIO -> Res.string.profesional_home_riesgo_medio
    RiesgoPaciente.BAJO -> Res.string.profesional_home_riesgo_bajo
}
