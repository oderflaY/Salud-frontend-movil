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
import io.ktor.http.headersOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
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
            assertEquals("$URL_BASE_DE_PRUEBA/conversaciones/conv_1/mensajes", peticion.url.toString())
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
        val cliente = clienteDePrueba {
            respond(
                content = """{"idMensaje":"msg_2","autor":"PACIENTE","texto":"Me duele","instante":"2026-09-12T10:01:00Z","tipo":"NORMAL"}""",
                status = HttpStatusCode.Created,
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
        val cliente = clienteDePrueba { peticion ->
            when {
                peticion.url.encodedPath.endsWith("/adjuntos") -> {
                    subioArchivo = true
                    respond(
                        content = """{"idAdjunto":"adj_servidor","tipo":"FOTO","nombre":"foto.jpg","tipoMime":"image/jpeg","url":"https://backend/archivos/adj_servidor"}""",
                        status = HttpStatusCode.Created,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

                else -> respond(
                    content = """{"idMensaje":"msg_3","autor":"PACIENTE","texto":"","instante":"2026-09-12T10:02:00Z","tipo":"NORMAL","adjunto":{"idAdjunto":"adj_servidor","tipo":"FOTO","nombre":"foto.jpg","tipoMime":"image/jpeg","url":"https://backend/archivos/adj_servidor"}}""",
                    status = HttpStatusCode.Created,
                    headers = headersOf("Content-Type", "application/json"),
                )
            }
        }
        val repositorio = ChatRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA, CanalTiempoRealFalso(), archivos)

        val mensaje = repositorio.enviarMensaje("conv_1", "", "2026-09-12T10:02:00Z", AutorMensaje.PACIENTE, adjunto)
            .getOrThrow()

        assertTrue(subioArchivo)
        // La ruta local sigue siendo la propia: no se descarga lo que este
        // dispositivo acaba de subir.
        assertEquals(rutaLocal, mensaje.adjunto?.rutaLocal)
        assertEquals("adj_servidor", mensaje.adjunto?.idAdjunto)
    }

    @Test
    fun un_mensaje_recibido_con_adjunto_descarga_y_guarda_los_bytes_una_sola_vez() = runTest {
        val archivos = ArchivosAdjuntosLocalesFalso()
        var descargas = 0
        val cliente = clienteDePrueba { peticion ->
            when {
                peticion.url.toString().endsWith("/mensajes") -> respond(
                    content = """[{"idMensaje":"msg_9","autor":"MEDICO","texto":"","instante":"2026-09-12T10:03:00Z","tipo":"NORMAL","adjunto":{"idAdjunto":"adj_remoto","tipo":"ARCHIVO","nombre":"receta.pdf","tipoMime":"application/pdf","url":"https://backend/archivos/adj_remoto"}}]""",
                    status = HttpStatusCode.OK,
                    headers = headersOf("Content-Type", "application/json"),
                )

                else -> {
                    descargas++
                    respond(content = byteArrayOf(9, 9, 9), status = HttpStatusCode.OK)
                }
            }
        }
        val repositorio = ChatRepositorioRemoto(cliente, URL_BASE_DE_PRUEBA, CanalTiempoRealFalso(), archivos)

        val mensajes = repositorio.obtenerHistorial("conv_1").getOrThrow()

        assertEquals(1, descargas)
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
    fun los_mensajes_sin_leer_llegan_por_el_canal_de_la_conversacion() = runTest {
        val canal = CanalTiempoRealFalso()
        val repositorio = ChatRepositorioRemoto(
            clienteDePrueba { respondError(HttpStatusCode.NotFound) },
            URL_BASE_DE_PRUEBA,
            canal,
            ArchivosAdjuntosLocalesFalso(),
        )

        val recibidos = mutableListOf<Int>()
        backgroundScope.launch {
            repositorio.mensajesSinLeer("conv_1").collect { recibidos.add(it) }
        }
        yield()
        canal.emitir("chat:conv_1", Json.parseToJsonElement("""{"noLeidos":3}"""))
        yield()

        assertEquals(listOf(3), recibidos)
    }
}
