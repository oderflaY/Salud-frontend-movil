package com.eter.salud.domain.time

/**
 * Aritmetica del calendario civil sobre fechas `YYYY-MM-DD`.
 *
 * Se implementa en codigo comun, y no delegando en cada plataforma, por dos
 * razones: el resultado tiene que ser identico en Android y iOS, y asi el
 * selector de fechas se puede probar sin depender del reloj del dispositivo.
 */
object CalendarioSalud {

    private val FORMATO = Regex("""(\d{4})-(\d{2})-(\d{2})""")

    private const val DIAS_POR_SEMANA = 7
    private const val DIAS_POR_MES_APROXIMADO = 30

    /** Partes de una fecha, ya normalizadas a dos y cuatro digitos. */
    data class PartesFecha(val dia: String, val mes: String, val anio: String)

    fun esFechaValida(fecha: String): Boolean = descomponerNumerica(fecha) != null

    /** Devuelve la fecha [dias] antes de [fecha], o la propia si no es valida. */
    fun restarDias(fecha: String, dias: Int): String {
        val partes = descomponerNumerica(fecha) ?: return fecha
        val (anio, mes, dia) = partes
        return fechaDesdeDiasEpoca(diasDesdeEpoca(anio, mes, dia) - dias)
    }

    fun restarSemanas(fecha: String, semanas: Int): String =
        restarDias(fecha, semanas * DIAS_POR_SEMANA)

    /** Un "mes" comercial de 30 dias: basta para una opcion rapida de captura. */
    fun restarMeses(fecha: String, meses: Int): String =
        restarDias(fecha, meses * DIAS_POR_MES_APROXIMADO)

    /** Devuelve la fecha [dias] despues de [fecha], o la propia si no es valida. */
    fun sumarDias(fecha: String, dias: Int): String = restarDias(fecha, -dias)

    /**
     * Dias de [inicio] a [fin], negativos si [fin] es anterior. Nulo si alguna
     * de las dos no existe en el calendario.
     *
     * La usa la agenda del medico para recorrer el rango visible dia a dia sin
     * que la Vista tenga que hacer cuentas de calendario.
     */
    fun diasEntre(inicio: String, fin: String): Int? {
        val (anioInicio, mesInicio, diaInicio) = descomponerNumerica(inicio) ?: return null
        val (anioFin, mesFin, diaFin) = descomponerNumerica(fin) ?: return null
        val diferencia = diasDesdeEpoca(anioFin, mesFin, diaFin) -
            diasDesdeEpoca(anioInicio, mesInicio, diaInicio)
        return diferencia.toInt()
    }

    /** Primer dia del mes al que pertenece [fecha]; nulo si no es valida. */
    fun primerDiaDelMes(fecha: String): String? {
        val (anio, mes, _) = descomponerNumerica(fecha) ?: return null
        return "${anio.acolchado(4)}-${mes.acolchado(2)}-01"
    }

    /**
     * Ultimo dia del mes al que pertenece [fecha]; nulo si no es valida.
     * Respeta los anios bisiestos, igual que el resto del objeto.
     */
    fun ultimoDiaDelMes(fecha: String): String? {
        val (anio, mes, _) = descomponerNumerica(fecha) ?: return null
        return "${anio.acolchado(4)}-${mes.acolchado(2)}-${diasDelMes(anio, mes).acolchado(2)}"
    }

    /**
     * Dia de la semana, con el lunes como 0 y el domingo como 6.
     *
     * El 1970-01-01 fue jueves, de ahi el desplazamiento de 3. Lo necesita la
     * rejilla del calendario para alinear el dia 1 bajo su columna correcta.
     */
    fun diaDeLaSemana(fecha: String): Int? {
        val (anio, mes, dia) = descomponerNumerica(fecha) ?: return null
        return ((diasDesdeEpoca(anio, mes, dia) + 3).mod(7L)).toInt()
    }

    /** Arma `YYYY-MM-DD` desde los tres campos; nulo si no existe en el calendario. */
    fun componer(dia: String, mes: String, anio: String): String? {
        val d = dia.trim().toIntOrNull() ?: return null
        val m = mes.trim().toIntOrNull() ?: return null
        val a = anio.trim().toIntOrNull() ?: return null
        val fecha = "${a.acolchado(4)}-${m.acolchado(2)}-${d.acolchado(2)}"
        return if (esFechaValida(fecha)) fecha else null
    }

