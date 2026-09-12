package com.eter.salud.data.repository

import com.eter.salud.data.red.URL_BASE_DE_PRUEBA
import com.eter.salud.data.red.clienteDePrueba
import com.eter.salud.domain.model.RiesgoPaciente
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PacientesVinculadosRepositorioRemotoTest {

    @Test
    fun obtiene_la_cartera_de_pacientes() = runTest {
        val cliente = clienteDePrueba { peticion ->
            assertEquals("$URL_BASE_DE_PRUEBA/profesionales/doc_1/pacientes", peticion.url.toString())
            respond(
                content = """[{"idPaciente":"pac_1","nombreCompleto":"Ana Lopez","riesgo":"ALTO","idConversacion":"conv_1"}]""",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val repositorio = PacientesVinculadosRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)

        val pacientes = repositorio.obtenerPacientesVinculados("doc_1").getOrThrow()

        assertEquals(RiesgoPaciente.ALTO, pacientes.single().riesgo)
    }

    @Test
    fun un_error_del_backend_se_reporta_como_fallo() = runTest {
        val cliente = clienteDePrueba { respondError(HttpStatusCode.InternalServerError) }
        val repositorio = PacientesVinculadosRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)

        assertTrue(repositorio.obtenerPacientesVinculados("doc_1").isFailure)
    }
}
