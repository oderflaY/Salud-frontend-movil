package com.eter.salud.data.repository

import com.eter.salud.data.red.motivoDeError
import com.eter.salud.domain.model.PerfilSupervivencia
import com.eter.salud.domain.repository.FalloEmergencia
import com.eter.salud.domain.repository.MotivoFalloEmergencia
import com.eter.salud.domain.repository.PerfilEmergenciaRepositorio
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess

/**
 * `PerfilEmergenciaRepositorio` contra el backend en Go
 * (`docs/CONTRATOS_BACKEND.md`, seccion 5). Es el unico repositorio que se
 * invoca sin sesion: el cliente HTTP no adjunta `Authorization` si no hay
 * sesion abierta (ver `data/red/ClienteHttp.kt`), asi que esta llamada
 * funciona igual con el telefono recien instalado.
 */
class PerfilEmergenciaRepositorioRemoto(
    private val cliente: HttpClient,
    private val baseUrl: String,
) : PerfilEmergenciaRepositorio {

    override suspend fun consultarPorTarjeta(idTarjetaRfid: String): Result<PerfilSupervivencia> {
        val respuesta = cliente.get("$baseUrl/tarjetas/$idTarjetaRfid/perfil-supervivencia")
        return if (respuesta.status.isSuccess()) {
            Result.success(respuesta.body())
        } else {
            val motivo = respuesta.motivoDeError<MotivoFalloEmergencia>() ?: MotivoFalloEmergencia.SIN_CONEXION
            Result.failure(FalloEmergencia(motivo))
        }
    }
}
