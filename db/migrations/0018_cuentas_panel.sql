-- Gestión de cuentas desde el panel web (2026-09-16).
--
--  - Rol ADMINISTRADOR: personal de la clínica que gestiona cuentas (alta de
--    médicos, cédulas, suspensión, contraseñas). NO ve expedientes clínicos:
--    no es médico tratante de nadie.
--  - Recuperación de contraseña sin correo electrónico: desde el login se pide
--    y queda en una bandeja que ve el administrador, quien da una contraseña
--    temporal. El sistema no envía correos.
--  - Médicos y administradores con contraseña temporal deben cambiarla en el
--    panel antes de usarlo. En la app móvil del médico no se bloquea (no tiene
--    esa pantalla), pero la temporal vence igual.
--
-- Todo lo sirve Axum (/api/v1); ninguna tabla ni función es para PostgREST.

-- ---------------------------------------------------------------------------
-- 1. Administradores
-- ---------------------------------------------------------------------------
create table app.administradores (
  id_admin                      text primary key default app.generar_id('adm'),
  correo                        citext not null unique,
  hash_contrasena               text not null,
  nombre                        text not null,
  apellidos                     text not null,
  estado_cuenta                 text not null default 'activa' check (estado_cuenta in ('activa', 'bloqueada')),
  debe_cambiar_contrasena       boolean not null default false,
  contrasena_temporal_expira_en timestamptz,
  sesiones_validas_desde        timestamptz,
  creado_en                     timestamptz not null default now(),
  actualizado_en                timestamptz not null default now()
);
alter table app.administradores enable row level security;
create trigger trg_administradores_actualizado_en
  before update on app.administradores
  for each row execute function app.set_actualizado_en();

alter table app.medicos
  add column debe_cambiar_contrasena boolean not null default false,
  add column contrasena_temporal_expira_en timestamptz;

-- La sesión de un administrador se revoca igual que la de los demás. El
-- cambio de contraseña obligatorio de médicos y administradores lo exige el
-- panel (Axum), no esta función: la app del médico no tiene esa pantalla.
create or replace function app.estado_sesion(p_rol text, p_sub text, p_iat bigint)
returns text
language sql
stable
security definer
set search_path = app, pg_temp
as $$
  select case p_rol
    when 'paciente' then coalesce((
      select case
        when p.estado_cuenta <> 'activa' then 'CUENTA_INACTIVA'
        when p.sesiones_validas_desde is not null
             and p_iat < extract(epoch from date_trunc('second', p.sesiones_validas_desde)) then 'SESION_REVOCADA'
        when p.debe_cambiar_contrasena then 'CAMBIO_CONTRASENA_REQUERIDO'
        else 'VIGENTE'
      end
      from app.pacientes p where p.id_paciente = p_sub), 'CUENTA_INACTIVA')
    when 'medico' then coalesce((
      select case
        when m.estado_cuenta <> 'activa' then 'CUENTA_INACTIVA'
        when m.sesiones_validas_desde is not null
             and p_iat < extract(epoch from date_trunc('second', m.sesiones_validas_desde)) then 'SESION_REVOCADA'
        else 'VIGENTE'
      end
      from app.medicos m where m.id_medico = p_sub), 'CUENTA_INACTIVA')
    when 'admin' then coalesce((
      select case
        when a.estado_cuenta <> 'activa' then 'CUENTA_INACTIVA'
        when a.sesiones_validas_desde is not null
             and p_iat < extract(epoch from date_trunc('second', a.sesiones_validas_desde)) then 'SESION_REVOCADA'
        else 'VIGENTE'
      end
      from app.administradores a where a.id_admin = p_sub), 'CUENTA_INACTIVA')
    else 'VIGENTE'
  end;
$$;

