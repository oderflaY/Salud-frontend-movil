-- Módulo 7: directorio médico + vinculación. Contrato: DirectorioMedicoRepositorio.
--
-- Nota de alcance: los 11 contratos del documento no incluyen un método para
-- que un médico fije su propia especialidad/universidad/disponibilidad — sin
-- eso el directorio nunca tendría nada que mostrar. Se agrega
-- `api.actualizar_mi_perfil_directorio` como una adición mínima y explícita
-- fuera de esos 11 contratos, documentada aquí por esa razón.

alter table app.medicos
  add column especialidad text
    check (especialidad in (
      'MEDICINA_GENERAL', 'CARDIOLOGIA', 'PEDIATRIA', 'DERMATOLOGIA', 'GINECOLOGIA',
      'PSIQUIATRIA', 'GERIATRIA', 'ENDOCRINOLOGIA', 'NEUMOLOGIA', 'NEUROLOGIA'
    )),
  add column universidad text,
  add column disponibilidad text;

alter table app.control_accesos_medico
  add column creado_en timestamptz not null default now();

create table app.conversaciones (
  id_conversacion text primary key default app.generar_id('conv'),
  id_paciente     text not null references app.pacientes(id_paciente) on delete cascade,
  id_medico       text not null references app.medicos(id_medico) on delete cascade,
  creado_en       timestamptz not null default now(),
  unique (id_paciente, id_medico)
);

create or replace function app.nombre_completo_medico(p_tratamiento text, p_nombre text, p_apellidos text)
returns text
language sql
immutable
as $$
  select trim(both ' ' from coalesce(p_tratamiento, '') || ' ' || p_nombre || ' ' || p_apellidos);
$$;

-- ---------------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------------
alter table app.conversaciones enable row level security;
create policy conversaciones_visibles_a_sus_participantes on app.conversaciones
  for select
  using (app.es_dueno_paciente(id_paciente) or id_medico = current_setting('request.jwt.claims', true)::json->>'sub');
-- RLS deniega por defecto si ningún policy aplica al comando: sin esta, el
-- INSERT de solicitar_vinculacion fallaría aunque el GRANT lo permita.
create policy conversaciones_creadas_por_paciente on app.conversaciones
  for insert
  with check (app.es_dueno_paciente(id_paciente));

grant select on app.conversaciones, app.medicos to paciente, medico;

-- Sin esto, api.directorio_medico (security_invoker) recibiría cero filas
-- para el rol paciente: la única SELECT policy que existía en app.medicos
-- (0002) es "el médico ve su propia fila", que no cubre "un paciente
-- navegando el directorio". Mismo error de fondo que la convención 11.
create policy medicos_visibles_en_directorio on app.medicos
  for select
  using (estado_verificacion = 'APROBADO' and estado_cuenta = 'activa');

-- Un médico puede editar su propio perfil de directorio (no el de otro).
create policy medicos_actualiza_propio_directorio on app.medicos
  for update
  using (id_medico = current_setting('request.jwt.claims', true)::json->>'sub')
  with check (id_medico = current_setting('request.jwt.claims', true)::json->>'sub');
grant update (especialidad, universidad, disponibilidad) on app.medicos to medico;

-- ---------------------------------------------------------------------------
-- Directorio: CRUD puro vía PostgREST — el filtro por especialidad lo hace
-- PostgREST solo (`?especialidad=eq.X`), sin RPC.
-- ---------------------------------------------------------------------------
create view api.directorio_medico with (security_invoker = true) as
select
  m.id_medico as "idMedico",
  app.nombre_completo_medico(m.tratamiento, m.nombre, m.apellidos) as "nombreCompleto",
  m.especialidad as "especialidad",
  (m.estado_verificacion = 'APROBADO') as "cedulaVerificada",
  m.universidad as "universidad",
  m.disponibilidad as "disponibilidad"
from app.medicos m
where m.estado_verificacion = 'APROBADO' and m.estado_cuenta = 'activa';

grant select on api.directorio_medico to paciente, medico;

create or replace function api.actualizar_mi_perfil_directorio(payload jsonb)
returns void
language plpgsql
security invoker
as $$
declare
  v_id_medico text := current_setting('request.jwt.claims', true)::json->>'sub';
begin
  if v_id_medico is null then
    perform app.lanzar_error(401, 'NO_AUTORIZADO');
  end if;

  update app.medicos set
    especialidad = coalesce(payload->>'especialidad', especialidad),
    universidad = coalesce(payload->>'universidad', universidad),
    disponibilidad = coalesce(payload->>'disponibilidad', disponibilidad)
  where id_medico = v_id_medico;
end;
$$;

grant execute on function api.actualizar_mi_perfil_directorio(jsonb) to medico;

