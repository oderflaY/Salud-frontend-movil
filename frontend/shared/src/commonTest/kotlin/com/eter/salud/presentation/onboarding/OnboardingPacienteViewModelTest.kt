package com.eter.salud.presentation.onboarding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
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
 * Pruebas unitarias de la maquina de estados del onboarding (Fase 1).
 * DM_Arquitectura_App.md, seccion 2: ningun flujo de datos se aprueba sin pruebas.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingPacienteViewModelTest {

    private lateinit var repositorio: PacienteRepositorioFalso

    @BeforeTest
    fun configurar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repositorio = PacienteRepositorioFalso()
    }

    @AfterTest
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        repositorio: PacienteRepositorioFalso = this.repositorio,
    ) = OnboardingPacienteViewModel(repositorio = repositorio, reloj = RelojFijo())

    // ---------------------------------------------------------------- Navegacion

    @Test
    fun el_flujo_arranca_en_la_pantalla_de_bienvenida() {
        val vm = viewModel()

        assertEquals(PasoOnboarding.BIENVENIDA, vm.estado.value.paso)
        assertFalse(vm.estado.value.puedeRetroceder)
        assertTrue(vm.estado.value.erroresPaso.isEmpty())
    }

    @Test
    fun avanzar_desde_bienvenida_lleva_a_la_pregunta_de_nombre() {
        val vm = viewModel()

        vm.avanzar()

        assertEquals(PasoOnboarding.NOMBRE, vm.estado.value.paso)
        assertEquals(1, vm.estado.value.numeroPregunta)
        assertEquals(PasoOnboarding.TOTAL_PREGUNTAS, vm.estado.value.totalPreguntas)
    }

    @Test
    fun retroceder_regresa_al_paso_previo_y_limpia_los_errores() {
        val vm = viewModel()
        vm.avanzar()
        vm.avanzar() // sin nombre: genera errores

        assertTrue(vm.estado.value.erroresPaso.isNotEmpty())

        vm.retroceder()

        assertEquals(PasoOnboarding.BIENVENIDA, vm.estado.value.paso)
        assertTrue(vm.estado.value.erroresPaso.isEmpty())
    }

    // ---------------------------------------------------------------- Validaciones

    @Test
    fun no_avanza_si_falta_el_nombre_o_los_apellidos() {
        val vm = viewModel()
        vm.avanzar()

        vm.avanzar()

        assertEquals(PasoOnboarding.NOMBRE, vm.estado.value.paso)
        assertContains(vm.estado.value.erroresPaso, ErrorCampoOnboarding.NOMBRE_VACIO)
        assertContains(vm.estado.value.erroresPaso, ErrorCampoOnboarding.APELLIDOS_VACIO)
    }

    @Test
    fun el_nombre_valido_permite_avanzar_y_queda_en_el_borrador() {
        val vm = viewModel()
        vm.avanzar()

        vm.actualizarNombre("  Juan  ")
        vm.actualizarApellidos("Perez Gomez")
        vm.avanzar()

        assertEquals(PasoOnboarding.FECHA_NACIMIENTO, vm.estado.value.paso)
        assertTrue(vm.estado.value.erroresPaso.isEmpty())
        assertEquals("Juan", vm.estado.value.borrador.nombre.trim())
    }

    @Test
    fun la_fecha_de_nacimiento_exige_formato_iso() {
        val vm = irA(PasoOnboarding.FECHA_NACIMIENTO)

        vm.actualizarFechaNacimiento("12/04/1985")
        vm.avanzar()

        assertEquals(PasoOnboarding.FECHA_NACIMIENTO, vm.estado.value.paso)
        assertContains(vm.estado.value.erroresPaso, ErrorCampoOnboarding.NACIMIENTO_FORMATO)
    }

    @Test
    fun la_fecha_de_nacimiento_no_puede_ser_futura() {
        val vm = irA(PasoOnboarding.FECHA_NACIMIENTO)

        vm.actualizarFechaNacimiento("2030-01-01")
        vm.avanzar()

        assertContains(vm.estado.value.erroresPaso, ErrorCampoOnboarding.NACIMIENTO_FUTURA)
    }

    @Test
    fun el_telefono_exige_diez_digitos() {
        val vm = irA(PasoOnboarding.TELEFONO)

        vm.actualizarTelefono("618123")
        vm.avanzar()

        assertContains(vm.estado.value.erroresPaso, ErrorCampoOnboarding.TELEFONO_FORMATO)

        vm.actualizarTelefono("+52 618 123 4567")
        vm.avanzar()

        assertEquals(PasoOnboarding.TIPO_SANGRE, vm.estado.value.paso)
    }

    @Test
    fun una_alergia_incompleta_no_se_agrega_a_la_lista() {
        val vm = irA(PasoOnboarding.ALERGIAS)

        vm.agregarAlergia(alergeno = "  ", severidad = "", reaccion = "")

        assertTrue(vm.estado.value.borrador.alergias.isEmpty())
        assertContains(vm.estado.value.erroresFormulario, ErrorCampoOnboarding.ALERGIA_ALERGENO_VACIO)
        assertContains(vm.estado.value.erroresFormulario, ErrorCampoOnboarding.ALERGIA_SEVERIDAD_VACIA)
        assertContains(vm.estado.value.erroresFormulario, ErrorCampoOnboarding.ALERGIA_REACCION_VACIA)
    }

    @Test
    fun una_alergia_completa_se_agrega_y_se_puede_eliminar() {
        val vm = irA(PasoOnboarding.ALERGIAS)

        vm.agregarAlergia("Penicilina", "Alta (Anafilaxia)", "Cierre de vias respiratorias")

        assertEquals(1, vm.estado.value.borrador.alergias.size)
        assertEquals("Penicilina", vm.estado.value.borrador.alergias.first().alergeno)
        assertTrue(vm.estado.value.erroresFormulario.isEmpty())

        vm.eliminarAlergia(0)

        assertTrue(vm.estado.value.borrador.alergias.isEmpty())
    }

    @Test
    fun el_paso_de_alergias_exige_registrar_o_declarar_que_no_hay() {
        val vm = irA(PasoOnboarding.ALERGIAS)

        vm.avanzar()

        assertEquals(PasoOnboarding.ALERGIAS, vm.estado.value.paso)
        assertContains(vm.estado.value.erroresPaso, ErrorCampoOnboarding.ALERGIAS_SIN_CONFIRMAR)

        vm.marcarSinAlergiasConocidas(true)
        vm.avanzar()

        assertEquals(PasoOnboarding.CONDICIONES_CRITICAS, vm.estado.value.paso)
    }

    @Test
    fun la_frecuencia_del_tratamiento_debe_ser_un_entero_de_horas() {
        val vm = irA(PasoOnboarding.TRATAMIENTOS_ACTIVOS)

        vm.agregarTratamiento(
            medicamento = "Losartan",
            dosis = "50mg",
            frecuenciaHoras = "cada doce",
            cantidadRestante = "14",
            viaAdministracion = "Oral",
        )

        assertTrue(vm.estado.value.borrador.tratamientos.isEmpty())
        assertContains(
            vm.estado.value.erroresFormulario,
            ErrorCampoOnboarding.TRATAMIENTO_FRECUENCIA_INVALIDA,
        )
    }

    @Test
    fun el_paso_de_contactos_exige_al_menos_un_contacto() {
        val vm = irA(PasoOnboarding.CONTACTOS_EMERGENCIA)

        vm.avanzar()

        assertEquals(PasoOnboarding.CONTACTOS_EMERGENCIA, vm.estado.value.paso)
        assertContains(vm.estado.value.erroresPaso, ErrorCampoOnboarding.CONTACTOS_VACIO)
    }

    // ------------------------------------------- Alerta por omision de datos criticos

    @Test
    fun omitir_el_tipo_de_sangre_lo_registra_como_campo_critico_pendiente() {
        val vm = irA(PasoOnboarding.TIPO_SANGRE)

        vm.omitirPaso()

        assertEquals(PasoOnboarding.ALERGIAS, vm.estado.value.paso)
        assertContains(
            vm.estado.value.camposCriticosOmitidos,
            CampoCriticoSupervivencia.TIPO_SANGRE,
        )
    }

    @Test
    fun enviar_con_un_campo_critico_omitido_alerta_a_la_interfaz_y_no_llama_a_la_red() = runTest {
        val vm = completarFase1(omitirTipoSangre = true)

        vm.enviar()

        assertTrue(vm.estado.value.mostrarAlertaCriticos)
        assertContains(
            vm.estado.value.camposCriticosOmitidos,
            CampoCriticoSupervivencia.TIPO_SANGRE,
        )
        assertEquals(0, repositorio.invocaciones)
        assertFalse(vm.estado.value.enviando)
    }

    @Test
    fun la_alerta_se_descarta_al_volver_a_completar_el_campo_critico() = runTest {
        val vm = completarFase1(omitirTipoSangre = true)
        vm.enviar()

        vm.seleccionarTipoSangre("O+")

        assertTrue(vm.estado.value.camposCriticosOmitidos.isEmpty())
        assertFalse(vm.estado.value.mostrarAlertaCriticos)
    }

    @Test
    fun el_paciente_puede_confirmar_el_envio_pese_a_la_alerta() = runTest {
        val vm = completarFase1(omitirTipoSangre = true)
        vm.enviar()

        vm.confirmarEnvioConCamposCriticosOmitidos()

        assertEquals(1, repositorio.invocaciones)
        assertFalse(vm.estado.value.mostrarAlertaCriticos)
        assertTrue(vm.estado.value.envioExitoso)
    }

    // ---------------------------------------------------------------- Envio

    @Test
    fun un_perfil_completo_se_envia_y_reporta_exito() = runTest {
        val vm = completarFase1()

        vm.enviar()

        assertEquals(1, repositorio.invocaciones)
        assertFalse(vm.estado.value.enviando)
        assertTrue(vm.estado.value.envioExitoso)
        assertNull(vm.estado.value.errorEnvio)
        assertEquals("pac_01H8X9A", vm.estado.value.idPacienteRegistrado)
    }

    @Test
    fun el_dto_enviado_lleva_los_datos_en_el_nodo_que_marca_el_documento_maestro() = runTest {
        val vm = completarFase1()

        vm.enviar()

        val dto = repositorio.ultimoDtoEnviado
        assertTrue(dto != null)
        assertEquals("Juan", dto.datosPersonales?.nombre)
        assertEquals("Perez Gomez", dto.datosPersonales?.apellidos)
        assertEquals("1985-04-12", dto.datosPersonales?.fechaNacimiento)
        assertEquals("O+", dto.perfilEmergenciaReducido?.tipoSangre)
        assertEquals("Penicilina", dto.perfilEmergenciaReducido?.alergias?.first()?.alergeno)
        assertEquals(
            listOf("Hipertension arterial"),
            dto.perfilEmergenciaReducido?.condicionesCriticas,
        )
        assertEquals(12, dto.tratamientosActivos.first().frecuenciaHoras)
        assertEquals(14, dto.tratamientosActivos.first().inventario?.cantidadRestante)
        assertEquals(1, dto.contactosEmergencia.first().prioridad)
    }

    @Test
    fun un_fallo_de_red_deja_el_estado_en_error_y_no_marca_exito() = runTest {
        val fallido = PacienteRepositorioFalso(Result.failure(RuntimeException("timeout")))
        val vm = completarFase1(repositorio = fallido)

        vm.enviar()

        assertFalse(vm.estado.value.enviando)
        assertFalse(vm.estado.value.envioExitoso)
        assertEquals(ErrorEnvio.SIN_CONEXION, vm.estado.value.errorEnvio)
    }

    @Test
    fun reintentar_despues_de_un_fallo_vuelve_a_llamar_al_repositorio() = runTest {
        val fallido = PacienteRepositorioFalso(Result.failure(RuntimeException("timeout")))
        val vm = completarFase1(repositorio = fallido)
        vm.enviar()

        vm.enviar()

        assertEquals(2, fallido.invocaciones)
    }

    // ---------------------------------------------------------------- Utilidades

    /** Avanza el flujo con datos validos hasta el paso indicado. */
    private fun irA(destino: PasoOnboarding): OnboardingPacienteViewModel {
        val vm = viewModel()
        while (vm.estado.value.paso != destino) {
            responderPasoActual(vm)
            val anterior = vm.estado.value.paso
            vm.avanzar()
            if (vm.estado.value.paso == anterior) {
                error("El flujo se quedo atorado en $anterior camino a $destino")
            }
        }
        return vm
    }

    private fun completarFase1(
        omitirTipoSangre: Boolean = false,
        repositorio: PacienteRepositorioFalso = this.repositorio,
    ): OnboardingPacienteViewModel {
        val vm = viewModel(repositorio)
        while (vm.estado.value.paso != PasoOnboarding.RESUMEN) {
            if (omitirTipoSangre && vm.estado.value.paso == PasoOnboarding.TIPO_SANGRE) {
                vm.omitirPaso()
                continue
            }
            responderPasoActual(vm)
            val anterior = vm.estado.value.paso
            vm.avanzar()
            if (vm.estado.value.paso == anterior) {
                error("El flujo se quedo atorado en $anterior")
            }
        }
        return vm
    }

    private fun responderPasoActual(vm: OnboardingPacienteViewModel) {
        when (vm.estado.value.paso) {
            PasoOnboarding.BIENVENIDA -> Unit
            PasoOnboarding.NOMBRE -> {
                vm.actualizarNombre("Juan")
                vm.actualizarApellidos("Perez Gomez")
            }
            PasoOnboarding.FECHA_NACIMIENTO -> vm.actualizarFechaNacimiento("1985-04-12")
            PasoOnboarding.GENERO -> vm.actualizarGenero("Masculino")
            PasoOnboarding.TELEFONO -> vm.actualizarTelefono("+526181234567")
            PasoOnboarding.TIPO_SANGRE -> vm.seleccionarTipoSangre("O+")
            PasoOnboarding.ALERGIAS -> vm.agregarAlergia(
                "Penicilina",
                "Alta (Anafilaxia)",
                "Cierre de vias respiratorias",
            )
            PasoOnboarding.CONDICIONES_CRITICAS ->
                vm.agregarCondicionCritica("Hipertension arterial")
            PasoOnboarding.MEDICACION_RESCATE ->
                vm.agregarMedicacionRescate("Anticoagulantes activos")
            PasoOnboarding.TRATAMIENTOS_ACTIVOS -> vm.agregarTratamiento(
                medicamento = "Losartan",
                dosis = "50mg",
                frecuenciaHoras = "12",
                cantidadRestante = "14",
                viaAdministracion = "Oral",
            )
            PasoOnboarding.CONTACTOS_EMERGENCIA -> vm.agregarContacto(
                nombre = "Maria Gomez",
                relacion = "Madre",
                telefono = "+526189876543",
                prioridad = 1,
            )
            PasoOnboarding.RESUMEN -> Unit
        }
    }
}
