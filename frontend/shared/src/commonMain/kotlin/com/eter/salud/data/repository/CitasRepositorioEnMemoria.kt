package com.eter.salud.data.repository

import com.eter.salud.domain.model.CanalNotificacion
import com.eter.salud.domain.model.Cita
import com.eter.salud.domain.model.ConfirmacionCita
import com.eter.salud.domain.model.DatosContactoCita
import com.eter.salud.domain.model.EstadoCita
import com.eter.salud.domain.model.FranjaAgenda
import com.eter.salud.domain.model.HorarioConsultorio
import com.eter.salud.domain.model.MotivoFalloCita
import com.eter.salud.domain.model.ReservaFranja
import com.eter.salud.domain.repository.CitasRepositorio
import com.eter.salud.domain.repository.FalloCita
import com.eter.salud.domain.time.CalendarioSalud
import com.eter.salud.domain.time.InstanteSalud
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Agenda simulada para desarrollo y pruebas de interfaz.
 *
 * Implementa de verdad las dos reglas de negocio del requerimiento, en vez de
 * devolver datos fijos, porque son justamente lo que hay que poder ensayar antes
 * de que exista el backend:
 *
 *  1. Solo se ofrecen franjas **estrictamente libres**: sin cita que ocupe, sin
 *     bloqueo del medico y sin retencion viva de otro paciente.
 *  2. Elegir un horario lo **retiene [ReservaFranja.MINUTOS_DE_RETENCION] minutos**
 *     mientras el paciente captura sus datos, y la retencion caduca sola.
 *
 * La agenda se publica como `StateFlow`: al confirmar una cita desde el chat, la
 * pantalla del medico se entera en el acto. Eso cubre la sincronizacion en
 * tiempo real del requerimiento sin WebSockets mientras no haya servidor, y
 * cuando lo haya bastara con cambiar esta clase.
 *
 * Se sustituira por el cliente HTTP contra el backend en Go sin tocar los
 * ViewModels, que solo dependen de [CitasRepositorio].
 */
