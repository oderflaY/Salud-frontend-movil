package com.eter.salud.presentation.perfil

/**
 * Secciones de la Fase 2 (Ajustes de Perfil), en el orden en que se listan.
 *
 * A diferencia de la Fase 1, esto NO es un flujo guiado: el paciente entra por
 * donde quiere y guarda seccion por seccion. Por eso cada una vive y se valida
 * de forma independiente.
 */
enum class SeccionPerfil(val numero: Int) {
    IDENTIFICACION_CURP(1),
    SEGURIDAD_SOCIAL(2),
    DONACION_ORGANOS(3),
    METRICAS_CORPORALES(4),
    PRESION_ARTERIAL(5),
    CIRUGIAS(6),
    ANTECEDENTES_HEREDOFAMILIARES(7);

    companion object {
        val TOTAL_SECCIONES: Int = entries.size
    }
}
