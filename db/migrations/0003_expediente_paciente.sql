-- Módulo 3: alta y expediente del paciente (PacienteDto completo).
-- Contratos: PacienteRepositorio (alta) + HistorialMedicoRepositorio (lectura/edición).
-- Se mantienen separados a propósito en las funciones RPC de abajo: dar de
-- alta y leer/editar el expediente completo tienen permisos distintos
-- (DM_HistorialMedico.md sección 2 — "Segmentación de Seguridad").

-- ---------------------------------------------------------------------------
-- Tablas
-- ---------------------------------------------------------------------------

create table app.datos_personales_paciente (
  id_paciente      text primary key references app.pacientes(id_paciente) on delete cascade,
  nombre           text not null,
  apellidos        text not null,
  fecha_nacimiento date,
  genero           text,
  telefono         text,
  actualizado_en   timestamptz not null default now()
);
create trigger trg_datos_personales_actualizado_en
  before update on app.datos_personales_paciente
  for each row execute function app.set_actualizado_en();

create table app.identificaciones_paciente (
  id_paciente text primary key references app.pacientes(id_paciente) on delete cascade,
  curp        text,
  nss         text,
  aseguradora text
);

create table app.dispositivos_rfid (
  id_tarjeta_rfid   text primary key default app.generar_id('rfid'),
  id_paciente       text not null references app.pacientes(id_paciente) on delete cascade,
  estado            text not null default 'activa' check (estado in ('activa', 'revocada')),
  fecha_asignacion  date not null default current_date,
  fecha_revocacion  date
);
create index idx_dispositivos_rfid_paciente on app.dispositivos_rfid(id_paciente);

create table app.contactos_emergencia (
  id_contacto text primary key default app.generar_id('contacto'),
  id_paciente text not null references app.pacientes(id_paciente) on delete cascade,
  nombre      text not null,
  relacion    text,
  telefono    text not null,
  prioridad   integer not null default 1
);
create index idx_contactos_emergencia_paciente on app.contactos_emergencia(id_paciente);

create table app.perfil_emergencia_reducido (
  id_paciente          text primary key references app.pacientes(id_paciente) on delete cascade,
  tipo_sangre          text,
  donador_organos      boolean not null default false,
  condiciones_criticas text[] not null default '{}',
  medicacion_rescate   text[] not null default '{}'
);

create table app.alergias (
  id_alergia  text primary key default app.generar_id('alergia'),
  id_paciente text not null references app.pacientes(id_paciente) on delete cascade,
  alergeno    text not null,
  severidad   text,
  reaccion    text
);
create index idx_alergias_paciente on app.alergias(id_paciente);

create table app.metricas_vitales_actuales (
  id_paciente             text primary key references app.pacientes(id_paciente) on delete cascade,
  peso_kg                 numeric(5,2),
  altura_cm               integer,
  -- El IMC lo calcula el cliente; el backend solo lo persiste tal cual, nunca lo recalcula.
  imc                     numeric(5,2),
  ultima_presion_arterial text,
  fecha_toma_metricas     timestamptz
);

create table app.historial_clinico (
  id_paciente                    text primary key references app.pacientes(id_paciente) on delete cascade,
  antecedentes_heredofamiliares  text[] not null default '{}'
);

create table app.cirugias (
  id_cirugia    text primary key default app.generar_id('cirugia'),
  id_paciente   text not null references app.pacientes(id_paciente) on delete cascade,
  procedimiento text not null,
  fecha         date,
  notas         text
);
create index idx_cirugias_paciente on app.cirugias(id_paciente);

create table app.tratamientos (
  id_tratamiento     text primary key default app.generar_id('trt'),
  id_paciente        text not null references app.pacientes(id_paciente) on delete cascade,
  medicamento        text not null,
  dosis              text,
  frecuencia_horas    integer,
  horarios_sugeridos text[] not null default '{}',
  via_administracion text,
  fecha_inicio       date,
  fecha_fin          date,
  id_medico_receta   text references app.medicos(id_medico),
  cantidad_restante  integer,
  umbral_alerta      integer not null default 5
);
create index idx_tratamientos_paciente on app.tratamientos(id_paciente);

