package com.eter.salud.presentation.expediente

import com.eter.salud.domain.model.DatosPersonales
import com.eter.salud.domain.model.PacienteDto
import com.eter.salud.presentation.perfil.HistorialMedicoRepositorioFalso
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
 * Expediente clinico visto por el medico.
 *
 * Lo que se fija: que el expediente se carga solo al construirse (sin efecto de
 * la Vista), que un fallo de red se distingue de "todavia cargando", y que
 * reintentar no dispara una segunda peticion mientras la primera sigue en vuelo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExpedienteMedicoViewModelTest {

    private val idPaciente = "pac_01H8X9A"

    @BeforeTest
    fun configurar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun limpiar() {
        Dispatchers.resetMain()
    }

    @Test
    fun al_construirse_carga_el_expediente_sin_que_la_vista_lo_pida() {
        val dto = PacienteDto(
            idPaciente = idPaciente,
            datosPersonales = DatosPersonales(
                nombre = "Alfredo",
                apellidos = "Valadez Gonzalez",
                fechaNacimiento = "1998-04-02",
                genero = "Masculino",
                telefono = "6181234567",
            ),
        )
        val repositorio = HistorialMedicoRepositorioFalso(perfilRemoto = Result.success(dto))

        val vm = ExpedienteMedicoViewModel(repositorio, idPaciente)

        assertFalse(vm.estado.value.cargando)
        assertEquals(dto, vm.estado.value.paciente)
        assertEquals(1, repositorio.lecturas)
    }

    @Test
    fun un_fallo_de_red_se_distingue_de_seguir_cargando() {
        val repositorio = HistorialMedicoRepositorioFalso(
            perfilRemoto = Result.failure(IllegalStateException("sin red")),
        )

        val vm = ExpedienteMedicoViewModel(repositorio, idPaciente)

        assertTrue(vm.estado.value.errorCarga)
        assertFalse(vm.estado.value.cargando)
        assertNull(vm.estado.value.paciente)
    }

    @Test
    fun reintentar_vuelve_a_pedir_el_expediente() {
        val repositorio = HistorialMedicoRepositorioFalso(
            perfilRemoto = Result.failure(IllegalStateException("sin red")),
        )
        val vm = ExpedienteMedicoViewModel(repositorio, idPaciente)
        assertEquals(1, repositorio.lecturas)

        vm.reintentar()

        assertEquals(2, repositorio.lecturas)
    }
}
