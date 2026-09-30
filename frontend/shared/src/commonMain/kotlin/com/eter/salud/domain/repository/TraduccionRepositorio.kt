package com.eter.salud.domain.repository

/**
 * Traduce un mensaje del chat al idioma de quien lo lee.
 *
 * Nunca se calcula en el telefono: traducir de verdad necesita un modelo, y el
 * backend decide cual (uno propio del equipo, o un servicio externo). La app
 * solo pide el texto ya traducido.
 *
 * Tampoco se traduce todo de oficio: cada traduccion tarda segundos y cuesta
 * computo, asi que la pide quien la necesita, mensaje por mensaje.
 */
interface TraduccionRepositorio {

    /**
     * @param idiomaDestino codigo corto del idioma de quien lee (`es`, `en`).
     * El idioma de origen no se manda: lo reconoce el modelo, y darlo por
     * supuesto traduciria mal un mensaje escrito en otro idioma.
     */
    suspend fun traducir(texto: String, idiomaDestino: String): Result<String>
}
