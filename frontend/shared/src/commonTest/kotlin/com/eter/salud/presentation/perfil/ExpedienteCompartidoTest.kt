package com.eter.salud.presentation.perfil

import com.eter.salud.data.expediente.ExpedienteCompartidoEnArchivos
import com.eter.salud.data.expediente.ExpedienteCompartidoEnMemoria
import com.eter.salud.domain.model.Adjunto
import com.eter.salud.domain.model.Alergia
import com.eter.salud.domain.model.BloqueDeExpediente
import com.eter.salud.domain.model.CategoriaDocumento
import com.eter.salud.domain.model.ContactoEmergencia
import com.eter.salud.domain.model.DatosPersonales
import com.eter.salud.domain.model.DocumentoClinico
import com.eter.salud.domain.model.ExpedienteCompartido
import com.eter.salud.domain.model.PacienteDto
import com.eter.salud.domain.model.PerfilEmergenciaReducido
import com.eter.salud.domain.model.TipoAdjunto
import com.eter.salud.domain.model.TratamientoActivo
import com.eter.salud.domain.model.paraElMedico
import com.eter.salud.presentation.expediente.ExpedienteMedicoViewModel
import com.eter.salud.presentation.onboarding.RelojFijo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val PACIENTE = PacienteDto(
    datosPersonales = DatosPersonales("Juan", "Perez", "1970-05-02", "M", "555"),
    perfilEmergenciaReducido = PerfilEmergenciaReducido(
        alergias = listOf(Alergia("Penicilina", "Alta", "Urticaria")),
        condicionesCriticas = listOf("Hipertension arterial"),
    ),
    tratamientosActivos = listOf(TratamientoActivo(medicamento = "Losartan", dosis = "50 mg", frecuenciaHoras = 24)),
    contactosEmergencia = listOf(ContactoEmergencia("Ana", "Esposa", "555", 1)),
)

