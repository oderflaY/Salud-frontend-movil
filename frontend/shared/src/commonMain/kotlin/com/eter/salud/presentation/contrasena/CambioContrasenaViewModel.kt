package com.eter.salud.presentation.contrasena

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eter.salud.domain.model.SesionPaciente
import com.eter.salud.domain.repository.CambioDeContrasenaRepositorio
import com.eter.salud.domain.repository.FalloCambioContrasena
import com.eter.salud.domain.repository.MotivoFalloCambioContrasena
import com.eter.salud.presentation.comun.ejecutarSeguro
import com.eter.salud.presentation.login.ValidadorLogin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ErrorCampoContrasena {
    ACTUAL_VACIA,
    NUEVA_CORTA,
    NO_COINCIDEN,
    IGUAL_A_LA_ACTUAL,
}

data class CambioContrasenaUiState(
    /** Tras un reset del medico. Sin campo de contrasena actual y sin poder plegarse. */
    val obligatorio: Boolean = false,
    /** En Ajustes el formulario solo se abre al pedirlo; el obligatorio siempre esta abierto. */
    val desplegado: Boolean = obligatorio,
    val actual: String = "",
    val nueva: String = "",
    val confirmacion: String = "",
    val visible: Boolean = false,
    val erroresCampo: List<ErrorCampoContrasena> = emptyList(),
    val error: MotivoFalloCambioContrasena? = null,
    val enviando: Boolean = false,
    /** Sesion con el token nuevo, pendiente de entregar a la app. */
    val sesionNueva: SesionPaciente? = null,
    /** En Ajustes: "Contrasena actualizada", hasta que se vuelva a abrir el formulario. */
    val completado: Boolean = false,
) {
    val puedeEnviar: Boolean
        get() = nueva.isNotEmpty() && confirmacion.isNotEmpty() &&
            (obligatorio || actual.isNotEmpty()) && !enviando && sesionNueva == null
}

object ValidadorCambioContrasena {
    fun validar(estado: CambioContrasenaUiState): List<ErrorCampoContrasena> = buildList {
        if (!estado.obligatorio && estado.actual.isEmpty()) add(ErrorCampoContrasena.ACTUAL_VACIA)
        if (estado.nueva.length < ValidadorLogin.MINIMO_CARACTERES_CONTRASENA) {
            add(ErrorCampoContrasena.NUEVA_CORTA)
        } else if (!estado.obligatorio && estado.nueva == estado.actual) {
            add(ErrorCampoContrasena.IGUAL_A_LA_ACTUAL)
        }
        if (estado.confirmacion != estado.nueva) add(ErrorCampoContrasena.NO_COINCIDEN)
    }
}

/**
 * Cambio de contrasena del paciente.
 *
 * El OBLIGATORIO llega despues de entrar con la contrasena temporal que le dio
 * su medico al resetear la cuenta: el backend no le deja hacer nada mas hasta
 * elegir una nueva. El VOLUNTARIO vive plegado en Ajustes y pide la actual.
 */
class CambioContrasenaViewModel(
    private val repositorio: CambioDeContrasenaRepositorio,
    obligatorio: Boolean,
) : ViewModel() {

    private val _estado = MutableStateFlow(CambioContrasenaUiState(obligatorio = obligatorio))
    val estado: StateFlow<CambioContrasenaUiState> = _estado.asStateFlow()

    fun desplegar() {
        _estado.update { it.copy(desplegado = true, completado = false) }
    }

    /** Cancelar borra lo tecleado: ninguna contrasena se queda en memoria. */
    fun plegar() {
        _estado.update { if (it.obligatorio || it.enviando) it else CambioContrasenaUiState() }
    }

    fun actualizarActual(valor: String) = editar { it.copy(actual = valor) }

    fun actualizarNueva(valor: String) = editar { it.copy(nueva = valor) }

    fun actualizarConfirmacion(valor: String) = editar { it.copy(confirmacion = valor) }

    fun alternarVisibilidad() {
        _estado.update { it.copy(visible = !it.visible) }
    }

    fun guardar() {
        val actual = _estado.value
        if (!actual.puedeEnviar) return

        val errores = ValidadorCambioContrasena.validar(actual)
        if (errores.isNotEmpty()) {
            _estado.update { it.copy(erroresCampo = errores, error = null) }
            return
        }

        _estado.update { it.copy(enviando = true, error = null) }
        viewModelScope.launch {
            val resultado = ejecutarSeguro {
                repositorio.cambiar(
                    contrasenaActual = if (actual.obligatorio) null else actual.actual,
                    contrasenaNueva = actual.nueva,
                )
            }
            _estado.update { previo ->
                resultado.fold(
                    onSuccess = { sesion ->
                        previo.copy(enviando = false, sesionNueva = sesion, actual = "", nueva = "", confirmacion = "")
                    },
                    onFailure = { fallo ->
                        val motivo = (fallo as? FalloCambioContrasena)?.motivo ?: MotivoFalloCambioContrasena.SIN_CONEXION
                        previo.copy(
                            enviando = false,
                            error = motivo,
                            // Con la actual mal escrita se limpia, como en el acceso.
                            actual = if (motivo == MotivoFalloCambioContrasena.CREDENCIALES_INVALIDAS) "" else previo.actual,
                        )
                    },
                )
            }
        }
    }

    /** La app ya guardo la sesion nueva. En Ajustes, el formulario se pliega con el aviso de exito. */
    fun sesionEntregada() {
        _estado.update {
            if (it.obligatorio) it.copy(sesionNueva = null) else CambioContrasenaUiState(completado = true)
        }
    }

    /** Escribir borra el aviso anterior: el paciente reintenta limpio. */
    private fun editar(bloque: (CambioContrasenaUiState) -> CambioContrasenaUiState) {
        _estado.update { bloque(it).copy(erroresCampo = emptyList(), error = null, completado = false) }
    }
}
