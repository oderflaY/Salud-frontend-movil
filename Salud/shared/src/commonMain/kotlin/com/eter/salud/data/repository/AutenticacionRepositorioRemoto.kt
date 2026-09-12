package com.eter.salud.data.repository

import com.eter.salud.data.red.motivoDeError
import com.eter.salud.domain.model.MotivoFalloAutenticacion
import com.eter.salud.domain.model.MotivoFalloRegistro
import com.eter.salud.domain.model.SesionPaciente
import com.eter.salud.domain.repository.AutenticacionRepositorio
import com.eter.salud.domain.repository.FalloAutenticacion
import com.eter.salud.domain.repository.FalloRegistro
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/**
 * `AutenticacionRepositorio` contra el backend en Go (`docs/CONTRATOS_BACKEND.md`,
 * seccion 1).
 *
 * No captura excepciones de red (timeout, sin conexion): las deja subir. La
 * capa de presentacion ya las trata como "sin conexion" en
 * `ejecutarSeguro`/`LoginViewModel.aErrorAutenticacion` sin que este
 * repositorio tenga que fabricar el mismo [FalloAutenticacion] a mano.
 */
class AutenticacionRepositorioRemoto(
    private val cliente: HttpClient,
    private val baseUrl: String,
) : AutenticacionRepositorio {

    override suspend fun iniciarSesion(correo: String, contrasena: String): Result<SesionPaciente> {
        val respuesta = cliente.post("$baseUrl/auth/pacientes/sesion") {
            contentType(ContentType.Application.Json)
            setBody(CuerpoAcceso(correo, contrasena))
        }
        return if (respuesta.status.isSuccess()) {
            Result.success(respuesta.body())
        } else {
            val motivo = respuesta.motivoDeError<MotivoFalloAutenticacion>() ?: MotivoFalloAutenticacion.SIN_CONEXION
            Result.failure(FalloAutenticacion(motivo))
        }
    }

    override suspend fun crearCuenta(correo: String, contrasena: String): Result<SesionPaciente> {
        val respuesta = cliente.post("$baseUrl/auth/pacientes") {
            contentType(ContentType.Application.Json)
            setBody(CuerpoAcceso(correo, contrasena))
        }
        return if (respuesta.status.isSuccess()) {
            Result.success(respuesta.body())
        } else {
            val motivo = respuesta.motivoDeError<MotivoFalloRegistro>() ?: MotivoFalloRegistro.SIN_CONEXION
            Result.failure(FalloRegistro(motivo))
        }
    }
}

@Serializable
private data class CuerpoAcceso(val correo: String, val contrasena: String)
