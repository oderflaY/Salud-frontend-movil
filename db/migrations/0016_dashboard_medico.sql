-- Dashboard web del medico (Salud-Frontend, React). Decisiones del dueno del
-- producto (2026-09-14):
--
--  - Entra el MEDICO con su misma cuenta de la app y ve SOLO a sus pacientes
--    con vinculo vigente. Mismas reglas que la app; no hay superusuario.
--  - "Resetear cuenta" da una contrasena temporal que el dashboard muestra una
--    sola vez. Cierra las sesiones abiertas del paciente y la app le obliga a
--    elegir una contrasena nueva al entrar. No toca el expediente.
--
-- El dashboard lo sirve Axum (/api/v1/*), no PostgREST: el reset necesita el
-- pepper de Argon2. Por eso las funciones de lectura de aqui reciben el id del
-- medico ya autenticado por Axum, y ningun rol de PostgREST puede ejecutarlas.

-- ---------------------------------------------------------------------------
-- 1. Sesiones revocables
-- ---------------------------------------------------------------------------
-- Un JWT vale hasta que vence (JWT_EXPIRACION_HORAS). Para que "cerrar
-- sesiones" sea verdad, cada cuenta guarda desde cuando valen sus tokens: uno
-- emitido antes (claim `iat`) se rechaza. Lo comprueban PostgREST (en
-- db-pre-request) y Axum, con la misma funcion.
--
-- De paso, una cuenta bloqueada o dada de baja deja de funcionar al instante,
-- no cuando vence su token.

alter table app.pacientes
  add column sesiones_validas_desde timestamptz,
  add column debe_cambiar_contrasena boolean not null default false,
  add column contrasena_temporal_expira_en timestamptz;

alter table app.medicos add column sesiones_validas_desde timestamptz;

insert into app.configuracion (clave, valor) values ('horas_validez_contrasena_temporal', '72')
on conflict (clave) do nothing;

-- `iat` tiene resolucion de segundos; la marca se trunca igual para que el
-- token emitido justo despues de un reset (en el mismo segundo) sea valido.
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
    else 'VIGENTE'
  end;
$$;

-- db-pre-request de PostgREST (docker-compose: PGRST_DB_PRE_REQUEST). Corre
-- antes de cada peticion, ya con el rol y los claims del JWT puestos.
create or replace function app.verificar_sesion()
returns void
language plpgsql
as $$
declare
  v_claims jsonb := nullif(current_setting('request.jwt.claims', true), '')::jsonb;
  v_estado text;
begin
  if v_claims is null or coalesce(v_claims->>'role', '') not in ('paciente', 'medico') then
    return;
  end if;

  v_estado := app.estado_sesion(v_claims->>'role', v_claims->>'sub', coalesce((v_claims->>'iat')::bigint, 0));

  if v_estado = 'VIGENTE' then
    return;
  elsif v_estado = 'CAMBIO_CONTRASENA_REQUERIDO' then
    -- 403 y no 401: la sesion es buena, solo falta elegir contrasena. La app
    -- lo usa para mandar a esa pantalla en vez de cerrar la sesion.
    perform app.lanzar_error(403, v_estado);
  else
    perform app.lanzar_error(401, v_estado);
  end if;
end;
$$;

-- El pre-request tambien corre para `anon`; sin USAGE en el esquema, cada
-- peticion anonima fallaria por permisos antes de llegar a su propio error.
grant usage on schema app to anon;
revoke all on function app.estado_sesion(text, text, bigint) from public;
grant execute on function app.estado_sesion(text, text, bigint) to anon, paciente, medico;
revoke all on function app.verificar_sesion() from public;
grant execute on function app.verificar_sesion() to anon, paciente, medico;

-- ---------------------------------------------------------------------------
-- 2. Auditoria de resets de cuenta
-- ---------------------------------------------------------------------------
-- Sin politicas RLS a proposito: solo la lee y escribe Axum (rol dueno). La
-- contrasena temporal NUNCA se guarda aqui, ni en claro ni con hash.
create table app.auditoria_reset_cuenta (
  id_auditoria text primary key default app.generar_id('audreset'),
  id_paciente  text not null references app.pacientes(id_paciente) on delete cascade,
  id_medico    text not null references app.medicos(id_medico),
  motivo       text,
  ip_origen    text,
  creado_en    timestamptz not null default now()
);
create index idx_auditoria_reset_paciente on app.auditoria_reset_cuenta(id_paciente, creado_en desc);
create index idx_auditoria_reset_medico on app.auditoria_reset_cuenta(id_medico, creado_en desc);
alter table app.auditoria_reset_cuenta enable row level security;

-- ---------------------------------------------------------------------------
-- 3. Alertas predictivas
-- ---------------------------------------------------------------------------
-- Una bandeja por medico: que un medico marque una alerta como atendida no
-- se la oculta a otro medico del mismo paciente.
--
-- Se generan a partir de senales que el sistema ya tiene (diario, adherencia,
-- inventario) cada vez que el dashboard las pide; `clave_origen` evita
-- duplicarlas.
create table app.alertas (
  id_alerta      text primary key default app.generar_id('alerta'),
  id_medico      text not null references app.medicos(id_medico) on delete cascade,
  id_paciente    text not null references app.pacientes(id_paciente) on delete cascade,
  tipo           text not null check (tipo in ('SINTOMA_DE_ALARMA', 'RIESGO_ADHERENCIA', 'INVENTARIO_BAJO')),
  prioridad      text not null check (prioridad in ('ALTA', 'MEDIA', 'BAJA')),
  titulo         text not null,
  detalle        text not null,
  clave_origen   text not null,
  estado         text not null default 'NUEVA'
                   check (estado in ('NUEVA', 'EN_REVISION', 'ATENDIDA', 'DESCARTADA')),
  nota           text,
  -- Cuando ocurrio la senal (la entrada del diario, por ejemplo), no cuando
  -- el dashboard la descubrio.
  creado_en      timestamptz not null default now(),
  actualizado_en timestamptz not null default now(),
  unique (id_medico, clave_origen)
);
create index idx_alertas_medico_estado on app.alertas(id_medico, estado, creado_en desc);
alter table app.alertas enable row level security;
create trigger trg_alertas_actualizado_en
  before update on app.alertas
  for each row execute function app.set_actualizado_en();

create or replace function app.vinculo_vigente(p_id_medico text, p_id_paciente text)
returns boolean
language sql
stable
as $$
  select exists (
    select 1 from app.control_accesos_medico ca
    where ca.id_medico = p_id_medico
      and ca.id_paciente = p_id_paciente
      and (ca.fecha_expiracion is null or ca.fecha_expiracion >= current_date)
  );
$$;

-- Mismo universo que app.riesgo_paciente (0011): tomas de los ultimos 7 dias
-- que ya vencieron. Asi el porcentaje del dashboard y el nivel de riesgo de
-- la app nunca se contradicen.
create or replace function app.adherencia_7_dias(p_id_paciente text)
returns table (programadas integer, cumplidas integer)
language sql
stable
as $$
  select count(ra.id_toma)::int,
         (count(ra.id_toma) filter (where ra.estado in ('tomado', 'tomado_tarde')))::int
  from app.tratamientos t
  join app.registro_adherencia ra on ra.id_tratamiento = t.id_tratamiento
  where t.id_paciente = p_id_paciente
    and ra.fecha_hora_programada >= now() - interval '7 days'
    and ra.fecha_hora_programada
        <= now() - (app.config_entero('minutos_gracia_toma') || ' minutes')::interval;
$$;

-- Dosis que el paciente toma al dia segun su pauta; null si no se sabe.
create or replace function app.dosis_por_dia(p_horarios text[], p_frecuencia_horas integer)
returns numeric
language sql
immutable
as $$
  select coalesce(
    nullif(cardinality(p_horarios), 0)::numeric,
    case when p_frecuencia_horas > 0 then 24.0 / p_frecuencia_horas end
  );
$$;

create or replace function app.sincronizar_alertas(p_id_medico text)
returns integer
language plpgsql
as $$
declare
  -- Una alerta de riesgo o de inventario atendida puede volver a salir la
  -- semana siguiente si el problema sigue; nunca dos abiertas a la vez.
  v_semana text := to_char(now() at time zone app.config_texto('zona_horaria_default'), 'IYYY-"S"IW');
  v_nuevas integer := 0;
  v_filas integer;
begin
  -- a) Sintomas del diario: una alerta por entrada ROJA (alta) o AMBAR
  --    (media) de los ultimos 7 dias. La severidad es la que calculo la app
  --    al escribirla (0010): aqui no se recalcula.
  insert into app.alertas (id_medico, id_paciente, tipo, prioridad, titulo, detalle, clave_origen, creado_en)
  select p_id_medico, e.id_paciente, 'SINTOMA_DE_ALARMA',
         case e.severidad when 'ROJO' then 'ALTA' else 'MEDIA' end,
         case e.severidad when 'ROJO' then 'Síntoma de alarma en el diario' else 'Síntoma a vigilar en el diario' end,
         left(e.texto, 280)
           || case when cardinality(e.terminos_detectados) > 0
                   then ' (' || array_to_string(e.terminos_detectados, ', ') || ')' else '' end,
         'diario:' || e.id_entrada,
         e.instante
  from app.control_accesos_medico ca
  join app.entradas_diario e on e.id_paciente = ca.id_paciente
  where ca.id_medico = p_id_medico
    and (ca.fecha_expiracion is null or ca.fecha_expiracion >= current_date)
    and e.severidad in ('ROJO', 'AMBAR')
    and e.instante >= now() - interval '7 days'
  on conflict (id_medico, clave_origen) do nothing;
  get diagnostics v_filas = row_count;
  v_nuevas := v_nuevas + v_filas;

  -- b) Riesgo de abandono: el mismo nivel que ve la app en la cartera.
  --    Si ya hay una abierta de prioridad media y el paciente paso a ALTO, se
  --    escala esa misma alerta en vez de abrir otra.
  update app.alertas al
     set prioridad = 'ALTA',
         titulo = 'Riesgo alto de abandono del tratamiento',
         detalle = (
           select format('Tomó %s de %s dosis programadas en los últimos 7 días (%s %%).',
                         a.cumplidas, a.programadas, round(100.0 * a.cumplidas / a.programadas))
           from app.adherencia_7_dias(al.id_paciente) a)
   where al.id_medico = p_id_medico
     and al.tipo = 'RIESGO_ADHERENCIA'
     and al.estado in ('NUEVA', 'EN_REVISION')
     and al.prioridad = 'MEDIA'
     and app.riesgo_paciente(al.id_paciente) = 'ALTO'
     and exists (select 1 from app.adherencia_7_dias(al.id_paciente) a where a.programadas > 0);

  insert into app.alertas (id_medico, id_paciente, tipo, prioridad, titulo, detalle, clave_origen)
  select p_id_medico, ca.id_paciente, 'RIESGO_ADHERENCIA',
         case r.riesgo when 'ALTO' then 'ALTA' else 'MEDIA' end,
         case r.riesgo when 'ALTO' then 'Riesgo alto de abandono del tratamiento' else 'Adherencia en descenso' end,
         format('Tomó %s de %s dosis programadas en los últimos 7 días (%s %%).',
                a.cumplidas, a.programadas, round(100.0 * a.cumplidas / a.programadas)),
         'riesgo:' || ca.id_paciente || ':' || v_semana
  from app.control_accesos_medico ca
  cross join lateral (select app.riesgo_paciente(ca.id_paciente) as riesgo) r
  cross join lateral app.adherencia_7_dias(ca.id_paciente) a
  where ca.id_medico = p_id_medico
    and (ca.fecha_expiracion is null or ca.fecha_expiracion >= current_date)
    and r.riesgo in ('ALTO', 'MEDIO')
    and a.programadas > 0
    and not exists (
      select 1 from app.alertas al
      where al.id_medico = p_id_medico and al.id_paciente = ca.id_paciente
        and al.tipo = 'RIESGO_ADHERENCIA' and al.estado in ('NUEVA', 'EN_REVISION')
    )
  on conflict (id_medico, clave_origen) do nothing;
  get diagnostics v_filas = row_count;
  v_nuevas := v_nuevas + v_filas;

  -- c) Medicamento por terminarse, con cuantos dias le alcanza a su pauta.
  insert into app.alertas (id_medico, id_paciente, tipo, prioridad, titulo, detalle, clave_origen)
  select p_id_medico, t.id_paciente, 'INVENTARIO_BAJO',
         case when t.cantidad_restante <= 0 or d.dias < 2 then 'ALTA' else 'MEDIA' end,
         case when t.cantidad_restante <= 0 then 'Se terminó ' || t.medicamento
              else 'Se está terminando ' || t.medicamento end,
         case
           when t.cantidad_restante <= 0 then 'Ya no le quedan dosis.'
           when t.cantidad_restante = 1 then 'Le queda 1 dosis.'
           else format('Le quedan %s dosis.', t.cantidad_restante)
         end
         || case
              when t.cantidad_restante <= 0 or d.dias is null then ''
              when d.dias < 1 then ' No le alcanza para un día completo.'
              when d.dias = 1 then ' Le alcanza para 1 día.'
              else format(' Le alcanza para unos %s días.', d.dias)
            end,
         'inventario:' || t.id_tratamiento || ':' || v_semana
  from app.control_accesos_medico ca
  join app.tratamientos t on t.id_paciente = ca.id_paciente
  cross join lateral (
    select floor(t.cantidad_restante / app.dosis_por_dia(t.horarios_sugeridos, t.frecuencia_horas))::int as dias
  ) d
  where ca.id_medico = p_id_medico
    and (ca.fecha_expiracion is null or ca.fecha_expiracion >= current_date)
    and t.cantidad_restante is not null
    and t.cantidad_restante <= t.umbral_alerta
    and (t.fecha_fin is null or t.fecha_fin >= current_date)
    and not exists (
      select 1 from app.alertas al
      where al.id_medico = p_id_medico
        and al.clave_origen like 'inventario:' || t.id_tratamiento || ':%'
        and al.estado in ('NUEVA', 'EN_REVISION')
    )
  on conflict (id_medico, clave_origen) do nothing;
  get diagnostics v_filas = row_count;
  v_nuevas := v_nuevas + v_filas;

  return v_nuevas;
end;
$$;

-- ---------------------------------------------------------------------------
-- 4. Lecturas del dashboard (JSON armado aqui, como cita_a_jsonb)
-- ---------------------------------------------------------------------------

-- "paciente1@salud.local" -> "pa•••••1@salud.local". Basta para que el medico
-- confirme con el paciente que es su cuenta antes de resetearla.
create or replace function app.enmascarar_correo(p_correo text)
returns text
language sql
immutable
as $$
  select case
    when position('@' in p_correo) > 4 then
      left(p_correo, 2) || repeat('•', position('@' in p_correo) - 4)
        || substr(p_correo, position('@' in p_correo) - 1)
    else '•••' || substr(p_correo, position('@' in p_correo))
  end;
$$;

create or replace function app.alerta_a_jsonb(p_id_alerta text)
returns jsonb
language sql
stable
as $$
  select jsonb_build_object(
    'idAlerta', al.id_alerta,
    'idPaciente', al.id_paciente,
    'nombrePaciente', app.nombre_completo_paciente(dp.nombre, dp.apellidos),
    'tipo', al.tipo,
    'prioridad', al.prioridad,
    'titulo', al.titulo,
    'detalle', al.detalle,
    'estado', al.estado,
    'nota', al.nota,
    'creadaEn', app.iso_utc(al.creado_en),
    'actualizadaEn', app.iso_utc(al.actualizado_en)
  )
  from app.alertas al
  left join app.datos_personales_paciente dp on dp.id_paciente = al.id_paciente
  where al.id_alerta = p_id_alerta;
$$;

create or replace function app.dashboard_alertas(p_id_medico text, p_estado text, p_prioridad text)
returns jsonb
language sql
stable
as $$
  select coalesce(jsonb_agg(app.alerta_a_jsonb(x.id_alerta) order by x.abierta desc, x.orden, x.creado_en desc), '[]'::jsonb)
  from (
    select al.id_alerta, al.creado_en,
           al.estado in ('NUEVA', 'EN_REVISION') as abierta,
           case al.prioridad when 'ALTA' then 0 when 'MEDIA' then 1 else 2 end as orden
    from app.alertas al
    where al.id_medico = p_id_medico
      and app.vinculo_vigente(p_id_medico, al.id_paciente)
      and (p_estado is null or al.estado = p_estado)
      and (p_prioridad is null or al.prioridad = p_prioridad)
    order by abierta desc, orden, al.creado_en desc
    limit 500
  ) x;
$$;

create or replace function app.dashboard_kpis(p_id_medico text)
returns jsonb
language sql
stable
as $$
  with vinculos as (
    select ca.id_paciente
    from app.control_accesos_medico ca
    join app.pacientes p on p.id_paciente = ca.id_paciente and p.estado_cuenta <> 'eliminada'
    where ca.id_medico = p_id_medico
      and (ca.fecha_expiracion is null or ca.fecha_expiracion >= current_date)
  ),
  riesgos as (
    select v.id_paciente, app.riesgo_paciente(v.id_paciente) as riesgo, a.programadas, a.cumplidas
    from vinculos v
    cross join lateral app.adherencia_7_dias(v.id_paciente) a
  ),
  hoy as (
    select (now() at time zone app.config_texto('zona_horaria_default'))::date as fecha
  ),
  alertas_abiertas as (
    select al.prioridad
    from app.alertas al
    join vinculos v on v.id_paciente = al.id_paciente
    where al.id_medico = p_id_medico and al.estado in ('NUEVA', 'EN_REVISION')
  )
  select jsonb_build_object(
    'totalPacientes', (select count(*) from vinculos),
    'pacientesRiesgoAlto', (select count(*) from riesgos where riesgo = 'ALTO'),
    'pacientesRiesgoMedio', (select count(*) from riesgos where riesgo = 'MEDIO'),
    'pacientesRiesgoBajo', (select count(*) from riesgos where riesgo = 'BAJO'),
    -- Null (no 0) si ningun paciente tiene tomas vencidas: "sin datos" no es
    -- "nadie se tomo nada".
    'adherenciaPromedio', (select round(100.0 * sum(cumplidas) / nullif(sum(programadas), 0), 1) from riesgos),
    'alertasAbiertas', (select count(*) from alertas_abiertas),
    'alertasAltaPrioridad', (select count(*) from alertas_abiertas where prioridad = 'ALTA'),
    'citasHoy', (
      select count(*) from app.citas c, hoy
      where c.id_medico = p_id_medico and c.fecha = hoy.fecha
        and c.estado in ('PENDIENTE', 'CONFIRMADA', 'EN_CURSO')),
    'citasProximos7Dias', (
      select count(*) from app.citas c, hoy
      where c.id_medico = p_id_medico and c.fecha between hoy.fecha and hoy.fecha + 6
        and c.estado in ('PENDIENTE', 'CONFIRMADA', 'EN_CURSO')),
    'propuestasSinResponder', (
      select count(*) from app.citas c, hoy
      where c.id_medico = p_id_medico and c.fecha >= hoy.fecha and c.estado = 'PROPUESTA_MEDICO'),
    'mensajesSinLeer', (
      select count(*) from app.mensajes m
      join app.conversaciones c on c.id_conversacion = m.id_conversacion
      join vinculos v on v.id_paciente = c.id_paciente
      where c.id_medico = p_id_medico and m.autor = 'PACIENTE' and m.instante > c.leido_por_medico_hasta),
    'entradasDiarioRojas7Dias', (
      select count(*) from app.entradas_diario e
      join vinculos v on v.id_paciente = e.id_paciente
      where e.severidad = 'ROJO' and e.instante >= now() - interval '7 days'),
    'tratamientosPorAgotarse', (
      select count(*) from app.tratamientos t
      join vinculos v on v.id_paciente = t.id_paciente
      where t.cantidad_restante is not null and t.cantidad_restante <= t.umbral_alerta
        and (t.fecha_fin is null or t.fecha_fin >= current_date)),
    'resetsUltimos30Dias', (
      select count(*) from app.auditoria_reset_cuenta ar
      where ar.id_medico = p_id_medico and ar.creado_en >= now() - interval '30 days'),
    'generadoEn', app.iso_utc(now())
  );
$$;

create or replace function app.dashboard_pacientes(p_id_medico text)
returns jsonb
language sql
stable
as $$
  select coalesce(jsonb_agg(x.fila order by x.orden, x.nombre), '[]'::jsonb)
  from (
    select
      case r.riesgo when 'ALTO' then 0 when 'MEDIO' then 1 else 2 end as orden,
      app.nombre_completo_paciente(dp.nombre, dp.apellidos) as nombre,
      jsonb_build_object(
        'idPaciente', p.id_paciente,
        'nombre', dp.nombre,
        'apellidos', dp.apellidos,
        'nombreCompleto', app.nombre_completo_paciente(dp.nombre, dp.apellidos),
        'correoEnmascarado', app.enmascarar_correo(p.correo::text),
        'fechaNacimiento', dp.fecha_nacimiento,
        'edad', extract(year from age(dp.fecha_nacimiento))::int,
        'genero', dp.genero,
        'telefono', dp.telefono,
        'estadoCuenta', p.estado_cuenta,
        'registroCompleto', not p.requiere_onboarding,
        'requiereCambioContrasena', p.debe_cambiar_contrasena,
        'pacienteDesde', app.iso_utc(p.creado_en),
        'vinculo', jsonb_build_object(
          'nivelAcceso', ca.nivel_acceso,
          'desde', app.iso_utc(ca.creado_en),
          'expira', ca.fecha_expiracion
        ),
        'riesgo', r.riesgo,
        'adherencia', jsonb_build_object(
          'porcentaje7Dias', round(100.0 * a.cumplidas / nullif(a.programadas, 0), 1),
          'dosisProgramadas', a.programadas,
          'dosisCumplidas', a.cumplidas,
          'porDia', (
            select jsonb_agg(jsonb_build_object(
                     'fecha', d.dia::date,
                     'programadas', coalesce(t.programadas, 0),
                     'cumplidas', coalesce(t.cumplidas, 0)
                   ) order by d.dia)
            from generate_series(
                   ((now() at time zone app.config_texto('zona_horaria_default'))::date - 6)::timestamp,
                   ((now() at time zone app.config_texto('zona_horaria_default'))::date)::timestamp,
                   interval '1 day') d(dia)
            left join lateral (
              select count(*)::int as programadas,
                     (count(*) filter (where ra.estado in ('tomado', 'tomado_tarde')))::int as cumplidas
              from app.registro_adherencia ra
              join app.tratamientos tr on tr.id_tratamiento = ra.id_tratamiento
              where tr.id_paciente = p.id_paciente
                and (ra.fecha_hora_programada at time zone app.config_texto('zona_horaria_default'))::date = d.dia::date
                and ra.fecha_hora_programada
                    <= now() - (app.config_entero('minutos_gracia_toma') || ' minutes')::interval
            ) t on true
          )
        ),
        'metricasVitales', jsonb_build_object(
          'pesoKg', m.peso_kg,
          'alturaCm', m.altura_cm,
          'imc', m.imc,
          'presionArterial', m.ultima_presion_arterial,
          'fechaToma', app.iso_utc(m.fecha_toma_metricas)
        ),
        'tipoSangre', per.tipo_sangre,
        'alergias', coalesce((
          select jsonb_agg(jsonb_build_object('alergeno', al.alergeno, 'severidad', al.severidad, 'reaccion', al.reaccion))
          from app.alergias al where al.id_paciente = p.id_paciente), '[]'::jsonb),
        'condicionesCriticas', to_jsonb(coalesce(per.condiciones_criticas, '{}')),
        'antecedentesHeredofamiliares', to_jsonb(coalesce(hc.antecedentes_heredofamiliares, '{}')),
        'cirugias', coalesce((
          select jsonb_agg(jsonb_build_object('procedimiento', ci.procedimiento, 'fecha', ci.fecha, 'notas', ci.notas) order by ci.fecha desc nulls last)
          from app.cirugias ci where ci.id_paciente = p.id_paciente), '[]'::jsonb),
        'tratamientosActivos', coalesce((
          select jsonb_agg(jsonb_build_object(
                   'idTratamiento', tr.id_tratamiento,
                   'medicamento', tr.medicamento,
                   'dosis', tr.dosis,
                   'horarios', to_jsonb(tr.horarios_sugeridos),
                   'frecuenciaHoras', tr.frecuencia_horas,
                   'viaAdministracion', tr.via_administracion,
                   'fechaInicio', tr.fecha_inicio,
                   'fechaFin', tr.fecha_fin,
                   'cantidadRestante', tr.cantidad_restante,
                   'umbralAlerta', tr.umbral_alerta,
                   'porAgotarse', coalesce(tr.cantidad_restante <= tr.umbral_alerta, false)
                 ) order by tr.medicamento)
          from app.tratamientos tr
          where tr.id_paciente = p.id_paciente
            and (tr.fecha_fin is null or tr.fecha_fin >= current_date)), '[]'::jsonb),
        'ultimaEntradaDiario', (
          select jsonb_build_object('instante', app.iso_utc(e.instante), 'severidad', e.severidad, 'texto', e.texto)
          from app.entradas_diario e where e.id_paciente = p.id_paciente
          order by e.instante desc limit 1),
        'proximaCita', (
          select jsonb_build_object(
                   'idCita', c.id_cita, 'folio', c.folio, 'fecha', c.fecha,
                   'horaInicio', to_char(c.hora_inicio, 'HH24:MI'), 'horaFin', to_char(c.hora_fin, 'HH24:MI'),
                   'estado', c.estado)
          from app.citas c
          where c.id_paciente = p.id_paciente and c.id_medico = p_id_medico
            and c.estado in ('PENDIENTE', 'CONFIRMADA', 'EN_CURSO', 'PROPUESTA_MEDICO')
            and c.fecha >= (now() at time zone app.config_texto('zona_horaria_default'))::date
          order by c.fecha, c.hora_inicio limit 1),
        'idConversacion', conv.id_conversacion,
        'mensajesSinLeer', (
          select count(*) from app.mensajes msg
          where msg.id_conversacion = conv.id_conversacion
            and msg.autor = 'PACIENTE' and msg.instante > conv.leido_por_medico_hasta),
        'alertasAbiertas', (
          select count(*) from app.alertas al
          where al.id_medico = p_id_medico and al.id_paciente = p.id_paciente
            and al.estado in ('NUEVA', 'EN_REVISION')),
        'tarjetaRfidActiva', exists (
          select 1 from app.dispositivos_rfid d where d.id_paciente = p.id_paciente and d.estado = 'activa'),
        -- Cuantas veces se consulto su perfil de emergencia con la tarjeta.
        'consultasTarjeta30Dias', (
          select count(*) from app.auditoria_rfid ar
          join app.dispositivos_rfid d on d.id_tarjeta_rfid = ar.id_tarjeta_rfid
          where d.id_paciente = p.id_paciente and ar.resultado = 'encontrada'
            and ar.creado_en >= now() - interval '30 days'),
        'ultimoReset', (
          select app.iso_utc(max(ar.creado_en)) from app.auditoria_reset_cuenta ar
          where ar.id_paciente = p.id_paciente)
      ) as fila
    from app.control_accesos_medico ca
    join app.pacientes p on p.id_paciente = ca.id_paciente and p.estado_cuenta <> 'eliminada'
    left join app.datos_personales_paciente dp on dp.id_paciente = p.id_paciente
    left join app.perfil_emergencia_reducido per on per.id_paciente = p.id_paciente
    left join app.metricas_vitales_actuales m on m.id_paciente = p.id_paciente
    left join app.historial_clinico hc on hc.id_paciente = p.id_paciente
    left join app.conversaciones conv on conv.id_paciente = ca.id_paciente and conv.id_medico = ca.id_medico
    cross join lateral (select app.riesgo_paciente(p.id_paciente) as riesgo) r
    cross join lateral app.adherencia_7_dias(p.id_paciente) a
    where ca.id_medico = p_id_medico
      and (ca.fecha_expiracion is null or ca.fecha_expiracion >= current_date)
  ) x;
$$;

-- Resets hechos por este medico o sobre sus pacientes: si otro medico reseteo
-- la cuenta de un paciente comun, este tambien debe poder verlo. La IP solo
-- se muestra en los propios.
create or replace function app.dashboard_auditoria(p_id_medico text)
returns jsonb
language sql
stable
as $$
  select coalesce(jsonb_agg(x.fila order by x.creado_en desc), '[]'::jsonb)
  from (
    select ar.creado_en,
           jsonb_build_object(
             'idAuditoria', ar.id_auditoria,
             'accion', 'RESET_CUENTA',
             'idPaciente', ar.id_paciente,
             'nombrePaciente', app.nombre_completo_paciente(dp.nombre, dp.apellidos),
             'idMedico', ar.id_medico,
             'nombreMedico', app.nombre_completo_medico(m.tratamiento, m.nombre, m.apellidos),
             'realizadoPorMi', ar.id_medico = p_id_medico,
             'motivo', ar.motivo,
             'ipOrigen', case when ar.id_medico = p_id_medico then ar.ip_origen end,
             'fecha', app.iso_utc(ar.creado_en)
           ) as fila
    from app.auditoria_reset_cuenta ar
    join app.medicos m on m.id_medico = ar.id_medico
    left join app.datos_personales_paciente dp on dp.id_paciente = ar.id_paciente
    where ar.id_medico = p_id_medico or app.vinculo_vigente(p_id_medico, ar.id_paciente)
    order by ar.creado_en desc
    limit 500
  ) x;
$$;

-- Nada de esto es para los roles de PostgREST: reciben el id del medico por
-- parametro y confian en que quien llama ya lo autentico (Axum).
revoke all on function app.vinculo_vigente(text, text) from public;
revoke all on function app.adherencia_7_dias(text) from public;
revoke all on function app.sincronizar_alertas(text) from public;
revoke all on function app.alerta_a_jsonb(text) from public;
revoke all on function app.dashboard_alertas(text, text, text) from public;
revoke all on function app.dashboard_kpis(text) from public;
revoke all on function app.dashboard_pacientes(text) from public;
revoke all on function app.dashboard_auditoria(text) from public;

notify pgrst, 'reload schema';
