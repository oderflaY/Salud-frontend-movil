package com.eter.salud.presentation.home

import com.eter.salud.domain.model.DatosPersonales
import com.eter.salud.domain.model.DiaDeAdherencia
import com.eter.salud.domain.model.DispositivoRfid
import com.eter.salud.domain.model.EstadoDia
import com.eter.salud.domain.model.EstadoToma
import com.eter.salud.domain.model.PacienteDto
import com.eter.salud.domain.model.RecordatorioMedicacion
import com.eter.salud.domain.model.ResumenAdherencia
import com.eter.salud.domain.model.TomaDelDia
import com.eter.salud.presentation.onboarding.RelojFijo
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

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

/**
 * Adherencia del dia y franja semanal.
 *
 * Lo que se fija aqui es la REGLA, no el numero: omitir una toma no puede subir
 * el anillo, y un dia en curso no puede pintarse como incumplido. Las dos cosas
 * son juicios sobre el paciente, y equivocarse en ellas es lo que hace que la
 * gente deje de abrir la pantalla.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdherenciaDiariaTest {

    @BeforeTest
    fun configurar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun estadoCon(tomas: List<TomaDelDia>) = HomeUiState(
        idPaciente = "pac_01H8X9A",
        tomasDelDia = tomas,
    )

    private fun toma(id: String, estado: EstadoToma) = TomaDelDia(
        idToma = id,
        idTratamiento = "trat_1",
        medicamento = "Paracetamol",
        dosis = "500 mg",
        horaProgramada = "08:00",
        estado = estado,
    )

    @Test
    fun sin_tomas_programadas_la_adherencia_del_dia_es_cero_y_no_revienta() {
        assertEquals(0, estadoCon(emptyList()).adherenciaDeHoy)
    }

    @Test
    fun todas_las_tomas_cumplidas_dan_el_cien_por_ciento() {
        val estado = estadoCon(
            listOf(toma("1", EstadoToma.TOMADO), toma("2", EstadoToma.TOMADO_TARDE)),
        )

        assertEquals(100, estado.adherenciaDeHoy)
    }

    @Test
    fun omitir_una_toma_NO_sube_la_adherencia() {
        // El paciente hizo su parte informando, pero la pastilla no se tomo. Un
        // anillo que subiera al omitir premiaria lo contrario de lo que busca.
        val estado = estadoCon(
            listOf(toma("1", EstadoToma.TOMADO), toma("2", EstadoToma.OMITIDO)),
        )

        assertEquals(50, estado.adherenciaDeHoy)
    }

    @Test
    fun solo_las_tomas_pendientes_merecen_recordatorio() {
        val estado = estadoCon(
            listOf(
                toma("1", EstadoToma.TOMADO),
                toma("2", EstadoToma.PENDIENTE),
                toma("3", EstadoToma.OMITIDO),
            ),
        )

        assertEquals(listOf("2"), estado.tomasPorRecordar.map { it.idToma })
    }

    @Test
    fun un_dia_en_curso_nunca_se_marca_incumplido() {
        val hoy = DiaDeAdherencia("2026-09-08", tomasProgramadas = 3, tomasCumplidas = 1, enCurso = true)

        assertEquals(EstadoDia.EN_CURSO, hoy.estado)
    }

    @Test
    fun un_dia_sin_tratamiento_no_es_un_incumplimiento() {
        // Pintarlo de rojo culparia al paciente de algo que nunca tuvo que hacer.
        assertEquals(EstadoDia.SIN_TOMAS, DiaDeAdherencia("2026-09-05", 0, 0).estado)
    }

    @Test
    fun un_dia_cerrado_con_tomas_pendientes_si_es_incumplimiento() {
        assertEquals(EstadoDia.INCOMPLETO, DiaDeAdherencia("2026-09-03", 3, 1).estado)
    }

    @Test
    fun la_clave_del_recordatorio_es_estable_para_la_misma_toma() {
        // De esto depende que reprogramar REEMPLACE la alarma en vez de anadir
        // una segunda del mismo medicamento.
        val a = RecordatorioMedicacion("toma_9", "Paracetamol", "500 mg", "2026-09-08", "08:00")
        val b = a.copy(medicamento = "Otro nombre")

        assertEquals(a.claveSistema, b.claveSistema)
    }

    @Test
    fun la_franja_semanal_llega_al_estado_tras_cargar() {
        val vm = HomeViewModel(
            historial = HistorialParaInicioFalso(),
            adherencia = AdherenciaRepositorioFalso(),
            idPaciente = "pac_01H8X9A",
            perfilEmergenciaPendiente = false,
            reloj = RelojFijo(),
        )

        vm.cargar()

        assertEquals(7, vm.estado.value.semana.size)
    }

    @Test
    fun si_la_semana_falla_el_panel_sigue_funcionando() {
        // La franja es contexto, no el dato con el que el paciente actua hoy.
        val vm = HomeViewModel(
            historial = HistorialParaInicioFalso(),
            adherencia = AdherenciaRepositorioFalso(
                semana = Result.failure(IllegalStateException("sin red")),
                // Con tomas de verdad: sin ellas la prueba pasaria por estar
                // todo vacio, sin demostrar que el panel sobrevive.
                tomas = Result.success(listOf(toma("1", EstadoToma.PENDIENTE))),
            ),
            idPaciente = "pac_01H8X9A",
            perfilEmergenciaPendiente = false,
            reloj = RelojFijo(),
        )

        vm.cargar()

        val estado = vm.estado.value
        assertFalse(estado.errorCarga)
        assertTrue(estado.semana.isEmpty())
        assertTrue(estado.tomasDelDia.isNotEmpty())
    }
}
