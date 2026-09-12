package com.eter.salud.ui.componentes

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Iconografia de la app: Material Symbols Rounded, peso 400, sin relleno.
 *
 * ## Por que Symbols y no un paquete de iconos
 *
 * El artefacto `material-icons-extended` dejo de publicarse para Compose
 * Multiplatform, y la recomendacion de JetBrains es justo esta: traer los
 * trazos de Material Symbols como vectores. Aqui se incrustan los trazos
 * OFICIALES (repositorio google/material-design-icons, licencia Apache 2.0) en
 * vez de redibujarlos a mano: un icono dibujado "parecido" al estandar es peor
 * que uno propio, porque el ojo detecta que casi es el de siempre.
 *
 * ## Por que una sola familia en toda la app
 *
 * Antes cada glifo se dibujaba con primitivas de `Canvas`. Sustituirlos solo en
 * dos pantallas habria dejado dos voces iconograficas conviviendo -- trazo
 * propio en unas, Symbols en otras --, y mezclar familias de iconos es el mismo
 * error que mezclar tres fuentes. Por eso el cambio vive AQUI: [IconoSalud]
 * conserva su firma y las veinte pantallas que lo usan heredan la familia nueva
 * sin tocar una linea.
 *
 * Los trazos usan la caja de Symbols (0..960 con el origen arriba a la
 * izquierda desplazado a -960), que se corrige con una traslacion de grupo.
 */
enum class GlifoSalud(internal val trazo: String) {
    /** `id_card`: la tarjeta de emergencia que lee el paramedico. */
    TARJETA(
        "M720-440q17 0 28.5-11.5T760-480q0-17-11.5-28.5T720-520H600q-17 0-28.5 11.5T560-480q0 17 11.5 28.5T600-440h120Zm0-120q17 0 28.5-11.5T760-600q0-17-11.5-28.5T720-640H600q-17 0-28.5 11.5T560-600q0 17 11.5 28.5T600-560h120ZM360-440q-36 0-65 6.5T244-413q-21 13-32 29.5T201-348q0 12 9 20t22 8h256q13 0 22-8.5t9-21.5q0-17-11-33t-32-30q-22-14-51-20.5t-65-6.5Zm0-40q33 0 56.5-23.5T440-560q0-33-23.5-56.5T360-640q-33 0-56.5 23.5T280-560q0 33 23.5 56.5T360-480ZM160-160q-33 0-56.5-23.5T80-240v-480q0-33 23.5-56.5T160-800h640q33 0 56.5 23.5T880-720v480q0 33-23.5 56.5T800-160H160Zm0-80h640v-480H160v480Zm0 0v-480 480Z",
    ),

    /** `edit_note`: el diario de sintomas. */
    DIARIO(
        "M200-400q-17 0-28.5-11.5T160-440q0-17 11.5-28.5T200-480h200q17 0 28.5 11.5T440-440q0 17-11.5 28.5T400-400H200Zm0-160q-17 0-28.5-11.5T160-600q0-17 11.5-28.5T200-640h360q17 0 28.5 11.5T600-600q0 17-11.5 28.5T560-560H200Zm0-160q-17 0-28.5-11.5T160-760q0-17 11.5-28.5T200-800h360q17 0 28.5 11.5T600-760q0 17-11.5 28.5T560-720H200Zm320 520v-66q0-8 3-15.5t9-13.5l209-208q9-9 20-13t22-4q12 0 23 4.5t20 13.5l37 37q8 9 12.5 20t4.5 22q0 11-4 22.5T863-380L655-172q-6 6-13.5 9t-15.5 3h-66q-17 0-28.5-11.5T520-200Zm300-223-37-37 37 37ZM580-220h38l121-122-18-19-19-18-122 121v38Zm141-141-19-18 37 37-18-19Z",
    ),

