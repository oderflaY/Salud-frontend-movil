package com.eter.salud.presentation.registro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eter.salud.domain.model.MotivoFalloRegistro
import com.eter.salud.domain.repository.AutenticacionRepositorio
import com.eter.salud.domain.repository.FalloRegistro
import com.eter.salud.presentation.comun.ejecutarSeguro
import com.eter.salud.presentation.login.ValidadorLogin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel del alta de cuenta del paciente.
 *
 * Al crear la cuenta el backend devuelve la sesion ya abierta, asi que esta
 * pantalla desemboca directamente en la app y no obliga a volver al acceso.
 *
 * No conoce Compose, ni recursos, ni red: solo el contrato
 * [AutenticacionRepositorio].
 */
class RegistroViewModel(
    private val repositorio: AutenticacionRepositorio,
) : ViewModel() {

    private val _estado = MutableStateFlow(RegistroUiState())
    val estado: StateFlow<RegistroUiState> = _estado.asStateFlow()

    fun actualizarCorreo(valor: String) = editar { it.copy(correo = valor) }

    fun actualizarContrasena(valor: String) = editar { it.copy(contrasena = valor) }

    fun actualizarConfirmacion(valor: String) = editar { it.copy(confirmacion = valor) }

    fun aceptarAvisoPrivacidad(aceptado: Boolean) = editar { it.copy(avisoAceptado = aceptado) }

    /** Una sola preferencia para los dos campos: siempre se comparan a la vista. */
    fun alternarVisibilidadContrasena() {
        _estado.update { it.copy(contrasenaVisible = !it.contrasenaVisible) }
    }

    fun descartarError() {
        _estado.update { it.copy(errorRegistro = null) }
    }

    /** Valida y, solo si el formulario esta correcto, da de alta la cuenta. */
    fun crearCuenta() {
        val actual = _estado.value
        if (actual.creandoCuenta || actual.sesion != null) return

        val errores = ValidadorRegistro.validar(
            correo = actual.correo,
            contrasena = actual.contrasena,
            confirmacion = actual.confirmacion,
            avisoAceptado = actual.avisoAceptado,
        )
        if (errores.isNotEmpty()) {
            _estado.update { it.copy(erroresCampo = errores, errorRegistro = null) }
            return
        }

        _estado.update { it.copy(creandoCuenta = true, errorRegistro = null) }
        viewModelScope.launch {
            val resultado = ejecutarSeguro {
                repositorio.crearCuenta(
                    correo = ValidadorLogin.normalizarCorreo(actual.correo),
                    contrasena = actual.contrasena,
                )
            }
            _estado.update { previo ->
                resultado.fold(
                    onSuccess = { sesion ->
                        // Las contrasenas se vacian en cuanto dejan de hacer falta.
                        previo.copy(
                            creandoCuenta = false,
                            sesion = sesion,
                            contrasena = "",
                            confirmacion = "",
                        )
                    },
                    onFailure = { fallo ->
                        previo.copy(
                            creandoCuenta = false,
                            errorRegistro = fallo.aErrorRegistro(),
                        )
                    },
                )
            }
        }
    }

    /** Cualquier excepcion no tipada se trata como corte de red: nunca se propaga. */
    private fun Throwable.aErrorRegistro(): ErrorRegistro =
        when ((this as? FalloRegistro)?.motivo) {
            MotivoFalloRegistro.CORREO_YA_REGISTRADO -> ErrorRegistro.CORREO_YA_REGISTRADO
            MotivoFalloRegistro.SIN_CONEXION, null -> ErrorRegistro.SIN_CONEXION
        }

    /** Punto unico de escritura: escribir siempre limpia el intento anterior. */
    private fun editar(bloque: (RegistroUiState) -> RegistroUiState) {
        _estado.update {
            bloque(it).copy(erroresCampo = emptyList(), errorRegistro = null)
        }
    }
}
