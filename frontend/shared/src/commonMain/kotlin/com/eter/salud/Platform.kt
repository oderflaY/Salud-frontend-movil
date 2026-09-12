package com.eter.salud

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform