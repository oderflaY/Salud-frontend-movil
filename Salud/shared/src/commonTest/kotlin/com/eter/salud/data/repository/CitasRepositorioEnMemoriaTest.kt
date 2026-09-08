package com.eter.salud.data.repository

import com.eter.salud.domain.model.DatosContactoCita
import com.eter.salud.domain.model.EstadoCita
import com.eter.salud.domain.model.FranjaAgenda
import com.eter.salud.domain.model.MotivoFalloCita
import com.eter.salud.domain.model.ReservaFranja
import com.eter.salud.domain.repository.FalloCita
import com.eter.salud.domain.time.InstanteSalud
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reglas de negocio del agendamiento.
 *
 * Estas pruebas no comprueban una pantalla: comprueban lo que evita que dos
 * pacientes acaben citados a la misma hora. Son las unicas del modulo que usan
 * `runTest`, porque el contrato del repositorio es enteramente suspendido y no
 * hay forma de invocarlo desde una prueba sin abrir una corrutina.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CitasRepositorioEnMemoriaTest {

    private val idMedico = "doc_889900A"
    private val hoy = "2026-09-08"
    private val ahora = "2026-09-08T15:00:00Z"

    private val contacto = DatosContactoCita(
        nombreCompleto = "Alfredo Valadez Gonzalez",
        telefono = "6181234567",
        correo = "alfredo@ejemplo.mx",
        motivo = "Dolor de cabeza desde hace tres dias",
    )

    private fun repositorio() = CitasRepositorioEnMemoria()

    private suspend fun primeraFranja(
        repositorio: CitasRepositorioEnMemoria,
        instante: String = ahora,
    ): FranjaAgenda = repositorio.franjasLibres(idMedico, hoy, instante).getOrThrow().first()

    // ------------------------------------------------------- Disponibilidad

    @Test
    fun la_agenda_se_ofrece_desde_el_dia_siguiente_y_no_desde_hoy() = runTest {
        val libres = repositorio().franjasLibres(idMedico, hoy, ahora).getOrThrow()

        assertTrue(libres.isNotEmpty())
        // El simulador no ofrece el mismo dia: descartar las horas ya pasadas
        // exigiria la zona horaria del consultorio, que es un dato del backend.
        assertTrue(libres.none { it.fecha <= hoy })
    }

    @Test
    fun una_franja_con_cita_deja_de_ofrecerse_a_los_demas() = runTest {
        val repositorio = repositorio()
        val elegida = primeraFranja(repositorio)
        val reserva = repositorio.reservarTemporalmente(elegida.idFranja, ahora).getOrThrow()
        repositorio.confirmarCita(reserva.idReserva, "pac_01H8X9A", contacto, ahora).getOrThrow()

        val libres = repositorio.franjasLibres(idMedico, hoy, ahora).getOrThrow()

        assertTrue(libres.none { it.idFranja == elegida.idFranja })
    }

    @Test
    fun cancelar_una_cita_devuelve_su_franja_al_catalogo() = runTest {
        val repositorio = repositorio()
        val elegida = primeraFranja(repositorio)
        val reserva = repositorio.reservarTemporalmente(elegida.idFranja, ahora).getOrThrow()
        val cita = repositorio
            .confirmarCita(reserva.idReserva, "pac_01H8X9A", contacto, ahora)
            .getOrThrow().cita

        repositorio.cambiarEstado(cita.idCita, EstadoCita.CANCELADA).getOrThrow()

        val libres = repositorio.franjasLibres(idMedico, hoy, ahora).getOrThrow()
        assertNotNull(libres.firstOrNull { it.idFranja == elegida.idFranja })
    }

    @Test
    fun una_inasistencia_no_libera_la_franja() = runTest {
        // No asistir no es lo mismo que cancelar: la hora se consumio igual, y
        // volver a ofrecerla falsearia la agenda de ese dia.
        val repositorio = repositorio()
        val elegida = primeraFranja(repositorio)
        val reserva = repositorio.reservarTemporalmente(elegida.idFranja, ahora).getOrThrow()
        val cita = repositorio
            .confirmarCita(reserva.idReserva, "pac_01H8X9A", contacto, ahora)
            .getOrThrow().cita

        repositorio.cambiarEstado(cita.idCita, EstadoCita.NO_ASISTIO).getOrThrow()

        val libres = repositorio.franjasLibres(idMedico, hoy, ahora).getOrThrow()
        assertNull(libres.firstOrNull { it.idFranja == elegida.idFranja })
    }

    @Test
    fun un_bloqueo_del_medico_retira_todas_las_franjas_que_cubre() = runTest {
        val repositorio = repositorio()
        val manana = repositorio.franjasLibres(idMedico, hoy, ahora).getOrThrow().first().fecha

        repositorio.bloquearHorario(idMedico, manana, "09:00", "10:00", "Cirugia").getOrThrow()

        val delDia = repositorio.franjasLibres(idMedico, hoy, ahora).getOrThrow()
            .filter { it.fecha == manana }
        // Las de 09:00 y 09:30 caen dentro; la de 10:00 empieza justo al acabar
        // el bloqueo y debe seguir libre.
        assertTrue(delDia.none { it.horaInicio == "09:00" || it.horaInicio == "09:30" })
        assertNotNull(delDia.firstOrNull { it.horaInicio == "10:00" })
    }

    // ------------------------------------------------- Retencion temporal

    @Test
    fun elegir_un_horario_lo_aparta_de_lo_que_ven_los_demas_pacientes() = runTest {
        val repositorio = repositorio()
        val elegida = primeraFranja(repositorio)

        repositorio.reservarTemporalmente(elegida.idFranja, ahora).getOrThrow()

        val libres = repositorio.franjasLibres(idMedico, hoy, ahora).getOrThrow()
        assertTrue(libres.none { it.idFranja == elegida.idFranja })
    }

    @Test
    fun dos_pacientes_no_pueden_apartar_la_misma_franja() = runTest {
        val repositorio = repositorio()
        val elegida = primeraFranja(repositorio)
        repositorio.reservarTemporalmente(elegida.idFranja, ahora).getOrThrow()

        val segunda = repositorio.reservarTemporalmente(elegida.idFranja, ahora)

        val fallo = assertIs<FalloCita>(segunda.exceptionOrNull())
        assertEquals(MotivoFalloCita.FRANJA_OCUPADA, fallo.motivo)
    }

    @Test
    fun la_retencion_caduca_sola_y_la_franja_vuelve_a_ofrecerse() = runTest {
        val repositorio = repositorio()
        val elegida = primeraFranja(repositorio)
        repositorio.reservarTemporalmente(elegida.idFranja, ahora).getOrThrow()

        val despues = InstanteSalud.sumarMinutos(
            ahora,
            ReservaFranja.MINUTOS_DE_RETENCION + 1,
        )!!

        val libres = repositorio.franjasLibres(idMedico, hoy, despues).getOrThrow()
        assertNotNull(libres.firstOrNull { it.idFranja == elegida.idFranja })
    }

    @Test
    fun soltar_la_reserva_devuelve_la_franja_sin_esperar_a_que_caduque() = runTest {
        val repositorio = repositorio()
        val elegida = primeraFranja(repositorio)
        val reserva = repositorio.reservarTemporalmente(elegida.idFranja, ahora).getOrThrow()

        repositorio.liberarReserva(reserva.idReserva)

        val libres = repositorio.franjasLibres(idMedico, hoy, ahora).getOrThrow()
        assertNotNull(libres.firstOrNull { it.idFranja == elegida.idFranja })
    }

    // ------------------------------------------------------- Confirmacion

    @Test
    fun confirmar_a_tiempo_escribe_la_cita_como_pendiente_con_folio_y_contacto() = runTest {
        val repositorio = repositorio()
        val elegida = primeraFranja(repositorio)
        val reserva = repositorio.reservarTemporalmente(elegida.idFranja, ahora).getOrThrow()

        val confirmacion = repositorio
            .confirmarCita(reserva.idReserva, "pac_01H8X9A", contacto, ahora)
            .getOrThrow()

        val cita = confirmacion.cita
        // Nace PENDIENTE: el paciente confirmo su intencion, el consultorio aun
        // no la ha validado.
        assertEquals(EstadoCita.PENDIENTE, cita.estado)
        assertTrue(cita.folio.isNotBlank())
        assertEquals(contacto, cita.contacto)
        assertEquals(elegida.fecha, cita.fecha)
        assertEquals(elegida.horaInicio, cita.horaInicio)
        assertTrue(confirmacion.canalesNotificados.isNotEmpty())
    }

    @Test
    fun confirmar_pasada_la_retencion_falla_con_reserva_expirada() = runTest {
        val repositorio = repositorio()
        val elegida = primeraFranja(repositorio)
        val reserva = repositorio.reservarTemporalmente(elegida.idFranja, ahora).getOrThrow()
        val tarde = InstanteSalud.sumarMinutos(ahora, ReservaFranja.MINUTOS_DE_RETENCION + 1)!!

        val resultado = repositorio.confirmarCita(reserva.idReserva, "pac_01H8X9A", contacto, tarde)

        val fallo = assertIs<FalloCita>(resultado.exceptionOrNull())
        assertEquals(MotivoFalloCita.RESERVA_EXPIRADA, fallo.motivo)
        // Y la franja queda libre de nuevo, no atrapada en una reserva muerta.
        val libres = repositorio.franjasLibres(idMedico, hoy, tarde).getOrThrow()
        assertNotNull(libres.firstOrNull { it.idFranja == elegida.idFranja })
    }

    @Test
    fun si_el_medico_bloquea_el_rango_la_confirmacion_pierde_ante_su_agenda() = runTest {
        val repositorio = repositorio()
        val elegida = primeraFranja(repositorio)
        val reserva = repositorio.reservarTemporalmente(elegida.idFranja, ahora).getOrThrow()

        repositorio.bloquearHorario(
            idMedico = idMedico,
            fecha = elegida.fecha,
            horaInicio = elegida.horaInicio,
            horaFin = elegida.horaFin,
            nota = "Cirugia de urgencia",
        ).getOrThrow()

        val resultado = repositorio.confirmarCita(reserva.idReserva, "pac_01H8X9A", contacto, ahora)

        val fallo = assertIs<FalloCita>(resultado.exceptionOrNull())
        assertEquals(MotivoFalloCita.FRANJA_OCUPADA, fallo.motivo)
    }

    // -------------------------------------------------------- Agenda viva

    @Test
    fun el_mismo_flujo_de_agenda_refleja_la_cita_confirmada_sin_volver_a_pedirla() = runTest {
        // Es la sincronizacion en tiempo real del requerimiento: la pantalla del
        // medico se suscribe UNA vez y ve aparecer lo que el chat escribe.
        val repositorio = repositorio()
        val agenda = repositorio.agendaDelMedico(idMedico)
        assertTrue(agenda.first().isEmpty())

        val elegida = primeraFranja(repositorio)
        val reserva = repositorio.reservarTemporalmente(elegida.idFranja, ahora).getOrThrow()
        repositorio.confirmarCita(reserva.idReserva, "pac_01H8X9A", contacto, ahora).getOrThrow()

        assertEquals(1, agenda.first().size)
    }

    @Test
    fun la_agenda_llega_ordenada_por_fecha_y_hora() = runTest {
        val repositorio = repositorio()
        val libres = repositorio.franjasLibres(idMedico, hoy, ahora).getOrThrow()
        val tarde = libres.last()
        val temprano = libres.first()

        listOf(tarde, temprano).forEach { franja ->
            val reserva = repositorio.reservarTemporalmente(franja.idFranja, ahora).getOrThrow()
            repositorio.confirmarCita(reserva.idReserva, "pac_01H8X9A", contacto, ahora).getOrThrow()
        }

        val agenda = repositorio.agendaDelMedico(idMedico).first()
        assertEquals(agenda.sortedBy { it.claveOrden }, agenda)
    }

    // ------------------------------------------------------- Reprogramar

    @Test
    fun reprogramar_mueve_la_cita_y_la_devuelve_a_pendiente() = runTest {
        val repositorio = repositorio()
        val libres = repositorio.franjasLibres(idMedico, hoy, ahora).getOrThrow()
        val original = libres.first()
        val destino = libres.last()
        val reserva = repositorio.reservarTemporalmente(original.idFranja, ahora).getOrThrow()
        val cita = repositorio
            .confirmarCita(reserva.idReserva, "pac_01H8X9A", contacto, ahora)
            .getOrThrow().cita
        repositorio.cambiarEstado(cita.idCita, EstadoCita.CONFIRMADA).getOrThrow()

        val movida = repositorio.reprogramar(cita.idCita, destino.idFranja, ahora).getOrThrow()

        assertEquals(destino.fecha, movida.fecha)
        assertEquals(destino.horaInicio, movida.horaInicio)
        // El paciente todavia no sabe de la nueva hora: dejarla en verde mentiria.
        assertEquals(EstadoCita.PENDIENTE, movida.estado)
        assertEquals(cita.folio, movida.folio)
        assertEquals(contacto, movida.contacto)
        assertEquals(1, repositorio.agendaDelMedico(idMedico).first().size)
    }

    @Test
    fun reprogramar_sobre_una_franja_ya_ocupada_no_pisa_a_nadie() = runTest {
        val repositorio = repositorio()
        val libres = repositorio.franjasLibres(idMedico, hoy, ahora).getOrThrow()
        val primera = libres[0]
        val segunda = libres[1]

        val citas = listOf(primera, segunda).map { franja ->
            val reserva = repositorio.reservarTemporalmente(franja.idFranja, ahora).getOrThrow()
            repositorio.confirmarCita(reserva.idReserva, "pac_01H8X9A", contacto, ahora)
                .getOrThrow().cita
        }

        val resultado = repositorio.reprogramar(citas[0].idCita, segunda.idFranja, ahora)

        val fallo = assertIs<FalloCita>(resultado.exceptionOrNull())
        assertEquals(MotivoFalloCita.FRANJA_OCUPADA, fallo.motivo)
    }

    @Test
    fun un_bloqueo_al_reves_se_rechaza_en_vez_de_crear_un_rango_imposible() = runTest {
        val resultado = repositorio().bloquearHorario(idMedico, "2026-09-09", "13:00", "11:00", "")

        assertTrue(resultado.isFailure)
    }

    @Test
    fun un_identificador_de_franja_que_no_existe_no_aparta_nada() = runTest {
        val resultado = repositorio().reservarTemporalmente("no_es_una_franja", ahora)

        assertTrue(resultado.isFailure)
        assertFalse(resultado.isSuccess)
    }
}
