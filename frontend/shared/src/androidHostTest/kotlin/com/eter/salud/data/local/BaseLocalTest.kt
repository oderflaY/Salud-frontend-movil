package com.eter.salud.data.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.eter.salud.data.db.BaseSalud
import com.eter.salud.data.demo.CatalogoDemo
import com.eter.salud.data.repository.guardarExpediente
import com.eter.salud.domain.model.AutorMensaje
import com.eter.salud.domain.model.DatosPersonales
import com.eter.salud.domain.model.EstadoToma
import com.eter.salud.domain.model.PacienteDto
import com.eter.salud.domain.model.TratamientoActivo
import com.eter.salud.domain.repository.FalloRegistro
import com.eter.salud.domain.time.CalendarioSalud
import com.eter.salud.presentation.citas.RelojAjustable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import java.util.Properties
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * La base local, con SQLite de verdad (el driver JDBC en la JVM).
 *
 * Lo que se fija es lo que el usuario pidio: que cerrar sesion -- e incluso
 * cerrar la app y volver a abrir la base -- no pierda el perfil, que la cuenta
 * principal ya tenga medicinas y citas, y que una cuenta nueva no entre sin
 * expediente.
 */
class BaseLocalTest {

    private val hoy = "2026-09-10"
    private val reloj = RelojAjustable(fecha = hoy, instante = "${hoy}T15:00:00Z")
    private val archivos = mutableListOf<File>()

    @AfterTest
    fun limpiar() {
        archivos.forEach { it.delete() }
    }

    private fun abrir(url: String = JdbcSqliteDriver.IN_MEMORY): Pair<BaseSalud, JdbcSqliteDriver> {
        val driver = JdbcSqliteDriver(url, Properties(), BaseSalud.Schema)
        return BaseSalud(driver) to driver
    }

    private fun archivoTemporal(): String {
        val archivo = File.createTempFile("salud", ".db").also { it.delete() }
        archivos += archivo
        return "jdbc:sqlite:${archivo.absolutePath}"
    }

    private fun expediente(idPaciente: String, nombre: String = "Ana") = PacienteDto(
        idPaciente = idPaciente,
        datosPersonales = DatosPersonales(nombre, "Soto Rivas", "1960-02-01", "Femenino", "+526180000000"),
        tratamientosActivos = listOf(
            TratamientoActivo(medicamento = "Metformina", dosis = "850 mg", frecuenciaHoras = 12),
        ),
    )

    // ---------------------------------------------------------- Cuenta nueva

    @Test
    fun una_cuenta_nueva_pide_su_expediente_hasta_que_lo_termina() = runTest {
        val (base) = abrir()
        val contenedor = ContenedorSalud(base, reloj)

        val nueva = contenedor.cuentasPacientes.crearCuenta("ana@correo.com", "clave-segura-1").getOrThrow()
        assertTrue(nueva.requiereOnboarding)
        // Salir y volver a entrar sin terminarlo: se le sigue pidiendo.
        assertTrue(contenedor.cuentasPacientes.iniciarSesion("ana@correo.com", "clave-segura-1").getOrThrow().requiereOnboarding)

        contenedor.expedienteNuevo(nueva.idPaciente).registrarPaciente(expediente(idPaciente = "ignorado"))

        val deVuelta = contenedor.cuentasPacientes.iniciarSesion("ana@correo.com", "clave-segura-1").getOrThrow()
        assertFalse(deVuelta.requiereOnboarding)
        val guardado = contenedor.historial.obtenerPaciente(nueva.idPaciente).getOrThrow()
        assertEquals("Ana", guardado.datosPersonales?.nombre)
        // Su tratamiento ya se convierte en tomas: cada 12 horas son dos.
        assertEquals(2, contenedor.adherencia.obtenerTomasDelDia(nueva.idPaciente, hoy).getOrThrow().size)
    }

    @Test
    fun un_correo_repetido_no_crea_otra_cuenta_y_la_contrasena_equivocada_no_entra() = runTest {
        val (base) = abrir()
        val cuentas = ContenedorSalud(base, reloj).cuentasPacientes
        cuentas.crearCuenta("ana@correo.com", "clave-segura-1").getOrThrow()

        assertIs<FalloRegistro>(cuentas.crearCuenta("ana@correo.com", "otra-clave").exceptionOrNull())
        assertTrue(cuentas.iniciarSesion("ana@correo.com", "clave-segura-2").isFailure)
    }

