// Hallmark - redesign 2026-09-09 - genero: modern-minimal - macroestructura: Indice plano
// critica pre-emision: P5 H5 E4 S4 R5 V5
// tema: Biotech Premium (tokens propios) - acento: Sapphire / NeonSky
// diversificacion: la corrida anterior dejo "cabecera + lista de tarjetas + flotante".
// Repetir esa huella era el tell de plantilla, asi que la fila pierde su tarjeta:
// las conversaciones se separan por aire, no por recuadros.
package com.eter.salud.ui.bandeja

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eter.salud.domain.model.AutorMensaje
import com.eter.salud.domain.model.ConversacionResumen
import com.eter.salud.domain.model.MedicoVinculado
import com.eter.salud.domain.time.RelojSalud
import com.eter.salud.domain.time.relojDelSistema
import com.eter.salud.presentation.bandeja.BandejaUiState
import com.eter.salud.presentation.bandeja.BandejaViewModel
import com.eter.salud.ui.componentes.AccionExtendidaFlotante
import com.eter.salud.ui.componentes.BloqueDeError
import com.eter.salud.ui.componentes.BotonAccionPrincipal
import com.eter.salud.ui.componentes.CabeceraGrande
import com.eter.salud.ui.componentes.GlifoSalud
import com.eter.salud.ui.componentes.IconoSalud
import com.eter.salud.ui.componentes.PuntoDeTriage
import com.eter.salud.ui.componentes.superficiePulsable
import com.eter.salud.ui.directorio.recurso
import com.eter.salud.ui.theme.AreaTactilMinima
import com.eter.salud.ui.theme.FormaSalud
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import com.eter.salud.ui.theme.MedidaSalud
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_bandeja_accion_directorio
import salud.shared.generated.resources.a11y_bandeja_cargando
import salud.shared.generated.resources.a11y_bandeja_conversacion
import salud.shared.generated.resources.a11y_bandeja_conversacion_sin_leer
import salud.shared.generated.resources.a11y_bandeja_vacia
import salud.shared.generated.resources.bandeja_accion_nuevo_especialista
import salud.shared.generated.resources.bandeja_cargando
import salud.shared.generated.resources.bandeja_error
import salud.shared.generated.resources.bandeja_titulo
import salud.shared.generated.resources.bandeja_ultimo_propio
import salud.shared.generated.resources.bandeja_vacia_accion
import salud.shared.generated.resources.bandeja_vacia_descripcion
import salud.shared.generated.resources.bandeja_vacia_titulo

/**
 * Bandeja de conversaciones del paciente.
 *
 * ## La macroestructura: indice plano
 *
 * Cada conversacion pierde su tarjeta. No es ahorro de pixeles: una lista de
 * recuadros redondeados identicos, uno debajo de otro, es la forma en la que
 * TODA app generada resuelve una bandeja, y ademas rinde peor -- el ojo tiene
 * que saltar el borde de cada caja antes de llegar al nombre que busca.
 *
 * Lo que separa una fila de la siguiente es el aire: 16dp arriba y abajo del
 * contenido, sin una sola linea divisoria. Lo que las alinea es el disco del
 * avatar, que forma una columna vertical continua por la que el ojo baja sin
 * leer un solo nombre. Eso es lo que hace recorrible una lista, no el borde.
 *
 * ## Por que la bandeja y no el directorio
 *
 * Hablar con quien ya te atiende es algo diario; buscar un especialista nuevo es
 * excepcional. Con el catalogo por delante, llegar a tu propia cardiologa exigia
 * atravesar una lista de desconocidos. El directorio no desaparece: vive en la
 * accion flotante, que es donde va una accion puntual sin robar sitio a lo que
 * se usa cada dia.
 */
