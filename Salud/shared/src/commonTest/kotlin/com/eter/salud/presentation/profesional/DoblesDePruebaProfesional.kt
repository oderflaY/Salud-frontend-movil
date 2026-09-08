package com.eter.salud.presentation.profesional

import com.eter.salud.domain.model.EstadoVerificacionCedula
import com.eter.salud.domain.model.PacienteVinculado
import com.eter.salud.domain.model.SesionProfesional
import com.eter.salud.domain.repository.AutenticacionProfesionalRepositorio
import com.eter.salud.domain.repository.PacientesVinculadosRepositorio

/** Repositorio de acceso de personal medico falso. */
class AutenticacionProfesionalRepositorioFalso(
    private val resultado: Result<SesionProfesional> = Result.success(
        SesionProfesional(
            idMedico = "doc_889900A",
            token = "jwt_de_prueba",
            nombre = "Elena",
            apellidos = "Ruiz Santos",
            tratamiento = "Dra.",
            estadoVerificacion = EstadoVerificacionCedula.APROBADO,
        ),
    ),
    private val resultadoRegistro: Result<SesionProfesional> = Result.success(
        SesionProfesional(
            idMedico = "doc_local_1",
            token = "jwt_de_prueba",
            nombre = "Carlos",
            apellidos = "Mendez",
            tratamiento = "Dr.",
            estadoVerificacion = EstadoVerificacionCedula.PENDIENTE,
        ),
    ),
) : AutenticacionProfesionalRepositorio {

    var invocaciones: Int = 0
        private set

    var cuentasCreadas: Int = 0
        private set

    var ultimoCorreo: String? = null
        private set

    var ultimaContrasena: String? = null
        private set

    var ultimaCedula: String? = null
        private set

    override suspend fun iniciarSesion(
        correo: String,
        contrasena: String,
    ): Result<SesionProfesional> {
        invocaciones++
        ultimoCorreo = correo
        ultimaContrasena = contrasena
        return resultado
    }

    override suspend fun crearCuenta(
        correo: String,
        contrasena: String,
        nombre: String,
        apellidos: String,
        tratamiento: String,
        cedulaProfesional: String,
    ): Result<SesionProfesional> {
        cuentasCreadas++
        ultimoCorreo = correo
        ultimaContrasena = contrasena
        ultimaCedula = cedulaProfesional
        return resultadoRegistro
    }
}

/** Cartera de pacientes vinculados falsa. */
class PacientesVinculadosRepositorioFalso(
    private val resultado: Result<List<PacienteVinculado>> = Result.success(emptyList()),
) : PacientesVinculadosRepositorio {

    var consultas: MutableList<String> = mutableListOf()
        private set

    override suspend fun obtenerPacientesVinculados(
        idMedico: String,
    ): Result<List<PacienteVinculado>> {
        consultas += idMedico
        return resultado
    }
}