    /** `stethoscope`: los medicos del paciente. */
    MEDICOS(
        "M540-80q-108 0-184-76t-76-184v-23q-86-14-143-80.5T80-600v-200q0-17 11.5-28.5T120-840h80q0-17 11.5-28.5T240-880q17 0 28.5 11.5T280-840v80q0 17-11.5 28.5T240-720q-17 0-28.5-11.5T200-760h-40v160q0 66 47 113t113 47q66 0 113-47t47-113v-160h-40q0 17-11.5 28.5T400-720q-17 0-28.5-11.5T360-760v-80q0-17 11.5-28.5T400-880q17 0 28.5 11.5T440-840h80q17 0 28.5 11.5T560-800v200q0 90-57 156.5T360-363v23q0 75 52.5 127.5T540-160q75 0 127.5-52.5T720-340v-67q-35-13-57.5-43.5T640-520q0-50 35-85t85-35q50 0 85 35t35 85q0 39-22.5 69.5T800-407v67q0 108-76 184T540-80Zm220-400q17 0 28.5-11.5T800-520q0-17-11.5-28.5T760-560q-17 0-28.5 11.5T720-520q0 17 11.5 28.5T760-480Zm0-40Z",
    ),

    /** `history`: el historial es la linea de tiempo del paciente. */
    HISTORIAL(
        "M480-120q-126 0-223-76.5T131-392q-4-15 6-27.5t27-14.5q16-2 29 6t18 24q24 90 99 147t170 57q117 0 198.5-81.5T760-480q0-117-81.5-198.5T480-760q-69 0-129 32t-101 88h70q17 0 28.5 11.5T360-600q0 17-11.5 28.5T320-560H160q-17 0-28.5-11.5T120-600v-160q0-17 11.5-28.5T160-800q17 0 28.5 11.5T200-760v54q51-64 124.5-99T480-840q75 0 140.5 28.5t114 77q48.5 48.5 77 114T840-480q0 75-28.5 140.5t-77 114q-48.5 48.5-114 77T480-120Zm40-376 100 100q11 11 11 28t-11 28q-11 11-28 11t-28-11L452-452q-6-6-9-13.5t-3-15.5v-159q0-17 11.5-28.5T480-680q17 0 28.5 11.5T520-640v144Z",
    ),

    /** `contactless`: la lectura NFC de la tarjeta. */
    ONDAS_NFC(
        "M276-480q0 9-1 18.5t-3 18.5q-3 17 4 32.5t23 21.5q16 6 30.5-1t18.5-23q4-16 6-33t2-34q0-17-2-34t-6-33q-4-16-18.5-23t-30.5-1q-16 6-23 21.5t-4 32.5q2 9 3 18.5t1 18.5Zm140 0q0 24-3 47t-9 45q-5 17 1.5 32t21.5 21q16 7 31.5-.5T479-360q9-29 13-59t4-61q0-31-4-61t-13-59q-5-17-20.5-24.5T427-625q-15 6-21.5 21t-1.5 32q6 22 9 45t3 47Zm140 0q0 37-5 72.5T535-338q-5 17 .5 33t21.5 23q16 7 31.5 0t20.5-24q14-42 20.5-85t6.5-89q0-46-6.5-89T609-654q-5-17-20.5-24t-31.5 0q-16 7-21.5 23t-.5 33q11 34 16 69.5t5 72.5ZM480-80q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-83 31.5-156T197-763q54-54 127-85.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 83-31.5 156T763-197q-54 54-127 85.5T480-80Zm0-80q134 0 227-93t93-227q0-134-93-227t-227-93q-134 0-227 93t-93 227q0 134 93 227t227 93Zm0-320Z",
    ),

    /** `check_circle`: un ESTADO ya conseguido (cedula validada, toma registrada). */
    VERIFICADO(
        "m424-408-86-86q-11-11-28-11t-28 11q-11 11-11 28t11 28l114 114q12 12 28 12t28-12l226-226q11-11 11-28t-11-28q-11-11-28-11t-28 11L424-408Zm56 328q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-83 31.5-156T197-763q54-54 127-85.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 83-31.5 156T763-197q-54 54-127 85.5T480-80Zm0-80q134 0 227-93t93-227q0-134-93-227t-227-93q-134 0-227 93t-93 227q0 134 93 227t227 93Zm0-320Z",
    ),

