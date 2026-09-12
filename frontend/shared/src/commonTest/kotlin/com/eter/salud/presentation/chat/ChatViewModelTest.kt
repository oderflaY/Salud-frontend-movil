package com.eter.salud.presentation.chat

import com.eter.salud.domain.model.Adjunto
import com.eter.salud.domain.model.AutorMensaje
import com.eter.salud.domain.model.MensajeChat
import com.eter.salud.domain.model.TipoAdjunto
import com.eter.salud.domain.model.TipoMensaje
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
import kotlin.test.assertTrue

/**
 * Chat de orientacion de primera vista. Cubre el flujo completo: la
 * conversacion ya trae el bloque de Orientacion Inicial al abrirse, el envio
 * es optimista (la burbuja aparece antes de que el backend confirme), y solo
 * el primer mensaje del paciente dispara la respuesta automatica de cortesia.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

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
        ChatViewModel(
            repositorio = repositorio,
            idConversacion = idConversacion,
            nombreMedico = "Dra. Elena Ruiz Santos",
            reloj = RelojFijo(),
        )

    // ------------------------------------------------------------------ Carga

    @Test
    fun al_abrir_el_chat_ya_esta_el_bloque_de_orientacion_inicial() {
        val vm = viewModel()


        val estado = vm.estado.value
        assertFalse(estado.cargando)
        assertEquals(1, estado.mensajes.size)
        assertEquals(TipoMensaje.ORIENTACION_INICIAL, estado.mensajes.single().tipo)
        assertEquals(AutorMensaje.MEDICO, estado.mensajes.single().autor)
    }

    @Test
    fun si_el_historial_falla_se_reporta_el_error_de_carga() {
        val vm = viewModel(ChatRepositorioFalso(historial = Result.failure(IllegalStateException("sin red"))))


        assertTrue(vm.estado.value.errorCarga)
        assertFalse(vm.estado.value.cargando)
    }

    // ------------------------------------------------------------------ Envio

    @Test
    fun un_texto_en_blanco_no_envia_nada() {
        val repositorio = ChatRepositorioFalso()
        val vm = viewModel(repositorio)

        vm.actualizarTexto("   ")
        vm.enviarMensaje()

        assertTrue(repositorio.mensajesEnviados.isEmpty())
    }

    @Test
    fun enviar_un_mensaje_lo_muestra_de_inmediato_y_limpia_el_campo() {
        val vm = viewModel()

        vm.actualizarTexto("Me duele la cabeza desde ayer")
        vm.enviarMensaje()

        val estado = vm.estado.value
        assertEquals("", estado.textoEnCurso)
        // No se usa "el ultimo mensaje": al ser el primero del paciente, la
        // respuesta automatica de cortesia se agrega justo despues y quedaria
        // al final.
        val mensajeDelPaciente = estado.mensajes.single { it.autor == AutorMensaje.PACIENTE }
        assertEquals("Me duele la cabeza desde ayer", mensajeDelPaciente.texto)
    }

    @Test
    fun al_confirmarse_el_envio_el_mensaje_optimista_se_reemplaza_por_el_definitivo() {
        val vm = viewModel()
        vm.actualizarTexto("Tengo fiebre")

        vm.enviarMensaje()

        val mensajesDelPaciente = vm.estado.value.mensajes.filter { it.autor == AutorMensaje.PACIENTE }
        assertEquals(1, mensajesDelPaciente.size)
        assertTrue(mensajesDelPaciente.single().idMensaje.startsWith("msg_confirmado_"))
    }

    @Test
    fun el_primer_mensaje_del_paciente_dispara_la_respuesta_automatica_de_cortesia() {
        val repositorio = ChatRepositorioFalso()
        val vm = viewModel(repositorio)

        vm.actualizarTexto("Hola, necesito orientacion")
        vm.enviarMensaje()

        assertEquals(1, repositorio.vecesRespuestaAutomatica)
        assertEquals(AutorMensaje.MEDICO, vm.estado.value.mensajes.last().autor)
        assertFalse(vm.estado.value.medicoEscribiendo)
    }

    @Test
    fun un_segundo_mensaje_del_paciente_no_repite_la_respuesta_automatica() {
        val repositorio = ChatRepositorioFalso()
        val vm = viewModel(repositorio)
        vm.actualizarTexto("Primer mensaje")
        vm.enviarMensaje()

        vm.actualizarTexto("Segundo mensaje")
        vm.enviarMensaje()

        assertEquals(1, repositorio.vecesRespuestaAutomatica)
        assertEquals(2, vm.estado.value.mensajes.count { it.autor == AutorMensaje.PACIENTE })
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
        // Un envio fallido tampoco cuenta como "primer mensaje" ya enviado.
        assertEquals(0, repositorio.vecesRespuestaAutomatica)
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

    // -------------------------------------------------------------- Adjuntos

    private val adjuntoDePrueba = Adjunto(
        idAdjunto = "adj_1",
        tipo = TipoAdjunto.FOTO,
        nombre = "receta.jpg",
        rutaLocal = "/datos/adjuntos/conv_x/adj_1.jpg",
        tipoMime = "image/jpeg",
    )

    @Test
    fun adjuntar_deja_el_archivo_listo_y_habilita_enviar_aunque_no_haya_texto() {
        val vm = viewModel()

        vm.adjuntar(adjuntoDePrueba)

        assertEquals(adjuntoDePrueba, vm.estado.value.adjuntoEnCurso)
        assertTrue(vm.estado.value.puedeEnviar)
    }

    @Test
    fun quitar_el_adjunto_lo_descarta_sin_tocar_el_texto() {
        val vm = viewModel()
        vm.actualizarTexto("Aqui esta")
        vm.adjuntar(adjuntoDePrueba)

        vm.quitarAdjunto()

        assertEquals(null, vm.estado.value.adjuntoEnCurso)
        assertEquals("Aqui esta", vm.estado.value.textoEnCurso)
    }

    @Test
    fun enviar_con_adjunto_y_sin_texto_manda_el_mensaje_igual() {
        val repositorio = ChatRepositorioFalso()
        val vm = viewModel(repositorio)
        vm.adjuntar(adjuntoDePrueba)

        vm.enviarMensaje()

        assertEquals(adjuntoDePrueba, repositorio.ultimoAdjuntoEnviado)
        assertEquals("", repositorio.mensajesEnviados.single())
        // La burbuja optimista ya lleva el adjunto, antes de que el backend
        // confirme. Se busca el mensaje DEL PACIENTE y no el ultimo de la
        // lista: al ser el primero, dispara ademas la cortesia automatica del
        // medico, que queda despues en la conversacion.
        val propio = vm.estado.value.mensajes.single { it.autor == AutorMensaje.PACIENTE }
        assertEquals(adjuntoDePrueba, propio.adjunto)
        // Y el campo queda limpio para el siguiente mensaje.
        assertEquals(null, vm.estado.value.adjuntoEnCurso)
    }

    @Test
    fun enviar_texto_y_adjunto_juntos_los_manda_en_el_mismo_mensaje() {
        val repositorio = ChatRepositorioFalso()
        val vm = viewModel(repositorio)
        vm.actualizarTexto("Aqui tienes mi receta")
        vm.adjuntar(adjuntoDePrueba)

        vm.enviarMensaje()

        val propio = vm.estado.value.mensajes.single { it.autor == AutorMensaje.PACIENTE }
        assertEquals("Aqui tienes mi receta", propio.texto)
        assertEquals(adjuntoDePrueba, propio.adjunto)
    }

    @Test
    fun si_el_envio_con_adjunto_falla_el_archivo_vuelve_al_campo() {
        val repositorio = ChatRepositorioFalso(
            resultadoEnvio = Result.failure(IllegalStateException("sin red")),
        )
        val vm = viewModel(repositorio)
        vm.adjuntar(adjuntoDePrueba)

        vm.enviarMensaje()

        assertTrue(vm.estado.value.errorEnvio)
        // Un fallo de red no puede obligar a elegir el archivo otra vez.
        assertEquals(adjuntoDePrueba, vm.estado.value.adjuntoEnCurso)
    }

    @Test
    fun sin_texto_ni_adjunto_enviar_no_hace_nada() {
        val repositorio = ChatRepositorioFalso()
        val vm = viewModel(repositorio)
        val totalPrevio = vm.estado.value.mensajes.size

        vm.enviarMensaje()

        assertEquals(totalPrevio, vm.estado.value.mensajes.size)
        assertTrue(repositorio.mensajesEnviados.isEmpty())
    }
}
