package com.eter.salud.presentation.onboarding

/**
 * Estado unico que el ViewModel expone a la Vista. La Vista es pasiva: solo
 * pinta esto (DM_Arquitectura_App.md, seccion 2).
 *
 * No contiene texto: los errores y los campos criticos viajan como claves que
 * la Vista traduce contra `strings.xml`.
 */
data class OnboardingUiState(
    val paso: PasoOnboarding = PasoOnboarding.BIENVENIDA,
    val borrador: BorradorPaciente = BorradorPaciente(),
    /** Errores que impiden avanzar del paso actual. */
    val erroresPaso: List<ErrorCampoOnboarding> = emptyList(),
    /** Errores del formulario embebido (agregar alergia, tratamiento, contacto). */
    val erroresFormulario: List<ErrorCampoOnboarding> = emptyList(),
    val camposCriticosOmitidos: List<CampoCriticoSupervivencia> = emptyList(),
    val mostrarAlertaCriticos: Boolean = false,
    val enviando: Boolean = false,
    val envioExitoso: Boolean = false,
    val errorEnvio: ErrorEnvio? = null,
    val idPacienteRegistrado: String? = null,
    /** Fecha de calendario actual; la usa el selector para resolver atajos. */
    val fechaDeHoy: String = "",
) {
    val numeroPregunta: Int? get() = paso.numeroPregunta

    val totalPreguntas: Int get() = PasoOnboarding.TOTAL_PREGUNTAS

    val puedeRetroceder: Boolean get() = paso.anterior != null && !enviando

    val puedeOmitir: Boolean get() = paso.esOmitible && !enviando

    val esUltimoPaso: Boolean get() = paso == PasoOnboarding.RESUMEN
}
