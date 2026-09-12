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
import io.ktor.http.headersOf
import kotlinx.coroutines.launch
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
            assertEquals("$URL_BASE_DE_PRUEBA/franjas/franja_1/reserva", peticion.url.toString())
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
                content = """{"motivo":"FRANJA_OCUPADA"}""",
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
                content = """{"motivo":"RESERVA_EXPIRADA"}""",
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
    fun cambia_el_estado_de_una_cita_con_patch() = runTest {
        val cliente = clienteDePrueba { peticion ->
            assertEquals(HttpMethod.Patch, peticion.method)
            assertEquals("$URL_BASE_DE_PRUEBA/citas/cita_1/estado", peticion.url.toString())
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
            assertEquals("$URL_BASE_DE_PRUEBA/franjas/franja_2/propuesta", peticion.url.toString())
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
    fun la_agenda_del_medico_se_alimenta_del_canal_de_tiempo_real() = runTest {
        val canal = CanalTiempoRealFalso()
        val repositorio = CitasRepositorioRemoto(
            clienteDePrueba { respondError(HttpStatusCode.NotFound) },
            URL_BASE_DE_PRUEBA,
            canal,
        )

        val recibidas = mutableListOf<List<com.eter.salud.domain.model.Cita>>()
        backgroundScope.launch {
            repositorio.agendaDelMedico("doc_1").collect { recibidas.add(it) }
        }

        val payload = Json.parseToJsonElement(
            """[{"idCita":"cita_1","folio":"CITA-1","idMedico":"doc_1","nombreMedico":"Dra. Ruiz","idPaciente":"pac_1","fecha":"2026-09-15","horaInicio":"09:00","horaFin":"09:30","estado":"CONFIRMADA"}]""",
        )
        // Da tiempo a que `onSubscription` conecte antes de emitir.
        kotlinx.coroutines.yield()
        canal.emitir("agenda:doc_1", payload)
        kotlinx.coroutines.yield()

        assertTrue(recibidas.isNotEmpty())
        assertEquals("cita_1", recibidas.single().single().idCita)
    }
}
