package com.eter.salud.presentation.agendapaciente

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eter.salud.domain.model.Cita
import com.eter.salud.domain.model.EstadoToma
import com.eter.salud.domain.model.TomaDelDia
import com.eter.salud.domain.repository.AdherenciaRepositorio
import com.eter.salud.domain.repository.CitasRepositorio
import com.eter.salud.domain.repository.DirectorioMedicoRepositorio
import com.eter.salud.domain.time.CalendarioSalud
import com.eter.salud.domain.time.RelojSalud
import com.eter.salud.domain.time.relojDelSistema
import com.eter.salud.presentation.comun.ejecutarSeguro
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Agenda del paciente: sus medicinas y sus citas, dia por dia.
 *
 * Cruza tres fuentes: el seguimiento de tomas (que medicinas tocan y si se
 * tomaron), el directorio (con que medicos esta vinculado) y la agenda de cada
 * uno de esos medicos (de donde salen SUS citas). El cruce es logica de
 * presentacion, asi que vive aqui y no en un repositorio.
 *
 * ## Dos semanas a la vista
 *
 * El calendario muestra catorce dias: la semana en curso y la siguiente. Una
 * sola semana escondia justo lo que mas se pregunta ("la cita del martes que
 * viene"), y obligaba a ir y volver para verlo.
 *
 * Las citas se leen al abrir y se reparten por dia en memoria: moverse por el
 * calendario no debe costar una lectura por toque, y un paciente mayor que
 * pulsa "siguiente" tres veces seguidas no tiene por que esperar tres cargas.
 * [refrescar] las vuelve a leer cuando la pantalla reaparece, para que una
 * cita recien pedida en el chat ya este al volver a la agenda.
 */
class AgendaPacienteViewModel(
    private val adherencia: AdherenciaRepositorio,
    private val citas: CitasRepositorio,
    private val directorio: DirectorioMedicoRepositorio,
    private val idPaciente: String,
    private val reloj: RelojSalud = relojDelSistema(),
) : ViewModel() {

    private val _estado = MutableStateFlow(AgendaPacienteUiState())
    val estado: StateFlow<AgendaPacienteUiState> = _estado.asStateFlow()

    /** Todas las citas del paciente, de todos sus medicos. */
    private var citasDelPaciente: List<Cita> = emptyList()

    private var cargaDelDia: Job? = null

    init {
        cargar()
    }

    /** Carga inicial (o reintento): citas, las dos semanas de hoy y el dia de hoy. */
    fun cargar() {
        val hoy = reloj.fechaHoy()
        _estado.update { it.copy(hoy = hoy, cargando = true, errorCarga = false) }
        viewModelScope.launch {
            releerCitas()
            mostrarVentana(lunesDe(hoy), seleccion = hoy)
        }
    }

    /**
     * Relee las citas sin mover lo que el paciente estaba mirando. No hace nada
     * mientras la carga inicial siga en curso: seria leer lo mismo dos veces.
     */
    fun refrescar() {
        val actual = _estado.value
        if (actual.cargando || actual.lunes.isEmpty()) return
        viewModelScope.launch {
            releerCitas()
            mostrarVentana(actual.lunes, seleccion = actual.fechaSeleccionada)
        }
    }

    /**
     * Acepta una cita que el medico propuso: pasa a CONFIRMADA. La respuesta se
     * relee entera con [releerCitas] en vez de aplicar el cambio a mano sobre
     * [citasDelPaciente] -- son solo dos pulsaciones al dia y correr la misma
     * ruta de lectura que ya usa [refrescar] es mas simple que mantener una
     * segunda forma de actualizar el mismo estado.
     */
    fun aceptarPropuesta(cita: Cita) = responderPropuesta(cita) { citas.aceptarPropuesta(it) }

    /** Rechaza una cita que el medico propuso: pasa a CANCELADA y libera la franja. */
    fun rechazarPropuesta(cita: Cita) = responderPropuesta(cita) { citas.rechazarPropuesta(it) }

    private fun responderPropuesta(cita: Cita, accion: suspend (String) -> Result<Cita>) {
        _estado.update { it.copy(errorRespuestaPropuesta = false) }
        viewModelScope.launch {
            val resultado = ejecutarSeguro { accion(cita.idCita) }
            if (resultado.isFailure) {
                _estado.update { it.copy(errorRespuestaPropuesta = true) }
                return@launch
            }
            releerCitas()
            cargarDia(_estado.value.fechaSeleccionada)
        }
    }

    fun descartarErrorRespuestaPropuesta() {
        _estado.update { it.copy(errorRespuestaPropuesta = false) }
    }

    fun periodoAnterior() = moverVentana(-DIAS_VENTANA)

    fun periodoSiguiente() = moverVentana(DIAS_VENTANA)

    fun irAHoy() {
        val hoy = _estado.value.hoy
        viewModelScope.launch { mostrarVentana(lunesDe(hoy), seleccion = hoy) }
    }

    /**
     * Elige un dia y trae lo que pasa en el. Si el dia cae fuera de la ventana
     * (una cita de la lista de proximas que esta mas alla del domingo visible),
     * el calendario se mueve hasta el.
     */
    fun elegirDia(fecha: String) {
        val actual = _estado.value
        if (fecha == actual.fechaSeleccionada) return
        if (actual.dias.none { it.fecha == fecha }) {
            viewModelScope.launch { mostrarVentana(lunesDe(fecha), seleccion = fecha) }
            return
        }
        _estado.update { it.copy(fechaSeleccionada = fecha) }
        cargarDia(fecha)
    }

    private fun moverVentana(dias: Int) {
        val actual = _estado.value
        val lunesNuevo = CalendarioSalud.sumarDias(actual.lunes, dias)
        viewModelScope.launch {
            // Se conserva la misma posicion en la ventana: quien miraba el jueves
            // de la primera semana sigue mirando ese jueves en la ventana nueva.
            // Si la ventana contiene hoy, gana hoy, que es lo que se busca.
            val desplazamiento = CalendarioSalud.diasEntre(actual.lunes, actual.fechaSeleccionada)
                ?.coerceIn(0, DIAS_VENTANA - 1) ?: 0
            val candidato = CalendarioSalud.sumarDias(lunesNuevo, desplazamiento)
            val hoy = actual.hoy
            val incluyeHoy = hoy >= lunesNuevo && hoy <= CalendarioSalud.sumarDias(lunesNuevo, DIAS_VENTANA - 1)
            mostrarVentana(lunesNuevo, seleccion = if (incluyeHoy) hoy else candidato)
        }
    }

    private suspend fun releerCitas() {
        val leidas = leerCitasDelPaciente()
        citasDelPaciente = leidas.getOrNull().orEmpty()
        val hoy = reloj.fechaHoy()
        val limite = CalendarioSalud.sumarDias(hoy, DIAS_VENTANA - 1)
        _estado.update {
            it.copy(
                citasNoDisponibles = leidas.isFailure,
                proximasCitas = citasDelPaciente
                    .filter { cita -> cita.fecha in hoy..limite && cita.estado.ocupaLaFranja }
                    .sortedBy { cita -> cita.claveOrden },
            )
        }
    }

    private suspend fun mostrarVentana(lunes: String, seleccion: String) {
        val hoy = _estado.value.hoy
        // El servicio responde por semanas: una peticion por cada domingo.
        val cumplimiento = (1..SEMANAS_VENTANA).flatMap { semana ->
            val domingo = CalendarioSalud.sumarDias(lunes, semana * DIAS_SEMANA - 1)
            ejecutarSeguro { adherencia.obtenerSemana(idPaciente, domingo) }.getOrNull().orEmpty()
        }.associateBy { it.fecha }

        val dias = (0 until DIAS_VENTANA).map { indice ->
            val fecha = CalendarioSalud.sumarDias(lunes, indice)
            DiaDelCalendario(
                fecha = fecha,
                esHoy = fecha == hoy,
                esFuturo = fecha > hoy,
                adherencia = cumplimiento[fecha],
                citas = citasDelPaciente.count { it.fecha == fecha },
            )
        }
        _estado.update {
            it.copy(lunes = lunes, dias = dias, fechaSeleccionada = seleccion)
        }
        cargarDia(seleccion)
    }

    /**
     * Trae las tomas del dia y les suma sus citas.
     *
     * Una carga anterior en vuelo se cancela: si el paciente toca lunes y
     * enseguida martes, la respuesta del lunes no puede llegar tarde y pintarse
     * encima del martes.
     */
    private fun cargarDia(fecha: String) {
        cargaDelDia?.cancel()
        cargaDelDia = viewModelScope.launch {
            val tomas = ejecutarSeguro { adherencia.obtenerTomasDelDia(idPaciente, fecha) }
            if (tomas.isFailure) {
                _estado.update { it.copy(cargando = false, errorCarga = true, eventos = emptyList()) }
                return@launch
            }
            val hoy = _estado.value.hoy
            val eventos = combinar(
                tomas = tomas.getOrThrow().map { toma ->
                    // Una toma de un dia que aun no llega no puede estar tomada
                    // ni omitida: se muestra como lo que es, algo programado.
                    if (fecha > hoy) toma.copy(estado = EstadoToma.PENDIENTE) else toma
                },
                citas = citasDelPaciente.filter { it.fecha == fecha },
            )
            _estado.update { it.copy(cargando = false, errorCarga = false, eventos = eventos) }
        }
    }

    /**
     * Las citas del paciente, reunidas de las agendas de sus medicos.
     *
     * Solo las SUYAS: la agenda de un medico trae las de todos sus pacientes y
     * sus bloqueos personales, y nada de eso le corresponde ver a este paciente.
     * Si falla la lista de medicos, falla todo el bloque de citas; si falla la
     * agenda de UN medico, se pierden solo sus citas y el resto se muestra.
     */
    private suspend fun leerCitasDelPaciente(): Result<List<Cita>> {
        val medicos = ejecutarSeguro { directorio.obtenerMedicosVinculados(idPaciente) }
        if (medicos.isFailure) return Result.failure(medicos.exceptionOrNull() ?: IllegalStateException())
        val reunidas = medicos.getOrThrow().flatMap { medico ->
            ejecutarSeguro { citas.cargarAgenda(medico.idMedico) }
                .getOrNull()
                .orEmpty()
                .filter { it.idPaciente == idPaciente && !it.esBloqueo }
        }
        return Result.success(reunidas)
    }

    companion object {
        private const val DIAS_SEMANA = 7
        private const val SEMANAS_VENTANA = 2

        /** Dias visibles en el calendario: dos semanas completas. */
        const val DIAS_VENTANA = DIAS_SEMANA * SEMANAS_VENTANA

        /** Lunes de la semana que contiene [fecha]. */
        internal fun lunesDe(fecha: String): String =
            CalendarioSalud.restarDias(fecha, CalendarioSalud.diaDeLaSemana(fecha) ?: 0)

        /**
         * Tomas y citas en una sola lista por hora. A la misma hora, la cita va
         * primero: es la que obliga a salir de casa, y la toma se puede hacer
         * en la sala de espera.
         */
        internal fun combinar(tomas: List<TomaDelDia>, citas: List<Cita>): List<EventoAgenda> =
            (citas.map { EventoAgenda.ConCita(it) } + tomas.map { EventoAgenda.Toma(it) })
                .sortedWith(compareBy<EventoAgenda> { it.hora }.thenBy { if (it is EventoAgenda.ConCita) 0 else 1 })
    }
}
