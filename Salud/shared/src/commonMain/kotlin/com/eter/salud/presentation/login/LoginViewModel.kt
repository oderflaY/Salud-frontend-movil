package com.eter.salud.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eter.salud.domain.model.MotivoFalloAutenticacion
import com.eter.salud.domain.repository.AutenticacionRepositorio
import com.eter.salud.domain.repository.FalloAutenticacion
import com.eter.salud.presentation.comun.ejecutarSeguro
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel del acceso del paciente.
 *
 * Responsabilidades (MVVM estricto):
 *  - Sostener lo escrito en el formulario y su visibilidad.
 *  - Validar antes de gastar una llamada de red.
 *  - Traducir el fallo del backend a una clave que la Vista sepa mostrar.
 *
 * No conoce Compose, ni recursos, ni red: solo el contrato
 * [AutenticacionRepositorio].
 */
class LoginViewModel(
    private val repositorio: AutenticacionRepositorio,
) : ViewModel() {

    private val _estado = MutableStateFlow(LoginUiState())
    val estado: StateFlow<LoginUiState> = _estado.asStateFlow()

    fun actualizarCorreo(valor: String) = editar { it.copy(correo = valor) }

    fun actualizarContrasena(valor: String) = editar { it.copy(contrasena = valor) }

    fun alternarVisibilidadContrasena() {
        _estado.update { it.copy(contrasenaVisible = !it.contrasenaVisible) }
    }

    /** Retira el aviso de fallo para que el paciente pueda reintentar limpio. */
    fun descartarError() {
        _estado.update { it.copy(errorAutenticacion = null) }
    }

    /**
     * Valida y, solo si el formulario esta correcto, autentica. Un formulario
     * invalido no gasta red ni expone credenciales a medio escribir.
     */
    fun iniciarSesion() {
        val actual = _estado.value
        if (actual.autenticando || actual.sesion != null) return

        val errores = ValidadorLogin.validar(
            correo = actual.correo,
            contrasena = actual.contrasena,
        )
        if (errores.isNotEmpty()) {
            _estado.update { it.copy(erroresCampo = errores, errorAutenticacion = null) }
            return
        }

        _estado.update { it.copy(autenticando = true, errorAutenticacion = null) }
        viewModelScope.launch {
            val resultado = ejecutarSeguro {
                repositorio.iniciarSesion(
                    correo = ValidadorLogin.normalizarCorreo(actual.correo),
                    contrasena = actual.contrasena,
                )
            }
            _estado.update { previo ->
                resultado.fold(
                    onSuccess = { sesion ->
                        previo.copy(autenticando = false, sesion = sesion, contrasena = "")
                    },
                    onFailure = { fallo -> previo.conFallo(fallo.aErrorAutenticacion()) },
                )
            }
        }
    }

    /**
     * Tras unas credenciales incorrectas se vacia la contrasena: es el patron
     * habitual de un formulario de acceso y evita reintentos ciegos con el
     * mismo texto. Ante un fallo de red se conserva, porque lo escrito era
     * correcto y el paciente solo necesita volver a intentarlo.
     */
    private fun LoginUiState.conFallo(error: ErrorAutenticacion): LoginUiState = copy(
        autenticando = false,
        errorAutenticacion = error,
        contrasena = if (error == ErrorAutenticacion.CREDENCIALES_INVALIDAS) "" else contrasena,
    )

    /** Cualquier excepcion no tipada se trata como corte de red: nunca se propaga. */
    private fun Throwable.aErrorAutenticacion(): ErrorAutenticacion =
        when ((this as? FalloAutenticacion)?.motivo) {
            MotivoFalloAutenticacion.CREDENCIALES_INVALIDAS ->
                ErrorAutenticacion.CREDENCIALES_INVALIDAS

            MotivoFalloAutenticacion.CUENTA_BLOQUEADA -> ErrorAutenticacion.CUENTA_BLOQUEADA
            MotivoFalloAutenticacion.SIN_CONEXION, null -> ErrorAutenticacion.SIN_CONEXION
        }

    /** Punto unico de escritura: escribir siempre limpia el intento anterior. */
    private fun editar(bloque: (LoginUiState) -> LoginUiState) {
        _estado.update {
            bloque(it).copy(erroresCampo = emptyList(), errorAutenticacion = null)
        }
    }
}
