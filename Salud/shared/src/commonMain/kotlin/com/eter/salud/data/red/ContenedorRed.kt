package com.eter.salud.data.red

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.eter.salud.data.adjuntos.ArchivosAdjuntosLocales
import com.eter.salud.data.adjuntos.rememberArchivosAdjuntosLocales
import com.eter.salud.data.repository.AdherenciaRepositorioRemoto
import com.eter.salud.data.repository.AutenticacionProfesionalRepositorioRemoto
import com.eter.salud.data.repository.AutenticacionRepositorioRemoto
import com.eter.salud.data.repository.ChatRepositorioRemoto
import com.eter.salud.data.repository.CitasRepositorioRemoto
import com.eter.salud.data.repository.DirectorioMedicoRepositorioRemoto
import com.eter.salud.data.repository.HistorialMedicoRepositorioRemoto
import com.eter.salud.data.repository.PacienteRepositorioRemoto
import com.eter.salud.data.repository.PacientesVinculadosRepositorioRemoto
import com.eter.salud.data.repository.PerfilEmergenciaRepositorioRemoto
import com.eter.salud.data.sesion.FuenteDeSesion
import io.ktor.client.engine.HttpClientEngineFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Los repositorios `*Remoto` de la app, todos sobre el mismo [HttpClient] y la
 * misma conexion de tiempo real -- analogo a
 * [com.eter.salud.data.local.ContenedorSalud], pero contra la red en vez de
 * SQLite.
 *
 * ## Por que el diario no esta aqui
 *
 * `DiarioRepositorio` se queda siempre en
 * [com.eter.salud.data.repository.DiarioRepositorioLocal]: el diario es
 * local-first POR DISENO (`docs/CONTRATOS_BACKEND.md`, seccion 10), no una
 * pieza pendiente de "migrar" a la red. `DiarioRepositorioRemoto` existe y esta
 * probado, pero conectarlo aqui deshaceria justo la garantia que el diario
 * necesita: poder escribirse sin cobertura.
 */
class ContenedorRed(
    fuenteDeSesion: FuenteDeSesion,
    archivos: ArchivosAdjuntosLocales,
    engine: HttpClientEngineFactory<*> = crearMotorHttp(),
) {
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val cliente = crearClienteHttp(fuenteDeSesion, engine)
    private val conexion = ConexionTiempoReal(cliente, fuenteDeSesion, alcance)
    private val baseUrl = ConfiguracionApi.BASE_URL

    val cuentasPacientes = AutenticacionRepositorioRemoto(cliente, baseUrl)
    val cuentasProfesionales = AutenticacionProfesionalRepositorioRemoto(cliente, baseUrl)

    /** El expediente que cierra el onboarding: sin estado propio, una sola instancia basta. */
    val expediente = PacienteRepositorioRemoto(cliente, baseUrl)
    val historial = HistorialMedicoRepositorioRemoto(cliente, baseUrl)
    val adherencia = AdherenciaRepositorioRemoto(cliente, baseUrl)
    val emergencia = PerfilEmergenciaRepositorioRemoto(cliente, baseUrl)
    val pacientesVinculados = PacientesVinculadosRepositorioRemoto(cliente, baseUrl)
    val directorio = DirectorioMedicoRepositorioRemoto(cliente, baseUrl)
    val chat = ChatRepositorioRemoto(cliente, baseUrl, conexion, archivos)
    val citas = CitasRepositorioRemoto(cliente, baseUrl, conexion)
}

/**
 * Crea (una sola vez por sesion de [fuenteDeSesion]) el contenedor de red.
 * Solo tiene sentido llamarlo cuando [ConfiguracionApi.USAR_BACKEND_REMOTO] es
 * verdadero; `App()` decide eso, no esta funcion.
 */
@Composable
fun recordarContenedorRed(fuenteDeSesion: FuenteDeSesion): ContenedorRed {
    val archivos = rememberArchivosAdjuntosLocales()
    return remember(fuenteDeSesion) { ContenedorRed(fuenteDeSesion, archivos) }
}
