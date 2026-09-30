package com.eter.salud.data.expediente

import androidx.compose.runtime.Composable
import com.eter.salud.data.red.JsonRed
import com.eter.salud.domain.model.BloqueDeExpediente
import com.eter.salud.domain.model.DocumentoClinico
import com.eter.salud.domain.model.ExpedienteCompartido
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString

/**
 * Lo que cada paciente comparte con su medico: bloques ocultos y documentos.
 *
 * Es un contrato aparte de [com.eter.salud.domain.repository.HistorialMedicoRepositorio]
 * a proposito: aquel es el expediente clinico (lo que el paciente ES), esto es
 * la decision del paciente sobre quien lo ve. Mezclarlos haria que editar un
 * dato de presion pudiera, por error, cambiar lo que el medico tiene permitido
 * leer.
 */
interface ExpedienteCompartidoRepositorio {
    fun de(idPaciente: String): Flow<ExpedienteCompartido>
    suspend fun cambiarBloque(idPaciente: String, bloque: BloqueDeExpediente, visible: Boolean)
    suspend fun agregarDocumento(idPaciente: String, documento: DocumentoClinico)
    suspend fun cambiarVisibilidad(idPaciente: String, idDocumento: String, visible: Boolean)

    /** Quita el documento y borra su archivo del telefono. */
    suspend fun eliminarDocumento(idPaciente: String, idDocumento: String)
}

/**
 * Base comun: un [ExpedienteCompartido] por paciente en memoria, y dos
 * ganchos para persistirlo. La version en disco y la de pruebas solo difieren
 * en esos ganchos.
 */
abstract class ExpedienteCompartidoBase : ExpedienteCompartidoRepositorio {
    private val candado = Mutex()
    private val porPaciente = mutableMapOf<String, MutableStateFlow<ExpedienteCompartido>>()

    protected abstract suspend fun leer(idPaciente: String): ExpedienteCompartido
    protected abstract suspend fun escribir(idPaciente: String, expediente: ExpedienteCompartido)
    protected open suspend fun borrarArchivo(ruta: String) {}

    private suspend fun flujo(idPaciente: String): MutableStateFlow<ExpedienteCompartido> = candado.withLock {
        porPaciente[idPaciente] ?: MutableStateFlow(leer(idPaciente)).also { porPaciente[idPaciente] = it }
    }

    override fun de(idPaciente: String): Flow<ExpedienteCompartido> = flow { emitAll(flujo(idPaciente)) }

    private suspend fun cambiar(idPaciente: String, cambio: (ExpedienteCompartido) -> ExpedienteCompartido) {
        val flujo = flujo(idPaciente)
        val nuevo = cambio(flujo.value)
        flujo.value = nuevo
        escribir(idPaciente, nuevo)
    }

    override suspend fun cambiarBloque(idPaciente: String, bloque: BloqueDeExpediente, visible: Boolean) =
        cambiar(idPaciente) {
            it.copy(bloquesOcultos = if (visible) it.bloquesOcultos - bloque else it.bloquesOcultos + bloque)
        }

    override suspend fun agregarDocumento(idPaciente: String, documento: DocumentoClinico) =
        cambiar(idPaciente) { it.copy(documentos = it.documentos.filterNot { d -> d.idDocumento == documento.idDocumento } + documento) }

    override suspend fun cambiarVisibilidad(idPaciente: String, idDocumento: String, visible: Boolean) =
        cambiar(idPaciente) {
            it.copy(documentos = it.documentos.map { d -> if (d.idDocumento == idDocumento) d.copy(visibleParaMedico = visible) else d })
        }

    override suspend fun eliminarDocumento(idPaciente: String, idDocumento: String) {
        val documento = flujo(idPaciente).value.documentos.firstOrNull { it.idDocumento == idDocumento } ?: return
        cambiar(idPaciente) { it.copy(documentos = it.documentos.filterNot { d -> d.idDocumento == idDocumento }) }
        // Despues de quitarlo del indice: si borrar falla queda un archivo
        // huerfano, nunca un documento en la lista que ya no abre.
        borrarArchivo(documento.rutaLocal)
    }
}

/**
 * En disco: `expedientes/<idPaciente>.json` en el almacenamiento privado.
 *
 * Un archivo JSON y no una tabla de la base local: la base del telefono de la
 * demostracion ya existe y no tiene migraciones, y una tabla nueva no se
 * crearia en una instalacion que ya estaba. Un archivo propio no toca la base.
 */
class ExpedienteCompartidoEnArchivos(directorio: String) : ExpedienteCompartidoBase() {
    private val carpeta = Path(directorio)

    private fun archivoDe(idPaciente: String) = Path(carpeta, "${idPaciente.filter { it.isLetterOrDigit() || it == '_' }}.json")

    override suspend fun leer(idPaciente: String): ExpedienteCompartido = withContext(Dispatchers.IO) {
        runCatching {
            val archivo = archivoDe(idPaciente)
            if (!SystemFileSystem.exists(archivo)) return@runCatching ExpedienteCompartido()
            val texto = SystemFileSystem.source(archivo).buffered().use { it.readString() }
            JsonRed.decodeFromString(ExpedienteCompartido.serializer(), texto)
        }.getOrDefault(ExpedienteCompartido())
    }

    override suspend fun escribir(idPaciente: String, expediente: ExpedienteCompartido) {
        withContext(Dispatchers.IO) {
            runCatching {
                SystemFileSystem.createDirectories(carpeta)
                val destino = archivoDe(idPaciente)
                val temporal = Path(carpeta, "${destino.name}.tmp")
                SystemFileSystem.sink(temporal).buffered().use {
                    it.writeString(JsonRed.encodeToString(ExpedienteCompartido.serializer(), expediente))
                }
                // Atomico: cerrar la app a media escritura no deja un indice roto.
                SystemFileSystem.atomicMove(temporal, destino)
            }
        }
    }

    override suspend fun borrarArchivo(ruta: String) {
        withContext(Dispatchers.IO) { runCatching { SystemFileSystem.delete(Path(ruta), mustExist = false) } }
    }
}

/** Para pruebas: nada toca disco. */
class ExpedienteCompartidoEnMemoria : ExpedienteCompartidoBase() {
    val guardados = mutableMapOf<String, ExpedienteCompartido>()
    val borrados = mutableListOf<String>()

    override suspend fun leer(idPaciente: String) = guardados[idPaciente] ?: ExpedienteCompartido()
    override suspend fun escribir(idPaciente: String, expediente: ExpedienteCompartido) {
        guardados[idPaciente] = expediente
    }
    override suspend fun borrarArchivo(ruta: String) {
        borrados += ruta
    }
}

/** Carpeta privada donde vive el indice de cada paciente. */
@Composable
expect fun rememberDirectorioDeExpedientes(): String
