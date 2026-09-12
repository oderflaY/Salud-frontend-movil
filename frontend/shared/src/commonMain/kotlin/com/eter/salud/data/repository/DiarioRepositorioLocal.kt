package com.eter.salud.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.eter.salud.data.db.BaseSalud
import com.eter.salud.data.db.Entrada_diario
import com.eter.salud.data.local.ContextoDeBase
import com.eter.salud.domain.diario.SeveridadDiario
import com.eter.salud.domain.diario.TriageDelDiario
import com.eter.salud.domain.model.EntradaDiario
import com.eter.salud.domain.repository.DiarioRepositorio
import com.eter.salud.domain.time.RelojSalud
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Bitacora del diario sobre la tabla `entrada_diario` (EntradaDiario.sq).
 *
 * "Eliminar" es una baja logica: el esquema prohibe el borrado fisico con un
 * disparador, porque lo que el medico ya vio no puede desaparecer del
 * expediente. La entrada se oculta al paciente y queda para auditoria.
 */
class DiarioRepositorioLocal(
    private val base: BaseSalud,
    private val reloj: RelojSalud,
) : DiarioRepositorio {

    override fun entradasDe(idPaciente: String): Flow<List<EntradaDiario>> =
        base.entradaDiarioQueries.entradasDe(idPaciente)
            .asFlow()
            .mapToList(ContextoDeBase)
            .map { filas -> filas.map { it.aEntrada() } }

    override suspend fun guardar(entrada: EntradaDiario): Result<Unit> = withContext(ContextoDeBase) {
        insertarEntrada(base, entrada)
        Result.success(Unit)
    }

    override suspend fun eliminar(idEntrada: String): Result<Unit> = withContext(ContextoDeBase) {
        base.entradaDiarioQueries.ocultar(reloj.instanteActual(), idEntrada)
        Result.success(Unit)
    }

    override suspend fun ultimaEntradaDe(idPaciente: String): Result<EntradaDiario?> =
        withContext(ContextoDeBase) {
            Result.success(base.entradaDiarioQueries.ultimaDe(idPaciente).executeAsOneOrNull()?.aEntrada())
        }

    private fun Entrada_diario.aEntrada() = EntradaDiario(
        idEntrada = id,
        idPaciente = id_paciente,
        instante = timestamp,
        fecha = fecha,
        texto = texto_bitacora,
        severidad = SeveridadDiario.desdeCodigo(severidad_calculada.toInt()),
        terminosDetectados = palabras_clave_detectadas.split(SEPARADOR_TERMINOS).filter { it.isNotBlank() },
    )
}

/** Inserta la entrada con la version del diccionario que calculo su triage. */
internal fun insertarEntrada(base: BaseSalud, entrada: EntradaDiario, siNoExiste: Boolean = false) {
    val terminos = entrada.terminosDetectados.joinToString(SEPARADOR_TERMINOS)
    val version = TriageDelDiario.VERSION_DICCIONARIO.toLong()
    with(entrada) {
        if (siNoExiste) {
            base.entradaDiarioQueries.insertarSiNoExiste(
                idEntrada, idPaciente, instante, fecha, texto, severidad.codigo.toLong(), terminos, version,
            )
        } else {
            base.entradaDiarioQueries.insertar(
                idEntrada, idPaciente, instante, fecha, texto, severidad.codigo.toLong(), terminos, version,
            )
        }
    }
}

private const val SEPARADOR_TERMINOS = ", "