private fun documento(id: String, fecha: String, visible: Boolean = true) = DocumentoClinico(
    idDocumento = id,
    titulo = "Radiografia $id",
    categoria = CategoriaDocumento.RADIOGRAFIA,
    nombreArchivo = "$id.jpg",
    rutaLocal = "/privado/$id.jpg",
    tipoMime = "image/jpeg",
    fecha = fecha,
    visibleParaMedico = visible,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ExpedienteCompartidoTest {

    @BeforeTest
    fun configurar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun limpiar() {
        Dispatchers.resetMain()
    }

    // ------------------------------------------------------------- Dominio

    @Test
    fun por_defecto_el_medico_ve_el_expediente_completo() {
        assertEquals(PACIENTE, PACIENTE.paraElMedico(ExpedienteCompartido()))
    }

    @Test
    fun ocultar_un_bloque_lo_vacia_y_el_nombre_queda_siempre() {
        val vista = PACIENTE.paraElMedico(
            ExpedienteCompartido(bloquesOcultos = setOf(BloqueDeExpediente.EMERGENCIA, BloqueDeExpediente.CONTACTOS)),
        )

        assertNull(vista.perfilEmergenciaReducido)
        assertTrue(vista.contactosEmergencia.isEmpty())
        assertEquals(PACIENTE.tratamientosActivos, vista.tratamientosActivos)
        assertEquals("Juan", vista.datosPersonales?.nombre)
    }

    @Test
    fun el_medico_solo_ve_los_documentos_visibles_del_mas_reciente_al_mas_antiguo() {
        val compartido = ExpedienteCompartido(
            documentos = listOf(
                documento("a", "2026-09-01"),
                documento("b", "2026-09-10", visible = false),
                documento("c", "2026-09-15"),
            ),
        )

        assertEquals(listOf("c", "a"), compartido.documentosVisibles.map { it.idDocumento })
    }

    // ---------------------------------------------------------- Repositorio

    @Test
    fun eliminar_un_documento_lo_quita_y_borra_su_archivo() = runTest {
        val repo = ExpedienteCompartidoEnMemoria()
        repo.agregarDocumento("pac_1", documento("a", "2026-09-01"))

        repo.eliminarDocumento("pac_1", "doc_inexistente")
        repo.eliminarDocumento("pac_1", "a")

        assertTrue(repo.de("pac_1").first().documentos.isEmpty())
        assertEquals(listOf("/privado/a.jpg"), repo.borrados)
    }

    @Test
    fun lo_decidido_sobrevive_a_cerrar_la_app() = runTest {
        val carpeta = Path(SystemTemporaryDirectory, "salud_expediente_${Random.nextLong()}").toString()
        val primera = ExpedienteCompartidoEnArchivos(carpeta)
        primera.cambiarBloque("pac_1", BloqueDeExpediente.TRATAMIENTOS, visible = false)
        primera.agregarDocumento("pac_1", documento("a", "2026-09-01"))

        // Otra instancia = la app abierta de nuevo: lee lo que quedo en disco.
        val reabierta = ExpedienteCompartidoEnArchivos(carpeta).de("pac_1").first()

        assertEquals(setOf(BloqueDeExpediente.TRATAMIENTOS), reabierta.bloquesOcultos)
        assertEquals(listOf("a"), reabierta.documentos.map { it.idDocumento })
        SystemFileSystem.list(Path(carpeta)).forEach { SystemFileSystem.delete(it) }
    }

    @Test
    fun cada_paciente_tiene_su_propio_expediente_compartido() = runTest {
        val repo = ExpedienteCompartidoEnMemoria()

        repo.cambiarBloque("pac_1", BloqueDeExpediente.EMERGENCIA, visible = false)

        assertTrue(repo.de("pac_2").first().bloquesOcultos.isEmpty())
    }

    // --------------------------------------------------- Lado del paciente

    private val radiografiaEscaneada = Adjunto("adj_1", TipoAdjunto.ESCANEO, "scan_001.jpg", "/privado/scan_001.jpg", "image/jpeg")

    @Test
    fun un_escaneo_se_sugiere_como_radiografia() {
        assertEquals(CategoriaDocumento.RADIOGRAFIA, ExpedienteCompartidoViewModel.categoriaSugerida(radiografiaEscaneada))
    }

    @Test
    fun adjuntar_pregunta_que_es_antes_de_guardarlo() = runTest {
        val repo = ExpedienteCompartidoEnMemoria()
        val vm = ExpedienteCompartidoViewModel(
            HistorialMedicoRepositorioFalso(perfilRemoto = Result.success(PACIENTE)), repo, "pac_1", RelojFijo(),
        )

        vm.prepararDocumento(radiografiaEscaneada)
        assertNotNull(vm.estado.value.documentoPorClasificar)
        assertTrue(repo.de("pac_1").first().documentos.isEmpty())

        vm.guardarDocumento(CategoriaDocumento.RADIOGRAFIA, "Radiografia de torax")

        val guardado = repo.de("pac_1").first().documentos.single()
        assertEquals("Radiografia de torax", guardado.titulo)
        assertEquals("/privado/scan_001.jpg", guardado.rutaLocal)
        assertTrue(guardado.visibleParaMedico)
        assertNull(vm.estado.value.documentoPorClasificar)
    }

    @Test
    fun cancelar_no_guarda_nada() = runTest {
        val repo = ExpedienteCompartidoEnMemoria()
        val vm = ExpedienteCompartidoViewModel(HistorialMedicoRepositorioFalso(), repo, "pac_1", RelojFijo())

        vm.prepararDocumento(radiografiaEscaneada)
        vm.cancelarDocumento()

        assertTrue(repo.de("pac_1").first().documentos.isEmpty())
    }

    // ----------------------------------------------------- Lado del medico

    @Test
    fun el_medico_ve_lo_que_el_paciente_decide_y_se_entera_si_cambia() = runTest {
        val repo = ExpedienteCompartidoEnMemoria()
        repo.agregarDocumento("pac_1", documento("visible", "2026-09-10"))
        repo.agregarDocumento("pac_1", documento("privado", "2026-09-12", visible = false))
        val medico = ExpedienteMedicoViewModel(
            HistorialMedicoRepositorioFalso(perfilRemoto = Result.success(PACIENTE)), "pac_1", repo,
        )

        assertEquals(listOf("visible"), medico.estado.value.documentos.map { it.idDocumento })
        assertNotNull(medico.estado.value.paciente?.perfilEmergenciaReducido)

        repo.cambiarBloque("pac_1", BloqueDeExpediente.EMERGENCIA, visible = false)

        assertNull(medico.estado.value.paciente?.perfilEmergenciaReducido)
        assertTrue(BloqueDeExpediente.EMERGENCIA in medico.estado.value.bloquesOcultos)
    }
}
