package com.eter.salud.presentation.directorio

import com.eter.salud.domain.model.Especialidad
import com.eter.salud.domain.model.MedicoVinculado
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Directorio Medico: estas pruebas garantizan que el paciente siempre puede
 * escoger un medico -- tenga o no uno ya --, que al escoger se abre la ficha
 * de ese medico, y que la lista resuelve sus cuatro escenarios sellados (carga,
 * exito, vacio y error) sin quedarse muda en ninguno.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DescubrimientoMedicoViewModelTest {

    private val idPaciente = "pac_01H8X9A"

    @BeforeTest
    fun configurar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun viewModel(repositorio: DirectorioMedicoRepositorioFalso) =
        DescubrimientoMedicoViewModel(repositorio = repositorio, idPaciente = idPaciente)

    // -------------------------------------------------------- Directorio vs chat

    @Test
    fun al_construirse_el_directorio_se_llena_solo_sin_que_la_vista_lo_pida() {
        // Regresion del bug del directorio vacio: la carga cuelga del ciclo de
        // vida del ViewModel, no de un `LaunchedEffect` cuya clave nunca cambia.
        val repositorio = DirectorioMedicoRepositorioFalso()

        val vm = viewModel(repositorio)

        assertEquals(1, repositorio.consultasVinculado)
        val directorio = assertIs<DirectorioUiState.ConDoctores>(vm.estado.value.directorio)
        assertTrue(directorio.doctores.isNotEmpty())
    }

    @Test
    fun sin_medico_vinculado_el_estado_dice_mostrar_el_directorio_con_doctores() {
        val vm = viewModel(DirectorioMedicoRepositorioFalso(medicoVinculado = Result.success(null)))


        val estado = vm.estado.value
        assertTrue(estado.debeMostrarDirectorio)
        assertNull(estado.medicoVinculado)
        val directorio = assertIs<DirectorioUiState.ConDoctores>(estado.directorio)
        assertTrue(directorio.doctores.isNotEmpty())
    }

    @Test
    fun con_medico_ya_vinculado_el_directorio_sigue_ofreciendo_escoger_otro() {
        // Antes el directorio desaparecia con UN medico vinculado, y como toda
        // cuenta nace vinculada con la Dra. Ruiz, nadie podia escoger a otro.
        val vinculado = MedicoVinculado(
            idMedico = "doc_889900A",
            nombreCompleto = "Dra. Elena Ruiz Santos",
            especialidad = Especialidad.CARDIOLOGIA,
            idConversacion = "conv_pac_01H8X9A_doc_889900A",
        )
        val repositorio = DirectorioMedicoRepositorioFalso(medicoVinculado = Result.success(vinculado))
        val vm = viewModel(repositorio)

        val estado = vm.estado.value
        assertTrue(estado.debeMostrarDirectorio)
        // Se conserva para el contador de mensajes de la barra inferior.
        assertEquals(vinculado, estado.medicoVinculado)
        assertIs<DirectorioUiState.ConDoctores>(estado.directorio)
    }

    @Test
    fun con_medico_ya_vinculado_se_puede_escoger_un_segundo_especialista() {
        val vinculado = MedicoVinculado(
            idMedico = "doc_889900A",
            nombreCompleto = "Dra. Elena Ruiz Santos",
            especialidad = Especialidad.CARDIOLOGIA,
            idConversacion = "conv_pac_01H8X9A_doc_889900A",
        )
        val repositorio = DirectorioMedicoRepositorioFalso(medicoVinculado = Result.success(vinculado))
        val vm = viewModel(repositorio)

        vm.solicitarVinculacion("doc_local_2")

        val estado = vm.estado.value
        assertEquals(listOf("doc_local_2"), repositorio.solicitudesVinculacion)
        assertEquals("doc_local_2", estado.medicoElegido?.idMedico)
        // El primero sigue siendo el de la barra: escoger otro no lo desplaza.
        assertEquals("doc_889900A", estado.medicoVinculado?.idMedico)
    }

    // ------------------------------------------------ Ficha del medico elegido

    @Test
    fun cerrar_la_ficha_devuelve_la_lista_para_comparar_medicos() {
        val vm = viewModel(DirectorioMedicoRepositorioFalso())
        vm.solicitarVinculacion("doc_local_2")

        vm.cerrarFicha()

        val estado = vm.estado.value
        assertTrue(estado.debeMostrarDirectorio)
        assertNull(estado.medicoElegido)
        assertNull(estado.perfilDelElegido)
    }

    @Test
    fun al_vincularse_la_ficha_se_toma_de_la_lista_ya_descargada() {
        val repositorio = DirectorioMedicoRepositorioFalso()
        val vm = viewModel(repositorio)

        vm.solicitarVinculacion("doc_local_2")

        assertEquals("Dr. Carlos Mendoza", vm.estado.value.perfilDelElegido?.nombreCompleto)
        // No hubo segunda llamada: el perfil ya estaba en la lista que el
        // paciente acaba de tocar.
        assertTrue(repositorio.perfilesPedidos.isEmpty())
    }

    // ------------------------------------------------- Los cuatro estados sellados

    @Test
    fun un_directorio_sin_doctores_publica_el_estado_vacio_y_no_una_lista_en_blanco() {
        val vm = viewModel(DirectorioMedicoRepositorioFalso(doctores = Result.success(emptyList())))


        assertEquals(DirectorioUiState.Vacio, vm.estado.value.directorio)
    }

    @Test
    fun si_la_consulta_del_directorio_falla_se_publica_el_estado_de_error() {
        val vm = viewModel(
            DirectorioMedicoRepositorioFalso(doctores = Result.failure(IllegalStateException("sin red"))),
        )


        val error = assertIs<DirectorioUiState.Error>(vm.estado.value.directorio)
        assertEquals(ErrorDirectorio.SIN_CONEXION, error.motivo)
    }

    @Test
    fun si_la_consulta_de_vinculo_falla_se_publica_el_estado_de_error() {
        val vm = viewModel(
            DirectorioMedicoRepositorioFalso(medicoVinculado = Result.failure(IllegalStateException("sin red"))),
        )


        assertIs<DirectorioUiState.Error>(vm.estado.value.directorio)
    }

    @Test
    fun una_carga_posterior_a_una_fallida_vuelve_a_intentarlo_en_vez_de_quedar_bloqueada() {
        // Regresion del guardia de reentrada: mirar `Cargando` habria bloqueado
        // para siempre, porque `Cargando` es tambien el estado inicial.
        val repositorio = DirectorioMedicoRepositorioFalso()
        val vm = viewModel(repositorio)

        vm.cargar()

        // Una consulta la hizo el `init` y otra esta llamada explicita: el
        // guardia se libera al terminar, no se queda pegado en `true`.
        assertEquals(2, repositorio.consultasVinculado)
    }

    // ---------------------------------------------------------------- Filtros

    @Test
    fun filtrar_por_especialidad_vuelve_a_consultar_el_directorio_con_el_filtro() {
        val repositorio = DirectorioMedicoRepositorioFalso()
        val vm = viewModel(repositorio)

        vm.filtrarPorEspecialidad(Especialidad.CARDIOLOGIA)

        assertEquals(listOf(null, Especialidad.CARDIOLOGIA), repositorio.especialidadesBuscadas)
        val directorio = assertIs<DirectorioUiState.ConDoctores>(vm.estado.value.directorio)
        assertTrue(directorio.doctores.all { it.especialidad == Especialidad.CARDIOLOGIA })
    }

    @Test
    fun elegir_de_nuevo_la_misma_especialidad_no_repite_la_consulta() {
        val repositorio = DirectorioMedicoRepositorioFalso()
        val vm = viewModel(repositorio)
        vm.filtrarPorEspecialidad(Especialidad.CARDIOLOGIA)

        vm.filtrarPorEspecialidad(Especialidad.CARDIOLOGIA)

        assertEquals(2, repositorio.especialidadesBuscadas.size)
    }

    @Test
    fun un_filtro_de_especialidad_sin_doctores_publica_el_estado_vacio() {
        val repositorio = DirectorioMedicoRepositorioFalso()
        val vm = viewModel(repositorio)

        // El doble de prueba no tiene ningun psiquiatra.
        vm.filtrarPorEspecialidad(Especialidad.PSIQUIATRIA)

        assertEquals(DirectorioUiState.Vacio, vm.estado.value.directorio)
    }

    @Test
    fun el_texto_de_busqueda_filtra_localmente_sin_gastar_red() {
        val repositorio = DirectorioMedicoRepositorioFalso()
        val vm = viewModel(repositorio)

        vm.actualizarTextoBusqueda("Ruiz")

        assertEquals(1, vm.estado.value.doctoresFiltrados.size)
        assertEquals("Dra. Elena Ruiz Santos", vm.estado.value.doctoresFiltrados.single().nombreCompleto)
        // Ni un filtro de especialidad ni una segunda busqueda salieron a red.
        assertEquals(listOf<Especialidad?>(null), repositorio.especialidadesBuscadas)
    }

    @Test
    fun una_busqueda_en_blanco_no_oculta_a_nadie() {
        val vm = viewModel(DirectorioMedicoRepositorioFalso())

        vm.actualizarTextoBusqueda("   ")

        val directorio = assertIs<DirectorioUiState.ConDoctores>(vm.estado.value.directorio)
        assertEquals(directorio.doctores.size, vm.estado.value.doctoresFiltrados.size)
    }

    @Test
    fun sin_lista_publicada_la_busqueda_no_devuelve_doctores_fantasma() {
        val vm = viewModel(DirectorioMedicoRepositorioFalso(doctores = Result.success(emptyList())))

        vm.actualizarTextoBusqueda("Ruiz")

        assertTrue(vm.estado.value.doctoresFiltrados.isEmpty())
    }

    // ----------------------------------------------------------- Vinculacion

    @Test
    fun vincularse_con_un_doctor_abre_su_ficha_para_iniciar_la_consulta() {
        val vm = viewModel(DirectorioMedicoRepositorioFalso())

        vm.solicitarVinculacion("doc_local_2")

        val estado = vm.estado.value
        assertFalse(estado.debeMostrarDirectorio)
        assertEquals("doc_local_2", estado.medicoElegido?.idMedico)
        assertEquals("doc_local_2", estado.medicoVinculado?.idMedico)
        assertNull(estado.idSolicitandoVinculacion)
    }

    @Test
    fun si_la_vinculacion_falla_el_paciente_sigue_viendo_el_directorio() {
        val repositorio = DirectorioMedicoRepositorioFalso(
            resultadoVinculacion = Result.failure(IllegalStateException("sin red")),
        )
        val vm = viewModel(repositorio)

        vm.solicitarVinculacion("doc_local_2")

        val estado = vm.estado.value
        assertTrue(estado.debeMostrarDirectorio)
        assertTrue(estado.errorVinculacion)
        assertNull(estado.medicoVinculado)
    }

    @Test
    fun descartar_el_error_de_vinculacion_lo_retira_del_estado() {
        val repositorio = DirectorioMedicoRepositorioFalso(
            resultadoVinculacion = Result.failure(IllegalStateException("sin red")),
        )
        val vm = viewModel(repositorio)
        vm.solicitarVinculacion("doc_local_2")

        vm.descartarErrorVinculacion()

        assertFalse(vm.estado.value.errorVinculacion)
    }
}
