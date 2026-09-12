package com.eter.salud.presentation.directorio

import com.eter.salud.domain.model.Especialidad
import com.eter.salud.domain.model.MedicoVinculado
import com.eter.salud.domain.model.PerfilDoctorDirectorio

/**
 * Estado unico de la seccion "Mi Medico". La Vista solo pinta esto
 * (DM_Arquitectura_App.md, seccion 2: la Vista es pasiva).
 *
 * [debeMostrarDirectorio] decide toda la pantalla: sin un doctor vinculado se
 * ve el Directorio Medico, y con uno se ve su ficha clinica con la accion de
 * iniciar la consulta. Nunca se llega a un chat vacio sin haber pasado por una
 * vinculacion real.
 *
 * El estado de la lista vive aparte, en [directorio], como jerarquia sellada:
 * carga, exito, vacio y error son cuatro casos que la Vista esta obligada a
 * resolver, no banderas booleanas que se puedan quedar sin pintar.
 */
data class DescubrimientoMedicoUiState(
    val idPaciente: String,
    val directorio: DirectorioUiState = DirectorioUiState.Cargando,
    /**
     * El primer medico con el que el paciente ya esta vinculado. Lo usa la barra
     * inferior para contar mensajes sin leer; NO decide que muestra el
     * directorio.
     */
    val medicoVinculado: MedicoVinculado? = null,
    /**
     * El medico que el paciente acaba de escoger en el directorio. Mientras no
     * sea nulo la pantalla muestra su ficha con "Iniciar consulta"; al cerrarla
     * vuelve la lista.
     */
    val medicoElegido: MedicoVinculado? = null,
    /**
     * Ficha publica del medico elegido: cedula, universidad y disponibilidad.
     *
     * Puede ser nula aun habiendo vinculacion, y la Vista tiene que aguantarlo:
     * significa que el doctor ya no figura en el directorio. En ese caso se
     * muestra lo que la vinculacion si garantiza (nombre y especialidad) en vez
     * de inventar unas credenciales que nadie ha verificado.
     */
    val perfilDelElegido: PerfilDoctorDirectorio? = null,
    val especialidadFiltro: Especialidad? = null,
    val textoBusqueda: String = "",
    /** idMedico de la solicitud en curso; la Vista desactiva ese boton mientras tanto. */
    val idSolicitandoVinculacion: String? = null,
    val errorVinculacion: Boolean = false,
) {
    /**
     * El directorio se muestra aunque el paciente ya tenga medico: un paciente
     * real acumula especialistas, y a esta pantalla se llega justamente desde
     * "buscar un especialista nuevo" en la bandeja.
     */
    val debeMostrarDirectorio: Boolean get() = medicoElegido == null

    /**
     * Doctores de [directorio] tras aplicar la busqueda por texto. El filtro se
     * resuelve aqui y no en el repositorio: no vale la pena una llamada de red
     * por cada tecla que el paciente escribe en la barra de busqueda.
     *
     * Devuelve vacio cuando el directorio aun no trae lista (carga, vacio o
     * error), de modo que la Vista solo la use dentro de `ConDoctores`.
     */
    val doctoresFiltrados: List<PerfilDoctorDirectorio>
        get() {
            val disponibles = (directorio as? DirectorioUiState.ConDoctores)?.doctores
                ?: return emptyList()
            val texto = textoBusqueda.trim()
            if (texto.isBlank()) return disponibles
            return disponibles.filter { it.nombreCompleto.contains(texto, ignoreCase = true) }
        }
}
