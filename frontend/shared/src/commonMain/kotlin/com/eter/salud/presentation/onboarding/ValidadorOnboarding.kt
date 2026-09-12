package com.eter.salud.presentation.onboarding

/**
 * Reglas de validacion de la Fase 1. Objeto puro y sin dependencias de UI para
 * poder probarlo aisladamente.
 */
object ValidadorOnboarding {

    /** Formato `YYYY-MM-DD`. */
    private val FORMATO_FECHA = Regex("""\d{4}-\d{2}-\d{2}""")

    private const val MIN_DIGITOS_TELEFONO = 10
    private const val MAX_DIGITOS_TELEFONO = 15
    private const val MIN_FRECUENCIA_HORAS = 1
    private const val MAX_FRECUENCIA_HORAS = 168

    /**
     * Valida el paso actual contra el borrador. Lista vacia significa "puede avanzar".
     * @param hoy fecha de calendario actual (`YYYY-MM-DD`), inyectada para poder probar.
     */
    fun validarPaso(
        paso: PasoOnboarding,
        borrador: BorradorPaciente,
        hoy: String,
    ): List<ErrorCampoOnboarding> =
        when (paso) {
            PasoOnboarding.BIENVENIDA, PasoOnboarding.RESUMEN -> emptyList()

            PasoOnboarding.NOMBRE -> buildList {
                if (borrador.nombre.isBlank()) add(ErrorCampoOnboarding.NOMBRE_VACIO)
                if (borrador.apellidos.isBlank()) add(ErrorCampoOnboarding.APELLIDOS_VACIO)
            }

            PasoOnboarding.FECHA_NACIMIENTO -> validarFechaNacimiento(borrador, hoy)

            PasoOnboarding.GENERO ->
                siVacio(borrador.genero, ErrorCampoOnboarding.GENERO_VACIO)

            PasoOnboarding.TELEFONO -> validarTelefono(
                telefono = borrador.telefono,
                errorVacio = ErrorCampoOnboarding.TELEFONO_VACIO,
                errorFormato = ErrorCampoOnboarding.TELEFONO_FORMATO,
            )

            PasoOnboarding.TIPO_SANGRE ->
                if (borrador.tipoSangre.isBlank() && !borrador.desconoceTipoSangre) {
                    listOf(ErrorCampoOnboarding.SANGRE_VACIO)
                } else {
                    emptyList()
                }

            PasoOnboarding.ALERGIAS -> siListaSinConfirmar(
                vacia = borrador.alergias.isEmpty(),
                declarada = borrador.sinAlergiasConocidas,
                error = ErrorCampoOnboarding.ALERGIAS_SIN_CONFIRMAR,
            )

            PasoOnboarding.CONDICIONES_CRITICAS -> siListaSinConfirmar(
                vacia = borrador.condicionesCriticas.isEmpty(),
                declarada = borrador.sinCondicionesCriticas,
                error = ErrorCampoOnboarding.CONDICIONES_SIN_CONFIRMAR,
            )

            PasoOnboarding.MEDICACION_RESCATE -> siListaSinConfirmar(
                vacia = borrador.medicacionRescate.isEmpty(),
                declarada = borrador.sinMedicacionRescate,
                error = ErrorCampoOnboarding.RESCATE_SIN_CONFIRMAR,
            )

            PasoOnboarding.TRATAMIENTOS_ACTIVOS -> siListaSinConfirmar(
                vacia = borrador.tratamientos.isEmpty(),
                declarada = borrador.sinTratamientos,
                error = ErrorCampoOnboarding.TRATAMIENTOS_SIN_CONFIRMAR,
            )

            PasoOnboarding.CONTACTOS_EMERGENCIA ->
                if (borrador.contactos.isEmpty()) {
                    listOf(ErrorCampoOnboarding.CONTACTOS_VACIO)
                } else {
                    emptyList()
                }
        }

    fun validarAlergia(
        alergeno: String,
        severidad: String,
        reaccion: String,
    ): List<ErrorCampoOnboarding> = buildList {
        if (alergeno.isBlank()) add(ErrorCampoOnboarding.ALERGIA_ALERGENO_VACIO)
        if (severidad.isBlank()) add(ErrorCampoOnboarding.ALERGIA_SEVERIDAD_VACIA)
        if (reaccion.isBlank()) add(ErrorCampoOnboarding.ALERGIA_REACCION_VACIA)
    }

