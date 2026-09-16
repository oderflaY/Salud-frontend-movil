package com.eter.salud.data.red

import com.eter.salud.data.adjuntos.ArchivosAdjuntosLocales
import com.eter.salud.data.repository.ChatRepositorioRemoto
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TiempoRealYCacheTest {

    @Test
    fun la_espera_entre_reconexiones_crece_hasta_treinta_segundos() {
        val esperas = (1..8).map { ConexionTiempoReal.esperaDeReconexion(it) }

        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L, 30_000L), esperas)
    }

    @Test
    fun el_chat_se_vuelve_a_pedir_cuando_el_socket_se_reabre() = runTest(UnconfinedTestDispatcher()) {
        val canal = CanalTiempoRealFalso()
        val repositorio = ChatRepositorioRemoto(
            clienteDePrueba { respond("[]") },
            URL_BASE_DE_PRUEBA,
            canal,
            SinArchivos,
            intervaloRevisionMs = Long.MAX_VALUE / 2,
        )
        var avisos = 0
        val escucha = launch { repositorio.cambiosEn("conv_1").collect { avisos++ } }

        canal.simularReconexion()

        assertEquals(1, avisos)
        escucha.cancel()
    }

    @Test
    fun las_copias_en_disco_sobreviven_y_se_borran_al_cerrar_sesion() = runTest {
        val carpeta = Path(SystemTemporaryDirectory, "salud_prueba_${Random.nextLong()}")
        val almacen = AlmacenDeRespuestasEnArchivos(carpeta.toString())
        val copia = RespuestaGuardada(200, "application/json", """{"valor":1}""")

        withContext(Dispatchers.Default) {
            almacen.guardar("llave", copia)
            almacen.guardar("llave", copia)
        }

        assertEquals(copia, AlmacenDeRespuestasEnArchivos(carpeta.toString()).leer("llave"))
        assertTrue(SystemFileSystem.list(carpeta).none { it.name.endsWith(".tmp") })
        almacen.borrarTodo()
        assertNull(almacen.leer("llave"))
        SystemFileSystem.delete(carpeta, mustExist = false)
    }
}

private object SinArchivos : ArchivosAdjuntosLocales {
    override fun rutaLocalDe(idConversacion: String, idAdjunto: String, nombreSugerido: String) = ""
    override suspend fun existe(rutaLocal: String) = false
    override suspend fun leerBytes(rutaLocal: String): ByteArray? = null
    override suspend fun guardarBytes(rutaLocal: String, bytes: ByteArray) = false
}
