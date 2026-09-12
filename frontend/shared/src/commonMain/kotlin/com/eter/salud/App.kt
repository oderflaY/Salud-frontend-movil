package com.eter.salud

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eter.salud.data.local.recordarContenedorSalud
import com.eter.salud.data.red.ConfiguracionApi
import com.eter.salud.data.red.recordarContenedorRed
import com.eter.salud.data.sesion.recordarAlmacenDeSesion
import com.eter.salud.domain.model.MedicoVinculado
import com.eter.salud.domain.model.SesionPaciente
import com.eter.salud.domain.model.SesionProfesional
import com.eter.salud.domain.nfc.rememberLectorTarjetaNfc
import com.eter.salud.domain.repository.AdherenciaRepositorio
import com.eter.salud.domain.repository.AutenticacionProfesionalRepositorio
import com.eter.salud.domain.repository.AutenticacionRepositorio
import com.eter.salud.domain.repository.ChatRepositorio
import com.eter.salud.domain.repository.CitasRepositorio
import com.eter.salud.domain.repository.DiarioRepositorio
import com.eter.salud.domain.repository.DirectorioMedicoRepositorio
import com.eter.salud.domain.repository.HistorialMedicoRepositorio
import com.eter.salud.domain.repository.PacienteRepositorio
import com.eter.salud.domain.repository.PacientesVinculadosRepositorio
import com.eter.salud.domain.repository.PerfilEmergenciaRepositorio
import com.eter.salud.presentation.agenda.AgendaMedicoViewModel
import com.eter.salud.presentation.bandeja.BandejaViewModel
import com.eter.salud.presentation.inbox.InboxViewModel
import com.eter.salud.presentation.agendapaciente.AgendaPacienteViewModel
import com.eter.salud.presentation.chat.ChatViewModel
import com.eter.salud.presentation.chatmedico.ChatMedicoViewModel
import com.eter.salud.presentation.citas.AgendaCitaViewModel
import com.eter.salud.presentation.configuracion.ConfiguracionViewModel
import com.eter.salud.presentation.diario.DiarioViewModel
import com.eter.salud.presentation.directorio.DescubrimientoMedicoViewModel
import com.eter.salud.presentation.emergencia.EscanerEmergenciaViewModel
import com.eter.salud.presentation.expediente.ExpedienteMedicoViewModel
import com.eter.salud.presentation.home.AccesoRapido
import com.eter.salud.presentation.home.HomeViewModel
import com.eter.salud.presentation.login.LoginViewModel
import com.eter.salud.presentation.navegacion.Destino
import com.eter.salud.presentation.navegacion.NavegacionViewModel
import com.eter.salud.presentation.navegacion.SECCIONES_PACIENTE
import com.eter.salud.presentation.navegacion.SECCIONES_PROFESIONAL
import com.eter.salud.presentation.onboarding.OnboardingPacienteViewModel
import com.eter.salud.presentation.perfil.PerfilMedicoViewModel
import com.eter.salud.presentation.profesional.HomeProfesionalViewModel
import com.eter.salud.presentation.profesional.LoginProfesionalViewModel
import com.eter.salud.presentation.profesional.RegistroProfesionalViewModel
import com.eter.salud.presentation.registro.RegistroViewModel
import com.eter.salud.presentation.sesion.SesionViewModel
import com.eter.salud.ui.agenda.AgendaMedicoScreen
import com.eter.salud.ui.bandeja.BandejaScreen
import com.eter.salud.ui.inbox.InboxScreen
import com.eter.salud.ui.agendapaciente.AgendaPacienteScreen
import com.eter.salud.ui.chat.ChatScreen
import com.eter.salud.ui.chatmedico.ChatMedicoScreen
import com.eter.salud.ui.citas.PanelAgendaCita
import com.eter.salud.ui.componentes.IsotipoSalud
import com.eter.salud.ui.configuracion.ConfiguracionScreen
import com.eter.salud.ui.diario.DiarioScreen
import com.eter.salud.ui.directorio.MiMedicoScreen
import com.eter.salud.ui.emergencia.EscanerEmergenciaScreen
import com.eter.salud.ui.expediente.ExpedienteMedicoScreen
import com.eter.salud.ui.home.HomeScreen
import com.eter.salud.ui.login.LoginScreen
import com.eter.salud.ui.navegacion.AnfitrionNavegacion
import com.eter.salud.ui.navegacion.BarraSecciones
import com.eter.salud.ui.onboarding.OnboardingPacienteScreen
import com.eter.salud.ui.perfil.PerfilMedicoScreen
import com.eter.salud.ui.plataforma.ManejadorDeRetroceso
import com.eter.salud.ui.plataforma.recordarMinimizadorDeApp
import com.eter.salud.ui.profesional.HomeProfesionalScreen
import com.eter.salud.ui.profesional.LoginProfesionalScreen
import com.eter.salud.ui.profesional.RegistroProfesionalScreen
import com.eter.salud.ui.registro.RegistroScreen
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import com.eter.salud.ui.theme.SaludTheme
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_app_restaurando
import salud.shared.generated.resources.app_restaurando
import salud.shared.generated.resources.nfc_titulo