class CitasRepositorioEnMemoria(
    private val nombresDeMedicos: Map<String, String> = NOMBRES_DEMO,
    /** Citas ya escritas al arrancar: las de la base local, o las de una prueba. */
    citasIniciales: List<Cita> = emptyList(),
    /**
     * Donde se escribe cada cita nueva o cambiada. Nulo en las pruebas: la
     * agenda funciona igual, solo que no sobrevive al proceso.
     */
    private val almacen: AlmacenDeCitas? = null,
    /** Ultimo folio ya emitido, para no repetirlo tras reiniciar la app. */
    folioInicial: Int = 0,
    /**
     * Identificadores de cita, bloqueo y retencion. Con base local tienen que
     * ser unicos entre arranques: un contador que vuelve a empezar en 1 haria
     * que la cita nueva pisara a la que ya estaba guardada con ese numero.
     */
    private val generadorDeId: (() -> String)? = null,
) : CitasRepositorio {

    private val agendas = mutableMapOf<String, MutableStateFlow<List<Cita>>>()

    init {
        citasIniciales.forEach { colocar(it) }
    }

    /** Retenciones vivas o caducadas, indexadas por su identificador. */
    private val retenciones = mutableMapOf<String, ReservaFranja>()

    private var consecutivoFolio = folioInicial
    private var consecutivoId = 0

    // ------------------------------------------------------------- Disponibilidad

    override suspend fun franjasLibres(
        idMedico: String,
        desde: String,
        ahora: String,
    ): Result<List<FranjaAgenda>> {
        if (!CalendarioSalud.esFechaValida(desde)) {
            return Result.failure(FalloCita(MotivoFalloCita.SIN_CONEXION))
        }
        val ocupadas = agenda(idMedico).value
        val libres = catalogoDe(idMedico, desde).filter { franja ->
            !chocaConAlgunaCita(franja, ocupadas) && !estaRetenidaPorOtro(franja, ahora)
        }
        return Result.success(libres)
    }

    override suspend fun reservarTemporalmente(
        idFranja: String,
        ahora: String,
    ): Result<ReservaFranja> {
        val franja = franjaDesdeId(idFranja)
            ?: return Result.failure(FalloCita(MotivoFalloCita.FRANJA_OCUPADA))

        if (chocaConAlgunaCita(franja, agenda(franja.idMedico).value) ||
            estaRetenidaPorOtro(franja, ahora)
        ) {
            return Result.failure(FalloCita(MotivoFalloCita.FRANJA_OCUPADA))
        }

        val expira = InstanteSalud.sumarMinutos(ahora, ReservaFranja.MINUTOS_DE_RETENCION)
            ?: return Result.failure(FalloCita(MotivoFalloCita.SIN_CONEXION))

        val reserva = ReservaFranja(
            idReserva = "res_${siguienteId()}",
            franja = franja,
            expiraEn = expira,
        )
        retenciones[reserva.idReserva] = reserva
        return Result.success(reserva)
    }

    override suspend fun liberarReserva(idReserva: String): Result<Unit> {
        retenciones.remove(idReserva)
        return Result.success(Unit)
    }

    // -------------------------------------------------------------- Confirmacion

    override suspend fun confirmarCita(
        idReserva: String,
        idPaciente: String,
        contacto: DatosContactoCita,
        ahora: String,
    ): Result<ConfirmacionCita> {
        val reserva = retenciones[idReserva]
            ?: return Result.failure(FalloCita(MotivoFalloCita.RESERVA_EXPIRADA))

        if (InstanteSalud.caduco(ahora, reserva.expiraEn)) {
            retenciones.remove(idReserva)
            return Result.failure(FalloCita(MotivoFalloCita.RESERVA_EXPIRADA))
        }

        val franja = reserva.franja
        // Se vuelve a comprobar aunque la franja estuviera retenida: el medico
        // pudo bloquear ese rango entre la eleccion y la confirmacion, y su
        // agenda manda sobre una retencion del chat.
        if (chocaConAlgunaCita(franja, agenda(franja.idMedico).value)) {
            retenciones.remove(idReserva)
            return Result.failure(FalloCita(MotivoFalloCita.FRANJA_OCUPADA))
        }

        val cita = Cita(
            idCita = "cita_${siguienteId()}",
            folio = siguienteFolio(),
            idMedico = franja.idMedico,
            nombreMedico = nombresDeMedicos[franja.idMedico].orEmpty(),
            idPaciente = idPaciente,
            fecha = franja.fecha,
            horaInicio = franja.horaInicio,
            horaFin = franja.horaFin,
            // Nace PENDIENTE y no CONFIRMADA: el paciente confirma su intencion,
            // pero el amarillo del calendario significa "el consultorio aun no la
            // ha validado". Darla por confirmada sola vaciaria ese estado.
            estado = EstadoCita.PENDIENTE,
            contacto = contacto,
        )
        retenciones.remove(idReserva)
        anotar(cita)

        return Result.success(
            ConfirmacionCita(
                cita = cita,
                // El envio real lo hace el backend. Aqui se declara por que
                // canales SALDRIA el comprobante para poder ensayar el mensaje
                // de exito del chat; ninguna de las dos notificaciones se manda.
                canalesNotificados = listOf(CanalNotificacion.CORREO, CanalNotificacion.WHATSAPP),
            ),
        )
    }

    // ------------------------------------------------------------------- Agenda

    override fun agendaDelMedico(idMedico: String): Flow<List<Cita>> =
        agenda(idMedico).asStateFlow()

    override suspend fun cargarAgenda(idMedico: String): Result<List<Cita>> =
        Result.success(agenda(idMedico).value)

    override suspend fun cambiarEstado(idCita: String, nuevo: EstadoCita): Result<Cita> =
        actualizarCita(idCita) { it.copy(estado = nuevo) }

    override suspend fun bloquearHorario(
        idMedico: String,
        fecha: String,
        horaInicio: String,
        horaFin: String,
        nota: String,
    ): Result<Cita> {
        if (!CalendarioSalud.esFechaValida(fecha) || horaFin <= horaInicio) {
            return Result.failure(FalloCita(MotivoFalloCita.SIN_CONEXION))
        }
        val bloqueo = Cita(
            idCita = "bloqueo_${siguienteId()}",
            folio = "",
            idMedico = idMedico,
            nombreMedico = nombresDeMedicos[idMedico].orEmpty(),
            idPaciente = "",
            fecha = fecha,
            horaInicio = horaInicio,
            horaFin = horaFin,
            estado = EstadoCita.BLOQUEADO,
            contacto = null,
            notaBloqueo = nota,
        )
        anotar(bloqueo)
        return Result.success(bloqueo)
    }

    override suspend fun reprogramar(
        idCita: String,
        idFranjaNueva: String,
        ahora: String,
    ): Result<Cita> {
        val flujo = agendas.values.firstOrNull { lista ->
            lista.value.any { it.idCita == idCita }
        } ?: return Result.failure(NoSuchElementException("Cita no encontrada: $idCita"))

        val original = flujo.value.first { it.idCita == idCita }
        val destino = franjaDesdeId(idFranjaNueva)
            ?: return Result.failure(FalloCita(MotivoFalloCita.FRANJA_OCUPADA))

        // La propia cita no cuenta como obstaculo de si misma: sin esto, mover
        // una cita a un rango que se solapa con su hora actual seria imposible.
        val otras = flujo.value.filterNot { it.idCita == idCita }
        if (chocaConAlgunaCita(destino, otras) || estaRetenidaPorOtro(destino, ahora)) {
            return Result.failure(FalloCita(MotivoFalloCita.FRANJA_OCUPADA))
        }

        val movida = original.copy(
            fecha = destino.fecha,
            horaInicio = destino.horaInicio,
            horaFin = destino.horaFin,
            // Reprogramar devuelve la cita a "por confirmar": el paciente todavia
            // no sabe de la nueva hora, asi que dejarla en verde mentiria.
            estado = if (original.esBloqueo) original.estado else EstadoCita.PENDIENTE,
        )
        flujo.value = (otras + movida).sortedBy { it.claveOrden }
        almacen?.guardar(movida)
        return Result.success(movida)
    }

    // ---------------------------------------------------------- Propuesta del medico

    override suspend fun proponerCita(
        idMedico: String,
        idPaciente: String,
        idFranja: String,
        contacto: DatosContactoCita,
        ahora: String,
    ): Result<Cita> {
        val franja = franjaDesdeId(idFranja)
            ?.takeIf { it.idMedico == idMedico }
            ?: return Result.failure(FalloCita(MotivoFalloCita.FRANJA_OCUPADA))

        if (chocaConAlgunaCita(franja, agenda(idMedico).value) || estaRetenidaPorOtro(franja, ahora)) {
            return Result.failure(FalloCita(MotivoFalloCita.FRANJA_OCUPADA))
        }

        val cita = Cita(
            idCita = "cita_${siguienteId()}",
            folio = siguienteFolio(),
            idMedico = idMedico,
            nombreMedico = nombresDeMedicos[idMedico].orEmpty(),
            idPaciente = idPaciente,
            fecha = franja.fecha,
            horaInicio = franja.horaInicio,
            horaFin = franja.horaFin,
            estado = EstadoCita.PROPUESTA_MEDICO,
            contacto = contacto,
        )
        anotar(cita)
        return Result.success(cita)
    }

    override suspend fun aceptarPropuesta(idCita: String): Result<Cita> =
        transicionarPropuesta(idCita, EstadoCita.CONFIRMADA)

    override suspend fun rechazarPropuesta(idCita: String): Result<Cita> =
        transicionarPropuesta(idCita, EstadoCita.CANCELADA)

    /**
     * Unico camino de salida de [EstadoCita.PROPUESTA_MEDICO]. Se comprueba el
     * estado ANTES de escribir: sin esta guarda, aceptar dos veces la misma
     * notificacion (un doble toque, una reconexion que reenvia la accion)
     * pisaria silenciosamente una cita que ya se habia cancelado o confirmado
     * por otra via.
     */
    private suspend fun transicionarPropuesta(idCita: String, nuevo: EstadoCita): Result<Cita> {
        val actual = citaPorId(idCita)
            ?: return Result.failure(NoSuchElementException("Cita no encontrada: $idCita"))
        if (actual.estado != EstadoCita.PROPUESTA_MEDICO) {
            return Result.failure(
                NoSuchElementException("La cita ya no tiene una propuesta pendiente: $idCita"),
            )
        }
        return actualizarCita(idCita) { it.copy(estado = nuevo) }
    }

    // ------------------------------------------------------------------ Internos

    private fun agenda(idMedico: String): MutableStateFlow<List<Cita>> =
        agendas.getOrPut(idMedico) { MutableStateFlow(emptyList()) }

    private fun citaPorId(idCita: String): Cita? =
        agendas.values.firstNotNullOfOrNull { lista -> lista.value.firstOrNull { it.idCita == idCita } }

    /** Punto unico de escritura de una cita ya existente: agenda viva y almacen. */
    private suspend fun actualizarCita(idCita: String, transformar: (Cita) -> Cita): Result<Cita> {
        val flujo = agendas.values.firstOrNull { lista ->
            lista.value.any { it.idCita == idCita }
        } ?: return Result.failure(NoSuchElementException("Cita no encontrada: $idCita"))

        val actualizada = transformar(flujo.value.first { it.idCita == idCita })
        flujo.value = flujo.value.map { if (it.idCita == idCita) actualizada else it }
        almacen?.guardar(actualizada)
        return Result.success(actualizada)
    }

    /** Alta de una cita o bloqueo: a la agenda viva y al almacen. */
    private suspend fun anotar(cita: Cita) {
        colocar(cita)
        almacen?.guardar(cita)
    }

    private fun colocar(cita: Cita) {
        val flujo = agenda(cita.idMedico)
        flujo.value = (flujo.value + cita).sortedBy { it.claveOrden }
    }

    /**
     * Huecos teoricos del medico en la ventana ofrecida, sin mirar ocupacion.
     *
     * Empieza al dia siguiente de [desde] a proposito. Filtrar las horas ya
     * pasadas de hoy exigiria comparar la hora de pared del consultorio con el
     * instante UTC del telefono, y esa conversion necesita la zona horaria de la
     * clinica, que es un dato del backend. Antes que inventarla, este simulador
     * no ofrece el mismo dia.
     */
    private fun catalogoDe(idMedico: String, desde: String): List<FranjaAgenda> =
        (PRIMER_DIA_OFRECIDO..DIAS_OFRECIDOS).flatMap { desplazamiento ->
            val fecha = CalendarioSalud.sumarDias(desde, desplazamiento)
            HorarioConsultorio.horasDeInicio().map { inicio ->
                FranjaAgenda(
                    idFranja = "$idMedico$SEPARADOR_ID$fecha$SEPARADOR_ID$inicio",
                    idMedico = idMedico,
                    fecha = fecha,
                    horaInicio = inicio,
                    horaFin = HorarioConsultorio.finDe(inicio),
                )
            }
        }

    /**
     * Reconstruye la franja desde su identificador en vez de guardar el catalogo
     * en memoria: el catalogo es una funcion pura del medico y la fecha, y
     * cachearlo solo abriria la posibilidad de que se desincronizara.
     */
    private fun franjaDesdeId(idFranja: String): FranjaAgenda? {
        val partes = idFranja.split(SEPARADOR_ID)
        if (partes.size != 3) return null
        val (idMedico, fecha, horaInicio) = partes
        if (!CalendarioSalud.esFechaValida(fecha)) return null
        if (FORMATO_HORA.matchEntire(horaInicio) == null) return null

        return FranjaAgenda(
            idFranja = idFranja,
            idMedico = idMedico,
            fecha = fecha,
            horaInicio = horaInicio,
            horaFin = HorarioConsultorio.finDe(horaInicio),
        )
    }

    /**
     * Solape de rangos horarios dentro del mismo dia. La comparacion es
     * lexicografica porque `HH:MM` es de ancho fijo, y usa `<` en los dos
     * extremos para que dos franjas contiguas (una que acaba a las 10:00 y otra
     * que empieza a las 10:00) no se consideren en conflicto.
     */
    private fun chocaConAlgunaCita(franja: FranjaAgenda, citas: List<Cita>): Boolean =
        citas.any { cita ->
            cita.estado.ocupaLaFranja &&
                cita.fecha == franja.fecha &&
                cita.horaInicio < franja.horaFin &&
                franja.horaInicio < cita.horaFin
        }

    private fun estaRetenidaPorOtro(franja: FranjaAgenda, ahora: String): Boolean =
        retenciones.values.any { reserva ->
            reserva.franja.idFranja == franja.idFranja &&
                !InstanteSalud.caduco(ahora, reserva.expiraEn)
        }

    private fun siguienteId(): String = generadorDeId?.invoke() ?: (++consecutivoId).toString()

    private fun siguienteFolio(): String =
        "$PREFIJO_FOLIO${(++consecutivoFolio + BASE_FOLIO)}"

    companion object {
        private const val PRIMER_DIA_OFRECIDO = 1
        private const val DIAS_OFRECIDOS = 14

        /**
         * Separador del identificador de franja. Es `|` y no `_` porque los
         * identificadores de medico ya llevan guion bajo (`doc_889900A`) y
         * partir por el daria trozos equivocados.
         */
        private const val SEPARADOR_ID = "|"

        private const val PREFIJO_FOLIO = "CITA-"
        private const val BASE_FOLIO = 4200

        /**
         * Consecutivo de un folio emitido por esta agenda, o nulo si el folio es
         * de otro origen (los de demostracion van de 5000 en adelante).
         */
        internal fun consecutivoDeFolio(folio: String): Int? =
            folio.removePrefix(PREFIJO_FOLIO).toIntOrNull()
                ?.minus(BASE_FOLIO)
                ?.takeIf { it in 1 until LIMITE_FOLIO_LOCAL }

        private const val LIMITE_FOLIO_LOCAL = 800

        private val FORMATO_HORA = Regex("""\d{2}:\d{2}""")

        /** Mismos doctores que publica [DirectorioMedicoRepositorioMock]. */
        internal val NOMBRES_DEMO: Map<String, String> =
            DirectorioMedicoRepositorioMock.DOCTORES_DEMO.associate { it.idMedico to it.nombreCompleto }
    }
}

/**
 * Almacen duradero de la agenda.
 *
 * La logica de agenda -- franjas libres, retenciones, choques -- vive en
 * [CitasRepositorioEnMemoria] y ya esta probada; lo unico que cambia con la base
 * local es que cada alta o cambio se escriba ademas en disco.
 */
interface AlmacenDeCitas {
    suspend fun guardar(cita: Cita)
}
