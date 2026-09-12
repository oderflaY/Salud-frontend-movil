package com.eter.salud.data.repository

import com.eter.salud.data.db.BaseSalud
import com.eter.salud.data.db.Cuenta_profesional
import com.eter.salud.data.local.ContextoDeBase
import com.eter.salud.data.local.idLocal
import com.eter.salud.data.seguridad.HuellaDeContrasena
import com.eter.salud.domain.model.EstadoVerificacionCedula
import com.eter.salud.domain.model.MotivoFalloAutenticacionProfesional
import com.eter.salud.domain.model.MotivoFalloRegistroProfesional
import com.eter.salud.domain.model.SesionProfesional
import com.eter.salud.domain.repository.AutenticacionProfesionalRepositorio
import com.eter.salud.domain.repository.FalloAutenticacionProfesional
import com.eter.salud.domain.repository.FalloRegistroProfesional
import kotlinx.coroutines.withContext

/**
 * Cuentas de personal medico en la base SQLite del dispositivo.
 *
 * Mismas reglas que la version en memoria: una cedula solo se registra una vez y
 * toda alta nueva nace [EstadoVerificacionCedula.PENDIENTE] (DM_PerfilMedico.md,
 * seccion 2: nadie se autoverifica).
 */
class AutenticacionProfesionalRepositorioLocal(
    private val base: BaseSalud,
) : AutenticacionProfesionalRepositorio {

    override suspend fun iniciarSesion(
        correo: String,
        contrasena: String,
    ): Result<SesionProfesional> = withContext(ContextoDeBase) {
        val cuenta = base.cuentasQueries.profesionalPorCorreo(correo).executeAsOneOrNull()
        if (cuenta != null && HuellaDeContrasena.verificar(contrasena, cuenta.huella_contrasena)) {
            Result.success(cuenta.aSesion())
        } else {
            Result.failure(
                FalloAutenticacionProfesional(MotivoFalloAutenticacionProfesional.CREDENCIALES_INVALIDAS),
            )
        }
    }

    override suspend fun crearCuenta(
        correo: String,
        contrasena: String,
        nombre: String,
        apellidos: String,
        tratamiento: String,
        cedulaProfesional: String,
    ): Result<SesionProfesional> = withContext(ContextoDeBase) {
        val consultas = base.cuentasQueries
        if (consultas.profesionalPorCorreo(correo).executeAsOneOrNull() != null) {
            return@withContext Result.failure(
                FalloRegistroProfesional(MotivoFalloRegistroProfesional.CORREO_YA_REGISTRADO),
            )
        }
        if (consultas.contarCedula(cedulaProfesional).executeAsOne() > 0) {
            return@withContext Result.failure(
                FalloRegistroProfesional(MotivoFalloRegistroProfesional.CEDULA_YA_REGISTRADA),
            )
        }
        val cuenta = Cuenta_profesional(
            correo = correo,
            id_medico = idLocal(PREFIJO_MEDICO),
            huella_contrasena = HuellaDeContrasena.crear(contrasena),
            nombre = nombre,
            apellidos = apellidos,
            tratamiento = tratamiento,
            cedula_profesional = cedulaProfesional,
            estado_verificacion = EstadoVerificacionCedula.PENDIENTE.name,
        )
        with(cuenta) {
            consultas.insertarProfesional(
                correo, id_medico, huella_contrasena, nombre, apellidos, tratamiento,
                cedula_profesional, estado_verificacion,
            )
        }
        Result.success(cuenta.aSesion())
    }

    private fun Cuenta_profesional.aSesion() = SesionProfesional(
        idMedico = id_medico,
        token = "$PREFIJO_TOKEN$id_medico",
        nombre = nombre,
        apellidos = apellidos,
        tratamiento = tratamiento,
        // Un valor desconocido cae a PENDIENTE, nunca a APROBADO.
        estadoVerificacion = EstadoVerificacionCedula.entries.firstOrNull { it.name == estado_verificacion }
            ?: EstadoVerificacionCedula.PENDIENTE,
    )

    private companion object {
        const val PREFIJO_MEDICO = "doc_"
        const val PREFIJO_TOKEN = "jwt_local_"
    }
}