/**
 * Punto de entrada compartido.
 *
 * Monta tres cosas y ninguna mas: el tema (que resuelve Modo Claro/Oscuro), la
 * pila de navegacion, y el reparto de destinos a pantallas.
 *
 * ## Como esta organizada la navegacion
 *
 * La version anterior era un `enum` en una variable y una cadena de `if`. No
 * habia pila, asi que cada pantalla tenia que saber a donde "volver", el boton
 * Atras del sistema cerraba la app desde cualquier sitio, y moverse entre las
 * zonas principales obligaba a pasar siempre por el panel central.
 *
 * Ahora hay tres piezas separadas:
 *
 *  - [NavegacionViewModel] guarda la pila y sobrevive a girar el telefono.
 *  - [AnfitrionNavegacion] dibuja la cima de la pila y anima el paso.
 *  - [BarraSecciones] da acceso de un toque a las zonas del portal activo.
 *
 * ## Como se protege el escaner de emergencia
 *
 * [Destino.Escaner] es el destino mas sensible de la app: abre datos clinicos de
 * un paciente inconsciente. Solo se resuelve dentro de la rama que exige una
 * `sesionProfesional` viva. Poner ese destino en la pila sin sesion de personal
 * medico no dibuja el escaner: cae en el reparto de acceso, que devuelve al
 * login. La garantia es estructural, no una comprobacion que alguien pueda
 * olvidar.
 *
 * Los repositorios en memoria son provisionales: se cambiaran por los clientes
 * HTTP del backend en Go sin tocar las vistas ni los ViewModels.
 */
