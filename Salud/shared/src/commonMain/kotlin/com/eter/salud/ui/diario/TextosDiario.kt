package com.eter.salud.ui.diario

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.eter.salud.domain.diario.SeveridadDiario
import com.eter.salud.ui.theme.LocalColoresSalud
import org.jetbrains.compose.resources.StringResource
import salud.shared.generated.resources.Res
import salud.shared.generated.resources.diario_severidad_ambar
import salud.shared.generated.resources.diario_severidad_rojo
import salud.shared.generated.resources.diario_severidad_verde

/**
 * Puente del semaforo del diario a `strings.xml` y a los tokens de color.
 *
 * Las etiquetas describen QUE HACER, no un diagnostico: "conviene revisarlo" en
 * vez de "riesgo moderado". El paciente lee esto sobre su propio texto, y una
 * palabra clinica sin medico detras asusta sin informar.
 */
internal fun SeveridadDiario.recurso(): StringResource = when (this) {
    SeveridadDiario.VERDE -> Res.string.diario_severidad_verde
    SeveridadDiario.AMBAR -> Res.string.diario_severidad_ambar
    SeveridadDiario.ROJO -> Res.string.diario_severidad_rojo
}

@Composable
internal fun SeveridadDiario.tinta(): Color = when (this) {
    SeveridadDiario.VERDE -> LocalColoresSalud.current.exito
    SeveridadDiario.AMBAR -> LocalColoresSalud.current.textoAdvertencia
    SeveridadDiario.ROJO -> LocalColoresSalud.current.acentoCritico
}

@Composable
internal fun SeveridadDiario.fondo(): Color = when (this) {
    SeveridadDiario.VERDE -> LocalColoresSalud.current.fondoCitaConfirmada
    SeveridadDiario.AMBAR -> LocalColoresSalud.current.fondoAdvertencia
    SeveridadDiario.ROJO -> LocalColoresSalud.current.fondoCritico
}
