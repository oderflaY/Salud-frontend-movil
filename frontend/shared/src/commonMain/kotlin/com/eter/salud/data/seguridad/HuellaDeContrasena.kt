package com.eter.salud.data.seguridad

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Huella de una contrasena para guardarla en la base local.
 *
 * Mientras no haya backend, el telefono guarda las cuentas, y una contrasena en
 * claro en SQLite se lee con cualquier explorador de archivos en un telefono
 * con root. Se guarda PBKDF2-HMAC-SHA256 con sal aleatoria por cuenta: aunque
 * alguien copie la base, tiene que atacar cada cuenta por separado y cada
 * intento le cuesta [ITERACIONES] rondas.
 *
 * ## Por que en Kotlin puro
 *
 * `MessageDigest` es de la JVM y CommonCrypto de iOS; una implementacion comun
 * se prueba una sola vez, contra los vectores publicados del RFC, y se comporta
 * igual en las dos plataformas. La sal sale de `Uuid.random()`, que usa el
 * generador criptografico del sistema en ambas.
 */
object HuellaDeContrasena {

    /**
     * Rondas de PBKDF2. Suficientes para que probar un diccionario sea caro y
     * pocas para que entrar no se note: ~100 ms en un telefono de gama media.
     */
    const val ITERACIONES = 10_000

    private const val ESQUEMA = "pbkdf2-sha256"
    private const val SEPARADOR = "$"
    private const val BYTES_HUELLA = 32

    /** Huella nueva con sal propia, lista para guardar. */
    @OptIn(ExperimentalUuidApi::class)
    fun crear(contrasena: String, iteraciones: Int = ITERACIONES): String {
        val sal = Uuid.random().toByteArray()
        val huella = pbkdf2(contrasena.encodeToByteArray(), sal, iteraciones, BYTES_HUELLA)
        return listOf(ESQUEMA, iteraciones.toString(), sal.aHex(), huella.aHex()).joinToString(SEPARADOR)
    }

    /**
     * Si [contrasena] produce la huella [guardada].
     *
     * Una huella con formato desconocido nunca verifica: ante la duda, la
     * puerta queda cerrada.
     */
    fun verificar(contrasena: String, guardada: String): Boolean {
        val partes = guardada.split(SEPARADOR)
        if (partes.size != 4 || partes[0] != ESQUEMA) return false
        val iteraciones = partes[1].toIntOrNull()?.takeIf { it > 0 } ?: return false
        val sal = partes[2].desdeHex() ?: return false
        val esperada = partes[3].desdeHex() ?: return false
        val calculada = pbkdf2(contrasena.encodeToByteArray(), sal, iteraciones, esperada.size)
        return calculada.igualEnTiempoConstante(esperada)
    }

    /** PBKDF2 (RFC 8018) con HMAC-SHA256 como funcion pseudoaleatoria. */
    internal fun pbkdf2(contrasena: ByteArray, sal: ByteArray, iteraciones: Int, longitud: Int): ByteArray {
        val hmac = Hmac256(contrasena)
        val salida = ByteArray(longitud)
        var bloque = 1
        var escritos = 0
        while (escritos < longitud) {
            var u = hmac.firmar(sal + bloque.aBytesBigEndian())
            val t = u.copyOf()
            repeat(iteraciones - 1) {
                u = hmac.firmar(u)
                for (i in t.indices) t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
            }
            val cuantos = minOf(t.size, longitud - escritos)
            t.copyInto(salida, escritos, 0, cuantos)
            escritos += cuantos
            bloque++
        }
        return salida
    }

    /** Comparar byte a byte sin salir antes: no revela cuanto acerto un intento. */
    private fun ByteArray.igualEnTiempoConstante(otro: ByteArray): Boolean {
        if (size != otro.size) return false
        var diferencia = 0
        for (i in indices) diferencia = diferencia or (this[i].toInt() xor otro[i].toInt())
        return diferencia == 0
    }

    private fun Int.aBytesBigEndian(): ByteArray =
        byteArrayOf((this ushr 24).toByte(), (this ushr 16).toByte(), (this ushr 8).toByte(), toByte())
}

/** HMAC-SHA256 (RFC 2104) con la clave ya preparada para reutilizarse. */
private class Hmac256(clave: ByteArray) {
    private val claveInterna: ByteArray
    private val claveExterna: ByteArray

    init {
        val normalizada = (if (clave.size > BLOQUE) Sha256.resumen(clave) else clave).copyOf(BLOQUE)
        claveInterna = ByteArray(BLOQUE) { (normalizada[it].toInt() xor 0x36).toByte() }
        claveExterna = ByteArray(BLOQUE) { (normalizada[it].toInt() xor 0x5c).toByte() }
    }

