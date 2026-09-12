package com.eter.salud.data.repository

import com.eter.salud.data.red.URL_BASE_DE_PRUEBA
import com.eter.salud.data.red.clienteDePrueba
import com.eter.salud.domain.model.Especialidad
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DirectorioMedicoRepositorioRemotoTest {

    @Test
    fun medico_vinculado_ausente_es_null_y_no_un_fallo() = runTest {
        val cliente = clienteDePrueba { respondError(HttpStatusCode.NotFound) }
        val repositorio = DirectorioMedicoRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)

        val resultado = repositorio.obtenerMedicoVinculado("pac_1")

        assertTrue(resultado.isSuccess)
        assertNull(resultado.getOrThrow())
    }

    @Test
    fun busca_en_el_directorio_filtrando_por_especialidad() = runTest {
        val cliente = clienteDePrueba { peticion ->
            assertEquals("CARDIOLOGIA", peticion.url.parameters["especialidad"])
            respond(
                content = """[{"idMedico":"doc_1","nombreCompleto":"Dra. Ruiz","especialidad":"CARDIOLOGIA","cedulaVerificada":true}]""",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val repositorio = DirectorioMedicoRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)

        val doctores = repositorio.buscarDirectorio(Especialidad.CARDIOLOGIA).getOrThrow()

        assertEquals("doc_1", doctores.single().idMedico)
    }

    @Test
    fun perfil_de_medico_dado_de_baja_es_null() = runTest {
        val cliente = clienteDePrueba { respondError(HttpStatusCode.NotFound) }
        val repositorio = DirectorioMedicoRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)

        val resultado = repositorio.obtenerPerfilDeMedico("doc_baja")

        assertTrue(resultado.isSuccess)
        assertNull(resultado.getOrThrow())
    }

    @Test
    fun solicita_vinculacion_con_el_medico_elegido() = runTest {
        val cliente = clienteDePrueba {
            respond(
                content = """{"idMedico":"doc_1","nombreCompleto":"Dra. Ruiz","especialidad":"CARDIOLOGIA","idConversacion":"conv_1"}""",
                status = HttpStatusCode.Created,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val repositorio = DirectorioMedicoRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)

        val vinculo = repositorio.solicitarVinculacion("pac_1", "doc_1").getOrThrow()

        assertEquals("conv_1", vinculo.idConversacion)
    }
}
