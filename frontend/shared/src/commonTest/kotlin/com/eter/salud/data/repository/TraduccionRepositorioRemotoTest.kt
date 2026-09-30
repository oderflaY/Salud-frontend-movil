package com.eter.salud.data.repository

import com.eter.salud.data.red.URL_BASE_DE_PRUEBA
import com.eter.salud.data.red.clienteDePrueba
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TraduccionRepositorioRemotoTest {

    private val json = headersOf("Content-Type", "application/json")

    @Test
    fun manda_el_texto_y_el_idioma_de_quien_lee() = runTest {
        var cuerpo = ""
        val cliente = clienteDePrueba { peticion ->
            assertEquals("$URL_BASE_DE_PRUEBA/chat/traducir", peticion.url.toString())
            cuerpo = (peticion.body as TextContent).text
            respond("""{"traduccion":"My stomach still hurts"}""", HttpStatusCode.OK, json)
        }

        val traduccion = TraduccionRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)
            .traducir("Todavia me duele el estomago", "en")

        assertEquals("My stomach still hurts", traduccion.getOrThrow())
        assertTrue(cuerpo.contains(""""idiomaDestino":"en""""), cuerpo)
        assertTrue(cuerpo.contains("Todavia me duele el estomago"), cuerpo)
    }

    @Test
    fun el_mismo_mensaje_no_se_traduce_dos_veces() = runTest {
        var llamadas = 0
        val cliente = clienteDePrueba {
            llamadas++
            respond("""{"traduccion":"Hello"}""", HttpStatusCode.OK, json)
        }
        val repositorio = TraduccionRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)

        repositorio.traducir("Hola", "en")
        val segunda = repositorio.traducir("  Hola  ", "en")

        assertEquals("Hello", segunda.getOrThrow())
        assertEquals(1, llamadas)
    }

    @Test
    fun otro_idioma_destino_si_vuelve_a_pedirse() = runTest {
        var llamadas = 0
        val cliente = clienteDePrueba {
            llamadas++
            respond("""{"traduccion":"x"}""", HttpStatusCode.OK, json)
        }
        val repositorio = TraduccionRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)

        repositorio.traducir("Hola", "en")
        repositorio.traducir("Hola", "pt")

        assertEquals(2, llamadas)
    }

    @Test
    fun sin_traduccion_configurada_en_el_backend_es_un_fallo_no_un_texto_vacio() = runTest {
        val cliente = clienteDePrueba { respondError(HttpStatusCode.ServiceUnavailable) }

        assertTrue(TraduccionRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA).traducir("Hola", "en").isFailure)
    }

    @Test
    fun una_traduccion_vacia_del_backend_se_trata_como_fallo() = runTest {
        val cliente = clienteDePrueba { respond("""{"traduccion":"   "}""", HttpStatusCode.OK, json) }

        assertTrue(TraduccionRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA).traducir("Hola", "en").isFailure)
    }
}
