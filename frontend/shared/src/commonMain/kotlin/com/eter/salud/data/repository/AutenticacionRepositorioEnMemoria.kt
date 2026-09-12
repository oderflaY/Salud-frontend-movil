package com.eter.salud.data.repository

import com.eter.salud.domain.model.MotivoFalloAutenticacion
import com.eter.salud.domain.model.MotivoFalloRegistro
import com.eter.salud.domain.model.SesionPaciente
import com.eter.salud.domain.repository.AutenticacionRepositorio
import com.eter.salud.domain.repository.FalloAutenticacion
import com.eter.salud.domain.repository.FalloRegistro

/**
 * Implementacion temporal para desarrollo local: mantiene las cuentas en
 * memoria. Se sustituira por el cliente HTTP contra el backend en Go sin tocar
 * los ViewModels, que solo dependen de [AutenticacionRepositorio].
 *
 * Las contrasenas se guardan en claro porque esto no persiste nada ni sale del
 * proceso; el hashing es responsabilidad del backend y jamas del cliente.
 */
class AutenticacionRepositorioEnMemoria(
    cuentaDemo: Pair<String, String> = CORREO_DEMO to CONTRASENA_DEMO,
    /**
     * Cuentas de pacientes de demostracion ya con su perfil completo:
     * correo -> (contrasena, idPaciente). Entran directo al panel, sin
     * cuestionario de emergencia.
     */
    cuentasCompletas: Map<String, Pair<String, String>> = emptyMap(),
) : AutenticacionRepositorio {

    private data class Cuenta(
        val contrasena: String,
        val idPaciente: String,
        val requiereOnboarding: Boolean,
    )

    private val cuentas = mutableMapOf(
        cuentaDemo.first to Cuenta(
            contrasena = cuentaDemo.second,
            idPaciente = ID_PACIENTE_DEMO,
            requiereOnboarding = true,
        ),
    ).apply {
        cuentasCompletas.forEach { (correo, datos) ->
            put(correo, Cuenta(contrasena = datos.first, idPaciente = datos.second, requiereOnboarding = false))
        }
    }

    private var siguienteId = 1

    override suspend fun iniciarSesion(
        correo: String,
        contrasena: String,
    ): Result<SesionPaciente> {
        val cuenta = cuentas[correo]
        return if (cuenta != null && cuenta.contrasena == contrasena) {
            Result.success(cuenta.aSesion())
        } else {
            Result.failure(
                FalloAutenticacion(MotivoFalloAutenticacion.CREDENCIALES_INVALIDAS),
            )
        }
    }

    override suspend fun crearCuenta(
        correo: String,
        contrasena: String,
    ): Result<SesionPaciente> {
        if (cuentas.containsKey(correo)) {
            return Result.failure(FalloRegistro(MotivoFalloRegistro.CORREO_YA_REGISTRADO))
        }
        val cuenta = Cuenta(
            contrasena = contrasena,
            idPaciente = "${PREFIJO_PACIENTE}${siguienteId++}",
            requiereOnboarding = true,
        )
        cuentas[correo] = cuenta
        return Result.success(cuenta.aSesion())
    }

    private fun Cuenta.aSesion() = SesionPaciente(
        idPaciente = idPaciente,
        token = "$PREFIJO_TOKEN$idPaciente",
        requiereOnboarding = requiereOnboarding,
    )

    private companion object {
        const val CORREO_DEMO = "paciente@salud.local"
        const val CONTRASENA_DEMO = "salud12345"
        const val ID_PACIENTE_DEMO = "pac_local_dev"
        const val PREFIJO_PACIENTE = "pac_local_"
        const val PREFIJO_TOKEN = "jwt_local_"
    }
}