    // ------------------------------------------------------ Cuenta principal

    @Test
    fun la_cuenta_principal_ya_tiene_tratamiento_y_citas_en_las_proximas_dos_semanas() = runTest {
        val (base) = abrir()
        val contenedor = ContenedorSalud(base, reloj).also { it.preparar() }

        val sesion = contenedor.cuentasPacientes
            .iniciarSesion(CatalogoDemo.CORREO_PRINCIPAL, CatalogoDemo.CONTRASENA)
            .getOrThrow()
        assertFalse(sesion.requiereOnboarding)

        val tomas = contenedor.adherencia.obtenerTomasDelDia(sesion.idPaciente, hoy).getOrThrow()
        assertEquals(5, tomas.size)
        assertTrue(tomas.all { it.estado == EstadoToma.PENDIENTE })

        val limite = CalendarioSalud.sumarDias(hoy, 13)
        val medicos = contenedor.directorio.obtenerMedicosVinculados(sesion.idPaciente).getOrThrow()
        val proximas = medicos.flatMap { contenedor.citas.cargarAgenda(it.idMedico).getOrThrow() }
            .filter { it.idPaciente == sesion.idPaciente && it.fecha in hoy..limite }
        assertEquals(4, proximas.size)
    }

    @Test
    fun la_siembra_corre_una_sola_vez_aunque_la_app_arranque_muchas() = runTest {
        val (base) = abrir()
        ContenedorSalud(base, reloj).preparar()
        val citas = base.citasQueries.todas().executeAsList().size
        val mensajes = base.mensajesQueries.contar(CatalogoDemo.idConversacion(CatalogoDemo.ID_PRINCIPAL, CatalogoDemo.DRA_CASTILLO)).executeAsOne()

        ContenedorSalud(base, reloj).preparar()
        ContenedorSalud(base, reloj).preparar()

        assertEquals(citas, base.citasQueries.todas().executeAsList().size)
        assertEquals(mensajes, base.mensajesQueries.contar(CatalogoDemo.idConversacion(CatalogoDemo.ID_PRINCIPAL, CatalogoDemo.DRA_CASTILLO)).executeAsOne())
    }

    // ------------------------------------------------ Datos que sobreviven

    @Test
    fun cerrar_la_app_y_reabrir_la_base_conserva_perfil_tomas_y_citas() = runTest {
        val url = archivoTemporal()
        val (primera, driverPrimera) = abrir(url)
        val antes = ContenedorSalud(primera, reloj).also { it.preparar() }
        val sesion = antes.cuentasPacientes.iniciarSesion(CatalogoDemo.CORREO_PRINCIPAL, CatalogoDemo.CONTRASENA).getOrThrow()
        val tomaDeHoy = antes.adherencia.obtenerTomasDelDia(sesion.idPaciente, hoy).getOrThrow().first()
        antes.adherencia.registrarToma(sesion.idPaciente, tomaDeHoy.idToma, EstadoToma.TOMADO, reloj.instanteActual())
        val bloqueo = antes.citas.bloquearHorario(CatalogoDemo.DRA_RUIZ, hoy, "16:00", "17:00", "Junta").getOrThrow()
        antes.chat.enviarMensaje("conv_prueba", "Hola doctora", reloj.instanteActual(), AutorMensaje.PACIENTE)
        driverPrimera.close()

        val (segunda) = abrir(url)
        val despues = ContenedorSalud(segunda, reloj).also { it.preparar() }

        assertTrue(despues.cuentasPacientes.iniciarSesion(CatalogoDemo.CORREO_PRINCIPAL, CatalogoDemo.CONTRASENA).isSuccess)
        assertEquals("Alfredo", despues.historial.obtenerPaciente(sesion.idPaciente).getOrThrow().datosPersonales?.nombre)
        val tomas = despues.adherencia.obtenerTomasDelDia(sesion.idPaciente, hoy).getOrThrow()
        assertEquals(EstadoToma.TOMADO, tomas.single { it.idToma == tomaDeHoy.idToma }.estado)
        assertTrue(despues.citas.cargarAgenda(CatalogoDemo.DRA_RUIZ).getOrThrow().any { it.idCita == bloqueo.idCita })
        assertTrue(despues.chat.obtenerHistorial("conv_prueba").getOrThrow().any { it.texto == "Hola doctora" })
    }

