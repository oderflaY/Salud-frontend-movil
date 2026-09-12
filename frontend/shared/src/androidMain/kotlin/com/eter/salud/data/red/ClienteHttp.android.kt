package com.eter.salud.data.red

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

actual fun crearMotorHttp(): HttpClientEngineFactory<*> = OkHttp
