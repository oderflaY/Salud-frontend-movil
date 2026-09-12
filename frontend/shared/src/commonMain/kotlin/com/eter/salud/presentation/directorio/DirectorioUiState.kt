package com.eter.salud.presentation.directorio

import com.eter.salud.domain.model.PerfilDoctorDirectorio

/**
 * Estado explicito del Directorio Medico. Es una jerarquia sellada y no un
 * puñado de banderas sueltas para que la Vista no pueda olvidarse de ninguno
 * de los cuatro escenarios posibles: el compilador la obliga a resolver los
 * cuatro en su `when`.
 *
 * Cubrir [Vacio] por separado de [ConDoctores] importa: "el backend no
 * devolvio a nadie" y "hay doctores pero tu busqueda no encontro ninguno" son
 * mensajes distintos para el paciente, y mezclarlos deja una pantalla muda.
 */
sealed interface DirectorioUiState {

    /** Consulta en vuelo: la Vista pinta el indicador de carga. */
    data object Cargando : DirectorioUiState

    /** Al menos un doctor disponible para vincularse. */
    data class ConDoctores(val doctores: List<PerfilDoctorDirectorio>) : DirectorioUiState

    /** El backend respondio bien, pero no hay ningun doctor en el directorio. */
    data object Vacio : DirectorioUiState

    /** La consulta fallo. El motivo viaja tipado, nunca como texto. */
    data class Error(val motivo: ErrorDirectorio) : DirectorioUiState
}

/**
 * Motivos de fallo del directorio.
 *
 * Es un enum y no el `String` de un mensaje porque el ViewModel jamas produce
 * texto para el usuario: emite claves y la Vista las traduce contra
 * `strings.xml` (DM_Arquitectura_App.md, seccion 5: cero hardcoding).
 */
enum class ErrorDirectorio {
    SIN_CONEXION,
}
