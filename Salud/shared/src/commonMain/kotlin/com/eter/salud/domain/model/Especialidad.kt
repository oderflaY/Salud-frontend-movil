package com.eter.salud.domain.model

/**
 * Especialidades del Directorio Medico. Enum cerrado y no texto libre: el
 * filtro por especialidad de la pantalla "Mi Medico" necesita comparar contra
 * valores exactos, y la traduccion vive en `strings.xml` (DM_Arquitectura_App.md,
 * seccion 5).
 */
enum class Especialidad {
    MEDICINA_GENERAL,
    CARDIOLOGIA,
    PEDIATRIA,
    DERMATOLOGIA,
    GINECOLOGIA,
    PSIQUIATRIA,
}
