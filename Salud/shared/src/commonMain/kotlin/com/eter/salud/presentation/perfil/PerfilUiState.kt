package com.eter.salud.presentation.perfil

import com.eter.salud.domain.mapper.calcularImc
import com.eter.salud.presentation.onboarding.BorradorPaciente

/**
 * Estado unico de la pantalla de ajustes de perfil (Fase 2). La Vista solo
 * pinta esto (DM_Arquitectura_App.md, seccion 2: la Vista es pasiva).
 *
 * No contiene texto: errores y secciones viajan como claves que la Vista
 * traduce contra `strings.xml`.
 */
data class PerfilUiState(
    /** Expediente en edicion; arranca vacio hasta que se descarga el perfil. */
    val borrador: BorradorPaciente = BorradorPaciente(),
    val idPaciente: String? = null,
    /** Seccion desplegada; nulo cuando se ve la lista de secciones. */
    val seccionAbierta: SeccionPerfil? = null,
    /** Secciones ya persistidas en el backend, no solo escritas en pantalla. */
    val seccionesCompletas: Set<SeccionPerfil> = emptySet(),
    /** Errores que impiden guardar la seccion abierta. */
    val erroresSeccion: List<ErrorCampoPerfil> = emptyList(),
    /** Errores del formulario embebido (agregar cirugia, agregar antecedente). */
    val erroresFormulario: List<ErrorCampoPerfil> = emptyList(),
    val cargando: Boolean = false,
    val guardando: Boolean = false,
    /** Ultima seccion guardada con exito, para confirmarlo una sola vez. */
    val seccionGuardada: SeccionPerfil? = null,
    val errorGuardado: ErrorGuardado? = null,
    /** Fecha de calendario actual; la usa el selector para resolver atajos. */
    val fechaDeHoy: String = "",
) {
    val porcentajeCompletado: Int
        get() = ValidadorPerfil.porcentajeCompletado(seccionesCompletas)

    /**
     * IMC en vivo mientras se escriben peso y altura. Es un valor derivado: no
     * se almacena en el borrador porque el backend lo recibe ya calculado en
     * `metricasVitalesActuales.imc`.
     */
    val imcCalculado: Double?
        get() = calcularImc(
            pesoKg = ValidadorPerfil.pesoValido(borrador.pesoKg),
            alturaCm = ValidadorPerfil.alturaValida(borrador.alturaCm),
        )

    val hayPerfilCargado: Boolean get() = idPaciente != null

    fun estaCompleta(seccion: SeccionPerfil): Boolean = seccion in seccionesCompletas
}
