package com.eter.salud.data.red

import androidx.compose.runtime.Composable
import com.eter.salud.data.seguridad.Sha256
import com.eter.salud.data.seguridad.aHex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString

/**
 * Las copias sin conexion, un archivo por lectura, en el almacenamiento
 * privado de la app (el mismo nivel de proteccion que la base SQLite).
 */
class AlmacenDeRespuestasEnArchivos(directorio: String) : AlmacenDeRespuestas {

    private val carpeta = Path(directorio)
    private val candado = Mutex()

    /**
     * Huella de lo ultimo escrito por llave. El chat abierto se revisa cada
     * pocos segundos y casi siempre llega lo mismo: sin esto se reescribia el
     * archivo en cada revision, gastando memoria flash y bateria.
     */
    private val escritas = HashMap<String, String>()

    override suspend fun leer(clave: String): RespuestaGuardada? = enDisco {
        val archivo = Path(carpeta, clave)
        if (!SystemFileSystem.exists(archivo)) return@enDisco null
        val texto = SystemFileSystem.source(archivo).buffered().use { it.readString() }
        JsonRed.decodeFromString(RespuestaGuardada.serializer(), texto)
    }

    override suspend fun guardar(clave: String, respuesta: RespuestaGuardada) {
        enDisco {
            val texto = JsonRed.encodeToString(RespuestaGuardada.serializer(), respuesta)
            // SHA-256 y no hashCode(): una colision dejaria una copia vieja sin avisar.
            val huella = Sha256.resumen(texto.encodeToByteArray()).aHex()
            val destino = Path(carpeta, clave)
            if (escritas[clave] == huella && SystemFileSystem.exists(destino)) return@enDisco
            SystemFileSystem.createDirectories(carpeta)
            val temporal = Path(carpeta, "$clave.tmp")
            SystemFileSystem.sink(temporal).buffered().use { it.writeString(texto) }
            // Renombrar es atomico: una app cerrada a media escritura no deja una copia rota.
            SystemFileSystem.atomicMove(temporal, destino)
            escritas[clave] = huella
        }
    }

    override suspend fun borrarTodo() {
        enDisco {
            escritas.clear()
            if (SystemFileSystem.exists(carpeta)) {
                SystemFileSystem.list(carpeta).forEach { SystemFileSystem.delete(it, mustExist = false) }
            }
        }
    }

    private suspend fun <T> enDisco(bloque: () -> T): T? = withContext(Dispatchers.IO) {
        candado.withLock { runCatching(bloque).getOrNull() }
    }
}

/** Carpeta privada de la app para las copias sin conexion. */
@Composable
expect fun rememberDirectorioSinConexion(): String
