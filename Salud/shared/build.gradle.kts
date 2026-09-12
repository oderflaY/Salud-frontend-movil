import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    
    android {
       namespace = "com.eter.salud.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }
    
    sourceSets {
        androidMain.dependencies {
            // `BackHandler` vive aqui. Sin el, el boton fisico de Atras cierra
            // la Activity desde cualquier pantalla en vez de retroceder.
            implementation(libs.androidx.activity.compose)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            implementation(libs.sqldelight.androidDriver)
            // Escaner de documentos con deteccion de bordes (adjuntar en el
            // chat). API de Play Services: presenta su propia UI de camara y
            // recorte, la app solo recibe la pagina ya resuelta.
            implementation(libs.mlkit.document.scanner)
            // Motor HTTP real de Android para el cliente Ktor hacia el backend.
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.nativeDriver)
            // Motor HTTP nativo de iOS (NSURLSession) para el cliente Ktor.
            implementation(libs.ktor.client.darwin)
        }
        commonMain.dependencies {
            // Persistencia ligera de la sesion. No es una base de datos: son
            // cuatro claves. Montar SQLite para eso seria desproporcionado.
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            // Bitacora del diario en SQLite local (Local-First). SQLDelight y no
            // Room: genera el codigo desde el `.sq` sin KSP, y el esquema SQL es
            // el contrato legible que un auditor puede revisar tal cual.
            implementation(libs.sqldelight.coroutines)
            // Cliente HTTP/WebSocket hacia el backend en Go. El motor concreto
            // (OkHttp, Darwin) se agrega por plataforma; aqui solo va lo comun.
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinxJson)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.websockets)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            // Motor HTTP falso: prueba cada repositorio *Remoto sin red real ni
            // depender de Android/iOS.
            implementation(libs.ktor.client.mock)
        }
        getByName("androidHostTest").dependencies {
            // SQLite real en la JVM para probar el esquema y los disparadores.
            implementation(libs.sqldelight.jvmDriver)
        }
    }
}

sqldelight {
    databases {
        create("BaseSalud") {
            packageName.set("com.eter.salud.data.db")
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}