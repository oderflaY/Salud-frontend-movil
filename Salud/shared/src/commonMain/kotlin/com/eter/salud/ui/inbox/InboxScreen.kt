// Hallmark - audit + redesign 2026-09-10 - genero: modern-minimal - macroestructura: Index-First (indice clinico)
// tema: custom "Clinica Serena" (ver SaludTheme.kt) - acento: azul clinico - una tarjeta por conversacion
// critica pre-emision: P5 H5 E4 S5 R5 V4
// triage sin texto visible: tono + intensidad del halo + latido (solo critico) + descripcion accesible
// cero divisorias, cero elevaciones - FAB: ExtendedFloatingActionButton de M3, plano
package com.eter.salud.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eter.salud.domain.model.AutorMensaje
import com.eter.salud.domain.model.PacienteVinculado
import com.eter.salud.domain.time.RelojSalud
import com.eter.salud.domain.time.relojDelSistema
import com.eter.salud.presentation.inbox.ConversacionClinica
import com.eter.salud.presentation.inbox.InboxUiState
import com.eter.salud.presentation.inbox.InboxViewModel
import com.eter.salud.presentation.inbox.NivelTriage
import com.eter.salud.ui.componentes.AccionExtendidaFlotante
import com.eter.salud.ui.componentes.BloqueDeError
import com.eter.salud.ui.componentes.GlifoSalud
import com.eter.salud.ui.componentes.IconoSalud
import com.eter.salud.ui.componentes.PuntoLuminoso
import com.eter.salud.ui.componentes.superficiePulsable
import com.eter.salud.ui.theme.AreaTactilMinima
import com.eter.salud.ui.theme.CifraSalud
import com.eter.salud.ui.theme.FormaSalud
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import com.eter.salud.ui.theme.MedidaSalud
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_bandeja_cargando
import salud.shared.generated.resources.a11y_inbox_accion_nuevo_especialista
import salud.shared.generated.resources.a11y_inbox_censo
import salud.shared.generated.resources.a11y_inbox_fila
import salud.shared.generated.resources.a11y_inbox_fila_sin_mensajes
import salud.shared.generated.resources.a11y_triage_critico
import salud.shared.generated.resources.a11y_triage_estable
import salud.shared.generated.resources.a11y_triage_vigilancia
import salud.shared.generated.resources.bandeja_accion_nuevo_especialista
import salud.shared.generated.resources.bandeja_error
import salud.shared.generated.resources.bandeja_ultimo_propio
import salud.shared.generated.resources.inbox_orden
import salud.shared.generated.resources.inbox_sin_mensajes
import salud.shared.generated.resources.inbox_titulo
import salud.shared.generated.resources.inbox_vacia_descripcion
import salud.shared.generated.resources.inbox_vacia_titulo

/**
 * Bandeja de conversaciones del medico.
 *
 * ## La macroestructura: indice clinico
 *
 * La pantalla ES la lista. No hay una sola linea divisoria ni barra superior:
 * un titulo a escala de portada, un censo de tres cifras y, debajo, una
 * tarjeta blanca por conversacion, separadas por aire. Lo que las alinea es la
 * columna de avatares, por la que el ojo baja sin leer un nombre.
 *
 * ## El orden es la mitad del triage
 *
 * Las conversaciones llegan ordenadas por gravedad (ver [InboxViewModel]): el
 * paciente critico esta ARRIBA, siempre, aunque haya escrito hace horas. El
 * punto de color confirma lo que la posicion ya dijo.
 *
 * ## Por que ningun texto dice el riesgo
 *
 * La fila tiene tres lineas de tinta -- nombre, hora, ultima frase -- y una
 * cuarta palabra ("Critico", "Estable") competiria con el nombre por la primera
 * lectura. El nivel se codifica sin palabras en tres canales que no dependen de
 * distinguir colores: la posicion en la lista, la intensidad del halo y el
 * latido del nivel mas grave. Para quien no ve la pantalla, la fila anuncia su
 * prioridad en la descripcion accesible: el texto que no se pinta, se lee.
 */
