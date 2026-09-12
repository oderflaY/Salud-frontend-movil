package com.eter.salud.presentation.navegacion

import com.eter.salud.domain.model.MedicoVinculado
import com.eter.salud.domain.model.PacienteVinculado

/**
 * Destinos de la app, como jerarquia sellada.
 *
 * Sustituye al `enum` plano anterior por dos razones que un enum no podia dar:
 *
 *  - **Los argumentos viajan con el destino.** Antes, abrir un chat exigia dejar
 *    el paciente elegido en una variable suelta al lado del enum y confiar en
 *    que ambos estuvieran sincronizados; si alguien ponia el destino y olvidaba
 *    el dato, la pantalla se quedaba en blanco. Aqui un [ChatConPaciente] sin
 *    paciente sencillamente no se puede construir.
 *  - **El `when` se vuelve exhaustivo.** Anadir una pantalla y olvidar
 *    dibujarla deja de compilar, en vez de caer en un `else` silencioso.
 *
 * [Seccion] marca los destinos que son raiz de pestana: los unicos que la barra
 * inferior ofrece y a los que se llega sin apilar.
 */
sealed interface Destino {

    /**
     * Pantalla que ocupa el alto entero sin cromo de navegacion.
     *
     * No es lo mismo que "apilada": una seccion tambien puede serlo. El escaner
     * de emergencia sigue siendo pestana -- se alcanza de un toque, que es su
     * razon de ser -- pero mientras se lee una tarjeta NFC junto a un paciente
     * inconsciente, una barra con tres pestanas al lado del pulgar solo puede
     * provocar un toque equivocado.
     */
    val esInmersivo: Boolean get() = false

    /** Destino que ademas es pestana de la barra inferior. */
    sealed interface Seccion : Destino

    // ------------------------------------------------------ Acceso, sin sesion
    data object Acceso : Destino
    data object Registro : Destino
    data object AccesoProfesional : Destino
    data object RegistroProfesional : Destino

    // ---------------------------------------------------------- Portal paciente
    data object Inicio : Seccion

    /**
     * Agenda del paciente: sus medicinas y sus citas, dia por dia.
     *
     * Seccion y no destino apilado: para una persona mayor "que me toca hoy y
     * manana" es una pregunta diaria, y tiene que estar a un toque, con su
     * nombre escrito en la barra.
     */
    data object AgendaPaciente : Seccion

    /** Bandeja de conversaciones. El directorio ya NO vive aqui. */
    data object MiMedico : Seccion
    data object Historial : Seccion

    /**
     * Directorio global de especialistas.
     *
     * Apilado y no seccion: buscar un medico nuevo es excepcional, y darle una
     * pestana fija lo pondria al mismo nivel que hablar con quien ya te atiende,
     * que es lo que se hace a diario.
     */
    data object Directorio : Destino

    /** Ajustes locales del dispositivo. Apilada: se abre y se cierra. */
    data object Configuracion : Destino

    /** Bitacora de sintomas del paciente. */
    data object Diario : Destino

    /**
     * El cuestionario de perfil de emergencia. NO es seccion: es un flujo con
     * principio y fin del que se sale terminandolo, y ofrecerlo como pestana
     * invitaria a abandonarlo a la mitad.
     */
    data object Onboarding : Destino

    data class ChatConMedico(val medico: MedicoVinculado) : Destino

    // ------------------------------------------------------ Portal profesional
    data object PanelProfesional : Seccion

    /**
     * Bandeja de conversaciones del medico, ordenada por triage.
     *
     * Seccion y no destino apilado: contestar a los pacientes es trabajo
     * diario, y la bandeja es donde el medico ve de un vistazo quien necesita
     * respuesta antes que nadie.
     */
    data object BandejaClinica : Seccion
    data object Agenda : Seccion
    data object Escaner : Seccion {
        override val esInmersivo: Boolean get() = true
    }

    data class ChatConPaciente(val paciente: PacienteVinculado) : Destino

    /**
     * Expediente clinico de un paciente, en solo lectura, desde el portal del
     * medico. Se llega desde el chat clinico ("Expediente") y desde la agenda
     * ("Iniciar consulta").
     *
     * [nombreReferencia] es lo unico que el origen ya conoce con certeza (el
     * chat trae el nombre del paciente vinculado; la agenda, ninguno todavia) y
     * se usa como titulo provisional mientras el expediente termina de cargar,
     * para que la cabecera no aparezca en blanco un instante.
     */
    data class ExpedientePaciente(val idPaciente: String, val nombreReferencia: String = "") : Destino
}

/**
 * Pestanas de cada portal, en el orden en que se pintan.
 *
 * Son listas y no un campo de [Destino] porque el orden es una decision de
 * presentacion, no del destino: la misma pantalla podria ocupar otra posicion en
 * otro portal sin cambiar lo que es.
 */
val SECCIONES_PACIENTE: List<Destino.Seccion> =
    listOf(Destino.Inicio, Destino.AgendaPaciente, Destino.MiMedico, Destino.Historial)

val SECCIONES_PROFESIONAL: List<Destino.Seccion> =
    listOf(Destino.PanelProfesional, Destino.BandejaClinica, Destino.Agenda, Destino.Escaner)
