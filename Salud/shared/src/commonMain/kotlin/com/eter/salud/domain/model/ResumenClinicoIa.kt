package com.eter.salud.domain.model

import kotlinx.serialization.Serializable

/** Un punto del resumen: una etiqueta clinica y su valor extraido ("Duracion" / "3 dias"). */
@Serializable
data class PuntoResumenIa(
    val etiqueta: String,
    val valor: String,
)

/**
 * El resumen que la IA del backend extrae de un mensaje largo del paciente,
 * mas el mensaje completo que respalda ese resumen.
 *
 * Vive en el dominio y no en `ui/componentes` (donde nacio junto a la tarjeta
 * que lo pinta): es un dato que sale de la red, no un detalle de la Vista. Se
 * calcula SIEMPRE en el backend, nunca en el cliente -- a diferencia del
 * triage del diario (una regla determinista, reproducible en cualquier
 * plataforma), un resumen clinico es una interpretacion del texto, y esa
 * interpretacion la hace el modelo que el backend decida usar, no el
 * telefono.
 */
@Serializable
data class ResumenClinicoIa(
    val puntos: List<PuntoResumenIa>,
    val mensajeOriginal: String,
) {
    /** Cuantas palabras tiene el mensaje sin resumir, para la etiqueta del acordeon. */
    val numeroPalabrasOriginal: Int
        get() = mensajeOriginal.trim().split(EXPRESION_ESPACIOS).count { it.isNotBlank() }

    private companion object {
        val EXPRESION_ESPACIOS = Regex("\\s+")
    }
}
