package com.eter.salud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eter.salud.data.repository.AdherenciaRepositorioEnMemoria
import com.eter.salud.data.repository.AutenticacionProfesionalRepositorioEnMemoria
import com.eter.salud.data.repository.AutenticacionRepositorioEnMemoria
import com.eter.salud.data.repository.ChatRepositorioEnMemoria
import com.eter.salud.data.repository.CitasRepositorioEnMemoria
import com.eter.salud.data.repository.DirectorioMedicoRepositorioMock
import com.eter.salud.data.repository.HistorialMedicoRepositorioEnMemoria
import com.eter.salud.data.repository.PacienteRepositorioEnMemoria
import com.eter.salud.data.repository.PacientesVinculadosRepositorioMock
import com.eter.salud.data.repository.PerfilEmergenciaRepositorioMock
import com.eter.salud.domain.model.PacienteVinculado
import com.eter.salud.domain.model.SesionPaciente
import com.eter.salud.domain.model.SesionProfesional
import com.eter.salud.domain.nfc.rememberLectorTarjetaNfc
import com.eter.salud.presentation.agenda.AgendaMedicoViewModel
import com.eter.salud.presentation.chat.ChatViewModel
import com.eter.salud.presentation.citas.AgendaCitaViewModel
import com.eter.salud.presentation.chatmedico.ChatMedicoViewModel
import com.eter.salud.presentation.directorio.DescubrimientoMedicoViewModel
import com.eter.salud.presentation.emergencia.EscanerEmergenciaViewModel
import com.eter.salud.presentation.home.AccesoRapido
import com.eter.salud.presentation.home.HomeViewModel
import com.eter.salud.presentation.login.LoginViewModel
import com.eter.salud.presentation.onboarding.OnboardingPacienteViewModel
import com.eter.salud.presentation.perfil.PerfilMedicoViewModel
import com.eter.salud.presentation.profesional.HomeProfesionalViewModel
import com.eter.salud.presentation.profesional.LoginProfesionalViewModel
import com.eter.salud.presentation.profesional.RegistroProfesionalViewModel
import com.eter.salud.presentation.registro.RegistroViewModel
import com.eter.salud.ui.agenda.AgendaMedicoScreen
import com.eter.salud.ui.chat.ChatScreen
import com.eter.salud.ui.citas.PanelAgendaCita
import com.eter.salud.ui.chatmedico.ChatMedicoScreen
import com.eter.salud.ui.directorio.MiMedicoScreen
import com.eter.salud.ui.emergencia.EscanerEmergenciaScreen
import com.eter.salud.ui.home.HomeScreen
import com.eter.salud.ui.login.LoginScreen
import com.eter.salud.ui.onboarding.OnboardingPacienteScreen
import com.eter.salud.ui.perfil.PerfilMedicoScreen
import com.eter.salud.ui.profesional.HomeProfesionalScreen
import com.eter.salud.ui.profesional.LoginProfesionalScreen
import com.eter.salud.ui.profesional.RegistroProfesionalScreen
import com.eter.salud.ui.registro.RegistroScreen
import com.eter.salud.ui.theme.SaludTheme
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.nfc_titulo

/**
 * Destinos de la app. Navegacion minima y explicita mientras no entre un grafo
 * completo; el objetivo es que cada pantalla siga siendo independiente de las
 * demas.
 *
 * Hay dos portales, cada uno con su propia sesion:
 *  - Paciente: [ACCESO], [REGISTRO], [INICIO], [ONBOARDING], [PERFIL].
 *  - Personal medico: [LOGIN_PROFESIONAL], [REGISTRO_PROFESIONAL],
 *    [INICIO_PROFESIONAL], [EMERGENCIA].
 *
 * [EMERGENCIA] (el escaner NFC y el perfil de supervivencia) es el destino mas
 * sensible de la app y SOLO se resuelve dentro de la rama con una
 * `sesionProfesional` abierta (ver [App]); ningun otro camino de navegacion lo
 * alcanza, ni siquiera con este valor puesto por error en `destino`.
 */