-- ---------------------------------------------------------------------------
-- Vínculos: reutiliza app.control_accesos_medico (ya es "quién puede leer/
-- editar el expediente de quién" desde el módulo 3) — un vínculo del
-- directorio ES ese mismo control de acceso, con nivel inicial "lectura".
-- ---------------------------------------------------------------------------
create type api.tipo_medico_vinculado as (
  "idMedico" text, "nombreCompleto" text, "especialidad" text, "idConversacion" text
);

-- Helper SECURITY DEFINER solo para leer nombre/especialidad de un médico ya
-- sabido (no decide a quién se le muestra, eso lo hace el JOIN contra
-- control_accesos_medico más abajo, que sigue siendo SECURITY INVOKER y por
-- lo tanto sigue respetando RLS). Sin este helper, el JOIN directo a
-- app.medicos se topa con la misma política de "aprobado o es su propia
-- fila" que en api.directorio_medico — un médico recién dado de alta (aún
-- PENDIENTE) desaparecería del vínculo del paciente que sí lo agregó (bug
-- real, mismo patrón que app.cita_a_jsonb en el módulo 9).
create or replace function app.medico_resumen(p_id_medico text)
returns table(nombre_completo text, especialidad text)
language sql
stable
security definer
set search_path = app, pg_temp
as $$
  select app.nombre_completo_medico(m.tratamiento, m.nombre, m.apellidos), m.especialidad
  from app.medicos m where m.id_medico = p_id_medico;
$$;

revoke all on function app.medico_resumen(text) from public;
grant execute on function app.medico_resumen(text) to paciente, medico;

create or replace function api.medico_vinculado(id_paciente text)
returns api.tipo_medico_vinculado
language plpgsql
security invoker
stable
as $$
#variable_conflict use_column
declare
  v_id_medico text;
  v_id_conversacion text;
  v_resumen record;
begin
  select ca.id_medico, c.id_conversacion into v_id_medico, v_id_conversacion
  from app.control_accesos_medico ca
  join app.conversaciones c on c.id_medico = ca.id_medico and c.id_paciente = ca.id_paciente
  where ca.id_paciente = medico_vinculado.id_paciente
  order by ca.creado_en desc
  limit 1;

  if v_id_medico is null then
    return null;
  end if;

  select * into v_resumen from app.medico_resumen(v_id_medico);

  return (v_id_medico, v_resumen.nombre_completo, v_resumen.especialidad, v_id_conversacion)::api.tipo_medico_vinculado;
end;
$$;

grant execute on function api.medico_vinculado(text) to paciente, medico;

create or replace function api.medicos_vinculados(id_paciente text)
returns setof api.tipo_medico_vinculado
language plpgsql
security invoker
stable
as $$
#variable_conflict use_column
declare
  v_fila record;
  v_resumen record;
begin
  for v_fila in
    select ca.id_medico, c.id_conversacion
    from app.control_accesos_medico ca
    join app.conversaciones c on c.id_medico = ca.id_medico and c.id_paciente = ca.id_paciente
    where ca.id_paciente = medicos_vinculados.id_paciente
    order by ca.creado_en desc
  loop
    select * into v_resumen from app.medico_resumen(v_fila.id_medico);
    return next (v_fila.id_medico, v_resumen.nombre_completo, v_resumen.especialidad, v_fila.id_conversacion)::api.tipo_medico_vinculado;
  end loop;
  return;
end;
$$;

grant execute on function api.medicos_vinculados(text) to paciente, medico;

create or replace function api.solicitar_vinculacion(id_medico text)
returns api.tipo_medico_vinculado
language plpgsql
security invoker
as $$
#variable_conflict use_column
-- La lista de columnas de INSERT y de ON CONFLICT (...) es una posición
-- puramente de nombres de columna: ahí SIEMPRE debe ganar la columna, nunca
-- el parámetro, aunque se llamen igual. Donde sí se necesita el valor del
-- parámetro (las listas VALUES) se calificó explícito como
-- `solicitar_vinculacion.id_medico`, que sigue funcionando igual con este
-- pragma activo.
declare
  v_id_paciente text := current_setting('request.jwt.claims', true)::json->>'sub';
begin
  if v_id_paciente is null then
    perform app.lanzar_error(401, 'NO_AUTORIZADO');
  end if;

  insert into app.control_accesos_medico (id_paciente, id_medico, nivel_acceso)
  values (v_id_paciente, solicitar_vinculacion.id_medico, 'lectura')
  on conflict (id_paciente, id_medico) do nothing;

  insert into app.conversaciones (id_paciente, id_medico)
  values (v_id_paciente, solicitar_vinculacion.id_medico)
  on conflict (id_paciente, id_medico) do nothing;

  return api.medico_vinculado(v_id_paciente);
end;
$$;

grant execute on function api.solicitar_vinculacion(text) to paciente;
-- La función es SECURITY INVOKER pero necesita insertar en tablas que el rol
-- paciente solo tiene en SELECT hasta ahora.
grant insert on app.control_accesos_medico, app.conversaciones to paciente;
