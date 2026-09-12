package com.eter.salud.presentation.agendapaciente

import com.eter.salud.domain.model.Cita
import com.eter.salud.domain.model.EstadoCita
import com.eter.salud.domain.model.EstadoToma
import com.eter.salud.domain.model.Especialidad
import com.eter.salud.domain.model.MedicoVinculado
import com.eter.salud.domain.model.TomaDelDia
import com.eter.salud.presentation.citas.CitasRepositorioFalso
import com.eter.salud.presentation.citas.RelojAjustable
import com.eter.salud.presentation.directorio.DirectorioMedicoRepositorioFalso
import com.eter.salud.presentation.home.AdherenciaRepositorioFalso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Agenda del paciente.
 *
 * Lo que se fija: que la ventana sean dos semanas desde el lunes y marque hoy,
 * que el paciente vea SOLO sus citas (la agenda del medico trae las de todos),
 * que tomas y citas se mezclen por hora, que un dia futuro no aparezca
 * "tomado", y que la lista de proximas cubra los catorce dias que vienen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AgendaPacienteViewModelTest {

    private val idPaciente = "pac_01H8X9A"

    /** 2026-09-10 es jueves: su lunes es el 7. */
    private val reloj = RelojAjustable(fecha = "2026-09-10")

    private val medico = MedicoVinculado(
        idMedico = "doc_889900A",
        nombreCompleto = "Dra. Elena Ruiz Santos",
        especialidad = Especialidad.CARDIOLOGIA,
        idConversacion = "conv_x",
    )

    @BeforeTest
    fun configurar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun cita(id: String, fecha: String, hora: String, paciente: String = idPaciente, estado: EstadoCita = EstadoCita.CONFIRMADA) = Cita(
        idCita = id,
        folio = "CITA-$id",
        idMedico = medico.idMedico,
        nombreMedico = medico.nombreCompleto,
        idPaciente = paciente,
        fecha = fecha,
        horaInicio = hora,
        horaFin = hora,
        estado = estado,
    )

    private fun toma(id: String, hora: String, estado: EstadoToma = EstadoToma.TOMADO) = TomaDelDia(
        idToma = id,
        idTratamiento = "trt_1",
        medicamento = "Metformina",
        dosis = "850 mg",
        horaProgramada = hora,
        estado = estado,
    )

    private fun viewModel(
        tomas: List<TomaDelDia> = emptyList(),
        agenda: List<Cita> = emptyList(),
        directorio: DirectorioMedicoRepositorioFalso = DirectorioMedicoRepositorioFalso(
            medicoVinculado = Result.success(medico),
        ),
        citas: CitasRepositorioFalso = CitasRepositorioFalso(agendaInicial = Result.success(agenda)),
    ) = AgendaPacienteViewModel(
        adherencia = AdherenciaRepositorioFalso(tomas = Result.success(tomas)),
        citas = citas,
        directorio = directorio,
        idPaciente = idPaciente,
        reloj = reloj,
    )

    @Test
    fun la_ventana_son_dos_semanas_de_lunes_a_domingo_y_marca_hoy_elegido() {
        val estado = viewModel().estado.value

        assertEquals(14, estado.dias.size)
        assertEquals("2026-09-07", estado.dias.first().fecha)
        assertEquals("2026-09-20", estado.dias.last().fecha)
        assertEquals("2026-09-10", estado.fechaSeleccionada)
        assertTrue(estado.dias.single { it.esHoy }.fecha == "2026-09-10")
        assertTrue(estado.incluyeHoy)
    }

    @Test
    fun el_paciente_solo_ve_sus_citas_y_nunca_los_bloqueos_del_medico() {
        val estado = viewModel(
            agenda = listOf(
                cita("mia", "2026-09-10", "11:00"),
                cita("de_otro", "2026-09-10", "12:00", paciente = "pac_otro"),
                cita("bloqueo", "2026-09-10", "14:00", paciente = "", estado = EstadoCita.BLOQUEADO),
            ),
        ).estado.value

        val citas = estado.eventos.filterIsInstance<EventoAgenda.ConCita>()
        assertEquals(listOf("mia"), citas.map { it.cita.idCita })
    }

    @Test
    fun tomas_y_citas_se_mezclan_por_hora_y_a_la_misma_hora_va_primero_la_cita() {
        val estado = viewModel(
            tomas = listOf(toma("t8", "08:00"), toma("t11", "11:00"), toma("t20", "20:00")),
            agenda = listOf(cita("c11", "2026-09-10", "11:00")),
        ).estado.value

        val orden = estado.eventos.map {
            when (it) {
                is EventoAgenda.Toma -> it.toma.idToma
                is EventoAgenda.ConCita -> it.cita.idCita
            }
        }
        assertEquals(listOf("t8", "c11", "t11", "t20"), orden)
    }

    @Test
    fun en_un_dia_futuro_las_tomas_salen_programadas_y_no_tomadas() {
        val vm = viewModel(tomas = listOf(toma("t8", "08:00", EstadoToma.TOMADO)))

        vm.elegirDia("2026-09-12")

        val evento = assertIs<EventoAgenda.Toma>(vm.estado.value.eventos.single())
        assertEquals(EstadoToma.PENDIENTE, evento.toma.estado)
    }

    @Test
    fun avanzar_mueve_dos_semanas_conserva_la_posicion_y_ofrece_volver_a_hoy() {
        val vm = viewModel()

        vm.periodoSiguiente()

        val estado = vm.estado.value
        assertEquals("2026-09-21", estado.lunes)
        // El mismo jueves de la primera semana, en la ventana nueva.
        assertEquals("2026-09-24", estado.fechaSeleccionada)
        assertFalse(estado.incluyeHoy)

        vm.irAHoy()
        assertEquals("2026-09-10", vm.estado.value.fechaSeleccionada)
    }

    @Test
    fun el_dia_con_cita_lo_cuenta_la_casilla_del_calendario() {
        val estado = viewModel(agenda = listOf(cita("c", "2026-09-11", "09:00"))).estado.value

        assertEquals(1, estado.dias.single { it.fecha == "2026-09-11" }.citas)
        assertEquals(0, estado.dias.single { it.fecha == "2026-09-10" }.citas)
    }

    @Test
    fun una_cita_de_la_semana_que_viene_ya_se_ve_sin_cambiar_de_pagina() {
        val estado = viewModel(agenda = listOf(cita("martes", "2026-09-15", "10:00"))).estado.value

        assertEquals(1, estado.dias.single { it.fecha == "2026-09-15" }.citas)
    }

    @Test
    fun las_proximas_citas_cubren_de_hoy_a_trece_dias_y_omiten_las_canceladas() {
        val estado = viewModel(
            agenda = listOf(
                cita("ayer", "2026-09-09", "10:00"),
                cita("hoy", "2026-09-10", "12:00"),
                cita("ultimo_dia", "2026-09-23", "09:00"),
                cita("fuera", "2026-09-24", "09:00"),
                cita("cancelada", "2026-09-12", "09:00", estado = EstadoCita.CANCELADA),
                cita("de_otro", "2026-09-11", "09:00", paciente = "pac_otro"),
            ),
        ).estado.value

        assertEquals(listOf("hoy", "ultimo_dia"), estado.proximasCitas.map { it.idCita })
    }

    @Test
    fun elegir_una_proxima_cita_fuera_de_la_ventana_mueve_el_calendario_hasta_ella() {
        val vm = viewModel(agenda = listOf(cita("lejana", "2026-09-22", "09:00")))

        vm.elegirDia("2026-09-22")

        val estado = vm.estado.value
        assertEquals("2026-09-21", estado.lunes)
        assertEquals("2026-09-22", estado.fechaSeleccionada)
        assertEquals("lejana", assertIs<EventoAgenda.ConCita>(estado.eventos.single()).cita.idCita)
    }

    @Test
    fun si_fallan_las_citas_la_agenda_sigue_con_las_medicinas_y_lo_avisa() {
        val estado = viewModel(
            tomas = listOf(toma("t8", "08:00")),
            directorio = DirectorioMedicoRepositorioFalso(medicoVinculado = Result.failure(IllegalStateException("sin red"))),
        ).estado.value

        assertTrue(estado.citasNoDisponibles)
        assertFalse(estado.errorCarga)
        assertEquals(1, estado.eventos.size)
    }

    // ---------------------------------------------- Propuesta del medico

    @Test
    fun aceptar_una_propuesta_la_pide_al_repositorio_por_su_id() {
        val propuesta = cita("prop_1", "2026-09-10", "11:00", estado = EstadoCita.PROPUESTA_MEDICO)
        val citasFalsas = CitasRepositorioFalso(agendaInicial = Result.success(listOf(propuesta)))
        val vm = viewModel(agenda = listOf(propuesta), citas = citasFalsas)

        vm.aceptarPropuesta(propuesta)

        assertEquals(listOf("prop_1"), citasFalsas.propuestasAceptadas)
        assertFalse(vm.estado.value.errorRespuestaPropuesta)
    }

    @Test
    fun rechazar_una_propuesta_la_pide_al_repositorio_por_su_id() {
        val propuesta = cita("prop_1", "2026-09-10", "11:00", estado = EstadoCita.PROPUESTA_MEDICO)
        val citasFalsas = CitasRepositorioFalso(agendaInicial = Result.success(listOf(propuesta)))
        val vm = viewModel(agenda = listOf(propuesta), citas = citasFalsas)

        vm.rechazarPropuesta(propuesta)

        assertEquals(listOf("prop_1"), citasFalsas.propuestasRechazadas)
    }

    @Test
    fun si_responder_a_una_propuesta_falla_se_avisa_sin_romper_la_pantalla() {
        val propuesta = cita("prop_1", "2026-09-10", "11:00", estado = EstadoCita.PROPUESTA_MEDICO)
        val citasFalsas = CitasRepositorioFalso(
            agendaInicial = Result.success(listOf(propuesta)),
            resultadoPropuesta = Result.failure(IllegalStateException("la propuesta ya no existe")),
        )
        val vm = viewModel(agenda = listOf(propuesta), citas = citasFalsas)

        vm.aceptarPropuesta(propuesta)

        assertTrue(vm.estado.value.errorRespuestaPropuesta)
    }
}
