package com.eter.salud.data.repository

import com.eter.salud.data.red.URL_BASE_DE_PRUEBA
import com.eter.salud.data.red.clienteDePrueba
import com.eter.salud.domain.model.DatosPersonales
import com.eter.salud.domain.model.PacienteDto
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PacienteRepositorioRemotoTest {

    private val paciente = PacienteDto(
        datosPersonales = DatosPersonales("Ana", "Lopez", "1990-01-01", "F", "555"),
    )

    @Test
    fun registra_al_paciente_y_devuelve_el_id_asignado() = runTest {
        val cliente = clienteDePrueba { peticion ->
            assertEquals("$URL_BASE_DE_PRUEBA/pacientes", peticion.url.toString())
            respond(
                content = """{"idPaciente":"pac_nuevo"}""",
                status = HttpStatusCode.Created,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val repositorio = PacienteRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)

        assertEquals("pac_nuevo", repositorio.registrarPaciente(paciente).getOrThrow())
    }

    @Test
    fun un_fallo_del_backend_se_reporta_como_fallo() = runTest {
        val cliente = clienteDePrueba { respondError(HttpStatusCode.InternalServerError) }
        val repositorio = PacienteRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)

        assertTrue(repositorio.registrarPaciente(paciente).isFailure)
    }
}
