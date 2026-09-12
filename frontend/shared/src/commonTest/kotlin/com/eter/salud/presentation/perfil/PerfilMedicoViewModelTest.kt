package com.eter.salud.presentation.perfil

import com.eter.salud.domain.model.Alergia
import com.eter.salud.domain.model.DatosPersonales
import com.eter.salud.domain.model.PacienteDto
import com.eter.salud.domain.model.PerfilEmergenciaReducido
import com.eter.salud.presentation.onboarding.RelojFijo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pruebas de la Fase 2: ajustes y completado del historial medico.
 *
 * Verifican que cada seccion se valide de forma independiente y que el estado
 * mute correctamente ANTES de llegar al backend (DM_Arquitectura_App.md,
 * seccion 2: ningun flujo de datos se aprueba sin pruebas).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PerfilMedicoViewModelTest {

    private val idPaciente = "pac_01H8X9A"

    @BeforeTest
    fun configurar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun viewModel(repositorio: HistorialMedicoRepositorioFalso) =
        PerfilMedicoViewModel(repositorio = repositorio, reloj = RelojFijo())

    /** Perfil tal como lo devuelve la API tras completar la Fase 1. */
    private fun perfilDeFase1() = PacienteDto(
        idPaciente = idPaciente,
        datosPersonales = DatosPersonales(
            nombre = "Juan",
            apellidos = "Perez Gomez",
            fechaNacimiento = "1985-04-12",
            genero = "Masculino",
            telefono = "+526181234567",
        ),
        perfilEmergenciaReducido = PerfilEmergenciaReducido(
            tipoSangre = "O+",
            alergias = listOf(
                Alergia("Penicilina", "Alta (Anafilaxia)", "Cierre de vias respiratorias"),
            ),
            condicionesCriticas = listOf("Hipertension arterial"),
        ),
    )

    // -------------------------------------------------------------------- Carga

    @Test
    fun cargar_el_perfil_hidrata_el_borrador_con_lo_que_ya_existe_en_el_backend() {
        val repositorio = HistorialMedicoRepositorioFalso(
            perfilRemoto = Result.success(perfilDeFase1()),
        )
        val vm = viewModel(repositorio)

        vm.cargarPerfil(idPaciente)

        val estado = vm.estado.value
        assertEquals(1, repositorio.lecturas)
        assertFalse(estado.cargando)
        assertEquals(idPaciente, estado.idPaciente)
        assertEquals("Juan", estado.borrador.nombre)
        assertEquals("O+", estado.borrador.tipoSangre)
        assertEquals(1, estado.borrador.alergias.size)
    }

    @Test
    fun un_perfil_recien_creado_arranca_sin_ninguna_seccion_completa() {
        val vm = viewModel(HistorialMedicoRepositorioFalso(Result.success(perfilDeFase1())))

        vm.cargarPerfil(idPaciente)

        assertTrue(vm.estado.value.seccionesCompletas.isEmpty())
        assertEquals(0, vm.estado.value.porcentajeCompletado)
    }

    // --------------------------------------------------------------- Navegacion

    @Test
    fun abrir_y_cerrar_una_seccion_limpia_los_errores_previos() {
        val vm = viewModel(HistorialMedicoRepositorioFalso())
        vm.cargarPerfil(idPaciente)

        vm.abrirSeccion(SeccionPerfil.IDENTIFICACION_CURP)
        vm.guardarSeccion() // CURP vacia: genera error
        assertTrue(vm.estado.value.erroresSeccion.isNotEmpty())

        vm.cerrarSeccion()

        assertNull(vm.estado.value.seccionAbierta)
        assertTrue(vm.estado.value.erroresSeccion.isEmpty())
    }

    // ---------------------------------------------------- Captura y mutacion

    @Test
    fun la_curp_valida_muta_el_estado_y_viaja_en_el_nodo_identificaciones() {
        val repositorio = HistorialMedicoRepositorioFalso(Result.success(perfilDeFase1()))
        val vm = viewModel(repositorio)
        vm.cargarPerfil(idPaciente)
        vm.abrirSeccion(SeccionPerfil.IDENTIFICACION_CURP)

        vm.actualizarCurp("pagj850412hdfrxx09")
        vm.guardarSeccion()

        val enviado = repositorio.ultimoDtoGuardado
        assertEquals("PAGJ850412HDFRXX09", enviado?.identificaciones?.curp)
        assertEquals(idPaciente, enviado?.idPaciente)
        assertContains(vm.estado.value.seccionesCompletas, SeccionPerfil.IDENTIFICACION_CURP)
    }

    @Test
    fun guardar_la_donacion_de_organos_no_borra_las_alergias_capturadas_en_la_fase_1() {
        val repositorio = HistorialMedicoRepositorioFalso(Result.success(perfilDeFase1()))
        val vm = viewModel(repositorio)
        vm.cargarPerfil(idPaciente)
        vm.abrirSeccion(SeccionPerfil.DONACION_ORGANOS)

        vm.marcarDonadorOrganos(true)
        vm.guardarSeccion()

        val emergencia = repositorio.ultimoDtoGuardado?.perfilEmergenciaReducido
        assertEquals(true, emergencia?.donadorOrganos)
        assertEquals("O+", emergencia?.tipoSangre)
        assertEquals(listOf("Hipertension arterial"), emergencia?.condicionesCriticas)
        assertEquals("Penicilina", emergencia?.alergias?.single()?.alergeno)
    }

    @Test
    fun las_metricas_corporales_generan_el_imc_y_la_fecha_de_toma_del_reloj_inyectado() {
        val repositorio = HistorialMedicoRepositorioFalso(Result.success(perfilDeFase1()))
        val vm = viewModel(repositorio)
        vm.cargarPerfil(idPaciente)
        vm.abrirSeccion(SeccionPerfil.METRICAS_CORPORALES)

        vm.actualizarPeso("78.5")
        vm.actualizarAltura("175")

        assertEquals(25.6, vm.estado.value.imcCalculado)

        vm.guardarSeccion()

        val metricas = repositorio.ultimoDtoGuardado?.metricasVitalesActuales
        assertEquals(78.5, metricas?.pesoKg)
        assertEquals(175, metricas?.alturaCm)
        assertEquals(25.6, metricas?.imc)
        assertEquals("2026-09-05T08:00:00Z", metricas?.fechaTomaMetricas)
    }

    @Test
    fun la_presion_arterial_viaja_como_sistolica_sobre_diastolica() {
        val repositorio = HistorialMedicoRepositorioFalso(Result.success(perfilDeFase1()))
        val vm = viewModel(repositorio)
        vm.cargarPerfil(idPaciente)
        vm.abrirSeccion(SeccionPerfil.PRESION_ARTERIAL)

        vm.actualizarPresionSistolica("120")
        vm.actualizarPresionDiastolica("80")
        vm.guardarSeccion()

        assertEquals(
            "120/80",
            repositorio.ultimoDtoGuardado?.metricasVitalesActuales?.ultimaPresionArterial,
        )
    }

    // -------------------------------------------------------- Listas dinamicas

    @Test
    fun una_cirugia_invalida_no_entra_a_la_lista_y_publica_el_error_del_formulario() {
        val vm = viewModel(HistorialMedicoRepositorioFalso())
        vm.cargarPerfil(idPaciente)
        vm.abrirSeccion(SeccionPerfil.CIRUGIAS)

        vm.agregarCirugia(procedimiento = "", fecha = "2015-08-20", notas = "")

        assertTrue(vm.estado.value.borrador.cirugias.isEmpty())
        assertContains(
            vm.estado.value.erroresFormulario,
            ErrorCampoPerfil.CIRUGIA_PROCEDIMIENTO_VACIO,
        )
    }

    @Test
    fun una_cirugia_valida_muta_el_nodo_historial_clinico() {
        val repositorio = HistorialMedicoRepositorioFalso(Result.success(perfilDeFase1()))
        val vm = viewModel(repositorio)
        vm.cargarPerfil(idPaciente)
        vm.abrirSeccion(SeccionPerfil.CIRUGIAS)

        vm.agregarCirugia(
            procedimiento = "Apendicectomia",
            fecha = "2015-08-20",
            notas = "Sin complicaciones",
        )
        vm.guardarSeccion()

        val cirugia = repositorio.ultimoDtoGuardado?.historialClinico?.cirugias?.single()
        assertEquals("Apendicectomia", cirugia?.procedimiento)
        assertEquals("2015-08-20", cirugia?.fecha)
        assertEquals("Sin complicaciones", cirugia?.notas)
        assertTrue(vm.estado.value.erroresFormulario.isEmpty())
    }

    @Test
    fun declarar_que_no_hay_antecedentes_vacia_la_lista_y_completa_la_seccion() {
        val repositorio = HistorialMedicoRepositorioFalso(Result.success(perfilDeFase1()))
        val vm = viewModel(repositorio)
        vm.cargarPerfil(idPaciente)
        vm.abrirSeccion(SeccionPerfil.ANTECEDENTES_HEREDOFAMILIARES)
        vm.agregarAntecedente("Diabetes tipo 2 en linea materna")

        vm.marcarSinAntecedentes(true)
        vm.guardarSeccion()

        assertTrue(vm.estado.value.borrador.antecedentesHeredofamiliares.isEmpty())
        assertContains(
            vm.estado.value.seccionesCompletas,
            SeccionPerfil.ANTECEDENTES_HEREDOFAMILIARES,
        )
    }

    @Test
    fun eliminar_un_antecedente_por_indice_respeta_el_resto_de_la_lista() {
        val vm = viewModel(HistorialMedicoRepositorioFalso())
        vm.cargarPerfil(idPaciente)
        vm.agregarAntecedente("Diabetes")
        vm.agregarAntecedente("Cardiopatia")

        vm.eliminarAntecedente(0)

        assertEquals(
            listOf("Cardiopatia"),
            vm.estado.value.borrador.antecedentesHeredofamiliares,
        )
    }

    // ---------------------------------------------------------------- Guardado

    @Test
    fun una_seccion_invalida_nunca_llega_al_backend() {
        val repositorio = HistorialMedicoRepositorioFalso()
        val vm = viewModel(repositorio)
        vm.cargarPerfil(idPaciente)
        vm.abrirSeccion(SeccionPerfil.METRICAS_CORPORALES)

        vm.actualizarPeso("600")
        vm.actualizarAltura("175")
        vm.guardarSeccion()

        assertEquals(0, repositorio.guardados)
        assertEquals(listOf(ErrorCampoPerfil.PESO_INVALIDO), vm.estado.value.erroresSeccion)
        assertEquals(SeccionPerfil.METRICAS_CORPORALES, vm.estado.value.seccionAbierta)
    }

    @Test
    fun un_guardado_exitoso_cierra_la_seccion_y_deja_la_confirmacion_para_la_vista() {
        val repositorio = HistorialMedicoRepositorioFalso(Result.success(perfilDeFase1()))
        val vm = viewModel(repositorio)
        vm.cargarPerfil(idPaciente)
        vm.abrirSeccion(SeccionPerfil.DONACION_ORGANOS)
        vm.marcarDonadorOrganos(false)

        vm.guardarSeccion()

        val estado = vm.estado.value
        assertFalse(estado.guardando)
        assertNull(estado.seccionAbierta)
        assertEquals(SeccionPerfil.DONACION_ORGANOS, estado.seccionGuardada)
        assertNull(estado.errorGuardado)

        vm.descartarConfirmacion()
        assertNull(vm.estado.value.seccionGuardada)
    }

    @Test
    fun si_el_backend_falla_la_seccion_sigue_abierta_y_no_se_marca_completa() {
        val repositorio = HistorialMedicoRepositorioFalso(
            perfilRemoto = Result.success(perfilDeFase1()),
            resultadoGuardado = Result.failure(IllegalStateException("sin red")),
        )
        val vm = viewModel(repositorio)
        vm.cargarPerfil(idPaciente)
        vm.abrirSeccion(SeccionPerfil.DONACION_ORGANOS)
        vm.marcarDonadorOrganos(true)

        vm.guardarSeccion()

        val estado = vm.estado.value
        assertEquals(1, repositorio.guardados)
        assertFalse(estado.guardando)
        assertEquals(SeccionPerfil.DONACION_ORGANOS, estado.seccionAbierta)
        assertEquals(ErrorGuardado.SIN_CONEXION, estado.errorGuardado)
        assertFalse(estado.seccionesCompletas.contains(SeccionPerfil.DONACION_ORGANOS))
    }

    @Test
    fun sin_perfil_cargado_no_se_intenta_guardar_nada() {
        val repositorio = HistorialMedicoRepositorioFalso()
        val vm = viewModel(repositorio)
        vm.abrirSeccion(SeccionPerfil.DONACION_ORGANOS)
        vm.marcarDonadorOrganos(true)

        vm.guardarSeccion()

        assertEquals(0, repositorio.guardados)
        assertEquals(ErrorGuardado.PERFIL_NO_CARGADO, vm.estado.value.errorGuardado)
    }

    @Test
    fun el_porcentaje_completado_avanza_con_cada_seccion_guardada() {
        val repositorio = HistorialMedicoRepositorioFalso(Result.success(perfilDeFase1()))
        val vm = viewModel(repositorio)
        vm.cargarPerfil(idPaciente)

        vm.abrirSeccion(SeccionPerfil.DONACION_ORGANOS)
        vm.marcarDonadorOrganos(true)
        vm.guardarSeccion()

        val esperado = 100 / SeccionPerfil.TOTAL_SECCIONES
        assertEquals(esperado, vm.estado.value.porcentajeCompletado)
    }

    @Test
    fun si_el_backend_no_devuelve_el_perfil_la_vista_recibe_el_error_de_carga() {
        val repositorio = HistorialMedicoRepositorioFalso(
            perfilRemoto = Result.failure(IllegalStateException("sin red")),
        )
        val vm = viewModel(repositorio)

        vm.cargarPerfil(idPaciente)

        assertFalse(vm.estado.value.cargando)
        assertEquals(ErrorGuardado.SIN_CONEXION, vm.estado.value.errorGuardado)
        assertNull(vm.estado.value.idPaciente)
    }
}