-- Compartida con el módulo 4 (Adherencia); aquí solo se crea la tabla porque
-- PacienteDto.registroAdherencia la referencia como parte del expediente.
create table app.registro_adherencia (
  id_toma               text primary key default app.generar_id('toma'),
  id_tratamiento        text not null references app.tratamientos(id_tratamiento) on delete cascade,
  fecha_hora_programada timestamptz not null,
  fecha_hora_real       timestamptz,
  estado                text not null default 'pendiente'
                          check (estado in ('pendiente', 'tomado', 'tomado_tarde', 'omitido')),
  sintomas_asociados    text
);
create index idx_registro_adherencia_tratamiento on app.registro_adherencia(id_tratamiento);

-- Control de accesos: quién puede leer/editar el expediente completo de qué
-- paciente. Es la tabla que de verdad segrega permisos (vía RLS más abajo),
-- no la separación de endpoints por sí sola.
create table app.control_accesos_medico (
  id_paciente       text not null references app.pacientes(id_paciente) on delete cascade,
  id_medico         text not null references app.medicos(id_medico) on delete cascade,
  nivel_acceso      text not null default 'lectura' check (nivel_acceso in ('lectura', 'lectura_escritura')),
  fecha_expiracion  date,
  primary key (id_paciente, id_medico)
);

-- ---------------------------------------------------------------------------
-- RLS: el paciente ve/edita lo suyo; un médico con vínculo vigente en
-- control_accesos_medico puede leer (y, si su nivel lo permite, escribir).
-- ---------------------------------------------------------------------------

create or replace function app.es_dueno_paciente(p_id_paciente text)
returns boolean
language sql
stable
as $$
  select p_id_paciente = current_setting('request.jwt.claims', true)::json->>'sub';
$$;

create or replace function app.tiene_acceso_medico(p_id_paciente text, p_requiere_escritura boolean default false)
returns boolean
language sql
stable
as $$
  select exists (
    select 1 from app.control_accesos_medico c
    where c.id_paciente = p_id_paciente
      and c.id_medico = current_setting('request.jwt.claims', true)::json->>'sub'
      and (c.fecha_expiracion is null or c.fecha_expiracion >= current_date)
      and (not p_requiere_escritura or c.nivel_acceso = 'lectura_escritura')
  );
$$;

-- El SELECT de 0002 en app.pacientes solo cubre "el paciente ve su propia
-- fila". Sin esta política adicional, un médico con vínculo vigente jamás
-- vería la fila base de app.pacientes (y por lo tanto ninguna fila de
-- api.paciente_expediente, sin importar qué tan permisivas sean las RLS de
-- las demás tablas) — las políticas permisivas del mismo comando se
-- combinan con OR, así que esto se suma a la de 0002 sin tocarla.
create policy pacientes_visible_a_medico_vinculado on app.pacientes
  for select
  using (app.tiene_acceso_medico(id_paciente));

do $$
declare
  t text;
begin
  foreach t in array array[
    'datos_personales_paciente', 'identificaciones_paciente', 'dispositivos_rfid',
    'contactos_emergencia', 'perfil_emergencia_reducido', 'alergias',
    'metricas_vitales_actuales', 'historial_clinico', 'cirugias', 'tratamientos'
  ]
  loop
    execute format('alter table app.%I enable row level security', t);
    execute format(
      'create policy %I_lectura on app.%I for select
         using (app.es_dueno_paciente(id_paciente) or app.tiene_acceso_medico(id_paciente))',
      t, t
    );
    execute format(
      'create policy %I_escritura on app.%I for all
         using (app.es_dueno_paciente(id_paciente) or app.tiene_acceso_medico(id_paciente, true))
         with check (app.es_dueno_paciente(id_paciente) or app.tiene_acceso_medico(id_paciente, true))',
      t, t
    );
  end loop;
end
$$;

alter table app.registro_adherencia enable row level security;
create policy registro_adherencia_por_tratamiento on app.registro_adherencia
  for all
  using (
    exists (
      select 1 from app.tratamientos t
      where t.id_tratamiento = registro_adherencia.id_tratamiento
        and (app.es_dueno_paciente(t.id_paciente) or app.tiene_acceso_medico(t.id_paciente))
    )
  );

alter table app.control_accesos_medico enable row level security;
create policy control_accesos_visible_a_dueno_y_medico on app.control_accesos_medico
  for select
  using (app.es_dueno_paciente(id_paciente) or id_medico = current_setting('request.jwt.claims', true)::json->>'sub');
