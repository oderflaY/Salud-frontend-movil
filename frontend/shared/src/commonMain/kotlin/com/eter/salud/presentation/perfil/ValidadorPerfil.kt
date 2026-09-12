package com.eter.salud.presentation.perfil

import com.eter.salud.presentation.onboarding.BorradorPaciente
import com.eter.salud.presentation.onboarding.ValidadorOnboarding

/**
 * Reglas de validacion del historial medico (Fase 2). Objeto puro, sin
 * dependencias de UI ni de red, para poder probarlo aisladamente.
 *
 * Los rangos clinicos son deliberadamente amplios: el objetivo es atajar
 * errores de dedo (un peso de 600 kg, una altura en metros), no diagnosticar.
 */
object ValidadorPerfil {

    /** Estructura oficial de la CURP mexicana, 18 caracteres. */
    private val FORMATO_CURP = Regex("""[A-Z]{4}\d{6}[HM][A-Z]{5}[A-Z0-9]\d""")

    private val FORMATO_FECHA = Regex("""\d{4}-\d{2}-\d{2}""")

    private const val DIGITOS_NSS = 11
    private val RANGO_PESO_KG = 2.0..400.0
    private val RANGO_ALTURA_CM = 30..250
    private val RANGO_SISTOLICA = 60..260
    private val RANGO_DIASTOLICA = 30..200

    /**
     * Valida una seccion completa. Lista vacia significa "se puede guardar".
     * @param hoy fecha de calendario actual (`YYYY-MM-DD`), inyectada para poder probar.
     */
    fun validarSeccion(
        seccion: SeccionPerfil,
        borrador: BorradorPaciente,
        hoy: String,
    ): List<ErrorCampoPerfil> = when (seccion) {
        SeccionPerfil.IDENTIFICACION_CURP -> validarCurp(borrador.curp)

        SeccionPerfil.SEGURIDAD_SOCIAL -> validarSeguridadSocial(
            nss = borrador.nss,
            aseguradora = borrador.aseguradora,
        )

        SeccionPerfil.DONACION_ORGANOS ->
            if (borrador.donadorOrganos == null) {
                listOf(ErrorCampoPerfil.DONADOR_SIN_RESPUESTA)
            } else {
                emptyList()
            }

        SeccionPerfil.METRICAS_CORPORALES -> validarMetricas(
            pesoKg = borrador.pesoKg,
            alturaCm = borrador.alturaCm,
        )

        SeccionPerfil.PRESION_ARTERIAL -> validarPresion(
            sistolica = borrador.presionSistolica,
            diastolica = borrador.presionDiastolica,
        )

        SeccionPerfil.CIRUGIAS ->
            if (borrador.cirugias.isEmpty() && !borrador.sinCirugias) {
                listOf(ErrorCampoPerfil.CIRUGIAS_SIN_CONFIRMAR)
            } else {
                emptyList()
            }

        SeccionPerfil.ANTECEDENTES_HEREDOFAMILIARES ->
            if (borrador.antecedentesHeredofamiliares.isEmpty() && !borrador.sinAntecedentes) {
                listOf(ErrorCampoPerfil.ANTECEDENTES_SIN_CONFIRMAR)
            } else {
                emptyList()
            }
    }

    fun validarCirugia(
        procedimiento: String,
        fecha: String,
        hoy: String,
    ): List<ErrorCampoPerfil> = buildList {
        if (procedimiento.isBlank()) add(ErrorCampoPerfil.CIRUGIA_PROCEDIMIENTO_VACIO)
        val fechaLimpia = fecha.trim()
        val fechaValida = FORMATO_FECHA.matches(fechaLimpia) &&
            !ValidadorOnboarding.esFechaFutura(fechaLimpia, hoy)
        if (!fechaValida) add(ErrorCampoPerfil.CIRUGIA_FECHA_INVALIDA)
    }

    /** Secciones que ya tienen dato suficiente para considerarse completas. */
    fun seccionesCompletas(borrador: BorradorPaciente, hoy: String): Set<SeccionPerfil> =
        SeccionPerfil.entries
            .filter { validarSeccion(it, borrador, hoy).isEmpty() }
            .toSet()

    /** Avance del expediente en porcentaje entero, para el indicador de la Vista. */
    fun porcentajeCompletado(seccionesCompletas: Set<SeccionPerfil>): Int =
        seccionesCompletas.size * 100 / SeccionPerfil.TOTAL_SECCIONES

    /** El peso admite coma decimal porque los teclados locales la producen. */
    fun pesoValido(pesoKg: String): Double? =
        pesoKg.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in RANGO_PESO_KG }

    fun alturaValida(alturaCm: String): Int? =
        alturaCm.trim().toIntOrNull()?.takeIf { it in RANGO_ALTURA_CM }

    private fun validarCurp(curp: String): List<ErrorCampoPerfil> {
        val limpia = curp.trim().uppercase()
        return when {
            limpia.isBlank() -> listOf(ErrorCampoPerfil.CURP_VACIA)
            !FORMATO_CURP.matches(limpia) -> listOf(ErrorCampoPerfil.CURP_FORMATO)
            else -> emptyList()
        }
    }

    /**
     * Basta con uno de los dos: hay pacientes sin seguridad social pero con
     * poliza privada, y al reves.
     */
    private fun validarSeguridadSocial(
        nss: String,
        aseguradora: String,
    ): List<ErrorCampoPerfil> {
        val nssLimpio = nss.trim()
        return when {
            nssLimpio.isBlank() && aseguradora.isBlank() -> listOf(ErrorCampoPerfil.NSS_VACIO)
            nssLimpio.isBlank() -> emptyList()
            nssLimpio.length != DIGITOS_NSS || !nssLimpio.all { it.isDigit() } ->
                listOf(ErrorCampoPerfil.NSS_FORMATO)

            else -> emptyList()
        }
    }

    private fun validarMetricas(pesoKg: String, alturaCm: String): List<ErrorCampoPerfil> =
        buildList {
            if (pesoValido(pesoKg) == null) add(ErrorCampoPerfil.PESO_INVALIDO)
            if (alturaValida(alturaCm) == null) add(ErrorCampoPerfil.ALTURA_INVALIDA)
        }

    /**
     * La lectura viaja completa o no viaja: una sistolica sin diastolica no es
     * interpretable clinicamente.
     */
    private fun validarPresion(sistolica: String, diastolica: String): List<ErrorCampoPerfil> {
        val alta = sistolica.trim().toIntOrNull()
        val baja = diastolica.trim().toIntOrNull()
        val valida = alta != null && baja != null &&
            alta in RANGO_SISTOLICA && baja in RANGO_DIASTOLICA && alta > baja
        return if (valida) emptyList() else listOf(ErrorCampoPerfil.PRESION_INVALIDA)
    }
}
