package com.eter.salud.presentation.directorio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eter.salud.domain.model.Especialidad
import com.eter.salud.domain.model.PerfilDoctorDirectorio
import com.eter.salud.domain.repository.DirectorioMedicoRepositorio
import com.eter.salud.presentation.comun.ejecutarSeguro
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel de la seccion "Mi Medico".
 *
 * Es el unico responsable de decidir si el paciente ve el Directorio Medico o
 * el chat: consulta primero si ya existe una vinculacion y, solo si no hay
 * ninguna, trae el directorio. Una vez vinculado, deja de gastar red en
 * busquedas de doctores.
 *
 * Expone un unico [StateFlow] con el estado de la pantalla; el estado de la
 * lista viaja dentro como [DirectorioUiState] sellado, de modo que la Vista
 * este obligada a resolver carga, exito, vacio y error.
 *
 * No conoce Compose, ni recursos, ni el modulo de chat: solo el contrato
 * [DirectorioMedicoRepositorio]. El chat vive en su propio
 * [com.eter.salud.presentation.chat.ChatViewModel], instanciado por la Vista
 * en cuanto aqui aparece un [com.eter.salud.domain.model.MedicoVinculado].
 */
class DescubrimientoMedicoViewModel(
    private val repositorio: DirectorioMedicoRepositorio,
    private val idPaciente: String,
) : ViewModel() {

    private val _estado = MutableStateFlow(DescubrimientoMedicoUiState(idPaciente = idPaciente))
    val estado: StateFlow<DescubrimientoMedicoUiState> = _estado.asStateFlow()

    /**
     * Guardia de reentrada propio y no `directorio == Cargando`: `Cargando` es
     * tambien el estado inicial, asi que mirarlo bloquearia la primera carga.
     */
    private var consultaEnCurso = false

    init {
        // La carga se ata al ciclo de vida del ViewModel, no a un
        // `LaunchedEffect` de la Vista: si el ViewModel se recreara, el efecto
        // no volveria a dispararse (su clave no cambia) y la pantalla se
        // quedaria vacia para siempre. Aqui eso no puede pasar.
        cargar()
    }

    fun cargar() {
        if (consultaEnCurso) return
        consultaEnCurso = true
        _estado.update { it.copy(directorio = DirectorioUiState.Cargando) }
        viewModelScope.launch {
            try {
                val resultadoVinculo = ejecutarSeguro { repositorio.obtenerMedicoVinculado(idPaciente) }
                val vinculado = resultadoVinculo.getOrNull()
                when {
                    resultadoVinculo.isFailure -> publicarError()

                    vinculado != null -> _estado.update {
                        // Ya hay con quien chatear: no se gasta red en el directorio.
                        it.copy(medicoVinculado = vinculado)
                    }

                    else -> consultarDirectorio(especialidad = null)
                }
            } finally {
                consultaEnCurso = false
            }
        }
    }

    fun actualizarTextoBusqueda(valor: String) {
        _estado.update { it.copy(textoBusqueda = valor) }
    }

    /** `null` significa "todas las especialidades". */
    fun filtrarPorEspecialidad(especialidad: Especialidad?) {
        if (_estado.value.especialidadFiltro == especialidad) return
        _estado.update {
            it.copy(especialidadFiltro = especialidad, directorio = DirectorioUiState.Cargando)
        }
        viewModelScope.launch { consultarDirectorio(especialidad) }
    }

    fun solicitarVinculacion(idMedico: String) {
        val actual = _estado.value
        if (actual.idSolicitandoVinculacion != null || actual.medicoVinculado != null) return
        _estado.update { it.copy(idSolicitandoVinculacion = idMedico, errorVinculacion = false) }
        viewModelScope.launch {
            val resultado = ejecutarSeguro { repositorio.solicitarVinculacion(idPaciente, idMedico) }
            _estado.update { previo ->
                resultado.fold(
                    onSuccess = { vinculado ->
                        previo.copy(idSolicitandoVinculacion = null, medicoVinculado = vinculado)
                    },
                    onFailure = {
                        previo.copy(idSolicitandoVinculacion = null, errorVinculacion = true)
                    },
                )
            }
        }
    }

    fun descartarErrorVinculacion() {
        _estado.update { it.copy(errorVinculacion = false) }
    }

    private suspend fun consultarDirectorio(especialidad: Especialidad?) {
        val resultado = ejecutarSeguro { repositorio.buscarDirectorio(especialidad) }
        resultado.fold(
            onSuccess = { doctores -> publicarDoctores(doctores) },
            onFailure = { publicarError() },
        )
    }

    private fun publicarDoctores(doctores: List<PerfilDoctorDirectorio>) {
        val nuevoEstado = if (doctores.isEmpty()) {
            DirectorioUiState.Vacio
        } else {
            DirectorioUiState.ConDoctores(doctores)
        }
        _estado.update { it.copy(directorio = nuevoEstado) }
    }

    private fun publicarError() {
        _estado.update {
            it.copy(directorio = DirectorioUiState.Error(ErrorDirectorio.SIN_CONEXION))
        }
    }
}