@Composable
@Preview
fun App() {
    // El tema lee la MISMA instancia que la pantalla de configuracion (misma
    // clave en el ViewModelStore): asi el interruptor de modo oscuro repinta
    // la app entera al instante, sin reiniciar nada.
    val configuracion = viewModel(key = CLAVE_CONFIGURACION) { ConfiguracionViewModel() }
    val ajustes by configuracion.estado.collectAsStateWithLifecycle()

    SaludTheme(modoOscuro = ajustes.modoOscuroEfectivo(sistemaEnOscuro = isSystemInDarkTheme())) {
        // La sesion vive en un ViewModel, no en un `remember`: un `remember` se
        // pierde cuando el sistema recrea la Activity -- al girar el telefono, al
        // plegarlo, al volver de una llamada -- y eso devolvia al paciente a la
        // pantalla de acceso en mitad de lo que estuviera haciendo.
        val contenedor = recordarContenedorSalud()
        // La primera vez siembra la demostracion; despues solo carga la agenda.
        val baseLista by produceState(false, contenedor) {
            contenedor.preparar()
            value = true
        }
        val almacenDeSesion = recordarAlmacenDeSesion()
        val sesiones = viewModel { SesionViewModel(almacenDeSesion) }
        val estadoSesion by sesiones.estado.collectAsStateWithLifecycle()

        val navegacion = viewModel { NavegacionViewModel(Destino.Acceso) }
        val pila by navegacion.pila.collectAsStateWithLifecycle()

        // Mientras se lee el disco NO se pinta el acceso: hacerlo y saltar al
        // panel de golpe es un parpadeo que hace dudar de si la sesion se
        // perdio. Pero tampoco se deja la pantalla en blanco -- si la lectura
        // tarda, o el archivo esta danado y el flujo no emite, el usuario se
        // queda ante un vacio sin explicacion. Se muestra que esta pasando.
        if (estadoSesion.restaurando || !baseLista) {
            PantallaDeArranque()
            return@SaludTheme
        }

        // Una sesion restaurada tiene que llevar al portal que le toca. Sin esto
        // la app arrancaria en el acceso con la sesion viva detras.
        LaunchedEffect(estadoSesion.hayAlguienDentro) {
            when {
                estadoSesion.profesional != null && pila.actual == Destino.Acceso ->
                    navegacion.reiniciarEn(Destino.PanelProfesional)

                estadoSesion.paciente != null && pila.actual == Destino.Acceso ->
                    navegacion.reiniciarEn(Destino.Inicio)
            }
        }

        // Politica unica del retroceso del sistema, en un solo sitio.
        //
        // Con pila, retrocede. En la raiz, MINIMIZA en vez de cerrar: cerrar
        // destruye el proceso y con el la sesion, asi que pulsar Atras una vez de
        // mas en el panel principal expulsaba al paciente de la app.
        val minimizar = recordarMinimizadorDeApp()
        ManejadorDeRetroceso(habilitado = true) {
            if (pila.puedeVolver) navegacion.volver() else minimizar()
        }

        // Todo sale de la base SQLite local, a traves de un solo contenedor por
        // proceso, MIENTRAS `ConfiguracionApi.USAR_BACKEND_REMOTO` siga en falso.
        // En cuanto haya un backend real que cumpla `docs/CONTRATOS_BACKEND.md`,
        // fijar esa bandera (y `ConfiguracionApi.BASE_URL`) cambia cada
        // repositorio por su version HTTP sin tocar ninguna Vista ni ViewModel:
        // ambos lados del `?:` implementan el mismo contrato de dominio.
        val contenedorRed = if (ConfiguracionApi.USAR_BACKEND_REMOTO) {
            recordarContenedorRed(almacenDeSesion)
        } else {
            null
        }
        val repositorioCuentas = contenedorRed?.cuentasPacientes ?: contenedor.cuentasPacientes
        val repositorioHistorial = contenedorRed?.historial ?: contenedor.historial
        val repositorioAdherencia = contenedorRed?.adherencia ?: contenedor.adherencia
        val repositorioCuentasProfesionales = contenedorRed?.cuentasProfesionales ?: contenedor.cuentasProfesionales
        val repositorioPacientesVinculados = contenedorRed?.pacientesVinculados ?: contenedor.pacientesVinculados
        val repositorioEmergencia = contenedorRed?.emergencia ?: contenedor.emergencia
        val repositorioDirectorio = contenedorRed?.directorio ?: contenedor.directorio
        val repositorioChat = contenedorRed?.chat ?: contenedor.chat
        // Una sola instancia para los dos portales: es lo que hace que la cita
        // que el paciente confirma en el chat aparezca en el calendario del
        // medico sin recargar nada.
        val repositorioCitas = contenedorRed?.citas ?: contenedor.citas
        // El diario se queda SIEMPRE local-first, incluso con el backend
        // encendido: es una decision de diseno del contrato, no una pieza
        // pendiente de conectar (ver `ContenedorRed` y `DiarioRepositorioRemoto`).
        val repositorioDiario = contenedor.diario
        val expedienteNuevo: (String) -> PacienteRepositorio = { idPaciente ->
            contenedorRed?.expediente ?: contenedor.expedienteNuevo(idPaciente)
        }

        val profesionalActual = estadoSesion.profesional
        val pacienteActual = estadoSesion.paciente

        // El panel del paciente se crea FUERA del reparto de destinos porque dos
        // pantallas distintas lo necesitan: el propio Inicio y el final del
        // onboarding, que le avisa de que el perfil quedo completo. Creado
        // dentro, cada una tendria su copia y el aviso se perderia.
        val inicioPaciente = pacienteActual?.let { activa ->
            viewModel(key = "$CLAVE_INICIO${activa.idPaciente}") {
                HomeViewModel(
                    historial = repositorioHistorial,
                    adherencia = repositorioAdherencia,
                    idPaciente = activa.idPaciente,
                    perfilEmergenciaPendiente = activa.requiereOnboarding,
                )
            }
        }

        // Sube a este nivel porque lo necesitan dos sitios: la seccion "Mi
        // medico" y la barra inferior, que cuenta los mensajes sin leer de la
        // conversacion con ese doctor. La clave es la misma, asi que sigue
        // siendo una sola instancia.
        val descubrimiento = pacienteActual?.let { activa ->
            viewModel(key = "$CLAVE_DIRECTORIO${activa.idPaciente}") {
                DescubrimientoMedicoViewModel(repositorioDirectorio, activa.idPaciente)
            }
        }
        val idConversacion = descubrimiento?.estado
            ?.collectAsStateWithLifecycle()?.value?.medicoVinculado?.idConversacion
        val sinLeer by produceState(0, idConversacion) {
            val conversacion = idConversacion
            if (conversacion == null) {
                value = 0
            } else {
                repositorioChat.mensajesSinLeer(conversacion).collect { value = it }
            }
        }

        val secciones = when {
            profesionalActual != null -> SECCIONES_PROFESIONAL
            // Sin expediente no hay pestanas: la unica salida del cuestionario
            // es terminarlo (o cerrar sesion desde su primera pantalla).
            pacienteActual != null && !pacienteActual.requiereOnboarding -> SECCIONES_PACIENTE
            else -> emptyList()
        }
        val raizDeSeccion =
            if (profesionalActual != null) Destino.PanelProfesional else Destino.Inicio

        Column(Modifier.fillMaxSize()) {
            AnfitrionNavegacion(pila = pila, modifier = Modifier.weight(1f)) { destino ->
                when {
                    profesionalActual != null -> PantallaProfesional(
                        destino = destino,
                        sesion = profesionalActual,
                        navegacion = navegacion,
                        repositorioPacientesVinculados = repositorioPacientesVinculados,
                        repositorioEmergencia = repositorioEmergencia,
                        repositorioChat = repositorioChat,
                        repositorioCitas = repositorioCitas,
                        repositorioDiario = repositorioDiario,
                        repositorioHistorial = repositorioHistorial,
                        alCerrarSesion = {
                            sesiones.cerrar()
                            navegacion.reiniciarEn(Destino.AccesoProfesional)
                        },
                    )

                    // Una cuenta recien creada TIENE que capturar su expediente
                    // antes de ver nada mas. La compuerta no depende del destino
                    // de la pila: ni un destino guardado ni un Atras la saltan, y
                    // cerrar la app a la mitad la vuelve a mostrar al entrar.
                    pacienteActual != null && pacienteActual.requiereOnboarding -> ExpedienteObligatorio(
                        idPaciente = pacienteActual.idPaciente,
                        repositorio = remember(pacienteActual.idPaciente) {
                            expedienteNuevo(pacienteActual.idPaciente)
                        },
                        alTerminar = {
                            // La sesion se reescribe (tambien en disco) ya sin la
                            // marca: el proximo arranque entra directo al panel.
                            sesiones.abrirComoPaciente(pacienteActual.copy(requiereOnboarding = false))
                            inicioPaciente?.marcarPerfilCompletado()
                            navegacion.reiniciarEn(Destino.Inicio)
                        },
                        alCerrarSesion = {
                            sesiones.cerrar()
                            navegacion.reiniciarEn(Destino.Acceso)
                        },
                    )

                    pacienteActual != null && inicioPaciente != null && descubrimiento != null ->
                        PantallaPaciente(
                        destino = destino,
                        sesion = pacienteActual,
                        inicio = inicioPaciente,
                        descubrimiento = descubrimiento,
                        navegacion = navegacion,
                        repositorioHistorial = repositorioHistorial,
                        repositorioDirectorio = repositorioDirectorio,
                        repositorioChat = repositorioChat,
                        repositorioCitas = repositorioCitas,
                        repositorioDiario = repositorioDiario,
                        repositorioAdherencia = repositorioAdherencia,
                        repositorioExpediente = remember(pacienteActual.idPaciente) {
                            expedienteNuevo(pacienteActual.idPaciente)
                        },
                        alCerrarSesion = {
                            sesiones.cerrar()
                            navegacion.reiniciarEn(Destino.Acceso)
                        },
                    )

                    else -> PantallaDeAcceso(
                        destino = destino,
                        navegacion = navegacion,
                        repositorioCuentas = repositorioCuentas,
                        repositorioCuentasProfesionales = repositorioCuentasProfesionales,
                        // `reiniciarEn` vacia la pila entera: tras entrar, el
                        // boton Atras NO puede devolver al formulario de acceso.
                        alAbrirSesionPaciente = { nueva ->
                            sesiones.abrirComoPaciente(nueva)
                            navegacion.reiniciarEn(Destino.Inicio)
                        },
                        alAbrirSesionProfesional = { nueva ->
                            sesiones.abrirComoProfesional(nueva)
                            navegacion.reiniciarEn(Destino.PanelProfesional)
                        },
                    )
                }
            }

            // La barra solo acompana a las secciones NO inmersivas. Sobre una
            // pantalla apilada (un chat, el cuestionario) estorbaria: ahi la
            // unica salida debe ser volver. Y sobre el escaner de emergencia
            // sobra del todo: se usa junto a un paciente inconsciente y tres
            // pestanas al lado del pulgar solo invitan a un toque equivocado.
            if (secciones.isNotEmpty() &&
                pila.actual is Destino.Seccion &&
                !pila.actual.esInmersivo
            ) {
                BarraSecciones(
                    secciones = secciones,
                    activa = pila.seccionActiva,
                    alElegir = { navegacion.irASeccion(it, raizDeSeccion) },
                    sinLeer = if (sinLeer > 0) mapOf(Destino.MiMedico to sinLeer) else emptyMap(),
                )
            }
        }
    }
}

