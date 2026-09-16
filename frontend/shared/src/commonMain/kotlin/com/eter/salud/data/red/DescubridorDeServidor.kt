package com.eter.salud.data.red

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Encuentra el backend en la red local cuando la computadora que lo corre
 * cambió de IP.
 *
 * En desarrollo el backend vive en una computadora de la casa u oficina, y el
 * router le reparte una IP distinta de vez en cuando (`192.168.18.27` hoy,
 * `192.168.18.110` mañana). Con la URL fija en el APK, la app dejaba de
 * conectar y había que recompilar. Aquí:
 *
 *  1. Se prueban primero las direcciones conocidas (la última que funcionó y la
 *     del APK), en orden.
 *  2. Si ninguna responde y son IPs privadas, se barre su subred /24 en lotes
 *     en paralelo y se queda la primera que conteste como backend de Salud.
 *
 * Nunca barre direcciones públicas: solo 10.x, 172.16-31.x y 192.168.x.
 *
 * @param esServidorSalud la prueba de un candidato; inyectada para poder probar
 * esta lógica sin red.
 */
class DescubridorDeServidor(
    private val esServidorSalud: suspend (String) -> Boolean,
) {

    suspend fun encontrar(conocidas: List<String>): String? {
        val candidatas = conocidas.map(::sinDiagonalFinal).filter { it.isNotBlank() }.distinct()
        candidatas.firstOrNull { esServidorSalud(it) }?.let { return it }

        for (subred in candidatas.mapNotNull(::subredDe).distinct()) {
            val barrido = (1..254)
                .map { "${subred.esquema}://${subred.prefijo}.$it:${subred.puerto}" }
                .filter { it !in candidatas }
            for (lote in barrido.chunked(TAMANO_LOTE)) {
                val hallado = coroutineScope {
                    lote.map { url -> async { url.takeIf { esServidorSalud(it) } } }.awaitAll().firstOrNull { it != null }
                }
                if (hallado != null) return hallado
            }
        }
        return null
    }

    data class Subred(val esquema: String, val prefijo: String, val puerto: Int)

    companion object {
        private const val TAMANO_LOTE = 48
        private val URL_IPV4 = Regex("^(https?)://(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})(?::(\\d+))?/?$")

        /** La subred /24 de una URL con IP privada; null para nombres o IPs públicas. */
        fun subredDe(url: String): Subred? {
            val m = URL_IPV4.matchEntire(url.trim()) ?: return null
            val (esquema, a, b, c) = m.destructured
            val o1 = a.toInt()
            val o2 = b.toInt()
            val privada = o1 == 10 || (o1 == 192 && o2 == 168) || (o1 == 172 && o2 in 16..31)
            if (!privada) return null
            val puerto = m.groupValues[6].toIntOrNull() ?: if (esquema == "https") 443 else 80
            return Subred(esquema, "$a.$b.$c", puerto)
        }

        private fun sinDiagonalFinal(url: String) = url.trim().trimEnd('/')
    }
}
