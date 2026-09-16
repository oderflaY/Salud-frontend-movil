package com.eter.salud.data.repository

import com.eter.salud.data.red.URL_BASE_DE_PRUEBA
import com.eter.salud.data.red.clienteDePrueba
import com.eter.salud.domain.diario.SeveridadDiario
import com.eter.salud.domain.model.EntradaDiario
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiarioRepositorioRemotoTest {

    private val entrada = EntradaDiario(
        idEntrada = "entrada_1",
        idPaciente = "pac_1",
        instante = "2026-09-12T10:00:00Z",
        fecha = "2026-09-12",
        texto = "Me duele la cabeza",
        severidad = SeveridadDiario.AMBAR,
    )

    private val json = headersOf("Content-Type", "application/json")

    @Test
    fun guarda_en_la_vista_con_el_id_que_genero_el_telefono() = runTest {
        val cliente = clienteDePrueba { peticion ->
            assertEquals("$URL_BASE_DE_PRUEBA/entradas_diario", peticion.url.toString())
            assertTrue((peticion.body as TextContent).text.contains(""""idEntrada":"entrada_1""""))
            respond("", HttpStatusCode.Created)
        }

        assertTrue(DiarioRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA).guardar(entrada).isSuccess)
    }

    @Test
    fun subir_dos_veces_la_misma_entrada_no_es_un_fallo() = runTest {
        // La llave primaria choca (409): la entrada ya estaba en el servidor.
        val cliente = clienteDePrueba { respond("""{"code":"23505"}""", HttpStatusCode.Conflict, json) }

        assertTrue(DiarioRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA).guardar(entrada).isSuccess)
    }

    @Test
    fun la_ultima_entrada_se_pide_ordenada_y_de_a_una() = runTest {
        val cliente = clienteDePrueba { peticion ->
            assertEquals("eq.pac_1", peticion.url.parameters["idPaciente"])
            assertEquals("instante.desc", peticion.url.parameters["order"])
            assertEquals("1", peticion.url.parameters["limit"])
            respond(
                """[{"idEntrada":"entrada_1","idPaciente":"pac_1","instante":"2026-09-12T10:00:00+00:00","fecha":"2026-09-12","texto":"Me duele la cabeza","severidad":"ROJO","terminosDetectados":["cefalea"]}]""",
                HttpStatusCode.OK,
                json,
            )
        }

        val ultima = DiarioRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA).ultimaEntradaDe("pac_1").getOrThrow()

        assertEquals(SeveridadDiario.ROJO, ultima?.severidad)
    }

    @Test
    fun el_medico_ve_el_color_mas_grave_entre_la_app_y_el_analisis_del_servidor() = runTest {
        val cliente = clienteDePrueba {
            respond(
                """[{"idEntrada":"entrada_1","idPaciente":"pac_1","instante":"2026-09-12T10:00:00+00:00","fecha":"2026-09-12","texto":"Presion 190/120","severidad":"VERDE","terminosDetectados":[],"severidadServidor":"ROJO","analisisRiesgo":{"nivel":"ROJO","hallazgos":[{"termino":"Presión muy alta (190/120)","categoria":"URGENCIA_FISICA"}]}}]""",
                HttpStatusCode.OK,
                json,
            )
        }

        val ultima = DiarioRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA).ultimaEntradaDe("pac_1").getOrThrow()

        assertEquals(SeveridadDiario.ROJO, ultima?.severidad)
        assertEquals(listOf("Presión muy alta (190/120)"), ultima?.terminosDetectados)
    }

    @Test
    fun un_color_del_servidor_mas_leve_no_baja_el_de_la_app() = runTest {
        val cliente = clienteDePrueba {
            respond(
                """[{"idEntrada":"entrada_1","idPaciente":"pac_1","instante":"2026-09-12T10:00:00+00:00","fecha":"2026-09-12","texto":"x","severidad":"ROJO","severidadServidor":"VERDE"}]""",
                HttpStatusCode.OK,
                json,
            )
        }

        assertEquals(SeveridadDiario.ROJO, DiarioRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA).ultimaEntradaDe("pac_1").getOrThrow()?.severidad)
    }

    @Test
    fun sin_entradas_la_ultima_es_null_y_no_un_fallo() = runTest {
        val cliente = clienteDePrueba { respond("[]", HttpStatusCode.OK, json) }

        assertNull(DiarioRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA).ultimaEntradaDe("pac_1").getOrThrow())
    }

    @Test
    fun eliminar_filtra_por_el_id_de_la_entrada() = runTest {
        val cliente = clienteDePrueba { peticion ->
            assertEquals(HttpMethod.Delete, peticion.method)
            assertEquals("eq.entrada_1", peticion.url.parameters["idEntrada"])
            respond("", HttpStatusCode.NoContent)
        }

        assertTrue(DiarioRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA).eliminar("entrada_1").isSuccess)
    }

    @Test
    fun un_error_del_backend_al_guardar_se_reporta_como_fallo() = runTest {
        val cliente = clienteDePrueba { respondError(HttpStatusCode.InternalServerError) }

        assertTrue(DiarioRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA).guardar(entrada).isFailure)
    }
}
