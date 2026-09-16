package com.eter.salud.domain.dictado

/**
 * Datos clinicos medibles que aparecieron en lo dictado.
 *
 * Solo se extrae lo que la persona DIJO con un numero y un contexto claro
 * ("fiebre de 38.5", "presion 130/85"). Nada se infiere: si no lo dijo, el campo
 * queda en null. Los rangos descartan cifras que no pueden ser ese dato (una
 * "temperatura" de 120 es otra cosa mal reconocida).
 */
data class ResumenClinico(
    /** "38.5 °C" */
    val temperatura: String? = null,
    /** "130/85" */
    val presion: String? = null,
    val pulso: Int? = null,
    val saturacion: Int? = null,
    val glucosa: Int? = null,
    /** Intensidad del dolor en escala del 0 al 10. */
    val dolor: Int? = null,
    /** Desde cuando, tal como se dijo: "hace 3 días", "desde anoche". */
    val desde: String? = null,
) {
    val vacio: Boolean
        get() = listOf(temperatura, presion, pulso, saturacion, glucosa, dolor, desde).all { it == null }

    /** Cada dato en su forma corta, para mostrarlo como chip mientras se dicta. */
    fun piezas(etiquetas: EtiquetasResumen): List<String> = listOfNotNull(
        temperatura?.let { "${etiquetas.temperatura} $it" },
        presion?.let { "${etiquetas.presion} $it mmHg" },
        pulso?.let { "${etiquetas.pulso} $it lpm" },
        saturacion?.let { "${etiquetas.saturacion} $it %" },
        glucosa?.let { "${etiquetas.glucosa} $it mg/dL" },
        dolor?.let { "${etiquetas.dolor} $it/10" },
        desde?.let { "${etiquetas.desde}: $it" },
    )
}

/** Textos ya traducidos del resumen (vienen de `strings.xml`). */
data class EtiquetasResumen(
    val titulo: String = "Resumen",
    val temperatura: String = "Temperatura",
    val presion: String = "Presión",
    val pulso: String = "Pulso",
    val saturacion: String = "Oxigenación",
    val glucosa: String = "Glucosa",
    val dolor: String = "Dolor",
    val desde: String = "Inicio",
)

object ResumenDelDictado {

    fun extraer(texto: String): ResumenClinico {
        val sinResumen = texto.substringBefore(MARCA_RESUMEN)
        val n = ProcesadorDeDictado.normalizar(sinResumen)
        return ResumenClinico(
            temperatura = temperatura(n),
            presion = presion(n),
            pulso = numeroEnRango(n, PULSO, 30..220),
            saturacion = numeroEnRango(n, SATURACION, 50..100),
            glucosa = numeroEnRango(n, GLUCOSA, 20..700),
            dolor = dolor(n),
            desde = desde(n, sinResumen),
        )
    }

    /** Bloque que se agrega al final del mensaje: "Resumen: Temperatura 38.5 °C · Dolor 7/10". */
    fun comoTexto(resumen: ResumenClinico, etiquetas: EtiquetasResumen): String =
        "${etiquetas.titulo}: " + resumen.piezas(etiquetas).joinToString(" · ")

    /** Agrega (o reemplaza) el bloque de resumen al final del texto. */
    fun conResumen(texto: String, resumen: ResumenClinico, etiquetas: EtiquetasResumen): String {
        val cuerpo = quitarResumen(texto).trimEnd()
        if (resumen.vacio || cuerpo.isEmpty()) return cuerpo
        return "$cuerpo$MARCA_RESUMEN${comoTexto(resumen, etiquetas)}"
    }

    /** Quita un bloque de resumen previo: dictar dos veces no lo duplica. */
    fun quitarResumen(texto: String): String = texto.substringBefore(MARCA_RESUMEN)

    /**
     * El bloque va separado del cuerpo por una linea en blanco y una raya. La raya
     * es la marca: el texto de un paciente no la usa, y asi un segundo dictado
     * reemplaza el resumen en vez de acumular dos.
     */
    const val MARCA_RESUMEN = "\n\n— "

    private val TEMPERATURA_CON_UNIDAD = Regex("(\\d{2}(?:[.,]\\d)?) ?°c")
    private val TEMPERATURA_CON_CONTEXTO = Regex("(?:temperatura|fiebre|temperature|fever)[^0-9\\n]{0,20}(\\d{2}(?:[.,]\\d)?)")
    private val PRESION = Regex("(?<!\\d)(\\d{2,3})/(\\d{2,3})(?!\\d)")
    private val PULSO = listOf(
        Regex("(\\d{2,3}) ?(?:lpm|bpm|latidos|pulsaciones)"),
        Regex("(?:pulso|frecuencia cardiaca|latidos|pulsaciones|pulse|heart rate)[^0-9\\n]{0,20}(\\d{2,3})"),
    )
    private val SATURACION = listOf(
        Regex("(?:saturacion|oxigenacion|oxigeno|spo2|oxygen|saturation)[^0-9\\n]{0,20}(\\d{2,3})"),
    )
    private val GLUCOSA = listOf(
        Regex("(?:glucosa|glucemia|azucar|glucose|sugar)[^0-9\\n]{0,20}(\\d{2,3})"),
    )
    private val DOLOR = listOf(
        Regex("(?<![\\d/])(\\d{1,2}) ?(?:/|de|sobre|out of) ?10(?!\\d)"),
        Regex("del? 1 al 10[^0-9\\n]{0,15}(\\d{1,2})"),
    )
    private val DESDE = listOf(
        Regex("hace (?:\\d+|un|una|unos|unas|pocos|pocas|varios|varias|dos|tres|cuatro|cinco) (?:minutos?|horas?|dias?|semanas?|meses?)"),
        Regex("desde (?:ayer|anoche|antier|antes de ayer|hoy|esta manana|esta tarde|esta noche|la manana|el (?:lunes|martes|miercoles|jueves|viernes|sabado|domingo)|hace [a-z0-9 ]{1,20}?(?=[.,;\\n]|$))"),
        Regex("since (?:yesterday|last night|this morning|monday|tuesday|wednesday|thursday|friday|saturday|sunday)"),
        Regex("for \\d+ (?:minutes?|hours?|days?|weeks?)"),
    )

    private fun temperatura(n: String): String? {
        val valor = (TEMPERATURA_CON_UNIDAD.find(n) ?: TEMPERATURA_CON_CONTEXTO.find(n))
            ?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
            ?: return null
        if (valor !in 34.0..43.0) return null
        val texto = if (valor % 1.0 == 0.0) valor.toInt().toString() else valor.toString()
        return "$texto °C"
    }

    private fun presion(n: String): String? {
        PRESION.findAll(n).forEach { m ->
            val sistolica = m.groupValues[1].toInt()
            val diastolica = m.groupValues[2].toInt()
            if (sistolica in 60..260 && diastolica in 30..160 && sistolica > diastolica) return "$sistolica/$diastolica"
        }
        return null
    }

    private fun numeroEnRango(n: String, patrones: List<Regex>, rango: IntRange): Int? =
        patrones.firstNotNullOfOrNull { patron ->
            patron.findAll(n).mapNotNull { it.groupValues[1].toIntOrNull() }.firstOrNull { it in rango }
        }

    private fun dolor(n: String): Int? = numeroEnRango(n, DOLOR, 0..10)

    /** Se busca en el texto normalizado y se devuelve el tramo ORIGINAL (con tildes). */
    private fun desde(n: String, original: String): String? {
        val m = DESDE.firstNotNullOfOrNull { it.find(n) } ?: return null
        return if (original.length == n.length) original.substring(m.range).trim() else m.value.trim()
    }
}