@Composable
fun InboxScreen(
    viewModel: InboxViewModel,
    modifier: Modifier = Modifier,
    alAbrirConversacion: (PacienteVinculado) -> Unit = {},
    alNuevoEspecialista: () -> Unit = {},
    reloj: RelojSalud = relojDelSistema(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val espaciado = LocalEspaciadoSalud.current

    // Al volver de un chat la bandeja se reconstruye: la fila tiene que decir
    // lo que el medico acaba de contestar. En la primera entrada la llamada
    // choca con la carga del `init` y se ignora.
    LaunchedEffect(Unit) { viewModel.cargar() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = espaciado.amplio,
                // La ultima fila nunca queda bajo la accion flotante: taparla
                // obligaria a desplazar para poder tocarla.
                bottom = espaciado.respiro + espaciado.generoso + espaciado.medio,
            ),
        ) {
            item(key = "cabecera") {
                Cabecera(
                    estado = estado,
                    modifier = Modifier.padding(horizontal = espaciado.amplio),
                )
                Spacer(Modifier.height(espaciado.respiro))
            }

            when (val actual = estado) {
                InboxUiState.Cargando -> item(key = "cargando") { Esqueleto() }

                InboxUiState.Error -> item(key = "error") {
                    BloqueDeError(
                        mensaje = stringResource(Res.string.bandeja_error),
                        alReintentar = viewModel::cargar,
                        modifier = Modifier.padding(horizontal = espaciado.amplio),
                    )
                }

                InboxUiState.SinConversaciones -> item(key = "vacio") {
                    BandejaVacia(Modifier.padding(horizontal = espaciado.amplio))
                }

                is InboxUiState.ConConversaciones -> items(
                    actual.conversaciones,
                    key = { it.paciente.idPaciente },
                ) { conversacion ->
                    FilaConversacion(
                        conversacion = conversacion,
                        horaLocal = conversacion.instanteUltimoMensaje
                            ?.let(reloj::horaLocal)
                            .orEmpty(),
                        alAbrir = { alAbrirConversacion(conversacion.paciente) },
                    )
                }
            }
        }

        AccionExtendidaFlotante(
            etiqueta = stringResource(Res.string.bandeja_accion_nuevo_especialista),
            glifo = GlifoSalud.ANADIR_MEDICO,
            descripcionAccesible = stringResource(Res.string.a11y_inbox_accion_nuevo_especialista),
            alPulsar = alNuevoEspecialista,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(espaciado.amplio),
        )
    }
}

// ------------------------------------------------------------------ Cabecera

/**
 * Titulo a escala de portada y, cuando hay conversaciones, el censo.
 *
 * ## El censo
 *
 * Tres cifras pesadas, una por nivel, cada una precedida de su punto. Es el dato
 * vital de la pantalla: antes de leer un nombre, el medico sabe si hoy tiene dos
 * urgencias o ninguna. Son CONTEOS, no etiquetas de riesgo de un paciente, y por
 * eso llevan cifra.
 *
 * Va alineado a la izquierda bajo el titulo, en el mismo eje que la columna de
 * avatares: la cabecera y la lista se leen sobre una sola vertical.
 */
@Composable
private fun Cabecera(estado: InboxUiState, modifier: Modifier = Modifier) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current

    Column(modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.inbox_titulo),
            style = CifraSalud.portada,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )

        if (estado is InboxUiState.ConConversaciones) {
            Spacer(Modifier.height(espaciado.compacto))
            Text(
                text = stringResource(Res.string.inbox_orden),
                style = MaterialTheme.typography.bodyMedium,
                color = colores.textoSecundario,
            )
            Spacer(Modifier.height(espaciado.amplio))
            Censo(estado.conversaciones)
        }
    }
}

