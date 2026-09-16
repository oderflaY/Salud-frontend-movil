package com.eter.salud.data.red

import com.eter.salud.domain.model.SesionPaciente
import com.eter.salud.presentation.sesion.SesionUiState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RespaldoSinConexionTest {

    private class Red {
        var caida = false
        var llamadas = 0
        var respuesta = """{"valor":1}"""
    }

    private fun sesion(idPaciente: String) = FuenteDeSesionFalsa(
        SesionUiState(paciente = SesionPaciente(idPaciente = idPaciente, token = "t", requiereOnboarding = false), restaurando = false),
    )

    private fun cliente(
        red: Red,
        almacen: AlmacenDeRespuestas,
        monitor: MonitorDeConexion,
        fuente: FuenteDeSesionFalsa = sesion("pac_1"),
    ) = HttpClient(MockEngine) {
        configurarPluginsRed(fuente, almacenSinConexion = almacen, monitorDeConexion = monitor)
        engine {
            addHandler {
                red.llamadas++
                if (red.caida) throw RuntimeException("sin red")
                respond(red.respuesta, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
            }
        }
    }

    @Test
    fun sin_red_muestra_la_ultima_lectura_guardada() = runTest {
        val red = Red()
        val monitor = MonitorDeConexion()
        val http = cliente(red, AlmacenDeRespuestasEnMemoria(), monitor)

        assertEquals("""{"valor":1}""", http.get("$URL_BASE_DE_PRUEBA/paciente_expediente").bodyAsText())
        red.caida = true
        val sinRed = http.get("$URL_BASE_DE_PRUEBA/paciente_expediente")

        assertEquals("""{"valor":1}""", sinRed.bodyAsText())
        assertEquals("1", sinRed.headers[ENCABEZADO_SIN_CONEXION])
        assertFalse(monitor.enLinea.value)
    }

    @Test
    fun sabiendo_que_no_hay_red_no_espera_al_servidor() = runTest {
        val red = Red()
        val monitor = MonitorDeConexion()
        val http = cliente(red, AlmacenDeRespuestasEnMemoria(), monitor)
        http.get("$URL_BASE_DE_PRUEBA/pacientes_vinculados")
        monitor.registrarFalloDeRed()
        val antes = red.llamadas

        http.get("$URL_BASE_DE_PRUEBA/pacientes_vinculados")

        assertEquals(antes, red.llamadas)
    }

    @Test
    fun las_lecturas_rpc_se_guardan_por_cuerpo_y_las_escrituras_nunca() = runTest {
        val red = Red()
        val http = cliente(red, AlmacenDeRespuestasEnMemoria(), MonitorDeConexion())
        suspend fun rpc(nombre: String, cuerpo: String) = http.post("$URL_BASE_DE_PRUEBA/rpc/$nombre") {
            contentType(ContentType.Application.Json)
            setBody(cuerpo)
        }
        rpc("obtener_historial", """{"id_conversacion":"conv_1"}""")
        rpc("enviar_mensaje", """{"texto_mensaje":"hola"}""")
        red.caida = true

        assertEquals("""{"valor":1}""", rpc("obtener_historial", """{"id_conversacion":"conv_1"}""").bodyAsText())
        assertFailsWith<RuntimeException> { rpc("obtener_historial", """{"id_conversacion":"conv_2"}""") }
        assertFailsWith<RuntimeException> { rpc("enviar_mensaje", """{"texto_mensaje":"hola"}""") }
    }

    @Test
    fun la_copia_de_un_paciente_no_se_le_muestra_a_otro() = runTest {
        val red = Red()
        val almacen = AlmacenDeRespuestasEnMemoria()
        cliente(red, almacen, MonitorDeConexion(), sesion("pac_1")).get("$URL_BASE_DE_PRUEBA/paciente_expediente")
        red.caida = true

        assertFailsWith<RuntimeException> {
            cliente(red, almacen, MonitorDeConexion(), sesion("pac_2")).get("$URL_BASE_DE_PRUEBA/paciente_expediente")
        }
    }

    @Test
    fun al_volver_la_red_avisa_una_sola_vez_y_guarda_lo_nuevo() = runTest {
        val red = Red()
        val monitor = MonitorDeConexion()
        val http = cliente(red, AlmacenDeRespuestasEnMemoria(), monitor)
        var avisos = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { monitor.reconexiones.collect { avisos++ } }
        red.caida = true
        runCatching { http.get("$URL_BASE_DE_PRUEBA/directorio_medico") }
        red.caida = false
        red.respuesta = """{"valor":2}"""

        http.get("$URL_BASE_DE_PRUEBA/directorio_medico")
        http.get("$URL_BASE_DE_PRUEBA/directorio_medico")
        red.caida = true

        assertEquals(1, avisos)
        assertEquals("""{"valor":2}""", http.get("$URL_BASE_DE_PRUEBA/directorio_medico").bodyAsText())
    }

    @Test
    fun sin_conexion_sondea_hasta_que_el_servidor_responde() = runTest {
        val monitor = MonitorDeConexion()
        var intentos = 0
        backgroundScope.launch { monitor.vigilarRecuperacion(intervaloMs = 1_000) { ++intentos >= 3 } }
        testScheduler.runCurrent()
        monitor.registrarFalloDeRed()

        testScheduler.advanceTimeBy(2_500)
        assertFalse(monitor.enLinea.value)
        testScheduler.advanceTimeBy(1_000)

        assertTrue(monitor.enLinea.value)
        assertEquals(3, intentos)
    }

    @Test
    fun la_sonda_y_el_socket_nunca_se_guardan() {
        val builder = HttpRequestBuilder().apply { url("$URL_BASE_DE_PRUEBA/healthz") }
        assertNull(claveDeLectura(builder, "pac_1"))
        val socket = HttpRequestBuilder().apply { url("ws://10.0.0.2:8000/realtime") }
        assertNull(claveDeLectura(socket, "pac_1"))
    }
}

