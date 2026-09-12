package com.eter.salud.domain.model

import kotlinx.serialization.Serializable

/**
 * Estado de una cita en la agenda del medico.
 *
 * [CANCELADA] y [NO_ASISTIO] se pintan del mismo color (el rojo de alerta), pero
 * son estados distintos y no uno solo: una cita cancelada libera la franja y una
 * inasistencia no; ademas el backend las cuenta por separado para la estadistica
 * de la clinica. Colapsarlas en la capa de dominio para ahorrar un valor haria
 * imposible distinguirlas mas tarde.
 *
 * [BLOQUEADO] no es una cita de un paciente, sino tiempo que el medico se
 * reserva (comida, cirugia, vacaciones). Vive en el mismo enum porque ocupa la
 * agenda igual que una cita y el calendario lo dibuja en la misma rejilla.
 *
 * [PROPUESTA_MEDICO] es el espejo de [PENDIENTE]: en aquel el paciente pidio la
 * hora y espera a que el consultorio la valide; aqui el MEDICO eligio la hora
 * (desde su propia agenda, para un paciente ya vinculado) y espera a que el
 * paciente la acepte. Ocupa la franja igual que cualquier otra cita viva -- dos
 * propuestas no pueden competir por el mismo hueco -- y solo dos caminos la
 * sacan de aqui: el paciente la acepta ([CONFIRMADA]) o la rechaza/el medico la
 * retira ([CANCELADA]). Nunca la confirma el propio medico: confirmar la
 * propuesta que uno mismo hizo no es una validacion de nadie.
 */
@Serializable
enum class EstadoCita {
    PENDIENTE,
    CONFIRMADA,
    EN_CURSO,
    CANCELADA,
    NO_ASISTIO,
    BLOQUEADO,
    PROPUESTA_MEDICO,
    ;

    /** Cierto si sigue ocupando la franja, es decir, si impide agendar encima. */
    val ocupaLaFranja: Boolean
        get() = this != CANCELADA
}

/**
 * Hueco agendable en la agenda de un medico.
 *
 * La hora se guarda como fecha civil (`YYYY-MM-DD`) mas hora local del
 * consultorio (`HH:MM`), y no como un instante UTC, a proposito: la agenda de
 * una clinica se razona en su hora de pared ("los martes de 9 a 2"), y guardarla
 * en UTC obligaria a convertir en cada pantalla y abriria la puerta a que un
 * cambio de horario de verano moviera todas las citas ya agendadas.
 */
@Serializable
data class FranjaAgenda(
    val idFranja: String,
    val idMedico: String,
    /** `YYYY-MM-DD`. */
    val fecha: String,
    /** `HH:MM` en la hora local del consultorio. */
    val horaInicio: String,
    /** `HH:MM` en la hora local del consultorio. */
    val horaFin: String,
) {
    /**
     * Clave de orden. Valida como comparacion lexicografica porque fecha y hora
     * son de ancho fijo y van de la unidad mayor a la menor.
     */
    val claveOrden: String get() = "$fecha $horaInicio"
}

/** Datos que el paciente aporta en el chat al agendar. */
@Serializable
data class DatosContactoCita(
    val nombreCompleto: String,
    val telefono: String,
    val correo: String,
    /** Motivo de consulta en palabras del paciente; lo lee el medico antes de recibirlo. */
    val motivo: String,
)

/**
 * Una cita ya escrita en la agenda del medico, o un bloqueo de horario.
 *
 * [contacto] es nulo unicamente cuando [estado] es [EstadoCita.BLOQUEADO]: un
 * bloqueo no tiene paciente al que llamar. Es la unica nulidad del modelo y esta
 * atada a ese estado.
 */
@Serializable
data class Cita(
    val idCita: String,
    /** Comprobante que el paciente recibe en el chat, por ejemplo `CITA-4F2A`. */
    val folio: String,
    val idMedico: String,
    val nombreMedico: String,
    /** Vacio en un bloqueo de horario. */
    val idPaciente: String,
    val fecha: String,
    val horaInicio: String,
    val horaFin: String,
    val estado: EstadoCita,
    val contacto: DatosContactoCita? = null,
    /** Texto del propio medico al bloquear ("Cirugia", "Comida"). Vacio en una cita. */
    val notaBloqueo: String = "",
) {
    val esBloqueo: Boolean get() = estado == EstadoCita.BLOQUEADO

    val claveOrden: String get() = "$fecha $horaInicio"
}

/**
 * Retencion temporal de una franja mientras el paciente termina de capturar sus
 * datos en el chat.
 *
 * Es la pieza que evita que dos pacientes agenden la misma hora al mismo tiempo:
 * desde que uno elige el horario y hasta [expiraEn], la franja deja de ofrecerse
 * a los demas. Si el paciente abandona la conversacion, la retencion caduca sola
 * y el hueco vuelve a estar libre sin que nadie tenga que limpiarlo.
 */
@Serializable
data class ReservaFranja(
    val idReserva: String,
    val franja: FranjaAgenda,
    /** Instante ISO 8601 UTC a partir del cual la retencion deja de valer. */
    val expiraEn: String,
) {
    companion object {
        /**
         * Minutos que la franja queda apartada.
         *
         * Vive aqui y no dentro del repositorio porque el chat tiene que
         * decirle al paciente de cuanto tiempo dispone ("apartamos el horario 5
         * minutos"). Con la constante escondida en la capa de datos, ese texto
         * seria un numero suelto en la Vista que nadie actualizaria el dia que
         * el backend cambiara la politica.
         */
        const val MINUTOS_DE_RETENCION = 5
    }
}

/** Canal por el que sale el comprobante de la cita. */
@Serializable
enum class CanalNotificacion {
    CORREO,
    WHATSAPP,
}

/**
 * Resultado de confirmar una cita. Lleva los canales que el backend acepto
 * notificar, no los que se le pidieron: si el envio de WhatsApp esta caido, el
 * chat debe poder decir "te llego por correo" en vez de prometer las dos cosas.
 */
@Serializable
data class ConfirmacionCita(
    val cita: Cita,
    val canalesNotificados: List<CanalNotificacion>,
)

/** Razones por las que el backend puede rechazar una operacion de agenda. */
enum class MotivoFalloCita {
    /** Pasaron los minutos de la retencion; hay que volver a elegir horario. */
    RESERVA_EXPIRADA,

    /** Otro paciente gano la carrera por esa franja. */
    FRANJA_OCUPADA,

    SIN_CONEXION,
}