/**
 * Lo que se ve mientras se restaura la sesion del disco.
 *
 * Dura milisegundos en un telefono sano, pero existe para el que no lo esta: un
 * aparato cargado, un arranque en frio, un archivo de sesion danado. En todos
 * esos casos la alternativa era una pantalla en blanco indefinida.
 */
@Composable
private fun PantallaDeArranque() {
    val colores = LocalColoresSalud.current
    val espaciado = LocalEspaciadoSalud.current
    val descripcion = stringResource(Res.string.a11y_app_restaurando)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .semantics(mergeDescendants = true) {
                contentDescription = descripcion
                liveRegion = LiveRegionMode.Polite
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IsotipoSalud()
        Spacer(Modifier.height(espaciado.generoso))
        CircularProgressIndicator(color = colores.acentoAccion)
        Spacer(Modifier.height(espaciado.medio))
        Text(
            text = stringResource(Res.string.app_restaurando),
            style = MaterialTheme.typography.bodyMedium,
            color = colores.textoSecundario,
        )
    }
}

// ------------------------------------------------------------ Portal de acceso

@Composable
private fun PantallaDeAcceso(
    destino: Destino,
    navegacion: NavegacionViewModel,
    repositorioCuentas: AutenticacionRepositorio,
    repositorioCuentasProfesionales: AutenticacionProfesionalRepositorio,
    alAbrirSesionPaciente: (SesionPaciente) -> Unit,
    alAbrirSesionProfesional: (SesionProfesional) -> Unit,
) {
    when (destino) {
        Destino.Registro -> {
            val registro = viewModel { RegistroViewModel(repositorioCuentas) }
            RegistroScreen(
                viewModel = registro,
                alCrearCuenta = alAbrirSesionPaciente,
                alVolverAlAcceso = navegacion::volver,
            )
        }

        Destino.RegistroProfesional -> {
            val registro = viewModel { RegistroProfesionalViewModel(repositorioCuentasProfesionales) }
            RegistroProfesionalScreen(
                viewModel = registro,
                alCrearCuenta = alAbrirSesionProfesional,
                alVolverAlAcceso = navegacion::volver,
            )
        }

        Destino.AccesoProfesional -> {
            val login = viewModel { LoginProfesionalViewModel(repositorioCuentasProfesionales) }
            LoginProfesionalScreen(
                viewModel = login,
                alIniciarSesion = alAbrirSesionProfesional,
                alRegistrarse = { navegacion.ir(Destino.RegistroProfesional) },
                alVolverAPaciente = { navegacion.reiniciarEn(Destino.Acceso) },
            )
        }

        // Cualquier otro destino sin sesion cae aqui. Es la red de seguridad que
        // hace imposible alcanzar una pantalla clinica sin autenticarse, incluso
        // si la pila quedara en un destino que no toca.
        else -> {
            val login = viewModel { LoginViewModel(repositorioCuentas) }
            LoginScreen(
                viewModel = login,
                alIniciarSesion = alAbrirSesionPaciente,
                alRegistrarse = { navegacion.ir(Destino.Registro) },
                alAccesoProfesional = { navegacion.reiniciarEn(Destino.AccesoProfesional) },
            )
        }
    }
}

