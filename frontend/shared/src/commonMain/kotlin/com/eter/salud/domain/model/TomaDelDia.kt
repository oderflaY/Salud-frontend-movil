package com.eter.salud.domain.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Estado de una toma programada. Los nombres se corresponden con los valores
 * del nodo `registroAdherencia.estado` del DM_HistorialMedico.md.
 */
@Serializable(with = EstadoTomaSerializer::class)
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
 * Serializa [EstadoToma] por su [EstadoToma.valorApi] (`"tomado"`, no
 * `"TOMADO"`): es el texto en minusculas que persiste el DM, distinto del
 * nombre del enum de Kotlin.
 */
object EstadoTomaSerializer : KSerializer<EstadoToma> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("EstadoToma", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: EstadoToma) = encoder.encodeString(value.valorApi)

    override fun deserialize(decoder: Decoder): EstadoToma = EstadoToma.desdeApi(decoder.decodeString())
}

/**
 * Una toma del dia, ya resuelta para pintarse: junta el horario programado del
 * tratamiento con el registro de adherencia correspondiente.
 */
@Serializable
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
@Serializable
data class ResumenAdherencia(
    val tomasProgramadas: Int,
    val tomasCumplidas: Int,
) {
    val sinDatos: Boolean get() = tomasProgramadas == 0

    /** Redondeo al entero mas cercano: 9 de 14 es 64 por ciento, no 64,28. */
    val porcentaje: Int
        get() = if (sinDatos) 0 else (tomasCumplidas * 200 + tomasProgramadas) / (tomasProgramadas * 2)
}
