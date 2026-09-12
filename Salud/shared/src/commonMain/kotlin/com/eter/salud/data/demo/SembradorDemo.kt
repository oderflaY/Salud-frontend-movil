package com.eter.salud.data.demo

import com.eter.salud.data.db.BaseSalud
import com.eter.salud.data.repository.DirectorioMedicoRepositorioMock
import com.eter.salud.data.repository.guardarExpediente
import com.eter.salud.data.repository.insertarEntrada
import com.eter.salud.data.repository.sembrarConversacion
import com.eter.salud.data.seguridad.HuellaDeContrasena
import com.eter.salud.domain.diario.TriageDelDiario
import com.eter.salud.domain.model.Cita
import com.eter.salud.domain.model.EntradaDiario
import com.eter.salud.domain.model.EstadoToma
import com.eter.salud.domain.model.EstadoVerificacionCedula
import com.eter.salud.domain.time.CalendarioSalud
import com.eter.salud.domain.time.RelojSalud
import com.eter.salud.domain.tratamiento.HorarioDeTratamiento

/**
 * Llena la base local con [CatalogoDemo] la primera vez que se abre la app.
 *
 * ## Una sola vez
 *
 * La siembra deja su version en la tabla `meta` y no se repite: si corriera en
 * cada arranque, pisaria lo que el paciente cambio (una toma marcada, un
 * mensaje, su expediente editado). Todo se inserta con `INSERT OR IGNORE`, asi
 * que ni siquiera una siembra interrumpida a la mitad duplica filas.
 *
 * ## Las citas si se renuevan
 *
 * Las citas de demostracion se fechan desde el dia de la siembra. Quien abra la
 * app tres semanas despues encontraria una agenda vacia hacia delante, y el
 * calendario de dos semanas no tendria nada que ensenar. Por eso, si la ultima
 * tanda tiene mas de [DIAS_PARA_RENOVAR] dias, se agenda una tanda nueva desde
 * hoy. Las viejas se quedan: son historia de la agenda.
 */