    /** `search`: la barra de busqueda del Directorio Medico. */
    BUSCAR(
        "M380-320q-109 0-184.5-75.5T120-580q0-109 75.5-184.5T380-840q109 0 184.5 75.5T640-580q0 44-14 83t-38 69l224 224q11 11 11 28t-11 28q-11 11-28 11t-28-11L532-372q-30 24-69 38t-83 14Zm0-80q75 0 127.5-52.5T560-580q0-75-52.5-127.5T380-760q-75 0-127.5 52.5T200-580q0 75 52.5 127.5T380-400Z",
    ),

    /** `send`: mandar un mensaje en el chat. */
    ENVIAR(
        "M792-443 176-183q-20 8-38-3.5T120-220v-520q0-22 18-33.5t38-3.5l616 260q25 11 25 37t-25 37ZM200-280l474-200-474-200v140l240 60-240 60v140Zm0 0v-400 400Z",
    ),

    /** `cancel`: solicitud rechazada, cita cancelada o toma omitida. */
    CANCELADO(
        "m480-424 116 116q11 11 28 11t28-11q11-11 11-28t-11-28L536-480l116-116q11-11 11-28t-11-28q-11-11-28-11t-28 11L480-536 364-652q-11-11-28-11t-28 11q-11 11-11 28t11 28l116 116-116 116q-11 11-11 28t11 28q11 11 28 11t28-11l116-116Zm0 344q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-83 31.5-156T197-763q54-54 127-85.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 83-31.5 156T763-197q-54 54-127 85.5T480-80Zm0-80q134 0 227-93t93-227q0-134-93-227t-227-93q-134 0-227 93t-93 227q0 134 93 227t227 93Zm0-320Z",
    ),

    /** `home`: panel principal del portal. */
    INICIO(
        "M240-200h120v-200q0-17 11.5-28.5T400-440h160q17 0 28.5 11.5T600-400v200h120v-360L480-740 240-560v360Zm-80 0v-360q0-19 8.5-36t23.5-28l240-180q21-16 48-16t48 16l240 180q15 11 23.5 28t8.5 36v360q0 33-23.5 56.5T720-120H560q-17 0-28.5-11.5T520-160v-200h-80v200q0 17-11.5 28.5T400-120H240q-33 0-56.5-23.5T160-200Zm320-270Z",
    ),

    /** `calendar_month`: la agenda de citas. */
    CALENDARIO(
        "M200-80q-33 0-56.5-23.5T120-160v-560q0-33 23.5-56.5T200-800h40v-40q0-17 11.5-28.5T280-880q17 0 28.5 11.5T320-840v40h320v-40q0-17 11.5-28.5T680-880q17 0 28.5 11.5T720-840v40h40q33 0 56.5 23.5T840-720v560q0 33-23.5 56.5T760-80H200Zm0-80h560v-400H200v400Zm0-480h560v-80H200v80Zm0 0v-80 80Zm280 240q-17 0-28.5-11.5T440-440q0-17 11.5-28.5T480-480q17 0 28.5 11.5T520-440q0 17-11.5 28.5T480-400Zm-160 0q-17 0-28.5-11.5T280-440q0-17 11.5-28.5T320-480q17 0 28.5 11.5T360-440q0 17-11.5 28.5T320-400Zm320 0q-17 0-28.5-11.5T600-440q0-17 11.5-28.5T640-480q17 0 28.5 11.5T680-440q0 17-11.5 28.5T640-400ZM480-240q-17 0-28.5-11.5T440-280q0-17 11.5-28.5T480-320q17 0 28.5 11.5T520-280q0 17-11.5 28.5T480-240Zm-160 0q-17 0-28.5-11.5T280-280q0-17 11.5-28.5T320-320q17 0 28.5 11.5T360-280q0 17-11.5 28.5T320-240Zm320 0q-17 0-28.5-11.5T600-280q0-17 11.5-28.5T640-320q17 0 28.5 11.5T680-280q0 17-11.5 28.5T640-240Z",
    ),

