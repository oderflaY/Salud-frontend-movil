package com.eter.salud.data.red

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

actual fun crearMotorHttp(): HttpClientEngineFactory<*> = Darwin
