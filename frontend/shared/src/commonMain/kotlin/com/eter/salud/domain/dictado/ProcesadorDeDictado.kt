package com.eter.salud.domain.dictado

/**
 * Convierte lo que devuelve el reconocedor de voz en texto listo para enviar.
 *
 * El reconocedor entrega palabras sueltas y sin formato ("treinta y ocho y medio
 * grados coma me duele la cabeza punto"). Aqui se aplican, en este orden:
 *
 *  1. Muletillas fuera ("eh", "mmm") y tartamudeo ("me me duele").
 *  2. Numeros hablados a cifras cuando importan: "treinta y ocho y medio" ->
 *     "38.5", "ciento veinte" -> "120". Los numeros chicos sin unidad se dejan
 *     en letra ("tengo dos hijos"), como se escriben en prosa.
 *  3. Puntuacion dictada: "coma", "punto", "punto y aparte", "signo de
 *     interrogacion"... con guardas para las frases donde esa palabra NO es un
 *     signo ("estoy a punto de", "entro en coma").
 *  4. Unidades clinicas: "38.5 grados" -> "38.5 °C", "120 sobre 80" ->
 *     "120/80", "93 por ciento" -> "93 %".
 *
 * Es una funcion pura y barata (una pasada lineal): se ejecuta en cada resultado
 * parcial del reconocedor sin que el texto en pantalla se retrase.
 */
object ProcesadorDeDictado {

    /** Texto crudo de UN segmento del reconocedor, ya limpio (sin mayusculas de oracion). */
    fun procesar(texto: String): String {
        val tokens = texto.trim().split(ESPACIOS).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return ""
        var piezas: List<Pieza> = tokens.flatMap(::separarSignos)
        piezas = quitarMuletillasYRepeticiones(piezas)
        piezas = convertirNumeros(piezas)
        piezas = aplicarComandos(piezas)
        piezas = aplicarUnidades(piezas)
        return renderizar(piezas)
    }

    /**
     * Une segmentos ya procesados. Cada segmento termina en una pausa de quien
     * dicta: si no trae su propio signo y es una frase (3 palabras o mas), la
     * pausa se toma como fin de oracion.
     */
    fun unirSegmentos(segmentos: List<String>): String {
        val sb = StringBuilder()
        segmentos.map { it.trim(' ') }.filter { it.isNotEmpty() }.forEach { segmento ->
            if (sb.isNotEmpty()) {
                val ultimo = sb.last()
                val palabrasPrevias = sb.substring(inicioDeUltimaOracion(sb)).split(ESPACIOS).count { it.any(Char::isLetterOrDigit) }
                if (ultimo.isLetterOrDigit() && palabrasPrevias >= PALABRAS_PARA_CERRAR_POR_PAUSA && segmento.first() !in ",.;:?!)") {
                    sb.append('.')
                }
                if (!sb.last().isWhitespace() && segmento.first() !in ",.;:?!)") sb.append(' ')
            }
            sb.append(segmento)
        }
        return sb.toString()
    }

    /** Lo que se ve en el campo mientras se dicta: el texto previo mas lo dictado. */
    fun vista(base: String, segmentos: List<String>, parcial: String = ""): String {
        val dictado = unirSegmentos(if (parcial.isBlank()) segmentos else segmentos + parcial)
        val combinado = when {
            dictado.isBlank() -> base
            base.isBlank() -> dictado
            base.endsWith('\n') -> base + dictado
            else -> base.trimEnd() + " " + dictado
        }
        return capitalizar(combinado)
    }

    /** Mayuscula al inicio de cada oracion, parrafo o pregunta. */
    fun capitalizar(texto: String): String {
        val sb = StringBuilder(texto.length)
        var mayuscula = true
        var tras = false
        for (c in texto) {
            when {
                c == '\n' -> { mayuscula = true; tras = false; sb.append(c) }
                c in ".?!" -> { tras = true; sb.append(c) }
                c.isWhitespace() -> { if (tras) { mayuscula = true; tras = false }; sb.append(c) }
                c.isLetter() -> {
                    tras = false
                    sb.append(if (mayuscula) c.uppercaseChar() else c)
                    mayuscula = false
                }
                c.isDigit() -> { tras = false; mayuscula = false; sb.append(c) }
                else -> { tras = false; sb.append(c) }
            }
        }
        return sb.toString()
    }

