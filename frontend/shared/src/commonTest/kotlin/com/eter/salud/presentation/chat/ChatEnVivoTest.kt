package com.eter.salud.presentation.chat

import com.eter.salud.domain.model.AutorMensaje
import com.eter.salud.domain.model.MensajeChat
import com.eter.salud.domain.model.RiesgoPaciente
import com.eter.salud.presentation.chatmedico.ChatMedicoViewModel
import com.eter.salud.presentation.chatmedico.HistorialChatUiState
import com.eter.salud.presentation.onboarding.RelojFijo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * El mensaje que el medico manda desde el panel web tiene que aparecer en el
 * chat del paciente sin salir y volver a entrar (y al reves).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatEnVivoTest {

    private val idConversacion = "conv_1"

    @BeforeTest
    fun configurar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun mensaje(id: String, autor: AutorMensaje, texto: String) =
        MensajeChat(idMensaje = id, autor = autor, texto = texto, instante = "2026-09-16T19:15:00Z")

    @Test
    fun el_paciente_ve_el_mensaje_del_medico_en_cuanto_llega_y_queda_leido() = runTest(UnconfinedTestDispatcher()) {
        val repositorio = ChatRepositorioFalso(historial = Result.success(listOf(mensaje("m1", AutorMensaje.PACIENTE, "Me duele el estomago"))))
        val vm = ChatViewModel(repositorio, idConversacion, "Dr. Carlos Silva", RelojFijo())
        val escucha = launch { vm.mantenerAlDia() }
        val leidasAntes = repositorio.vecesMarcadaLeida

        repositorio.historial = Result.success(
            listOf(
                mensaje("m1", AutorMensaje.PACIENTE, "Me duele el estomago"),
                mensaje("m2", AutorMensaje.MEDICO, "Lo mejor seria que vinieras a consulta"),
            ),
        )
        repositorio.simularCambio()

        assertEquals(listOf("m1", "m2"), vm.estado.value.mensajes.map { it.idMensaje })
        assertEquals(leidasAntes + 1, repositorio.vecesMarcadaLeida)
        escucha.cancel()
    }

    @Test
    fun un_refresco_no_borra_lo_que_el_paciente_esta_escribiendo_ni_duplica_su_mensaje() = runTest(UnconfinedTestDispatcher()) {
        val repositorio = ChatRepositorioFalso(historial = Result.success(emptyList()))
        val vm = ChatViewModel(repositorio, idConversacion, "Dr. Carlos Silva", RelojFijo())
        val escucha = launch { vm.mantenerAlDia() }

        vm.actualizarTexto("okey")
        vm.enviarMensaje()
        val confirmado = vm.estado.value.mensajes.single { it.autor == AutorMensaje.PACIENTE }
        repositorio.historial = Result.success(vm.estado.value.mensajes.filter { it.idMensaje == confirmado.idMensaje })
        vm.actualizarTexto("sigo con dolor")
        repositorio.simularCambio()

        assertEquals(1, vm.estado.value.mensajes.count { it.texto == "okey" })
        assertEquals("sigo con dolor", vm.estado.value.textoEnCurso)
        escucha.cancel()
    }

    @Test
    fun si_el_refresco_falla_se_queda_lo_que_ya_se_veia() = runTest(UnconfinedTestDispatcher()) {
        val repositorio = ChatRepositorioFalso(historial = Result.success(listOf(mensaje("m1", AutorMensaje.MEDICO, "Hola"))))
        val vm = ChatViewModel(repositorio, idConversacion, "Dr. Carlos Silva", RelojFijo())
        val escucha = launch { vm.mantenerAlDia() }

        repositorio.historial = Result.failure(IllegalStateException("sin red"))
        repositorio.simularCambio()

        assertEquals(listOf("m1"), vm.estado.value.mensajes.map { it.idMensaje })
        escucha.cancel()
    }

    @Test
    fun el_medico_ve_el_mensaje_nuevo_del_paciente() = runTest(UnconfinedTestDispatcher()) {
        val repositorio = ChatRepositorioFalso(historial = Result.success(listOf(mensaje("m1", AutorMensaje.MEDICO, "Como sigue?"))))
        val vm = ChatMedicoViewModel(repositorio, idConversacion, "Juan Perez", RiesgoPaciente.MEDIO, RelojFijo())
        val escucha = launch { vm.mantenerAlDia() }

        repositorio.historial = Result.success(
            listOf(mensaje("m1", AutorMensaje.MEDICO, "Como sigue?"), mensaje("m2", AutorMensaje.PACIENTE, "okey")),
        )
        repositorio.simularCambio()

        val historial = assertIs<HistorialChatUiState.ConMensajes>(vm.estado.value.historial)
        assertEquals(listOf("m1", "m2"), historial.mensajes.map { it.idMensaje })
        escucha.cancel()
    }
}
