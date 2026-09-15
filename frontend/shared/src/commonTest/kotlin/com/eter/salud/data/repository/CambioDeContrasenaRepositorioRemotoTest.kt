package com.eter.salud.data.repository

import com.eter.salud.data.red.URL_BASE_DE_PRUEBA
import com.eter.salud.data.red.clienteDePrueba
import com.eter.salud.domain.repository.FalloCambioContrasena
import com.eter.salud.domain.repository.MotivoFalloCambioContrasena
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CambioDeContrasenaRepositorioRemotoTest {

    private val json = headersOf("Content-Type", "application/json")
    private val sesionNueva =
        """{"idPaciente":"pac_1","token":"jwt_nuevo","requiereOnboarding":false,"requiereCambioContrasena":false}"""

    @Test
    fun el_cambio_obligatorio_no_manda_la_contrasena_actual() = runTest {
        var ruta = ""
        var metodo: HttpMethod? = null
        var cuerpo = ""
        val repositorio = CambioDeContrasenaRepositorioRemoto(
            clienteDePrueba { peticion ->
                ruta = peticion.url.encodedPath
                metodo = peticion.method
                cuerpo = (peticion.body as TextContent).text
                respond(sesionNueva, HttpStatusCode.OK, json)
            },
            URL_BASE_DE_PRUEBA,
        )

        val resultado = repositorio.cambiar(contrasenaActual = null, contrasenaNueva = "MiClaveNueva2026")

        assertEquals("/auth/pacientes/contrasena", ruta)
        assertEquals(HttpMethod.Post, metodo)
        assertEquals("""{"contrasenaNueva":"MiClaveNueva2026"}""", cuerpo)
        assertEquals("jwt_nuevo", resultado.getOrThrow().token)
        assertFalse(resultado.getOrThrow().requiereCambioContrasena)
    }

    @Test
    fun el_cambio_voluntario_manda_la_actual() = runTest {
        var cuerpo = ""
        val repositorio = CambioDeContrasenaRepositorioRemoto(
            clienteDePrueba { peticion ->
                cuerpo = (peticion.body as TextContent).text
                respond(sesionNueva, HttpStatusCode.OK, json)
            },
            URL_BASE_DE_PRUEBA,
        )

        repositorio.cambiar(contrasenaActual = "Demo1234", contrasenaNueva = "OtraClave2027")

        assertTrue(cuerpo.contains(""""contrasenaActual":"Demo1234""""))
    }

    @Test
    fun cada_motivo_del_backend_llega_tipado() = runTest {
        val casos = mapOf(
            (HttpStatusCode.UnprocessableEntity to "CONTRASENA_NO_VALIDA") to MotivoFalloCambioContrasena.CONTRASENA_NO_VALIDA,
            (HttpStatusCode.Unauthorized to "CREDENCIALES_INVALIDAS") to MotivoFalloCambioContrasena.CREDENCIALES_INVALIDAS,
            (HttpStatusCode.Unauthorized to "CONTRASENA_TEMPORAL_VENCIDA") to MotivoFalloCambioContrasena.CONTRASENA_TEMPORAL_VENCIDA,
            (HttpStatusCode.Unauthorized to "SESION_REVOCADA") to MotivoFalloCambioContrasena.SESION_REVOCADA,
            (HttpStatusCode.InternalServerError to "ERROR_INTERNO") to MotivoFalloCambioContrasena.SIN_CONEXION,
        )
        for ((respuesta, esperado) in casos) {
            val (codigo, motivo) = respuesta
            val repositorio = CambioDeContrasenaRepositorioRemoto(
                clienteDePrueba { respond("""{"message":"$motivo"}""", codigo, json) },
                URL_BASE_DE_PRUEBA,
            )

            val fallo = repositorio.cambiar(null, "MiClaveNueva2026").exceptionOrNull()

            assertEquals(esperado, (fallo as FalloCambioContrasena).motivo, motivo)
        }
    }
}
