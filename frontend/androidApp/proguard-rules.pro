# Reglas de R8 para el build de release.
#
# Sin esto, minificar rompe la app en silencio: kotlinx.serialization resuelve
# los serializadores por reflexion sobre el companion generado, y R8 lo borra
# por no ver ninguna llamada directa. El sintoma es una
# SerializationException en produccion que no aparece en debug.

# --- Trazas legibles -------------------------------------------------------
# Un crash de produccion sin numero de linea es casi imposible de diagnosticar.
# El mapping.txt que genera R8 se sube a Play Console para desofuscar.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- kotlinx.serialization -------------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Los serializadores generados ($$serializer) y los companion que los exponen.
-keep,includedescriptorclasses class com.eter.salud.**$$serializer { *; }
-keepclassmembers class com.eter.salud.** {
    *** Companion;
}
-keepclasseswithmembers class com.eter.salud.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Los enum que viajan en el JSON (EstadoToma, Especialidad, RiesgoPaciente,
# los MotivoFallo*): sus valores se resuelven por nombre.
-keepclassmembers enum com.eter.salud.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- Ktor ------------------------------------------------------------------
# El motor se inyecta explicitamente (ContenedorRed.crearMotorHttp), no por
# ServiceLoader, asi que no hace falta preservar el descubrimiento de motores.
# Solo se silencian las dependencias opcionales que Ktor referencia y que esta
# app no empaqueta.
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
-dontwarn kotlinx.atomicfu.**
-keepclassmembers class io.ktor.** {
    volatile <fields>;
}

# --- Corrutinas ------------------------------------------------------------
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# --- SQLDelight / SQLite ---------------------------------------------------
-dontwarn app.cash.sqldelight.**
-keep class app.cash.sqldelight.driver.android.** { *; }

# --- OkHttp / Okio ---------------------------------------------------------
# Ambas traen sus propias reglas de consumidor; esto solo calla los avisos de
# sus dependencias opcionales (Conscrypt, BouncyCastle, animal-sniffer).
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
