# Salud-frontend-movil

Sistema de gestión y registro de pacientes: reportan qué medicamentos toman y
pueden comunicarse con sus médicos de confianza. Cliente móvil del backend en
[`../backend`](../backend) (Rust/Axum + PostgREST) — ver
[`mapeo-endpoints.md`](./mapeo-endpoints.md) y
[`docs/CONTRATOS_BACKEND.md`](./docs/CONTRATOS_BACKEND.md) para el contrato
entre ambos, y los equivalentes en [`../docs/mapeo-endpoints.md`](../docs/mapeo-endpoints.md)
y [`../docs/contratos-datos-backend.md`](../docs/contratos-datos-backend.md)
del lado del backend.

Fuente original: [oderflaY/Salud-frontend-movil](https://github.com/oderflaY/Salud-frontend-movil).

---

This is a Kotlin Multiplatform project targeting Android, iOS.

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./shared/src/iosMain/kotlin) folder would be the right pla ce for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
    folder is the appropriate location.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…