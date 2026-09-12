package com.eter.salud.data.repository

import com.eter.salud.data.red.motivoDeError
import com.eter.salud.domain.model.MotivoFalloAutenticacionProfesional
import com.eter.salud.domain.model.MotivoFalloRegistroProfesional
import com.eter.salud.domain.model.SesionProfesional
import com.eter.salud.domain.repository.AutenticacionProfesionalRepositorio
import com.eter.salud.domain.repository.FalloAutenticacionProfesional
import com.eter.salud.domain.repository.FalloRegistroProfesional
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/**
 * `AutenticacionProfesionalRepositorio` contra el backend en Go
 * (`docs/CONTRATOS_BACKEND.md`, seccion 2).
 */
class AutenticacionProfesionalRepositorioRemoto(
    private val cliente: HttpClient,
    private val baseUrl: String,
) : AutenticacionProfesionalRepositorio {

    override suspend fun iniciarSesion(correo: String, contrasena: String): Result<SesionProfesional> {
        val respuesta = cliente.post("$baseUrl/auth/profesionales/sesion") {
            contentType(ContentType.Application.Json)
            setBody(CuerpoAccesoProfesional(correo, contrasena))
        }
        return if (respuesta.status.isSuccess()) {
            Result.success(respuesta.body())
        } else {
            val motivo = respuesta.motivoDeError<MotivoFalloAutenticacionProfesional>()
                ?: MotivoFalloAutenticacionProfesional.SIN_CONEXION
            Result.failure(FalloAutenticacionProfesional(motivo))
        }
    }

    override suspend fun crearCuenta(
        correo: String,
        contrasena: String,
        nombre: String,
        apellidos: String,
        tratamiento: String,
        cedulaProfesional: String,
    ): Result<SesionProfesional> {
        val respuesta = cliente.post("$baseUrl/auth/profesionales") {
            contentType(ContentType.Application.Json)
            setBody(
                CuerpoAltaProfesional(
                    correo = correo,
                    contrasena = contrasena,
                    nombre = nombre,
                    apellidos = apellidos,
                    tratamiento = tratamiento,
                    cedulaProfesional = cedulaProfesional,
                ),
            )
        }
        return if (respuesta.status.isSuccess()) {
            Result.success(respuesta.body())
        } else {
            val motivo = respuesta.motivoDeError<MotivoFalloRegistroProfesional>()
                ?: MotivoFalloRegistroProfesional.SIN_CONEXION
            Result.failure(FalloRegistroProfesional(motivo))
        }
    }
}

@Serializable
private data class CuerpoAccesoProfesional(val correo: String, val contrasena: String)

@Serializable
private data class CuerpoAltaProfesional(
    val correo: String,
    val contrasena: String,
    val nombre: String,
    val apellidos: String,
    val tratamiento: String,
    val cedulaProfesional: String,
)
