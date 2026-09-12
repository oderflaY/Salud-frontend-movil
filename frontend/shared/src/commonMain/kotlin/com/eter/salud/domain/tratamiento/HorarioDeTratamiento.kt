package com.eter.salud.domain.tratamiento

import com.eter.salud.domain.model.EstadoToma
import com.eter.salud.domain.model.TomaDelDia
import com.eter.salud.domain.model.TratamientoActivo

/**
 * Del tratamiento del expediente a las tomas de un dia.
 *
 * El expediente dice "Metformina, 850 mg, cada 12 horas"; la pantalla de inicio
 * necesita "08:00 Metformina" y "20:00 Metformina". Esta traduccion era tarea
 * del backend, pero mientras la base sea local la hace el cliente, y vive aqui
 * para que sea UNA sola regla, probada, y no una cuenta suelta en cada pantalla.
 */
object HorarioDeTratamiento {

    /**
     * Hora de la primera toma cuando el tratamiento solo trae frecuencia.
     *
     * Las 08:00 y no la medianoche: "cada 8 horas" empezando a las 00:00 pone
     * una toma a las 00:00, que despierta al paciente, en vez de 08-16-24.
     */
    const val PRIMERA_TOMA = 8

    private const val HORAS_DIA = 24

    /**
     * Horas `HH:MM` del tratamiento, ordenadas.
     *
     * Si el tratamiento trae horarios sugeridos, mandan ellos. Si no, se reparten
     * desde [PRIMERA_TOMA] segun la frecuencia: cada 8 horas son 08, 16 y 00. Una
     * frecuencia fuera de 1..24 se trata como una vez al dia; nunca como cero
     * tomas, que haria desaparecer una medicina del panel sin avisar.
     */
    fun horariosDe(tratamiento: TratamientoActivo): List<String> {
        val sugeridos = tratamiento.horariosSugeridos.filter(::esHoraValida)
        if (sugeridos.isNotEmpty()) return sugeridos.distinct().sorted()
        val frecuencia = tratamiento.frecuenciaHoras.takeIf { it in 1..HORAS_DIA } ?: HORAS_DIA
        val tomasAlDia = (HORAS_DIA / frecuencia).coerceAtLeast(1)
        return (0 until tomasAlDia)
            .map { indice -> (PRIMERA_TOMA + indice * frecuencia) % HORAS_DIA }
            .distinct()
            .map { hora -> "${hora.toString().padStart(2, '0')}:00" }
            .sorted()
    }

    /**
     * Las tomas de un dia cualquiera, todas pendientes, ordenadas por hora.
     *
     * [TomaDelDia.idToma] sale de [clave] sin fecha: quien las use para un dia
     * concreto la compone con [IdDeToma].
     */
    fun tomasProgramadas(tratamientos: List<TratamientoActivo>): List<TomaProgramada> =
        tratamientos.flatMap { tratamiento ->
            val idTratamiento = tratamiento.idTratamiento ?: idPorDefecto(tratamiento.medicamento)
            horariosDe(tratamiento).map { hora ->
                TomaProgramada(
                    clave = clave(idTratamiento, hora),
                    idTratamiento = idTratamiento,
                    medicamento = tratamiento.medicamento,
                    dosis = tratamiento.dosis,
                    hora = hora,
                )
            }
        }.sortedWith(compareBy<TomaProgramada> { it.hora }.thenBy { it.medicamento })

    /** Identificador estable de un tratamiento sin id del backend. */
    fun idPorDefecto(medicamento: String): String =
        "trt_" + medicamento.trim().lowercase().map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")

    /** Toma dentro del dia: `<idTratamiento>_<HHMM>`. */
    fun clave(idTratamiento: String, hora: String): String = "${idTratamiento}_${hora.replace(":", "")}"

    private fun esHoraValida(hora: String): Boolean {
        val partes = hora.split(":")
        if (partes.size != 2 || partes.any { it.length != 2 }) return false
        val h = partes[0].toIntOrNull() ?: return false
        val m = partes[1].toIntOrNull() ?: return false
        return h in 0..23 && m in 0..59
    }
}

/** Una toma del tratamiento, sin fecha ni estado todavia. */
data class TomaProgramada(
    val clave: String,
    val idTratamiento: String,
    val medicamento: String,
    val dosis: String,
    /** `HH:MM`. */
    val hora: String,
) {
    fun enFecha(fecha: String, estado: EstadoToma) = TomaDelDia(
        idToma = IdDeToma.componer(clave, fecha),
        idTratamiento = idTratamiento,
        medicamento = medicamento,
        dosis = dosis,
        horaProgramada = hora,
        estado = estado,
    )
}

/**
 * Identificador de una toma en un dia: la clave de la toma y la fecha.
 *
 * Lleva la fecha dentro porque `registrarToma` solo recibe el identificador, y
 * la misma pastilla de las 08:00 es una toma distinta cada dia. Sin la fecha,
 * marcar la de hoy marcaria tambien la de manana.
 */
object IdDeToma {
    private const val SEPARADOR = "@"

    fun componer(clave: String, fecha: String): String = "$clave$SEPARADOR$fecha"

    /** Clave y fecha; nulo si el identificador no tiene esta forma. */
    fun descomponer(idToma: String): Pair<String, String>? {
        val corte = idToma.lastIndexOf(SEPARADOR)
        if (corte <= 0 || corte == idToma.lastIndex) return null
        return idToma.substring(0, corte) to idToma.substring(corte + 1)
    }
}
