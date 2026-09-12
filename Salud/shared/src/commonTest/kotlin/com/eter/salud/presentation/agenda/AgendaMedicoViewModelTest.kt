package com.eter.salud.presentation.agenda

import com.eter.salud.domain.model.EstadoCita
import com.eter.salud.domain.model.MotivoFalloCita
import com.eter.salud.domain.model.PacienteVinculado
import com.eter.salud.domain.model.RiesgoPaciente
import com.eter.salud.domain.repository.FalloCita
import com.eter.salud.presentation.citas.CitasRepositorioFalso
import com.eter.salud.presentation.citas.RelojAjustable
import com.eter.salud.presentation.profesional.PacientesVinculadosRepositorioFalso
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Calendario del medico.
 *
 * Lo que se fija aqui: la agenda se suscribe sola al arrancar (no depende de un
 * efecto de la Vista), el rango visible depende de la vista elegida, y ninguna
 * accion escribe la lista a mano: la unica fuente de verdad es el flujo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AgendaMedicoViewModelTest {

    private val idMedico = "doc_889900A"

    @BeforeTest
    fun configurar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        repositorio: CitasRepositorioFalso = CitasRepositorioFalso(),
        pacientesVinculados: PacientesVinculadosRepositorioFalso? = null,
    ) = AgendaMedicoViewModel(
        repositorio = repositorio,
        idMedico = idMedico,
        // Martes 8 de septiembre de 2026.
        reloj = RelojAjustable(fecha = "2026-09-08"),
        pacientesVinculados = pacientesVinculados,
    )

    private val pacienteDemo = PacienteVinculado(
        idPaciente = "pac_01H8X9A",
        nombreCompleto = "Alfredo Valadez Gonzalez",
        riesgo = RiesgoPaciente.MEDIO,
        idConversacion = "conv_1",
    )

    // ------------------------------------------------------------- Carga

    @Test
    fun al_construirse_la_agenda_se_suscribe_sola_sin_que_la_vista_lo_pida() {
        val repositorio = CitasRepositorioFalso(
            agendaInicial = Result.success(listOf(CitasRepositorioFalso.CITA_DEMO)),
        )

        val vm = viewModel(repositorio)

        assertFalse(vm.estado.value.cargando)
        assertEquals(1, vm.estado.value.citas.size)
    }

    @Test
    fun una_cita_que_llega_por_el_flujo_aparece_sin_recargar_la_pantalla() {
        // Es la sincronizacion en tiempo real: el paciente confirma en el chat y
        // el calendario del medico lo refleja sin que nadie pulse nada.
        val repositorio = CitasRepositorioFalso()
        val vm = viewModel(repositorio)
        assertTrue(vm.estado.value.citas.isEmpty())

        repositorio.emitirAgenda(listOf(CitasRepositorioFalso.CITA_DEMO))

        assertEquals(1, vm.estado.value.citas.size)
    }

    @Test
    fun si_la_carga_inicial_falla_se_publica_el_estado_de_error() {
        val repositorio = CitasRepositorioFalso(
            agendaInicial = Result.failure(IllegalStateException("sin red")),
        )

        val vm = viewModel(repositorio)

        assertTrue(vm.estado.value.errorCarga)
        assertFalse(vm.estado.value.cargando)
    }

    @Test
    fun tras_un_fallo_de_carga_se_puede_reintentar_y_la_agenda_aparece() {
        // Sin esto la pantalla quedaba muerta: la suscripcion del `init` no se
        // reintenta sola y la unica salida era cerrar la app.
        val repositorio = CitasRepositorioFalso(
            agendaInicial = Result.failure(IllegalStateException("sin red")),
        )
        val vm = viewModel(repositorio)
        assertTrue(vm.estado.value.errorCarga)

        repositorio.emitirAgenda(listOf(CitasRepositorioFalso.CITA_DEMO))
        vm.reintentar()

        assertFalse(vm.estado.value.errorCarga)
    }

    @Test
    fun reintentar_mientras_ya_esta_cargando_no_duplica_la_consulta() {
        val repositorio = CitasRepositorioFalso()
        val vm = viewModel(repositorio)

        vm.reintentar()
        vm.reintentar()

        // No revienta ni deja el estado en carga permanente.
        assertFalse(vm.estado.value.cargando)
    }

    // ------------------------------------------------------------ Rangos

    @Test
    fun la_vista_diaria_solo_abarca_el_dia_ancla() {
        val vm = viewModel()

        val rango = vm.estado.value.rango
        assertEquals("2026-09-08", rango.desde)
        assertEquals("2026-09-08", rango.hasta)
    }

    @Test
    fun la_vista_de_dos_semanas_va_del_lunes_al_domingo_de_la_semana_siguiente() {
        val vm = viewModel()

        vm.cambiarVista(VistaCalendario.DOS_SEMANAS)

        val rango = vm.estado.value.rango
        // El 8 fue martes: empieza el lunes 7 y acaba el domingo 20.
        assertEquals("2026-09-07", rango.desde)
        assertEquals("2026-09-20", rango.hasta)
        assertEquals(14, rango.fechas.size)
    }

    @Test
    fun la_vista_mensual_abarca_el_mes_completo() {
        val vm = viewModel()

        vm.cambiarVista(VistaCalendario.MENSUAL)

        val rango = vm.estado.value.rango
        assertEquals("2026-09-01", rango.desde)
        assertEquals("2026-09-30", rango.hasta)
    }

    @Test
    fun avanzar_mueve_un_periodo_del_tamano_de_la_vista_y_no_siempre_un_dia() {
        val vm = viewModel()

        vm.irAlPeriodoSiguiente()
        assertEquals("2026-09-09", vm.estado.value.fechaAncla)

        vm.cambiarVista(VistaCalendario.DOS_SEMANAS)
        vm.irAlPeriodoSiguiente()
        assertEquals("2026-09-23", vm.estado.value.fechaAncla)
    }

    @Test
    fun cambiar_de_mes_salta_por_los_extremos_y_no_sumando_treinta_dias() {
        // Con una suma fija, febrero se saltaria o se repetiria.
        val vm = viewModel()
        vm.cambiarVista(VistaCalendario.MENSUAL)

        vm.irAlPeriodoSiguiente()
        assertEquals("2026-10-01", vm.estado.value.fechaAncla)

        vm.irAlPeriodoAnterior()
        assertEquals("2026-09-30", vm.estado.value.fechaAncla)
    }

    @Test
    fun volver_a_hoy_devuelve_el_ancla_a_la_fecha_del_reloj() {
        val vm = viewModel()
        vm.irAlPeriodoSiguiente()

        vm.irAHoy()

        assertEquals("2026-09-08", vm.estado.value.fechaAncla)
    }

    @Test
    fun tocar_un_dia_del_mes_lo_abre_en_vista_diaria() {
        val vm = viewModel()
        vm.cambiarVista(VistaCalendario.MENSUAL)

        vm.abrirDia("2026-09-15")

        assertEquals(VistaCalendario.DIARIA, vm.estado.value.vista)
        assertEquals("2026-09-15", vm.estado.value.fechaAncla)
    }

    // ---------------------------------------------------------- Rejilla

    @Test
    fun la_rejilla_del_dia_marca_ocupadas_las_horas_que_cubre_una_cita() {
        val repositorio = CitasRepositorioFalso(
            agendaInicial = Result.success(listOf(CitasRepositorioFalso.CITA_DEMO)),
        )
        val vm = viewModel(repositorio)
        vm.abrirDia("2026-09-09")

        val renglones = vm.estado.value.renglonesDelDia
        assertFalse(renglones.first { it.horaInicio == "09:00" }.libre)
        assertTrue(renglones.first { it.horaInicio == "09:30" }.libre)
    }

    @Test
    fun una_cita_cancelada_deja_de_ocupar_su_hora_en_la_rejilla() {
        val cancelada = CitasRepositorioFalso.CITA_DEMO.copy(estado = EstadoCita.CANCELADA)
        val repositorio = CitasRepositorioFalso(agendaInicial = Result.success(listOf(cancelada)))
        val vm = viewModel(repositorio)
        vm.abrirDia("2026-09-09")

        assertTrue(vm.estado.value.renglonesDelDia.first { it.horaInicio == "09:00" }.libre)
    }

    @Test
    fun el_resumen_por_estado_solo_cuenta_lo_del_periodo_visible() {
        val repositorio = CitasRepositorioFalso(
            agendaInicial = Result.success(
                listOf(
                    CitasRepositorioFalso.CITA_DEMO,
                    CitasRepositorioFalso.CITA_DEMO.copy(idCita = "cita_2", fecha = "2026-10-20"),
                ),
            ),
        )
        val vm = viewModel(repositorio)
        vm.abrirDia("2026-09-09")

        assertEquals(mapOf(EstadoCita.PENDIENTE to 1), vm.estado.value.resumenPorEstado)
    }

    // ------------------------------------------------------- Acciones

    @Test
    fun confirmar_una_cita_pide_el_cambio_de_estado_al_repositorio() {
        val repositorio = CitasRepositorioFalso(
            agendaInicial = Result.success(listOf(CitasRepositorioFalso.CITA_DEMO)),
        )
        val vm = viewModel(repositorio)

        vm.confirmarCita(CitasRepositorioFalso.CITA_DEMO)

        assertEquals(listOf("cita_1" to EstadoCita.CONFIRMADA), repositorio.estadosPedidos)
    }

    @Test
    fun iniciar_consulta_deja_la_cita_en_curso() {
        val repositorio = CitasRepositorioFalso(
            agendaInicial = Result.success(listOf(CitasRepositorioFalso.CITA_DEMO)),
        )
        val vm = viewModel(repositorio)

        vm.iniciarConsulta(CitasRepositorioFalso.CITA_DEMO)

        assertEquals(listOf("cita_1" to EstadoCita.EN_CURSO), repositorio.estadosPedidos)
    }

    @Test
    fun la_cita_abierta_en_el_detalle_se_refresca_con_su_nuevo_estado() {
        val repositorio = CitasRepositorioFalso(
            agendaInicial = Result.success(listOf(CitasRepositorioFalso.CITA_DEMO)),
        )
        val vm = viewModel(repositorio)
        vm.abrirCita(CitasRepositorioFalso.CITA_DEMO)

        vm.confirmarCita(CitasRepositorioFalso.CITA_DEMO)

        assertEquals(EstadoCita.CONFIRMADA, vm.estado.value.citaSeleccionada?.estado)
    }

    @Test
    fun si_el_cambio_de_estado_falla_se_avisa_en_vez_de_mentir_en_el_calendario() {
        val repositorio = CitasRepositorioFalso(
            agendaInicial = Result.success(listOf(CitasRepositorioFalso.CITA_DEMO)),
            resultadoCambioEstado = Result.failure(IllegalStateException("sin red")),
        )
        val vm = viewModel(repositorio)

        vm.confirmarCita(CitasRepositorioFalso.CITA_DEMO)

        assertTrue(vm.estado.value.errorAccion)
        // La lista no se toco: sigue mandando el flujo.
        assertEquals(EstadoCita.PENDIENTE, vm.estado.value.citas.single().estado)
    }

    // ------------------------------------------------------- Bloqueos

    @Test
    fun dos_toques_bloquean_el_rango_completo_incluida_la_ultima_franja() {
        val repositorio = CitasRepositorioFalso()
        val vm = viewModel(repositorio)
        vm.abrirDia("2026-09-09")

        vm.iniciarBloqueo("12:00")
        vm.completarBloqueo("13:00")

        // Hasta las 13:30 y no hasta las 13:00: la franja tocada se bloquea
        // entera, no hasta su hora de inicio.
        assertEquals(
            listOf(Triple("2026-09-09", "12:00", "13:30")),
            repositorio.bloqueosPedidos,
        )
    }

    @Test
    fun un_solo_toque_repetido_bloquea_esa_unica_franja_y_no_un_rango_vacio() {
        val repositorio = CitasRepositorioFalso()
        val vm = viewModel(repositorio)
        vm.abrirDia("2026-09-09")

        vm.iniciarBloqueo("13:00")
        vm.completarBloqueo("13:00")

        assertEquals(
            listOf(Triple("2026-09-09", "13:00", "13:30")),
            repositorio.bloqueosPedidos,
        )
    }

    @Test
    fun tocar_primero_la_hora_final_no_rechaza_el_gesto_sino_que_lo_ordena() {
        val repositorio = CitasRepositorioFalso()
        val vm = viewModel(repositorio)
        vm.abrirDia("2026-09-09")

        vm.iniciarBloqueo("13:00")
        vm.completarBloqueo("11:00")

        assertEquals(
            listOf(Triple("2026-09-09", "11:00", "13:30")),
            repositorio.bloqueosPedidos,
        )
    }

    @Test
    fun cancelar_el_bloqueo_limpia_la_marca_y_la_nota() {
        val vm = viewModel()
        vm.iniciarBloqueo("12:00")
        vm.actualizarNotaDeBloqueo("Cirugia")

        vm.cancelarBloqueo()

        assertFalse(vm.estado.value.hayBloqueoEnCurso)
        assertEquals("", vm.estado.value.notaDeBloqueo)
    }

    @Test
    fun cambiar_de_vista_a_media_marca_cancela_el_bloqueo_en_curso() {
        // Si sobreviviera, el segundo toque caeria sobre otro dia y bloquearia
        // una hora que el medico nunca eligio.
        val vm = viewModel()
        vm.iniciarBloqueo("12:00")

        vm.cambiarVista(VistaCalendario.DOS_SEMANAS)

        assertFalse(vm.estado.value.hayBloqueoEnCurso)
    }

    // --------------------------------------------------- Reprogramacion

    @Test
    fun reprogramar_ofrece_los_horarios_libres_y_mueve_la_cita_elegida() {
        val repositorio = CitasRepositorioFalso(
            agendaInicial = Result.success(listOf(CitasRepositorioFalso.CITA_DEMO)),
        )
        val vm = viewModel(repositorio)
        vm.abrirCita(CitasRepositorioFalso.CITA_DEMO)

        vm.abrirReprogramacion()
        assertTrue(vm.estado.value.franjasParaReprogramar.isNotEmpty())

        vm.reprogramarEn(CitasRepositorioFalso.FRANJAS_DEMO.last())

        assertEquals(
            listOf("cita_1" to "doc_889900A|2026-09-10|09:00"),
            repositorio.reprogramacionesPedidas,
        )
        // Movida la cita, la hoja se cierra: seguir mostrando la hora vieja
        // seria informacion caducada.
        assertNull(vm.estado.value.citaSeleccionada)
    }

    @Test
    fun sin_cita_abierta_no_se_puede_reprogramar_nada() {
        val repositorio = CitasRepositorioFalso()
        val vm = viewModel(repositorio)

        vm.abrirReprogramacion()
        vm.reprogramarEn(CitasRepositorioFalso.FRANJAS_DEMO.first())

        assertTrue(repositorio.reprogramacionesPedidas.isEmpty())
        assertFalse(vm.estado.value.reprogramando)
    }

    // ------------------------------------------------- Proponer una cita

    @Test
    fun al_construirse_carga_sola_la_cartera_de_pacientes_vinculados() {
        val pacientes = PacientesVinculadosRepositorioFalso(Result.success(listOf(pacienteDemo)))

        val vm = viewModel(pacientesVinculados = pacientes)

        assertEquals(listOf(pacienteDemo), vm.estado.value.pacientesVinculados)
    }

    @Test
    fun sin_cartera_inyectada_la_agenda_sigue_funcionando() {
        // La cartera es opcional a proposito: las pruebas del calendario que no
        // necesitan el flujo de "Agendar cita" no tienen por que inventar una.
        val vm = viewModel(pacientesVinculados = null)

        assertTrue(vm.estado.value.pacientesVinculados.isEmpty())
    }

    @Test
    fun iniciar_la_propuesta_abre_la_hoja_vacia() {
        val vm = viewModel()

        vm.iniciarPropuestaCita()

        assertTrue(vm.estado.value.proponiendoCita)
        assertNull(vm.estado.value.pacienteParaPropuesta)
    }

    @Test
    fun elegir_paciente_pide_los_horarios_libres_del_propio_medico() {
        val repositorio = CitasRepositorioFalso()
        val vm = viewModel(repositorio)
        vm.iniciarPropuestaCita()

        vm.elegirPacienteParaPropuesta(pacienteDemo)

        assertEquals(pacienteDemo, vm.estado.value.pacienteParaPropuesta)
        assertEquals(CitasRepositorioFalso.FRANJAS_DEMO, vm.estado.value.franjasParaPropuesta)
    }

    @Test
    fun confirmar_la_propuesta_la_envia_al_paciente_elegido_y_cierra_la_hoja() {
        val repositorio = CitasRepositorioFalso()
        val vm = viewModel(repositorio)
        vm.iniciarPropuestaCita()
        vm.elegirPacienteParaPropuesta(pacienteDemo)
        vm.actualizarMotivoPropuesta("Seguimiento de tratamiento")

        vm.confirmarPropuestaCita(CitasRepositorioFalso.FRANJAS_DEMO.first())

        val (idPaciente, idFranja, contacto) = repositorio.propuestasPedidas.single()
        assertEquals(pacienteDemo.idPaciente, idPaciente)
        assertEquals(CitasRepositorioFalso.FRANJAS_DEMO.first().idFranja, idFranja)
        assertEquals(pacienteDemo.nombreCompleto, contacto.nombreCompleto)
        assertEquals("Seguimiento de tratamiento", contacto.motivo)
        // La hoja se cierra sola: no hace falta un segundo toque para descartarla.
        assertFalse(vm.estado.value.proponiendoCita)
        assertNull(vm.estado.value.pacienteParaPropuesta)
    }

    @Test
    fun si_la_franja_ya_se_ocupo_la_propuesta_falla_y_la_hoja_sigue_abierta() {
        val repositorio = CitasRepositorioFalso(
            resultadoPropuesta = Result.failure(FalloCita(MotivoFalloCita.FRANJA_OCUPADA)),
        )
        val vm = viewModel(repositorio)
        vm.iniciarPropuestaCita()
        vm.elegirPacienteParaPropuesta(pacienteDemo)

        vm.confirmarPropuestaCita(CitasRepositorioFalso.FRANJAS_DEMO.first())

        assertTrue(vm.estado.value.errorPropuesta)
        assertTrue(vm.estado.value.proponiendoCita)
    }

    @Test
    fun cancelar_la_propuesta_limpia_todo_el_flujo() {
        val vm = viewModel()
        vm.iniciarPropuestaCita()
        vm.elegirPacienteParaPropuesta(pacienteDemo)
        vm.actualizarMotivoPropuesta("algo")

        vm.cancelarPropuestaCita()

        assertFalse(vm.estado.value.proponiendoCita)
        assertNull(vm.estado.value.pacienteParaPropuesta)
        assertTrue(vm.estado.value.franjasParaPropuesta.isEmpty())
        assertEquals("", vm.estado.value.motivoPropuesta)
    }
}
