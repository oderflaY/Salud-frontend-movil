package com.eter.salud.data.repository

import com.eter.salud.data.demo.CuentaMedicaDemo

import com.eter.salud.domain.model.EstadoVerificacionCedula
import com.eter.salud.domain.model.MotivoFalloAutenticacionProfesional
import com.eter.salud.domain.model.MotivoFalloRegistroProfesional
import com.eter.salud.domain.model.SesionProfesional
import com.eter.salud.domain.repository.AutenticacionProfesionalRepositorio
import com.eter.salud.domain.repository.FalloAutenticacionProfesional
import com.eter.salud.domain.repository.FalloRegistroProfesional

/**
 * Implementacion temporal para desarrollo local: mantiene las cuentas de
 * personal medico en memoria. Se sustituira por el cliente HTTP contra el
 * backend en Go sin tocar los ViewModels.
 *
 * La cuenta de demostracion es la Dra. Elena Ruiz de DM_PerfilMedico.md, ya
 * verificada; cualquier alta nueva arranca `PENDIENTE` porque asi lo exige la
 * seccion 2 del documento (nadie se autoverifica).
 */
class AutenticacionProfesionalRepositorioEnMemoria(
    cuentaDemo: Pair<String, String> = CORREO_DEMO to CONTRASENA_DEMO,
    /** Otras cuentas medicas de demostracion, ya verificadas. */
    cuentasExtra: List<CuentaMedicaDemo> = emptyList(),
) : AutenticacionProfesionalRepositorio {

    private data class Cuenta(
        val contrasena: String,
        val idMedico: String,
        val nombre: String,
        val apellidos: String,
        val tratamiento: String,
        val cedulaProfesional: String,
        val estadoVerificacion: EstadoVerificacionCedula,
    )

    private val cuentas = mutableMapOf(
        cuentaDemo.first to Cuenta(
            contrasena = cuentaDemo.second,
            idMedico = ID_MEDICO_DEMO,
            nombre = "Elena",
            apellidos = "Ruiz Santos",
            tratamiento = "Dra.",
            cedulaProfesional = "12345678",
            estadoVerificacion = EstadoVerificacionCedula.APROBADO,
        ),
    ).apply {
        cuentasExtra.forEach { extra ->
            put(
                extra.correo,
                Cuenta(
                    contrasena = cuentaDemo.second,
                    idMedico = extra.idMedico,
                    nombre = extra.nombre,
                    apellidos = extra.apellidos,
                    tratamiento = extra.tratamiento,
                    cedulaProfesional = extra.cedula,
                    estadoVerificacion = EstadoVerificacionCedula.APROBADO,
                ),
            )
        }
    }

    private val cedulasRegistradas = mutableSetOf("12345678").apply { addAll(cuentasExtra.map { it.cedula }) }
    private var siguienteId = 1

    override suspend fun iniciarSesion(
        correo: String,
        contrasena: String,
    ): Result<SesionProfesional> {
        val cuenta = cuentas[correo]
        return if (cuenta != null && cuenta.contrasena == contrasena) {
            Result.success(cuenta.aSesion())
        } else {
            Result.failure(
                FalloAutenticacionProfesional(
                    MotivoFalloAutenticacionProfesional.CREDENCIALES_INVALIDAS,
                ),
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
    ): Result<SesionProfesional> {
        if (cuentas.containsKey(correo)) {
            return Result.failure(
                FalloRegistroProfesional(MotivoFalloRegistroProfesional.CORREO_YA_REGISTRADO),
            )
        }
        if (cedulaProfesional in cedulasRegistradas) {
            return Result.failure(
                FalloRegistroProfesional(MotivoFalloRegistroProfesional.CEDULA_YA_REGISTRADA),
            )
        }
        val cuenta = Cuenta(
            contrasena = contrasena,
            idMedico = "$PREFIJO_MEDICO${siguienteId++}",
            nombre = nombre,
            apellidos = apellidos,
            tratamiento = tratamiento,
            cedulaProfesional = cedulaProfesional,
            // Recien creada: falta que el sistema valide la cedula.
            estadoVerificacion = EstadoVerificacionCedula.PENDIENTE,
        )
        cuentas[correo] = cuenta
        cedulasRegistradas += cedulaProfesional
        return Result.success(cuenta.aSesion())
    }

    private fun Cuenta.aSesion() = SesionProfesional(
        idMedico = idMedico,
        token = "$PREFIJO_TOKEN$idMedico",
        nombre = nombre,
        apellidos = apellidos,
        tratamiento = tratamiento,
        estadoVerificacion = estadoVerificacion,
    )

    private companion object {
        const val CORREO_DEMO = "dr.elena.ruiz@hospital.com"
        const val CONTRASENA_DEMO = "salud12345"
        const val ID_MEDICO_DEMO = "doc_889900A"
        const val PREFIJO_MEDICO = "doc_local_"
        const val PREFIJO_TOKEN = "jwt_local_"
    }
}