// ---------------------------------------------------------- Portal de paciente

@Composable
private fun PantallaPaciente(
    destino: Destino,
    sesion: SesionPaciente,
    inicio: HomeViewModel,
    descubrimiento: DescubrimientoMedicoViewModel,
    navegacion: NavegacionViewModel,
    repositorioHistorial: HistorialMedicoRepositorio,
    repositorioDirectorio: DirectorioMedicoRepositorio,
    repositorioChat: ChatRepositorio,
    repositorioCitas: CitasRepositorio,
    repositorioDiario: DiarioRepositorio,
    repositorioAdherencia: AdherenciaRepositorio,
    repositorioExpediente: PacienteRepositorio,
    alCerrarSesion: () -> Unit,
) {
    when (destino) {
        Destino.Diario -> {
            val diario = viewModel(key = "$CLAVE_DIARIO${sesion.idPaciente}") {
                DiarioViewModel(repositorioDiario, sesion.idPaciente)
            }
            DiarioScreen(viewModel = diario, alVolver = navegacion::volver)
        }

        Destino.Onboarding -> ExpedienteObligatorio(
            idPaciente = sesion.idPaciente,
            repositorio = repositorioExpediente,
            alTerminar = {
                inicio.marcarPerfilCompletado()
                // Vuelve al panel en vez de apilarse encima: el cuestionario
                // esta terminado y dejarlo en la pila permitiria volver a el.
                navegacion.volverHasta(Destino.Inicio)
            },
        )

        Destino.Historial -> {
            // Con clave por paciente: sin ella, quien entrara despues de cerrar
            // sesion en el mismo telefono veria un instante el expediente de
            // la cuenta anterior mientras carga el suyo.
            val perfil = viewModel(key = "$CLAVE_PERFIL${sesion.idPaciente}") {
                PerfilMedicoViewModel(repositorioHistorial)
            }
            // Sin `alVolver`: es una raiz de pestana, no una pantalla apilada.
            PerfilMedicoScreen(viewModel = perfil, idPaciente = sesion.idPaciente)
        }

        // La seccion es la FICHA del medico, nunca la conversacion. El chat es
        // un destino apilado: una teleconsulta necesita la pantalla entera, y
        // con la barra de secciones abajo el teclado colisiona con el campo.
        Destino.MiMedico -> {
            val bandeja = viewModel(key = "$CLAVE_BANDEJA${sesion.idPaciente}") {
                BandejaViewModel(repositorioDirectorio, repositorioChat, sesion.idPaciente)
            }
            BandejaScreen(
                viewModel = bandeja,
                alAbrirConversacion = { medico -> navegacion.ir(Destino.ChatConMedico(medico)) },
                alBuscarMedico = { navegacion.ir(Destino.Directorio) },
            )
        }

        Destino.AgendaPaciente -> {
            val agendaPaciente = viewModel(key = "$CLAVE_AGENDA_PACIENTE${sesion.idPaciente}") {
                AgendaPacienteViewModel(
                    adherencia = repositorioAdherencia,
                    citas = repositorioCitas,
                    directorio = repositorioDirectorio,
                    idPaciente = sesion.idPaciente,
                )
            }
            AgendaPacienteScreen(viewModel = agendaPaciente)
        }

        Destino.Directorio -> MiMedicoScreen(
            viewModel = descubrimiento,
            alVolver = navegacion::volver,
            alIniciarConsulta = { medico ->
                // Tras vincularse se vuelve a la bandeja y se entra al chat: el
                // directorio ya cumplio su proposito y dejarlo en la pila haria
                // que Atras devolviera a una lista donde ese doctor ya no esta
                // disponible para vincular.
                navegacion.volverHasta(Destino.MiMedico)
                navegacion.ir(Destino.ChatConMedico(medico))
                // La proxima visita al directorio abre la lista, no esta ficha.
                descubrimiento.cerrarFicha()
            },
        )

        Destino.Configuracion -> {
            val configuracion = viewModel(key = CLAVE_CONFIGURACION) { ConfiguracionViewModel() }
            ConfiguracionScreen(
                viewModel = configuracion,
                alVolver = navegacion::volver,
                alCerrarSesion = alCerrarSesion,
            )
        }

        is Destino.ChatConMedico -> ChatDelPaciente(
            medico = destino.medico,
            idPaciente = sesion.idPaciente,
            repositorioChat = repositorioChat,
            repositorioCitas = repositorioCitas,
            repositorioDirectorio = repositorioDirectorio,
            alVolver = navegacion::volver,
        )

        else -> HomeScreen(
            viewModel = inicio,
            alCompletarPerfil = { navegacion.ir(Destino.Onboarding) },
            alAbrirAcceso = { acceso ->
                when (acceso) {
                    AccesoRapido.HISTORIAL -> navegacion.irASeccion(Destino.Historial, Destino.Inicio)
                    AccesoRapido.MIS_MEDICOS -> navegacion.irASeccion(Destino.MiMedico, Destino.Inicio)
                    AccesoRapido.DIARIO_SINTOMAS -> navegacion.ir(Destino.Diario)
                    else -> Unit
                }
            },
            alAbrirAjustes = { navegacion.ir(Destino.Configuracion) },
            alAbrirAgenda = { navegacion.irASeccion(Destino.AgendaPaciente, Destino.Inicio) },
        )
    }
}

