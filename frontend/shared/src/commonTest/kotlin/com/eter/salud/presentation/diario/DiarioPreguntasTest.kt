package com.eter.salud.presentation.diario

import com.eter.salud.domain.model.EntradaDiario
import com.eter.salud.domain.model.PacienteDto
import com.eter.salud.domain.model.PerfilEmergenciaReducido
import com.eter.salud.domain.repository.DiarioRepositorio
import com.eter.salud.presentation.onboarding.RelojFijo
import com.eter.salud.presentation.perfil.HistorialMedicoRepositorioFalso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Diario en memoria: solo lo necesario para estas pruebas. */
private class DiarioRepositorioFalso : DiarioRepositorio {
    private val entradas = MutableStateFlow<List<EntradaDiario>>(emptyList())
    var guardadas = mutableListOf<EntradaDiario>()
        private set

    override fun entradasDe(idPaciente: String): Flow<List<EntradaDiario>> = entradas.asStateFlow()

    override suspend fun guardar(entrada: EntradaDiario): Result<Unit> {
        guardadas += entrada
        return Result.success(Unit)
    }

    override suspend fun eliminar(idEntrada: String): Result<Unit> = Result.success(Unit)

    override suspend fun ultimaEntradaDe(idPaciente: String): Result<EntradaDiario?> = Result.success(null)
}

@OptIn(ExperimentalCoroutinesApi::class)
class DiarioPreguntasTest {

    @BeforeTest
    fun configurar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun pacienteCon(vararg condiciones: String) = PacienteDto(
        perfilEmergenciaReducido = PerfilEmergenciaReducido(condicionesCriticas = condiciones.toList()),
    )

    private fun viewModel(
        diario: DiarioRepositorioFalso = DiarioRepositorioFalso(),
        paciente: PacienteDto = pacienteCon("Hipertension arterial"),
    ) = DiarioViewModel(
        repositorio = diario,
        idPaciente = "pac_1",
        reloj = RelojFijo(),
        historial = HistorialMedicoRepositorioFalso(perfilRemoto = Result.success(paciente)),
    )

    @Test
    fun las_preguntas_salen_de_la_condicion_del_paciente() {
        val vm = viewModel(paciente = pacienteCon("Diabetes tipo 2"))

        val preguntas = vm.estado.value.preguntas.map { it.pregunta }

        assertTrue(preguntas.any { it.contains("glucosa", ignoreCase = true) }, preguntas.toString())
        assertEquals(listOf("Diabetes tipo 2"), vm.estado.value.condiciones)
    }

    @Test
    fun responder_escribe_el_inicio_en_el_borrador_sin_borrar_lo_ya_escrito() {
        val vm = viewModel()
        vm.actualizarBorrador("Hoy amaneci cansado")

        val pregunta = vm.estado.value.preguntas.first { it.clave == "presion_cifra" }
        vm.responder(pregunta)

        val borrador = vm.estado.value.borrador
        assertTrue(borrador.startsWith("Hoy amaneci cansado"), borrador)
        assertTrue(borrador.contains(pregunta.inicioDeRespuesta), borrador)
    }

    @Test
    fun una_pregunta_respondida_deja_de_ofrecerse() {
        val vm = viewModel()
        val pregunta = vm.estado.value.preguntas.first()

        vm.responder(pregunta)

        assertTrue(vm.estado.value.preguntasPendientes.none { it.clave == pregunta.clave })
    }

    @Test
    fun al_guardar_la_entrada_las_preguntas_vuelven_para_manana() {
        val vm = viewModel()
        vm.responder(vm.estado.value.preguntas.first())
        vm.actualizarBorrador("Mi presion hoy fue de 120/80")

        vm.guardar()

        assertEquals(vm.estado.value.preguntas.size, vm.estado.value.preguntasPendientes.size)
    }

    @Test
    fun sin_historial_el_diario_sigue_funcionando_como_hoja_en_blanco() {
        val vm = DiarioViewModel(DiarioRepositorioFalso(), "pac_1", RelojFijo(), historial = null)

        assertTrue(vm.estado.value.preguntas.isEmpty())
        vm.actualizarBorrador("Me duele la cabeza")
        vm.guardar()
        assertEquals(1, (vm.estado.value.entradas.size + 1))
    }
}
