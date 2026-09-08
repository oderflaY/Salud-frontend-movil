package com.eter.salud.presentation.home

import com.eter.salud.domain.model.DatosPersonales
import com.eter.salud.domain.model.DispositivoRfid
import com.eter.salud.domain.model.EstadoToma
import com.eter.salud.domain.model.PacienteDto
import com.eter.salud.domain.model.ResumenAdherencia
import com.eter.salud.domain.model.TomaDelDia
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
 * Panel principal del paciente: identidad, tomas del dia y adherencia semanal.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val idPaciente = "pac_01H8X9A"

    @BeforeTest
    fun configurar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun toma(
        id: String,
        hora: String,
        estado: EstadoToma = EstadoToma.PENDIENTE,
        medicamento: String = "Losartan",
    ) = TomaDelDia(
        idToma = id,
        idTratamiento = "trt_001",
        medicamento = medicamento,
        dosis = "50mg",
        horaProgramada = hora,
        estado = estado,
    )

    private fun perfil() = PacienteDto(
        idPaciente = idPaciente,
        datosPersonales = DatosPersonales(
            nombre = "Juan",
            apellidos = "Perez Gomez",
            fechaNacimiento = "1985-04-12",
            genero = "Masculino",
            telefono = "+526181234567",
        ),
        dispositivosRfid = listOf(
            DispositivoRfid(
                idTarjetaRfid = "rfid_hash_abc123",
                estado = "activa",
                fechaAsignacion = "2026-01-10T10:00:00Z",
            ),
        ),
    )

    private fun viewModel(
        historial: HistorialParaInicioFalso = HistorialParaInicioFalso(Result.success(perfil())),
        adherencia: AdherenciaRepositorioFalso = AdherenciaRepositorioFalso(),
        perfilPendiente: Boolean = false,
    ) = HomeViewModel(
        historial = historial,
        adherencia = adherencia,
        idPaciente = idPaciente,
        perfilEmergenciaPendiente = perfilPendiente,
        reloj = RelojFijo(),
    )

    // ------------------------------------------------------------------ Carga

    @Test
    fun la_cabecera_toma_el_nombre_y_el_estado_de_la_tarjeta_del_expediente() {
        val vm = viewModel()

        vm.cargar()

        val estado = vm.estado.value
        assertEquals("Juan", estado.nombrePaciente)
        assertTrue(estado.tarjetaRfidActiva)
        assertFalse(estado.cargando)
        assertFalse(estado.errorCarga)
    }

    @Test
    fun una_tarjeta_extraviada_no_cuenta_como_activa() {
        val perfilConTarjetaRevocada = perfil().copy(
            dispositivosRfid = listOf(
                DispositivoRfid(
                    idTarjetaRfid = "rfid_hash_abc123",
                    estado = "extraviada",
                    fechaAsignacion = "2026-01-10T10:00:00Z",
                    fechaRevocacion = "2026-08-01T10:00:00Z",
                ),
            ),
        )
        val vm = viewModel(HistorialParaInicioFalso(Result.success(perfilConTarjetaRevocada)))

        vm.cargar()

        assertFalse(vm.estado.value.tarjetaRfidActiva)
    }

    @Test
    fun las_tomas_del_dia_se_ordenan_por_hora_programada() {
        val adherencia = AdherenciaRepositorioFalso(
            tomas = Result.success(
                listOf(toma("log_2", "20:00"), toma("log_1", "08:00")),
            ),
        )
        val vm = viewModel(adherencia = adherencia)

        vm.cargar()

        assertEquals(
            listOf("log_1", "log_2"),
            vm.estado.value.tomasDelDia.map { it.idToma },
        )
        assertEquals(2, vm.estado.value.tomasPendientes)
        assertFalse(vm.estado.value.todasLasTomasRegistradas)
    }

    @Test
    fun si_el_backend_falla_la_pantalla_lo_dice_en_lugar_de_quedarse_vacia() {
        val vm = viewModel(
            historial = HistorialParaInicioFalso(Result.failure(IllegalStateException("sin red"))),
        )

        vm.cargar()

        assertTrue(vm.estado.value.errorCarga)
        assertFalse(vm.estado.value.cargando)
    }

    // -------------------------------------------------------- Registro de tomas

    @Test
    fun marcar_una_toma_como_tomada_muta_su_estado_y_refresca_la_adherencia() {
        val adherencia = AdherenciaRepositorioFalso(
            tomas = Result.success(listOf(toma("log_1", "08:00"))),
            resumenes = listOf(
                Result.success(ResumenAdherencia(tomasProgramadas = 14, tomasCumplidas = 6)),
                Result.success(ResumenAdherencia(tomasProgramadas = 14, tomasCumplidas = 7)),
            ),
        )
        val vm = viewModel(adherencia = adherencia)
        vm.cargar()
        assertEquals(6, vm.estado.value.resumenSemanal?.tomasCumplidas)

        vm.marcarTomada("log_1")

        val estado = vm.estado.value
        assertEquals(EstadoToma.TOMADO, estado.tomasDelDia.single().estado)
        assertEquals(listOf("log_1" to EstadoToma.TOMADO), adherencia.registros)
        assertEquals(7, estado.resumenSemanal?.tomasCumplidas)
        assertEquals(0, estado.tomasPendientes)
        assertTrue(estado.todasLasTomasRegistradas)
        assertNull(estado.tomaEnCurso)
    }

    @Test
    fun omitir_una_toma_la_registra_como_omitida() {
        val adherencia = AdherenciaRepositorioFalso(
            tomas = Result.success(listOf(toma("log_1", "08:00"))),
        )
        val vm = viewModel(adherencia = adherencia)
        vm.cargar()

        vm.omitirToma("log_1")

        assertEquals(EstadoToma.OMITIDO, vm.estado.value.tomasDelDia.single().estado)
        assertEquals(listOf("log_1" to EstadoToma.OMITIDO), adherencia.registros)
    }

    @Test
    fun si_el_registro_falla_la_toma_vuelve_a_pendiente_y_se_avisa() {
        val adherencia = AdherenciaRepositorioFalso(
            tomas = Result.success(listOf(toma("log_1", "08:00"))),
            resultadoRegistro = Result.failure(IllegalStateException("sin red")),
        )
        val vm = viewModel(adherencia = adherencia)
        vm.cargar()

        vm.marcarTomada("log_1")

        val estado = vm.estado.value
        assertEquals(EstadoToma.PENDIENTE, estado.tomasDelDia.single().estado)
        assertTrue(estado.errorRegistroToma)
        assertNull(estado.tomaEnCurso)
    }

    @Test
    fun una_toma_ya_registrada_no_se_vuelve_a_enviar() {
        val adherencia = AdherenciaRepositorioFalso(
            tomas = Result.success(listOf(toma("log_1", "08:00", EstadoToma.TOMADO))),
        )
        val vm = viewModel(adherencia = adherencia)
        vm.cargar()

        vm.marcarTomada("log_1")

        assertTrue(adherencia.registros.isEmpty())
    }

    @Test
    fun una_toma_inexistente_no_provoca_ninguna_llamada() {
        val adherencia = AdherenciaRepositorioFalso(
            tomas = Result.success(listOf(toma("log_1", "08:00"))),
        )
        val vm = viewModel(adherencia = adherencia)
        vm.cargar()

        vm.marcarTomada("log_inexistente")

        assertTrue(adherencia.registros.isEmpty())
    }

    // ------------------------------------------------------------- Adherencia

    @Test
    fun una_semana_sin_tomas_programadas_no_divide_entre_cero() {
        val vm = viewModel()

        vm.cargar()

        assertEquals(0, vm.estado.value.resumenSemanal?.porcentaje)
        assertTrue(vm.estado.value.resumenSemanal?.sinDatos == true)
    }

    @Test
    fun el_porcentaje_de_adherencia_se_redondea_al_entero_mas_cercano() {
        assertEquals(50, ResumenAdherencia(tomasProgramadas = 14, tomasCumplidas = 7).porcentaje)
        assertEquals(64, ResumenAdherencia(tomasProgramadas = 14, tomasCumplidas = 9).porcentaje)
        assertEquals(100, ResumenAdherencia(tomasProgramadas = 3, tomasCumplidas = 3).porcentaje)
    }

    // ---------------------------------------------------------- Perfil y accesos

    @Test
    fun terminar_el_onboarding_retira_el_aviso_sin_recrear_la_pantalla() {
        val vm = viewModel(perfilPendiente = true)
        vm.cargar()
        assertTrue(vm.estado.value.perfilEmergenciaPendiente)

        vm.marcarPerfilCompletado()

        assertFalse(vm.estado.value.perfilEmergenciaPendiente)
    }

    @Test
    fun los_accesos_rapidos_son_siempre_los_mismos_cuatro_y_en_el_mismo_orden() {
        val vm = viewModel()

        assertEquals(
            listOf(
                AccesoRapido.TARJETA_RFID,
                AccesoRapido.DIARIO_SINTOMAS,
                AccesoRapido.MIS_MEDICOS,
                AccesoRapido.HISTORIAL,
            ),
            vm.estado.value.accesosRapidos,
        )
    }
}
