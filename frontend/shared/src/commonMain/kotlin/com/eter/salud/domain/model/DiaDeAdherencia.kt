package com.eter.salud.domain.model

import kotlinx.serialization.Serializable

/**
 * Cumplimiento de UN dia, tal como lo pinta la franja semanal del panel.
 *
 * Es un modelo aparte de [ResumenAdherencia] porque responde a otra pregunta:
 * aquel dice "como voy en conjunto", este dice "que paso cada dia". Fundirlos
 * obligaria a la Vista a reconstruir el detalle a partir de un agregado, que es
 * justo lo que un agregado ya perdio.
 */
@Serializable
data class DiaDeAdherencia(
    /** `YYYY-MM-DD`. */
    val fecha: String,
    val tomasProgramadas: Int,
    val tomasCumplidas: Int,
    /**
     * Si el dia aun no ha llegado (o es hoy y sigue en curso).
     *
     * Viaja como dato y no se deduce comparando con el reloj del dispositivo: la
     * frontera de "hoy" depende de la zona horaria del tratamiento, que conoce
     * el backend. Calcularla aqui pintaria de rojo el dia de manana para quien
     * viaje al este.
     */
    val enCurso: Boolean = false,
) {
    val estado: EstadoDia
        get() = when {
            enCurso -> EstadoDia.EN_CURSO
            tomasProgramadas == 0 -> EstadoDia.SIN_TOMAS
            tomasCumplidas >= tomasProgramadas -> EstadoDia.COMPLETO
            else -> EstadoDia.INCOMPLETO
        }
}

/**
 * Estado de un dia en la franja semanal.
 *
 * [SIN_TOMAS] y [EN_CURSO] se distinguen de [INCOMPLETO] a proposito: un dia sin
 * tratamiento programado, o un dia que todavia no ha terminado, NO son un
 * incumplimiento. Pintarlos de rojo culparia al paciente de algo que no hizo, y
 * en adherencia esa culpa mal puesta es lo que hace que la gente deje de mirar
 * la pantalla.
 */
enum class EstadoDia {
    COMPLETO,
    INCOMPLETO,
    SIN_TOMAS,
    EN_CURSO,
}

/**
 * Un recordatorio de toma que el sistema operativo debe disparar.
 *
 * Lleva todo lo que la notificacion necesita mostrar, para que el codigo de
 * plataforma no tenga que volver a consultar nada: cuando la alarma suena, la
 * app puede estar cerrada y no habra repositorio ni sesion viva a la que
 * preguntar.
 */
data class RecordatorioMedicacion(
    val idToma: String,
    val medicamento: String,
    val dosis: String,
    /** `YYYY-MM-DD`. */
    val fecha: String,
    /** Hora local `HH:MM`. */
    val horaProgramada: String,
) {
    /**
     * Identificador estable del recordatorio en el sistema operativo.
     *
     * Se deriva de la toma y no de un contador: reprogramar la misma toma tiene
     * que REEMPLAZAR su alarma, no anadir una segunda. Con identificadores
     * aleatorios, un paciente que abriera la app tres veces recibiria tres
     * avisos de la misma pastilla.
     */
    val claveSistema: String get() = "toma_$idToma"
}
