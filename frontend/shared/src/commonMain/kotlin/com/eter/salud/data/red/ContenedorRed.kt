package com.eter.salud.data.red

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.eter.salud.data.adjuntos.ArchivosAdjuntosLocales
import com.eter.salud.data.adjuntos.rememberArchivosAdjuntosLocales
import com.eter.salud.data.repository.AdherenciaRepositorioRemoto
import com.eter.salud.data.repository.AutenticacionProfesionalRepositorioRemoto
import com.eter.salud.data.repository.AutenticacionRepositorioRemoto
import com.eter.salud.data.repository.BajaDeCuentaRepositorioRemoto
import com.eter.salud.data.repository.CambioDeContrasenaRepositorioRemoto
import com.eter.salud.data.repository.ChatRepositorioRemoto
import com.eter.salud.data.repository.CitasRepositorioRemoto
import com.eter.salud.data.repository.DiarioRepositorioRemoto
import com.eter.salud.data.repository.DiarioRepositorioSincronizado
import com.eter.salud.data.repository.DirectorioMedicoRepositorioRemoto
import com.eter.salud.data.repository.HistorialMedicoRepositorioRemoto
import com.eter.salud.data.repository.PacienteRepositorioRemoto
import com.eter.salud.data.repository.PacientesVinculadosRepositorioRemoto
import com.eter.salud.data.repository.PerfilEmergenciaRepositorioRemoto
import com.eter.salud.data.sesion.AvisoDeSesion
import com.eter.salud.data.sesion.FuenteDeSesion
import com.eter.salud.domain.repository.DiarioRepositorio
import io.ktor.client.engine.HttpClientEngineFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Los repositorios `*Remoto` de la app, todos sobre el mismo [HttpClient] y la
 * misma conexion de tiempo real -- analogo a
 * [com.eter.salud.data.local.ContenedorSalud], pero contra la red en vez de
 * SQLite.
 *
 * ## El diario, en dos sabores
 *
 * El diario es local-first POR DISENO (`docs/CONTRATOS_BACKEND.md`, seccion
 * 10): tiene que poder escribirse sin cobertura. Por eso el paciente usa
 * [diarioSincronizado], que escribe en su telefono y sube despues. El medico
 * usa [diario], el remoto: las entradas del paciente nunca estan en el
 * telefono del medico, y antes de esto el medico no veia ninguna alerta ROJA.
 */
class ContenedorRed(
    fuenteDeSesion: FuenteDeSesion,
    archivos: ArchivosAdjuntosLocales,
    almacenSinConexion: AlmacenDeRespuestas? = null,
    engine: HttpClientEngineFactory<*> = crearMotorHttp(),
) {
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // Sin conexion, pregunta cada pocos segundos si ya volvio (y en
        // desarrollo, si el servidor cambio de IP); al volver, las pantallas
        // abiertas recargan solas.
        alcance.launch {
            EstadoDeConexion.vigilarRecuperacion { VigiaDeServidor.servidorResponde() }
        }
        // Al cerrar sesion se borran las copias: los datos clinicos de una
        // cuenta no se quedan en el telefono para quien entre despues.
        if (almacenSinConexion != null) {
            alcance.launch {
                var anterior: String? = null
                fuenteDeSesion.sesion
                    .map { it.paciente?.idPaciente ?: it.profesional?.idMedico }
                    .distinctUntilChanged()
                    .collect { actual ->
                        if (anterior != null && actual != anterior) almacenSinConexion.borrarTodo()
                        anterior = actual
                    }
            }
        }
    }

    // Antes que el cliente: el cliente avisa aqui desde su primera respuesta.
    private val _avisosDeSesion = MutableSharedFlow<AvisoDeSesion>(extraBufferCapacity = 16)

    /** El backend dio por cerrada la sesion, o pide elegir contrasena. `App()` decide que hacer. */
    val avisosDeSesion: SharedFlow<AvisoDeSesion> = _avisosDeSesion.asSharedFlow()

    private val cliente = crearClienteHttp(fuenteDeSesion, engine, almacenSinConexion) { _avisosDeSesion.tryEmit(it) }
    private val conexion = ConexionTiempoReal(cliente, fuenteDeSesion, alcance)
    private val baseUrl = ConfiguracionApi.BASE_URL

    val cuentasPacientes = AutenticacionRepositorioRemoto(cliente, baseUrl)
    val cuentasProfesionales = AutenticacionProfesionalRepositorioRemoto(cliente, baseUrl)
    val bajaDeCuenta = BajaDeCuentaRepositorioRemoto(cliente, baseUrl)

    /** El obligatorio tras un reset desde el panel del medico, y el voluntario de Ajustes. */
    val cambioDeContrasena = CambioDeContrasenaRepositorioRemoto(cliente, baseUrl)

    /** El expediente que cierra el onboarding: sin estado propio, una sola instancia basta. */
    val expediente = PacienteRepositorioRemoto(cliente, baseUrl)
    val historial = HistorialMedicoRepositorioRemoto(cliente, baseUrl)
    val adherencia = AdherenciaRepositorioRemoto(cliente, baseUrl)
    val emergencia = PerfilEmergenciaRepositorioRemoto(cliente, baseUrl)
    val pacientesVinculados = PacientesVinculadosRepositorioRemoto(cliente, baseUrl)
    val directorio = DirectorioMedicoRepositorioRemoto(cliente, baseUrl)
    val chat = ChatRepositorioRemoto(cliente, baseUrl, conexion, archivos, EstadoDeConexion.reconexiones)
    val citas = CitasRepositorioRemoto(cliente, baseUrl, conexion)

    /** Fuente del medico: lo que el paciente ya subio. */
    val diario = DiarioRepositorioRemoto(cliente, baseUrl)

    /** Fuente del paciente: [local] primero, subida al servidor en segundo plano. */
    fun diarioSincronizado(local: DiarioRepositorio): DiarioRepositorio =
        DiarioRepositorioSincronizado(local, diario, alcance, EstadoDeConexion.reconexiones)
}

/**
 * Crea (una sola vez por sesion de [fuenteDeSesion]) el contenedor de red.
 * Solo tiene sentido llamarlo cuando [ConfiguracionApi.USAR_BACKEND_REMOTO] es
 * verdadero; `App()` decide eso, no esta funcion.
 */
@Composable
fun recordarContenedorRed(fuenteDeSesion: FuenteDeSesion): ContenedorRed {
    val archivos = rememberArchivosAdjuntosLocales()
    val directorio = rememberDirectorioSinConexion()
    return remember(fuenteDeSesion) {
        ContenedorRed(fuenteDeSesion, archivos, AlmacenDeRespuestasEnArchivos(directorio))
    }
}