create policy control_accesos_editable_por_dueno on app.control_accesos_medico
  for all
  using (app.es_dueno_paciente(id_paciente))
  with check (app.es_dueno_paciente(id_paciente));

-- Formatea un instante como `YYYY-MM-DDTHH:MM:SSZ` — la convención fija del
-- documento de contratos. El `jsonb_build_object` de Postgres serializa un
-- `timestamptz` como `...+00:00`, no `...Z`; aunque casi todo parser ISO-8601
-- acepta ambos, esto lo deja idéntico a lo que el cliente espera.
create or replace function app.iso_utc(p_instante timestamptz)
returns text
language sql
immutable
as $$
  select to_char(p_instante at time zone 'utc', 'YYYY-MM-DD"T"HH24:MI:SS"Z"');
$$;

-- ---------------------------------------------------------------------------
-- Vista agregada de solo lectura: arma el PacienteDto completo tal como lo
-- espera el cliente (mismos nombres de campo). Las escrituras NO pasan por
-- esta vista — son las funciones RPC de abajo, porque desempacar los arrays
-- anidados del DTO es más claro como función que como vista actualizable con
-- INSTEAD OF triggers.
-- ---------------------------------------------------------------------------

create view api.paciente_expediente with (security_invoker = true) as
select
  p.id_paciente as "idPaciente",
  p.estado_cuenta as "estadoCuenta",
  jsonb_build_object(
    'curp', i.curp, 'nss', i.nss, 'aseguradora', i.aseguradora
  ) as "identificaciones",
  coalesce(
    (select jsonb_agg(jsonb_build_object(
        'idTarjetaRfid', d.id_tarjeta_rfid, 'estado', d.estado,
        'fechaAsignacion', d.fecha_asignacion, 'fechaRevocacion', d.fecha_revocacion
      ))
     from app.dispositivos_rfid d where d.id_paciente = p.id_paciente),
    '[]'::jsonb
  ) as "dispositivosRfid",
  jsonb_build_object(
    'nombre', dp.nombre, 'apellidos', dp.apellidos,
    'fechaNacimiento', dp.fecha_nacimiento, 'genero', dp.genero, 'telefono', dp.telefono
  ) as "datosPersonales",
  coalesce(
    (select jsonb_agg(jsonb_build_object(
        'nombre', c.nombre, 'relacion', c.relacion,
        'telefono', c.telefono, 'prioridad', c.prioridad
      ) order by c.prioridad)
     from app.contactos_emergencia c where c.id_paciente = p.id_paciente),
    '[]'::jsonb
  ) as "contactosEmergencia",
  jsonb_build_object(
    'tipoSangre', per.tipo_sangre, 'donadorOrganos', per.donador_organos,
    'alergias', coalesce(
      (select jsonb_agg(jsonb_build_object(
          'alergeno', a.alergeno, 'severidad', a.severidad, 'reaccion', a.reaccion
        ))
       from app.alergias a where a.id_paciente = p.id_paciente),
      '[]'::jsonb
    ),
    'condicionesCriticas', to_jsonb(coalesce(per.condiciones_criticas, '{}')),
    'medicacionRescate', to_jsonb(coalesce(per.medicacion_rescate, '{}'))
  ) as "perfilEmergenciaReducido",
  jsonb_build_object(
    'pesoKg', m.peso_kg, 'alturaCm', m.altura_cm, 'imc', m.imc,
    'ultimaPresionArterial', m.ultima_presion_arterial,
    'fechaTomaMetricas', app.iso_utc(m.fecha_toma_metricas)
  ) as "metricasVitalesActuales",
  jsonb_build_object(
    'cirugias', coalesce(
      (select jsonb_agg(jsonb_build_object(
          'procedimiento', ci.procedimiento, 'fecha', ci.fecha, 'notas', ci.notas
        ))
       from app.cirugias ci where ci.id_paciente = p.id_paciente),
      '[]'::jsonb
    ),
    'antecedentesHeredofamiliares', to_jsonb(coalesce(hc.antecedentes_heredofamiliares, '{}'))
  ) as "historialClinico",
  coalesce(
    (select jsonb_agg(jsonb_build_object(
        'idTratamiento', t.id_tratamiento, 'medicamento', t.medicamento, 'dosis', t.dosis,
        'frecuenciaHoras', t.frecuencia_horas, 'horariosSugeridos', to_jsonb(t.horarios_sugeridos),
        'viaAdministracion', t.via_administracion, 'fechaInicio', t.fecha_inicio,
        'fechaFin', t.fecha_fin, 'idMedicoReceta', t.id_medico_receta,
        'inventario', jsonb_build_object(
          'cantidadRestante', t.cantidad_restante, 'umbralAlerta', t.umbral_alerta
        )
      ))
     from app.tratamientos t where t.id_paciente = p.id_paciente),
    '[]'::jsonb
  ) as "tratamientosActivos",
  coalesce(
    (select jsonb_agg(jsonb_build_object(
        'idToma', ra.id_toma, 'idTratamiento', ra.id_tratamiento,
        'fechaHoraProgramada', app.iso_utc(ra.fecha_hora_programada),
        'fechaHoraReal', app.iso_utc(ra.fecha_hora_real),
        'estado', ra.estado, 'sintomasAsociados', ra.sintomas_asociados
      ))
     from app.registro_adherencia ra
     join app.tratamientos t2 on t2.id_tratamiento = ra.id_tratamiento
     where t2.id_paciente = p.id_paciente),
    '[]'::jsonb
  ) as "registroAdherencia",
  jsonb_build_object(
    'medicosAutorizados', coalesce(
      (select jsonb_agg(jsonb_build_object(
          'idMedico', ca.id_medico, 'nivelAcceso', ca.nivel_acceso,
          'fechaExpiracion', ca.fecha_expiracion
        ))
       from app.control_accesos_medico ca where ca.id_paciente = p.id_paciente),
      '[]'::jsonb
    )
  ) as "controlAccesos"
