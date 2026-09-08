package com.eter.salud.domain.time

/**
 * Aritmetica sobre instantes ISO 8601 UTC (`YYYY-MM-DDTHH:MM:SSZ`).
 *
 * Existe por una sola razon: el bloqueo temporal de una franja mientras el
 * paciente captura sus datos. Para saber si esa retencion caduco hay que sumar
 * minutos a un instante y comparar, y ninguna de las dos cosas se puede hacer
 * sobre texto sin cuentas.
 *
 * Se implementa en codigo comun, como [CalendarioSalud], para que Android e iOS
 * decidan identico: una franja que caduca en un telefono y no en el otro
 * permitiria que dos pacientes agendaran la misma hora.
 */
object InstanteSalud {

    private val FORMATO = Regex("""(\d{4}-\d{2}-\d{2})T(\d{2}):(\d{2}):(\d{2})Z""")

    private const val MINUTOS_POR_DIA = 24 * 60

    /**
     * Suma [minutos] (puede ser negativo) a un instante ISO 8601 UTC.
     *
     * Devuelve nulo si el texto no tiene el formato exacto. Nulo y no el propio
     * instante a proposito: una caducidad calculada sobre un instante que no se
     * entendio seria una retencion que nunca expira, y esa franja quedaria
     * bloqueada para siempre.
     */
    fun sumarMinutos(instanteIso: String, minutos: Int): String? {
        val partes = FORMATO.matchEntire(instanteIso.trim())?.groupValues ?: return null
        val fecha = partes[1]
        val segundos = partes[4]

        val minutoDelDia = partes[2].toInt() * 60 + partes[3].toInt() + minutos
        // `floorDiv` y `mod` y no `/` y `%`: con minutos negativos que cruzan la
        // medianoche, la division entera de Kotlin trunca hacia cero y daria el
        // dia equivocado.
        val diasDesplazados = minutoDelDia.floorDiv(MINUTOS_POR_DIA)
        val minutoNormalizado = minutoDelDia.mod(MINUTOS_POR_DIA)

        val fechaFinal = CalendarioSalud.sumarDias(fecha, diasDesplazados)
        val hora = (minutoNormalizado / 60).aDosDigitos()
        val minuto = (minutoNormalizado % 60).aDosDigitos()
        return "${fechaFinal}T$hora:$minuto:${segundos}Z"
    }

    /**
     * Cierto si [instante] ya paso de [limite].
     *
     * La comparacion es lexicografica y no numerica porque el formato es de
     * ancho fijo y va de la unidad mayor a la menor: para dos instantes ISO 8601
     * UTC bien formados, comparar el texto da el mismo orden que comparar el
     * tiempo. Si alguno no lo esta, se responde `false` (no ha caducado) en vez
     * de adivinar.
     */
    fun caduco(instante: String, limite: String): Boolean {
        if (!esInstanteValido(instante) || !esInstanteValido(limite)) return false
        return instante > limite
    }

    fun esInstanteValido(instanteIso: String): Boolean =
        FORMATO.matchEntire(instanteIso.trim()) != null

    /** Extrae la fecha civil (`YYYY-MM-DD`) de un instante, o cadena vacia. */
    fun fechaDe(instanteIso: String): String =
        FORMATO.matchEntire(instanteIso.trim())?.groupValues?.get(1).orEmpty()

    private fun Int.aDosDigitos(): String = toString().padStart(2, '0')
}
