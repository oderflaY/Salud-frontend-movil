package com.eter.salud.presentation.emergencia

import com.eter.salud.domain.model.Alergia
import com.eter.salud.domain.model.IdentidadSupervivencia
import com.eter.salud.domain.model.PerfilEmergenciaReducido
import com.eter.salud.domain.model.PerfilSupervivencia
import com.eter.salud.domain.nfc.DisponibilidadNfc
import com.eter.salud.domain.nfc.ErrorLecturaNfc
import com.eter.salud.domain.repository.FalloEmergencia
import com.eter.salud.domain.repository.MotivoFalloEmergencia
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Escaner de emergencia. Cada prueba describe un momento real de una atencion
 * prehospitalaria: la tarjeta se lee, se aleja a medias, esta revocada, o no hay
 * red en la ambulancia.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EscanerEmergenciaViewModelTest {

    private val idTarjeta = "C38610A8"

    @BeforeTest
    fun configurar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun perfil() = PerfilSupervivencia(
        idTarjetaRfid = idTarjeta,
        datosPersonales = IdentidadSupervivencia(
            nombre = "Alfredo",
            apellidos = "Valadez Gonzalez",
            fechaNacimiento = "1998-05-15",
        ),
        perfilEmergenciaReducido = PerfilEmergenciaReducido(
            tipoSangre = "O+",
            donadorOrganos = true,
            alergias = listOf(
                Alergia("Penicilina", "Alta (Anafilaxia)", "Cierre de vias respiratorias"),
            ),
            condicionesCriticas = listOf("Asma reactiva"),
            medicacionRescate = listOf("Salbutamol (Inhalador)"),
        ),
    )

    private fun viewModel(
        lector: LectorNfcFalso = LectorNfcFalso(),
        repositorio: PerfilEmergenciaRepositorioFalso =
            PerfilEmergenciaRepositorioFalso(Result.success(perfil())),
    ) = EscanerEmergenciaViewModel(
        lector = lector,
        repositorio = repositorio,
        reloj = RelojFijo(fecha = "2026-09-06"),
    )

    // --------------------------------------------------------- Estado de antena

    @Test
    fun al_activarse_la_pantalla_la_antena_queda_escuchando() {
        val lector = LectorNfcFalso()
        val vm = viewModel(lector)

        vm.activarEscaneo()

        assertEquals(FaseEscaneo.ESPERANDO_TARJETA, vm.estado.value.fase)
        assertTrue(lector.escuchando)
        assertEquals(1, lector.vecesIniciado)
    }

    @Test
    fun con_el_nfc_apagado_no_se_enciende_la_antena_y_se_explica_por_que() {
        val lector = LectorNfcFalso(disponibilidad = DisponibilidadNfc.DESACTIVADA)
        val vm = viewModel(lector)

        vm.activarEscaneo()

        assertEquals(FaseEscaneo.ANTENA_NO_DISPONIBLE, vm.estado.value.fase)
        assertEquals(DisponibilidadNfc.DESACTIVADA, vm.estado.value.disponibilidad)
        assertEquals(0, lector.vecesIniciado)
    }

    @Test
    fun un_dispositivo_sin_antena_lo_dice_en_lugar_de_esperar_para_siempre() {
        val vm = viewModel(LectorNfcFalso(disponibilidad = DisponibilidadNfc.NO_SOPORTADA))

        vm.activarEscaneo()

        assertEquals(FaseEscaneo.ANTENA_NO_DISPONIBLE, vm.estado.value.fase)
        assertEquals(DisponibilidadNfc.NO_SOPORTADA, vm.estado.value.disponibilidad)
    }

    @Test
    fun salir_de_la_pantalla_apaga_la_antena_para_no_gastar_bateria() {
        val lector = LectorNfcFalso()
        val vm = viewModel(lector)
        vm.activarEscaneo()

        vm.detenerEscaneo()

        assertFalse(lector.escuchando)
        assertEquals(1, lector.vecesDetenido)
        assertEquals(FaseEscaneo.INACTIVO, vm.estado.value.fase)
    }

    // ------------------------------------------------------------ Lectura feliz

    @Test
    fun una_tarjeta_leida_consulta_el_perfil_y_lo_publica() {
        val lector = LectorNfcFalso()
        val repositorio = PerfilEmergenciaRepositorioFalso(Result.success(perfil()))
        val vm = viewModel(lector, repositorio)
        vm.activarEscaneo()

        lector.simularLectura(idTarjeta)

        val estado = vm.estado.value
        assertEquals(listOf(idTarjeta), repositorio.consultas)
        assertEquals(FaseEscaneo.PERFIL_DISPONIBLE, estado.fase)
        assertEquals("Alfredo Valadez Gonzalez", estado.perfil?.datosPersonales?.nombreCompleto)
        assertEquals("O+", estado.perfil?.perfilEmergenciaReducido?.tipoSangre)
        assertNull(estado.errorConsulta)
    }

    @Test
    fun la_edad_se_calcula_para_que_el_paramedico_pueda_dosificar() {
        val lector = LectorNfcFalso()
        val vm = viewModel(lector)
        vm.activarEscaneo()

        lector.simularLectura(idTarjeta)

        assertEquals(28, vm.estado.value.edadPaciente)
    }

    @Test
    fun al_mostrar_el_perfil_se_apaga_la_antena_y_se_pide_la_vibracion_de_exito() {
        val lector = LectorNfcFalso()
        val vm = viewModel(lector)
        vm.activarEscaneo()

        lector.simularLectura(idTarjeta)

        assertFalse(lector.escuchando)
        assertTrue(vm.estado.value.confirmarConVibracion)
    }

    @Test
    fun la_vibracion_se_consume_una_sola_vez() {
        val lector = LectorNfcFalso()
        val vm = viewModel(lector)
        vm.activarEscaneo()
        lector.simularLectura(idTarjeta)

        vm.vibracionConsumida()

        assertFalse(vm.estado.value.confirmarConVibracion)
    }

    @Test
    fun el_identificador_de_la_tarjeta_se_normaliza_antes_de_consultar() {
        val lector = LectorNfcFalso()
        val repositorio = PerfilEmergenciaRepositorioFalso(Result.success(perfil()))
        val vm = viewModel(lector, repositorio)
        vm.activarEscaneo()

        lector.simularLectura(" c38610a8 ")

        assertEquals(listOf("C38610A8"), repositorio.consultas)
    }

    // ----------------------------------------------------------------- Fallos

    @Test
    fun una_tarjeta_que_se_aleja_a_media_lectura_deja_la_antena_escuchando() {
        val lector = LectorNfcFalso()
        val vm = viewModel(lector)
        vm.activarEscaneo()

        lector.simularFallo(ErrorLecturaNfc.LECTURA_INTERRUMPIDA)

        val estado = vm.estado.value
        assertEquals(FaseEscaneo.ESPERANDO_TARJETA, estado.fase)
        assertEquals(ErrorEscaneo.LECTURA_INTERRUMPIDA, estado.errorConsulta)
        assertTrue(lector.escuchando)
    }

    @Test
    fun una_tarjeta_desconocida_se_distingue_de_un_fallo_de_red() {
        val lector = LectorNfcFalso()
        val vm = viewModel(
            lector,
            PerfilEmergenciaRepositorioFalso(
                Result.failure(FalloEmergencia(MotivoFalloEmergencia.TARJETA_DESCONOCIDA)),
            ),
        )
        vm.activarEscaneo()

        lector.simularLectura(idTarjeta)

        assertEquals(FaseEscaneo.ERROR, vm.estado.value.fase)
        assertEquals(ErrorEscaneo.TARJETA_DESCONOCIDA, vm.estado.value.errorConsulta)
        assertNull(vm.estado.value.perfil)
    }

    @Test
    fun una_tarjeta_revocada_avisa_para_pedir_otra_identificacion() {
        val lector = LectorNfcFalso()
        val vm = viewModel(
            lector,
            PerfilEmergenciaRepositorioFalso(
                Result.failure(FalloEmergencia(MotivoFalloEmergencia.TARJETA_REVOCADA)),
            ),
        )
        vm.activarEscaneo()

        lector.simularLectura(idTarjeta)

        assertEquals(ErrorEscaneo.TARJETA_REVOCADA, vm.estado.value.errorConsulta)
    }

    @Test
    fun un_fallo_inesperado_se_reporta_como_falta_de_conexion() {
        val lector = LectorNfcFalso()
        val vm = viewModel(
            lector,
            PerfilEmergenciaRepositorioFalso(Result.failure(IllegalStateException("socket"))),
        )
        vm.activarEscaneo()

        lector.simularLectura(idTarjeta)

        assertEquals(ErrorEscaneo.SIN_CONEXION, vm.estado.value.errorConsulta)
    }

    @Test
    fun una_segunda_tarjeta_durante_la_consulta_no_lanza_una_consulta_paralela() {
        val lector = LectorNfcFalso()
        val repositorio = PerfilEmergenciaRepositorioFalso(Result.success(perfil()))
        val vm = viewModel(lector, repositorio)
        vm.activarEscaneo()

        lector.simularLectura(idTarjeta)
        lector.simularLectura(idTarjeta)

        assertEquals(1, repositorio.consultas.size)
    }

    // -------------------------------------------------------------- Reescaneo

    @Test
    fun escanear_otra_tarjeta_limpia_el_perfil_anterior_y_reactiva_la_antena() {
        val lector = LectorNfcFalso()
        val vm = viewModel(lector)
        vm.activarEscaneo()
        lector.simularLectura(idTarjeta)

        vm.escanearOtraTarjeta()

        val estado = vm.estado.value
        assertEquals(FaseEscaneo.ESPERANDO_TARJETA, estado.fase)
        assertNull(estado.perfil)
        assertNull(estado.errorConsulta)
        assertTrue(lector.escuchando)
        assertEquals(2, lector.vecesIniciado)
    }

    @Test
    fun tras_un_error_se_puede_reintentar_sin_recrear_la_pantalla() {
        val lector = LectorNfcFalso()
        val vm = viewModel(
            lector,
            PerfilEmergenciaRepositorioFalso(
                Result.failure(FalloEmergencia(MotivoFalloEmergencia.SIN_CONEXION)),
            ),
        )
        vm.activarEscaneo()
        lector.simularLectura(idTarjeta)
        assertEquals(FaseEscaneo.ERROR, vm.estado.value.fase)

        vm.escanearOtraTarjeta()

        assertEquals(FaseEscaneo.ESPERANDO_TARJETA, vm.estado.value.fase)
        assertNull(vm.estado.value.errorConsulta)
    }
}