from app.pacientes p
left join app.identificaciones_paciente i on i.id_paciente = p.id_paciente
left join app.datos_personales_paciente dp on dp.id_paciente = p.id_paciente
left join app.perfil_emergencia_reducido per on per.id_paciente = p.id_paciente
left join app.metricas_vitales_actuales m on m.id_paciente = p.id_paciente
left join app.historial_clinico hc on hc.id_paciente = p.id_paciente;

-- El paciente puede marcar su propio onboarding como completo (lo hace
-- api.registrar_paciente); ningún otro campo de app.pacientes es escribible
-- desde este rol.
create policy pacientes_actualiza_propio_onboarding on app.pacientes
  for update
  using (id_paciente = current_setting('request.jwt.claims', true)::json->>'sub')
  with check (id_paciente = current_setting('request.jwt.claims', true)::json->>'sub');
grant update (requiere_onboarding) on app.pacientes to paciente;

grant usage on schema app to paciente, medico;
grant select on
  app.pacientes, app.identificaciones_paciente, app.dispositivos_rfid,
  app.datos_personales_paciente, app.contactos_emergencia,
  app.perfil_emergencia_reducido, app.alergias, app.metricas_vitales_actuales,
  app.historial_clinico, app.cirugias, app.tratamientos, app.registro_adherencia,
  app.control_accesos_medico
to paciente, medico;
grant select on api.paciente_expediente to paciente, medico;

-- ---------------------------------------------------------------------------
-- Escrituras: registrar_paciente (alta / onboarding) y actualizar_historial
-- (edición del expediente completo) como RPC. Ambas reciben el PacienteDto
-- completo como un único parámetro jsonb — PostgREST pasa el body entero
-- como ese parámetro cuando la función solo tiene uno de tipo json/jsonb.
-- ---------------------------------------------------------------------------

create or replace function api.registrar_paciente(payload jsonb)
returns text
language plpgsql
security invoker
as $$
declare
  v_id_paciente text;
begin
  v_id_paciente := current_setting('request.jwt.claims', true)::json->>'sub';
  if v_id_paciente is null then
    perform app.lanzar_error(401, 'NO_AUTORIZADO');
  end if;

  perform app._guardar_expediente(v_id_paciente, payload);

  update app.pacientes set requiere_onboarding = false where id_paciente = v_id_paciente;

  return v_id_paciente;
end;
$$;

create or replace function api.actualizar_historial(payload jsonb)
returns void
language plpgsql
security invoker
as $$
declare
  v_id_paciente text;
begin
  v_id_paciente := coalesce(payload->>'idPaciente', current_setting('request.jwt.claims', true)::json->>'sub');
  if not (app.es_dueno_paciente(v_id_paciente) or app.tiene_acceso_medico(v_id_paciente, true)) then
    perform app.lanzar_error(401, 'NO_AUTORIZADO');
  end if;

  perform app._guardar_expediente(v_id_paciente, payload);
