package com.eter.salud.domain.diario

import kotlinx.serialization.Serializable

/**
 * Semaforo de una entrada del diario.
 *
 * NO es un diagnostico y la app no debe presentarlo como tal. Es un orden de
 * lectura para el medico: con veinte pacientes escribiendo, dice por cual
 * empezar. Confundirlo con un dictamen clinico seria el error mas grave que este
 * modulo puede cometer.
 *
 * [codigo] es el valor que se persiste en SQLite (0 = verde, 1 = ambar,
 * 2 = rojo). Es explicito y no el `ordinal`: reordenar el enum algun dia no
 * puede cambiar el significado de filas ya guardadas.
 */
@Serializable
enum class SeveridadDiario(val codigo: Int) {
    VERDE(0),
    AMBAR(1),
    ROJO(2);

    companion object {
        /** Un codigo desconocido se lee como ROJO: ante la duda, mas grave. */
        fun desdeCodigo(codigo: Int): SeveridadDiario = entries.firstOrNull { it.codigo == codigo } ?: ROJO
    }
}

/**
 * Categoria semantica de un termino vigilado.
 *
 * La division fisico / mental no es decorativa: el medico no lee igual "tengo
 * fiebre" que "tengo ansiedad", y una mencion de autolesion exige una respuesta
 * distinta (una linea de crisis, no una cita). Por eso el resultado dice QUE
 * categorias se tocaron, ademas del color.
 */
enum class CategoriaTriage(val severidad: SeveridadDiario) {
    URGENCIA_FISICA(SeveridadDiario.ROJO),
    URGENCIA_MENTAL(SeveridadDiario.ROJO),
    PRECAUCION_FISICA(SeveridadDiario.AMBAR),
    PRECAUCION_MENTAL(SeveridadDiario.AMBAR),
}

/**
 * Lo que el triage encontro en un texto.
 *
 * @property palabrasDetectadas los terminos EXACTOS que dispararon el semaforo,
 * en su forma legible ("opresión", "mareo"), sin repetir y en el orden en que se
 * evaluaron. Es lo que el medico ve para entender POR QUE ese color.
 */
data class ResultadoTriage(
    val severidad: SeveridadDiario,
    val palabrasDetectadas: List<String>,
    val categorias: Set<CategoriaTriage>,
) {
    /** Hay mencion de riesgo para la propia vida: se ofrece la linea de crisis. */
    val requiereLineaDeCrisis: Boolean get() = CategoriaTriage.URGENCIA_MENTAL in categorias

    companion object {
        val VACIO = ResultadoTriage(SeveridadDiario.VERDE, emptyList(), emptySet())
    }
}

/**
 * Motor de triage local del diario: un cotejo de terminos por palabra completa,
 * dividido en categorias semanticas.
 *
 * ## Que es y que no es
 *
 * Es un cotejo, no comprension de lenguaje. No entiende negaciones ("ya NO me
 * duele"), ni ironia, ni contexto. Por eso el resultado se llama severidad y no
 * diagnostico, y por eso el medico ve SIEMPRE el texto completo junto al color.
 *
 * ## Las reglas, en orden
 *
 *  1. Cualquier termino de urgencia (fisica o mental): **ROJO**. Un solo
 *     "desmayo" o un solo "suicidio" bastan; no se espera a que se acumulen.
 *  2. Tres o mas terminos de precaucion: **ROJO**. Fiebre, mareo y vomito juntos
 *     dibujan un cuadro que ninguno dibuja solo.
 *  3. Uno o dos de precaucion: **AMBAR**.
 *  4. Ninguno: **VERDE**.
 *
 * Se equivoca hacia arriba a proposito: un falso ambar cuesta que el medico lea
 * una entrada trivial; un falso verde cuesta que no lea una que no lo era.
 *
 * ## Palabra completa, nunca subcadena
 *
 * "tos" no marca "estos" ni "todos"; "morir" no marca "amorirse" pero si
 * "morir". Se comprueba que el caracter anterior y el posterior al termino no
 * sean letras. Mayusculas y acentos se normalizan antes: "OPRESIÓN" y
 * "opresion" son la misma palabra.
 *
 * ## Es una funcion pura
 *
 * Sin estado, sin reloj, sin red: el mismo texto da el mismo resultado en
 * Android, en iOS y en el backend. Eso es lo que permite calcularlo EN VIVO
 * mientras el paciente teclea, y lo que hace que el valor guardado sea
 * auditable: [VERSION_DICCIONARIO] queda grabado junto a cada entrada.
 */
object TriageDelDiario {

    /**
     * Version del diccionario. Se guarda con cada entrada: si dentro de meses el
     * diccionario cambia, una entrada vieja sigue diciendo con que reglas se
     * calculo su color, y el expediente no se reescribe solo.
     */
    const val VERSION_DICCIONARIO = 2

