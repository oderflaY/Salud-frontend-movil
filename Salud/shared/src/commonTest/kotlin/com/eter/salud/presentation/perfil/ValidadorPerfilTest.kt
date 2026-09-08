package com.eter.salud.presentation.perfil

import com.eter.salud.presentation.onboarding.BorradorPaciente
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reglas de validacion del historial medico (Fase 2).
 * DM_Arquitectura_App.md, seccion 2: TDD obligatorio.
 */
class ValidadorPerfilTest {

    private val hoy = "2026-09-05"

    // ------------------------------------------------------------------ CURP

    @Test
    fun la_curp_vacia_se_reporta_como_faltante() {
        val errores = ValidadorPerfil.validarSeccion(
            seccion = SeccionPerfil.IDENTIFICACION_CURP,
            borrador = BorradorPaciente(curp = "   "),
            hoy = hoy,
        )

        assertEquals(listOf(ErrorCampoPerfil.CURP_VACIA), errores)
    }

    @Test
    fun la_curp_con_estructura_invalida_se_rechaza() {
        val errores = ValidadorPerfil.validarSeccion(
            seccion = SeccionPerfil.IDENTIFICACION_CURP,
            borrador = BorradorPaciente(curp = "PAGJ85041"),
            hoy = hoy,
        )

        assertEquals(listOf(ErrorCampoPerfil.CURP_FORMATO), errores)
    }

    @Test
    fun la_curp_oficial_en_minusculas_se_acepta() {
        val errores = ValidadorPerfil.validarSeccion(
            seccion = SeccionPerfil.IDENTIFICACION_CURP,
            borrador = BorradorPaciente(curp = "pagj850412hdfrxx09"),
            hoy = hoy,
        )

        assertTrue(errores.isEmpty())
    }

    // -------------------------------------------------- Numero de seguridad social

    @Test
    fun el_nss_debe_tener_once_digitos() {
        val errores = ValidadorPerfil.validarSeccion(
            seccion = SeccionPerfil.SEGURIDAD_SOCIAL,
            borrador = BorradorPaciente(nss = "12345"),
            hoy = hoy,
        )

        assertEquals(listOf(ErrorCampoPerfil.NSS_FORMATO), errores)
    }

    @Test
    fun la_aseguradora_sola_basta_para_guardar_la_seccion() {
        val errores = ValidadorPerfil.validarSeccion(
            seccion = SeccionPerfil.SEGURIDAD_SOCIAL,
            borrador = BorradorPaciente(aseguradora = "IMSS"),
            hoy = hoy,
        )

        assertTrue(errores.isEmpty())
    }

    @Test
    fun la_seccion_de_seguridad_social_vacia_no_se_puede_guardar() {
        val errores = ValidadorPerfil.validarSeccion(
            seccion = SeccionPerfil.SEGURIDAD_SOCIAL,
            borrador = BorradorPaciente(),
            hoy = hoy,
        )

        assertEquals(listOf(ErrorCampoPerfil.NSS_VACIO), errores)
    }

    // ------------------------------------------------------- Metricas corporales

    @Test
    fun el_peso_fuera_de_rango_clinico_se_rechaza() {
        val errores = ValidadorPerfil.validarSeccion(
            seccion = SeccionPerfil.METRICAS_CORPORALES,
            borrador = BorradorPaciente(pesoKg = "600", alturaCm = "175"),
            hoy = hoy,
        )

        assertEquals(listOf(ErrorCampoPerfil.PESO_INVALIDO), errores)
    }

    @Test
    fun la_altura_no_entera_se_rechaza() {
        val errores = ValidadorPerfil.validarSeccion(
            seccion = SeccionPerfil.METRICAS_CORPORALES,
            borrador = BorradorPaciente(pesoKg = "78.5", alturaCm = "1.75"),
            hoy = hoy,
        )

        assertEquals(listOf(ErrorCampoPerfil.ALTURA_INVALIDA), errores)
    }

