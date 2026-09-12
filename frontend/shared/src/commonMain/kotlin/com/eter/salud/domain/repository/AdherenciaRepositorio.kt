package com.eter.salud.domain.repository

import com.eter.salud.domain.model.DiaDeAdherencia
import com.eter.salud.domain.model.EstadoToma
import com.eter.salud.domain.model.ResumenAdherencia
import com.eter.salud.domain.model.TomaDelDia

/**
 * Contrato de seguimiento de tomas hacia la API en Go.
 *
 * Se mantiene aparte del expediente porque su ritmo es distinto: el historial
 * se consulta de vez en cuando y esto se escribe varias veces al dia.
 */
interface AdherenciaRepositorio {

    /** Tomas programadas para [fecha] (`YYYY-MM-DD`) con su estado actual. */
    suspend fun obtenerTomasDelDia(idPaciente: String, fecha: String): Result<List<TomaDelDia>>

    /**
     * Registra el desenlace de una toma.
     *
     * La app envia [EstadoToma.TOMADO] u [EstadoToma.OMITIDO] con el instante
     * real; distinguir "tomado" de "tomado tarde" es del backend, que conoce el
     * horario y la zona horaria del tratamiento. Hacerlo en el cliente obligaria
     * a comparar un instante UTC contra una hora local y se equivocaria al
     * cruzar la medianoche.
     */
    suspend fun registrarToma(
        idPaciente: String,
        idToma: String,
        estado: EstadoToma,
        instante: String,
    ): Result<Unit>

    /**
     * Cumplimiento dia a dia de la semana que termina en [fechaFinal].
     *
     * Devuelve siempre siete entradas en orden, incluidas las de los dias sin
     * tratamiento: la franja del panel dibuja una casilla por dia y un hueco
     * ausente descuadraria la semana entera.
     */
    suspend fun obtenerSemana(
        idPaciente: String,
        fechaFinal: String,
    ): Result<List<DiaDeAdherencia>>

    /** Cumplimiento de los siete dias que terminan en [fechaFinal]. */
    suspend fun obtenerResumenSemanal(
        idPaciente: String,
        fechaFinal: String,
    ): Result<ResumenAdherencia>
}