/**
 * El cuestionario del expediente medico de una cuenta.
 *
 * Su ViewModel lleva la clave del paciente: con la clave por defecto, la
 * segunda cuenta creada en el mismo telefono heredaria el cuestionario ya
 * enviado de la primera y saltaria directo a "perfil completo".
 */
@Composable
private fun ExpedienteObligatorio(
    idPaciente: String,
    repositorio: PacienteRepositorio,
    alTerminar: () -> Unit,
    alCerrarSesion: (() -> Unit)? = null,
) {
    val onboarding = viewModel(key = "$CLAVE_ONBOARDING$idPaciente") { OnboardingPacienteViewModel(repositorio) }
    OnboardingPacienteScreen(
        viewModel = onboarding,
        alTerminarRegistro = alTerminar,
        alCerrarSesion = alCerrarSesion,
    )
}

@Composable
private fun ChatDelPaciente(
    medico: MedicoVinculado,
    idPaciente: String,
    repositorioChat: ChatRepositorio,
    repositorioCitas: CitasRepositorio,
    repositorioDirectorio: DirectorioMedicoRepositorio,
    alVolver: () -> Unit,
) {
    val chat = viewModel(key = "$CLAVE_CHAT${medico.idConversacion}") {
        ChatViewModel(
            repositorio = repositorioChat,
            idConversacion = medico.idConversacion,
            nombreMedico = medico.nombreCompleto,
        )
    }
    val agendaCita = viewModel(key = "$CLAVE_AGENDA_CITA${medico.idConversacion}") {
        AgendaCitaViewModel(
            citas = repositorioCitas,
            directorio = repositorioDirectorio,
            idPaciente = idPaciente,
            medicoVinculado = medico,
        )
    }
    ChatScreen(
        viewModel = chat,
        alVolver = alVolver,
        alEnviarMensaje = agendaCita::evaluarMensaje,
        alAgendarCita = agendaCita::iniciar,
        panelAgenda = { PanelAgendaCita(agendaCita) },
    )
}

