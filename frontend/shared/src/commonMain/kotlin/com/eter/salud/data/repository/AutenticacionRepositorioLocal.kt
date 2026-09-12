package com.eter.salud.data.repository

import com.eter.salud.data.db.BaseSalud
import com.eter.salud.data.db.Cuenta_paciente
import com.eter.salud.data.local.ContextoDeBase
import com.eter.salud.data.local.idLocal
import com.eter.salud.data.seguridad.HuellaDeContrasena
import com.eter.salud.domain.model.MotivoFalloAutenticacion
import com.eter.salud.domain.model.MotivoFalloRegistro
import com.eter.salud.domain.model.SesionPaciente
import com.eter.salud.domain.repository.AutenticacionRepositorio
import com.eter.salud.domain.repository.FalloAutenticacion
import com.eter.salud.domain.repository.FalloRegistro
import com.eter.salud.domain.time.RelojSalud
import kotlinx.coroutines.withContext

/**
 * Cuentas de paciente en la base SQLite del dispositivo.
 *
 * Sustituye a [AutenticacionRepositorioEnMemoria] en la app: con aquella, una
 * cuenta creada hoy desaparecia al cerrar la app y el paciente ya no podia
 * volver a entrar. Se sustituira por el cliente HTTP contra el backend en Go sin
 * tocar los ViewModels, que solo dependen de [AutenticacionRepositorio].
 */
class AutenticacionRepositorioLocal(
    private val base: BaseSalud,
    private val reloj: RelojSalud,
) : AutenticacionRepositorio {

    override suspend fun iniciarSesion(
        correo: String,
        contrasena: String,
    ): Result<SesionPaciente> = withContext(ContextoDeBase) {
        val cuenta = base.cuentasQueries.pacientePorCorreo(correo).executeAsOneOrNull()
        if (cuenta != null && HuellaDeContrasena.verificar(contrasena, cuenta.huella_contrasena)) {
            Result.success(cuenta.aSesion())
        } else {
            Result.failure(FalloAutenticacion(MotivoFalloAutenticacion.CREDENCIALES_INVALIDAS))
        }
    }

    override suspend fun crearCuenta(
        correo: String,
        contrasena: String,
    ): Result<SesionPaciente> = withContext(ContextoDeBase) {
        if (base.cuentasQueries.pacientePorCorreo(correo).executeAsOneOrNull() != null) {
            return@withContext Result.failure(FalloRegistro(MotivoFalloRegistro.CORREO_YA_REGISTRADO))
        }
        val idPaciente = idLocal(PREFIJO_PACIENTE)
        base.cuentasQueries.insertarPaciente(
            correo = correo,
            id_paciente = idPaciente,
            huella_contrasena = HuellaDeContrasena.crear(contrasena),
            // Toda cuenta nueva nace sin expediente: el acceso la manda a
            // capturarlo antes de dejarla entrar al panel.
            requiere_onboarding = 1L,
            creada_en = reloj.instanteActual(),
        )
        Result.success(SesionPaciente(idPaciente, tokenDe(idPaciente), requiereOnboarding = true))
    }

    private fun Cuenta_paciente.aSesion() = SesionPaciente(
        idPaciente = id_paciente,
        token = tokenDe(id_paciente),
        requiereOnboarding = requiere_onboarding == 1L,
    )

    private fun tokenDe(idPaciente: String) = "$PREFIJO_TOKEN$idPaciente"

    private companion object {
        const val PREFIJO_PACIENTE = "pac_"
        const val PREFIJO_TOKEN = "jwt_local_"
    }
}
