import org.jetbrains.kotlin.gradle.dsl.JvmTarget
// Importado en vez de usar java.util.Properties completo: dentro del Kotlin DSL
// `java` resuelve a la extension de Gradle, no al paquete.
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

/**
 * Configuracion que no puede vivir en el repositorio: la URL del backend de
 * cada desarrollador y las credenciales de firma. `local.properties` ya esta
 * ignorado por git en todo proyecto Android, asi que es el lugar natural.
 */
val propiedadesLocales = rootProject.file("local.properties").takeIf { it.exists() }?.let { archivo ->
    Properties().apply { archivo.inputStream().use(::load) }
}

fun propiedadLocal(clave: String): String? =
    propiedadesLocales?.getProperty(clave) ?: project.findProperty(clave) as String?

// 10.0.2.2 es como el emulador de Android ve el localhost de la maquina
// anfitriona. Un telefono fisico necesita la IP de esa maquina en la red local:
// se pone en local.properties, no aqui, para no fijar la IP de un desarrollador
// como valor del repositorio.
val urlBackendDebug = propiedadLocal("salud.baseUrl.debug") ?: "http://10.0.2.2:8000"
val urlBackendRelease = propiedadLocal("salud.baseUrl.release")

val credencialesFirma = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { archivo ->
    Properties().apply { archivo.inputStream().use(::load) }
}

android {
    namespace = "com.eter.salud"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.eter.salud"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    signingConfigs {
        // Solo se declara si existe keystore.properties: un clon recien hecho
        // debe poder compilar debug sin tener el almacen de claves.
        credencialesFirma?.let { credenciales ->
            create("release") {
                storeFile = rootProject.file(credenciales.getProperty("storeFile"))
                storePassword = credenciales.getProperty("storePassword")
                keyAlias = credenciales.getProperty("keyAlias")
                keyPassword = credenciales.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "URL_BACKEND", "\"$urlBackendDebug\"")
        }
        release {
            // Sin esto el .aab pesa de mas y viaja con los nombres originales
            // de cada clase. Las reglas de proguard-rules.pro son obligatorias:
            // kotlinx.serialization se rompe si R8 borra los serializadores.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")

            // Un release apuntando a una IP de red local es una app muerta en
            // el telefono del usuario, y ademas Android bloquea el trafico sin
            // cifrar fuera de debug. Mejor que falle la compilacion aqui que
            // descubrirlo con la app ya publicada.
            val seCompilaRelease = gradle.startParameter.taskNames.any {
                it.contains("Release", ignoreCase = true)
            }
            if (seCompilaRelease) {
                require(!urlBackendRelease.isNullOrBlank()) {
                    "Falta salud.baseUrl.release en local.properties " +
                        "(por ejemplo: salud.baseUrl.release=https://api.tudominio.com)"
                }
                require(urlBackendRelease.startsWith("https://")) {
                    "salud.baseUrl.release debe ser https:// — Android bloquea " +
                        "el trafico sin cifrar en builds de release. Valor actual: $urlBackendRelease"
                }
            }
            buildConfigField("String", "URL_BACKEND", "\"${urlBackendRelease.orEmpty()}\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        // Todas las pantallas viven en :shared, y ese modulo KMP no expone una
        // tarea de lint propia para su codigo principal: sin esto, lint solo
        // revisaria MainActivity y el manifiesto.
        checkDependencies = true
    }
}