    /** Version final al terminar de dictar: mayusculas y punto final si falta. */
    fun cerrar(texto: String): String {
        val limpio = capitalizar(texto).trimEnd()
        if (limpio.isEmpty()) return ""
        val ultimo = limpio.last()
        return if (ultimo.isLetterOrDigit() || ultimo in "%)") "$limpio." else limpio
    }

    // ------------------------------------------------------------------ piezas

    private sealed interface Pieza
    private data class Palabra(val texto: String) : Pieza {
        val clave: String = normalizar(texto)
    }
    private data class Signo(val signo: String) : Pieza

    private fun separarSignos(token: String): List<Pieza> {
        var inicio = 0
        var fin = token.length
        val antes = mutableListOf<Pieza>()
        val despues = mutableListOf<Pieza>()
        while (inicio < fin && token[inicio] in "¿¡(") { antes += Signo(token[inicio].toString()); inicio++ }
        while (fin > inicio && token[fin - 1] in ".,;:?!)") { despues.add(0, Signo(token[fin - 1].toString())); fin-- }
        val nucleo = token.substring(inicio, fin)
        return antes + (if (nucleo.isNotEmpty()) listOf(Palabra(nucleo)) else emptyList()) + despues
    }

    private fun quitarMuletillasYRepeticiones(piezas: List<Pieza>): List<Pieza> {
        val salida = mutableListOf<Pieza>()
        for (p in piezas) {
            if (p is Palabra) {
                if (p.clave in MULETILLAS) continue
                val previa = salida.lastOrNull() as? Palabra
                if (previa != null && previa.clave == p.clave && p.clave.length > 1 && p.clave.all(Char::isLetter)) continue
            }
            salida += p
        }
        return salida
    }

    // ------------------------------------------------------------------ numeros

    private fun convertirNumeros(piezas: List<Pieza>): List<Pieza> {
        val salida = mutableListOf<Pieza>()
        var i = 0
        while (i < piezas.size) {
            val lectura = leerNumero(piezas, i)
            if (lectura == null) {
                salida += piezas[i]
                i++
                continue
            }
            val (valor, consumidas, soloLetras) = lectura
            val siguiente = (piezas.getOrNull(i + consumidas) as? Palabra)?.clave
            val tieneUnidad = siguiente in PALABRAS_DE_UNIDAD
            val convertir = !soloLetras || valor >= 10 || tieneUnidad
            if (convertir) {
                salida += Palabra(formatear(valor))
            } else {
                for (k in i until i + consumidas) salida += piezas[k]
            }
            i += consumidas
        }
        return salida
    }

    private data class Lectura(val valor: Double, val consumidas: Int, val soloLetras: Boolean)

    private fun leerNumero(piezas: List<Pieza>, desde: Int): Lectura? {
        val primera = piezas[desde] as? Palabra ?: return null
        // "por ciento" es una unidad, no el numero 100.
        if (primera.clave == "ciento" && (piezas.getOrNull(desde - 1) as? Palabra)?.clave == "por") return null
        var valor: Double
        var j: Int
        var soloLetras = true

        val digitos = primera.texto.replace(',', '.').toDoubleOrNull()
        if (digitos != null && primera.texto.first().isDigit()) {
            valor = digitos
            j = desde + 1
            soloLetras = false
        } else {
            val entero = leerEnteroEnLetras(piezas, desde) ?: return null
            valor = entero.first.toDouble()
            j = entero.second
        }

        // "y medio" y "punto cinco" / "coma cinco" despues de un entero.
        val sig1 = (piezas.getOrNull(j) as? Palabra)?.clave
        val sig2 = piezas.getOrNull(j + 1) as? Palabra
        if (valor % 1.0 == 0.0) {
            if (sig1 == "y" && sig2?.clave == "medio") {
                return Lectura(valor + 0.5, j + 2 - desde, soloLetras)
            }
            if ((sig1 == "punto" || sig1 == "coma") && sig2 != null) {
                val decimal = UNIDADES[sig2.clave] ?: sig2.texto.toIntOrNull()?.takeIf { it in 0..9 }
                if (decimal != null) return Lectura(valor + decimal / 10.0, j + 2 - desde, false)
            }
        }
        return Lectura(valor, j - desde, soloLetras)
    }

