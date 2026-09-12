-- Módulos 1 y 2: autenticación de paciente y de profesional. Dos portales
-- separados a propósito (reglas de alta distintas) — ver docs/contratos-datos-backend.md
-- secciones 1 y 2.
--
-- El hashing de contraseña (argon2id) y la firma del JWT viven en Axum, nunca
-- en SQL: un secreto de firma no debe poder leerse con una consulta. Estas
-- tablas solo guardan el hash ya calculado.

create table app.pacientes (
  id_paciente         text primary key default app.generar_id('pac'),
  correo              citext not null unique,
  hash_contrasena     text not null,
  estado_cuenta       text not null default 'activa'
                        check (estado_cuenta in ('activa', 'bloqueada')),
  requiere_onboarding boolean not null default true,
  creado_en           timestamptz not null default now(),
  actualizado_en      timestamptz not null default now()
);

create trigger trg_pacientes_actualizado_en
  before update on app.pacientes
  for each row execute function app.set_actualizado_en();

create table app.medicos (
  id_medico            text primary key default app.generar_id('doc'),
  correo               citext not null unique,
  hash_contrasena      text not null,
  nombre               text not null,
  apellidos            text not null,
  tratamiento          text not null
                         check (tratamiento in ('Dr.', 'Dra.', 'Dr(a).')),
  cedula_profesional   text not null unique,
  estado_cuenta        text not null default 'activa'
                         check (estado_cuenta in ('activa', 'bloqueada')),
  estado_verificacion  text not null default 'PENDIENTE'
                         check (estado_verificacion in ('PENDIENTE', 'APROBADO', 'RECHAZADO')),
  creado_en            timestamptz not null default now(),
  actualizado_en       timestamptz not null default now()
);

create trigger trg_medicos_actualizado_en
  before update on app.medicos
  for each row execute function app.set_actualizado_en();

-- RLS: cada quien solo ve/edita su propia fila (Axum es dueño de la escritura
-- vía sqlx con rol de servicio, así que estas políticas cubren el acceso que
-- pudiera llegar por PostgREST a futuro, por ejemplo el propio médico leyendo
-- su estadoVerificacion desde /directorio-medico).
alter table app.pacientes enable row level security;
alter table app.medicos enable row level security;

create policy pacientes_propio_registro on app.pacientes
  for select
  using (id_paciente = current_setting('request.jwt.claims', true)::json->>'sub');

create policy medicos_propio_registro on app.medicos
  for select
  using (id_medico = current_setting('request.jwt.claims', true)::json->>'sub');
