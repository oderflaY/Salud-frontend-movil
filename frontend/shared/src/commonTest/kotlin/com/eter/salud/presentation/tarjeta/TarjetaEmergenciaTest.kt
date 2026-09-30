package com.eter.salud.presentation.tarjeta

import com.eter.salud.data.preferencias.PreferenciasDeTarjetaEnMemoria
import com.eter.salud.data.repository.PerfilEmergenciaSegmentado
import com.eter.salud.domain.model.Alergia
import com.eter.salud.domain.model.DatoDeTarjeta
import com.eter.salud.domain.model.DatosPersonales
import com.eter.salud.domain.model.DispositivoRfid
import com.eter.salud.domain.model.IdentidadSupervivencia
import com.eter.salud.domain.model.PacienteDto
import com.eter.salud.domain.model.PerfilEmergenciaReducido
import com.eter.salud.domain.model.PerfilSupervivencia
import com.eter.salud.domain.model.VisibilidadDeTarjeta
import com.eter.salud.domain.model.segun
import com.eter.salud.domain.repository.PerfilEmergenciaRepositorio
import com.eter.salud.presentation.perfil.HistorialMedicoRepositorioFalso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val PERFIL = PerfilEmergenciaReducido(
    tipoSangre = "O+",
    donadorOrganos = true,
    alergias = listOf(Alergia("Penicilina", "Alta", "Anafilaxia")),
    condicionesCriticas = listOf("Asma reactiva"),
    medicacionRescate = listOf("Salbutamol"),
)

@OptIn(ExperimentalCoroutinesApi::class)
class TarjetaEmergenciaTest {

    @BeforeTest
    fun configurar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun paciente(tarjetaActiva: Boolean = true) = PacienteDto(
        datosPersonales = DatosPersonales("Ana", "Lopez", "1990-01-01", "F", "555"),
        dispositivosRfid = listOf(
            DispositivoRfid("C38610A8", if (tarjetaActiva) "activa" else "revocada", "2026-01-10T10:00:00Z"),
        ),
        perfilEmergenciaReducido = PERFIL,
    )

    private fun viewModel(
        preferencias: PreferenciasDeTarjetaEnMemoria = PreferenciasDeTarjetaEnMemoria(),
        paciente: PacienteDto = paciente(),
    ) = TarjetaEmergenciaViewModel(
        historial = HistorialMedicoRepositorioFalso(perfilRemoto = Result.success(paciente)),
        preferencias = preferencias,
        idPaciente = "pac_1",
    )

    // ------------------------------------------------------------- Dominio

    @Test
    fun por_defecto_la_tarjeta_lo_muestra_todo() {
        assertEquals(PERFIL, PERFIL.segun(VisibilidadDeTarjeta.TODO_VISIBLE))
    }

    @Test
    fun ocultar_un_dato_lo_vacia_y_deja_los_demas_igual() {
        val vista = PERFIL.segun(VisibilidadDeTarjeta(setOf(DatoDeTarjeta.TIPO_SANGRE)))

        assertNull(vista.tipoSangre)
        assertEquals(PERFIL.alergias, vista.alergias)
        assertEquals(PERFIL.condicionesCriticas, vista.condicionesCriticas)
    }

    @Test
    fun los_vitales_ocultos_son_los_que_se_advierten() {
        val visibilidad = VisibilidadDeTarjeta(setOf(DatoDeTarjeta.ALERGIAS, DatoDeTarjeta.TIPO_SANGRE))

        assertEquals(listOf(DatoDeTarjeta.ALERGIAS), visibilidad.vitalesOcultos)
    }

    // ------------------------------------------------------------ Pantalla

    @Test
    fun muestra_el_nombre_la_tarjeta_y_todo_lo_que_comparte() {
        val estado = viewModel().estado.value

        assertEquals("Ana Lopez", estado.nombreCompleto)
        assertEquals("C38610A8", estado.idTarjeta)
        assertEquals(PERFIL, estado.vistaDelParamedico)
    }

    @Test
    fun apagar_un_interruptor_cambia_la_vista_del_paramedico_y_se_guarda() = runTest {
        val preferencias = PreferenciasDeTarjetaEnMemoria()
        val vm = viewModel(preferencias)

        vm.cambiar(DatoDeTarjeta.CONDICIONES, visible = false)

        assertTrue(vm.estado.value.vistaDelParamedico.condicionesCriticas.isEmpty())
        assertEquals(setOf(DatoDeTarjeta.CONDICIONES), preferencias.visibilidad("C38610A8").first().ocultos)
    }

    @Test
    fun volver_a_encenderlo_lo_muestra_otra_vez() {
        val vm = viewModel()

        vm.cambiar(DatoDeTarjeta.ALERGIAS, visible = false)
        vm.cambiar(DatoDeTarjeta.ALERGIAS, visible = true)

        assertEquals(PERFIL.alergias, vm.estado.value.vistaDelParamedico.alergias)
    }

    @Test
    fun sin_tarjeta_activa_no_hay_nada_que_administrar() {
        val estado = viewModel(paciente = paciente(tarjetaActiva = false)).estado.value

        assertTrue(estado.sinTarjeta)
    }

    // --------------------------------------------------- Al escanear la tarjeta

    @Test
    fun al_escanear_se_aplica_lo_que_el_paciente_oculto() = runTest {
        val preferencias = PreferenciasDeTarjetaEnMemoria()
        preferencias.guardar("C38610A8", VisibilidadDeTarjeta(setOf(DatoDeTarjeta.ALERGIAS)))
        val base = object : PerfilEmergenciaRepositorio {
            override suspend fun consultarPorTarjeta(idTarjetaRfid: String) = Result.success(
                PerfilSupervivencia(idTarjetaRfid, IdentidadSupervivencia("Ana", "Lopez", "1990-01-01"), PERFIL),
            )
        }

        // El escaner puede leer el UID en minusculas: debe ser la misma tarjeta.
        val escaneado = PerfilEmergenciaSegmentado(base, preferencias).consultarPorTarjeta("c38610a8").getOrThrow()

        assertTrue(escaneado.perfilEmergenciaReducido.alergias.isEmpty())
        assertEquals(PERFIL.condicionesCriticas, escaneado.perfilEmergenciaReducido.condicionesCriticas)
        assertEquals("Ana", escaneado.datosPersonales.nombre)
    }
}