    /** "ciento veinte", "treinta y ocho", "quince". Devuelve (valor, indice siguiente). */
    private fun leerEnteroEnLetras(piezas: List<Pieza>, desde: Int): Pair<Int, Int>? {
        fun clave(k: Int) = (piezas.getOrNull(k) as? Palabra)?.clave
        var j = desde
        var total = 0
        var algo = false

        CENTENAS[clave(j)]?.let { total += it; j++; algo = true }
        val c = clave(j)
        when {
            c != null && c in ESPECIALES -> { total += ESPECIALES.getValue(c); j++; algo = true }
            c != null && c in DECENAS -> {
                total += DECENAS.getValue(c); j++; algo = true
                val unidad = clave(j + 1)?.let { UNIDADES[it] }
                if (clave(j) == "y" && unidad != null && unidad > 0) { total += unidad; j += 2 }
            }
            c != null && c in UNIDADES && !(c in ARTICULOS && !algo) -> { total += UNIDADES.getValue(c); j++; algo = true }
        }
        return if (algo) total to j else null
    }

    private fun formatear(valor: Double): String =
        if (valor % 1.0 == 0.0) valor.toLong().toString() else {
            val decimas = kotlin.math.round(valor * 10).toLong()
            "${decimas / 10}.${decimas % 10}"
        }

    // ------------------------------------------------------------------ signos dictados

    private fun aplicarComandos(piezas: List<Pieza>): List<Pieza> {
        val salida = mutableListOf<Pieza>()
        var i = 0
        buscar@ while (i < piezas.size) {
            for ((frase, signo) in COMANDOS) {
                if (coincide(piezas, i, frase) && !esUsoLiteral(piezas, i, frase)) {
                    salida += Signo(signo)
                    i += frase.size
                    continue@buscar
                }
            }
            salida += piezas[i]
            i++
        }
        return salida
    }

    private fun coincide(piezas: List<Pieza>, desde: Int, frase: List<String>): Boolean =
        frase.indices.all { k -> (piezas.getOrNull(desde + k) as? Palabra)?.clave == frase[k] }

    /** "a punto de", "entro en coma", "my period": la palabra no es un signo. */
    private fun esUsoLiteral(piezas: List<Pieza>, i: Int, frase: List<String>): Boolean {
        if (frase.size != 1) return false
        val anterior = (piezas.getOrNull(i - 1) as? Palabra)?.clave
        val siguiente = (piezas.getOrNull(i + 1) as? Palabra)?.clave
        return when (frase[0]) {
            "punto" -> anterior in ANTES_DE_PUNTO_LITERAL || siguiente in DESPUES_DE_PUNTO_LITERAL
            "coma" -> anterior in ANTES_DE_COMA_LITERAL || siguiente in DESPUES_DE_COMA_LITERAL
            "period" -> anterior in ANTES_DE_PERIOD_LITERAL
            else -> false
        }
    }

    // ------------------------------------------------------------------ unidades