    fun validarTratamiento(
        medicamento: String,
        dosis: String,
        frecuenciaHoras: String,
        cantidadRestante: String,
    ): List<ErrorCampoOnboarding> = buildList {
        if (medicamento.isBlank()) add(ErrorCampoOnboarding.TRATAMIENTO_MEDICAMENTO_VACIO)
        if (dosis.isBlank()) add(ErrorCampoOnboarding.TRATAMIENTO_DOSIS_VACIA)
        when {
            frecuenciaHoras.isBlank() ->
                add(ErrorCampoOnboarding.TRATAMIENTO_FRECUENCIA_VACIA)

            !esFrecuenciaValida(frecuenciaHoras) ->
                add(ErrorCampoOnboarding.TRATAMIENTO_FRECUENCIA_INVALIDA)
        }
        if (cantidadRestante.isNotBlank() && cantidadRestante.trim().toIntOrNull() == null) {
            add(ErrorCampoOnboarding.TRATAMIENTO_INVENTARIO_INVALIDO)
        }
    }

    fun validarContacto(
        nombre: String,
        relacion: String,
        telefono: String,
    ): List<ErrorCampoOnboarding> = buildList {
        if (nombre.isBlank()) add(ErrorCampoOnboarding.CONTACTO_NOMBRE_VACIO)
        if (relacion.isBlank()) add(ErrorCampoOnboarding.CONTACTO_RELACION_VACIA)
        addAll(
            validarTelefono(
                telefono = telefono,
                errorVacio = ErrorCampoOnboarding.CONTACTO_TELEFONO_VACIO,
                errorFormato = ErrorCampoOnboarding.CONTACTO_TELEFONO_FORMATO,
            ),
        )
    }

    /**
     * Datos de supervivencia ausentes. Marcar "no lo se" en el tipo de sangre
     * satisface la validacion del paso, pero el dato sigue faltando en la
     * tarjeta RFID, por lo que se sigue reportando aqui.
     */
    fun camposCriticosOmitidos(borrador: BorradorPaciente): List<CampoCriticoSupervivencia> =
        buildList {
            if (borrador.tipoSangre.isBlank()) add(CampoCriticoSupervivencia.TIPO_SANGRE)
            if (borrador.alergias.isEmpty() && !borrador.sinAlergiasConocidas) {
                add(CampoCriticoSupervivencia.ALERGIAS)
            }
            if (borrador.condicionesCriticas.isEmpty() && !borrador.sinCondicionesCriticas) {
                add(CampoCriticoSupervivencia.CONDICIONES_CRITICAS)
            }
            if (borrador.medicacionRescate.isEmpty() && !borrador.sinMedicacionRescate) {
                add(CampoCriticoSupervivencia.MEDICACION_RESCATE)
            }
            if (borrador.contactos.isEmpty()) {
                add(CampoCriticoSupervivencia.CONTACTOS_EMERGENCIA)
            }
        }

    private fun validarFechaNacimiento(
        borrador: BorradorPaciente,
        hoy: String,
    ): List<ErrorCampoOnboarding> {
        val fecha = borrador.fechaNacimiento.trim()
        return when {
            fecha.isBlank() -> listOf(ErrorCampoOnboarding.NACIMIENTO_VACIO)
            !FORMATO_FECHA.matches(fecha) -> listOf(ErrorCampoOnboarding.NACIMIENTO_FORMATO)
            esFechaFutura(fecha, hoy) -> listOf(ErrorCampoOnboarding.NACIMIENTO_FUTURA)
            else -> emptyList()
        }
    }

    /** Comparacion lexicografica valida porque ambas fechas son `YYYY-MM-DD`. */
    fun esFechaFutura(fecha: String, hoy: String): Boolean = fecha.trim() > hoy

    private fun validarTelefono(
        telefono: String,
        errorVacio: ErrorCampoOnboarding,
        errorFormato: ErrorCampoOnboarding,
    ): List<ErrorCampoOnboarding> {
        val digitos = telefono.count { it.isDigit() }
        return when {
            telefono.isBlank() -> listOf(errorVacio)
            digitos !in MIN_DIGITOS_TELEFONO..MAX_DIGITOS_TELEFONO -> listOf(errorFormato)
            else -> emptyList()
        }
    }

    private fun esFrecuenciaValida(frecuenciaHoras: String): Boolean {
        val horas = frecuenciaHoras.trim().toIntOrNull() ?: return false
        return horas in MIN_FRECUENCIA_HORAS..MAX_FRECUENCIA_HORAS
    }

    private fun siVacio(valor: String, error: ErrorCampoOnboarding) =
        if (valor.isBlank()) listOf(error) else emptyList()

    private fun siListaSinConfirmar(
        vacia: Boolean,
        declarada: Boolean,
        error: ErrorCampoOnboarding,
    ) = if (vacia && !declarada) listOf(error) else emptyList()
}