    /** `arrow_back`: la unica salida de una pantalla apilada. */
    ATRAS(
        "m313-440 196 196q12 12 11.5 28T508-188q-12 11-28 11.5T452-188L188-452q-6-6-8.5-13t-2.5-15q0-8 2.5-15t8.5-13l264-264q11-11 27.5-11t28.5 11q12 12 12 28.5T508-715L313-520h447q17 0 28.5 11.5T800-480q0 17-11.5 28.5T760-440H313Z",
    ),

    /** `pill`: una toma de medicacion. */
    PASTILLA(
        "M345-120q-94 0-159.5-65.5T120-345q0-45 17-86t49-73l270-270q32-32 73-49t86-17q94 0 159.5 65.5T840-615q0 45-17 86t-49 73L504-186q-32 32-73 49t-86 17Zm266-286 107-106q20-20 31-47t11-56q0-60-42.5-102.5T615-760q-29 0-56 11t-47 31L406-611l205 205ZM345-200q29 0 56-11t47-31l106-107-205-205-107 106q-20 20-31 47t-11 56q0 60 42.5 102.5T345-200Z",
    ),

    /**
     * `check`: marca de verificacion SUELTA, sin circulo.
     *
     * Es la hermana de [VERIFICADO] y la diferencia importa: aquel es un ESTADO
     * ya conseguido y por eso va cerrado en su circulo; este es una ACCION por
     * hacer -- "marcar esta toma" -- y una accion no se dibuja dentro de un
     * contenedor, porque el contenedor es el boton que la envuelve.
     */
    CONFIRMAR(
        "m382-354 339-339q12-12 28-12t28 12q12 12 12 28.5T777-636L410-268q-12 12-28 12t-28-12L182-440q-12-12-11.5-28.5T183-497q12-12 28.5-12t28.5 12l142 143Z",
    ),

    /** `person_add`: vincular a un especialista nuevo. */
    ANADIR_MEDICO(
        "M720-520h-80q-17 0-28.5-11.5T600-560q0-17 11.5-28.5T640-600h80v-80q0-17 11.5-28.5T760-720q17 0 28.5 11.5T800-680v80h80q17 0 28.5 11.5T920-560q0 17-11.5 28.5T880-520h-80v80q0 17-11.5 28.5T760-400q-17 0-28.5-11.5T720-440v-80Zm-360 40q-66 0-113-47t-47-113q0-66 47-113t113-47q66 0 113 47t47 113q0 66-47 113t-113 47ZM40-240v-32q0-34 17.5-62.5T104-378q62-31 126-46.5T360-440q66 0 130 15.5T616-378q29 15 46.5 43.5T680-272v32q0 33-23.5 56.5T600-160H120q-33 0-56.5-23.5T40-240Zm80 0h480v-32q0-11-5.5-20T580-306q-54-27-109-40.5T360-360q-56 0-111 13.5T140-306q-9 5-14.5 14t-5.5 20v32Zm240-320q33 0 56.5-23.5T440-640q0-33-23.5-56.5T360-720q-33 0-56.5 23.5T280-640q0 33 23.5 56.5T360-560Zm0-80Zm0 400Z",
    ),

    /** `settings`: los ajustes locales del dispositivo. */
    AJUSTES(
        "M433-80q-27 0-46.5-18T363-142l-9-66q-13-5-24.5-12T307-235l-62 26q-25 11-50 2t-39-32l-47-82q-14-23-8-49t27-43l53-40q-1-7-1-13.5v-27q0-6.5 1-13.5l-53-40q-21-17-27-43t8-49l47-82q14-23 39-32t50 2l62 26q11-8 23-15t24-12l9-66q4-26 23.5-44t46.5-18h94q27 0 46.5 18t23.5 44l9 66q13 5 24.5 12t22.5 15l62-26q25-11 50-2t39 32l47 82q14 23 8 49t-27 43l-53 40q1 7 1 13.5v27q0 6.5-2 13.5l53 40q21 17 27 43t-8 49l-48 82q-14 23-39 32t-50-2l-60-26q-11 8-23 15t-24 12l-9 66q-4 26-23.5 44T527-80h-94Zm7-80h79l14-106q31-8 57.5-23.5T639-327l99 41 39-68-86-65q5-14 7-29.5t2-31.5q0-16-2-31.5t-7-29.5l86-65-39-68-99 42q-22-23-48.5-38.5T533-694l-13-106h-79l-14 106q-31 8-57.5 23.5T321-633l-99-41-39 68 86 64q-5 15-7 30t-2 32q0 16 2 31t7 30l-86 65 39 68 99-42q22 23 48.5 38.5T427-266l13 106Zm42-180q58 0 99-41t41-99q0-58-41-99t-99-41q-59 0-99.5 41T342-480q0 58 40.5 99t99.5 41Zm-2-140Z",
    ),