    @Test
    fun una_cita_nueva_despues_de_reabrir_no_pisa_a_la_anterior() = runTest {
        val url = archivoTemporal()
        val (primera, driverPrimera) = abrir(url)
        val antes = ContenedorSalud(primera, reloj).also { it.preparar() }
        val vieja = antes.citas.bloquearHorario(CatalogoDemo.DRA_RUIZ, hoy, "16:00", "17:00", "Junta").getOrThrow()
        driverPrimera.close()

        val (segunda) = abrir(url)
        val despues = ContenedorSalud(segunda, reloj).also { it.preparar() }
        val nueva = despues.citas.bloquearHorario(CatalogoDemo.DRA_RUIZ, hoy, "18:00", "19:00", "Comida").getOrThrow()

        val agenda = despues.citas.cargarAgenda(CatalogoDemo.DRA_RUIZ).getOrThrow().map { it.idCita }
        assertTrue(vieja.idCita in agenda && nueva.idCita in agenda)
    }

    // ------------------------------------------------------------- Tomas

    @Test
    fun las_tomas_salen_del_tratamiento_y_un_dia_pasado_sin_marcar_cuenta_como_omitido() = runTest {
        val (base) = abrir()
        val adherencia = ContenedorSalud(base, reloj).adherencia
        guardarExpediente(base, reloj, expediente("pac_ana"), tomasDesde = CalendarioSalud.restarDias(hoy, 3))

        val ayer = adherencia.obtenerTomasDelDia("pac_ana", CalendarioSalud.restarDias(hoy, 1)).getOrThrow()
        val antesDelExpediente = adherencia.obtenerTomasDelDia("pac_ana", CalendarioSalud.restarDias(hoy, 5)).getOrThrow()
        val manana = adherencia.obtenerTomasDelDia("pac_ana", CalendarioSalud.sumarDias(hoy, 1)).getOrThrow()

        assertTrue(ayer.isNotEmpty() && ayer.all { it.estado == EstadoToma.OMITIDO })
        assertTrue(antesDelExpediente.isEmpty())
        assertTrue(manana.all { it.estado == EstadoToma.PENDIENTE })
    }

    // -------------------------------------------------- Medicos y mensajes

    @Test
    fun el_medico_elegido_en_el_directorio_ve_al_paciente_en_su_cartera() = runTest {
        val (base) = abrir()
        val contenedor = ContenedorSalud(base, reloj)
        guardarExpediente(base, reloj, expediente("pac_ana"))

        contenedor.directorio.solicitarVinculacion("pac_ana", "doc_local_27").getOrThrow()
        // Sin expediente no entra en ninguna cartera, aunque este vinculado.
        contenedor.directorio.solicitarVinculacion("pac_sin_expediente", "doc_local_27").getOrThrow()

        val cartera = contenedor.pacientesVinculados.obtenerPacientesVinculados("doc_local_27").getOrThrow()
        assertEquals(listOf("Ana Soto Rivas"), cartera.map { it.nombreCompleto })
        assertTrue(contenedor.directorio.obtenerMedicosVinculados("pac_ana").getOrThrow().any { it.idMedico == "doc_local_27" })
    }

    @Test
    fun el_acuse_automatico_no_cuenta_como_no_leido_y_un_mensaje_del_medico_si() = runTest {
        val (base) = abrir()
        val chat = ContenedorSalud(base, reloj).chat
        chat.obtenerHistorial("conv_x")
        assertEquals(0, chat.mensajesSinLeer("conv_x").first())

        chat.obtenerRespuestaAutomatica("conv_x", reloj.instanteActual())
        assertEquals(0, chat.mensajesSinLeer("conv_x").first())

        chat.enviarMensaje("conv_x", "Como sigue?", reloj.instanteActual(), AutorMensaje.MEDICO)
        assertEquals(1, chat.mensajesSinLeer("conv_x").first())

        chat.marcarConversacionLeida("conv_x")
        assertEquals(0, chat.mensajesSinLeer("conv_x").first())
    }
}