@Composable
private fun Censo(conversaciones: List<ConversacionClinica>) {
    val espaciado = LocalEspaciadoSalud.current
    val conteo = conversaciones.groupingBy { it.nivel }.eachCount()
    val criticos = conteo[NivelTriage.CRITICO] ?: 0
    val vigilancia = conteo[NivelTriage.VIGILANCIA] ?: 0
    val estables = conteo[NivelTriage.ESTABLE] ?: 0
    val descripcion = stringResource(Res.string.a11y_inbox_censo, criticos, vigilancia, estables)

    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = descripcion },
        horizontalArrangement = Arrangement.spacedBy(espaciado.generoso),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CifraDeCenso(NivelTriage.CRITICO, criticos)
        CifraDeCenso(NivelTriage.VIGILANCIA, vigilancia)
        CifraDeCenso(NivelTriage.ESTABLE, estables)
    }
}

@Composable
private fun CifraDeCenso(nivel: NivelTriage, cantidad: Int) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current

    Row(verticalAlignment = Alignment.CenterVertically) {
        // En el censo el punto va sin halo ni latido: aqui acompana a una cifra
        // que ya dice todo, y tres halos en fila competirian con los de la lista.
        Box(
            Modifier
                .size(MedidaSalud.punto)
                .background(nivel.senal(), CircleShape),
        )
        Spacer(Modifier.width(espaciado.compacto))
        Text(
            text = cantidad.toString(),
            style = CifraSalud.hora,
            // Un nivel sin pacientes se apaga: un "0" en tinta plena pesaria lo
            // mismo que un "3" y el censo dejaria de leerse de un vistazo.
            color = if (cantidad > 0) MaterialTheme.colorScheme.onBackground else colores.textoSecundario,
        )
    }
}

// --------------------------------------------------------------------- Filas

/**
 * Una conversacion del indice.
 *
 * El area pulsable es la fila entera, de margen a margen, con 20dp de aire
 * arriba y abajo: el hueco que antes daba el espacio entre tarjetas lo da ahora
 * el propio relleno de la fila, y el toque cae en cualquier punto de ella.
 */
