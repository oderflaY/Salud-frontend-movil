package com.eter.salud.presentation.profesional

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eter.salud.domain.model.MotivoFalloAutenticacionProfesional
import com.eter.salud.domain.repository.AutenticacionProfesionalRepositorio
import com.eter.salud.domain.repository.FalloAutenticacionProfesional
import com.eter.salud.presentation.comun.ejecutarSeguro
import com.eter.salud.presentation.login.ValidadorLogin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel del acceso de personal medico.
 *
 * Es la unica puerta hacia el escaner de emergencia: sin una sesion valida
 * aqui, la app nunca instancia [com.eter.salud.presentation.emergencia.EscanerEmergenciaViewModel].
 * Mismo contrato de responsabilidades que el acceso de paciente (MVVM
 * estricto): sostiene el formulario, valida antes de gastar red, y traduce el
 * fallo del backend a una clave que la Vista sepa mostrar.
 */
class LoginProfesionalViewModel(
    private val repositorio: AutenticacionProfesionalRepositorio,
) : ViewModel() {

    private val _estado = MutableStateFlow(LoginProfesionalUiState())
    val estado: StateFlow<LoginProfesionalUiState> = _estado.asStateFlow()

    fun actualizarCorreo(valor: String) = editar { it.copy(correo = valor) }

    fun actualizarContrasena(valor: String) = editar { it.copy(contrasena = valor) }

    fun alternarVisibilidadContrasena() {
        _estado.update { it.copy(contrasenaVisible = !it.contrasenaVisible) }
    }

    fun descartarError() {
        _estado.update { it.copy(errorAutenticacion = null) }
    }

    fun iniciarSesion() {
        val actual = _estado.value
        if (actual.autenticando || actual.sesion != null) return

        val errores = ValidadorLoginProfesional.validar(
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

    private fun LoginProfesionalUiState.conFallo(
        error: ErrorAutenticacionProfesional,
    ): LoginProfesionalUiState = copy(
        autenticando = false,
        errorAutenticacion = error,
        contrasena = if (error == ErrorAutenticacionProfesional.CREDENCIALES_INVALIDAS) {
            ""
        } else {
            contrasena
        },
    )

    /** Cualquier excepcion no tipada se trata como corte de red: nunca se propaga. */
    private fun Throwable.aErrorAutenticacion(): ErrorAutenticacionProfesional =
        when ((this as? FalloAutenticacionProfesional)?.motivo) {
            MotivoFalloAutenticacionProfesional.CREDENCIALES_INVALIDAS ->
                ErrorAutenticacionProfesional.CREDENCIALES_INVALIDAS

            MotivoFalloAutenticacionProfesional.CUENTA_BLOQUEADA ->
                ErrorAutenticacionProfesional.CUENTA_BLOQUEADA

            MotivoFalloAutenticacionProfesional.SIN_CONEXION, null ->
                ErrorAutenticacionProfesional.SIN_CONEXION
        }

    private fun editar(bloque: (LoginProfesionalUiState) -> LoginProfesionalUiState) {
        _estado.update {
            bloque(it).copy(erroresCampo = emptyList(), errorAutenticacion = null)
        }
    }
}
