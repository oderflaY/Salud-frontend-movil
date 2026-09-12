package com.eter.salud.presentation.sesion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eter.salud.data.sesion.FuenteDeSesion
import com.eter.salud.domain.model.SesionPaciente
import com.eter.salud.domain.model.SesionProfesional
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Quien esta dentro de la app, y con que papel.
 *
 * Estado de la sesion abierta. Solo uno de los dos puede estar vivo a la vez:
 * los dos portales tienen datos clinicos distintos y mezclarlos seria un fallo
 * de seguridad, no una comodidad.
 */
data class SesionUiState(
    val paciente: SesionPaciente? = null,
    val profesional: SesionProfesional? = null,
    /**
     * Todavia se esta leyendo el disco.
     *
     * La pantalla NO debe decidir nada mientras esto sea cierto. Sin esta
     * bandera, la app pintaria el acceso durante los milisegundos que tarda la
     * lectura y saltaria al panel de golpe: un parpadeo que hace dudar de si la
     * sesion se habia perdido.
     */
    val restaurando: Boolean = true,
) {
    val hayAlguienDentro: Boolean get() = paciente != null || profesional != null
}

/**
 * Unico punto de verdad de la sesion.
 *
 * ## Por que un ViewModel y no un `remember` de Compose
 *
 * Antes la sesion vivia en dos `remember` dentro de `App()`. Un `remember` NO
 * sobrevive a que el sistema recree la Activity, y Android la recrea al girar el
 * telefono, al plegarlo, al cambiar el tamano de fuente o al volver de una
 * llamada. En todos esos casos el paciente aparecia de golpe en la pantalla de
 * acceso -- a veces en mitad del cuestionario clinico, perdiendo lo capturado.
 *
 * Un `ViewModel` sobrevive a la recreacion de la Activity, que es exactamente
 * la clase de perdida que se reportaba.
 *
 * ## Y tambien a que muera el proceso
 *
 * Un ViewModel no sobrevive a que el sistema MATE la app por falta de memoria,
 * ni a cerrarla del todo. Por eso la sesion se escribe ademas en disco a traves
 * de [AlmacenDeSesion]: al arrancar se lee de ahi antes de decidir que pantalla
 * mostrar, y el paciente encuentra la app donde la dejo.
 *
 * La memoria es la fuente de verdad DURANTE la ejecucion y el disco es la copia
 * duradera. Escuchar el disco en vivo seria peor: un fallo de escritura o una
 * lectura tardia podria expulsar a alguien que ya esta dentro.
 */
class SesionViewModel(
    private val almacen: FuenteDeSesion? = null,
) : ViewModel() {

    private val _estado = MutableStateFlow(SesionUiState())
    val estado: StateFlow<SesionUiState> = _estado.asStateFlow()

    init {
        viewModelScope.launch {
            // Solo el PRIMER valor: a partir de ahi manda la memoria. `collect`
            // dejaria el disco pisando la sesion viva en cada escritura.
            val guardada = almacen?.sesion?.first() ?: SesionUiState()
            _estado.value = guardada.copy(restaurando = false)
        }
    }

    /** Abre sesion de paciente. Cierra cualquier sesion profesional que hubiera. */
    fun abrirComoPaciente(sesion: SesionPaciente) {
        _estado.value = SesionUiState(paciente = sesion, restaurando = false)
        viewModelScope.launch { almacen?.guardarPaciente(sesion) }
    }

    /** Abre sesion de personal medico. Cierra cualquier sesion de paciente. */
    fun abrirComoProfesional(sesion: SesionProfesional) {
        _estado.value = SesionUiState(profesional = sesion, restaurando = false)
        viewModelScope.launch { almacen?.guardarProfesional(sesion) }
    }

    /**
     * Cierra la sesion.
     *
     * Deja el estado ENTERO en blanco, no solo el papel activo: es lo que
     * garantiza que despues de salir no quede ningun dato de la sesion anterior
     * al alcance de la siguiente persona que abra la app.
     */
    fun cerrar() {
        _estado.value = SesionUiState(restaurando = false)
        viewModelScope.launch { almacen?.borrar() }
    }
}
