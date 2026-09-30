package com.eter.salud.domain.model

/**
 * Los bloques de la tarjeta de emergencia que el paciente decide si muestra.
 *
 * El nombre y la fecha de nacimiento NO estan aqui a proposito: un paramedico
 * necesita dirigirse a la persona y calcular dosis por edad, y una tarjeta sin
 * nombre no sirve para nada en una urgencia. Lo demas es informacion clinica,
 * y quien decide que se comparte es el paciente.
 *
 * [esVital] marca lo que puede salvar la vida en una urgencia: ocultarlo esta
 * permitido -- es su decision --, pero la pantalla se lo advierte antes.
 */
enum class DatoDeTarjeta(val esVital: Boolean) {
    ALERGIAS(esVital = true),
    MEDICACION_RESCATE(esVital = true),
    CONDICIONES(esVital = true),
    TIPO_SANGRE(esVital = false),
    DONACION_ORGANOS(esVital = false),
}

/**
 * Lo que el paciente eligio ocultar de su tarjeta de emergencia.
 *
 * Se guarda lo OCULTO y no lo visible: la tarjeta nace mostrandolo todo (lo
 * mas seguro en una urgencia) y un dato nuevo que se agregue en una version
 * futura aparece visible hasta que el paciente decida esconderlo, en vez de
 * quedar oculto sin que nadie lo haya elegido.
 */
data class VisibilidadDeTarjeta(val ocultos: Set<DatoDeTarjeta> = emptySet()) {

    fun muestra(dato: DatoDeTarjeta): Boolean = dato !in ocultos

    fun conDato(dato: DatoDeTarjeta, visible: Boolean): VisibilidadDeTarjeta =
        copy(ocultos = if (visible) ocultos - dato else ocultos + dato)

    /** Datos vitales que el paciente oculto: los que la pantalla le advierte. */
    val vitalesOcultos: List<DatoDeTarjeta>
        get() = DatoDeTarjeta.entries.filter { it.esVital && it in ocultos }

    companion object {
        val TODO_VISIBLE = VisibilidadDeTarjeta()
    }
}

/**
 * El perfil de emergencia tal como lo vera quien escanee la tarjeta: los
 * bloques ocultos se vacian, el resto queda igual.
 *
 * Vaciar y no quitar el campo: el escaner ya sabe pintar "sin alergias
 * registradas" para una lista vacia, y un dato ausente no delata que existia.
 */
fun PerfilEmergenciaReducido.segun(visibilidad: VisibilidadDeTarjeta): PerfilEmergenciaReducido = copy(
    tipoSangre = tipoSangre.takeIf { visibilidad.muestra(DatoDeTarjeta.TIPO_SANGRE) },
    donadorOrganos = donadorOrganos.takeIf { visibilidad.muestra(DatoDeTarjeta.DONACION_ORGANOS) },
    alergias = alergias.takeIf { visibilidad.muestra(DatoDeTarjeta.ALERGIAS) }.orEmpty(),
    condicionesCriticas = condicionesCriticas.takeIf { visibilidad.muestra(DatoDeTarjeta.CONDICIONES) }.orEmpty(),
    medicacionRescate = medicacionRescate.takeIf { visibilidad.muestra(DatoDeTarjeta.MEDICACION_RESCATE) }.orEmpty(),
)