    private fun aplicarUnidades(piezas: List<Pieza>): List<Pieza> {
        val salida = mutableListOf<Pieza>()
        var i = 0
        while (i < piezas.size) {
            val p = piezas[i]
            val numero = (p as? Palabra)?.texto?.toDoubleOrNull()
            if (p is Palabra && numero != null) {
                fun clave(k: Int) = (piezas.getOrNull(i + k) as? Palabra)?.clave
                val segundoNumero = (piezas.getOrNull(i + 2) as? Palabra)?.texto?.toIntOrNull()
                when {
                    clave(1) in setOf("grados", "degrees") && numero in 30.0..45.0 -> {
                        val extra = if (clave(2) in setOf("centigrados", "celsius")) 1 else 0
                        salida += Palabra("${p.texto} °C"); i += 2 + extra; continue
                    }
                    clave(1) == "por" && clave(2) == "ciento" -> { salida += Palabra("${p.texto} %"); i += 3; continue }
                    clave(1) in setOf("porciento", "percent") -> { salida += Palabra("${p.texto} %"); i += 2; continue }
                    clave(1) == "sobre" && segundoNumero != null && p.texto.length in 2..3 && segundoNumero in 30..200 -> {
                        salida += Palabra("${p.texto}/$segundoNumero"); i += 3; continue
                    }
                    clave(1) == "latidos" && clave(2) == "por" && clave(3) == "minuto" -> { salida += Palabra("${p.texto} lpm"); i += 4; continue }
                    clave(1) in UNIDADES_ABREVIADAS -> { salida += Palabra("${p.texto} ${UNIDADES_ABREVIADAS.getValue(clave(1)!!)}"); i += 2; continue }
                }
            }
            salida += p
            i++
        }
        return salida
    }

    // ------------------------------------------------------------------ render

    private fun renderizar(piezas: List<Pieza>): String {
        val sb = StringBuilder()
        var inicioOracion = 0
        fun recortar() { while (sb.isNotEmpty() && sb.last() == ' ') sb.setLength(sb.length - 1) }

        for (p in piezas) {
            when (p) {
                is Palabra -> {
                    if (sb.isNotEmpty() && !sb.last().isWhitespace() && sb.last() !in "(¿¡") sb.append(' ')
                    sb.append(p.texto)
                }
                is Signo -> when (p.signo) {
                    "\n", "\n\n" -> {
                        recortar()
                        // "Punto y aparte" cierra la oracion; "nueva linea" solo salta.
                        if (p.signo == "\n\n" && sb.isNotEmpty() && (sb.last().isLetterOrDigit() || sb.last() in "%)")) sb.append('.')
                        if (sb.isNotEmpty()) sb.append(p.signo)
                        inicioOracion = sb.length
                    }
                    "(", "¿", "¡" -> {
                        if (sb.isNotEmpty() && !sb.last().isWhitespace() && sb.last() !in "(¿¡") sb.append(' ')
                        sb.append(p.signo)
                    }
                    "?", "!" -> {
                        recortar()
                        if (sb.isEmpty()) continue
                        val apertura = if (p.signo == "?") '¿' else '¡'
                        if (apertura !in sb.substring(inicioOracion)) {
                            var k = inicioOracion
                            while (k < sb.length && sb[k].isWhitespace()) k++
                            sb.insert(k, apertura)
                        }
                        sb.append(p.signo)
                        inicioOracion = sb.length
                    }
                    "." -> {
                        recortar()
                        if (sb.isNotEmpty() && sb.last() !in ".?!\n") sb.append('.')
                        inicioOracion = sb.length
                    }
                    else -> {
                        recortar()
                        if (sb.isNotEmpty() && sb.last() !in ",;:.?!\n") sb.append(p.signo)
                    }
                }
            }
        }
        return sb.toString().trimStart().trimEnd(' ')
    }

    private fun inicioDeUltimaOracion(sb: StringBuilder): Int {
        for (k in sb.length - 1 downTo 0) if (sb[k] in ".?!\n") return k + 1
        return 0
    }

    /** Minusculas y sin acentos: el reconocedor no es consistente con las tildes. */
    internal fun normalizar(texto: String): String =
        texto.lowercase().map { ACENTOS[it] ?: it }.joinToString("")

    private val ACENTOS = mapOf('á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ü' to 'u')
    private val ESPACIOS = Regex("\\s+")
    private const val PALABRAS_PARA_CERRAR_POR_PAUSA = 3

    private val MULETILLAS = setOf("eh", "ehh", "eeh", "em", "emm", "ehm", "mm", "mmm", "hmm", "uh", "um")