-- ---------------------------------------------------------------------------
-- 2. Auditoría de acciones sobre cuentas (NOM-004)
-- ---------------------------------------------------------------------------
-- Los nombres se guardan como estaban al momento: el registro debe seguir
-- diciendo a quién y quién aunque la cuenta se dé de baja después.
create table app.auditoria_cuentas (
  id_evento      text primary key default app.generar_id('aud'),
  tipo           text not null check (tipo in (
                   'reset_cuenta', 'suspender_cuenta', 'activar_cuenta',
                   'crear_doctor', 'aprobar_cedula', 'rechazar_cedula')),
  tipo_cuenta    text not null check (tipo_cuenta in ('paciente', 'medico')),
  id_cuenta      text not null,
  nombre_cuenta  text not null,
  tipo_actor     text not null check (tipo_actor in ('admin', 'medico')),
  id_actor       text not null,
  nombre_actor   text not null,
  motivo         text,
  ip_origen      text,
  creado_en      timestamptz not null default now()
);
create index idx_auditoria_cuentas_fecha on app.auditoria_cuentas(creado_en desc);
create index idx_auditoria_cuentas_cuenta on app.auditoria_cuentas(tipo_cuenta, id_cuenta, creado_en desc);
alter table app.auditoria_cuentas enable row level security;

-- ---------------------------------------------------------------------------
-- 3. Solicitudes de recuperación de contraseña
-- ---------------------------------------------------------------------------
-- A lo más una pendiente por cuenta: pedirla otra vez no llena la bandeja.
create table app.solicitudes_recuperacion (
  id_solicitud  text primary key default app.generar_id('solrec'),
  tipo_cuenta   text not null check (tipo_cuenta in ('paciente', 'medico')),
  id_cuenta     text not null,
  ip_origen     text,
  creado_en     timestamptz not null default now(),
  atendida_en   timestamptz,
  id_evento     text references app.auditoria_cuentas(id_evento)
);
create unique index ux_solicitud_pendiente on app.solicitudes_recuperacion(tipo_cuenta, id_cuenta)
  where atendida_en is null;
alter table app.solicitudes_recuperacion enable row level security;

-- ---------------------------------------------------------------------------
-- 4. Lecturas para el administrador
-- ---------------------------------------------------------------------------

create or replace function app.estado_cuenta_panel(p_estado text, p_debe_cambiar boolean)
returns text
language sql
immutable
as $$
  select case
    when p_estado = 'bloqueada' then 'suspendida'
    when p_debe_cambiar then 'reseteada'
    else 'activa'
  end;
$$;

create or replace function app.especialidad_legible(p_codigo text)
returns text
language sql
immutable
as $$
  select case p_codigo
    when 'MEDICINA_GENERAL' then 'Medicina general'
    when 'CARDIOLOGIA' then 'Cardiología'
    when 'PEDIATRIA' then 'Pediatría'
    when 'DERMATOLOGIA' then 'Dermatología'
    when 'GINECOLOGIA' then 'Ginecología'
    when 'PSIQUIATRIA' then 'Psiquiatría'
    when 'GERIATRIA' then 'Geriatría'
    when 'ENDOCRINOLOGIA' then 'Endocrinología'
    when 'NEUMOLOGIA' then 'Neumología'
    when 'NEUROLOGIA' then 'Neurología'
    else p_codigo
  end;
$$;

create or replace function app.panel_admin_medicos()
returns jsonb
language sql
stable
as $$
  select coalesce(jsonb_agg(jsonb_build_object(
           'id', m.id_medico,
           'nombre_completo', app.nombre_completo_medico(m.tratamiento, m.nombre, m.apellidos),
           'nombre', m.nombre,
           'apellidos', m.apellidos,
           'tratamiento', m.tratamiento,
           'correo', m.correo::text,
           'cedula_profesional', m.cedula_profesional,
           'especialidad', app.especialidad_legible(m.especialidad),
           'estado_verificacion', m.estado_verificacion,
           'estado_cuenta', app.estado_cuenta_panel(m.estado_cuenta, m.debe_cambiar_contrasena),
           'pacientes_vinculados', (
             select count(*) from app.control_accesos_medico ca
             join app.pacientes p on p.id_paciente = ca.id_paciente and p.estado_cuenta <> 'eliminada'
             where ca.id_medico = m.id_medico
               and (ca.fecha_expiracion is null or ca.fecha_expiracion >= current_date)),
           'solicitud_recuperacion_pendiente', exists (
             select 1 from app.solicitudes_recuperacion s
             where s.tipo_cuenta = 'medico' and s.id_cuenta = m.id_medico and s.atendida_en is null),
           'creado_en', app.iso_local(m.creado_en)
         ) order by m.estado_verificacion = 'APROBADO', m.apellidos, m.nombre), '[]'::jsonb)
  from app.medicos m
  where m.estado_cuenta <> 'eliminada';
