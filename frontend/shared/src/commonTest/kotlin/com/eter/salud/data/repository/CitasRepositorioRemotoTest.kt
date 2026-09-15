package com.eter.salud.data.repository

import com.eter.salud.data.red.CanalTiempoRealFalso
import com.eter.salud.data.red.URL_BASE_DE_PRUEBA
import com.eter.salud.data.red.clienteDePrueba
import com.eter.salud.domain.model.DatosContactoCita
import com.eter.salud.domain.model.EstadoCita
import com.eter.salud.domain.model.MotivoFalloCita
import com.eter.salud.domain.repository.FalloCita
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CitasRepositorioRemotoTest {

    private val contacto = DatosContactoCita("Ana Lopez", "555", "ana@x.com", "Chequeo")

    @Test
    fun reserva_temporalmente_una_franja() = runTest {
        val cliente = clienteDePrueba { peticion ->
            assertEquals("$URL_BASE_DE_PRUEBA/rpc/reservar_temporalmente", peticion.url.toString())
            assertTrue((peticion.body as TextContent).text.contains(""""id_franja":"franja_1""""))
            respond(
                content = """{"idReserva":"reserva_1","franja":{"idFranja":"franja_1","idMedico":"doc_1","fecha":"2026-09-15","horaInicio":"09:00","horaFin":"09:30"},"expiraEn":"2026-09-12T10:05:00Z"}""",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val repositorio = CitasRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA, CanalTiempoRealFalso())

        val reserva = repositorio.reservarTemporalmente("franja_1", "2026-09-12T10:00:00Z").getOrThrow()

        assertEquals("reserva_1", reserva.idReserva)
    }

    @Test
    fun franja_ocupada_se_traduce_al_motivo_tipado() = runTest {
        val cliente = clienteDePrueba {
            respond(
                content = """{"message":"FRANJA_OCUPADA"}""",
                status = HttpStatusCode.Conflict,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val repositorio = CitasRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA, CanalTiempoRealFalso())

        val fallo = repositorio.reservarTemporalmente("franja_1", "2026-09-12T10:00:00Z")
            .exceptionOrNull() as? FalloCita

        assertEquals(MotivoFalloCita.FRANJA_OCUPADA, fallo?.motivo)
    }

    @Test
    fun reserva_expirada_al_confirmar() = runTest {
        val cliente = clienteDePrueba {
            respond(
                content = """{"message":"RESERVA_EXPIRADA"}""",
                status = HttpStatusCode.Gone,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val repositorio = CitasRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA, CanalTiempoRealFalso())

        val fallo = repositorio.confirmarCita("reserva_1", "pac_1", contacto, "2026-09-12T10:10:00Z")
            .exceptionOrNull() as? FalloCita

        assertEquals(MotivoFalloCita.RESERVA_EXPIRADA, fallo?.motivo)
    }

    @Test
    fun cambia_el_estado_de_una_cita_por_rpc() = runTest {
        val cliente = clienteDePrueba { peticion ->
            assertEquals(HttpMethod.Post, peticion.method)
            assertEquals("$URL_BASE_DE_PRUEBA/rpc/cambiar_estado", peticion.url.toString())
            assertTrue((peticion.body as TextContent).text.contains(""""nuevo_estado":"CONFIRMADA""""))
            respond(
                content = """{"idCita":"cita_1","folio":"CITA-1","idMedico":"doc_1","nombreMedico":"Dra. Ruiz","idPaciente":"pac_1","fecha":"2026-09-15","horaInicio":"09:00","horaFin":"09:30","estado":"CONFIRMADA"}""",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val repositorio = CitasRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA, CanalTiempoRealFalso())

        val cita = repositorio.cambiarEstado("cita_1", EstadoCita.CONFIRMADA).getOrThrow()

        assertEquals(EstadoCita.CONFIRMADA, cita.estado)
    }

    @Test
    fun propone_una_cita_desde_la_agenda_del_medico() = runTest {
        val cliente = clienteDePrueba { peticion ->
            assertEquals("$URL_BASE_DE_PRUEBA/rpc/proponer_cita", peticion.url.toString())
            val cuerpo = (peticion.body as TextContent).text
            assertTrue(cuerpo.contains(""""id_franja":"franja_2""""), cuerpo)
            assertTrue(cuerpo.contains(""""id_paciente":"pac_1""""), cuerpo)
            respond(
                content = """{"idCita":"cita_2","folio":"CITA-2","idMedico":"doc_1","nombreMedico":"Dra. Ruiz","idPaciente":"pac_1","fecha":"2026-09-16","horaInicio":"10:00","horaFin":"10:30","estado":"PROPUESTA_MEDICO"}""",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val repositorio = CitasRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA, CanalTiempoRealFalso())

        val cita = repositorio.proponerCita("doc_1", "pac_1", "franja_2", contacto, "2026-09-12T10:00:00Z").getOrThrow()

        assertEquals(EstadoCita.PROPUESTA_MEDICO, cita.estado)
    }

    @Test
    fun un_error_del_backend_sin_motivo_conocido_cae_a_sin_conexion() = runTest {
        val cliente = clienteDePrueba { respondError(HttpStatusCode.InternalServerError) }
        val repositorio = CitasRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA, CanalTiempoRealFalso())

        val fallo = repositorio.liberarReserva("reserva_x").exceptionOrNull() as? FalloCita

        assertEquals(MotivoFalloCita.SIN_CONEXION, fallo?.motivo)
    }

    @Test
    fun la_agenda_del_medico_se_relee_con_cada_aviso_del_canal() = runTest {
        val canal = CanalTiempoRealFalso()
        val repositorio = CitasRepositorioRemoto(
            clienteDePrueba { peticion ->
                assertEquals("$URL_BASE_DE_PRUEBA/rpc/cargar_agenda", peticion.url.toString())
                respond("""[{"idCita":"cita_1","folio":"CITA-1","idMedico":"doc_1","nombreMedico":"Dra. Ruiz","idPaciente":"pac_1","fecha":"2026-09-15","horaInicio":"09:00","horaFin":"09:30","estado":"CONFIRMADA"}]""", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
            },
            URL_BASE_DE_PRUEBA,
            canal,
        )

        val recibidas = Channel<List<com.eter.salud.domain.model.Cita>>(Channel.UNLIMITED)
        backgroundScope.launch {
            repositorio.agendaDelMedico("doc_1").collect { recibidas.send(it) }
        }
        // Da tiempo a que el colector se suscriba al canal antes de emitir.
        kotlinx.coroutines.yield()
        // Lo que de verdad emite el backend: solo el id de la cita que cambio.
        canal.emitir("agenda:doc_1", Json.parseToJsonElement("""{"idCita":"cita_1"}"""))

        val agenda = withContext(Dispatchers.Default) { withTimeout(5_000) { recibidas.receive() } }
        assertEquals("cita_1", agenda.single().idCita)
    }

    @Test
    fun bloquear_devuelve_el_primero_de_los_bloqueos_creados() = runTest {
        val cliente = clienteDePrueba {
            respond("""[{"idCita":"cita_1","folio":"CITA-1","idMedico":"doc_1","nombreMedico":"Dra. Ruiz","idPaciente":"pac_1","fecha":"2026-09-15","horaInicio":"09:00","horaFin":"09:30","estado":"BLOQUEADO"}]""", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }
        val repositorio = CitasRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA, CanalTiempoRealFalso())

        val bloqueo = repositorio.bloquearHorario("doc_1", "2026-09-15", "09:00", "10:00", "Junta").getOrThrow()

        assertEquals(EstadoCita.BLOQUEADO, bloqueo.estado)
    }

    @Test
    fun bloquear_un_rango_sin_franjas_se_reporta_como_fallo() = runTest {
        val cliente = clienteDePrueba { respond("[]", HttpStatusCode.OK, headersOf("Content-Type", "application/json")) }
        val repositorio = CitasRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA, CanalTiempoRealFalso())

        assertTrue(repositorio.bloquearHorario("doc_1", "2026-09-15", "13:00", "14:00", "").isFailure)
    }

    @Test
    fun una_propuesta_retirada_se_distingue_de_la_falta_de_red() = runTest {
        val cliente = clienteDePrueba { peticion ->
            assertEquals("$URL_BASE_DE_PRUEBA/rpc/aceptar_propuesta", peticion.url.toString())
            respond("""{"message":"PROPUESTA_NO_DISPONIBLE"}""", HttpStatusCode.Conflict, headersOf("Content-Type", "application/json"))
        }
        val repositorio = CitasRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA, CanalTiempoRealFalso())

        val fallo = repositorio.aceptarPropuesta("cita_9").exceptionOrNull() as? FalloCita

        assertEquals(MotivoFalloCita.PROPUESTA_NO_DISPONIBLE, fallo?.motivo)
    }
}
