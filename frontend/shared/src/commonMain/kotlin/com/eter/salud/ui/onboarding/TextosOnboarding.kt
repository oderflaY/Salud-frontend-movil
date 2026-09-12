package com.eter.salud.ui.onboarding

import com.eter.salud.presentation.onboarding.CampoCriticoSupervivencia
import com.eter.salud.presentation.onboarding.ErrorCampoOnboarding
import com.eter.salud.presentation.onboarding.ErrorEnvio
import org.jetbrains.compose.resources.StringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.error_alergia_alergeno_vacio
import salud.shared.generated.resources.error_alergia_reaccion_vacia
import salud.shared.generated.resources.error_alergia_severidad_vacia
import salud.shared.generated.resources.error_alergias_sin_confirmar
import salud.shared.generated.resources.error_apellidos_vacio
import salud.shared.generated.resources.error_condicion_vacia
import salud.shared.generated.resources.error_condiciones_sin_confirmar
import salud.shared.generated.resources.error_contacto_nombre_vacio
import salud.shared.generated.resources.error_contacto_relacion_vacia
import salud.shared.generated.resources.error_contacto_telefono_formato
import salud.shared.generated.resources.error_contacto_telefono_vacio
import salud.shared.generated.resources.error_contactos_vacio
import salud.shared.generated.resources.error_genero_vacio
import salud.shared.generated.resources.error_nacimiento_formato
import salud.shared.generated.resources.error_nacimiento_futura
import salud.shared.generated.resources.error_nacimiento_vacio
import salud.shared.generated.resources.error_nombre_vacio
import salud.shared.generated.resources.error_rescate_sin_confirmar
import salud.shared.generated.resources.error_rescate_vacio
import salud.shared.generated.resources.error_sangre_vacio
import salud.shared.generated.resources.error_telefono_formato
import salud.shared.generated.resources.error_telefono_vacio
import salud.shared.generated.resources.error_tratamiento_dosis_vacia
import salud.shared.generated.resources.error_tratamiento_frecuencia_invalida
import salud.shared.generated.resources.error_tratamiento_frecuencia_vacia
import salud.shared.generated.resources.error_tratamiento_inventario_invalido
import salud.shared.generated.resources.error_tratamiento_medicamento_vacio
import salud.shared.generated.resources.error_tratamientos_sin_confirmar
import salud.shared.generated.resources.estado_envio_error
import salud.shared.generated.resources.onb_alergias_titulo
import salud.shared.generated.resources.onb_condiciones_titulo
import salud.shared.generated.resources.onb_contactos_titulo
import salud.shared.generated.resources.onb_rescate_titulo
import salud.shared.generated.resources.onb_sangre_campo

/**
 * Puente entre las claves de error del ViewModel y el archivo central de
 * textos. La traduccion vive en la Vista: el ViewModel nunca conoce cadenas.
 */
internal fun ErrorCampoOnboarding.recurso(): StringResource = when (this) {
    ErrorCampoOnboarding.NOMBRE_VACIO -> Res.string.error_nombre_vacio
    ErrorCampoOnboarding.APELLIDOS_VACIO -> Res.string.error_apellidos_vacio
    ErrorCampoOnboarding.NACIMIENTO_VACIO -> Res.string.error_nacimiento_vacio
    ErrorCampoOnboarding.NACIMIENTO_FORMATO -> Res.string.error_nacimiento_formato
    ErrorCampoOnboarding.NACIMIENTO_FUTURA -> Res.string.error_nacimiento_futura
    ErrorCampoOnboarding.GENERO_VACIO -> Res.string.error_genero_vacio
    ErrorCampoOnboarding.TELEFONO_VACIO -> Res.string.error_telefono_vacio
    ErrorCampoOnboarding.TELEFONO_FORMATO -> Res.string.error_telefono_formato
    ErrorCampoOnboarding.SANGRE_VACIO -> Res.string.error_sangre_vacio
    ErrorCampoOnboarding.ALERGIAS_SIN_CONFIRMAR -> Res.string.error_alergias_sin_confirmar
    ErrorCampoOnboarding.ALERGIA_ALERGENO_VACIO -> Res.string.error_alergia_alergeno_vacio
    ErrorCampoOnboarding.ALERGIA_SEVERIDAD_VACIA -> Res.string.error_alergia_severidad_vacia
    ErrorCampoOnboarding.ALERGIA_REACCION_VACIA -> Res.string.error_alergia_reaccion_vacia
    ErrorCampoOnboarding.CONDICIONES_SIN_CONFIRMAR -> Res.string.error_condiciones_sin_confirmar
    ErrorCampoOnboarding.CONDICION_VACIA -> Res.string.error_condicion_vacia
    ErrorCampoOnboarding.RESCATE_SIN_CONFIRMAR -> Res.string.error_rescate_sin_confirmar
    ErrorCampoOnboarding.RESCATE_VACIO -> Res.string.error_rescate_vacio
    ErrorCampoOnboarding.TRATAMIENTOS_SIN_CONFIRMAR ->
        Res.string.error_tratamientos_sin_confirmar
    ErrorCampoOnboarding.TRATAMIENTO_MEDICAMENTO_VACIO ->
        Res.string.error_tratamiento_medicamento_vacio
    ErrorCampoOnboarding.TRATAMIENTO_DOSIS_VACIA -> Res.string.error_tratamiento_dosis_vacia
    ErrorCampoOnboarding.TRATAMIENTO_FRECUENCIA_VACIA ->
        Res.string.error_tratamiento_frecuencia_vacia
    ErrorCampoOnboarding.TRATAMIENTO_FRECUENCIA_INVALIDA ->
        Res.string.error_tratamiento_frecuencia_invalida
    ErrorCampoOnboarding.TRATAMIENTO_INVENTARIO_INVALIDO ->
        Res.string.error_tratamiento_inventario_invalido
    ErrorCampoOnboarding.CONTACTOS_VACIO -> Res.string.error_contactos_vacio
    ErrorCampoOnboarding.CONTACTO_NOMBRE_VACIO -> Res.string.error_contacto_nombre_vacio
    ErrorCampoOnboarding.CONTACTO_RELACION_VACIA -> Res.string.error_contacto_relacion_vacia
    ErrorCampoOnboarding.CONTACTO_TELEFONO_VACIO -> Res.string.error_contacto_telefono_vacio
    ErrorCampoOnboarding.CONTACTO_TELEFONO_FORMATO -> Res.string.error_contacto_telefono_formato
}

/** Nombre legible del dato critico que falta, para la alerta de cierre. */
internal fun CampoCriticoSupervivencia.recurso(): StringResource = when (this) {
    CampoCriticoSupervivencia.TIPO_SANGRE -> Res.string.onb_sangre_campo
    CampoCriticoSupervivencia.ALERGIAS -> Res.string.onb_alergias_titulo
    CampoCriticoSupervivencia.CONDICIONES_CRITICAS -> Res.string.onb_condiciones_titulo
    CampoCriticoSupervivencia.MEDICACION_RESCATE -> Res.string.onb_rescate_titulo
    CampoCriticoSupervivencia.CONTACTOS_EMERGENCIA -> Res.string.onb_contactos_titulo
}

internal fun ErrorEnvio.recurso(): StringResource = when (this) {
    ErrorEnvio.SIN_CONEXION -> Res.string.estado_envio_error
}