// ------------------------------------------------------- Portal profesional

@Composable
private fun PantallaProfesional(
    destino: Destino,
    sesion: SesionProfesional,
    navegacion: NavegacionViewModel,
    repositorioPacientesVinculados: PacientesVinculadosRepositorio,
    repositorioEmergencia: PerfilEmergenciaRepositorio,
    repositorioChat: ChatRepositorio,
    repositorioCitas: CitasRepositorio,
    repositorioDiario: DiarioRepositorio,
    repositorioHistorial: HistorialMedicoRepositorio,
    alCerrarSesion: () -> Unit,
) {
    when (destino) {
        // Unico punto de la app donde se instancia el escaner, y esta dentro de
        // la rama que ya exige sesion de personal medico.
        Destino.Escaner -> {
            val mensajeDeLaHoja = stringResource(Res.string.nfc_titulo)
            val lector = rememberLectorTarjetaNfc(mensajeDeLaHoja)
            val escaner = viewModel { EscanerEmergenciaViewModel(lector, repositorioEmergencia) }
            EscanerEmergenciaScreen(viewModel = escaner, alVolver = navegacion::volver)
        }

        Destino.BandejaClinica -> {
            val bandejaClinica = viewModel(key = "$CLAVE_BANDEJA_CLINICA${sesion.idMedico}") {
                InboxViewModel(
                    pacientesVinculados = repositorioPacientesVinculados,
                    chat = repositorioChat,
                    diario = repositorioDiario,
                    idMedico = sesion.idMedico,
                )
            }
            InboxScreen(
                viewModel = bandejaClinica,
                alAbrirConversacion = { paciente -> navegacion.ir(Destino.ChatConPaciente(paciente)) },
                // Sin destino todavia: el portal del medico aun no tiene
                // directorio de especialistas. El control existe por diseno y
                // se cablea aqui cuando exista la pantalla de interconsulta.
                alNuevoEspecialista = {},
            )
        }

        Destino.Agenda -> {
            val agenda = viewModel(key = "$CLAVE_AGENDA${sesion.idMedico}") {
                AgendaMedicoViewModel(
                    repositorio = repositorioCitas,
                    idMedico = sesion.idMedico,
                    pacientesVinculados = repositorioPacientesVinculados,
                )
            }
            AgendaMedicoScreen(
                viewModel = agenda,
                alAbrirExpediente = { cita ->
                    navegacion.ir(Destino.ExpedientePaciente(cita.idPaciente))
                },
            )
        }

        Destino.Configuracion -> {
            val configuracion = viewModel(key = CLAVE_CONFIGURACION) { ConfiguracionViewModel() }
            ConfiguracionScreen(
                viewModel = configuracion,
                alVolver = navegacion::volver,
                alCerrarSesion = alCerrarSesion,
            )
        }

        is Destino.ChatConPaciente -> {
            val paciente = destino.paciente
            val chatMedico = viewModel(key = "$CLAVE_CHAT_MEDICO${paciente.idConversacion}") {
                ChatMedicoViewModel(
                    repositorio = repositorioChat,
                    idConversacion = paciente.idConversacion,
                    nombrePaciente = paciente.nombreCompleto,
                    riesgoPaciente = paciente.riesgo,
                )
            }
            ChatMedicoScreen(
                viewModel = chatMedico,
                alVolver = navegacion::volver,
                alAbrirExpediente = {
                    navegacion.ir(Destino.ExpedientePaciente(paciente.idPaciente, paciente.nombreCompleto))
                },
            )
        }

        is Destino.ExpedientePaciente -> {
            val expediente = viewModel(key = "$CLAVE_EXPEDIENTE${destino.idPaciente}") {
                ExpedienteMedicoViewModel(
                    repositorio = repositorioHistorial,
                    idPaciente = destino.idPaciente,
                )
            }
            ExpedienteMedicoScreen(
                viewModel = expediente,
                nombreReferencia = destino.nombreReferencia,
                alVolver = navegacion::volver,
            )
        }

        else -> {
            val inicioProfesional = viewModel(key = "$CLAVE_INICIO_PROFESIONAL${sesion.idMedico}") {
                HomeProfesionalViewModel(
                    pacientesVinculados = repositorioPacientesVinculados,
                    diario = repositorioDiario,
                    idMedico = sesion.idMedico,
                    tratamiento = sesion.tratamiento,
                    apellidos = sesion.apellidos,
                    estadoVerificacion = sesion.estadoVerificacion,
                )
            }
            HomeProfesionalScreen(
                viewModel = inicioProfesional,
                alEscanearTarjeta = { navegacion.irASeccion(Destino.Escaner, Destino.PanelProfesional) },
                alAbrirAgenda = { navegacion.irASeccion(Destino.Agenda, Destino.PanelProfesional) },
                alAbrirChat = { paciente -> navegacion.ir(Destino.ChatConPaciente(paciente)) },
                alAbrirAjustes = { navegacion.ir(Destino.Configuracion) },
            )
        }
    }
}

