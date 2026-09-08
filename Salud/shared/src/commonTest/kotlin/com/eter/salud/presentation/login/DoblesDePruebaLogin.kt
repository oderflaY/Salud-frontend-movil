package com.eter.salud.presentation.login

import com.eter.salud.domain.model.SesionPaciente
import com.eter.salud.domain.repository.AutenticacionRepositorio

/** Repositorio de acceso falso: captura las credenciales y simula la respuesta. */
class AutenticacionRepositorioFalso(
    private val resultado: Result<SesionPaciente> = Result.success(
        SesionPaciente(
            idPaciente = "pac_01H8X9A",
            token = "jwt_de_prueba",
            requiereOnboarding = false,
        ),
    ),
    private val resultadoRegistro: Result<SesionPaciente> = Result.success(
        SesionPaciente(
            idPaciente = "pac_nuevo",
            token = "jwt_de_prueba",
            requiereOnboarding = true,
        ),
    ),
) : AutenticacionRepositorio {

    var invocaciones: Int = 0
        private set

    var cuentasCreadas: Int = 0
        private set

    var ultimoCorreo: String? = null
        private set

    var ultimaContrasena: String? = null
        private set

    override suspend fun iniciarSesion(
        correo: String,
        contrasena: String,
    ): Result<SesionPaciente> {
        invocaciones++
        ultimoCorreo = correo
        ultimaContrasena = contrasena
        return resultado
    }

    override suspend fun crearCuenta(
        correo: String,
        contrasena: String,
    ): Result<SesionPaciente> {
        cuentasCreadas++
        ultimoCorreo = correo
        ultimaContrasena = contrasena
        return resultadoRegistro
    }
}
