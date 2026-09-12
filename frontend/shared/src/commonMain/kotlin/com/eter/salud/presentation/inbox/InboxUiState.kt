package com.eter.salud.presentation.inbox

import com.eter.salud.domain.diario.SeveridadDiario
import com.eter.salud.domain.model.AutorMensaje
import com.eter.salud.domain.model.PacienteVinculado
import com.eter.salud.domain.model.RiesgoPaciente

/**
 * Prioridad clinica de una conversacion en la bandeja del medico.
 *
 * Es UNA sola senal para el punto de color, pero se alimenta de dos fuentes de
 * vidas distintas: el riesgo del expediente, que sirve el backend y cambia poco,
 * y la severidad de la ultima entrada del diario, que calcula el telefono del
 * paciente a partir de palabras clave y puede cambiar a cualquier hora.
 *
 * El orden de las constantes ES la gravedad: la bandeja ordena por `ordinal`
 * descendente y [combinar] compara ordinales.
 */
enum class NivelTriage {
    ESTABLE,
    VIGILANCIA,
    CRITICO;

    companion object {

        /**
         * La prioridad de un paciente: la MAS GRAVE de sus dos senales.
         *
         * ## Por que el maximo y no un promedio
         *
         * Es la misma regla que gobierna [com.eter.salud.domain.diario.TriageDelDiario]:
         * ante la duda, marcar mas grave. Un paciente de riesgo bajo en el
         * expediente que acaba de anotar "dolor en el pecho" es, AHORA, un
         * paciente critico; promediar los dos lo dejaria en vigilancia, en mitad
         * de la lista, que es exactamente donde no puede estar.
         *
         * Un paciente que no ha escrito en su diario ([severidadDiario] nula) se
         * rige solo por el expediente: no escribir no es estar bien, pero
         * tampoco es una alarma.
         */
        fun combinar(riesgo: RiesgoPaciente, severidadDiario: SeveridadDiario?): NivelTriage {
            val porExpediente = when (riesgo) {
                RiesgoPaciente.BAJO -> ESTABLE
                RiesgoPaciente.MEDIO -> VIGILANCIA
                RiesgoPaciente.ALTO -> CRITICO
            }
            val porDiario = when (severidadDiario) {
                null -> ESTABLE
                SeveridadDiario.VERDE -> ESTABLE
                SeveridadDiario.AMBAR -> VIGILANCIA
                SeveridadDiario.ROJO -> CRITICO
            }
            return if (porDiario.ordinal > porExpediente.ordinal) porDiario else porExpediente
        }
    }
}

/**
 * Una conversacion tal como se pinta en la bandeja del medico.
 *
 * El ultimo mensaje es opcional a proposito: a diferencia de la bandeja del
 * paciente, aqui NO se esconde una conversacion vacia. Un paciente vinculado que
 * aun no ha escrito sigue siendo un paciente del medico, y si su diario lo marca
 * critico tiene que aparecer arriba aunque no haya dicho una palabra en el chat.
 */
data class ConversacionClinica(
    val paciente: PacienteVinculado,
    val nivel: NivelTriage,
    /** Texto del ultimo mensaje, sin recortar: el recorte es decision de la Vista. */
    val ultimoMensaje: String? = null,
    /** Instante ISO 8601 UTC del ultimo mensaje. La Vista lo pasa a hora local. */
    val instanteUltimoMensaje: String? = null,
    val autorUltimoMensaje: AutorMensaje? = null,
)

/**
 * Estado de la bandeja clinica. Sellado: la Vista esta obligada a resolver los
 * cuatro escenarios y ninguno se queda sin pintar por olvido.
 */
sealed interface InboxUiState {

    data object Cargando : InboxUiState

    /** El medico no tiene ninguna conversacion abierta con sus pacientes. */
    data object SinConversaciones : InboxUiState

    /** Ya ordenadas: primero la prioridad clinica, despues la actividad reciente. */
    data class ConConversaciones(val conversaciones: List<ConversacionClinica>) : InboxUiState

    data object Error : InboxUiState
}
