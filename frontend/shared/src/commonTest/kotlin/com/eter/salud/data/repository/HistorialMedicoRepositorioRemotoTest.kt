package com.eter.salud.data.repository

import com.eter.salud.data.red.URL_BASE_DE_PRUEBA
import com.eter.salud.data.red.clienteDePrueba
import com.eter.salud.domain.model.DatosPersonales
import com.eter.salud.domain.model.PacienteDto
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistorialMedicoRepositorioRemotoTest {

    @Test
    fun descarga_el_expediente_del_paciente() = runTest {
        val cliente = clienteDePrueba { peticion ->
            assertEquals("$URL_BASE_DE_PRUEBA/pacientes/pac_1", peticion.url.toString())
            respond(
                content = """{"idPaciente":"pac_1","datosPersonales":{"nombre":"Ana","apellidos":"Lopez","fechaNacimiento":"1990-01-01","genero":"F","telefono":"555"}}""",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val repositorio = HistorialMedicoRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)

        val paciente = repositorio.obtenerPaciente("pac_1").getOrThrow()

        assertEquals("Ana", paciente.datosPersonales?.nombre)
    }

    @Test
    fun actualiza_el_expediente_con_put_al_id_del_paciente() = runTest {
        val cliente = clienteDePrueba { peticion ->
            assertEquals(HttpMethod.Put, peticion.method)
            assertEquals("$URL_BASE_DE_PRUEBA/pacientes/pac_1", peticion.url.toString())
            respond("", HttpStatusCode.NoContent)
        }
        val repositorio = HistorialMedicoRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)
        val paciente = PacienteDto(
            idPaciente = "pac_1",
            datosPersonales = DatosPersonales("Ana", "Lopez", "1990-01-01", "F", "555"),
        )

        assertTrue(repositorio.actualizarHistorial(paciente).isSuccess)
    }

    @Test
    fun un_error_del_backend_se_reporta_como_fallo() = runTest {
        val cliente = clienteDePrueba { respondError(HttpStatusCode.Forbidden) }
        val repositorio = HistorialMedicoRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)

        assertTrue(repositorio.obtenerPaciente("pac_1").isFailure)
    }
}