$$;

-- Solo datos de la cuenta: el administrador no es médico tratante.
create or replace function app.panel_admin_pacientes()
returns jsonb
language sql
stable
as $$
  select coalesce(jsonb_agg(jsonb_build_object(
           'id', p.id_paciente,
           'nombre_completo', app.nombre_paciente_panel(p.id_paciente),
           'correo', p.correo::text,
           'estado_cuenta', app.estado_cuenta_panel(p.estado_cuenta, p.debe_cambiar_contrasena),
           'registro_completo', not p.requiere_onboarding,
           'medicos_vinculados', (
             select count(*) from app.control_accesos_medico ca
             where ca.id_paciente = p.id_paciente
               and (ca.fecha_expiracion is null or ca.fecha_expiracion >= current_date)),
           'solicitud_recuperacion_pendiente', exists (
             select 1 from app.solicitudes_recuperacion s
             where s.tipo_cuenta = 'paciente' and s.id_cuenta = p.id_paciente and s.atendida_en is null),
           'creado_en', app.iso_local(p.creado_en)
         ) order by app.nombre_paciente_panel(p.id_paciente)), '[]'::jsonb)
  from app.pacientes p
  where p.estado_cuenta <> 'eliminada';
$$;

create or replace function app.panel_admin_solicitudes()
returns jsonb
language sql
stable
as $$
  select coalesce(jsonb_agg(x.fila order by x.creado_en), '[]'::jsonb)
  from (
    select s.creado_en,
           jsonb_build_object(
             'id', s.id_solicitud,
             'tipo_cuenta', s.tipo_cuenta,
             'id_cuenta', s.id_cuenta,
             'nombre_cuenta', coalesce(
               app.nombre_completo_medico(m.tratamiento, m.nombre, m.apellidos),
               app.nombre_paciente_panel(p.id_paciente)),
             'correo', coalesce(m.correo::text, p.correo::text),
             'fecha', app.iso_local(s.creado_en)
           ) as fila
    from app.solicitudes_recuperacion s
    left join app.medicos m on s.tipo_cuenta = 'medico' and m.id_medico = s.id_cuenta and m.estado_cuenta <> 'eliminada'
    left join app.pacientes p on s.tipo_cuenta = 'paciente' and p.id_paciente = s.id_cuenta and p.estado_cuenta <> 'eliminada'
    where s.atendida_en is null
      and (m.id_medico is not null or p.id_paciente is not null)
  ) x;
$$;

-- Un evento de cualquiera de las dos bitácoras con la forma del panel.
create or replace function app.panel_eventos_auditoria()
returns table (creado_en timestamptz, tipo_cuenta text, id_cuenta text, fila jsonb)
language sql
stable
as $$
  select ar.creado_en, 'paciente', ar.id_paciente,
         jsonb_build_object(
           'id', ar.id_auditoria,
           'tipo', 'reset_cuenta',
           'tipo_cuenta', 'paciente',
           'id_cuenta', ar.id_paciente,
           'nombre_cuenta', app.nombre_paciente_panel(ar.id_paciente),
           'id_paciente', ar.id_paciente,
           'nombre_paciente', app.nombre_paciente_panel(ar.id_paciente),
           'realizado_por', app.nombre_completo_medico(m.tratamiento, m.nombre, m.apellidos),
           'fecha', app.iso_local(ar.creado_en),
           'motivo', ar.motivo)
  from app.auditoria_reset_cuenta ar
  join app.medicos m on m.id_medico = ar.id_medico
  union all
  select ac.creado_en, ac.tipo_cuenta, ac.id_cuenta,
         jsonb_build_object(
           'id', ac.id_evento,
           'tipo', ac.tipo,
           'tipo_cuenta', ac.tipo_cuenta,
           'id_cuenta', ac.id_cuenta,
           'nombre_cuenta', ac.nombre_cuenta,
           'id_paciente', case when ac.tipo_cuenta = 'paciente' then ac.id_cuenta end,
           'nombre_paciente', case when ac.tipo_cuenta = 'paciente' then ac.nombre_cuenta end,
           'realizado_por', ac.nombre_actor,
           'fecha', app.iso_local(ac.creado_en),
           'motivo', ac.motivo)
  from app.auditoria_cuentas ac;
