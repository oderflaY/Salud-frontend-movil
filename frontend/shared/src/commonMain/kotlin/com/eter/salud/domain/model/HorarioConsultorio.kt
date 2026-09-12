package com.eter.salud.domain.model

/**
 * Jornada del consultorio: unica fuente de las horas que se pueden agendar.
 *
 * Vive en el dominio y no en el repositorio porque la usan los dos lados del
 * modulo: el simulador de agenda para generar los huecos que ofrece el chat, y
 * la rejilla del calendario del medico para dibujar las filas del dia. Si cada
 * uno tuviera sus constantes, la rejilla podria pintar una fila de las 15:00 que
 * el chat nunca ofreceria, o dejar fuera una hora que si existe.
 *
 * Cuando el backend permita jornadas por medico, esto pasa a ser un dato que
 * viaja con el perfil del doctor y deja de ser constante.
 */
object HorarioConsultorio {

    const val HORA_APERTURA = 9
    const val HORA_CIERRE = 14
    const val DURACION_CONSULTA_MINUTOS = 30

    private const val MINUTOS_POR_HORA = 60

    /** Horas de inicio de cada consulta del dia, en formato `HH:MM`. */
    fun horasDeInicio(): List<String> =
        (HORA_APERTURA * MINUTOS_POR_HORA until HORA_CIERRE * MINUTOS_POR_HORA
            step DURACION_CONSULTA_MINUTOS)
            .map { aHoraDePared(it) }

    /** Hora de fin de la consulta que empieza en [horaInicio]; vacio si no es `HH:MM`. */
    fun finDe(horaInicio: String): String {
        val minutos = aMinutos(horaInicio) ?: return ""
        return aHoraDePared(minutos + DURACION_CONSULTA_MINUTOS)
    }

    /** Minutos desde la medianoche de una hora `HH:MM`, o nulo si no lo es. */
    fun aMinutos(hora: String): Int? {
        if (FORMATO_HORA.matchEntire(hora.trim()) == null) return null
        val partes = hora.trim().split(":")
        return partes[0].toInt() * MINUTOS_POR_HORA + partes[1].toInt()
    }

    fun aHoraDePared(minutoDelDia: Int): String {
        val hora = (minutoDelDia / MINUTOS_POR_HORA).toString().padStart(2, '0')
        val minuto = (minutoDelDia % MINUTOS_POR_HORA).toString().padStart(2, '0')
        return "$hora:$minuto"
    }

    private val FORMATO_HORA = Regex("""\d{2}:\d{2}""")
}
