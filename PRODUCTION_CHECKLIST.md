# Publicar en Play Store

## Ya resuelto en el repositorio

- Build de release minificado con R8 (3.1 MB contra 21.6 MB del debug), con reglas para kotlinx.serialization, Ktor y SQLDelight.
- URL del backend por tipo de build, desde `frontend/local.properties`. El release **no compila** si falta la URL o no es `https://`.
- Firma de release leída de `frontend/keystore.properties` (ignorado por git, igual que `*.jks`).
- Sin respaldo en la nube ni transferencia a otro teléfono de los datos clínicos (`allowBackup` + `dataExtractionRules`).
- Quitado el permiso de cámara: el escáner de ML Kit no lo necesita.
- Ícono propio y gráficos de la ficha en `frontend/playstore/`.
- Pruebas: `bash scripts/verificar_demo.sh` (122 comprobaciones contra la API real; córrelo antes de cada demo), frontend 560/560, backend 38 unitarias + integración.

## Lo que te toca a ti

### 1. Backend con dominio y HTTPS

En `frontend/local.properties`:

```properties
salud.baseUrl.release=https://api.tudominio.com
```

Y en el `.env` del servidor, antes de levantarlo: cambiar `POSTGRES_PASSWORD`, `JWT_SECRET`, `AUTHENTICATOR_PASSWORD` y `ARGON2_SECRET_KEY` (`openssl rand -base64 48` para cada uno), y `RUST_LOG=info`. Opcional: `DEEPSEEK_API_KEY` para los resúmenes clínicos con IA en el chat del médico (sin ella, el médico ve el mensaje original y todo lo demás funciona igual). **No correr `seed_demo.sh` en producción**: borra todos los pacientes. Crea el primer administrador del panel con `docker compose exec -e ADMIN_CONTRASENA='…' backend backend crear-admin correo Nombre Apellidos` (sin `ADMIN_CONTRASENA` genera una temporal que se cambia al entrar); la cuenta demo `admin@salud.local` no debe existir en producción. Si publicas el dashboard web del médico, pon su dominio en `CORS_ORIGENES` (p. ej. `https://panel.tudominio.com`; nunca `*`) y en el dashboard `VITE_API_URL=https://api.tudominio.com/api/v1`.

### 2. Almacén de claves (una sola vez)

```bash
cd ~/Documents/GitHub/Salud-frontend-movil/frontend && keytool -genkeypair -v -keystore salud-release.jks -keyalg RSA -keysize 4096 -validity 10000 -alias salud
```

Luego crea `frontend/keystore.properties`:

```properties
storeFile=salud-release.jks
storePassword=la-que-elegiste
keyAlias=salud
keyPassword=la-que-elegiste
```

Guarda el `.jks` y las contraseñas fuera de esta computadora. Activa **Play App Signing** al crear la app: así ese archivo es solo la clave de subida y, si se pierde, Google la puede reemplazar.

### 3. Generar el paquete

Esta máquina tiene 7.5 GB de RAM; sin estas opciones el sistema mata la compilación:

```bash
cd ~/Documents/GitHub/Salud-frontend-movil/frontend && ./gradlew :androidApp:bundleRelease -Pkotlin.compiler.execution.strategy=in-process "-Dorg.gradle.jvmargs=-Xmx3g"
```

Sale en `androidApp/build/outputs/bundle/release/androidApp-release.aab`. El `mapping.txt` para desofuscar errores ya va dentro del `.aab`. Para cada actualización sube `versionCode` en `androidApp/build.gradle.kts`.

### 4. Play Console

- **Política de privacidad**: URL pública. Obligatoria para una app con datos de salud.
- **Seguridad de los datos**: declarar información de salud y datos personales; cifrado en tránsito: sí.
- **Declaración de apps de salud**.
- **Clasificación de contenido**.
- **Ficha**: `frontend/playstore/icono-512.png`, `frontend/playstore/grafico-destacado-1024x500.png` y al menos 2 capturas del teléfono.

## Decisiones ya aplicadas

1. **Eliminar cuenta** (Play la exige). En la app: *Ajustes → Cuenta → Eliminar cuenta*. En la web: `https://api.tudominio.com/auth/eliminar-cuenta` — esa URL va en el formulario de *Seguridad de los datos*. Se borra el acceso y lo que no es clínico; el expediente se conserva 5 años (NOM-004) y después se purga solo (`pg_cron`, todos los días a las 3:30). **La política de privacidad tiene que decir exactamente eso.** El detalle de qué se borra y qué no está en `db/migrations/0012_baja_de_cuentas.sql`.
2. **Recordatorios tras reiniciar el teléfono: pendiente.** Se quitó `RECEIVE_BOOT_COMPLETED` para no declarar un permiso sin uso. Mientras tanto, las tomas del día se reprograman al volver a abrir la pantalla de inicio.
3. **Alarmas exactas.** Se quitó `USE_EXACT_ALARM`; la app pide al paciente activar las alarmas exactas desde la pantalla de inicio.

Además se corrigió que Android nunca pedía el permiso de notificaciones: en Android 13+ los recordatorios de medicación no se programaban. Ahora la pantalla de inicio lo pide, y si el paciente lo negó antes, lo lleva a Ajustes.

## Avisos conocidos, sin acción

- R8 dice que no puede leer los metadatos de Kotlin 2.4. Es inofensivo aquí: la app no usa `kotlin-reflect`, y todos los serializadores sobreviven al minificado (verificado comparando el DEX de debug y el de release). Desaparece al actualizar AGP.
- Lint sugiere versiones más nuevas de dependencias. No se actualizan justo antes de publicar sin probar.
