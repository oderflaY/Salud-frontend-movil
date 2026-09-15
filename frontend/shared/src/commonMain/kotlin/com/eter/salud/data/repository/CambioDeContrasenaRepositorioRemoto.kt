package com.eter.salud.data.repository

import com.eter.salud.data.red.motivoDeError
import com.eter.salud.domain.model.SesionPaciente
import com.eter.salud.domain.repository.CambioDeContrasenaRepositorio
import com.eter.salud.domain.repository.FalloCambioContrasena
import com.eter.salud.domain.repository.MotivoFalloCambioContrasena
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/** `POST /auth/pacientes/contrasena`, servido por Axum (necesita el pepper de Argon2). */
class CambioDeContrasenaRepositorioRemoto(
    private val cliente: HttpClient,
    private val baseUrl: String,
) : CambioDeContrasenaRepositorio {

    override suspend fun cambiar(contrasenaActual: String?, contrasenaNueva: String): Result<SesionPaciente> {
        val respuesta = cliente.post("$baseUrl/auth/pacientes/contrasena") {
            contentType(ContentType.Application.Json)
            setBody(CuerpoCambioContrasena(contrasenaActual, contrasenaNueva))
        }
        if (respuesta.status.isSuccess()) return Result.success(respuesta.body())
        val motivo = respuesta.motivoDeError<MotivoFalloCambioContrasena>() ?: MotivoFalloCambioContrasena.SIN_CONEXION
        return Result.failure(FalloCambioContrasena(motivo))
    }
}

/** `contrasenaActual` en null no viaja (`explicitNulls = false` en `JsonRed`). */
@Serializable
private data class CuerpoCambioContrasena(
    val contrasenaActual: String? = null,
    val contrasenaNueva: String,
)
