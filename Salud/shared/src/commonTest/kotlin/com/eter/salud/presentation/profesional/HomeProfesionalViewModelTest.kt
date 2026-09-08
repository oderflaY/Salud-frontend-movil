package com.eter.salud.presentation.profesional

import com.eter.salud.domain.model.EstadoVerificacionCedula
import com.eter.salud.domain.model.PacienteVinculado
import com.eter.salud.domain.model.RiesgoPaciente
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
import kotlin.test.assertTrue

/**
 * Panel principal del profesional: identidad ya conocida al entrar (viene de
 * la sesion) y la cartera de pacientes, que si se descarga.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeProfesionalViewModelTest {

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
        repositorio: PacientesVinculadosRepositorioFalso = PacientesVinculadosRepositorioFalso(),
        estadoVerificacion: EstadoVerificacionCedula = EstadoVerificacionCedula.APROBADO,
    ) = HomeProfesionalViewModel(
        pacientesVinculados = repositorio,
        idMedico = idMedico,
        tratamiento = "Dra.",
        apellidos = "Ruiz Santos",
        estadoVerificacion = estadoVerificacion,
    )

    @Test
    fun el_estado_arranca_con_la_identidad_que_trae_la_sesion_sin_esperar_a_la_red() {
        val vm = viewModel()

        val estado = vm.estado.value
        assertEquals("Dra.", estado.tratamiento)
        assertEquals("Ruiz Santos", estado.apellidos)
        assertTrue(estado.cedulaVerificada)
        assertFalse(estado.cargando)
    }

    @Test
    fun una_cedula_pendiente_no_se_muestra_como_verificada() {
        val vm = viewModel(estadoVerificacion = EstadoVerificacionCedula.PENDIENTE)

        assertFalse(vm.estado.value.cedulaVerificada)
    }

    @Test
    fun cargar_descarga_la_cartera_de_pacientes_del_medico_de_la_sesion() {
        val repositorio = PacientesVinculadosRepositorioFalso(
            Result.success(
                listOf(
                    PacienteVinculado("pac_01H8X9A", "Juan Perez Gomez", RiesgoPaciente.ALTO),
                    PacienteVinculado("pac_02J9Y8B", "Maria Lopez", RiesgoPaciente.BAJO),
                ),
            ),
        )
        val vm = viewModel(repositorio)

        vm.cargar()

        assertEquals(listOf(idMedico), repositorio.consultas)
        assertEquals(2, vm.estado.value.pacientesVinculados.size)
        assertEquals(RiesgoPaciente.ALTO, vm.estado.value.pacientesVinculados.first().riesgo)
        assertFalse(vm.estado.value.cargando)
    }

    @Test
    fun si_el_backend_falla_la_pantalla_lo_dice_en_lugar_de_quedarse_vacia() {
        val repositorio = PacientesVinculadosRepositorioFalso(
            Result.failure(IllegalStateException("sin red")),
        )
        val vm = viewModel(repositorio)

        vm.cargar()

        assertTrue(vm.estado.value.errorCarga)
        assertFalse(vm.estado.value.cargando)
    }

    @Test
    fun una_carga_posterior_a_una_exitosa_vuelve_a_consultar_en_vez_de_quedar_bloqueada() {
        // Con el dispatcher no confinado el fake nunca suspende de verdad, asi
        // que esto no prueba el guardia contra reentradas concurrentes (eso
        // exigiria un repositorio que realmente suspenda); prueba que
        // `cargando` no se queda pegado en `true` tras terminar, lo que
        // impediria refrescar la pantalla con un pull-to-refresh futuro.
        val repositorio = PacientesVinculadosRepositorioFalso()
        val vm = viewModel(repositorio)

        vm.cargar()
        vm.cargar()

        assertEquals(2, repositorio.consultas.size)
        assertFalse(vm.estado.value.cargando)
    }
}
