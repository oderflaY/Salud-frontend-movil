package com.eter.salud.presentation.onboarding

/**
 * Pasos de la Fase 1 del onboarding, en el orden exacto en que se presentan.
 * El orden del enum ES la maquina de estados: no hay saltos arbitrarios.
 */
enum class PasoOnboarding(
    /** Numero de pregunta mostrado al paciente; nulo en pantallas informativas. */
    val numeroPregunta: Int? = null,
    /** Indica si el paso puede posponerse sin bloquear el registro. */
    val esOmitible: Boolean = false,
) {
    BIENVENIDA,
    NOMBRE(numeroPregunta = 1),
    FECHA_NACIMIENTO(numeroPregunta = 2),
    GENERO(numeroPregunta = 3),
    TELEFONO(numeroPregunta = 4),
    TIPO_SANGRE(numeroPregunta = 5, esOmitible = true),
    ALERGIAS(numeroPregunta = 6, esOmitible = true),
    CONDICIONES_CRITICAS(numeroPregunta = 7, esOmitible = true),
    MEDICACION_RESCATE(numeroPregunta = 8, esOmitible = true),
    TRATAMIENTOS_ACTIVOS(numeroPregunta = 9, esOmitible = true),
    CONTACTOS_EMERGENCIA(numeroPregunta = 10, esOmitible = true),
    RESUMEN;

    val siguiente: PasoOnboarding?
        get() = entries.getOrNull(ordinal + 1)

    val anterior: PasoOnboarding?
        get() = entries.getOrNull(ordinal - 1)

    companion object {
        /** Total de preguntas de la Fase 1 (para el indicador "Paso X de Y"). */
        val TOTAL_PREGUNTAS: Int = entries.count { it.numeroPregunta != null }
    }
}