    /**
     * El diccionario. Cada termino en su forma LEGIBLE (con acentos); el cotejo
     * usa su version normalizada. Las frases largas van antes que las cortas que
     * contienen: "opresión en el pecho" debe ganarle a "opresión".
     */
    private val DICCIONARIO: Map<CategoriaTriage, List<String>> = mapOf(
        CategoriaTriage.URGENCIA_FISICA to listOf(
            "opresión en el pecho", "dolor en el pecho", "no puedo respirar", "falta de aire",
            "visión borrosa", "habla arrastrada", "sangrado", "sangre", "desmayé", "desmayo",
            "opresión", "asfixia", "ahogo", "convulsión", "adormecido", "no siento",
        ),
        CategoriaTriage.URGENCIA_MENTAL to listOf(
            "quitarme la vida", "no quiero vivir", "hacerme daño", "suicidarme", "suicidio",
            "autolesionarme", "autolesión", "matarme", "morirme", "morir",
        ),
        CategoriaTriage.PRECAUCION_FISICA to listOf(
            "dolor fuerte", "presión alta", "fiebre", "mareos", "mareo", "vómitos", "vómito",
            "náuseas", "náusea", "hinchazón", "hinchados", "palpitaciones", "cansancio",
            "debilidad", "diarrea", "dolor", "tos",
        ),
        CategoriaTriage.PRECAUCION_MENTAL to listOf(
            "ataques de pánico", "ataque de pánico", "ansiedad", "pánico", "insomnio",
            "desesperación", "angustia", "tristeza profunda",
        ),
    )

    /** Precaucion acumulada a partir de la cual el cuadro pasa a ROJO. */
    private const val PRECAUCIONES_PARA_ROJO = 3

    /** Evalua un texto: severidad, palabras exactas detectadas y categorias. */
    fun evaluar(texto: String): ResultadoTriage {
        val normalizado = normalizar(texto)
        if (normalizado.isBlank()) return ResultadoTriage.VACIO

        // Hallazgos en orden de diccionario, sin solaparse: un termino contenido
        // en otro ya hallado ("dolor" dentro de "dolor en el pecho", "pánico"
        // dentro de "ataque de pánico") no cuenta dos veces.
        val hallazgos = mutableListOf<Pair<String, CategoriaTriage>>()
        DICCIONARIO.forEach { (categoria, terminos) ->
            terminos.forEach { termino ->
                val clave = normalizar(termino)
                val contenidoEnOtro = hallazgos.any { (ya, _) -> normalizar(ya).contains(clave) }
                if (!contenidoEnOtro && normalizado.contieneTermino(clave)) {
                    hallazgos += termino to categoria
                }
            }
        }
        if (hallazgos.isEmpty()) return ResultadoTriage.VACIO

        val categorias = hallazgos.map { it.second }.toSet()
        val urgencias = hallazgos.count { it.second.severidad == SeveridadDiario.ROJO }
        val precauciones = hallazgos.size - urgencias
        val severidad = when {
            urgencias > 0 || precauciones >= PRECAUCIONES_PARA_ROJO -> SeveridadDiario.ROJO
            else -> SeveridadDiario.AMBAR
        }
        return ResultadoTriage(
            severidad = severidad,
            palabrasDetectadas = hallazgos.map { it.first }.distinct(),
            categorias = categorias,
        )
    }

    /** Atajo: solo el color. */
    fun clasificar(texto: String): SeveridadDiario = evaluar(texto).severidad

    /** Atajo: solo las palabras detectadas, para mostrarlas al medico. */
    fun terminosDetectados(texto: String): List<String> = evaluar(texto).palabrasDetectadas

    /**
     * Cotejo por palabra completa: los caracteres de alrededor no pueden ser
     * letras. Sin esto, "tos" marcaria "estos" y el diario entero saldria ambar.
     */
    private fun String.contieneTermino(termino: String): Boolean {
        var desde = 0
        while (true) {
            val indice = indexOf(termino, desde)
            if (indice < 0) return false
            val anterior = getOrNull(indice - 1)
            val siguiente = getOrNull(indice + termino.length)
            if (anterior?.isLetter() != true && siguiente?.isLetter() != true) return true
            desde = indice + 1
        }
    }

    /**
     * Minusculas y sin acentos. La tabla se escribe a mano porque Kotlin comun
     * no trae normalizacion Unicode: delegarla en cada plataforma haria que el
     * mismo texto se clasificara distinto en Android y en iOS.
     */
    private fun normalizar(texto: String): String =
        texto.lowercase().map { ACENTOS[it] ?: it }.joinToString("")

    private val ACENTOS = mapOf(
        'á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ü' to 'u', 'ñ' to 'n',
    )
}
