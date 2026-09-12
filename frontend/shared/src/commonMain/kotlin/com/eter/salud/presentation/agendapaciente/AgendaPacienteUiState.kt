package com.eter.salud.presentation.agendapaciente

import com.eter.salud.domain.model.Cita
import com.eter.salud.domain.model.DiaDeAdherencia
import com.eter.salud.domain.model.TomaDelDia

/**
 * Una casilla del calendario de dos semanas del paciente.
 *
 * Lleva ya resuelto lo que la casilla pinta -- si es hoy, si es futuro, cuantas
 * citas tiene -- para que la Vista no compare fechas: comparar fechas en la
 * Vista es donde nacen los "hoy" que se equivocan a medianoche.
 */
data class DiaDelCalendario(
    /** `YYYY-MM-DD`. */
    val fecha: String,
    val esHoy: Boolean,
    /**
     * Un dia futuro no se juzga: sus medicinas aun no tocan, y pintarlo de
     * "incompleto" culparia al paciente por algo que todavia no paso.
     */
    val esFuturo: Boolean,
    /** Cumplimiento del dia; nulo si el servicio no lo devolvio. */
    val adherencia: DiaDeAdherencia?,
    val citas: Int,
)

/**
 * Algo que ocurre a una hora del dia elegido: una toma o una cita.
 *
 * Van en una sola lista y ordenadas por hora a proposito. Para una persona
 * mayor el dia no esta dividido en "medicinas" y "citas": esta dividido en
 * horas. "A las 10 la pastilla, a las 11 el cardiologo" es como se lo cuenta a
 * si misma, y asi se lo cuenta la pantalla.
 */
sealed interface EventoAgenda {
    /** `HH:MM`: la clave de orden. */
    val hora: String

    data class Toma(val toma: TomaDelDia) : EventoAgenda {
        override val hora: String get() = toma.horaProgramada
    }

    data class ConCita(val cita: Cita) : EventoAgenda {
        override val hora: String get() = cita.horaInicio
    }
}

/** Estado unico de la agenda del paciente. */
data class AgendaPacienteUiState(
    val hoy: String = "",
    val fechaSeleccionada: String = "",
    /** Lunes con el que empieza la ventana visible, `YYYY-MM-DD`. */
    val lunes: String = "",
    /** Los catorce dias visibles, de lunes a domingo de la semana siguiente. */
    val dias: List<DiaDelCalendario> = emptyList(),
    /**
     * Citas de hoy a trece dias vista, en orden.
     *
     * No depende de la ventana del calendario: aunque el paciente este mirando
     * el mes pasado, lo que tiene por delante sigue a la vista. Es la pregunta
     * "que tengo en estas dos semanas" contestada sin tocar casilla por casilla.
     */
    val proximasCitas: List<Cita> = emptyList(),
    /** Lo que ocurre el dia elegido, ya ordenado por hora. */
    val eventos: List<EventoAgenda> = emptyList(),
    val cargando: Boolean = true,
    val errorCarga: Boolean = false,
    /**
     * Las citas no se pudieron leer, pero las medicinas si.
     *
     * No es un error de pantalla: la agenda sigue sirviendo para lo que el
     * paciente consulta a diario (sus tomas). Se avisa en una linea y se sigue.
     */
    val citasNoDisponibles: Boolean = false,
    /** Aceptar o rechazar una propuesta del medico no se pudo aplicar. */
    val errorRespuestaPropuesta: Boolean = false,
) {
    /** Si la ventana visible contiene hoy; si no, se ofrece "Volver a hoy". */
    val incluyeHoy: Boolean get() = dias.any { it.esHoy }

    val diaSeleccionado: DiaDelCalendario? get() = dias.firstOrNull { it.fecha == fechaSeleccionada }
}
