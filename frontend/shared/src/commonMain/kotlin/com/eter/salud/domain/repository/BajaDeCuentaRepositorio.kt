package com.eter.salud.domain.repository

/** Cada tipo de cuenta tiene su propia ruta de baja en el backend. */
enum class TipoDeCuenta { PACIENTE, PROFESIONAL }

enum class MotivoFalloBaja {
    CREDENCIALES_INVALIDAS,
    SIN_CONEXION,
}

class FalloBaja(val motivo: MotivoFalloBaja) : Exception(motivo.name)

/**
 * Baja definitiva de la cuenta, que Google Play exige a toda app con registro.
 *
 * Que se borra y que se conserva lo decide el backend
 * (`db/migrations/0012_baja_de_cuentas.sql`): la app solo prueba quien es.
 *
 * Pide correo y contrasena otra vez aunque haya una sesion abierta: es la
 * accion mas destructiva de la app, y no debe poder dispararla cualquiera que
 * encuentre el telefono desbloqueado.
 *
 * Contrato aparte de [AutenticacionRepositorio] y
 * [AutenticacionProfesionalRepositorio] a proposito: solo existe contra el
 * backend. Con la app en modo local no hay cuenta en ningun servidor que borrar,
 * y la opcion simplemente no se ofrece.
 */
interface BajaDeCuentaRepositorio {
    suspend fun darDeBaja(tipo: TipoDeCuenta, correo: String, contrasena: String): Result<Unit>
}