@Composable
fun BandejaScreen(
    viewModel: BandejaViewModel,
    modifier: Modifier = Modifier,
    alAbrirConversacion: (MedicoVinculado) -> Unit = {},
    alBuscarMedico: () -> Unit = {},
    reloj: RelojSalud = relojDelSistema(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()

    // Al volver a la pestana: un medico escogido en el directorio o un mensaje
    // nuevo tienen que estar ya en la lista.
    LaunchedEffect(Unit) { viewModel.refrescar() }
    val espaciado = LocalEspaciadoSalud.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = espaciado.medio,
                // Hueco para que la ultima conversacion no quede bajo la accion
                // flotante: taparla obligaria a desplazar para poder tocarla.
                bottom = espaciado.respiro + espaciado.generoso,
            ),
        ) {
            item(key = "cabecera") {
                CabeceraGrande(
                    titulo = stringResource(Res.string.bandeja_titulo),
                    modifier = Modifier.padding(horizontal = espaciado.amplio),
                )
                Spacer(Modifier.height(espaciado.amplio))
            }

            when (val actual = estado) {
                BandejaUiState.Cargando -> item(key = "cargando") { IndicadorCargando() }

                BandejaUiState.Error -> item(key = "error") {
                    BloqueDeError(
                        mensaje = stringResource(Res.string.bandeja_error),
                        alReintentar = viewModel::cargar,
                        modifier = Modifier.padding(horizontal = espaciado.amplio),
                    )
                }

                BandejaUiState.SinConversaciones -> item(key = "vacio") {
                    BandejaVacia(
                        alBuscarMedico = alBuscarMedico,
                        modifier = Modifier.padding(horizontal = espaciado.amplio),
                    )
                }

                is BandejaUiState.ConConversaciones -> items(
                    actual.conversaciones,
                    key = { it.idConversacion },
                ) { conversacion ->
                    FilaConversacion(
                        conversacion = conversacion,
                        horaLocal = reloj.horaLocal(conversacion.instanteUltimoMensaje),
                        alAbrir = { alAbrirConversacion(viewModel.medicoDe(conversacion)) },
                    )
                }
            }
        }

        AccionExtendidaFlotante(
            etiqueta = stringResource(Res.string.bandeja_accion_nuevo_especialista),
            glifo = GlifoSalud.ANADIR_MEDICO,
            descripcionAccesible = stringResource(Res.string.a11y_bandeja_accion_directorio),
            alPulsar = alBuscarMedico,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(espaciado.amplio),
        )
    }
}

/**
 * Una conversacion del indice.
 *
 * ## Sin tarjeta, sin divisoria
 *
 * La fila se pinta sobre el fondo de la pantalla. Su area pulsable ocupa el
 * ancho completo -- de margen a margen -- y el relleno de 16dp verticales le da
 * el aire que antes daba el hueco entre tarjetas. Ganar ese ancho importa: el
 * area tactil de una fila de bandeja deberia ser toda la fila, y con tarjetas
 * se perdian 24dp a cada lado.
 *
 * ## La senal de no leido
 *
 * Un punto de acento con halo sobre el avatar, mas el nombre en peso fuerte.
 * Son dos senales para el mismo hecho a proposito: el punto se ve al recorrer
 * la lista sin leer, y el peso se ve al leerla. La cuenta exacta sigue en la
 * pastilla de la derecha, porque "3 sin leer" y "1 sin leer" no son lo mismo y
 * un punto no sabe decir la diferencia.
 */
@Composable
private fun FilaConversacion(
    conversacion: ConversacionResumen,
    horaLocal: String,
    alAbrir: () -> Unit,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val especialidad = stringResource(conversacion.especialidad.recurso())
    val sinLeer = conversacion.mensajesSinLeer > 0

    // Un mensaje propio se prefija con "Tu:", como en cualquier mensajeria: sin
    // eso, el ultimo texto parece siempre dicho por el medico y confunde sobre
    // quien tiene la pelota.
    val vistaPrevia = if (conversacion.autorUltimoMensaje == AutorMensaje.PACIENTE) {
        stringResource(Res.string.bandeja_ultimo_propio, conversacion.ultimoMensaje)
    } else {
        conversacion.ultimoMensaje
    }

    val descripcion = if (sinLeer) {
        stringResource(
            Res.string.a11y_bandeja_conversacion_sin_leer,
            conversacion.nombreMedico,
            especialidad,
            horaLocal,
            conversacion.ultimoMensaje,
            conversacion.mensajesSinLeer,
        )
    } else {
        stringResource(
            Res.string.a11y_bandeja_conversacion,
            conversacion.nombreMedico,
            especialidad,
            horaLocal,
            conversacion.ultimoMensaje,
        )
    }

    val fuenteDeInteraccion = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AreaTactilMinima)
            .clickable(
                interactionSource = fuenteDeInteraccion,
                indication = null,
                onClick = alAbrir,
            )
            .superficiePulsable(fuenteDeInteraccion, FormaSalud.grande)
            .padding(horizontal = espaciado.amplio, vertical = espaciado.medio)
            .semantics(mergeDescendants = true) { contentDescription = descripcion },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            inicial = inicialDe(conversacion.nombreMedico),
            resaltado = sinLeer,
        )
        Spacer(Modifier.width(espaciado.medio))

        Column(Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = conversacion.nombreMedico,
                    style = MaterialTheme.typography.bodyLarge,
                    // El peso, y no el color, marca lo no leido: teñir el nombre
                    // de azul lo confundiria con un enlace, y en una lista donde
                    // TODAS las filas son pulsables eso no distingue nada.
                    fontWeight = if (sinLeer) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(espaciado.compacto))
                Text(
                    text = horaLocal,
                    style = MaterialTheme.typography.labelSmall,
                    color = colores.textoSecundario,
                )
            }
            Text(
                text = especialidad,
                style = MaterialTheme.typography.labelMedium,
                color = colores.textoSecundario,
            )
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

        if (sinLeer) {
            Spacer(Modifier.width(espaciado.compacto))
            Surface(color = colores.acentoAccion, shape = FormaSalud.pastilla) {
                Text(
                    text = conversacion.mensajesSinLeer.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colores.sobreAcentoAccion,
                    modifier = Modifier.padding(
                        horizontal = espaciado.compacto,
                        vertical = espaciado.minimo / 2,
                    ),
                )
            }
        }
    }
}

