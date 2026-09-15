package com.eter.salud.data.repository

import com.eter.salud.data.adjuntos.ArchivosAdjuntosLocales
import com.eter.salud.data.red.CanalTiempoRealFalso
import com.eter.salud.data.red.URL_BASE_DE_PRUEBA
import com.eter.salud.data.red.clienteDePrueba
import com.eter.salud.domain.model.Adjunto
import com.eter.salud.domain.model.AutorMensaje
import com.eter.salud.domain.model.TipoAdjunto
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Sistema de archivos en memoria: nada toca disco real en la prueba. */
private class ArchivosAdjuntosLocalesFalso : ArchivosAdjuntosLocales {
    val guardados = mutableMapOf<String, ByteArray>()

    override fun rutaLocalDe(idConversacion: String, idAdjunto: String, nombreSugerido: String): String =
        "memoria://$idConversacion/$idAdjunto"

    override suspend fun existe(rutaLocal: String): Boolean = guardados.containsKey(rutaLocal)

    override suspend fun leerBytes(rutaLocal: String): ByteArray? = guardados[rutaLocal]

    override suspend fun guardarBytes(rutaLocal: String, bytes: ByteArray): Boolean {
        guardados[rutaLocal] = bytes
        return true
    }
}

class ChatRepositorioRemotoTest {