@Composable
private fun FilaConversacion(
    conversacion: ConversacionClinica,
    horaLocal: String,
    alAbrir: () -> Unit,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val nombre = conversacion.paciente.nombreCompleto
    val prioridad = stringResource(conversacion.nivel.recursoAccesible())

    val vistaPrevia = when {
        conversacion.ultimoMensaje == null -> stringResource(Res.string.inbox_sin_mensajes)
        // En la bandeja del medico, "tu" es el medico: sin el prefijo, su propia
        // respuesta parece dicha por el paciente y confunde sobre quien tiene
        // la pelota.
        conversacion.autorUltimoMensaje == AutorMensaje.MEDICO ->
            stringResource(Res.string.bandeja_ultimo_propio, conversacion.ultimoMensaje)

        else -> conversacion.ultimoMensaje
    }

    val descripcion = if (conversacion.ultimoMensaje == null) {
        stringResource(Res.string.a11y_inbox_fila_sin_mensajes, nombre, prioridad)
    } else {
        stringResource(Res.string.a11y_inbox_fila, nombre, prioridad, horaLocal, vistaPrevia)
    }

    val fuenteDeInteraccion = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .padding(horizontal = MARGEN_TARJETA, vertical = espaciado.minimo + 2.dp)
            .fillMaxWidth()
            .heightIn(min = AreaTactilMinima)
            // Cada conversacion en su tarjeta: con el sistema "Clinica Serena"
            // la tarjeta blanca con borde suave es la unidad que se toca, y
            // separa una persona de la siguiente sin una sola linea divisoria.
            .clip(FormaSalud.destacada)
            .background(colores.fondoTarjeta)
            .border(1.dp, colores.separador, FormaSalud.destacada)
            .clickable(
                interactionSource = fuenteDeInteraccion,
                indication = null,
                onClick = alAbrir,
            )
            .superficiePulsable(fuenteDeInteraccion, FormaSalud.destacada)
            .padding(horizontal = espaciado.medio + espaciado.minimo, vertical = RELLENO_VERTICAL_FILA)
            .semantics(mergeDescendants = true) { contentDescription = descripcion },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarConDestello(inicial = inicialDe(nombre), nivel = conversacion.nivel)
        Spacer(Modifier.width(espaciado.medio + espaciado.minimo))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = nombre,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (horaLocal.isNotEmpty()) {
                    Spacer(Modifier.width(espaciado.compacto))
                    Text(
                        text = horaLocal,
                        style = MaterialTheme.typography.labelMedium,
                        color = colores.textoSecundario,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(espaciado.minimo))
            Text(
                text = vistaPrevia,
                style = MaterialTheme.typography.bodyMedium,
                color = colores.textoSecundario,
                // La bandeja sirve para decidir a cual entrar, no para leer la
                // consulta desde aqui.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Disco con la inicial del paciente y, flotando sobre su borde, el Glow Dot.
 *
 * El punto se centra EN el borde del disco, a la altura de la una y media de un
 * reloj: dentro del disco taparia la inicial, y fuera de el se leeria como un
 * adorno de la fila en vez de como una senal sobre esta persona.
 */
@Composable
private fun AvatarConDestello(inicial: String, nivel: NivelTriage) {
    val colores = LocalColoresSalud.current
    val disco = MedidaSalud.discoBandeja
    val destello = nivel.destello()
    val ladoCanvas = destello.diametro * destello.alcance
    // Centro del punto sobre la circunferencia del disco, a 45 grados: el
    // desplazamiento es radio * (1 +- cos 45) menos medio lienzo del halo.
    val radio = disco / 2
    val desvio = radio * COSENO_45

    Box(Modifier.size(disco)) {
        Box(
            modifier = Modifier
                .size(disco)
                .background(colores.veloAcento, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = inicial,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        PuntoLuminoso(
            color = nivel.senal(),
            // El aro es del color del FONDO, no del disco: separa el punto de
            // la superficie sobre la que se posa en cualquier modo.
            colorAro = MaterialTheme.colorScheme.background,
            diametro = destello.diametro,
            alcance = destello.alcance,
            intensidad = destello.intensidad,
            late = destello.late,
            modifier = Modifier.offset(
                x = radio + desvio - ladoCanvas / 2,
                y = radio - desvio - ladoCanvas / 2,
            ),
        )
    }
}

// ------------------------------------------------------------------- Estados

/**
 * Esqueleto de carga: la forma de la lista antes que la lista.
 *
 * Un indicador circular centrado dice "espera"; un esqueleto con la geometria de
 * las filas dice "esto es lo que viene", y cuando llegan los datos nada salta de
 * sitio. Es estatico a proposito: un brillo recorriendo las filas seria el unico
 * movimiento de la pantalla, y no comunica nada que la forma no diga ya.
 */
@Composable
private fun Esqueleto() {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val descripcion = stringResource(Res.string.a11y_bandeja_cargando)

    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = descripcion
            liveRegion = LiveRegionMode.Polite
        },
    ) {
        FRACCIONES_ESQUELETO.forEach { fraccion ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = espaciado.amplio, vertical = RELLENO_VERTICAL_FILA),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(MedidaSalud.discoBandeja)
                        .background(colores.fondoCampo, CircleShape),
                )
                Spacer(Modifier.width(espaciado.medio + espaciado.minimo))
                Column(Modifier.weight(1f)) {
                    BarraEsqueleto(fraccion = fraccion, alto = ALTO_BARRA_NOMBRE, color = colores.fondoCampo)
                    Spacer(Modifier.height(espaciado.compacto))
                    BarraEsqueleto(fraccion = 0.9f, alto = ALTO_BARRA_TEXTO, color = colores.fondoCampo)
                }
            }
        }
    }
}

@Composable
private fun BarraEsqueleto(fraccion: Float, alto: Dp, color: Color) {
    Box(
        Modifier
            .fillMaxWidth(fraccion)
            .height(alto)
            .background(color, FormaSalud.pastilla),
    )
}