class SembradorDemo(
    private val base: BaseSalud,
    private val reloj: RelojSalud,
) {

    fun sembrar() {
        val hoy = reloj.fechaHoy()
        if (base.metaQueries.valor(CLAVE_VERSION).executeAsOneOrNull() != VERSION) {
            sembrarTodo(hoy)
        } else {
            renovarCitasSiHaceFalta(hoy)
        }
    }

    private fun sembrarTodo(hoy: String) {
        // Una sola huella para todas las cuentas de demostracion: comparten la
        // contrasena publica del catalogo, y calcular diez PBKDF2 retrasaria el
        // primer arranque sin proteger nada que no sea ya publico.
        val huella = HuellaDeContrasena.crear(CatalogoDemo.CONTRASENA)
        val ahora = reloj.instanteActual()
        val inicioHistorial = CalendarioSalud.restarDias(hoy, DIAS_DE_HISTORIAL)

        base.transaction {
            CatalogoDemo.pacientes.forEach { paciente ->
                base.cuentasQueries.insertarPacienteSiNoExiste(paciente.correo, paciente.id, huella, 0L, ahora)
            }
            sembrarCuentasMedicas(huella)

            CatalogoDemo.expedientes.forEach { (idPaciente, expediente) ->
                if (base.expedienteQueries.obtener(idPaciente).executeAsOneOrNull() == null) {
                    val riesgo = CatalogoDemo.pacientes.first { it.id == idPaciente }.riesgo
                    guardarExpediente(base, reloj, expediente, riesgo, tomasDesde = inicioHistorial)
                }
            }

            CatalogoDemo.vinculaciones.forEach { (idPaciente, medicos) ->
                medicos.forEach { idMedico -> base.vinculosQueries.vincular(idPaciente, idMedico, ahora) }
            }

            sembrarHistorialDeTomas(hoy)

            CatalogoDemo.conversaciones(hoy).forEach { (idConversacion, mensajes) ->
                if (base.mensajesQueries.contar(idConversacion).executeAsOne() == 0L) {
                    sembrarConversacion(base, idConversacion, mensajes)
                }
            }

            CatalogoDemo.anotacionesDiario.forEachIndexed { indice, anotacion ->
                val fecha = CalendarioSalud.restarDias(hoy, anotacion.diasAtras)
                val triage = TriageDelDiario.evaluar(anotacion.texto)
                insertarEntrada(
                    base,
                    EntradaDiario(
                        idEntrada = "diario_demo_$indice",
                        idPaciente = anotacion.idPaciente,
                        instante = "${fecha}T${anotacion.horaUtc}:00Z",
                        fecha = fecha,
                        texto = anotacion.texto,
                        severidad = triage.severidad,
                        terminosDetectados = triage.palabrasDetectadas,
                    ),
                    siNoExiste = true,
                )
            }

            agendarCitas(hoy, lote = "")
            base.metaQueries.fijar(CLAVE_VERSION, VERSION)
        }
    }

    private fun sembrarCuentasMedicas(huella: String) {
        base.cuentasQueries.insertarProfesionalSiNoExiste(
            "dr.elena.ruiz@hospital.com", CatalogoDemo.DRA_RUIZ, huella,
            "Elena", "Ruiz Santos", "Dra.", "12345678", EstadoVerificacionCedula.APROBADO.name,
        )
        CatalogoDemo.cuentasMedicas.forEach { cuenta ->
            base.cuentasQueries.insertarProfesionalSiNoExiste(
                cuenta.correo, cuenta.idMedico, huella, cuenta.nombre, cuenta.apellidos,
                cuenta.tratamiento, cuenta.cedula, EstadoVerificacionCedula.APROBADO.name,
            )
        }
    }

    /**
     * Dos semanas de tomas ya registradas antes del primer arranque, con el
     * patron fijo de olvidos de siempre (dos dias por semana con la ultima toma
     * omitida). Sin esto, el calendario abriria con catorce dias en rojo.
     */
    private fun sembrarHistorialDeTomas(hoy: String) {
        CatalogoDemo.expedientes.forEach { (idPaciente, expediente) ->
            val programadas = HorarioDeTratamiento.tomasProgramadas(expediente.tratamientosActivos)
            (1..DIAS_DE_HISTORIAL).forEach { atras ->
                val fecha = CalendarioSalud.restarDias(hoy, atras)
                val olvidadas = PATRON_OLVIDOS[atras % PATRON_OLVIDOS.size]
                programadas.forEachIndexed { indice, toma ->
                    val omitida = indice >= programadas.size - olvidadas
                    base.adherenciaQueries.registrarSiNoExiste(
                        idPaciente,
                        fecha,
                        toma.clave,
                        (if (omitida) EstadoToma.OMITIDO else EstadoToma.TOMADO).valorApi,
                        "${fecha}T${toma.hora}:00Z",
                    )
                }
            }
        }
    }

    private fun renovarCitasSiHaceFalta(hoy: String) {
        val ancla = base.metaQueries.valor(CLAVE_ANCLA_CITAS).executeAsOneOrNull() ?: return
        val dias = CalendarioSalud.diasEntre(ancla, hoy) ?: return
        if (dias < DIAS_PARA_RENOVAR) return
        base.transaction {
            // Solo las de hoy en adelante: las "pasadas" de la tanda nueva serian
            // consultas que nunca ocurrieron, inventadas en medio del historial.
            agendarCitas(hoy, lote = "${hoy}_") { it.fecha >= hoy }
        }
    }

    private fun agendarCitas(hoy: String, lote: String, filtro: (Cita) -> Boolean = { true }) {
        val nombres = DirectorioMedicoRepositorioMock.DOCTORES_DEMO.associate { it.idMedico to it.nombreCompleto }
        CatalogoDemo.citas(hoy, nombres, lote).filter(filtro).forEach { cita ->
            with(cita) {
                base.citasQueries.insertarSiNoExiste(
                    idCita, folio, idMedico, nombreMedico, idPaciente, fecha, horaInicio, horaFin,
                    estado.name, contacto?.nombreCompleto, contacto?.telefono, contacto?.correo,
                    contacto?.motivo, notaBloqueo,
                )
            }
        }
        base.metaQueries.fijar(CLAVE_ANCLA_CITAS, hoy)
    }

    private companion object {
        const val CLAVE_VERSION = "semilla_demo"
        const val CLAVE_ANCLA_CITAS = "semilla_demo_citas_desde"

        /** Subir este valor vuelve a sembrar lo que falte en instalaciones existentes. */
        const val VERSION = "1"

        const val DIAS_DE_HISTORIAL = 14
        const val DIAS_PARA_RENOVAR = 7

        /** Tomas olvidadas segun cuantos dias atras: dos dias con un olvido. */
        val PATRON_OLVIDOS = listOf(0, 0, 1, 0, 0, 1, 0)
    }
}
