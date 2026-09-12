package com.eter.salud.data.repository

import com.eter.salud.data.red.URL_BASE_DE_PRUEBA
import com.eter.salud.data.red.clienteDePrueba
import com.eter.salud.domain.diario.SeveridadDiario
import com.eter.salud.domain.model.EntradaDiario
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
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

    @Test
    fun guardar_una_entrada_la_deja_disponible_en_entradasDe() = runTest {
        val cliente = clienteDePrueba { respond("", HttpStatusCode.Created) }
        val repositorio = DiarioRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)

        repositorio.guardar(entrada)

        assertEquals(listOf(entrada), repositorio.entradasDe("pac_1").first())
    }

    @Test
    fun eliminar_una_entrada_la_retira_del_flujo_local() = runTest {
        val cliente = clienteDePrueba { respond("", HttpStatusCode.NoContent) }
        val repositorio = DiarioRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)
        repositorio.guardar(entrada)

        repositorio.eliminar("entrada_1")

        assertTrue(repositorio.entradasDe("pac_1").first().isEmpty())
    }

    @Test
    fun ultima_entrada_ausente_es_null_y_no_un_fallo() = runTest {
        val cliente = clienteDePrueba { respondError(HttpStatusCode.NotFound) }
        val repositorio = DiarioRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)

        val resultado = repositorio.ultimaEntradaDe("pac_1")

        assertTrue(resultado.isSuccess)
        assertNull(resultado.getOrThrow())
    }

    @Test
    fun un_error_del_backend_al_guardar_se_reporta_como_fallo() = runTest {
        val cliente = clienteDePrueba { respondError(HttpStatusCode.InternalServerError) }
        val repositorio = DiarioRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA)

        assertTrue(repositorio.guardar(entrada).isFailure)
    }
}
