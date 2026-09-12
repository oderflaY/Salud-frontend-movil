-- Bootstrap: esquemas, roles, y las primitivas compartidas por los 11 módulos.
-- Nada de negocio vive aquí todavía — solo la infraestructura que evita repetirse
-- (generación de IDs, configuración en caliente, errores tipados, notificaciones,
-- timestamps de auditoría).

-- ---------------------------------------------------------------------------
-- Extensiones. `citext` para correos case-insensitive sin lógica repetida de
-- lower() en cada query; gen_random_uuid() ya es nativo desde PG13 (no
-- depende de pgcrypto).
-- ---------------------------------------------------------------------------
create extension if not exists citext;

-- ---------------------------------------------------------------------------
-- Esquemas: `app` son las tablas reales (nunca expuestas), `api` es lo único
-- que PostgREST sirve (vistas + funciones RPC).
-- ---------------------------------------------------------------------------
create schema if not exists app;
create schema if not exists api;

-- ---------------------------------------------------------------------------
-- Roles Postgres. `authenticator` es con el que se conecta PostgREST; hace
-- `SET ROLE` al rol real según el claim `role` del JWT que firma el backend.
-- `anon` es el único camino sin sesión (perfil de emergencia por RFID).
-- ---------------------------------------------------------------------------
do $$
begin
  if not exists (select 1 from pg_roles where rolname = 'anon') then
    create role anon nologin;
  end if;
  if not exists (select 1 from pg_roles where rolname = 'paciente') then
    create role paciente nologin;
  end if;
  if not exists (select 1 from pg_roles where rolname = 'medico') then
    create role medico nologin;
  end if;
  if not exists (select 1 from pg_roles where rolname = 'authenticator') then
    -- Sin contraseña a propósito: un secreto real no debe vivir en un archivo
    -- de migración versionado. scripts/set_secrets.sh la fija desde la env
    -- var AUTHENTICATOR_PASSWORD justo después de aplicar migraciones.
    create role authenticator noinherit login;
  end if;
end
$$;

grant anon, paciente, medico to authenticator;
grant usage on schema api to anon, paciente, medico;

-- ---------------------------------------------------------------------------
-- IDs con prefijo semántico (pac_, doc_, cita_, ...). El cliente nunca
-- construye un ID, siempre lo recibe generado así.
-- ---------------------------------------------------------------------------
create or replace function app.generar_id(p_prefijo text)
returns text
language sql
volatile
as $$
  select p_prefijo || '_' || replace(gen_random_uuid()::text, '-', '');
$$;

-- ---------------------------------------------------------------------------
-- Configuración en caliente (ej. MINUTOS_DE_RETENCION). Cambiar una fila,
-- no desplegar código, cuando el negocio ajusta una política.
-- ---------------------------------------------------------------------------
create table app.configuracion (
  clave  text primary key,
  valor  text not null
);

insert into app.configuracion (clave, valor) values
  ('minutos_retencion', '5'),
  ('umbral_alerta_inventario_default', '5');

create or replace function app.config_texto(p_clave text)
returns text
language sql
stable
as $$
  select valor from app.configuracion where clave = p_clave;
$$;

create or replace function app.config_entero(p_clave text)
returns integer
language sql
stable
as $$
  select valor::integer from app.configuracion where clave = p_clave;
$$;

-- ---------------------------------------------------------------------------
-- Errores tipados de punta a punta: SQLSTATE 'PT' + 3 dígitos de status HTTP
-- es la convención que PostgREST traduce directo a la respuesta HTTP.
--
-- El MESSAGE se deja como el motivo en texto plano, NO como
-- `json_build_object('motivo', ...)::text` — se probó en vivo (módulo 9,
-- reservar_temporalmente) y PostgREST NO parsea un MESSAGE con forma de
-- JSON: lo deja tal cual, como un string dentro de su propio
-- `{"code","message","details","hint"}`. Pelear contra eso hubiera dejado
-- un shape de error distinto entre RPC (PostgREST) y Axum; en vez de eso,
-- Axum adoptó el mismo campo `message` (ver backend/src/error.rs) para que
-- el cliente lea siempre `body.message`, venga de quien venga la respuesta.
-- Ver docs/errores.md para la tabla motivo -> HTTP de cada módulo.
-- ---------------------------------------------------------------------------
create or replace function app.lanzar_error(p_http_status int, p_motivo text)
returns void
language plpgsql
as $$
begin
  raise exception using
    errcode = 'PT' || p_http_status::text,
    message = p_motivo;
end;
$$;

-- ---------------------------------------------------------------------------
-- Timestamps de auditoría reutilizables en cualquier tabla que los necesite.
-- ---------------------------------------------------------------------------
create or replace function app.set_actualizado_en()
returns trigger
language plpgsql
as $$
begin
  new.actualizado_en := now();
  return new;
end;
$$;

-- ---------------------------------------------------------------------------
-- Tiempo real: un único canal NOTIFY ('realtime_events'); el payload trae el
-- canal lógico (chat:{id}, agenda:{id}, diario:{id}) para que Axum reparta a
-- los WebSockets suscritos sin necesitar un canal Postgres distinto por
-- conversación/médico.
-- ---------------------------------------------------------------------------
create or replace function app.emitir_evento(p_canal text, p_evento text, p_payload jsonb)
returns void
language plpgsql
as $$
begin
  perform pg_notify(
    'realtime_events',
    jsonb_build_object('canal', p_canal, 'evento', p_evento, 'payload', p_payload)::text
  );
end;
$$;
