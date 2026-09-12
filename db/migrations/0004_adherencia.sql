-- Módulo 4: Adherencia (tomas de medicación). Contrato: AdherenciaRepositorio.
--
-- Decisión de diseño (no está en el documento, hace falta para poder
-- implementarlo): las filas de app.registro_adherencia para un tratamiento y
-- un día dado se materializan de forma perezosa (la primera vez que se
-- consulta ese día para ese paciente), no por un job nocturno — así no hace
-- falta un scheduler aparte y el resultado es idéntico sin importar cuándo
-- se consulte. `app.materializar_tomas_dia` es idempotente (ON CONFLICT DO
-- NOTHING sobre una unique constraint), así que consultarla más de una vez
-- para el mismo día no duplica nada.

alter table app.registro_adherencia
  add constraint ux_registro_adherencia_tratamiento_horario
  unique (id_tratamiento, fecha_hora_programada);

insert into app.configuracion (clave, valor) values
  ('zona_horaria_default', 'America/Mexico_City'),
  ('minutos_gracia_toma', '60')
on conflict (clave) do nothing;

-- app.config_texto/config_entero se llaman desde funciones SECURITY INVOKER
-- (paciente/medico), así que ese rol necesita poder leer la tabla de
-- configuración — son valores operativos, no datos de un paciente.
grant select on app.configuracion to paciente, medico;

create or replace function app.materializar_tomas_dia(p_id_paciente text, p_fecha date)
returns void
language plpgsql
security definer
set search_path = app, pg_temp
as $$
declare
  v_zona text := app.config_texto('zona_horaria_default');
  v_tratamiento record;
  v_horario text;
begin
  for v_tratamiento in
    select * from app.tratamientos
    where id_paciente = p_id_paciente
      and (fecha_inicio is null or fecha_inicio <= p_fecha)
      and (fecha_fin is null or fecha_fin >= p_fecha)
  loop
    foreach v_horario in array v_tratamiento.horarios_sugeridos
    loop
      insert into app.registro_adherencia (id_tratamiento, fecha_hora_programada, estado)
      values (
        v_tratamiento.id_tratamiento,
        (p_fecha::text || ' ' || v_horario)::timestamp at time zone v_zona,
        'pendiente'
      )
      on conflict (id_tratamiento, fecha_hora_programada) do nothing;
    end loop;
  end loop;
end;
$$;

revoke all on function app.materializar_tomas_dia(text, date) from public;
grant execute on function app.materializar_tomas_dia(text, date) to paciente, medico;

-- ---------------------------------------------------------------------------
-- obtenerTomasDelDia
-- ---------------------------------------------------------------------------
create or replace function api.tomas_del_dia(id_paciente text, fecha date)
returns table("idToma" text, "idTratamiento" text, "medicamento" text, "dosis" text, "horaProgramada" text, "estado" text)
language plpgsql
security invoker
as $$
declare
  v_zona text := app.config_texto('zona_horaria_default');
begin
  if not (app.es_dueno_paciente(tomas_del_dia.id_paciente) or app.tiene_acceso_medico(tomas_del_dia.id_paciente)) then
    perform app.lanzar_error(401, 'NO_AUTORIZADO');
  end if;

  perform app.materializar_tomas_dia(tomas_del_dia.id_paciente, tomas_del_dia.fecha);

  return query
    select ra.id_toma, ra.id_tratamiento, t.medicamento, t.dosis,
           to_char(ra.fecha_hora_programada at time zone v_zona, 'HH24:MI'),
           ra.estado
    from app.registro_adherencia ra
    join app.tratamientos t on t.id_tratamiento = ra.id_tratamiento
    where t.id_paciente = tomas_del_dia.id_paciente
      and (ra.fecha_hora_programada at time zone v_zona)::date = tomas_del_dia.fecha
    order by ra.fecha_hora_programada;
end;
$$;

grant execute on function api.tomas_del_dia(text, date) to paciente, medico;

-- ---------------------------------------------------------------------------
-- registrarToma — el backend decide tomado vs tomado_tarde. Es una acción
-- exclusiva del paciente dueño (un médico no marca tomas en su nombre).
-- ---------------------------------------------------------------------------
create or replace function api.registrar_toma(id_toma text, estado text, instante timestamptz)
returns void
language plpgsql
security invoker
as $$
declare
  v_id_paciente text;
  v_programada timestamptz;
  v_gracia_minutos int := app.config_entero('minutos_gracia_toma');
  v_estado_final text;
