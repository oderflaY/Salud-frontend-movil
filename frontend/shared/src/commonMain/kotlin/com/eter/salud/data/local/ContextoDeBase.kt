package com.eter.salud.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Hilo en el que corre toda lectura y escritura de SQLite.
 *
 * Los ViewModels llaman a los repositorios desde el hilo principal; una consulta
 * en ese hilo congela la pantalla mientras el disco responde, y en un telefono
 * lento o con el almacenamiento casi lleno eso se nota como un toque ignorado.
 */
internal val ContextoDeBase: CoroutineContext = Dispatchers.IO

/**
 * Identificador unico generado en el dispositivo.
 *
 * Aleatorio y no un contador: un contador en memoria vuelve a empezar en cada
 * arranque y el segundo "cita_1" pisaria al primero en la base.
 */
@OptIn(ExperimentalUuidApi::class)
internal fun idLocal(prefijo: String): String = prefijo + Uuid.random().toHexString().take(LARGO_ID)

private const val LARGO_ID = 12
