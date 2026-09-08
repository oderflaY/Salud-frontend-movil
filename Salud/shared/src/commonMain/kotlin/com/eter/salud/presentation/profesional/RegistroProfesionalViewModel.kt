package com.eter.salud.presentation.profesional

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eter.salud.domain.model.MotivoFalloRegistroProfesional
import com.eter.salud.domain.repository.AutenticacionProfesionalRepositorio
import com.eter.salud.domain.repository.FalloRegistroProfesional
import com.eter.salud.presentation.comun.ejecutarSeguro
import com.eter.salud.presentation.login.ValidadorLogin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel del alta de personal medico.
 *
 * A diferencia del paciente, la identidad y la Cedula Profesional se capturan
 * en la misma alta: DM_PerfilMedico.md liga la identidad legal del profesional
 * a su credencial de acceso desde el primer momento, no en una fase separada.
 *
 * El backend devuelve la sesion ya abierta pero con la cedula en estado
 * `PENDIENTE`: la cuenta funciona de inmediato, la verificacion llega despues.
 */
class RegistroProfesionalViewModel(
    private val repositorio: AutenticacionProfesionalRepositorio,
) : ViewModel() {

    private val _estado = MutableStateFlow(RegistroProfesionalUiState())
    val estado: StateFlow<RegistroProfesionalUiState> = _estado.asStateFlow()

    fun actualizarCorreo(valor: String) = editar { it.copy(correo = valor) }

    fun actualizarContrasena(valor: String) = editar { it.copy(contrasena = valor) }

    fun actualizarConfirmacion(valor: String) = editar { it.copy(confirmacion = valor) }

    fun actualizarNombre(valor: String) = editar { it.copy(nombre = valor) }

    fun actualizarApellidos(valor: String) = editar { it.copy(apellidos = valor) }

    fun actualizarTratamiento(valor: String) = editar { it.copy(tratamiento = valor) }

    fun actualizarCedulaProfesional(valor: String) = editar { it.copy(cedulaProfesional = valor) }

    fun aceptarAvisoPrivacidad(aceptado: Boolean) = editar { it.copy(avisoAceptado = aceptado) }

    fun alternarVisibilidadContrasena() {
        _estado.update { it.copy(contrasenaVisible = !it.contrasenaVisible) }
    }

    fun descartarError() {
        _estado.update { it.copy(errorRegistro = null) }
    }

    fun crearCuenta() {
        val actual = _estado.value
        if (actual.creandoCuenta || actual.sesion != null) return

        val errores = ValidadorRegistroProfesional.validar(
            correo = actual.correo,
            contrasena = actual.contrasena,
            confirmacion = actual.confirmacion,
            nombre = actual.nombre,
            apellidos = actual.apellidos,
            tratamiento = actual.tratamiento,
            cedulaProfesional = actual.cedulaProfesional,
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
                    nombre = actual.nombre.trim(),
                    apellidos = actual.apellidos.trim(),
                    tratamiento = actual.tratamiento,
                    cedulaProfesional = actual.cedulaProfesional.trim(),
                )
            }
            _estado.update { previo ->
                resultado.fold(
                    onSuccess = { sesion ->
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

    private fun Throwable.aErrorRegistro(): ErrorRegistroProfesional =
        when ((this as? FalloRegistroProfesional)?.motivo) {
            MotivoFalloRegistroProfesional.CORREO_YA_REGISTRADO ->
                ErrorRegistroProfesional.CORREO_YA_REGISTRADO

            MotivoFalloRegistroProfesional.CEDULA_YA_REGISTRADA ->
                ErrorRegistroProfesional.CEDULA_YA_REGISTRADA

            MotivoFalloRegistroProfesional.SIN_CONEXION, null ->
                ErrorRegistroProfesional.SIN_CONEXION
        }

    private fun editar(
        bloque: (RegistroProfesionalUiState) -> RegistroProfesionalUiState,
    ) {
        _estado.update {
            bloque(it).copy(erroresCampo = emptyList(), errorRegistro = null)
        }
    }
}
