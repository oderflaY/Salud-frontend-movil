package com.eter.salud.data.local

import androidx.compose.runtime.Composable
import app.cash.sqldelight.db.SqlDriver
import com.eter.salud.data.db.BaseSalud
import com.eter.salud.data.demo.SembradorDemo
import com.eter.salud.data.repository.AdherenciaRepositorioLocal
import com.eter.salud.data.repository.AlmacenDeCitas
import com.eter.salud.data.repository.AutenticacionProfesionalRepositorioLocal
import com.eter.salud.data.repository.AutenticacionRepositorioLocal
import com.eter.salud.data.repository.ChatRepositorioLocal
import com.eter.salud.data.repository.CitasRepositorioEnMemoria
import com.eter.salud.data.repository.DiarioRepositorioLocal
import com.eter.salud.data.repository.DirectorioMedicoRepositorioLocal
import com.eter.salud.data.repository.HistorialMedicoRepositorioLocal
import com.eter.salud.data.repository.PacienteRepositorioLocal
import com.eter.salud.data.repository.PacientesVinculadosRepositorioLocal
import com.eter.salud.data.repository.PerfilEmergenciaRepositorioMock
import com.eter.salud.domain.model.Cita
import com.eter.salud.domain.model.DatosContactoCita
import com.eter.salud.domain.model.EstadoCita
import com.eter.salud.domain.repository.PacienteRepositorio
import com.eter.salud.domain.time.RelojSalud
import com.eter.salud.domain.time.relojDelSistema
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Los repositorios de la app, todos sobre la misma base SQLite.
 *
 * ## Uno por proceso
 *
 * Antes cada repositorio se creaba con un `remember` dentro de `App()`. Al girar
 * el telefono la Activity se recrea, la composicion tambien, y nacian
 * repositorios nuevos mientras los ViewModels -- que si sobreviven -- seguian
 * hablando con los viejos: dos copias de la agenda que ya no se veian entre si.
 * Con la base de por medio eso seria ademas dos conexiones al mismo archivo.
 * Hay un contenedor por proceso y todos lo comparten.
 *
 * ## Preparar antes de usar
 *
 * [preparar] siembra la demostracion y carga la agenda de disco. `App()` lo
 * espera detras de la pantalla de arranque; ninguna pantalla toca un
 * repositorio antes.
 */
class ContenedorSalud(
    private val base: BaseSalud,
    private val reloj: RelojSalud = relojDelSistema(),
) {
    val cuentasPacientes = AutenticacionRepositorioLocal(base, reloj)
    val cuentasProfesionales = AutenticacionProfesionalRepositorioLocal(base)
    val historial = HistorialMedicoRepositorioLocal(base, reloj)
    val adherencia = AdherenciaRepositorioLocal(base, reloj)
    val directorio = DirectorioMedicoRepositorioLocal(base, reloj)
    val pacientesVinculados = PacientesVinculadosRepositorioLocal(base)
    val chat = ChatRepositorioLocal(base)
    val diario = DiarioRepositorioLocal(base, reloj)

    /** La tarjeta RFID aun no se registra desde la app: sigue siendo la de pruebas. */
    val emergencia = PerfilEmergenciaRepositorioMock()

    private var agenda: CitasRepositorioEnMemoria? = null
    private val preparacion = Mutex()

    /** Agenda de todas las consultas. Existe desde que termina [preparar]. */
    val citas: CitasRepositorioEnMemoria
        get() = checkNotNull(agenda) { "ContenedorSalud.preparar() no ha terminado" }

    /** El expediente que cierra el onboarding de [idPaciente]. */
    fun expedienteNuevo(idPaciente: String): PacienteRepositorio = PacienteRepositorioLocal(base, reloj, idPaciente)

    /** Siembra (solo la primera vez) y carga la agenda. Llamarla de nuevo no hace nada. */
    suspend fun preparar() = preparacion.withLock {
        if (agenda != null) return@withLock
        agenda = withContext(ContextoDeBase) {
            SembradorDemo(base, reloj).sembrar()
            val guardadas = base.citasQueries.todas().executeAsList().map { it.aCita() }
            CitasRepositorioEnMemoria(
                citasIniciales = guardadas,
                almacen = AlmacenDeCitasLocal(base),
                folioInicial = guardadas.mapNotNull { CitasRepositorioEnMemoria.consecutivoDeFolio(it.folio) }
                    .maxOrNull() ?: 0,
                generadorDeId = { idLocal("") },
            )
        }
    }
}

/** Escribe cada cita nueva o cambiada de la agenda en la tabla `cita`. */
private class AlmacenDeCitasLocal(private val base: BaseSalud) : AlmacenDeCitas {
    override suspend fun guardar(cita: Cita) {
        withContext(ContextoDeBase) {
            with(cita) {
                base.citasQueries.guardar(
                    idCita, folio, idMedico, nombreMedico, idPaciente, fecha, horaInicio, horaFin,
                    estado.name, contacto?.nombreCompleto, contacto?.telefono, contacto?.correo,
                    contacto?.motivo, notaBloqueo,
                )
            }
        }
    }
}

private fun com.eter.salud.data.db.Cita.aCita() = Cita(
    idCita = id_cita,
    folio = folio,
    idMedico = id_medico,
    nombreMedico = nombre_medico,
    idPaciente = id_paciente,
    fecha = fecha,
    horaInicio = hora_inicio,
    horaFin = hora_fin,
    // Un estado desconocido se lee como PENDIENTE: la cita sigue ocupando su
    // hueco y el consultorio la vuelve a revisar, en vez de liberarse sola.
    estado = EstadoCita.entries.firstOrNull { it.name == estado } ?: EstadoCita.PENDIENTE,
    contacto = if (contacto_nombre == null) {
        null
    } else {
        DatosContactoCita(
            nombreCompleto = contacto_nombre,
            telefono = contacto_telefono.orEmpty(),
            correo = contacto_correo.orEmpty(),
            motivo = contacto_motivo.orEmpty(),
        )
    },
    notaBloqueo = nota_bloqueo,
)

/**
 * El contenedor del proceso. Se crea con el primer driver que pida la
 * plataforma y a partir de ahi se reutiliza, sobreviva o no la Activity.
 */
internal object ContenedorCompartido {
    private var instancia: ContenedorSalud? = null

    fun obtener(abrirDriver: () -> SqlDriver): ContenedorSalud =
        instancia ?: ContenedorSalud(BaseSalud(abrirDriver())).also { instancia = it }
}

/** Nombre del archivo de la base. Igual en las dos plataformas. */
internal const val ARCHIVO_DE_BASE = "salud.db"

/**
 * El contenedor de la app. Es `expect` porque abrir SQLite necesita el `Context`
 * en Android y nada en iOS.
 */
@Composable
expect fun recordarContenedorSalud(): ContenedorSalud