    /** `forum`: la bandeja de conversaciones del medico. */
    CONVERSACIONES(
        "M280-240q-17 0-28.5-11.5T240-280v-80h520v-360h80q17 0 28.5 11.5T880-680v503q0 27-24.5 37.5T812-148l-92-92H280Zm-40-200-92 92q-19 19-43.5 8.5T80-377v-463q0-17 11.5-28.5T120-880h520q17 0 28.5 11.5T680-840v360q0 17-11.5 28.5T640-440H240Zm360-80v-280H160v280h440Zm-440 0v-280 280Z",
    ),

    /** `chevron_right`: "ir a", al final de una fila que abre otra pantalla. */
    SIGUIENTE(
        "M504-480 348-636q-11-11-11-28t11-28q11-11 28-11t28 11l184 184q6 6 8.5 13t2.5 15q0 8-2.5 15t-8.5 13L404-268q-11 11-28 11t-28-11q-11-11-11-28t11-28l156-156Z",
    ),

    /** `chevron_left`: la semana anterior del calendario. */
    ANTERIOR(
        "m432-480 156 156q11 11 11 28t-11 28q-11 11-28 11t-28-11L348-452q-6-6-8.5-13t-2.5-15q0-8 2.5-15t8.5-13l184-184q11-11 28-11t28 11q11 11 11 28t-11 28L432-480Z",
    ),

    /** `event`: una cita con fecha y hora. */
    EVENTO(
        "M580-240q-42 0-71-29t-29-71q0-42 29-71t71-29q42 0 71 29t29 71q0 42-29 71t-71 29ZM200-80q-33 0-56.5-23.5T120-160v-560q0-33 23.5-56.5T200-800h40v-40q0-17 11.5-28.5T280-880q17 0 28.5 11.5T320-840v40h320v-40q0-17 11.5-28.5T680-880q17 0 28.5 11.5T720-840v40h40q33 0 56.5 23.5T840-720v560q0 33-23.5 56.5T760-80H200Zm0-80h560v-400H200v400Zm0-480h560v-80H200v80Zm0 0v-80 80Z",
    ),

    /** `schedule`: una toma que aun no toca o no se ha registrado. */
    PENDIENTE(
        "M520-496v-144q0-17-11.5-28.5T480-680q-17 0-28.5 11.5T440-640v159q0 8 3 15.5t9 13.5l132 132q11 11 28 11t28-11q11-11 11-28t-11-28L520-496ZM480-80q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-83 31.5-156T197-763q54-54 127-85.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 83-31.5 156T763-197q-54 54-127 85.5T480-80Zm0-400Zm0 320q133 0 226.5-93.5T800-480q0-133-93.5-226.5T480-800q-133 0-226.5 93.5T160-480q0 133 93.5 226.5T480-160Z",
    ),

    // ---------------------------------------------------------------------
    // Los cuatro glifos de aqui abajo (adjuntar un archivo en el chat) NO son
    // trazos oficiales de Material Symbols: son geometria propia, hecha solo
    // de rectangulos y un triangulo, igual que `IsotipoSalud` en
    // `ComponentesSalud.kt`. Se opto por esto en vez de incrustar un trazo
    // oficial "de memoria" porque un trazo mal transcrito falla en TIEMPO DE
    // EJECUCION (el parseo de `addPathNodes`), no en compilacion, y no hay
    // forma de ensayar el render en este entorno. Un icono compuesto de
    // rectangulos es sencillo de verificar a mano antes de que el usuario lo
    // vea. Si mas adelante se dispone del trazo oficial exacto, se sustituye
    // aqui sin tocar el resto del archivo.

