# Correr el Backend Localmente

Esta guía te ayuda a levantar el backend (Rust/Axum + PostgreSQL) en tu máquina local.

## Requisitos

- **Docker Desktop** instalado y funcionando
- Archivo `.env` en la raíz del proyecto (copia de `.env.example`)

## Pasos para Correr el Backend

### 1. Verificar que Docker está instalado

```bash
docker --version
docker compose --version
```

Si no tienes Docker, instálalo:

**En Linux (CachyOS/Arch):**
```bash
sudo pacman -Syu docker docker-compose
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -aG docker $USER
```

Luego cierra y abre la terminal para que se apliquen los permisos.

### 2. Configurar el archivo `.env`

Copia el archivo de ejemplo:

```bash
cp .env.example .env
```

Abre `.env` y completa los valores requeridos (base de datos, secretos JWT, etc.).

### 3. Dar permisos a los scripts

Los scripts de migración necesitan permisos de ejecución:

```bash
chmod +x scripts/*.sh
```

### 4. Levantar los servicios

```bash
sudo docker compose up -d --build
```

Esto construye e inicia todos los servicios:
- PostgreSQL (base de datos)
- Migraciones de esquema
- PostgREST (API CRUD en puerto 3001)
- Backend Rust/Axum (puerto 8080)
- Caddy (proxy en puerto 8000)

### 5. Verificar que todo está corriendo

```bash
curl http://localhost:8000/healthz
```

Deberías ver: `ok`

También puedes verificar el estado de los contenedores:

```bash
sudo docker compose ps
```

## Comandos Útiles

### Ver los logs en tiempo real

```bash
sudo docker compose logs -f
```

Para ver logs de un servicio específico:

```bash
sudo docker compose logs -f backend
sudo docker compose logs -f db
sudo docker compose logs -f postgrest
```

### Detener todo

```bash
sudo docker compose down
```

### Reconstruir sin caché

```bash
sudo docker compose down
sudo docker compose up -d --build --no-cache
```

### Acceder a la base de datos PostgreSQL

```bash
sudo docker compose exec db psql -U ${POSTGRES_USER} -d ${POSTGRES_DB}
```

(Necesitas tener los valores de `.env` disponibles)

## Estructura de la Stack

```
Cliente (puerto 8000)
    ↓ (HTTP proxy)
Caddy (puerto 8000)
    ├─→ Backend (Rust/Axum, puerto 8080) — Auth, WebSocket, IA
    └─→ PostgREST (puerto 3001) — CRUD/RPC directo sobre la BD
            ↓
        PostgreSQL (puerto 5432)
```

## Endpoints Principales

- `POST /auth/login` — Autenticación
- `WS /realtime` — WebSocket tiempo real
- `POST /ia/resumen` — Resumen de chat (IA)
- Todos los demás endpoints → PostgREST (CRUD)

Para el mapeo completo de endpoints, ver [docs/mapeo-endpoints.md](docs/mapeo-endpoints.md).

## Solución de Problemas

### Error: "docker command not found"

Docker no está instalado o no está en el PATH. Instálalo según tu OS.

### Error: "Permission denied" en migrate.sh

Asegúrate de darle permisos:

```bash
chmod +x scripts/*.sh
```

### Error: "service didn't complete successfully"

Revisa los logs:

```bash
sudo docker compose logs migrate
```

### Base de datos vacía o con datos viejos

Para resetear la BD completamente:

```bash
sudo docker compose down -v
sudo docker compose up -d --build
```

(La bandera `-v` elimina los volúmenes de datos)

## Próximos pasos

- **Pruebas end-to-end**: `./scripts/smoke.sh`
- **Conectar el cliente móvil**: ver [README.md](README.md#conectar-el-cliente-móvil-a-este-backend)
- **Tests del backend**: `cd backend && cargo test`
- **Tests de BD**: `docker compose --profile test run dbtest`












# Dar permisos a scripts
chmod +x scripts/*.sh

# Copiar .env
cp .env.example .env

# Correr backend
sudo docker compose up -d --build

# Verificar que está corriendo
curl http://localhost:8000/healthz
