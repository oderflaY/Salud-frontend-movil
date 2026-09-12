package com.eter.salud.presentation.navegacion

import com.eter.salud.domain.model.Especialidad
import com.eter.salud.domain.model.MedicoVinculado
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pila de retorno de la app.
 *
 * De esta estructura cuelga toda la navegacion, asi que se prueba entera y sin
 * levantar Compose. Lo critico no es que apile: es que NUNCA quede vacia, que el
 * cierre de sesion no deje camino de vuelta a datos clinicos, y que la barra
 * inferior no convierta el historial en un registro de toques.
 */
class PilaNavegacionTest {

    private val medico = MedicoVinculado(
        idMedico = "doc_889900A",
        nombreCompleto = "Dra. Elena Ruiz Santos",
        especialidad = Especialidad.CARDIOLOGIA,
        idConversacion = "conv_pac_01H8X9A_doc_889900A",
    )

    private fun enInicio() = PilaNavegacion.raiz(Destino.Inicio)

    // -------------------------------------------------------------- Invariantes

    @Test
    fun una_pila_vacia_no_se_puede_construir() {
        // Si fuera posible, la interfaz se quedaria sin nada que dibujar.
        assertFailsWith<IllegalArgumentException> { PilaNavegacion(emptyList()) }
    }

    @Test
    fun desde_la_raiz_no_se_puede_volver_y_volver_no_la_vacia() {
        val pila = enInicio()

        assertFalse(pila.puedeVolver)
        assertEquals(pila.entradas, pila.volver().entradas)
    }

    // ------------------------------------------------------------------ Apilar

    @Test
    fun apilar_deja_el_destino_nuevo_arriba_y_permite_volver() {
        val pila = enInicio().apilar(Destino.ChatConMedico(medico))

        assertEquals(Destino.ChatConMedico(medico), pila.actual)
        assertTrue(pila.puedeVolver)
        assertTrue(pila.avanzando)
    }

    @Test
    fun volver_devuelve_al_destino_anterior_y_marca_el_sentido() {
        val pila = enInicio().apilar(Destino.ChatConMedico(medico)).volver()

        assertEquals(Destino.Inicio, pila.actual)
        // El sentido gobierna la animacion: volver no puede verse como avanzar.
        assertFalse(pila.avanzando)
    }

    @Test
    fun un_doble_toque_no_apila_la_misma_pantalla_dos_veces() {
        // Muy comun en una lista de pacientes: sin esta guarda haria falta
        // pulsar Atras dos veces para salir de una sola pantalla.
        val destino = Destino.ChatConMedico(medico)
        val pila = enInicio().apilar(destino).apilar(destino)

        assertEquals(2, pila.entradas.size)
    }

    @Test
    fun el_destino_lleva_sus_argumentos_asi_que_dos_chats_distintos_si_se_apilan() {
        val otro = medico.copy(idMedico = "doc_local_2", idConversacion = "conv_2")

        val pila = enInicio().apilar(Destino.ChatConMedico(medico)).apilar(Destino.ChatConMedico(otro))

        assertEquals(3, pila.entradas.size)
    }

    // ---------------------------------------------------------------- Secciones

    @Test
    fun cambiar_de_pestana_no_acumula_historial() {
        // Saltar entre pestanas no es navegar: si se apilara, volver seria
        // recorrer el registro de toques del usuario.
        val pila = enInicio()
            .irASeccion(Destino.MiMedico, Destino.Inicio)
            .irASeccion(Destino.Historial, Destino.Inicio)
            .irASeccion(Destino.MiMedico, Destino.Inicio)

        assertEquals(listOf(Destino.Inicio, Destino.MiMedico), pila.entradas)
    }

    @Test
    fun atras_desde_cualquier_pestana_lleva_al_panel_principal() {
        val pila = enInicio().irASeccion(Destino.Historial, Destino.Inicio)

        assertEquals(Destino.Inicio, pila.volver().actual)
    }

    @Test
    fun la_pestana_de_inicio_queda_como_unica_entrada_y_ahi_si_se_sale() {
        val pila = enInicio()
            .irASeccion(Destino.Historial, Destino.Inicio)
            .irASeccion(Destino.Inicio, Destino.Inicio)

        assertEquals(listOf(Destino.Inicio), pila.entradas)
        assertFalse(pila.puedeVolver)
    }

    @Test
    fun una_pantalla_apilada_sobre_una_pestana_mantiene_encendida_esa_pestana() {
        val pila = enInicio()
            .irASeccion(Destino.MiMedico, Destino.Inicio)
            .apilar(Destino.ChatConMedico(medico))

        assertEquals(Destino.MiMedico, pila.seccionActiva)
    }

    @Test
    fun fuera_de_las_pestanas_no_hay_ninguna_encendida() {
        val pila = PilaNavegacion.raiz(Destino.Acceso).apilar(Destino.Registro)

        assertEquals(null, pila.seccionActiva)
    }

    // ------------------------------------------------------------- Reiniciar

    @Test
    fun cerrar_sesion_borra_todo_camino_de_vuelta_a_datos_clinicos() {
        // Es la prueba de seguridad de la navegacion: tras salir, Atras no puede
        // devolver a una pantalla de la sesion anterior.
        val pila = enInicio()
            .irASeccion(Destino.Historial, Destino.Inicio)
            .apilar(Destino.ChatConMedico(medico))
            .reiniciarEn(Destino.Acceso)

        assertEquals(listOf(Destino.Acceso), pila.entradas)
        assertFalse(pila.puedeVolver)
    }

    // ------------------------------------------------------------ Volver hasta

    @Test
    fun volver_hasta_un_destino_lo_deja_arriba_y_descarta_lo_de_encima() {
        val pila = enInicio()
            .apilar(Destino.MiMedico)
            .apilar(Destino.ChatConMedico(medico))
            .volverHasta(Destino.Inicio)

        assertEquals(listOf(Destino.Inicio), pila.entradas)
    }

    @Test
    fun volver_hasta_un_destino_que_no_esta_en_la_pila_no_mueve_nada() {
        // Un objetivo escrito mal no debe poder dejar al usuario en ningun sitio.
        val pila = enInicio().apilar(Destino.MiMedico)

        assertEquals(pila.entradas, pila.volverHasta(Destino.Escaner).entradas)
    }
}

/**
 * Pantallas inmersivas.
 *
 * El escaner de emergencia es pestana -- se alcanza de un toque -- pero se usa
 * junto a un paciente inconsciente, y ahi la barra inferior sobra.
 */
class DestinoInmersivoTest {

    @Test
    fun el_escaner_de_emergencia_es_inmersivo() {
        assertTrue(Destino.Escaner.esInmersivo)
    }

    @Test
    fun las_demas_secciones_conservan_su_barra() {
        listOf(
            Destino.Inicio,
            Destino.AgendaPaciente,
            Destino.MiMedico,
            Destino.Historial,
            Destino.PanelProfesional,
            Destino.BandejaClinica,
            Destino.Agenda,
        ).forEach { seccion ->
            assertFalse(seccion.esInmersivo, seccion.toString())
        }
    }

    @Test
    fun el_escaner_sigue_siendo_pestana_pese_a_ser_inmersivo() {
        // Inmersivo no significa apilado: perder el acceso de un toque seria
        // peor que la barra que se le quita.
        assertTrue(Destino.Escaner in SECCIONES_PROFESIONAL)
    }
}