end;
$$;

-- Función interna compartida por ambas RPC: reemplaza cada sub-colección del
-- expediente con lo que venga en el payload (lista completa, no un diff) —
-- así el cliente no tiene que calcular altas/bajas de arrays, manda el
-- estado deseado completo tal como ya arma el DTO en memoria.
create or replace function app._guardar_expediente(p_id_paciente text, payload jsonb)
returns void
language plpgsql
security definer
set search_path = app, pg_temp
as $$
declare
  v_ident jsonb := payload->'identificaciones';
  v_datos jsonb := payload->'datosPersonales';
  v_perfil jsonb := payload->'perfilEmergenciaReducido';
  v_metricas jsonb := payload->'metricasVitalesActuales';
  v_historial jsonb := payload->'historialClinico';
  elem jsonb;
begin
  if v_ident is not null then
    insert into app.identificaciones_paciente (id_paciente, curp, nss, aseguradora)
    values (p_id_paciente, v_ident->>'curp', v_ident->>'nss', v_ident->>'aseguradora')
    on conflict (id_paciente) do update set
      curp = excluded.curp, nss = excluded.nss, aseguradora = excluded.aseguradora;
  end if;

  if v_datos is not null then
    insert into app.datos_personales_paciente (id_paciente, nombre, apellidos, fecha_nacimiento, genero, telefono)
    values (
      p_id_paciente, v_datos->>'nombre', v_datos->>'apellidos',
      nullif(v_datos->>'fechaNacimiento', '')::date, v_datos->>'genero', v_datos->>'telefono'
    )
    on conflict (id_paciente) do update set
      nombre = excluded.nombre, apellidos = excluded.apellidos,
      fecha_nacimiento = excluded.fecha_nacimiento, genero = excluded.genero,
      telefono = excluded.telefono;
  end if;

  -- Igual que tratamientosActivos: se conserva el id existente (upsert), no
  -- se borra y recrea, porque el backend es dueño del ciclo de vida de la
  -- tarjeta (activa/revocada) y de su idTarjetaRfid.
  if payload ? 'dispositivosRfid' then
    for elem in select * from jsonb_array_elements(payload->'dispositivosRfid')
    loop
      insert into app.dispositivos_rfid (id_tarjeta_rfid, id_paciente, estado, fecha_asignacion, fecha_revocacion)
      values (
        coalesce(elem->>'idTarjetaRfid', app.generar_id('rfid')), p_id_paciente,
        coalesce(elem->>'estado', 'activa'),
        coalesce(nullif(elem->>'fechaAsignacion', '')::date, current_date),
        nullif(elem->>'fechaRevocacion', '')::date
      )
      on conflict (id_tarjeta_rfid) do update set
        estado = excluded.estado, fecha_revocacion = excluded.fecha_revocacion;
    end loop;
  end if;

  if payload ? 'contactosEmergencia' then
    delete from app.contactos_emergencia where id_paciente = p_id_paciente;
    for elem in select * from jsonb_array_elements(payload->'contactosEmergencia')
    loop
      insert into app.contactos_emergencia (id_paciente, nombre, relacion, telefono, prioridad)
      values (p_id_paciente, elem->>'nombre', elem->>'relacion', elem->>'telefono',
              coalesce((elem->>'prioridad')::int, 1));
    end loop;
  end if;

  if v_perfil is not null then
    insert into app.perfil_emergencia_reducido
      (id_paciente, tipo_sangre, donador_organos, condiciones_criticas, medicacion_rescate)
    values (
      p_id_paciente, v_perfil->>'tipoSangre', coalesce((v_perfil->>'donadorOrganos')::boolean, false),
      coalesce(array(select jsonb_array_elements_text(v_perfil->'condicionesCriticas')), '{}'),
      coalesce(array(select jsonb_array_elements_text(v_perfil->'medicacionRescate')), '{}')
    )
    on conflict (id_paciente) do update set
      tipo_sangre = excluded.tipo_sangre, donador_organos = excluded.donador_organos,
      condiciones_criticas = excluded.condiciones_criticas, medicacion_rescate = excluded.medicacion_rescate;

    if v_perfil ? 'alergias' then
      delete from app.alergias where id_paciente = p_id_paciente;
      for elem in select * from jsonb_array_elements(v_perfil->'alergias')
      loop
        insert into app.alergias (id_paciente, alergeno, severidad, reaccion)
        values (p_id_paciente, elem->>'alergeno', elem->>'severidad', elem->>'reaccion');
      end loop;
    end if;
  end if;

  if v_metricas is not null then
    insert into app.metricas_vitales_actuales
      (id_paciente, peso_kg, altura_cm, imc, ultima_presion_arterial, fecha_toma_metricas)
    values (
      p_id_paciente, (v_metricas->>'pesoKg')::numeric, (v_metricas->>'alturaCm')::int,
      (v_metricas->>'imc')::numeric, v_metricas->>'ultimaPresionArterial',
      nullif(v_metricas->>'fechaTomaMetricas', '')::timestamptz
    )
    on conflict (id_paciente) do update set
      peso_kg = excluded.peso_kg, altura_cm = excluded.altura_cm, imc = excluded.imc,
      ultima_presion_arterial = excluded.ultima_presion_arterial,
      fecha_toma_metricas = excluded.fecha_toma_metricas;
  end if;

  if v_historial is not null then
    insert into app.historial_clinico (id_paciente, antecedentes_heredofamiliares)
    values (
      p_id_paciente,
      coalesce(array(select jsonb_array_elements_text(v_historial->'antecedentesHeredofamiliares')), '{}')
    )
    on conflict (id_paciente) do update set
      antecedentes_heredofamiliares = excluded.antecedentes_heredofamiliares;

    if v_historial ? 'cirugias' then
      delete from app.cirugias where id_paciente = p_id_paciente;
      for elem in select * from jsonb_array_elements(v_historial->'cirugias')
      loop
        insert into app.cirugias (id_paciente, procedimiento, fecha, notas)
        values (p_id_paciente, elem->>'procedimiento', nullif(elem->>'fecha', '')::date, elem->>'notas');
      end loop;
    end if;
  end if;

  if payload ? 'tratamientosActivos' then
    for elem in select * from jsonb_array_elements(payload->'tratamientosActivos')
    loop
      insert into app.tratamientos (
        id_tratamiento, id_paciente, medicamento, dosis, frecuencia_horas,
        horarios_sugeridos, via_administracion, fecha_inicio, fecha_fin,
        id_medico_receta, cantidad_restante, umbral_alerta
      )
      values (
        coalesce(elem->>'idTratamiento', app.generar_id('trt')), p_id_paciente,
        elem->>'medicamento', elem->>'dosis', (elem->>'frecuenciaHoras')::int,
        coalesce(array(select jsonb_array_elements_text(elem->'horariosSugeridos')), '{}'),
        elem->>'viaAdministracion', nullif(elem->>'fechaInicio', '')::date,
        nullif(elem->>'fechaFin', '')::date, elem->>'idMedicoReceta',
        (elem->'inventario'->>'cantidadRestante')::int,
        coalesce((elem->'inventario'->>'umbralAlerta')::int, app.config_entero('umbral_alerta_inventario_default'))
      )
      on conflict (id_tratamiento) do update set
        medicamento = excluded.medicamento, dosis = excluded.dosis,
        frecuencia_horas = excluded.frecuencia_horas, horarios_sugeridos = excluded.horarios_sugeridos,
        via_administracion = excluded.via_administracion, fecha_inicio = excluded.fecha_inicio,
        fecha_fin = excluded.fecha_fin, id_medico_receta = excluded.id_medico_receta,
        cantidad_restante = excluded.cantidad_restante, umbral_alerta = excluded.umbral_alerta;
    end loop;
  end if;
end;
$$;

grant execute on function api.registrar_paciente(jsonb) to paciente;
grant execute on function api.actualizar_historial(jsonb) to paciente, medico;

-- _guardar_expediente es SECURITY DEFINER (bypasea RLS a propósito, ya que
-- la autorización real ya se validó arriba en las dos funciones api.*) — se
-- le quita el EXECUTE por defecto a PUBLIC y se re-otorga solo a los roles
-- que de verdad la invocan de forma anidada, para que no quede alcanzable
-- por ninguna otra vía aunque cambie la exposición de esquemas más adelante.
revoke all on function app._guardar_expediente(text, jsonb) from public;
grant execute on function app._guardar_expediente(text, jsonb) to paciente, medico;
