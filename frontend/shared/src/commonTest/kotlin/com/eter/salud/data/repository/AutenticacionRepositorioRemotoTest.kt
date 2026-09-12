package com.eter.salud.data.repository

import com.eter.salud.data.red.URL_BASE_DE_PRUEBA
import com.eter.salud.data.red.clienteDePrueba
import com.eter.salud.domain.model.MotivoFalloAutenticacion
import com.eter.salud.domain.model.MotivoFalloRegistro
import com.eter.salud.domain.repository.FalloAutenticacion
import com.eter.salud.domain.repository.FalloRegistro
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondBadRequest
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutenticacionRepositorioRemotoTest {

    @Test
    fun inicia_sesion_correctamente_cuando_el_backend_responde_200() = kotlinx.coroutines.test.runTest {
        val cliente = clienteDePrueba { peticion ->
            assertEquals("$URL_BASE_DE_PRUEBA/auth/pacientes/sesion", peticion.url.toString())
            respond(
                content = """{"idPaciente":"pac_1","token":"jwt_x","requiereOnboarding":false}""",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val repositorio = AutenticacionRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)

        val resultado = repositorio.iniciarSesion("a@b.com", "1234")

        assertEquals("pac_1", resultado.getOrThrow().idPaciente)
    }

    @Test
    fun credenciales_invalidas_se_traduce_al_motivo_tipado() = kotlinx.coroutines.test.runTest {
        val cliente = clienteDePrueba {
            respond(
                content = """{"motivo":"CREDENCIALES_INVALIDAS"}""",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val repositorio = AutenticacionRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)

        val resultado = repositorio.iniciarSesion("a@b.com", "mala")

        val fallo = resultado.exceptionOrNull() as? FalloAutenticacion
        assertEquals(MotivoFalloAutenticacion.CREDENCIALES_INVALIDAS, fallo?.motivo)
    }

    @Test
    fun un_error_sin_cuerpo_reconocible_cae_a_sin_conexion() = kotlinx.coroutines.test.runTest {
        val cliente = clienteDePrueba { respondBadRequest() }
        val repositorio = AutenticacionRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)

        val resultado = repositorio.iniciarSesion("a@b.com", "1234")

        val fallo = resultado.exceptionOrNull() as? FalloAutenticacion
        assertEquals(MotivoFalloAutenticacion.SIN_CONEXION, fallo?.motivo)
    }

    @Test
    fun correo_ya_registrado_al_crear_cuenta() = kotlinx.coroutines.test.runTest {
        val cliente = clienteDePrueba {
            respond(
                content = """{"motivo":"CORREO_YA_REGISTRADO"}""",
                status = HttpStatusCode.Conflict,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val repositorio = AutenticacionRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)

        val resultado = repositorio.crearCuenta("a@b.com", "1234")

        val fallo = resultado.exceptionOrNull() as? FalloRegistro
        assertEquals(MotivoFalloRegistro.CORREO_YA_REGISTRADO, fallo?.motivo)
        assertTrue(resultado.isFailure)
    }
}
