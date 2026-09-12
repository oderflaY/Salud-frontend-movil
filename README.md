# +salud

Sistema de gestión y registro de pacientes: reportan qué medicamentos toman,
llevan su expediente e historial médico, y se comunican con sus médicos de
confianza. Incluye un módulo de **emergencia por tarjeta RFID** (un
paramédico puede consultar el perfil de supervivencia de un paciente sin
sesión, con la tarjeta física como credencial) y capacidades de **IA**
(resumen de conversaciones de chat y traducción, vía DeepSeek).

Monorepo con dos partes:

- [`backend/`](backend/) — API en Rust/Axum + PostgreSQL/PostgREST.
- [`frontend/`](frontend/) — cliente móvil Kotlin Multiplatform (Android/iOS),
  originalmente el repo [oderflaY/Salud-frontend-movil](https://github.com/oderflaY/Salud-frontend-movil).

## Arquitectura

```
Celular / navegador
        │  HTTP :8000 (PROXY_PORT)
        ▼
   Caddy (proxy)
   ├── /auth/* /realtime /tarjetas/* /chat/*  → backend (Axum, :8080)
   └── todo lo demás (CRUD/RPC)               → PostgREST (:3000)
                                                       │
                                                       ▼
                                                  PostgreSQL
```

- **PostgREST** expone la mayoría de los módulos directamente sobre
  PostgreSQL (vistas y funciones `rpc/...`): expediente, adherencia,
  directorio médico, cartera de pacientes, chat, citas/agenda, diario.
- **Axum** (Rust) atiende lo que necesita secretos de firma, hashing, estado
  en memoria (WebSocket) o llamar a un servicio externo: autenticación,
  emergencia RFID, tiempo real (`/realtime`) e IA (resumen/traducción).
- Backend y PostgREST comparten el mismo `JWT_SECRET`: el token que emite
  Axum en el login es el mismo que PostgREST valida para las políticas RLS de
  cada RPC.

Ver [docs/mapeo-endpoints.md](docs/mapeo-endpoints.md) para la referencia
completa de endpoints y [docs/contratos-datos-backend.md](docs/contratos-datos-backend.md)
para el contrato de payloads que consume el cliente Kotlin.

## Estructura

```
backend/         Servicio Rust/Axum (auth, emergencia, realtime, ia)
db/
  migrations/    Esquema de PostgreSQL, un archivo por módulo (0001-0010)
  tests/         Pruebas pgTAP, una por módulo
deploy/caddy/    Configuración del proxy
docs/            Convenciones, contratos y mapeo de endpoints
frontend/        Cliente Kotlin Multiplatform (Android/iOS)
scripts/         migrate.sh, run_pgtap.sh, set_secrets.sh, smoke.sh
docker-compose.yml
```

## Cómo correrlo

Requiere Docker Desktop y un archivo `.env` (copia [`.env.example`](.env.example)
y llena los secretos — nunca se versiona).

```bash
docker compose up -d --build
```

Levanta `db` → `migrate` (aplica `db/migrations/*.sql`) → `postgrest` +
`backend` → `proxy` (Caddy en `:8000`, el único puerto que necesita hablar un
cliente).

Verificar que todo quedó arriba:

```bash
curl http://localhost:8000/healthz   # -> ok
```

## Pruebas

- **pgTAP** (esquema y políticas RLS): `docker compose --profile test run dbtest`
- **cargo test** (backend): `cd backend && cargo test`
- **Smoke test end-to-end** (los 11 módulos, contra el stack real):
  `./scripts/smoke.sh`

## Conectar el cliente móvil a este backend

Por defecto el cliente Kotlin usa repositorios locales (SQLite) y **no**
llama al backend — es el modo con el que ya viene probado el proyecto
original. Para apuntarlo a este backend:

1. En [`frontend/shared/src/commonMain/kotlin/com/eter/salud/data/red/ConfiguracionApi.kt`](frontend/shared/src/commonMain/kotlin/com/eter/salud/data/red/ConfiguracionApi.kt),
   fijar `USAR_BACKEND_REMOTO = true` y `BASE_URL` a la IP real de la máquina
   que corre Docker en tu red local (`10.0.2.2` solo sirve para el emulador
   de Android).
2. Para un teléfono físico, agregar esa misma IP a la lista blanca de
   `frontend/androidApp/src/debug/res/xml/network_security_config.xml`
   (Android bloquea HTTP sin cifrar hacia cualquier host no listado ahí).
3. Abrir el puerto `8000` en el firewall de Windows si el teléfono está en
   otra red que la propia PC:
   ```powershell
   New-NetFirewallRule -DisplayName "Salud backend (8000)" -Direction Inbound -Protocol TCP -LocalPort 8000 -Action Allow -Profile Any
   ```
4. Recompilar e instalar la app — el cambio del manifiesto de red va
   empaquetado en el APK, no se recoge con hot reload.

Ver [frontend/README.md](frontend/README.md) para el resto del cliente
(estructura Kotlin Multiplatform, cómo correr Android/iOS).
