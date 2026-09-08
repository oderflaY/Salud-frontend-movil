package com.eter.salud.domain.repository

import com.eter.salud.domain.model.MotivoFalloAutenticacionProfesional
import com.eter.salud.domain.model.MotivoFalloRegistroProfesional
import com.eter.salud.domain.model.SesionProfesional

/**
 * Contrato de acceso del personal medico hacia la API en Go.
 *
 * Vive separado de [AutenticacionRepositorio] (el del paciente): son dos
 * portales con reglas de alta distintas -- este exige una Cedula Profesional
 * que el backend debe validar antes de otorgar acceso a expedientes
 * (DM_PerfilMedico.md, seccion 2).
 */
interface AutenticacionProfesionalRepositorio {

    suspend fun iniciarSesion(correo: String, contrasena: String): Result<SesionProfesional>

    /**
     * Da de alta al profesional. El backend devuelve la sesion ya abierta con
     * [com.eter.salud.domain.model.EstadoVerificacionCedula.PENDIENTE]: la
     * cuenta existe, pero la Cedula Profesional aun no ha sido validada.
     */
    suspend fun crearCuenta(
        correo: String,
        contrasena: String,
        nombre: String,
        apellidos: String,
        tratamiento: String,
        cedulaProfesional: String,
    ): Result<SesionProfesional>
}

class FalloAutenticacionProfesional(
    val motivo: MotivoFalloAutenticacionProfesional,
) : Exception(motivo.name)

class FalloRegistroProfesional(
    val motivo: MotivoFalloRegistroProfesional,
) : Exception(motivo.name)
