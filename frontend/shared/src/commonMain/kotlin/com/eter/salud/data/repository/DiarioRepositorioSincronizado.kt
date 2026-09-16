package com.eter.salud.data.repository

import com.eter.salud.domain.model.EntradaDiario
import com.eter.salud.domain.repository.DiarioRepositorio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * El diario del PACIENTE: se escribe primero en el telefono y se sube despues.
 *
 * El diario tiene que poder escribirse sin cobertura (el sintoma ocurre donde
 * ocurre), asi que [local] es la fuente de verdad y guardar nunca espera a la
 * red. Pero sin subirlo, el medico jamas veia una entrada en ROJO: lee el
 * servidor, no este telefono. Tras cada guardado, y al abrir el diario, se
 * sube en segundo plano lo que el servidor aun no tiene; sin red, se reintenta
 * la proxima vez.
 *
 * Nunca se borra del servidor "lo que falta en el telefono": tras reinstalar
 * la app el telefono esta vacio, y eso borraria el historial clinico entero.
 * Borrar una entrada a proposito si se propaga, como mejor esfuerzo.
 */
class DiarioRepositorioSincronizado(
    private val local: DiarioRepositorio,
    private val remoto: DiarioRepositorioRemoto,
    private val alcance: CoroutineScope,
    reconexiones: Flow<Unit> = emptyFlow(),
) : DiarioRepositorio {

    private val subiendo = Mutex()

    /** El paciente cuyo diario se abrio o escribio en este telefono. */
    private var ultimoPaciente: String? = null

    init {
        // Lo escrito sin red sube solo en cuanto vuelve la conexion, sin
        // esperar a que el paciente abra el diario otra vez.
        alcance.launch {
            reconexiones.collect { ultimoPaciente?.let { sincronizar(it) } }
        }
    }

    override fun entradasDe(idPaciente: String): Flow<List<EntradaDiario>> =
        local.entradasDe(idPaciente).onStart { sincronizarEnSegundoPlano(idPaciente) }

    override suspend fun guardar(entrada: EntradaDiario): Result<Unit> =
        local.guardar(entrada).also { if (it.isSuccess) sincronizarEnSegundoPlano(entrada.idPaciente) }

    override suspend fun eliminar(idEntrada: String): Result<Unit> =
        local.eliminar(idEntrada).also { if (it.isSuccess) alcance.launch { remoto.eliminar(idEntrada) } }

    override suspend fun ultimaEntradaDe(idPaciente: String): Result<EntradaDiario?> =
        local.ultimaEntradaDe(idPaciente)

    private fun sincronizarEnSegundoPlano(idPaciente: String) {
        ultimoPaciente = idPaciente
        alcance.launch { sincronizar(idPaciente) }
    }

    /** Sube lo que el servidor no tiene. Publico para poder probarlo sin esperar. */
    suspend fun sincronizar(idPaciente: String) = subiendo.withLock {
        val enTelefono = local.entradasDe(idPaciente).first()
        if (enTelefono.isEmpty()) return@withLock
        // Sin red no se sabe que falta: se deja para la siguiente vez.
        val enServidor = remoto.idsEnServidor(idPaciente).getOrNull() ?: return@withLock
        enTelefono
            .filterNot { it.idEntrada in enServidor }
            .forEach { remoto.guardar(it) }
    }
}