/**
 * Prefijos de las claves del `ViewModelStore`.
 *
 * `viewModel(key = ...)` guarda por la clave EXACTA, sin combinarla con la
 * clase: dos ViewModel distintos bajo la misma clave se expulsan y se destruyen
 * mutuamente en cada recomposicion. El prefijo por rol garantiza que eso no
 * pueda volver a pasar aunque el identificador de fondo coincida.
 */
private const val CLAVE_INICIO = "inicio_paciente_"
private const val CLAVE_DIRECTORIO = "directorio_medico_"
private const val CLAVE_CHAT = "chat_"
private const val CLAVE_INICIO_PROFESIONAL = "inicio_profesional_"
private const val CLAVE_CHAT_MEDICO = "chat_medico_"
private const val CLAVE_AGENDA = "agenda_medico_"
private const val CLAVE_AGENDA_CITA = "agenda_cita_"
private const val CLAVE_BANDEJA = "bandeja_"
private const val CLAVE_BANDEJA_CLINICA = "bandeja_clinica_"
private const val CLAVE_AGENDA_PACIENTE = "agenda_paciente_"
private const val CLAVE_DIARIO = "diario_"
private const val CLAVE_ONBOARDING = "onboarding_"
private const val CLAVE_PERFIL = "perfil_medico_"
private const val CLAVE_EXPEDIENTE = "expediente_medico_"

/** Sin sufijo: las preferencias son del dispositivo, no de una sesion. */
private const val CLAVE_CONFIGURACION = "configuracion"
