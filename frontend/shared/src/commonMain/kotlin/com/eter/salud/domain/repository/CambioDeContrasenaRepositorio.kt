package com.eter.salud.domain.repository

import com.eter.salud.domain.model.SesionPaciente

/** Los nombres coinciden con el `message` del backend, que es como se leen. */
enum class MotivoFalloCambioContrasena {
    /** La contrasena actual no es la correcta. */
    CREDENCIALES_INVALIDAS,
    /** Menos de 8 caracteres, o igual a la que ya tenia. */
    CONTRASENA_NO_VALIDA,
    /** La temporal del medico paso de su plazo: hay que pedirle otra. */
    CONTRASENA_TEMPORAL_VENCIDA,
    /** La sesion se cerro (otro reset, o la cuenta se dio de baja). */
    SESION_REVOCADA,
    CUENTA_INACTIVA,
    SIN_CONEXION,
}

class FalloCambioContrasena(val motivo: MotivoFalloCambioContrasena) : Exception(motivo.name)

/**
 * Cambio de contrasena del paciente. Dos usos:
 *
 *  - OBLIGATORIO, justo despues de entrar con la contrasena temporal que le dio
 *    su medico (reset desde el panel web). `contrasenaActual` va en null: la
 *    sesion se abrio con esa temporal hace un momento.
 *  - VOLUNTARIO, desde Ajustes. Ahi `contrasenaActual` es obligatoria: un
 *    telefono desbloqueado no debe bastar para quedarse con la cuenta.
 *
 * Devuelve la sesion con un token nuevo: el backend cierra las demas sesiones
 * de la cuenta al cambiarla, incluida la que hizo la peticion.
 *
 * Solo existe contra el backend, como [BajaDeCuentaRepositorio].
 */
interface CambioDeContrasenaRepositorio {
    suspend fun cambiar(contrasenaActual: String?, contrasenaNueva: String): Result<SesionPaciente>
}