/**
 * Bandeja vacia, compuesta con la geometria de una fila real: el mismo disco en
 * el mismo sitio. No hay boton: la unica accion de la pantalla ya flota abajo,
 * y un segundo "Nuevo especialista" aqui seria el mismo control dos veces.
 */
@Composable
private fun BandejaVacia(modifier: Modifier = Modifier) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { },
        verticalArrangement = Arrangement.spacedBy(espaciado.medio),
    ) {
        Box(
            modifier = Modifier
                .size(MedidaSalud.discoBandeja)
                .background(colores.fondoCampo, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            IconoSalud(
                glifo = GlifoSalud.CONVERSACIONES,
                lado = LADO_GLIFO_VACIO,
                color = colores.textoSecundario,
            )
        }
        Text(
            text = stringResource(Res.string.inbox_vacia_titulo),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(Res.string.inbox_vacia_descripcion),
            style = MaterialTheme.typography.bodyMedium,
            color = colores.textoSecundario,
        )
    }
}

// ---------------------------------------------------------------- Auxiliares

/**
 * Como se dibuja cada nivel: la gravedad sube el tamano, el alcance y la
 * intensidad del halo, y solo el critico late.
 *
 * Las tres escalas crecen JUNTAS a proposito. Si solo cambiara el tono, el
 * nivel dependeria de distinguir rojo de verde; con el halo creciendo, un
 * critico se ve mas grande y mas encendido que un estable aunque la pantalla
 * estuviera en escala de grises.
 */
private data class Destello(
    val diametro: Dp,
    val alcance: Float,
    val intensidad: Float,
    val late: Boolean,
)

private fun NivelTriage.destello(): Destello = when (this) {
    NivelTriage.ESTABLE -> Destello(diametro = 10.dp, alcance = 2.2f, intensidad = 0.28f, late = false)
    NivelTriage.VIGILANCIA -> Destello(diametro = 12.dp, alcance = 2.8f, intensidad = 0.42f, late = false)
    NivelTriage.CRITICO -> Destello(diametro = 14.dp, alcance = 3.4f, intensidad = 0.55f, late = true)
}

@Composable
private fun NivelTriage.senal(): Color {
    val colores = LocalColoresSalud.current
    return when (this) {
        NivelTriage.ESTABLE -> colores.senalEstable
        NivelTriage.VIGILANCIA -> colores.senalVigilancia
        NivelTriage.CRITICO -> colores.senalCritico
    }
}

private fun NivelTriage.recursoAccesible(): StringResource = when (this) {
    NivelTriage.ESTABLE -> Res.string.a11y_triage_estable
    NivelTriage.VIGILANCIA -> Res.string.a11y_triage_vigilancia
    NivelTriage.CRITICO -> Res.string.a11y_triage_critico
}

/**
 * Inicial del nombre, saltando el tratamiento ("Sr.", "Dra."): con varios
 * pacientes tratados de la misma forma, la primera letra dejaria la columna de
 * avatares llena de discos identicos.
 */
private fun inicialDe(nombre: String): String {
    val partes = nombre.split(" ").filter { it.isNotBlank() }
    val significativa = partes.firstOrNull { !it.endsWith(".") } ?: partes.firstOrNull()
    return significativa?.take(1)?.uppercase().orEmpty()
}

/** 22dp arriba y abajo dentro de la tarjeta: deja sitio al halo del punto critico. */
private val RELLENO_VERTICAL_FILA = 22.dp
private val MARGEN_TARJETA = 16.dp
private val LADO_GLIFO_VACIO = 24.dp
private val ALTO_BARRA_NOMBRE = 14.dp
private val ALTO_BARRA_TEXTO = 12.dp

/** Anchos distintos por fila: un esqueleto de barras identicas se lee como patron, no como lista. */
private val FRACCIONES_ESQUELETO = listOf(0.55f, 0.42f, 0.62f, 0.48f)
private const val COSENO_45 = 0.7071f