    fun firmar(mensaje: ByteArray): ByteArray =
        Sha256.resumen(claveExterna + Sha256.resumen(claveInterna + mensaje))

    private companion object {
        const val BLOQUE = 64
    }
}

/** SHA-256 (FIPS 180-4). */
internal object Sha256 {

    private val K = intArrayOf(
        0x428a2f98, 0x71374491, 0xb5c0fbcfL.toInt(), 0xe9b5dba5L.toInt(),
        0x3956c25b, 0x59f111f1, 0x923f82a4L.toInt(), 0xab1c5ed5L.toInt(),
        0xd807aa98L.toInt(), 0x12835b01, 0x243185be, 0x550c7dc3,
        0x72be5d74, 0x80deb1feL.toInt(), 0x9bdc06a7L.toInt(), 0xc19bf174L.toInt(),
        0xe49b69c1L.toInt(), 0xefbe4786L.toInt(), 0x0fc19dc6, 0x240ca1cc,
        0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152L.toInt(), 0xa831c66dL.toInt(), 0xb00327c8L.toInt(), 0xbf597fc7L.toInt(),
        0xc6e00bf3L.toInt(), 0xd5a79147L.toInt(), 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
        0x650a7354, 0x766a0abb, 0x81c2c92eL.toInt(), 0x92722c85L.toInt(),
        0xa2bfe8a1L.toInt(), 0xa81a664bL.toInt(), 0xc24b8b70L.toInt(), 0xc76c51a3L.toInt(),
        0xd192e819L.toInt(), 0xd6990624L.toInt(), 0xf40e3585L.toInt(), 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
        0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814L.toInt(), 0x8cc70208L.toInt(),
        0x90befffaL.toInt(), 0xa4506cebL.toInt(), 0xbef9a3f7L.toInt(), 0xc67178f2L.toInt(),
    )

    fun resumen(datos: ByteArray): ByteArray {
        val h = intArrayOf(
            0x6a09e667, 0xbb67ae85L.toInt(), 0x3c6ef372, 0xa54ff53aL.toInt(),
            0x510e527f, 0x9b05688cL.toInt(), 0x1f83d9ab, 0x5be0cd19,
        )
        val relleno = ((55 - datos.size) % 64 + 64) % 64
        val mensaje = datos.copyOf(datos.size + 1 + relleno + 8)
        mensaje[datos.size] = 0x80.toByte()
        val bits = datos.size.toLong() * 8
        for (i in 0 until 8) mensaje[mensaje.size - 1 - i] = (bits ushr (8 * i)).toByte()

        val w = IntArray(64)
        for (inicio in mensaje.indices step 64) {
            for (t in 0 until 16) {
                val j = inicio + t * 4
                w[t] = ((mensaje[j].toInt() and 0xff) shl 24) or
                    ((mensaje[j + 1].toInt() and 0xff) shl 16) or
                    ((mensaje[j + 2].toInt() and 0xff) shl 8) or
                    (mensaje[j + 3].toInt() and 0xff)
            }
            for (t in 16 until 64) {
                val s0 = w[t - 15].rotateRight(7) xor w[t - 15].rotateRight(18) xor (w[t - 15] ushr 3)
                val s1 = w[t - 2].rotateRight(17) xor w[t - 2].rotateRight(19) xor (w[t - 2] ushr 10)
                w[t] = w[t - 16] + s0 + w[t - 7] + s1
            }
            var a = h[0]
            var b = h[1]
            var c = h[2]
            var d = h[3]
            var e = h[4]
            var f = h[5]
            var g = h[6]
            var hh = h[7]
            for (t in 0 until 64) {
                val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
                val ch = (e and f) xor (e.inv() and g)
                val temp1 = hh + s1 + ch + K[t] + w[t]
                val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val temp2 = s0 + maj
                hh = g
                g = f
                f = e
                e = d + temp1
                d = c
                c = b
                b = a
                a = temp1 + temp2
            }
            h[0] += a
            h[1] += b
            h[2] += c
            h[3] += d
            h[4] += e
            h[5] += f
            h[6] += g
            h[7] += hh
        }
        return ByteArray(32) { (h[it / 4] ushr (24 - 8 * (it % 4))).toByte() }
    }
}

internal fun ByteArray.aHex(): String = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

internal fun String.desdeHex(): ByteArray? {
    if (length % 2 != 0) return null
    return ByteArray(length / 2) { i ->
        substring(i * 2, i * 2 + 2).toIntOrNull(16)?.toByte() ?: return null
    }
}
