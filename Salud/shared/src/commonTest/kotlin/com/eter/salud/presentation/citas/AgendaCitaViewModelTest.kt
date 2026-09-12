package com.eter.salud.presentation.citas

import com.eter.salud.domain.model.Especialidad
import com.eter.salud.domain.model.MedicoVinculado
import com.eter.salud.domain.model.MotivoFalloCita
import com.eter.salud.presentation.directorio.DirectorioMedicoRepositorioFalso
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Maquina de estados del agendamiento conversacional.
 *
 * Lo critico que se fija aqui: la franja se aparta EN CUANTO el paciente la
 * elige (antes de capturar datos, que es la ventana en la que dos pacientes
 * podrian pisarse), y ninguna salida del flujo deja una retencion colgada.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AgendaCitaViewModelTest {

    private val vinculado = MedicoVinculado(
        idMedico = "doc_889900A",
        nombreCompleto = "Dra. Elena Ruiz Santos",
        especialidad = Especialidad.CARDIOLOGIA,
        idConversacion = "conv_pac_01H8X9A_doc_889900A",
    )

    @BeforeTest
    fun configurar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        citas: CitasRepositorioFalso = CitasRepositorioFalso(),
        conMedicoVinculado: Boolean = true,
    ) = AgendaCitaViewModel(
        citas = citas,
        directorio = DirectorioMedicoRepositorioFalso(),
        idPaciente = "pac_01H8X9A",
        medicoVinculado = if (conMedicoVinculado) vinculado else null,
        reloj = RelojAjustable(),
    )

    /** Lleva el flujo hasta el paso de captura de datos, con la franja ya apartada. */
    private fun hastaDatos(citas: CitasRepositorioFalso = CitasRepositorioFalso()): AgendaCitaViewModel {
        val vm = viewModel(citas)
        vm.usarMedicoVinculado()
        vm.elegirFranja(CitasRepositorioFalso.FRANJAS_DEMO.first())
        return vm
    }

    private fun AgendaCitaViewModel.capturarDatosValidos() {
        actualizarNombre("Alfredo Valadez Gonzalez")
        actualizarTelefono("6181234567")
        actualizarCorreo("alfredo@ejemplo.mx")
        actualizarMotivo("Dolor de cabeza desde hace tres dias")
    }

    // ----------------------------------------------------------- Apertura

    @Test
    fun el_chat_arranca_sin_panel_de_agendamiento() {
        assertFalse(viewModel().estado.value.activo)
    }

    @Test
    fun una_frase_que_pide_cita_abre_el_flujo_en_el_paso_de_especialidad() {
        val vm = viewModel()

        vm.evaluarMensaje("Quiero agendar una cita")

        assertTrue(vm.estado.value.activo)
        assertEquals(PasoAgenda.ESPECIALIDAD, vm.estado.value.paso)
    }

    @Test
    fun un_mensaje_clinico_normal_no_abre_el_flujo() {
        val vm = viewModel()

        vm.evaluarMensaje("Me duele la cabeza desde ayer")

        assertFalse(vm.estado.value.activo)
    }

    @Test
    fun un_segundo_mensaje_no_reinicia_un_flujo_ya_abierto() {
        // Sin esta guarda, escribir "agendar" a mitad de la captura borraria los
        // datos que el paciente ya habia tecleado.
        val vm = hastaDatos()
        vm.capturarDatosValidos()

        vm.evaluarMensaje("Quiero agendar una cita")

        assertEquals(PasoAgenda.DATOS, vm.estado.value.paso)
        assertEquals("Alfredo Valadez Gonzalez", vm.estado.value.nombreCompleto)
    }

    // ------------------------------------------------ Especialidad y medico

    @Test
    fun el_atajo_del_medico_de_siempre_salta_directo_a_los_horarios() {
        val citas = CitasRepositorioFalso()
        val vm = viewModel(citas)

        vm.usarMedicoVinculado()

        assertEquals(PasoAgenda.FRANJA, vm.estado.value.paso)
        assertEquals("Dra. Elena Ruiz Santos", vm.estado.value.medico?.nombreCompleto)
        assertEquals(1, citas.franjasPedidas)
    }

    @Test
    fun sin_medico_vinculado_el_atajo_no_existe_y_no_hace_nada() {
        val vm = viewModel(conMedicoVinculado = false)
        vm.iniciar()

        vm.usarMedicoVinculado()

        assertFalse(vm.tieneMedicoVinculado)
        assertEquals(PasoAgenda.ESPECIALIDAD, vm.estado.value.paso)
    }

    @Test
    fun elegir_especialidad_trae_los_medicos_de_esa_especialidad() {
        val vm = viewModel()
        vm.iniciar()

        vm.elegirEspecialidad(Especialidad.CARDIOLOGIA)

        assertEquals(PasoAgenda.MEDICO, vm.estado.value.paso)
        assertTrue(vm.estado.value.medicos.isNotEmpty())
        assertTrue(vm.estado.value.medicos.all { it.especialidad == Especialidad.CARDIOLOGIA })
    }

    // -------------------------------------------------------- Horarios

    @Test
    fun los_horarios_llegan_agrupados_por_dia_y_en_orden() {
        val vm = viewModel()
        vm.usarMedicoVinculado()

        val dias = vm.estado.value.dias
        assertEquals(listOf("2026-09-09", "2026-09-10"), dias.map { it.fecha })
        assertEquals(listOf("09:00", "09:30"), dias.first().franjas.map { it.horaInicio })
    }

    @Test
    fun una_agenda_sin_huecos_se_anuncia_en_vez_de_quedar_en_blanco() {
        val citas = CitasRepositorioFalso(franjas = Result.success(emptyList()))
        val vm = viewModel(citas)

        vm.usarMedicoVinculado()

        assertEquals(ErrorAgendaCita.SIN_HORARIOS, vm.estado.value.error)
    }

    @Test
    fun elegir_un_horario_lo_aparta_antes_de_pedir_los_datos() {
        // El orden importa: si la retencion ocurriera al confirmar, otro paciente
        // podria agendar esa hora mientras este escribe su telefono.
        val citas = CitasRepositorioFalso()
        val vm = viewModel(citas)
        vm.usarMedicoVinculado()

        vm.elegirFranja(CitasRepositorioFalso.FRANJAS_DEMO.first())

        assertEquals(listOf("doc_889900A|2026-09-09|09:00"), citas.reservasSolicitadas)
        assertEquals(PasoAgenda.DATOS, vm.estado.value.paso)
        assertNotNull(vm.estado.value.reserva)
    }

    @Test
    fun si_otro_paciente_gana_la_franja_se_avisa_y_se_recarga_la_lista() {
        val citas = CitasRepositorioFalso(
            resultadoReserva = CitasRepositorioFalso.fallo(MotivoFalloCita.FRANJA_OCUPADA),
        )
        val vm = viewModel(citas)
        vm.usarMedicoVinculado()

        vm.elegirFranja(CitasRepositorioFalso.FRANJAS_DEMO.first())

        assertEquals(ErrorAgendaCita.FRANJA_OCUPADA, vm.estado.value.error)
        assertEquals(PasoAgenda.FRANJA, vm.estado.value.paso)
        // Una por el atajo y otra por la recarga: la lista deja de mostrar una
        // hora que ya no existe.
        assertEquals(2, citas.franjasPedidas)
    }

    // ---------------------------------------------------------- Datos

    @Test
    fun unos_datos_incompletos_no_dejan_pasar_al_resumen() {
        val vm = hastaDatos()

        vm.revisarResumen()

        assertEquals(PasoAgenda.DATOS, vm.estado.value.paso)
        assertTrue(ErrorCampoCita.NOMBRE_VACIO in vm.estado.value.errores)
    }

    @Test
    fun corregir_un_campo_retira_su_error_sin_tocar_los_demas() {
        val vm = hastaDatos()
        vm.revisarResumen()

        vm.actualizarNombre("Alfredo Valadez Gonzalez")

        val errores = vm.estado.value.errores
        assertFalse(ErrorCampoCita.NOMBRE_VACIO in errores)
        assertTrue(ErrorCampoCita.TELEFONO_VACIO in errores)
    }

    @Test
    fun con_los_datos_completos_se_pasa_al_resumen() {
        val vm = hastaDatos()

        vm.capturarDatosValidos()
        vm.revisarResumen()

        assertEquals(PasoAgenda.RESUMEN, vm.estado.value.paso)
        assertTrue(vm.estado.value.errores.isEmpty())
    }

    // --------------------------------------------------- Confirmacion

    @Test
    fun confirmar_publica_el_acuse_con_folio_y_suelta_la_retencion() {
        val citas = CitasRepositorioFalso()
        val vm = hastaDatos(citas)
        vm.capturarDatosValidos()
        vm.revisarResumen()

        vm.confirmar()

        val estado = vm.estado.value
        assertEquals(PasoAgenda.CONFIRMADA, estado.paso)
        assertEquals("CITA-4201", estado.confirmacion?.cita?.folio)
        // Ya es una cita: la retencion deja de existir.
        assertNull(estado.reserva)
    }

    @Test
    fun el_telefono_viaja_al_backend_ya_limpio_de_espacios_y_guiones() {
        val citas = CitasRepositorioFalso()
        val vm = hastaDatos(citas)
        vm.capturarDatosValidos()
        vm.actualizarTelefono("(618) 123-45-67")
        vm.revisarResumen()

        vm.confirmar()

        assertEquals("6181234567", citas.contactoConfirmado?.telefono)
    }

    @Test
    fun si_la_retencion_caduco_se_explica_y_se_vuelve_a_elegir_horario() {
        val citas = CitasRepositorioFalso(
            resultadoConfirmacion = CitasRepositorioFalso.fallo(MotivoFalloCita.RESERVA_EXPIRADA),
        )
        val vm = hastaDatos(citas)
        vm.capturarDatosValidos()
        vm.revisarResumen()

        vm.confirmar()

        val estado = vm.estado.value
        assertEquals(ErrorAgendaCita.RESERVA_EXPIRADA, estado.error)
        assertEquals(PasoAgenda.FRANJA, estado.paso)
        // No se queda en un resumen que ya no se puede confirmar.
        assertNull(estado.franja)
        assertNull(estado.reserva)
    }

    @Test
    fun un_fallo_sin_motivo_conocido_se_cuenta_como_falta_de_conexion() {
        val citas = CitasRepositorioFalso(
            resultadoConfirmacion = Result.failure(IllegalStateException("algo raro")),
        )
        val vm = hastaDatos(citas)
        vm.capturarDatosValidos()
        vm.revisarResumen()

        vm.confirmar()

        assertEquals(ErrorAgendaCita.SIN_CONEXION, vm.estado.value.error)
    }

    // --------------------------------------------------------- Salidas

    @Test
    fun cancelar_suelta_la_franja_para_que_otro_la_pueda_tomar_ya() {
        val citas = CitasRepositorioFalso()
        val vm = hastaDatos(citas)

        vm.cancelar()

        assertEquals(listOf("res_doc_889900A|2026-09-09|09:00"), citas.reservasLiberadas)
        assertFalse(vm.estado.value.activo)
    }

    @Test
    fun cancelar_sin_franja_apartada_no_intenta_liberar_nada() {
        val citas = CitasRepositorioFalso()
        val vm = viewModel(citas)
        vm.iniciar()

        vm.cancelar()

        assertTrue(citas.reservasLiberadas.isEmpty())
    }

    @Test
    fun cerrar_el_acuse_devuelve_el_chat_a_la_conversacion() {
        val vm = hastaDatos()
        vm.capturarDatosValidos()
        vm.revisarResumen()
        vm.confirmar()

        vm.cerrarConfirmacion()

        assertFalse(vm.estado.value.activo)
    }

    @Test
    fun desde_el_resumen_se_puede_volver_a_corregir_los_datos() {
        val vm = hastaDatos()
        vm.capturarDatosValidos()
        vm.revisarResumen()

        vm.volverADatos()

        assertEquals(PasoAgenda.DATOS, vm.estado.value.paso)
        // Lo capturado no se pierde al retroceder.
        assertEquals("Alfredo Valadez Gonzalez", vm.estado.value.nombreCompleto)
    }
}
