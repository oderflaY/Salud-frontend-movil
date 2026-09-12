package com.eter.salud.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.eter.salud.domain.time.CalendarioSalud
import com.eter.salud.ui.theme.FormaSalud
import com.eter.salud.ui.theme.AreaTactilMinima
import com.eter.salud.ui.theme.LocalColoresSalud
import com.eter.salud.ui.theme.LocalEspaciadoSalud
import org.jetbrains.compose.resources.stringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.a11y_fecha_campo_anio
import salud.shared.generated.resources.a11y_fecha_campo_dia
import salud.shared.generated.resources.a11y_fecha_campo_mes
import salud.shared.generated.resources.a11y_fecha_opcion
import salud.shared.generated.resources.a11y_fecha_opcion_elegida
import salud.shared.generated.resources.a11y_fecha_opciones_rapidas
import salud.shared.generated.resources.a11y_fecha_seleccionada
import salud.shared.generated.resources.fecha_campo_anio
import salud.shared.generated.resources.fecha_campo_dia
import salud.shared.generated.resources.fecha_campo_mes
import salud.shared.generated.resources.fecha_opcion_ayer
import salud.shared.generated.resources.fecha_opcion_hace_un_mes
import salud.shared.generated.resources.fecha_opcion_hace_una_semana
import salud.shared.generated.resources.fecha_opcion_hoy
import salud.shared.generated.resources.fecha_opcion_personalizada
import salud.shared.generated.resources.fecha_placeholder_anio
import salud.shared.generated.resources.fecha_placeholder_dia
import salud.shared.generated.resources.fecha_placeholder_mes
import salud.shared.generated.resources.fecha_seleccionada

/**
 * Atajos de fecha ofrecidos como chips. [PERSONALIZADA] siempre esta presente:
 * es la que despliega los campos de dia, mes y ano.
 */
enum class OpcionFechaRapida {
    HOY,
    AYER,
    HACE_UNA_SEMANA,
    HACE_UN_MES,
    PERSONALIZADA,
}

/**
 * Selector de fecha simplificado.
 *
 * Sustituye al calendario nativo con desplazamiento infinito, que en un telefono
 * obliga a decenas de gestos para llegar a un ano de nacimiento. En su lugar:
 *
 *  - Chips para las fechas frecuentes, resueltas con [CalendarioSalud] sobre la
 *    fecha de hoy que inyecta quien usa el componente.
 *  - Tres campos numericos cortos (dia, mes, ano) para cualquier otra fecha. Al
 *    completarse los tres se compone `YYYY-MM-DD`; si la combinacion no existe
 *    en el calendario, el valor se limpia y la Vista muestra el error.
 *
 * El valor siempre sale en el formato del DM (`YYYY-MM-DD`), nunca localizado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectorFechaSalud(
    valor: String,
    alCambiar: (String) -> Unit,
    fechaDeHoy: String,
    modifier: Modifier = Modifier,
    opcionesRapidas: List<OpcionFechaRapida> = emptyList(),
    error: String? = null,
) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current

    val partesIniciales = remember(valor) { CalendarioSalud.descomponer(valor) }
    var dia by rememberSaveable { mutableStateOf(partesIniciales?.dia.orEmpty()) }
    var mes by rememberSaveable { mutableStateOf(partesIniciales?.mes.orEmpty()) }
    var anio by rememberSaveable { mutableStateOf(partesIniciales?.anio.orEmpty()) }
    var camposVisibles by rememberSaveable { mutableStateOf(opcionesRapidas.isEmpty()) }

    /** Recompone la fecha cada vez que cambia una de las tres partes. */
    fun recomponer(nuevoDia: String, nuevoMes: String, nuevoAnio: String) {
        dia = nuevoDia
        mes = nuevoMes
        anio = nuevoAnio
        alCambiar(CalendarioSalud.componer(nuevoDia, nuevoMes, nuevoAnio).orEmpty())
    }

    fun elegirRapida(opcion: OpcionFechaRapida) {
        if (opcion == OpcionFechaRapida.PERSONALIZADA) {
            camposVisibles = true
            return
        }
        camposVisibles = false
        val fecha = opcion.resolver(fechaDeHoy)
        CalendarioSalud.descomponer(fecha)?.let {
            dia = it.dia
            mes = it.mes
            anio = it.anio
        }
        alCambiar(fecha)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (opcionesRapidas.isNotEmpty()) {
            val descripcionGrupo = stringResource(Res.string.a11y_fecha_opciones_rapidas)
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = descripcionGrupo },
                horizontalArrangement = Arrangement.spacedBy(espaciado.compacto),
                verticalArrangement = Arrangement.spacedBy(espaciado.compacto),
            ) {
                opcionesRapidas.forEach { opcion ->
                    ChipFecha(
                        etiqueta = stringResource(opcion.recurso()),
                        elegido = opcion.estaElegida(valor, fechaDeHoy, camposVisibles),
                        alPulsar = { elegirRapida(opcion) },
                    )
                }
            }
            Spacer(Modifier.height(espaciado.medio))
        }

        if (camposVisibles) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(espaciado.compacto),
            ) {
                CampoNumeroFecha(
                    valor = dia,
                    alCambiar = { recomponer(it.soloDigitos(2), mes, anio) },
                    etiqueta = stringResource(Res.string.fecha_campo_dia),
                    marcador = stringResource(Res.string.fecha_placeholder_dia),
                    descripcionAccesible = stringResource(Res.string.a11y_fecha_campo_dia),
                    modifier = Modifier.weight(1f),
                )
                CampoNumeroFecha(
                    valor = mes,
                    alCambiar = { recomponer(dia, it.soloDigitos(2), anio) },
                    etiqueta = stringResource(Res.string.fecha_campo_mes),
                    marcador = stringResource(Res.string.fecha_placeholder_mes),
                    descripcionAccesible = stringResource(Res.string.a11y_fecha_campo_mes),
                    modifier = Modifier.weight(1f),
                )
                CampoNumeroFecha(
                    valor = anio,
                    alCambiar = { recomponer(dia, mes, it.soloDigitos(4)) },
                    etiqueta = stringResource(Res.string.fecha_campo_anio),
                    marcador = stringResource(Res.string.fecha_placeholder_anio),
                    descripcionAccesible = stringResource(Res.string.a11y_fecha_campo_anio),
                    modifier = Modifier.weight(1.4f),
                )
            }
        }

        if (valor.isNotBlank()) {
            val anuncio = stringResource(Res.string.a11y_fecha_seleccionada, valor)
            Spacer(Modifier.height(espaciado.compacto))
            Text(
                text = stringResource(Res.string.fecha_seleccionada, valor),
                style = MaterialTheme.typography.labelMedium,
                color = colores.textoSecundario,
                modifier = Modifier.semantics { contentDescription = anuncio },
            )
        }

        if (error != null) {
            Spacer(Modifier.height(espaciado.compacto))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Chip de opcion rapida. Sin relleno agresivo: borde y color de texto bastan. */
@Composable
private fun ChipFecha(etiqueta: String, elegido: Boolean, alPulsar: () -> Unit) {
    val espaciado = LocalEspaciadoSalud.current
    val colores = LocalColoresSalud.current
    val descripcion = if (elegido) {
        stringResource(Res.string.a11y_fecha_opcion_elegida, etiqueta)
    } else {
        stringResource(Res.string.a11y_fecha_opcion, etiqueta)
    }
    val forma = FormaSalud.pastilla

    Text(
        text = etiqueta,
        style = MaterialTheme.typography.labelLarge,
        color = if (elegido) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            colores.textoSecundario
        },
        modifier = Modifier
            .heightIn(min = AreaTactilMinima)
            .background(
                color = if (elegido) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    colores.fondoCampo
                },
                shape = forma,
            )
            .border(
                width = 1.dp,
                color = if (elegido) MaterialTheme.colorScheme.primary else colores.separador,
                shape = forma,
            )
            .clickable(onClick = alPulsar)
            .semantics {
                contentDescription = descripcion
                selected = elegido
            }
            .padding(horizontal = espaciado.medio, vertical = espaciado.compacto + 2.dp),
    )
}

