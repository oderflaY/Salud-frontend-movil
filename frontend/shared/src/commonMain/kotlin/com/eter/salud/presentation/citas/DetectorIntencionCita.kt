package com.eter.salud.presentation.citas

/**
 * Reconoce en el texto del paciente la intencion de agendar una cita.
 *
 * Es deliberadamente un cotejo de frases y no un modelo de lenguaje: el chat de
 * esta app no interpreta sintomas ni conversa, y hacerle creer al paciente que
 * "entiende" lo que escribe seria enganoso en un contexto clinico. Cuando el
 * backend incorpore comprension de lenguaje, esta clase se sustituye entera sin
 * tocar el ViewModel.
 *
 * Por la misma razon el flujo que dispara es reversible: si acierta cuando no
 * debia, el paciente cancela con un toque y sigue escribiendo.
 */
object DetectorIntencionCita {

    /**
     * Frases que abren el flujo. Se exige un verbo de agendar o una peticion
     * explicita de consulta; la palabra "cita" suelta no basta, porque aparece
     * tambien al hablar de una cita pasada ("no pude ir a mi cita").
     */
    private val FRASES_DE_INTENCION = listOf(
        "agendar",
        "agendame",
        "agenda una cita",
        "quiero una cita",
        "necesito una cita",
        "pedir una cita",
        "pedir cita",
        "sacar cita",
        "apartar una cita",
        "programar una cita",
        "reservar una cita",
        "hacer una cita",
        "ver a un doctor",
        "ver a una doctora",
        "ver a un medico",
        "ver a una medica",
        "necesito un doctor",
        "necesito un medico",
    )

    fun quiereAgendar(texto: String): Boolean {
        val normalizado = normalizar(texto)
        return FRASES_DE_INTENCION.any { normalizado.contains(it) }
    }

    /**
     * Minusculas y sin acentos, para que "quiero agendar" y "Quiero Agendár"
     * lleguen iguales al cotejo.
     *
     * La tabla de acentos se escribe a mano porque Kotlin comun no trae
     * normalizacion Unicode: delegarla en cada plataforma haria que el chat
     * reconociera la intencion en Android y no en iOS.
     */
    private fun normalizar(texto: String): String =
        texto.lowercase().map { letra -> ACENTOS[letra] ?: letra }.joinToString("")

    private val ACENTOS = mapOf(
        'á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ü' to 'u', 'ñ' to 'n',
    )
}
