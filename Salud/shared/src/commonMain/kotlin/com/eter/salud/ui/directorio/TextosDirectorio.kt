package com.eter.salud.ui.directorio

import com.eter.salud.domain.model.Especialidad
import com.eter.salud.presentation.directorio.ErrorDirectorio
import org.jetbrains.compose.resources.StringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.directorio_especialidad_cardiologia
import salud.shared.generated.resources.directorio_especialidad_dermatologia
import salud.shared.generated.resources.directorio_especialidad_ginecologia
import salud.shared.generated.resources.directorio_especialidad_medicina_general
import salud.shared.generated.resources.directorio_especialidad_pediatria
import salud.shared.generated.resources.directorio_especialidad_psiquiatria
import salud.shared.generated.resources.directorio_estado_error

/** Puente entre las especialidades del Directorio Medico y `strings.xml`. */
internal fun Especialidad.recurso(): StringResource = when (this) {
    Especialidad.MEDICINA_GENERAL -> Res.string.directorio_especialidad_medicina_general
    Especialidad.CARDIOLOGIA -> Res.string.directorio_especialidad_cardiologia
    Especialidad.PEDIATRIA -> Res.string.directorio_especialidad_pediatria
    Especialidad.DERMATOLOGIA -> Res.string.directorio_especialidad_dermatologia
    Especialidad.GINECOLOGIA -> Res.string.directorio_especialidad_ginecologia
    Especialidad.PSIQUIATRIA -> Res.string.directorio_especialidad_psiquiatria
}

/** Motivo de fallo del directorio, traducido en la Vista y no en el ViewModel. */
internal fun ErrorDirectorio.recurso(): StringResource = when (this) {
    ErrorDirectorio.SIN_CONEXION -> Res.string.directorio_estado_error
}
