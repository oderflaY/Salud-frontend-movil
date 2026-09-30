package com.eter.salud.presentation.chat

import com.eter.salud.domain.model.AutorMensaje
import com.eter.salud.domain.model.MensajeChat
import com.eter.salud.domain.model.RiesgoPaciente
import com.eter.salud.data.preferencias.PreferenciasDeLectura
import com.eter.salud.domain.repository.TraduccionRepositorio
import com.eter.salud.presentation.chatmedico.ChatMedicoViewModel
import com.eter.salud.presentation.comun.EstadoTraduccion
import com.eter.salud.presentation.onboarding.RelojFijo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

private class TraductorFalso(
    private val resultado: Result<String> = Result.success("My stomach still hurts"),
) : TraduccionRepositorio {
    var peticiones = mutableListOf<Pair<String, String>>()
        private set

    override suspend fun traducir(texto: String, idiomaDestino: String): Result<String> {
        peticiones += texto to idiomaDestino
        return resultado
    }
}

/** La preferencia "traducir siempre", en memoria y cambiable a mitad de la prueba. */
private class PreferenciasFalsas(traducirSiempre: Boolean) : PreferenciasDeLectura {
    private val flujo = MutableStateFlow(traducirSiempre)
    var guardado = false
        private set

    override val traducirSiempre = flujo.asStateFlow()

    override suspend fun cambiarTraducirSiempre(activo: Boolean) {
        guardado = true
        flujo.value = activo
    }

    /** Simula el cambio hecho desde otra pantalla (Configuracion). */
    fun cambiar(activo: Boolean) {
        flujo.value = activo
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TraduccionDeChatTest {

    private val mensajeDelMedico = MensajeChat(
        idMensaje = "m1",
        autor = AutorMensaje.MEDICO,
        texto = "Todavia me duele el estomago",
        instante = "2026-09-17T10:00:00Z",
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
        traductor: TraduccionRepositorio?,
        preferencias: PreferenciasDeLectura? = null,
    ) = ChatViewModel(
        repositorio = ChatRepositorioFalso(historial = Result.success(listOf(mensajeDelMedico))),
        idConversacion = "conv_1",
        nombreMedico = "Dr. Carlos Silva",
        reloj = RelojFijo(),
        traduccion = traductor,
        idiomaDeLectura = "en",
        preferencias = preferencias,
    )

    @Test
    fun sin_traduccion_en_el_backend_la_accion_no_se_ofrece() {
        val vm = viewModel(null)

        vm.traducir("m1")

        assertFalse(vm.estado.value.puedeTraducir)
        assertNull(vm.estado.value.traducciones["m1"])
    }

    @Test
    fun traduce_al_idioma_de_quien_lee_y_deja_ver_el_original() {
        val traductor = TraductorFalso()
        val vm = viewModel(traductor)

        vm.traducir("m1")

        assertEquals(listOf("Todavia me duele el estomago" to "en"), traductor.peticiones)
        val lista = assertIs<EstadoTraduccion.Lista>(vm.estado.value.traducciones["m1"])
        assertEquals("My stomach still hurts", lista.texto)
        assertFalse(lista.mostrandoOriginal)

        vm.alternarTraduccion("m1")
        assertTrue(assertIs<EstadoTraduccion.Lista>(vm.estado.value.traducciones["m1"]).mostrandoOriginal)
    }

    @Test
    fun un_fallo_deja_reintentar_sin_perder_el_mensaje() {
        val vm = viewModel(TraductorFalso(Result.failure(IllegalStateException("sin red"))))

        vm.traducir("m1")

        assertEquals(EstadoTraduccion.Fallida, vm.estado.value.traducciones["m1"])
        assertEquals("Todavia me duele el estomago", vm.estado.value.mensajes.single().texto)
    }

    @Test
    fun si_el_mensaje_ya_estaba_en_tu_idioma_se_dice_y_no_es_un_fallo() {
        val vm = viewModel(TraductorFalso(Result.success("Todavia me duele el estomago")))

        vm.traducir("m1")

        assertEquals(EstadoTraduccion.SinCambios, vm.estado.value.traducciones["m1"])
    }

    @Test
    fun con_la_traduccion_automatica_encendida_no_hay_que_pedir_nada() {
        val traductor = TraductorFalso()
        val preferencias = PreferenciasFalsas(traducirSiempre = true)
        val vm = viewModel(traductor, preferencias)

        assertTrue(vm.estado.value.traduccionAutomatica)
        assertEquals("My stomach still hurts", assertIs<EstadoTraduccion.Lista>(vm.estado.value.traducciones["m1"]).texto)
        assertEquals(1, traductor.peticiones.size)
    }

    @Test
    fun apagarla_devuelve_los_originales_sin_volver_a_traducir() {
        val traductor = TraductorFalso()
        val preferencias = PreferenciasFalsas(traducirSiempre = true)
        val vm = viewModel(traductor, preferencias)

        preferencias.cambiar(false)

        assertFalse(vm.estado.value.traduccionAutomatica)
        assertTrue(assertIs<EstadoTraduccion.Lista>(vm.estado.value.traducciones["m1"]).mostrandoOriginal)
        assertEquals(1, traductor.peticiones.size)
    }

    @Test
    fun el_interruptor_guarda_la_preferencia_en_el_telefono() {
        val preferencias = PreferenciasFalsas(traducirSiempre = false)
        val vm = viewModel(TraductorFalso(), preferencias)

        vm.cambiarTraduccionAutomatica(true)

        assertTrue(preferencias.guardado)
        assertTrue(vm.estado.value.traduccionAutomatica)
    }

    @Test
    fun sin_traduccion_en_el_backend_la_preferencia_no_hace_nada() {
        val preferencias = PreferenciasFalsas(traducirSiempre = true)
        val vm = viewModel(null, preferencias)

        assertFalse(vm.estado.value.traduccionAutomatica)
        assertTrue(vm.estado.value.traducciones.isEmpty())
    }

    @Test
    fun un_mensaje_que_no_esta_en_pantalla_no_se_traduce() {
        val traductor = TraductorFalso()
        val vm = viewModel(traductor)

        vm.traducir("no_existe")

        assertTrue(traductor.peticiones.isEmpty())
    }

    @Test
    fun el_medico_traduce_los_mensajes_del_paciente() {
        val traductor = TraductorFalso(Result.success("My stomach hurts"))
        val vm = ChatMedicoViewModel(
            repositorio = ChatRepositorioFalso(
                historial = Result.success(
                    listOf(mensajeDelMedico.copy(autor = AutorMensaje.PACIENTE, texto = "Me duele el estomago")),
                ),
            ),
            idConversacion = "conv_1",
            nombrePaciente = "Juan Perez",
            riesgoPaciente = RiesgoPaciente.BAJO,
            reloj = RelojFijo(),
            traduccion = traductor,
            idiomaDeLectura = "en",
        )

        vm.traducir("m1")

        assertTrue(vm.estado.value.puedeTraducir)
        assertEquals("My stomach hurts", assertIs<EstadoTraduccion.Lista>(vm.estado.value.traducciones["m1"]).texto)
    }
}
