package com.eter.salud.data.repository

import com.eter.salud.data.red.URL_BASE_DE_PRUEBA
import com.eter.salud.data.red.clienteDePrueba
import com.eter.salud.domain.model.EstadoVerificacionCedula
import com.eter.salud.domain.model.MotivoFalloRegistroProfesional
import com.eter.salud.domain.repository.FalloRegistroProfesional
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AutenticacionProfesionalRepositorioRemotoTest {

    @Test
    fun alta_correcta_llega_pendiente_de_verificacion() = runTest {
        val cliente = clienteDePrueba { peticion ->
            assertEquals("$URL_BASE_DE_PRUEBA/auth/profesionales", peticion.url.toString())
            respond(
                content = """
                    {"idMedico":"doc_1","token":"jwt","nombre":"Elena","apellidos":"Ruiz",
                     "tratamiento":"Dra.","estadoVerificacion":"PENDIENTE"}
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val repositorio = AutenticacionProfesionalRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)

        val sesion = repositorio.crearCuenta("a@b.com", "1234", "Elena", "Ruiz", "Dra.", "CED123").getOrThrow()

        assertEquals(EstadoVerificacionCedula.PENDIENTE, sesion.estadoVerificacion)
    }

    @Test
    fun cedula_duplicada_se_traduce_al_motivo_tipado() = runTest {
        val cliente = clienteDePrueba {
            respond(
                content = """{"motivo":"CEDULA_YA_REGISTRADA"}""",
                status = HttpStatusCode.Conflict,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val repositorio = AutenticacionProfesionalRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)

        val resultado = repositorio.crearCuenta("a@b.com", "1234", "Elena", "Ruiz", "Dra.", "CED123")

        val fallo = resultado.exceptionOrNull() as? FalloRegistroProfesional
        assertEquals(MotivoFalloRegistroProfesional.CEDULA_YA_REGISTRADA, fallo?.motivo)
    }
}