begin
  select t.id_paciente, ra.fecha_hora_programada
    into v_id_paciente, v_programada
  from app.registro_adherencia ra
  join app.tratamientos t on t.id_tratamiento = ra.id_tratamiento
  where ra.id_toma = registrar_toma.id_toma;

  if v_id_paciente is null then
    perform app.lanzar_error(404, 'TOMA_DESCONOCIDA');
  end if;

  if not app.es_dueno_paciente(v_id_paciente) then
    perform app.lanzar_error(401, 'NO_AUTORIZADO');
  end if;

  if registrar_toma.estado = 'omitido' then
    v_estado_final := 'omitido';
  elsif registrar_toma.estado = 'tomado' then
    if registrar_toma.instante <= v_programada + make_interval(mins => v_gracia_minutos) then
      v_estado_final := 'tomado';
    else
      v_estado_final := 'tomado_tarde';
    end if;
  else
    perform app.lanzar_error(400, 'SOLICITUD_INVALIDA');
  end if;

  update app.registro_adherencia ra
     set estado = v_estado_final, fecha_hora_real = registrar_toma.instante
   where ra.id_toma = registrar_toma.id_toma;
end;
$$;

grant execute on function api.registrar_toma(text, text, timestamptz) to paciente;
-- El insert de materializar_tomas_dia corre SECURITY DEFINER (no necesita
-- grant); el UPDATE de registrar_toma sí corre como el rol invocador.
grant update on app.registro_adherencia to paciente;

-- ---------------------------------------------------------------------------
-- obtenerSemana — siempre 7 días, generate_series como tabla base para que
-- un día sin tratamiento siga apareciendo con ceros.
-- ---------------------------------------------------------------------------
create or replace function api.adherencia_semana(id_paciente text, fecha_final date)
returns table("fecha" date, "tomasProgramadas" int, "tomasCumplidas" int, "enCurso" boolean)
language plpgsql
security invoker
as $$
declare
  v_zona text := app.config_texto('zona_horaria_default');
  v_dia date;
begin
  if not (app.es_dueno_paciente(adherencia_semana.id_paciente) or app.tiene_acceso_medico(adherencia_semana.id_paciente)) then
    perform app.lanzar_error(401, 'NO_AUTORIZADO');
  end if;

  for v_dia in select generate_series(adherencia_semana.fecha_final - 6, adherencia_semana.fecha_final, interval '1 day')::date
  loop
    perform app.materializar_tomas_dia(adherencia_semana.id_paciente, v_dia);
  end loop;

  return query
    select
      dias.dia::date as "fecha",
      count(ra.id_toma)::int as "tomasProgramadas",
      count(ra.id_toma) filter (where ra.estado in ('tomado', 'tomado_tarde'))::int as "tomasCumplidas",
      (dias.dia::date = (now() at time zone v_zona)::date) as "enCurso"
    -- generate_series sobre `date` con paso `interval` devuelve `timestamp`,
    -- no `date` — de ahí el ::date explícito arriba.
    from generate_series(adherencia_semana.fecha_final - 6, adherencia_semana.fecha_final, interval '1 day') as dias(dia)
    left join app.tratamientos t on t.id_paciente = adherencia_semana.id_paciente
    left join app.registro_adherencia ra
      on ra.id_tratamiento = t.id_tratamiento
     and (ra.fecha_hora_programada at time zone v_zona)::date = dias.dia::date
    group by dias.dia
    order by dias.dia;
end;
$$;

grant execute on function api.adherencia_semana(text, date) to paciente, medico;

-- ---------------------------------------------------------------------------
-- obtenerResumenSemanal — mismos 7 días, agregados a dos conteos.
-- ---------------------------------------------------------------------------
create or replace function api.adherencia_resumen(id_paciente text, fecha_final date)
returns table("tomasProgramadas" int, "tomasCumplidas" int)
language sql
security invoker
as $$
  select
    coalesce(sum(s."tomasProgramadas"), 0)::int,
    coalesce(sum(s."tomasCumplidas"), 0)::int
  from api.adherencia_semana(id_paciente, fecha_final) as s;
$$;

grant execute on function api.adherencia_resumen(text, date) to paciente, medico;
