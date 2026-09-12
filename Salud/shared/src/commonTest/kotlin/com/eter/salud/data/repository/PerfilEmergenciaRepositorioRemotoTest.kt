package com.eter.salud.data.repository

import com.eter.salud.data.red.URL_BASE_DE_PRUEBA
import com.eter.salud.data.red.FuenteDeSesionFalsa
import com.eter.salud.data.red.clienteDePrueba
import com.eter.salud.domain.repository.FalloEmergencia
import com.eter.salud.domain.repository.MotivoFalloEmergencia
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PerfilEmergenciaRepositorioRemotoTest {

    @Test
    fun consulta_sin_sesion_abierta_y_sin_header_de_autorizacion() = runTest {
        val cliente = clienteDePrueba(FuenteDeSesionFalsa()) { peticion ->
            assertNull(peticion.headers["Authorization"])
            respond(
                content = """{"idTarjetaRfid":"rfid_1","datosPersonales":{"nombre":"Ana","apellidos":"Lopez","fechaNacimiento":"1990-01-01"},"perfilEmergenciaReducido":{"tipoSangre":"O+"}}""",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val repositorio = PerfilEmergenciaRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)

        val perfil = repositorio.consultarPorTarjeta("rfid_1").getOrThrow()

        assertEquals("O+", perfil.perfilEmergenciaReducido.tipoSangre)
    }

    @Test
    fun tarjeta_revocada_se_traduce_al_motivo_tipado() = runTest {
        val cliente = clienteDePrueba {
            respond(
                content = """{"motivo":"TARJETA_REVOCADA"}""",
                status = HttpStatusCode.Forbidden,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val repositorio = PerfilEmergenciaRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)

        val fallo = repositorio.consultarPorTarjeta("rfid_1").exceptionOrNull() as? FalloEmergencia

        assertEquals(MotivoFalloEmergencia.TARJETA_REVOCADA, fallo?.motivo)
    }
}