    @Test
    fun el_peso_con_coma_decimal_se_acepta_porque_el_teclado_local_la_produce() {
        val errores = ValidadorPerfil.validarSeccion(
            seccion = SeccionPerfil.METRICAS_CORPORALES,
            borrador = BorradorPaciente(pesoKg = "78,5", alturaCm = "175"),
            hoy = hoy,
        )

        assertTrue(errores.isEmpty())
    }

    // --------------------------------------------------------- Presion arterial

    @Test
    fun la_presion_sistolica_menor_que_la_diastolica_es_invalida() {
        val errores = ValidadorPerfil.validarSeccion(
            seccion = SeccionPerfil.PRESION_ARTERIAL,
            borrador = BorradorPaciente(presionSistolica = "70", presionDiastolica = "120"),
            hoy = hoy,
        )

        assertEquals(listOf(ErrorCampoPerfil.PRESION_INVALIDA), errores)
    }

    @Test
    fun la_presion_incompleta_es_invalida() {
        val errores = ValidadorPerfil.validarSeccion(
            seccion = SeccionPerfil.PRESION_ARTERIAL,
            borrador = BorradorPaciente(presionSistolica = "120"),
            hoy = hoy,
        )

        assertEquals(listOf(ErrorCampoPerfil.PRESION_INVALIDA), errores)
    }

    // ------------------------------------------------------------------ Cirugias

    @Test
    fun una_cirugia_sin_procedimiento_ni_fecha_valida_acumula_ambos_errores() {
        val errores = ValidadorPerfil.validarCirugia(
            procedimiento = "  ",
            fecha = "20-08-2015",
            hoy = hoy,
        )

        assertContains(errores, ErrorCampoPerfil.CIRUGIA_PROCEDIMIENTO_VACIO)
        assertContains(errores, ErrorCampoPerfil.CIRUGIA_FECHA_INVALIDA)
    }

    @Test
    fun una_cirugia_con_fecha_futura_se_rechaza() {
        val errores = ValidadorPerfil.validarCirugia(
            procedimiento = "Apendicectomia",
            fecha = "2027-01-01",
            hoy = hoy,
        )

        assertEquals(listOf(ErrorCampoPerfil.CIRUGIA_FECHA_INVALIDA), errores)
    }

    @Test
    fun la_seccion_de_cirugias_exige_lista_o_declaracion_explicita() {
        val sinDeclarar = ValidadorPerfil.validarSeccion(
            seccion = SeccionPerfil.CIRUGIAS,
            borrador = BorradorPaciente(),
            hoy = hoy,
        )
        val declarada = ValidadorPerfil.validarSeccion(
            seccion = SeccionPerfil.CIRUGIAS,
            borrador = BorradorPaciente(sinCirugias = true),
            hoy = hoy,
        )

        assertEquals(listOf(ErrorCampoPerfil.CIRUGIAS_SIN_CONFIRMAR), sinDeclarar)
        assertTrue(declarada.isEmpty())
    }

    // ------------------------------------------------------------ Donacion y avance

    @Test
    fun la_donacion_de_organos_exige_una_respuesta_explicita() {
        val errores = ValidadorPerfil.validarSeccion(
            seccion = SeccionPerfil.DONACION_ORGANOS,
            borrador = BorradorPaciente(donadorOrganos = null),
            hoy = hoy,
        )

        assertEquals(listOf(ErrorCampoPerfil.DONADOR_SIN_RESPUESTA), errores)
    }

    @Test
    fun las_secciones_completas_se_calculan_sobre_el_borrador() {
        val borrador = BorradorPaciente(
            curp = "PAGJ850412HDFRXX09",
            nss = "12345678901",
            donadorOrganos = true,
            sinCirugias = true,
        )

        val completas = ValidadorPerfil.seccionesCompletas(borrador, hoy)

        assertEquals(
            setOf(
                SeccionPerfil.IDENTIFICACION_CURP,
                SeccionPerfil.SEGURIDAD_SOCIAL,
                SeccionPerfil.DONACION_ORGANOS,
                SeccionPerfil.CIRUGIAS,
            ),
            completas,
        )
    }
}
