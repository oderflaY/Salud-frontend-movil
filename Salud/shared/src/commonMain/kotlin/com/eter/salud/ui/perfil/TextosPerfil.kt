package com.eter.salud.ui.perfil

import com.eter.salud.presentation.perfil.ErrorCampoPerfil
import com.eter.salud.presentation.perfil.ErrorGuardado
import com.eter.salud.presentation.perfil.SeccionPerfil
import org.jetbrains.compose.resources.StringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.error_altura_invalida
import salud.shared.generated.resources.error_antecedente_vacio
import salud.shared.generated.resources.error_antecedentes_sin_confirmar
import salud.shared.generated.resources.error_cirugia_fecha_invalida
import salud.shared.generated.resources.error_cirugia_procedimiento_vacio
import salud.shared.generated.resources.error_cirugias_sin_confirmar
import salud.shared.generated.resources.error_curp_formato
import salud.shared.generated.resources.error_curp_vacia
import salud.shared.generated.resources.error_donador_sin_respuesta
import salud.shared.generated.resources.error_guardado_perfil_no_cargado
import salud.shared.generated.resources.error_guardado_sin_conexion
import salud.shared.generated.resources.error_nss_formato
import salud.shared.generated.resources.error_nss_vacio
import salud.shared.generated.resources.error_peso_invalido
import salud.shared.generated.resources.error_presion_invalida
import salud.shared.generated.resources.f2_antecedentes_titulo
import salud.shared.generated.resources.f2_cirugias_titulo
import salud.shared.generated.resources.f2_curp_titulo
import salud.shared.generated.resources.f2_donador_titulo
import salud.shared.generated.resources.f2_metricas_titulo
import salud.shared.generated.resources.f2_nss_titulo
import salud.shared.generated.resources.f2_presion_titulo
import salud.shared.generated.resources.perfil_fila_antecedentes
import salud.shared.generated.resources.perfil_fila_cirugias
import salud.shared.generated.resources.perfil_fila_curp
import salud.shared.generated.resources.perfil_fila_donador
import salud.shared.generated.resources.perfil_fila_metricas
import salud.shared.generated.resources.perfil_fila_presion
import salud.shared.generated.resources.perfil_fila_seguridad_social

/**
 * Puente entre las claves de la Fase 2 y el archivo central de textos.
 * El ViewModel nunca conoce cadenas; la traduccion vive aqui, en la Vista.
 */
internal fun ErrorCampoPerfil.recurso(): StringResource = when (this) {
    ErrorCampoPerfil.CURP_VACIA -> Res.string.error_curp_vacia
    ErrorCampoPerfil.CURP_FORMATO -> Res.string.error_curp_formato
    ErrorCampoPerfil.NSS_VACIO -> Res.string.error_nss_vacio
    ErrorCampoPerfil.NSS_FORMATO -> Res.string.error_nss_formato
    ErrorCampoPerfil.DONADOR_SIN_RESPUESTA -> Res.string.error_donador_sin_respuesta
    ErrorCampoPerfil.PESO_INVALIDO -> Res.string.error_peso_invalido
    ErrorCampoPerfil.ALTURA_INVALIDA -> Res.string.error_altura_invalida
    ErrorCampoPerfil.PRESION_INVALIDA -> Res.string.error_presion_invalida
    ErrorCampoPerfil.CIRUGIAS_SIN_CONFIRMAR -> Res.string.error_cirugias_sin_confirmar
    ErrorCampoPerfil.CIRUGIA_PROCEDIMIENTO_VACIO -> Res.string.error_cirugia_procedimiento_vacio
    ErrorCampoPerfil.CIRUGIA_FECHA_INVALIDA -> Res.string.error_cirugia_fecha_invalida
    ErrorCampoPerfil.ANTECEDENTES_SIN_CONFIRMAR -> Res.string.error_antecedentes_sin_confirmar
    ErrorCampoPerfil.ANTECEDENTE_VACIO -> Res.string.error_antecedente_vacio
}

internal fun ErrorGuardado.recurso(): StringResource = when (this) {
    ErrorGuardado.SIN_CONEXION -> Res.string.error_guardado_sin_conexion
    ErrorGuardado.PERFIL_NO_CARGADO -> Res.string.error_guardado_perfil_no_cargado
}

/** Nombre corto para la lista de secciones. */
internal fun SeccionPerfil.recursoFila(): StringResource = when (this) {
    SeccionPerfil.IDENTIFICACION_CURP -> Res.string.perfil_fila_curp
    SeccionPerfil.SEGURIDAD_SOCIAL -> Res.string.perfil_fila_seguridad_social
    SeccionPerfil.DONACION_ORGANOS -> Res.string.perfil_fila_donador
    SeccionPerfil.METRICAS_CORPORALES -> Res.string.perfil_fila_metricas
    SeccionPerfil.PRESION_ARTERIAL -> Res.string.perfil_fila_presion
    SeccionPerfil.CIRUGIAS -> Res.string.perfil_fila_cirugias
    SeccionPerfil.ANTECEDENTES_HEREDOFAMILIARES -> Res.string.perfil_fila_antecedentes
}

/** Pregunta completa que encabeza la seccion cuando esta abierta. */
internal fun SeccionPerfil.recursoTitulo(): StringResource = when (this) {
    SeccionPerfil.IDENTIFICACION_CURP -> Res.string.f2_curp_titulo
    SeccionPerfil.SEGURIDAD_SOCIAL -> Res.string.f2_nss_titulo
    SeccionPerfil.DONACION_ORGANOS -> Res.string.f2_donador_titulo
    SeccionPerfil.METRICAS_CORPORALES -> Res.string.f2_metricas_titulo
    SeccionPerfil.PRESION_ARTERIAL -> Res.string.f2_presion_titulo
    SeccionPerfil.CIRUGIAS -> Res.string.f2_cirugias_titulo
    SeccionPerfil.ANTECEDENTES_HEREDOFAMILIARES -> Res.string.f2_antecedentes_titulo
}
