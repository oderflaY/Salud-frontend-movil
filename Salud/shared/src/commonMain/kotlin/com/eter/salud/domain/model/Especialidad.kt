package com.eter.salud.domain.model

import kotlinx.serialization.Serializable

/**
 * Especialidades del Directorio Medico. Enum cerrado y no texto libre: el
 * filtro por especialidad de la pantalla "Mi Medico" necesita comparar contra
 * valores exactos, y la traduccion vive en `strings.xml` (DM_Arquitectura_App.md,
 * seccion 5).
 */
@Serializable
enum class Especialidad {
    MEDICINA_GENERAL,
    CARDIOLOGIA,
    PEDIATRIA,
    DERMATOLOGIA,
    GINECOLOGIA,
    PSIQUIATRIA,
    GERIATRIA,
    ENDOCRINOLOGIA,
    NEUMOLOGIA,
    NEUROLOGIA,
}
