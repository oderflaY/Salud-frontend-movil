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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * El diario del paciente: nunca espera a la red para guardar, y sube al
 * servidor solo lo que falta, para que el medico vea las alertas.
 */
class DiarioRepositorioSincronizadoTest {

    private fun entrada(id: String) = EntradaDiario(
        idEntrada = id,
        idPaciente = "pac_1",
        instante = "2026-09-12T10:00:00Z",
        fecha = "2026-09-12",
        texto = "Palpitaciones por la noche",
        severidad = SeveridadDiario.ROJO,
    )

    @Test
    fun guardar_escribe_en_el_telefono_aunque_no_haya_red() = runTest {
        val local = DiarioRepositorioEnMemoria()
        val remoto = DiarioRepositorioRemoto(clienteDePrueba { respondError(HttpStatusCode.ServiceUnavailable) }, URL_BASE_DE_PRUEBA)
        val diario = DiarioRepositorioSincronizado(local, remoto, backgroundScope)

        assertTrue(diario.guardar(entrada("e1")).isSuccess)
        assertEquals(listOf("e1"), local.entradasDe("pac_1").first().map { it.idEntrada })
    }

    @Test
    fun sube_solo_lo_que_el_servidor_aun_no_tiene() = runTest {
        val local = DiarioRepositorioEnMemoria()
        local.guardar(entrada("e1"))
        local.guardar(entrada("e2"))
        val subidas = mutableListOf<String>()
        val remoto = DiarioRepositorioRemoto(
            clienteDePrueba { peticion ->
                if (peticion.method == HttpMethod.Get) {
                    // El servidor ya tiene e1.
                    respond("""[{"idEntrada":"e1"}]""", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
                } else {
                    subidas += Regex(""""idEntrada":"([^"]+)"""").find((peticion.body as TextContent).text)!!.groupValues[1]
                    respond("", HttpStatusCode.Created)
                }
            },
            URL_BASE_DE_PRUEBA,
        )

        DiarioRepositorioSincronizado(local, remoto, backgroundScope).sincronizar("pac_1")

        assertEquals(listOf("e2"), subidas)
    }

    @Test
    fun sin_red_no_sube_nada_y_no_revienta() = runTest {
        val local = DiarioRepositorioEnMemoria()
        local.guardar(entrada("e1"))
        var intentosDeSubida = 0
        val remoto = DiarioRepositorioRemoto(
            clienteDePrueba { peticion ->
                if (peticion.method != HttpMethod.Get) intentosDeSubida++
                respondError(HttpStatusCode.ServiceUnavailable)
            },
            URL_BASE_DE_PRUEBA,
        )

        DiarioRepositorioSincronizado(local, remoto, backgroundScope).sincronizar("pac_1")

        assertEquals(0, intentosDeSubida, "sin saber que tiene el servidor, no se sube a ciegas")
    }
}