@Composable
private fun CampoNumeroFecha(
    valor: String,
    alCambiar: (String) -> Unit,
    etiqueta: String,
    marcador: String,
    descripcionAccesible: String,
    modifier: Modifier = Modifier,
) {
    CampoTextoRellenoSalud(
        valor = valor,
        alCambiar = alCambiar,
        etiqueta = etiqueta,
        marcador = marcador,
        descripcionAccesible = descripcionAccesible,
        tipoTeclado = KeyboardType.Number,
        modifier = modifier,
    )
}

/** Fecha que representa cada atajo, calculada sobre la de hoy. */
private fun OpcionFechaRapida.resolver(fechaDeHoy: String): String = when (this) {
    OpcionFechaRapida.HOY -> fechaDeHoy
    OpcionFechaRapida.AYER -> CalendarioSalud.restarDias(fechaDeHoy, 1)
    OpcionFechaRapida.HACE_UNA_SEMANA -> CalendarioSalud.restarSemanas(fechaDeHoy, 1)
    OpcionFechaRapida.HACE_UN_MES -> CalendarioSalud.restarMeses(fechaDeHoy, 1)
    OpcionFechaRapida.PERSONALIZADA -> ""
}

/**
 * "Otra fecha" queda marcada cuando los campos estan abiertos; el resto, cuando
 * el valor actual coincide con lo que ese atajo representa.
 */
private fun OpcionFechaRapida.estaElegida(
    valor: String,
    fechaDeHoy: String,
    camposVisibles: Boolean,
): Boolean = if (this == OpcionFechaRapida.PERSONALIZADA) {
    camposVisibles
} else {
    !camposVisibles && valor.isNotBlank() && valor == resolver(fechaDeHoy)
}

private fun OpcionFechaRapida.recurso() = when (this) {
    OpcionFechaRapida.HOY -> Res.string.fecha_opcion_hoy
    OpcionFechaRapida.AYER -> Res.string.fecha_opcion_ayer
    OpcionFechaRapida.HACE_UNA_SEMANA -> Res.string.fecha_opcion_hace_una_semana
    OpcionFechaRapida.HACE_UN_MES -> Res.string.fecha_opcion_hace_un_mes
    OpcionFechaRapida.PERSONALIZADA -> Res.string.fecha_opcion_personalizada
}

/** Solo digitos y como maximo [maximo]: evita pelear con el teclado del sistema. */
private fun String.soloDigitos(maximo: Int): String =
    filter { it.isDigit() }.take(maximo)