    private val ARTICULOS = setOf("un", "una", "uno")
    private val UNIDADES = mapOf(
        "cero" to 0, "un" to 1, "uno" to 1, "una" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4,
        "cinco" to 5, "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9,
    )
    private val ESPECIALES = mapOf(
        "diez" to 10, "once" to 11, "doce" to 12, "trece" to 13, "catorce" to 14, "quince" to 15,
        "dieciseis" to 16, "diecisiete" to 17, "dieciocho" to 18, "diecinueve" to 19, "veinte" to 20,
        "veintiun" to 21, "veintiuno" to 21, "veintiuna" to 21, "veintidos" to 22, "veintitres" to 23,
        "veinticuatro" to 24, "veinticinco" to 25, "veintiseis" to 26, "veintisiete" to 27,
        "veintiocho" to 28, "veintinueve" to 29,
    )
    private val DECENAS = mapOf(
        "treinta" to 30, "cuarenta" to 40, "cincuenta" to 50, "sesenta" to 60,
        "setenta" to 70, "ochenta" to 80, "noventa" to 90,
    )
    private val CENTENAS = mapOf(
        "cien" to 100, "ciento" to 100, "doscientos" to 200, "doscientas" to 200, "trescientos" to 300,
        "trescientas" to 300, "cuatrocientos" to 400, "cuatrocientas" to 400, "quinientos" to 500,
        "quinientas" to 500, "seiscientos" to 600, "seiscientas" to 600, "setecientos" to 700,
        "setecientas" to 700, "ochocientos" to 800, "ochocientas" to 800, "novecientos" to 900,
        "novecientas" to 900,
    )

    /** Palabras que convierten en cifra incluso un numero chico: "dos miligramos". */
    private val PALABRAS_DE_UNIDAD = setOf(
        "grados", "degrees", "por", "porciento", "percent", "sobre", "miligramos", "mililitros", "gramos",
        "kilos", "kilogramos", "latidos", "pulsaciones", "lpm", "mg", "ml", "de", "pastillas", "tabletas", "gotas",
    )

    private val UNIDADES_ABREVIADAS = mapOf(
        "miligramos" to "mg", "mililitros" to "ml", "gramos" to "g", "kilos" to "kg", "kilogramos" to "kg",
        "pulsaciones" to "lpm",
    )

    /** De la frase mas larga a la mas corta: "punto y aparte" gana a "punto". */
    private val COMANDOS: List<Pair<List<String>, String>> = listOf(
        listOf("signo", "de", "interrogacion") to "?",
        listOf("signo", "de", "pregunta") to "?",
        listOf("signo", "de", "exclamacion") to "!",
        listOf("punto", "y", "aparte") to "\n\n",
        listOf("punto", "y", "seguido") to ".",
        listOf("punto", "y", "coma") to ";",
        listOf("salto", "de", "linea") to "\n",
        listOf("abre", "interrogacion") to "¿",
        listOf("cierra", "interrogacion") to "?",
        listOf("abre", "exclamacion") to "¡",
        listOf("cierra", "exclamacion") to "!",
        listOf("abre", "parentesis") to "(",
        listOf("cierra", "parentesis") to ")",
        listOf("nuevo", "parrafo") to "\n\n",
        listOf("nueva", "linea") to "\n",
        listOf("dos", "puntos") to ":",
        listOf("punto", "final") to ".",
        listOf("new", "paragraph") to "\n\n",
        listOf("new", "line") to "\n",
        listOf("question", "mark") to "?",
        listOf("exclamation", "mark") to "!",
        listOf("full", "stop") to ".",
        listOf("punto") to ".",
        listOf("coma") to ",",
        listOf("comma") to ",",
        listOf("period") to ".",
    )

    private val ANTES_DE_PUNTO_LITERAL = setOf(
        "a", "al", "el", "un", "ese", "este", "aquel", "mismo", "cada", "ningun", "algun", "buen", "mal", "su", "mi", "tu", "del",
    )
    private val DESPUES_DE_PUNTO_LITERAL = setOf("de", "del", "en", "medio", "exacto", "exactamente", "debil", "critico")
    private val ANTES_DE_COMA_LITERAL = setOf("en", "un", "el", "estado", "de", "del", "la", "su", "mi", "al")
    private val DESPUES_DE_COMA_LITERAL = setOf("diabetico", "inducido", "etilico", "profundo", "hepatico")
    private val ANTES_DE_PERIOD_LITERAL = setOf("my", "the", "a", "her", "last", "first", "this", "that", "your")
}
