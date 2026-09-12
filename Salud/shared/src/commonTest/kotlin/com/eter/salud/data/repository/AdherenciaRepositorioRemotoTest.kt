package com.eter.salud.data.repository

import com.eter.salud.data.red.URL_BASE_DE_PRUEBA
import com.eter.salud.data.red.clienteDePrueba
import com.eter.salud.domain.model.EstadoToma
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdherenciaRepositorioRemotoTest {

    @Test
    fun obtiene_las_tomas_del_dia() = runTest {
        val cliente = clienteDePrueba { peticion ->
            assertTrue(peticion.url.toString().startsWith("$URL_BASE_DE_PRUEBA/pacientes/pac_1/tomas"))
            assertEquals("2026-09-12", peticion.url.parameters["fecha"])
            respond(
                content = """[{"idToma":"t1","idTratamiento":"trt_1","medicamento":"Losartan","dosis":"50mg","horaProgramada":"08:00","estado":"pendiente"}]""",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val repositorio = AdherenciaRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)

        val tomas = repositorio.obtenerTomasDelDia("pac_1", "2026-09-12").getOrThrow()

        assertEquals(EstadoToma.PENDIENTE, tomas.single().estado)
    }

    @Test
    fun registra_una_toma_en_la_ruta_correcta() = runTest {
        val cliente = clienteDePrueba { peticion ->
            assertEquals("$URL_BASE_DE_PRUEBA/pacientes/pac_1/tomas/t1/registro", peticion.url.toString())
            respond("", HttpStatusCode.NoContent)
        }
        val repositorio = AdherenciaRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)

        val resultado = repositorio.registrarToma("pac_1", "t1", EstadoToma.TOMADO, "2026-09-12T08:05:00Z")

        assertTrue(resultado.isSuccess)
    }

    @Test
    fun la_semana_siempre_se_reporta_tal_cual_llega() = runTest {
        val cliente = clienteDePrueba {
            respond(
                content = """[{"fecha":"2026-09-06","tomasProgramadas":2,"tomasCumplidas":2,"enCurso":false}]""",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val repositorio = AdherenciaRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)

        val semana = repositorio.obtenerSemana("pac_1", "2026-09-12").getOrThrow()

        assertEquals(1, semana.size)
    }

    @Test
    fun un_error_del_backend_se_reporta_como_fallo() = runTest {
        val cliente = clienteDePrueba { respondError(HttpStatusCode.InternalServerError) }
        val repositorio = AdherenciaRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)

        assertTrue(repositorio.obtenerResumenSemanal("pac_1", "2026-09-12").isFailure)
    }
}