    /** Boton de "adjuntar" del chat: una cruz simple, sin curvas. */
    ADJUNTAR(
        "M420,-760 L540,-760 L540,-200 L420,-200 Z M200,-540 L760,-540 L760,-420 L200,-420 Z",
    ),

    /** Opcion "Foto" de la hoja de adjuntar: marco con una montana dentro. */
    FOTO(
        "M180,-780 L780,-780 L780,-720 L180,-720 Z " +
            "M180,-240 L780,-240 L780,-180 L180,-180 Z " +
            "M180,-780 L240,-780 L240,-180 L180,-180 Z " +
            "M720,-780 L780,-780 L780,-180 L720,-180 Z " +
            "M280,-240 L460,-500 L640,-240 Z",
    ),

    /** Opcion "Archivo" de la hoja de adjuntar: una hoja con lineas de texto. */
    ARCHIVO(
        "M220,-780 L740,-780 L740,-730 L220,-730 Z " +
            "M220,-250 L740,-250 L740,-200 L220,-200 Z " +
            "M220,-780 L270,-780 L270,-200 L220,-200 Z " +
            "M690,-780 L740,-780 L740,-200 L690,-200 Z " +
            "M300,-620 L660,-620 L660,-590 L300,-590 Z " +
            "M300,-540 L660,-540 L660,-510 L300,-510 Z " +
            "M300,-460 L560,-460 L560,-430 L300,-430 Z",
    ),

    /** Opcion "Escanear documento": las cuatro esquinas de un visor de escaneo. */
    ESCANER(
        "M160,-800 L320,-800 L320,-750 L160,-750 Z " +
            "M160,-800 L210,-800 L210,-640 L160,-640 Z " +
            "M640,-800 L800,-800 L800,-750 L640,-750 Z " +
            "M750,-800 L800,-800 L800,-640 L750,-640 Z " +
            "M160,-210 L320,-210 L320,-160 L160,-160 Z " +
            "M160,-320 L210,-320 L210,-160 L160,-160 Z " +
            "M640,-210 L800,-210 L800,-160 L640,-160 Z " +
            "M750,-320 L800,-320 L800,-160 L750,-160 Z",
    ),
}

/**
 * Icono decorativo: se marca sin semantica porque su significado ya lo aporta
 * la etiqueta del elemento que lo contiene. Duplicarlo haria que TalkBack y
 * VoiceOver leyeran lo mismo dos veces.
 */
@Composable
fun IconoSalud(
    glifo: GlifoSalud,
    modifier: Modifier = Modifier,
    lado: Dp = 28.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Icon(
        imageVector = VECTORES.getValue(glifo),
        contentDescription = null,
        tint = color,
        modifier = modifier
            .size(lado)
            .clearAndSetSemantics { },
    )
}

/**
 * Los vectores se construyen una sola vez, al primer uso.
 *
 * Parsear un trazo de Symbols cuesta poco, pero la barra de secciones pinta tres
 * iconos en cada recomposicion: construirlos alli cada vez seria trabajo
 * repetido en el marco mas visible de la app.
 */
private val VECTORES: Map<GlifoSalud, ImageVector> by lazy {
    GlifoSalud.entries.associateWith { glifo ->
        ImageVector.Builder(
            name = glifo.name,
            defaultWidth = LADO_SIMBOLO,
            defaultHeight = LADO_SIMBOLO,
            viewportWidth = CAJA_SIMBOLO,
            viewportHeight = CAJA_SIMBOLO,
        )
            // La caja de Symbols es `0 -960 960 960`: se baja 960 para que el
            // trazo caiga dentro del lienzo positivo de Compose.
            .addGroup(translationY = CAJA_SIMBOLO)
            .addPath(pathData = addPathNodes(glifo.trazo), fill = SolidColor(Color.Black))
            .clearGroup()
            .build()
    }
}

private val LADO_SIMBOLO = 24.dp
private const val CAJA_SIMBOLO = 960f