    fun descomponer(fecha: String): PartesFecha? {
        val (anio, mes, dia) = descomponerNumerica(fecha) ?: return null
        return PartesFecha(
            dia = dia.acolchado(2),
            mes = mes.acolchado(2),
            anio = anio.acolchado(4),
        )
    }

    /** Comparacion lexicografica valida porque ambas fechas son `YYYY-MM-DD`. */
    fun esAnterior(fecha: String, limite: String): Boolean = fecha < limite

    /**
     * Edad cumplida en anios. La usa el personal de emergencia para calcular
     * dosis, asi que descuenta el anio cuando aun no ha llegado el cumpleanios
     * en vez de redondear.
     *
     * Devuelve nulo si la fecha no es valida o es posterior a [hoy]: mostrar una
     * edad inventada en una pantalla de triage es peor que no mostrar ninguna.
     */
    fun edadEnAnios(fechaNacimiento: String, hoy: String): Int? {
        val nacimiento = descomponerNumerica(fechaNacimiento) ?: return null
        val actual = descomponerNumerica(hoy) ?: return null
        val (anioNacimiento, mesNacimiento, diaNacimiento) = nacimiento
        val (anioActual, mesActual, diaActual) = actual
        val cumpleanosPasado = mesActual > mesNacimiento ||
            (mesActual == mesNacimiento && diaActual >= diaNacimiento)
        val edad = anioActual - anioNacimiento - if (cumpleanosPasado) 0 else 1
        return edad.takeIf { it >= 0 }
    }

    private fun descomponerNumerica(fecha: String): Triple<Int, Int, Int>? {
        val grupos = FORMATO.matchEntire(fecha.trim())?.groupValues ?: return null
        val anio = grupos[1].toInt()
        val mes = grupos[2].toInt()
        val dia = grupos[3].toInt()
        if (mes !in 1..12) return null
        if (dia !in 1..diasDelMes(anio, mes)) return null
        return Triple(anio, mes, dia)
    }

    private fun diasDelMes(anio: Int, mes: Int): Int = when (mes) {
        2 -> if (esBisiesto(anio)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    private fun esBisiesto(anio: Int): Boolean =
        anio % 4 == 0 && (anio % 100 != 0 || anio % 400 == 0)

    /**
     * Dias transcurridos desde 1970-01-01, con el algoritmo de calendario civil
     * de Howard Hinnant: desplaza el inicio del anio a marzo para que el dia
     * bisiesto quede siempre al final y desaparezcan los casos especiales.
     */
    private fun diasDesdeEpoca(anio: Int, mes: Int, dia: Int): Long {
        val a = if (mes <= 2) anio - 1 else anio
        val era = (if (a >= 0) a else a - 399) / 400
        val anioDeEra = a - era * 400
        val diaDeAnio = (153 * (mes + (if (mes > 2) -3 else 9)) + 2) / 5 + dia - 1
        val diaDeEra = anioDeEra * 365L + anioDeEra / 4 - anioDeEra / 100 + diaDeAnio
        return era * 146097L + diaDeEra - 719468L
    }

    /** Inversa exacta de [diasDesdeEpoca]. */
    private fun fechaDesdeDiasEpoca(dias: Long): String {
        val z = dias + 719468L
        val era = (if (z >= 0) z else z - 146096) / 146097
        val diaDeEra = z - era * 146097
        val anioDeEra = (diaDeEra - diaDeEra / 1460 + diaDeEra / 36524 - diaDeEra / 146096) / 365
        val anio = anioDeEra + era * 400
        val diaDeAnio = diaDeEra - (365 * anioDeEra + anioDeEra / 4 - anioDeEra / 100)
        val mesDesplazado = (5 * diaDeAnio + 2) / 153
        val dia = (diaDeAnio - (153 * mesDesplazado + 2) / 5 + 1).toInt()
        val mes = (mesDesplazado + (if (mesDesplazado < 10) 3 else -9)).toInt()
        val anioFinal = (if (mes <= 2) anio + 1 else anio).toInt()
        return "${anioFinal.acolchado(4)}-${mes.acolchado(2)}-${dia.acolchado(2)}"
    }

    private fun Int.acolchado(digitos: Int): String = toString().padStart(digitos, '0')
}
