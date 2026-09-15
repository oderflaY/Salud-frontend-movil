package com.eter.salud.data.repository

import com.eter.salud.data.red.URL_BASE_DE_PRUEBA
import com.eter.salud.data.red.clienteDePrueba
import com.eter.salud.domain.repository.FalloBaja
import com.eter.salud.domain.repository.MotivoFalloBaja
import com.eter.salud.domain.repository.TipoDeCuenta
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BajaDeCuentaRepositorioRemotoTest {

    @Test
    fun un_paciente_se_da_de_baja_en_su_ruta_con_sus_credenciales() = runTest {
        val cliente = clienteDePrueba { peticion ->
            assertEquals(HttpMethod.Post, peticion.method)
            assertEquals("$URL_BASE_DE_PRUEBA/auth/pacientes/baja", peticion.url.toString())
            val cuerpo = (peticion.body as TextContent).text
            assertTrue(cuerpo.contains(""""correo":"ana@correo.mx""""), cuerpo)
            assertTrue(cuerpo.contains(""""contrasena":"Demo1234""""), cuerpo)
            respond("", HttpStatusCode.NoContent)
        }
        val repositorio = BajaDeCuentaRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)

        assertTrue(repositorio.darDeBaja(TipoDeCuenta.PACIENTE, "ana@correo.mx", "Demo1234").isSuccess)
    }

    @Test
    fun un_medico_usa_la_ruta_de_profesionales() = runTest {
        val cliente = clienteDePrueba { peticion ->
            assertEquals("$URL_BASE_DE_PRUEBA/auth/profesionales/baja", peticion.url.toString())
            respond("", HttpStatusCode.NoContent)
        }
        val repositorio = BajaDeCuentaRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)

        assertTrue(repositorio.darDeBaja(TipoDeCuenta.PROFESIONAL, "dra@correo.mx", "Demo1234").isSuccess)
    }

    @Test
    fun credenciales_malas_se_traducen_al_motivo_tipado() = runTest {
        val cliente = clienteDePrueba {
            respond(
                content = """{"message":"CREDENCIALES_INVALIDAS"}""",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val repositorio = BajaDeCuentaRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)

        val fallo = repositorio.darDeBaja(TipoDeCuenta.PACIENTE, "a@b.mx", "mala").exceptionOrNull() as? FalloBaja
        assertEquals(MotivoFalloBaja.CREDENCIALES_INVALIDAS, fallo?.motivo)
    }

    @Test
    fun un_error_sin_motivo_reconocible_cae_a_sin_conexion() = runTest {
        val cliente = clienteDePrueba { respondError(HttpStatusCode.BadGateway) }
        val repositorio = BajaDeCuentaRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)

        val fallo = repositorio.darDeBaja(TipoDeCuenta.PACIENTE, "a@b.mx", "x").exceptionOrNull() as? FalloBaja
        assertEquals(MotivoFalloBaja.SIN_CONEXION, fallo?.motivo)
    }
}
