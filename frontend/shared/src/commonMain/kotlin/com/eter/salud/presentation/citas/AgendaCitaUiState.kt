package com.eter.salud.presentation.citas

import com.eter.salud.domain.model.ConfirmacionCita
import com.eter.salud.domain.model.Especialidad
import com.eter.salud.domain.model.FranjaAgenda
import com.eter.salud.domain.model.PerfilDoctorDirectorio
import com.eter.salud.domain.model.ReservaFranja

/**
 * Paso del flujo conversacional de agendamiento.
 *
 * Es un enum y no un booleano por pantalla porque los pasos son excluyentes: el
 * paciente esta en uno y solo uno, y modelarlo asi hace imposible el estado
 * absurdo de "eligiendo horario y capturando datos a la vez".
 */
enum class PasoAgenda {
    /** El flujo no esta abierto: el chat se comporta como una conversacion normal. */
    INACTIVO,
    ESPECIALIDAD,
    MEDICO,
    FRANJA,
    DATOS,
    RESUMEN,
    CONFIRMADA,
}

/**
 * Medico ya elegido para la cita.
 *
 * No se reutiliza [PerfilDoctorDirectorio] porque el paciente puede llegar aqui
 * por dos caminos: eligiendo del directorio, o con el atajo "con mi medico de
 * siempre", que parte de un `MedicoVinculado` y no conoce ni la universidad ni
 * el estado de la cedula. Un perfil con esos campos inventados para rellenar el
 * hueco mostraria en pantalla datos de confianza que nadie verifico.
 */
data class MedicoElegido(
    val idMedico: String,
    val nombreCompleto: String,
    val especialidad: Especialidad,
)

/** Un dia con sus horarios libres, tal como se ofrece en el carrusel del chat. */
data class DiaConFranjas(
    val fecha: String,
    val franjas: List<FranjaAgenda>,
)

/**
 * Estado unico del agendamiento dentro del chat. La Vista solo pinta esto
 * (DM_Arquitectura_App.md, seccion 2: la Vista es pasiva).
 */
data class AgendaCitaUiState(
    val paso: PasoAgenda = PasoAgenda.INACTIVO,
    val especialidad: Especialidad? = null,
    val medicos: List<PerfilDoctorDirectorio> = emptyList(),
    val medico: MedicoElegido? = null,
    val franjas: List<FranjaAgenda> = emptyList(),
    val franja: FranjaAgenda? = null,
    /** Retencion viva de la franja mientras se capturan los datos. */
    val reserva: ReservaFranja? = null,
    val nombreCompleto: String = "",
    val telefono: String = "",
    val correo: String = "",
    val motivo: String = "",
    val errores: List<ErrorCampoCita> = emptyList(),
    val confirmacion: ConfirmacionCita? = null,
    val cargando: Boolean = false,
    val error: ErrorAgendaCita? = null,
) {
    /** Cierto cuando el chat debe mostrar el panel de agendamiento sobre la conversacion. */
    val activo: Boolean get() = paso != PasoAgenda.INACTIVO

    /**
     * Horarios agrupados por dia y en orden. La Vista recibe el carrusel ya
     * armado: agrupar en la capa de dibujo repetiria el calculo en cada
     * recomposicion y pondria logica donde no le toca.
     */
    val dias: List<DiaConFranjas>
        get() = franjas
            .groupBy { it.fecha }
            .toList()
            .sortedBy { (fecha, _) -> fecha }
            .map { (fecha, delDia) -> DiaConFranjas(fecha, delDia.sortedBy { it.horaInicio }) }
}
