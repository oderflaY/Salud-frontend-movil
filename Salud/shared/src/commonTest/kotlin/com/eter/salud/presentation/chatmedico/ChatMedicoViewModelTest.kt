package com.eter.salud.presentation.chatmedico

import com.eter.salud.domain.model.AutorMensaje
import com.eter.salud.domain.model.MensajeChat
import com.eter.salud.domain.model.RiesgoPaciente
import com.eter.salud.presentation.chat.ChatRepositorioFalso
import com.eter.salud.presentation.onboarding.RelojFijo
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
 * Chat desde el perfil del medico. Lo critico aqui es que el medico escriba
 * COMO medico sobre la misma conversacion que ve el paciente: si el autor se
 * diera por supuesto, sus respuestas se pintarian del lado equivocado.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatMedicoViewModelTest {

    private val idConversacion = "conv_pac_01H8X9A_doc_889900A"

    @BeforeTest
    fun configurar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun viewModel(repositorio: ChatRepositorioFalso = ChatRepositorioFalso()) =
        ChatMedicoViewModel(
            repositorio = repositorio,
            idConversacion = idConversacion,
            nombrePaciente = "Juan Perez Gomez",
            riesgoPaciente = RiesgoPaciente.ALTO,
            reloj = RelojFijo(),
        )

    // ------------------------------------------------------------------ Estado

    @Test
    fun el_contexto_clinico_viaja_en_la_sesion_y_la_conversacion_se_carga_sola() {
        val vm = viewModel()

        val estado = vm.estado.value
        assertEquals("Juan Perez Gomez", estado.nombrePaciente)
        assertEquals(RiesgoPaciente.ALTO, estado.riesgoPaciente)
        // La carga cuelga del ciclo de vida del ViewModel, no de la Vista.
        assertIs<HistorialChatUiState.ConMensajes>(estado.historial)
    }

    @Test
    fun cargar_publica_el_historial_de_la_conversacion() {
        val vm = viewModel()


        val historial = assertIs<HistorialChatUiState.ConMensajes>(vm.estado.value.historial)
        assertTrue(historial.mensajes.isNotEmpty())
    }

    @Test
    fun una_conversacion_sin_mensajes_se_publica_como_lista_vacia_y_no_como_error() {
        val vm = viewModel(ChatRepositorioFalso(historial = Result.success(emptyList())))


        val historial = assertIs<HistorialChatUiState.ConMensajes>(vm.estado.value.historial)
        assertTrue(historial.mensajes.isEmpty())
    }

    @Test
    fun si_el_historial_falla_se_publica_el_estado_de_error() {
        val vm = viewModel(ChatRepositorioFalso(historial = Result.failure(IllegalStateException("sin red"))))


        val error = assertIs<HistorialChatUiState.Error>(vm.estado.value.historial)
        assertEquals(ErrorChatMedico.SIN_CONEXION, error.motivo)
    }

    @Test
    fun una_carga_posterior_a_la_del_init_vuelve_a_intentarlo_en_vez_de_quedar_bloqueada() {
        val repositorio = ChatRepositorioFalso()
        val vm = viewModel(repositorio)

        vm.cargar()

        assertIs<HistorialChatUiState.ConMensajes>(vm.estado.value.historial)
    }

    // ------------------------------------------------------------------ Envio

    @Test
    fun el_medico_escribe_como_medico_y_no_como_paciente() {
        val repositorio = ChatRepositorioFalso()
        val vm = viewModel(repositorio)

        vm.actualizarTexto("Acude a urgencias si la fiebre pasa de 39 grados")
        vm.enviarMensaje()

        assertEquals(AutorMensaje.MEDICO, repositorio.ultimoAutorEnviado)
        assertEquals(AutorMensaje.MEDICO, vm.estado.value.mensajes.last().autor)
    }

    @Test
    fun enviar_un_mensaje_lo_muestra_de_inmediato_y_limpia_el_campo() {
        val vm = viewModel()

        vm.actualizarTexto("Manten reposo")
        vm.enviarMensaje()

        assertEquals("", vm.estado.value.textoEnCurso)
        assertEquals("Manten reposo", vm.estado.value.mensajes.last().texto)
    }

    @Test
    fun al_confirmarse_el_envio_el_mensaje_optimista_se_reemplaza_por_el_definitivo() {
        val vm = viewModel()
        vm.actualizarTexto("Agenda cita presencial")

        vm.enviarMensaje()

        val delMedico = vm.estado.value.mensajes.filter { it.autor == AutorMensaje.MEDICO }
        assertTrue(delMedico.none { it.idMensaje.startsWith("local_medico_") })
    }

    @Test
    fun un_texto_en_blanco_no_envia_nada() {
        val repositorio = ChatRepositorioFalso()
        val vm = viewModel(repositorio)

        vm.actualizarTexto("   ")
        vm.enviarMensaje()

        assertTrue(repositorio.mensajesEnviados.isEmpty())
    }

    @Test
    fun sin_historial_descargado_no_se_envia_para_no_perder_el_texto() {
        val repositorio = ChatRepositorioFalso(historial = Result.failure(IllegalStateException("sin red")))
        val vm = viewModel(repositorio)

        vm.actualizarTexto("Mensaje que no debe salir")
        vm.enviarMensaje()

        assertTrue(repositorio.mensajesEnviados.isEmpty())
        assertEquals("Mensaje que no debe salir", vm.estado.value.textoEnCurso)
    }

    @Test
    fun si_el_envio_falla_el_mensaje_optimista_se_retira_y_el_texto_vuelve_al_campo() {
        val repositorio = ChatRepositorioFalso(
            resultadoEnvio = Result.failure(IllegalStateException("sin red")),
        )
        val vm = viewModel(repositorio)
        val totalPrevio = vm.estado.value.mensajes.size

        vm.actualizarTexto("Este mensaje no debe quedar")
        vm.enviarMensaje()

        val estado = vm.estado.value
        assertEquals(totalPrevio, estado.mensajes.size)
        assertTrue(estado.errorEnvio)
        assertEquals("Este mensaje no debe quedar", estado.textoEnCurso)
    }

    @Test
    fun el_medico_no_recibe_ninguna_respuesta_automatica() {
        val repositorio = ChatRepositorioFalso()
        val vm = viewModel(repositorio)

        vm.actualizarTexto("Primer mensaje del medico")
        vm.enviarMensaje()

        assertEquals(0, repositorio.vecesRespuestaAutomatica)
    }

    // -------------------------------------------------------- Respuestas rapidas

    @Test
    fun una_respuesta_rapida_llena_el_campo_pero_no_la_envia_sola() {
        val repositorio = ChatRepositorioFalso()
        val vm = viewModel(repositorio)

        vm.usarRespuestaRapida("Acude a urgencias")

        assertEquals("Acude a urgencias", vm.estado.value.textoEnCurso)
        assertTrue(repositorio.mensajesEnviados.isEmpty())
        assertTrue(vm.estado.value.puedeEnviar)
    }

    @Test
    fun escribir_de_nuevo_tras_un_fallo_limpia_el_aviso_de_error() {
        val repositorio = ChatRepositorioFalso(
            resultadoEnvio = Result.failure(IllegalStateException("sin red")),
        )
        val vm = viewModel(repositorio)
        vm.actualizarTexto("Hola")
        vm.enviarMensaje()
        assertTrue(vm.estado.value.errorEnvio)

        vm.actualizarTexto("Hola de nuevo")

        assertFalse(vm.estado.value.errorEnvio)
    }
}