$$;

create or replace function app.panel_admin_auditoria()
returns jsonb
language sql
stable
as $$
  select coalesce(jsonb_agg(x.fila order by x.creado_en desc), '[]'::jsonb)
  from (select e.creado_en, e.fila from app.panel_eventos_auditoria() e order by e.creado_en desc limit 1000) x;
$$;

-- El médico ve lo que se hizo sobre sus pacientes y sobre su propia cuenta.
create or replace function app.panel_auditoria(p_id_medico text)
returns jsonb
language sql
stable
as $$
  select coalesce(jsonb_agg(x.fila order by x.creado_en desc), '[]'::jsonb)
  from (
    select e.creado_en, e.fila
    from app.panel_eventos_auditoria() e
    where (e.tipo_cuenta = 'paciente' and app.vinculo_vigente(p_id_medico, e.id_cuenta))
       or (e.tipo_cuenta = 'medico' and e.id_cuenta = p_id_medico)
       or (e.fila->>'tipo' = 'reset_cuenta' and exists (
             select 1 from app.auditoria_reset_cuenta ar
             where ar.id_auditoria = e.fila->>'id' and ar.id_medico = p_id_medico))
    order by e.creado_en desc
    limit 500
  ) x;
$$;

-- ---------------------------------------------------------------------------
-- 5. Adherencia por día en la lista de pacientes (gráfica del panel)
-- ---------------------------------------------------------------------------
create or replace function app.adherencia_por_dia(p_id_paciente text)
returns jsonb
language sql
stable
as $$
  select jsonb_agg(jsonb_build_object(
           'fecha', d.dia::date,
           'programadas', coalesce(t.programadas, 0),
           'cumplidas', coalesce(t.cumplidas, 0)
         ) order by d.dia)
  from generate_series((app.hoy_local() - 6)::timestamp, app.hoy_local()::timestamp, interval '1 day') d(dia)
  left join lateral (
    select count(*)::int as programadas,
           (count(*) filter (where ra.estado in ('tomado', 'tomado_tarde')))::int as cumplidas
    from app.registro_adherencia ra
    join app.tratamientos tr on tr.id_tratamiento = ra.id_tratamiento
    where tr.id_paciente = p_id_paciente
      and (ra.fecha_hora_programada at time zone app.config_texto('zona_horaria_default'))::date = d.dia::date
      and ra.fecha_hora_programada <= now() - make_interval(mins => app.config_entero('minutos_gracia_toma'))
  ) t on true;
$$;

create or replace function app.panel_pacientes(p_id_medico text)
returns jsonb
language sql
stable
as $$
  select coalesce(jsonb_agg(
           app.panel_paciente(p_id_medico, x.id_paciente)
             || jsonb_build_object('adherencia_por_dia', app.adherencia_por_dia(x.id_paciente))
           order by x.orden, x.nombre), '[]'::jsonb)
  from (
    select ca.id_paciente,
           case app.riesgo_paciente(ca.id_paciente) when 'ALTO' then 0 when 'MEDIO' then 1 else 2 end as orden,
           app.nombre_paciente_panel(ca.id_paciente) as nombre
    from app.control_accesos_medico ca
    join app.pacientes p on p.id_paciente = ca.id_paciente and p.estado_cuenta <> 'eliminada'
    where ca.id_medico = p_id_medico
      and (ca.fecha_expiracion is null or ca.fecha_expiracion >= current_date)
  ) x;
$$;

revoke all on function app.estado_cuenta_panel(text, boolean) from public;
revoke all on function app.especialidad_legible(text) from public;
revoke all on function app.panel_admin_medicos() from public;
revoke all on function app.panel_admin_pacientes() from public;
revoke all on function app.panel_admin_solicitudes() from public;
revoke all on function app.panel_eventos_auditoria() from public;
revoke all on function app.panel_admin_auditoria() from public;
revoke all on function app.adherencia_por_dia(text) from public;

notify pgrst, 'reload schema';