    @Test
    fun obtiene_el_historial_de_la_conversacion() = runTest {
        val cliente = clienteDePrueba { peticion ->
            assertEquals("$URL_BASE_DE_PRUEBA/rpc/obtener_historial", peticion.url.toString())
            assertTrue((peticion.body as TextContent).text.contains(""""id_conversacion":"conv_1""""))
            respond(
                content = """[{"idMensaje":"msg_1","autor":"MEDICO","texto":"Hola","instante":"2026-09-12T10:00:00Z","tipo":"NORMAL"}]""",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val repositorio = ChatRepositorioRemoto(
            cliente, URL_BASE_DE_PRUEBA, CanalTiempoRealFalso(), ArchivosAdjuntosLocalesFalso(),
        )

        val mensajes = repositorio.obtenerHistorial("conv_1").getOrThrow()

        assertEquals("Hola", mensajes.single().texto)
    }

    @Test
    fun envia_un_mensaje_de_solo_texto() = runTest {
        val cliente = clienteDePrueba { peticion ->
            assertEquals("$URL_BASE_DE_PRUEBA/rpc/enviar_mensaje", peticion.url.toString())
            val cuerpo = (peticion.body as TextContent).text
            // Nombres de parametro de la funcion SQL, no del DTO.
            assertTrue(cuerpo.contains(""""texto_mensaje":"Me duele""""), cuerpo)
            assertTrue(cuerpo.contains(""""autor_remitente":"PACIENTE""""), cuerpo)
            assertFalse(cuerpo.contains("id_adjunto"), "sin adjunto el campo no viaja: $cuerpo")
            respond(
                // La RPC devuelve un array con el mensaje creado.
                content = """[{"idMensaje":"msg_2","autor":"PACIENTE","texto":"Me duele","instante":"2026-09-12T10:01:00Z","tipo":"NORMAL","adjunto":null}]""",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val repositorio = ChatRepositorioRemoto(
            cliente, URL_BASE_DE_PRUEBA, CanalTiempoRealFalso(), ArchivosAdjuntosLocalesFalso(),
        )

        val mensaje = repositorio.enviarMensaje("conv_1", "Me duele", "2026-09-12T10:01:00Z", AutorMensaje.PACIENTE)
            .getOrThrow()

        assertEquals("msg_2", mensaje.idMensaje)
    }

    @Test
    fun enviar_con_adjunto_sube_los_bytes_y_conserva_la_copia_local() = runTest {
        val archivos = ArchivosAdjuntosLocalesFalso()
        val rutaLocal = "memoria://conv_1/adj_local"
        archivos.guardarBytes(rutaLocal, byteArrayOf(1, 2, 3))
        val adjunto = Adjunto("adj_local", TipoAdjunto.FOTO, "foto.jpg", rutaLocal, "image/jpeg")

        var subioArchivo = false
        var idAdjuntoEnviado = ""
        val cliente = clienteDePrueba { peticion ->
            when (peticion.url.encodedPath) {
                "/chat/conv_1/adjuntos" -> {
                    subioArchivo = true
                    respond(
                        content = """{"idAdjunto":"adj_servidor","tipo":"FOTO","nombre":"foto.jpg","tipoMime":"image/jpeg","url":"/chat/conv_1/adjuntos/adj_servidor"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

                "/rpc/enviar_mensaje" -> {
                    idAdjuntoEnviado = Regex(""""id_adjunto":"([^"]+)"""")
                        .find((peticion.body as TextContent).text)?.groupValues?.get(1).orEmpty()
                    respond(
                        content = """[{"idMensaje":"msg_3","autor":"PACIENTE","texto":"","instante":"2026-09-12T10:02:00Z","tipo":"NORMAL","adjunto":{"idAdjunto":"adj_servidor","tipo":"FOTO","nombre":"foto.jpg","tipoMime":"image/jpeg","url":"/chat/conv_1/adjuntos/adj_servidor"}}]""",
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

                else -> error("ruta inesperada: ${peticion.url}")
            }
        }
        val repositorio = ChatRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA, CanalTiempoRealFalso(), archivos)

        val mensaje = repositorio.enviarMensaje("conv_1", "", "2026-09-12T10:02:00Z", AutorMensaje.PACIENTE, adjunto)
            .getOrThrow()

        assertTrue(subioArchivo)
        // El mensaje referencia el id que asigno el SERVIDOR, no el temporal local.
        assertEquals("adj_servidor", idAdjuntoEnviado)
        // La ruta local sigue siendo la propia: no se descarga lo que este
        // dispositivo acaba de subir.
        assertEquals(rutaLocal, mensaje.adjunto?.rutaLocal)
        assertEquals("adj_servidor", mensaje.adjunto?.idAdjunto)
    }

    @Test
    fun un_mensaje_recibido_con_adjunto_descarga_y_guarda_los_bytes_una_sola_vez() = runTest {
        val archivos = ArchivosAdjuntosLocalesFalso()
        var descargas = 0
        var urlDescarga = ""
        val cliente = clienteDePrueba { peticion ->
            when {
                peticion.url.encodedPath == "/rpc/obtener_historial" -> respond(
                    content = """[{"idMensaje":"msg_9","autor":"MEDICO","texto":"","instante":"2026-09-12T10:03:00Z","tipo":"NORMAL","adjunto":{"idAdjunto":"adj_remoto","tipo":"ARCHIVO","nombre":"receta.pdf","tipoMime":"application/pdf","url":"/chat/conv_1/adjuntos/adj_remoto"}}]""",
                    status = HttpStatusCode.OK,
                    headers = headersOf("Content-Type", "application/json"),
                )

                else -> {
                    descargas++
                    urlDescarga = peticion.url.toString()
                    respond(content = byteArrayOf(9, 9, 9), status = HttpStatusCode.OK)
                }
            }
        }
        val repositorio = ChatRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA, CanalTiempoRealFalso(), archivos)

        val mensajes = repositorio.obtenerHistorial("conv_1").getOrThrow()

        assertEquals(1, descargas)
        // El backend manda la ruta relativa; se resuelve contra la BASE_URL.
        assertEquals("$URL_BASE_DE_PRUEBA/chat/conv_1/adjuntos/adj_remoto", urlDescarga)
        assertTrue(archivos.guardados.containsKey(mensajes.single().adjunto?.rutaLocal))
    }

    @Test
    fun un_error_del_backend_se_reporta_como_fallo() = runTest {
        val cliente = clienteDePrueba { respondError(HttpStatusCode.InternalServerError) }
        val repositorio = ChatRepositorioRemoto(
            cliente, URL_BASE_DE_PRUEBA, CanalTiempoRealFalso(), ArchivosAdjuntosLocalesFalso(),
        )

        assertTrue(repositorio.obtenerHistorial("conv_1").isFailure)
    }

    @Test
    fun los_sin_leer_se_cuentan_al_abrir_y_otra_vez_con_cada_aviso_del_canal() = runTest {
        val canal = CanalTiempoRealFalso()
        var conteo = 2
        val repositorio = ChatRepositorioRemoto(
            clienteDePrueba { peticion ->
                assertEquals("$URL_BASE_DE_PRUEBA/rpc/mensajes_sin_leer", peticion.url.toString())
                respond(conteo.toString(), HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
            },
            URL_BASE_DE_PRUEBA,
            canal,
            ArchivosAdjuntosLocalesFalso(),
        )

        val recibidos = Channel<Int>(Channel.UNLIMITED)
        backgroundScope.launch {
            repositorio.mensajesSinLeer("conv_1").collect { recibidos.send(it) }
        }
        assertEquals(2, recibidos.recibirPronto())
        conteo = 3
        // El backend avisa "mensaje_nuevo" con el mensaje, no con un conteo.
        canal.emitir("chat:conv_1", Json.parseToJsonElement("""{"idMensaje":"msg_9","autor":"MEDICO","texto":"hola"}"""))

        assertEquals(3, recibidos.recibirPronto())
    }

    @Test
    fun marcar_como_leida_baja_el_contador_sin_esperar_otro_mensaje() = runTest {
        var conteo = 4
        val repositorio = ChatRepositorioRemoto(
            clienteDePrueba { peticion ->
                when (peticion.url.encodedPath) {
                    "/rpc/marcar_conversacion_leida" -> {
                        conteo = 0
                        respond("", HttpStatusCode.NoContent)
                    }
                    else -> respond(conteo.toString(), HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
                }
            },
            URL_BASE_DE_PRUEBA,
            CanalTiempoRealFalso(),
            ArchivosAdjuntosLocalesFalso(),
        )

        val recibidos = Channel<Int>(Channel.UNLIMITED)
        backgroundScope.launch {
            repositorio.mensajesSinLeer("conv_1").collect { recibidos.send(it) }
        }
        assertEquals(4, recibidos.recibirPronto())
        repositorio.marcarConversacionLeida("conv_1")

        assertEquals(0, recibidos.recibirPronto())
    }
}

/**
 * El motor falso de Ktor resuelve en su propio hilo, fuera del reloj virtual
 * de `runTest`: hay que esperar el valor en tiempo real, con un tope.
 */
private suspend fun <T> Channel<T>.recibirPronto(): T =
    withContext(Dispatchers.Default) { withTimeout(5_000) { receive() } }