/**
 * Disco con la inicial del medico, con el punto de acento cuando hay mensajes
 * sin leer.
 *
 * El punto se posa fuera del borde del disco, no dentro: encima de la inicial
 * taparia la unica letra que hace la lista recorrible.
 */
@Composable
private fun Avatar(inicial: String, resaltado: Boolean) {
    val colores = LocalColoresSalud.current

    Box(contentAlignment = Alignment.TopEnd) {
        Box(
            modifier = Modifier
                .padding(DESPLAZAMIENTO_PUNTO)
                .size(MedidaSalud.disco)
                .background(colores.veloAcento, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = inicial,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (resaltado) {
            PuntoDeTriage(color = colores.acentoAccion)
        }
    }
}

// ---------------------------------------------------------------- Auxiliares

/**
 * Bandeja sin conversaciones.
 *
 * ## Por que no es una ilustracion centrada
 *
 * El estado vacio por defecto de toda app generada es el mismo: un dibujo
 * grande, un titulo centrado, un parrafo gris y un boton debajo, todo sobre el
 * mismo eje vertical. Se reconoce al instante y no ayuda a nadie.
 *
 * Aqui el vacio se compone con la geometria que tendran las conversaciones
 * cuando existan: el mismo disco en el mismo sitio, el mismo margen, la misma
 * alineacion a la izquierda. El usuario ve la FORMA de lo que va a llegar, no
 * un cartel que interrumpe. El glifo va a tamano de contenido -- no de
 * ilustracion -- y el texto describe QUE HACER, no lamenta que no haya nada.
 */
@Composable
private fun BandejaVacia(alBuscarMedico: () -> Unit, modifier: Modifier = Modifier) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val descripcion = stringResource(Res.string.a11y_bandeja_vacia)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = descripcion },
        verticalArrangement = Arrangement.spacedBy(espaciado.medio),
    ) {
        // El glifo ocupa el lugar del disco de una conversacion real: el hueco
        // vacio esta donde estara la primera cara.
        Box(
            modifier = Modifier
                .size(MedidaSalud.disco)
                .background(colores.fondoCampo, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            IconoSalud(
                glifo = GlifoSalud.MEDICOS,
                lado = LADO_GLIFO_VACIO,
                color = colores.textoSecundario,
            )
        }
        Text(
            text = stringResource(Res.string.bandeja_vacia_titulo),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(Res.string.bandeja_vacia_descripcion),
            style = MaterialTheme.typography.bodyMedium,
            color = colores.textoSecundario,
        )
        Spacer(Modifier.height(espaciado.minimo))
        BotonAccionPrincipal(
            etiqueta = stringResource(Res.string.bandeja_vacia_accion),
            alPulsar = alBuscarMedico,
            descripcionAccesible = stringResource(Res.string.a11y_bandeja_accion_directorio),
        )
    }
}

@Composable
private fun IndicadorCargando() {
    val descripcion = stringResource(Res.string.a11y_bandeja_cargando)
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(espaciado.respiro)
            // La region viva sin descripcion no anuncia nada: TalkBack necesita
            // QUE leer, no solo saber que algo cambio.
            .semantics {
                contentDescription = descripcion
                liveRegion = LiveRegionMode.Polite
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = colores.acentoAccion)
        Spacer(Modifier.height(espaciado.medio))
        Text(
            text = stringResource(Res.string.bandeja_cargando),
            style = MaterialTheme.typography.bodyMedium,
            color = colores.textoSecundario,
        )
    }
}

/**
 * Inicial del nombre, saltando el tratamiento.
 *
 * "Dra. Elena Ruiz" da "E" y no "D": con media plantilla siendo "Dr." o "Dra.",
 * usar la primera letra dejaria toda la bandeja llena de discos identicos.
 */
private fun inicialDe(nombre: String): String {
    val partes = nombre.split(" ").filter { it.isNotBlank() }
    val significativa = partes.firstOrNull { !it.endsWith(".") } ?: partes.firstOrNull()
    return significativa?.take(1)?.uppercase().orEmpty()
}

/**
 * Cuanto se aparta el disco de la esquina para dejar sitio al halo del punto.
 *
 * Es la mitad del halo (12dp x 2.6 / 2 redondeado): sin este margen, el
 * degradado se recortaria contra el borde del `Box` y el punto se veria como un
 * cuarto de circulo pegado a la esquina.
 */
private val DESPLAZAMIENTO_PUNTO = 8.dp
private val LADO_GLIFO_VACIO = 22.dp