private enum class DestinoApp {
    ACCESO,
    REGISTRO,
    LOGIN_PROFESIONAL,
    REGISTRO_PROFESIONAL,
    INICIO,
    ONBOARDING,
    PERFIL,
    MI_MEDICO,
    INICIO_PROFESIONAL,
    EMERGENCIA,
    CHAT_MEDICO,
    AGENDA_MEDICO,
}

/**
 * Punto de entrada compartido. Monta el tema (que resuelve Modo Claro/Oscuro) y
 * encadena los dos portales de la app: el del paciente (acceso o alta de
 * cuenta, panel principal, onboarding y ajustes de perfil) y el del personal
 * medico (acceso o alta de cuenta, panel principal y escaner de emergencia).
 *
 * Correccion de seguridad: el escaner de emergencia dejo de ser alcanzable
 * desde el acceso del paciente. Ahora exige autenticarse como personal medico
 * antes de poder verlo, tal como exige el flujo de control de acceso.
 *
 * Los repositorios en memoria son provisionales: se cambiaran por los clientes
 * HTTP del backend en Go sin tocar las vistas ni los ViewModels.
 */
@Composable
@Preview
fun App() {
    SaludTheme {
        var sesion by remember { mutableStateOf<SesionPaciente?>(null) }
        var sesionProfesional by remember { mutableStateOf<SesionProfesional?>(null) }
        var pacienteEnChat by remember { mutableStateOf<PacienteVinculado?>(null) }
        var destino by remember { mutableStateOf(DestinoApp.ACCESO) }

        val repositorioCuentas = remember { AutenticacionRepositorioEnMemoria() }
        val repositorioHistorial = remember { HistorialMedicoRepositorioEnMemoria() }
        val repositorioAdherencia = remember { AdherenciaRepositorioEnMemoria() }
        val repositorioCuentasProfesionales = remember { AutenticacionProfesionalRepositorioEnMemoria() }
        val repositorioPacientesVinculados = remember { PacientesVinculadosRepositorioMock() }
        val repositorioEmergencia = remember { PerfilEmergenciaRepositorioMock() }
        val repositorioDirectorio = remember { DirectorioMedicoRepositorioMock() }
        val repositorioChat = remember { ChatRepositorioEnMemoria() }
        // Una sola instancia para los dos portales: es lo que hace que la cita
        // que el paciente confirma en el chat aparezca en el calendario del
        // medico sin recargar nada. Con un repositorio por portal, cada lado
        // veria una agenda distinta.
        val repositorioCitas = remember { CitasRepositorioEnMemoria() }

        // ------------------------------------------------- Portal de personal medico

        val profesionalActual = sesionProfesional
        if (profesionalActual != null) {
            if (destino == DestinoApp.EMERGENCIA) {
                val mensajeDeLaHoja = stringResource(Res.string.nfc_titulo)
                val lector = rememberLectorTarjetaNfc(mensajeDeLaHoja)
                val escaner = viewModel { EscanerEmergenciaViewModel(lector, repositorioEmergencia) }
                EscanerEmergenciaScreen(
                    viewModel = escaner,
                    alVolver = { destino = DestinoApp.INICIO_PROFESIONAL },
                )
                return@SaludTheme
            }

            if (destino == DestinoApp.AGENDA_MEDICO) {
                val agenda = viewModel(key = "$CLAVE_AGENDA${profesionalActual.idMedico}") {
                    AgendaMedicoViewModel(repositorioCitas, profesionalActual.idMedico)
                }
                AgendaMedicoScreen(
                    viewModel = agenda,
                    alVolver = { destino = DestinoApp.INICIO_PROFESIONAL },
                    // `alAbrirExpediente` queda sin conectar a proposito: hoy no
                    // existe una pantalla de expediente del lado del medico que
                    // se abra por `idPaciente` (la unica vista clinica que hay
                    // se alcanza leyendo la tarjeta NFC). Inventar un salto a
                    // otra pantalla haria que el boton mintiera sobre a donde
                    // lleva. "Iniciar consulta" si cambia el estado de la cita.
                )
                return@SaludTheme
            }

            val pacienteElegido = pacienteEnChat
            if (destino == DestinoApp.CHAT_MEDICO && pacienteElegido != null) {
                val chatMedico = viewModel(key = "$CLAVE_CHAT_MEDICO${pacienteElegido.idConversacion}") {
                    ChatMedicoViewModel(
                        repositorio = repositorioChat,
                        idConversacion = pacienteElegido.idConversacion,
                        nombrePaciente = pacienteElegido.nombreCompleto,
                        riesgoPaciente = pacienteElegido.riesgo,
                    )
                }
                ChatMedicoScreen(
                    viewModel = chatMedico,
                    alVolver = {
                        pacienteEnChat = null
                        destino = DestinoApp.INICIO_PROFESIONAL
                    },
                )
                return@SaludTheme
            }

            val inicioProfesional = viewModel(key = "$CLAVE_INICIO_PROFESIONAL${profesionalActual.idMedico}") {
                HomeProfesionalViewModel(
                    pacientesVinculados = repositorioPacientesVinculados,
                    idMedico = profesionalActual.idMedico,
                    tratamiento = profesionalActual.tratamiento,
                    apellidos = profesionalActual.apellidos,
                    estadoVerificacion = profesionalActual.estadoVerificacion,
                )
            }
            HomeProfesionalScreen(
                viewModel = inicioProfesional,
                // Unica forma de instanciar el escaner en toda la app: exige
                // haber llegado hasta aqui con una sesion de personal medico.
                alEscanearTarjeta = { destino = DestinoApp.EMERGENCIA },
                alAbrirAgenda = { destino = DestinoApp.AGENDA_MEDICO },
                alAbrirChat = { paciente ->
                    pacienteEnChat = paciente
                    destino = DestinoApp.CHAT_MEDICO
                },
                alCerrarSesion = {
                    sesionProfesional = null
                    pacienteEnChat = null
                    destino = DestinoApp.LOGIN_PROFESIONAL
                },
            )
            return@SaludTheme
        }

        if (destino == DestinoApp.LOGIN_PROFESIONAL || destino == DestinoApp.REGISTRO_PROFESIONAL) {
            val abrirSesionProfesional: (SesionProfesional) -> Unit = { nueva ->
                sesionProfesional = nueva
                destino = DestinoApp.INICIO_PROFESIONAL
            }

            when (destino) {
                DestinoApp.REGISTRO_PROFESIONAL -> {
                    val registro = viewModel {
                        RegistroProfesionalViewModel(repositorioCuentasProfesionales)
                    }
                    RegistroProfesionalScreen(
                        viewModel = registro,
                        alCrearCuenta = abrirSesionProfesional,
                        alVolverAlAcceso = { destino = DestinoApp.LOGIN_PROFESIONAL },
                    )
                }

                else -> {
                    val login = viewModel {
                        LoginProfesionalViewModel(repositorioCuentasProfesionales)
                    }
                    LoginProfesionalScreen(
                        viewModel = login,
                        alIniciarSesion = abrirSesionProfesional,
                        alRegistrarse = { destino = DestinoApp.REGISTRO_PROFESIONAL },
                        alVolverAPaciente = { destino = DestinoApp.ACCESO },
                    )
                }
            }
            return@SaludTheme
        }

        // ------------------------------------------------------------ Portal de paciente

        val abrirSesion: (SesionPaciente) -> Unit = { nueva ->
            sesion = nueva
            destino = DestinoApp.INICIO
        }

        val actual = sesion
        if (actual == null) {
            when (destino) {
                DestinoApp.REGISTRO -> {
                    val registro = viewModel { RegistroViewModel(repositorioCuentas) }
                    RegistroScreen(
                        viewModel = registro,
                        alCrearCuenta = abrirSesion,
                        alVolverAlAcceso = { destino = DestinoApp.ACCESO },
                    )
                }

                else -> {
                    val login = viewModel { LoginViewModel(repositorioCuentas) }
                    LoginScreen(
                        viewModel = login,
                        alIniciarSesion = abrirSesion,
                        alRegistrarse = { destino = DestinoApp.REGISTRO },
                        alAccesoProfesional = { destino = DestinoApp.LOGIN_PROFESIONAL },
                    )
                }
            }
            return@SaludTheme
        }

        // Sobrevive a las idas y venidas al onboarding: por eso se crea fuera del
        // `when` y se le avisa cuando el perfil de emergencia queda completo.
        //
        // La clave lleva prefijo de rol a proposito. `ViewModelProvider` guarda
        // por la clave EXACTA que se le pasa: si dos ViewModel distintos usan la
        // misma, cada peticion encuentra el tipo equivocado, crea uno nuevo y
        // `ViewModelStore.put` destruye (`clear()`) al anterior, cancelando sus
        // corrutinas. Con `inicio` creandose en cada recomposicion, eso vaciaba
        // el ViewModel del directorio una y otra vez.
        val inicio = viewModel(key = "$CLAVE_INICIO${actual.idPaciente}") {
            HomeViewModel(
                historial = repositorioHistorial,
                adherencia = repositorioAdherencia,
                idPaciente = actual.idPaciente,
                perfilEmergenciaPendiente = actual.requiereOnboarding,
            )
        }

        when (destino) {
            DestinoApp.ONBOARDING -> {
                val repositorio = remember { PacienteRepositorioEnMemoria() }
                val onboarding = viewModel { OnboardingPacienteViewModel(repositorio) }
                OnboardingPacienteScreen(
                    viewModel = onboarding,
                    alTerminarRegistro = {
                        inicio.marcarPerfilCompletado()
                        destino = DestinoApp.INICIO
                    },
                )
            }

            DestinoApp.PERFIL -> {
                val perfil = viewModel { PerfilMedicoViewModel(repositorioHistorial) }
                PerfilMedicoScreen(
                    viewModel = perfil,
                    idPaciente = actual.idPaciente,
                    alVolver = { destino = DestinoApp.INICIO },
                )
            }

            DestinoApp.MI_MEDICO -> {
                // El propio ViewModel decide directorio-vs-chat; aqui solo se
                // lee su estado para saber cual de las dos pantallas construir,
                // igual que arriba se decide entre Login y Registro.
                val descubrimiento = viewModel(key = "$CLAVE_DIRECTORIO${actual.idPaciente}") {
                    DescubrimientoMedicoViewModel(repositorioDirectorio, actual.idPaciente)
                }
                val estadoMedico by descubrimiento.estado.collectAsStateWithLifecycle()

                val vinculado = estadoMedico.medicoVinculado
                if (vinculado != null) {
                    val chat = viewModel(key = "$CLAVE_CHAT${vinculado.idConversacion}") {
                        ChatViewModel(
                            repositorio = repositorioChat,
                            idConversacion = vinculado.idConversacion,
                            nombreMedico = vinculado.nombreCompleto,
                        )
                    }
                    val agendaCita = viewModel(key = "$CLAVE_AGENDA_CITA${vinculado.idConversacion}") {
                        AgendaCitaViewModel(
                            citas = repositorioCitas,
                            directorio = repositorioDirectorio,
                            idPaciente = actual.idPaciente,
                            medicoVinculado = vinculado,
                        )
                    }
                    ChatScreen(
                        viewModel = chat,
                        alVolver = { destino = DestinoApp.INICIO },
                        alEnviarMensaje = agendaCita::evaluarMensaje,
                        alAgendarCita = agendaCita::iniciar,
                        panelAgenda = { PanelAgendaCita(agendaCita) },
                    )
                } else {
                    MiMedicoScreen(
                        viewModel = descubrimiento,
                        alVolver = { destino = DestinoApp.INICIO },
                    )
                }
            }

            else -> HomeScreen(
                viewModel = inicio,
                alCompletarPerfil = { destino = DestinoApp.ONBOARDING },
                alAbrirAcceso = { acceso ->
                    // Historial y Mis medicos ya tienen pantalla; los otros dos
                    // destinos entraran sin tocar esta pantalla.
                    when (acceso) {
                        AccesoRapido.HISTORIAL -> destino = DestinoApp.PERFIL
                        AccesoRapido.MIS_MEDICOS -> destino = DestinoApp.MI_MEDICO
                        else -> Unit
                    }
                },
                alCerrarSesion = {
                    sesion = null
                    destino = DestinoApp.ACCESO
                },
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
