package com.eter.salud.domain.repository

import com.eter.salud.domain.model.Cita
import com.eter.salud.domain.model.ConfirmacionCita
import com.eter.salud.domain.model.DatosContactoCita
import com.eter.salud.domain.model.EstadoCita
import com.eter.salud.domain.model.FranjaAgenda
import com.eter.salud.domain.model.MotivoFalloCita
import com.eter.salud.domain.model.ReservaFranja
import kotlinx.coroutines.flow.Flow

/**
 * Fallo de agenda con causa conocida.
 *
 * Los demas modulos traducen cualquier excepcion a "sin conexion", pero aqui la
 * causa cambia lo que la Vista debe ofrecer: ante una franja que se ocupo hay
 * que volver a la lista de horarios, y ante una retencion caducada hay que
 * avisar de por que se perdio. Por eso el motivo viaja tipado en vez de
 * deducirse de un mensaje de texto.
 */
class FalloCita(val motivo: MotivoFalloCita) : Exception(motivo.name)

/**
 * Contrato de agendamiento hacia la API en Go. Lo comparten los dos lados del
 * modulo: el chat del paciente escribe citas y la agenda del medico las lee.
 *
 * Dos decisiones gobiernan todo el contrato:
 *
 *  - **El instante actual se recibe, no se consulta.** Cada operacion sensible al
 *    tiempo toma `ahora` como parametro, igual que ya hace `ChatRepositorio`.
 *    Asi el reloj se inyecta una sola vez en el ViewModel y la caducidad de una
 *    retencion se puede probar sin esperar cinco minutos reales.
 *
 *  - **La agenda del medico es un [Flow] y no una consulta puntual.** La
 *    sincronizacion en tiempo real que pide el requerimiento (que una cita
 *    confirmada en el chat aparezca sin recargar) es una propiedad del contrato,
 *    no del transporte: hoy la satisface una implementacion en memoria y manana
 *    un WebSocket, sin que el ViewModel ni la Vista cambien una linea.
 *
 * Los medicos por especialidad NO se piden aqui: ya los da
 * [DirectorioMedicoRepositorio.buscarDirectorio]. Duplicar ese catalogo seria
 * abrir la puerta a que el chat ofrezca doctores que el directorio no lista.
 */
interface CitasRepositorio {

    /**
     * Franjas estrictamente libres de [idMedico] a partir de [desde].
     *
     * "Estrictamente libres" excluye tres cosas: las franjas con una cita que
     * ocupa ([EstadoCita.ocupaLaFranja]), las que el medico bloqueo, y las que
     * otro paciente tiene retenidas y aun no han caducado a la altura de [ahora].
     */
    suspend fun franjasLibres(
        idMedico: String,
        desde: String,
        ahora: String,
    ): Result<List<FranjaAgenda>>

    /**
     * Retiene la franja para el paciente que esta capturando sus datos.
     *
     * Falla con [MotivoFalloCita.FRANJA_OCUPADA] si otro se le adelanto: la
     * carrera se resuelve en el backend, nunca comparando listas en el cliente.
     */
    suspend fun reservarTemporalmente(idFranja: String, ahora: String): Result<ReservaFranja>

    /**
     * Suelta una retencion antes de que caduque, cuando el paciente abandona el
     * flujo. No devolver la franja de inmediato la dejaria muerta durante los
     * cinco minutos completos sin que nadie la vaya a usar.
     */
    suspend fun liberarReserva(idReserva: String): Result<Unit>

    /**
     * Convierte una retencion viva en cita y dispara el comprobante.
     *
     * Falla con [MotivoFalloCita.RESERVA_EXPIRADA] si [ahora] paso de la
     * caducidad: es el backend quien decide, porque el reloj del telefono se
     * puede cambiar a mano.
     */
    suspend fun confirmarCita(
        idReserva: String,
        idPaciente: String,
        contacto: DatosContactoCita,
        ahora: String,
    ): Result<ConfirmacionCita>

    /** Agenda viva del medico: emite de nuevo con cada alta, cambio o bloqueo. */
    fun agendaDelMedico(idMedico: String): Flow<List<Cita>>

    /** Carga inicial de la agenda; el [Flow] se encarga de lo que venga despues. */
    suspend fun cargarAgenda(idMedico: String): Result<List<Cita>>

    suspend fun cambiarEstado(idCita: String, nuevo: EstadoCita): Result<Cita>

    /**
     * Reserva tiempo propio del medico (comida, cirugia, vacaciones). El rango
     * desaparece de inmediato de lo que el chat ofrece a los pacientes.
     */
    suspend fun bloquearHorario(
        idMedico: String,
        fecha: String,
        horaInicio: String,
        horaFin: String,
        nota: String,
    ): Result<Cita>

    /** Mueve una cita a otra franja libre, conservando paciente, motivo y folio. */
    suspend fun reprogramar(idCita: String, idFranjaNueva: String, ahora: String): Result<Cita>
}
