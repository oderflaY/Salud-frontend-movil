package com.eter.salud.domain.model

import com.eter.salud.domain.diario.SeveridadDiario
import kotlinx.serialization.Serializable

/**
 * Una entrada de la bitacora del paciente.
 *
 * [severidad] se guarda calculada y no se recalcula al leer. Es deliberado: el
 * diccionario de terminos vigilados evolucionara, y si la severidad se
 * recalculara en cada lectura, una entrada de hace tres meses cambiaria de color
 * sola. El medico veria moverse un historial que ya reviso, y la trazabilidad de
 * lo que se le mostro se perderia.
 */
@Serializable
data class EntradaDiario(
    val idEntrada: String,
    val idPaciente: String,
    /** Instante ISO 8601 UTC en que el paciente escribio. */
    val instante: String,
    /** `YYYY-MM-DD`, para agrupar por dia sin convertir zonas horarias. */
    val fecha: String,
    val texto: String,
    val severidad: SeveridadDiario,
    /** Terminos vigilados hallados, para que el medico vea POR QUE ese color. */
    val terminosDetectados: List<String> = emptyList(),
)
