package com.eter.salud.domain.model

/**
 * Estado de una toma programada. Los nombres se corresponden con los valores
 * del nodo `registroAdherencia.estado` del DM_HistorialMedico.md.
 */
enum class EstadoToma(val valorApi: String) {
    PENDIENTE("pendiente"),
    TOMADO("tomado"),
    TOMADO_TARDE("tomado_tarde"),
    OMITIDO("omitido");

    /** Una toma registrada ya no admite cambios desde la pantalla de inicio. */
    val estaRegistrada: Boolean get() = this != PENDIENTE

    companion object {
        fun desdeApi(valor: String?): EstadoToma =
            entries.firstOrNull { it.valorApi == valor } ?: PENDIENTE
    }
}

/**
 * Una toma del dia, ya resuelta para pintarse: junta el horario programado del
 * tratamiento con el registro de adherencia correspondiente.
 */
data class TomaDelDia(
    val idToma: String,
    val idTratamiento: String,
    val medicamento: String,
    val dosis: String,
    /** Hora local `HH:MM`, tal como aparece en `horariosSugeridos`. */
    val horaProgramada: String,
    val estado: EstadoToma,
)

/**
 * Cumplimiento del tratamiento en una ventana de tiempo.
 *
 * Se modela con los dos conteos y no con un porcentaje ya calculado para que la
 * pantalla pueda mostrar tambien "7 de 14", que es lo que un paciente entiende.
 */
data class ResumenAdherencia(
    val tomasProgramadas: Int,
    val tomasCumplidas: Int,
) {
    val sinDatos: Boolean get() = tomasProgramadas == 0

    /** Redondeo al entero mas cercano: 9 de 14 es 64 por ciento, no 64,28. */
    val porcentaje: Int
        get() = if (sinDatos) 0 else (tomasCumplidas * 200 + tomasProgramadas) / (tomasProgramadas * 2)
}
