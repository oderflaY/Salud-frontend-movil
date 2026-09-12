package com.eter.salud.presentation.profesional

import com.eter.salud.domain.diario.SeveridadDiario
import com.eter.salud.domain.model.EstadoVerificacionCedula
import com.eter.salud.domain.model.PacienteVinculado
import com.eter.salud.domain.model.RiesgoPaciente
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

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
        diario: DiarioParaPanelFalso = DiarioParaPanelFalso(),
    ) = HomeProfesionalViewModel(
        pacientesVinculados = repositorio,
        diario = diario,
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


/**
 * El punto de triage de la tarjeta del paciente.
 *
 * Cierra el circuito: lo que el paciente escribe en su diario decide el orden en
 * que su medico lo atiende. Lo critico es que "sin anotaciones" NO se confunda
 * con "verde": silencio no es lo mismo que estar bien.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TriageEnLaCarteraTest {

    private val idMedico = "doc_889900A"

    @BeforeTest
    fun configurar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private val cartera = listOf(
        PacienteVinculado(
            idPaciente = "pac_01H8X9A",
            nombreCompleto = "Alfredo Valadez Gonzalez",
            riesgo = RiesgoPaciente.BAJO,
            idConversacion = "conv_1",
        ),
        PacienteVinculado(
            idPaciente = "pac_02",
            nombreCompleto = "Maria Lopez",
            riesgo = RiesgoPaciente.MEDIO,
            idConversacion = "conv_2",
        ),
    )

    private fun viewModel(diario: DiarioParaPanelFalso) = HomeProfesionalViewModel(
        pacientesVinculados = PacientesVinculadosRepositorioFalso(Result.success(cartera)),
        diario = diario,
        idMedico = idMedico,
        tratamiento = "Dra.",
        apellidos = "Ruiz Santos",
        estadoVerificacion = EstadoVerificacionCedula.APROBADO,
    )

    @Test
    fun la_severidad_del_ultimo_diario_llega_a_la_tarjeta_del_paciente() {
        val vm = viewModel(DiarioParaPanelFalso(ultimas = mapOf("pac_01H8X9A" to SeveridadDiario.ROJO)))

        vm.cargar()

        assertEquals(SeveridadDiario.ROJO, vm.estado.value.severidadDelDiario["pac_01H8X9A"])
    }

    @Test
    fun un_paciente_sin_anotaciones_queda_FUERA_del_mapa_y_no_en_verde() {
        // Silencio no es lo mismo que estar bien: pintarlo verde le diria al
        // medico "todo en orden" sobre alguien que no ha escrito nada.
        val vm = viewModel(DiarioParaPanelFalso(ultimas = emptyMap()))

        vm.cargar()

        assertTrue(vm.estado.value.severidadDelDiario.isEmpty())
    }

    @Test
    fun solo_se_pide_la_ULTIMA_entrada_de_cada_paciente() {
        // Descargar bitacoras completas de veinte pacientes para pintar veinte
        // circulos gastaria red y memoria en datos que la pantalla no muestra.
        val diario = DiarioParaPanelFalso()
        val vm = viewModel(diario)

        vm.cargar()

        assertEquals(
            vm.estado.value.pacientesVinculados.map { it.idPaciente },
            diario.pacientesConsultados,
        )
    }

    @Test
    fun si_el_diario_falla_la_cartera_sigue_viendose() {
        // El punto es contexto; la cartera es lo que el medico viene a ver.
        val diario = DiarioParaPanelFalso(
            resultado = Result.failure(IllegalStateException("sin red")),
        )
        val vm = viewModel(diario)

        vm.cargar()

        val estado = vm.estado.value
        assertFalse(estado.errorCarga)
        assertTrue(estado.pacientesVinculados.isNotEmpty())
        assertTrue(estado.severidadDelDiario.isEmpty())
    }
}
