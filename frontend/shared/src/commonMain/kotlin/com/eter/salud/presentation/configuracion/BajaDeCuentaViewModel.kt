package com.eter.salud.presentation.configuracion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eter.salud.domain.repository.BajaDeCuentaRepositorio
import com.eter.salud.domain.repository.FalloBaja
import com.eter.salud.domain.repository.MotivoFalloBaja
import com.eter.salud.domain.repository.TipoDeCuenta
import com.eter.salud.presentation.comun.ejecutarSeguro
import com.eter.salud.presentation.login.ValidadorLogin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BajaDeCuentaUiState(
    /** El formulario solo se muestra tras pedirlo: no vive abierto en Ajustes. */
    val desplegada: Boolean = false,
    val correo: String = "",
    val contrasena: String = "",
    /** "Entiendo que es definitivo": sin marcarlo, el boton no se habilita. */
    val confirmada: Boolean = false,
    val enviando: Boolean = false,
    val error: MotivoFalloBaja? = null,
    /** La cuenta ya no existe: la Vista cierra la sesion al verlo. */
    val completada: Boolean = false,
) {
    val puedeEnviar: Boolean
        get() = confirmada && correo.isNotBlank() && contrasena.isNotEmpty() && !enviando && !completada
}

class BajaDeCuentaViewModel(
    private val repositorio: BajaDeCuentaRepositorio,
    /** Publico porque la Vista explica distinto que se borra a un medico que a un paciente. */
    val tipo: TipoDeCuenta,
) : ViewModel() {

    private val _estado = MutableStateFlow(BajaDeCuentaUiState())
    val estado: StateFlow<BajaDeCuentaUiState> = _estado.asStateFlow()

    fun desplegar() {
        _estado.update { it.copy(desplegada = true) }
    }

    /** Cancelar vuelve al estado inicial: la contrasena tecleada no se queda en memoria. */
    fun plegar() {
        _estado.update { if (it.enviando) it else BajaDeCuentaUiState() }
    }

    fun actualizarCorreo(valor: String) {
        _estado.update { it.copy(correo = valor, error = null) }
    }

    fun actualizarContrasena(valor: String) {
        _estado.update { it.copy(contrasena = valor, error = null) }
    }

    fun cambiarConfirmacion(valor: Boolean) {
        _estado.update { it.copy(confirmada = valor) }
    }

    fun darDeBaja() {
        val actual = _estado.value
        if (!actual.puedeEnviar) return

        _estado.update { it.copy(enviando = true, error = null) }
        viewModelScope.launch {
            val resultado = ejecutarSeguro {
                repositorio.darDeBaja(
                    tipo = tipo,
                    correo = ValidadorLogin.normalizarCorreo(actual.correo),
                    contrasena = actual.contrasena,
                )
            }
            _estado.update { previo ->
                resultado.fold(
                    onSuccess = { previo.copy(enviando = false, completada = true, contrasena = "") },
                    onFailure = { fallo ->
                        val motivo = (fallo as? FalloBaja)?.motivo ?: MotivoFalloBaja.SIN_CONEXION
                        previo.copy(
                            enviando = false,
                            error = motivo,
                            // Igual que en el acceso: con credenciales malas se
                            // limpia para no reenviar a ciegas la misma clave.
                            contrasena = if (motivo == MotivoFalloBaja.CREDENCIALES_INVALIDAS) "" else previo.contrasena,
                        )
                    },
                )
            }
        }
    }
}
