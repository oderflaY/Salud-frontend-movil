package com.eter.salud.ui.agenda

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.eter.salud.domain.model.EstadoCita
import com.eter.salud.domain.time.CalendarioSalud
import com.eter.salud.presentation.agenda.VistaCalendario
import com.eter.salud.presentation.citas.ErrorAgendaCita
import com.eter.salud.presentation.citas.ErrorCampoCita
import com.eter.salud.ui.theme.ColoresSalud
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.agenda_estado_bloqueado
import salud.shared.generated.resources.agenda_estado_cancelada
import salud.shared.generated.resources.agenda_estado_confirmada
import salud.shared.generated.resources.agenda_estado_en_curso
import salud.shared.generated.resources.agenda_estado_no_asistio
import salud.shared.generated.resources.agenda_estado_pendiente
import salud.shared.generated.resources.agenda_estado_propuesta_medico
import salud.shared.generated.resources.agenda_vista_diaria
import salud.shared.generated.resources.agenda_vista_mensual
import salud.shared.generated.resources.agenda_vista_dos_semanas
import salud.shared.generated.resources.cita_error_correo_formato
import salud.shared.generated.resources.cita_error_correo_vacio
import salud.shared.generated.resources.cita_error_franja_ocupada
import salud.shared.generated.resources.cita_error_motivo_vacio
import salud.shared.generated.resources.cita_error_nombre_vacio
import salud.shared.generated.resources.cita_error_reserva_expirada
import salud.shared.generated.resources.cita_error_sin_conexion
import salud.shared.generated.resources.cita_error_sin_horarios
import salud.shared.generated.resources.cita_error_telefono_formato
import salud.shared.generated.resources.cita_error_telefono_vacio
import salud.shared.generated.resources.fecha_corta
import salud.shared.generated.resources.fecha_larga
import salud.shared.generated.resources.opciones_dias_semana
import salud.shared.generated.resources.opciones_meses

/**
 * Puentes entre los tipos del modulo de citas y `strings.xml`.
 *
 * La traduccion vive en la Vista y nunca en los ViewModels, que emiten claves:
 * asi el mismo estado sirve en Android, en iOS y en las pruebas, donde no hay
 * recursos que resolver (DM_Arquitectura_App.md, seccion 5).
 */

internal fun EstadoCita.recurso(): StringResource = when (this) {
    EstadoCita.PENDIENTE -> Res.string.agenda_estado_pendiente
    EstadoCita.CONFIRMADA -> Res.string.agenda_estado_confirmada
    EstadoCita.EN_CURSO -> Res.string.agenda_estado_en_curso
    EstadoCita.CANCELADA -> Res.string.agenda_estado_cancelada
    EstadoCita.NO_ASISTIO -> Res.string.agenda_estado_no_asistio
    EstadoCita.BLOQUEADO -> Res.string.agenda_estado_bloqueado
    EstadoCita.PROPUESTA_MEDICO -> Res.string.agenda_estado_propuesta_medico
}

internal fun VistaCalendario.recurso(): StringResource = when (this) {
    VistaCalendario.DIARIA -> Res.string.agenda_vista_diaria
    VistaCalendario.DOS_SEMANAS -> Res.string.agenda_vista_dos_semanas
    VistaCalendario.MENSUAL -> Res.string.agenda_vista_mensual
}

internal fun ErrorAgendaCita.recurso(): StringResource = when (this) {
    ErrorAgendaCita.SIN_CONEXION -> Res.string.cita_error_sin_conexion
    ErrorAgendaCita.FRANJA_OCUPADA -> Res.string.cita_error_franja_ocupada
    ErrorAgendaCita.RESERVA_EXPIRADA -> Res.string.cita_error_reserva_expirada
    ErrorAgendaCita.SIN_HORARIOS -> Res.string.cita_error_sin_horarios
}

internal fun ErrorCampoCita.recurso(): StringResource = when (this) {
    ErrorCampoCita.NOMBRE_VACIO -> Res.string.cita_error_nombre_vacio
    ErrorCampoCita.TELEFONO_VACIO -> Res.string.cita_error_telefono_vacio
    ErrorCampoCita.TELEFONO_FORMATO -> Res.string.cita_error_telefono_formato
    ErrorCampoCita.CORREO_VACIO -> Res.string.cita_error_correo_vacio
    ErrorCampoCita.CORREO_FORMATO -> Res.string.cita_error_correo_formato
    ErrorCampoCita.MOTIVO_VACIO -> Res.string.cita_error_motivo_vacio
}

/**
 * Par (texto, fondo) del estado, tomado siempre de los tokens semanticos.
 *
 * [EstadoCita.CANCELADA] y [EstadoCita.NO_ASISTIO] comparten par: significan lo
 * mismo para la vista ("esta cita no va a ocurrir") y su diferencia la dice la
 * etiqueta escrita, no el color.
 */
internal fun ColoresSalud.parDeEstado(estado: EstadoCita): Pair<Color, Color> = when (estado) {
    // PENDIENTE y PROPUESTA_MEDICO comparten par: para quien mira el calendario
    // las dos dicen lo mismo -- "esta cita todavia no la valido la otra
    // parte" -- y solo cambia QUIEN falta por responder, que ya lo dice la
    // palabra del estado, no el color.
    EstadoCita.PENDIENTE, EstadoCita.PROPUESTA_MEDICO -> citaPendiente to fondoCitaPendiente
    EstadoCita.CONFIRMADA -> citaConfirmada to fondoCitaConfirmada
    EstadoCita.EN_CURSO -> citaEnCurso to fondoCitaEnCurso
    EstadoCita.CANCELADA, EstadoCita.NO_ASISTIO -> citaCancelada to fondoCitaCancelada
    EstadoCita.BLOQUEADO -> citaBloqueada to fondoCitaBloqueada
}

/**
 * "Martes 10 de Septiembre" a partir de `YYYY-MM-DD`.
 *
 * Se arma con [CalendarioSalud] y los nombres de `strings.xml` en vez de con un
 * formateador de la plataforma: el nombre del dia tiene que salir igual en
 * Android y en iOS, y el idioma lo manda el archivo de recursos, no el sistema.
 * Si la fecha no es valida devuelve el texto tal cual, que es informacion mas
 * util que una cadena vacia.
 */
@Composable
internal fun fechaLarga(fecha: String): String {
    val partes = CalendarioSalud.descomponer(fecha) ?: return fecha
    val indiceDia = CalendarioSalud.diaDeLaSemana(fecha) ?: return fecha
    val dias = stringArrayResource(Res.array.opciones_dias_semana)
    val meses = stringArrayResource(Res.array.opciones_meses)
    return stringResource(
        Res.string.fecha_larga,
        dias.getOrElse(indiceDia) { "" },
        partes.dia.toInt(),
        meses.getOrElse(partes.mes.toInt() - 1) { "" },
    )
}

/** "10 de Septiembre": la version sin dia de la semana, para rangos y resumenes. */
@Composable
internal fun fechaCorta(fecha: String): String {
    val partes = CalendarioSalud.descomponer(fecha) ?: return fecha
    val meses = stringArrayResource(Res.array.opciones_meses)
    return stringResource(
        Res.string.fecha_corta,
        partes.dia.toInt(),
        meses.getOrElse(partes.mes.toInt() - 1) { "" },
    )
}
