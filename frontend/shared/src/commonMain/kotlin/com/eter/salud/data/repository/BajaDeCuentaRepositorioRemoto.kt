package com.eter.salud.data.repository

import com.eter.salud.data.red.motivoDeError
import com.eter.salud.domain.repository.BajaDeCuentaRepositorio
import com.eter.salud.domain.repository.FalloBaja
import com.eter.salud.domain.repository.MotivoFalloBaja
import com.eter.salud.domain.repository.TipoDeCuenta
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/** `POST /auth/{pacientes|profesionales}/baja`, servido por Axum (necesita el pepper de Argon2). */
class BajaDeCuentaRepositorioRemoto(
    private val cliente: HttpClient,
    private val baseUrl: String,
) : BajaDeCuentaRepositorio {

    override suspend fun darDeBaja(tipo: TipoDeCuenta, correo: String, contrasena: String): Result<Unit> {
        val ruta = when (tipo) {
            TipoDeCuenta.PACIENTE -> "pacientes"
            TipoDeCuenta.PROFESIONAL -> "profesionales"
        }
        val respuesta = cliente.post("$baseUrl/auth/$ruta/baja") {
            contentType(ContentType.Application.Json)
            setBody(CuerpoBaja(correo, contrasena))
        }
        if (respuesta.status.isSuccess()) return Result.success(Unit)
        val motivo = respuesta.motivoDeError<MotivoFalloBaja>() ?: MotivoFalloBaja.SIN_CONEXION
        return Result.failure(FalloBaja(motivo))
    }
}

@Serializable
private data class CuerpoBaja(val correo: String, val contrasena: String)
