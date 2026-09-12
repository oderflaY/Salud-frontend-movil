-- Módulo 9: citas y agenda. Contrato: CitasRepositorio (FalloCita). El más
-- complejo: comparte estado entre el chat del paciente y la agenda del
-- médico, y tiene una condición de carrera real (dos pacientes reservando la
-- misma franja) que se resuelve con `SELECT ... FOR UPDATE` dentro de una
-- transacción — el mismo mecanismo, no una cola ni un lock aplicativo.
--
-- Nota de alcance: los 11 contratos no incluyen un método para que el
-- médico genere sus franjas base (solo `bloquearHorario`, que las bloquea
-- una vez que ya existen). Se agrega `api.generar_franjas` como adición
-- mínima, igual que el perfil de directorio en el módulo 7.

create table app.franjas (
  id_franja   text primary key default app.generar_id('franja'),
  id_medico   text not null references app.medicos(id_medico),
  fecha       date not null,
  hora_inicio time not null,
  hora_fin    time not null,
  unique (id_medico, fecha, hora_inicio)
);
create index idx_franjas_medico_fecha on app.franjas(id_medico, fecha);

create table app.reservas (
  id_reserva text primary key default app.generar_id('reserva'),
  id_franja  text not null references app.franjas(id_franja),
  expira_en  timestamptz not null
);
create index idx_reservas_franja on app.reservas(id_franja);

create table app.citas (
  id_cita           text primary key default app.generar_id('cita'),
  folio             text not null unique,
  id_medico         text not null references app.medicos(id_medico),
  id_paciente       text references app.pacientes(id_paciente),
  id_franja         text not null references app.franjas(id_franja),
  fecha             date not null,
  hora_inicio       time not null,
  hora_fin          time not null,
  estado            text not null
    check (estado in ('PENDIENTE', 'CONFIRMADA', 'EN_CURSO', 'CANCELADA', 'NO_ASISTIO', 'BLOQUEADO')),
  contacto_nombre   text,
  contacto_telefono text,
  contacto_correo   text,
  contacto_motivo   text,
  nota_bloqueo      text,
  -- Un bloqueo no tiene paciente; cualquier otro estado sí.
  check ((estado = 'BLOQUEADO') = (id_paciente is null))
);
create index idx_citas_franja on app.citas(id_franja);
create index idx_citas_medico_fecha on app.citas(id_medico, fecha);

create or replace function app.generar_folio()
returns text
language sql
volatile
as $$
  select 'CITA-' || upper(substr(md5(random()::text || clock_timestamp()::text), 1, 4));
$$;

-- SECURITY DEFINER a propósito: hace un JOIN a app.medicos solo para leer
-- nombre/tratamiento. Si esta función corriera como el rol invocador
-- (paciente), la política de RLS de app.medicos ("aprobado y visible en
-- directorio" o "es su propia fila") escondería al médico cada vez que
-- estuviera PENDIENTE o RECHAZADO — el INNER JOIN perdía la fila entera y
-- toda la función devolvía NULL (bug real: confirmar_cita devolvía
-- `"cita": null` con un médico recién dado de alta). La autorización real ya
-- la validó quien llamó a esta función, comprobando que puede ver la fila
-- de app.citas; mostrar el nombre del médico de esa cita no depende de si
-- ese médico ya fue aprobado en el directorio.
create or replace function app.cita_a_jsonb(p_id_cita text)
returns jsonb
language sql
stable
security definer
set search_path = app, pg_temp
as $$
  select jsonb_build_object(
    'idCita', c.id_cita, 'folio', c.folio, 'idMedico', c.id_medico,
    'nombreMedico', app.nombre_completo_medico(m.tratamiento, m.nombre, m.apellidos),
    'idPaciente', c.id_paciente, 'fecha', c.fecha,
    'horaInicio', to_char(c.hora_inicio, 'HH24:MI'), 'horaFin', to_char(c.hora_fin, 'HH24:MI'),
    'estado', c.estado,
    'contacto', case when c.id_paciente is null then null else jsonb_build_object(
      'nombreCompleto', c.contacto_nombre, 'telefono', c.contacto_telefono,
      'correo', c.contacto_correo, 'motivo', c.contacto_motivo
    ) end,
    'notaBloqueo', coalesce(c.nota_bloqueo, '')
  )
  from app.citas c
  join app.medicos m on m.id_medico = c.id_medico
  where c.id_cita = p_id_cita;
$$;

revoke all on function app.cita_a_jsonb(text) from public;
grant execute on function app.cita_a_jsonb(text) to paciente, medico;

-- ---------------------------------------------------------------------------
-- RLS. Franjas y reservas no son datos sensibles de un paciente en
-- particular (son horarios de consultorio / retenciones anónimas hasta que
-- se confirman) — visibles a cualquier rol autenticado. Citas sí lo son.
-- ---------------------------------------------------------------------------
alter table app.franjas enable row level security;
create policy franjas_visibles_a_autenticados on app.franjas for select using (true);
create policy franjas_creadas_por_su_medico on app.franjas
  for insert
  with check (id_medico = current_setting('request.jwt.claims', true)::json->>'sub');
-- Sin esta, `SELECT ... FOR UPDATE` sobre franjas (reservar_temporalmente,
-- confirmar_cita, reprogramar, bloquear_horario) devuelve CERO filas bajo
-- RLS aunque la política de SELECT sí las deje ver: Postgres exige también
-- una política para el comando UPDATE (la que de verdad aplicaría si se
-- modificara la fila) para poder tomar el bloqueo de fila, no solo verla.
-- El GRANT de columna por sí solo no alcanza sin esta política.
create policy franjas_bloqueables_por_autenticados on app.franjas for update using (true);

alter table app.reservas enable row level security;
create policy reservas_visibles_a_autenticados on app.reservas for all using (true);

alter table app.citas enable row level security;
create policy citas_visibles_a_participantes on app.citas
  for select
  using (id_medico = current_setting('request.jwt.claims', true)::json->>'sub' or app.es_dueno_paciente(id_paciente));
-- Mismo motivo que en franjas: cambiar_estado hace un UPDATE real y
-- reprogramar necesita FOR UPDATE sobre citas — ambos exigen esta política.
create policy citas_actualizables_por_participantes on app.citas
  for update
  using (id_medico = current_setting('request.jwt.claims', true)::json->>'sub' or app.es_dueno_paciente(id_paciente));
-- confirmar_cita inserta con id_paciente = quien confirma; bloquear_horario
-- inserta un BLOQUEADO (id_paciente null) como el propio médico.
create policy citas_creadas_por_participantes on app.citas
  for insert
  with check (
    (id_paciente is not null and app.es_dueno_paciente(id_paciente))
    or (id_paciente is null and id_medico = current_setting('request.jwt.claims', true)::json->>'sub')
  );

grant select on app.franjas, app.citas to paciente, medico;
-- `SELECT ... FOR UPDATE` (reservar_temporalmente, reprogramar,
-- bloquear_horario) exige el privilegio UPDATE además de SELECT, aunque
-- ninguna de las dos políticas RLS de franjas permita un UPDATE real desde
-- estos roles — el grant solo habilita el bloqueo de fila, RLS sigue
-- vetando la escritura de datos.
grant update on app.franjas to paciente, medico;
-- franjas_libres (paciente Y medico) lee app.reservas para descartar
-- franjas retenidas — ambos roles necesitan al menos SELECT ahí, aunque
-- solo el paciente reserva/libera.
-- update incluido aunque nadie actualiza una reserva: confirmar_cita hace
-- `SELECT ... FOR UPDATE` sobre ella, que exige el privilegio UPDATE igual
-- que en franjas/citas (ver convención 11 del doc de convenciones).
grant select, insert, update, delete on app.reservas to paciente;
grant select on app.reservas to medico;
grant insert on app.franjas to medico;
grant insert, update on app.citas to paciente, medico;

-- ---------------------------------------------------------------------------
-- generar_franjas (adición fuera de los 11 contratos, ver nota arriba)
-- ---------------------------------------------------------------------------
create or replace function api.generar_franjas(
  fecha_inicio date, fecha_fin date, hora_inicio time, hora_fin time, duracion_minutos int
)
returns integer
language plpgsql
security invoker
as $$
#variable_conflict use_column
declare
  v_id_medico text := current_setting('request.jwt.claims', true)::json->>'sub';
  v_fecha date := generar_franjas.fecha_inicio;
  v_hora time;
  v_creadas int := 0;
begin
  if v_id_medico is null then
    perform app.lanzar_error(401, 'NO_AUTORIZADO');
  end if;

  while v_fecha <= generar_franjas.fecha_fin loop
    v_hora := generar_franjas.hora_inicio;
    while v_hora < generar_franjas.hora_fin loop
      insert into app.franjas (id_medico, fecha, hora_inicio, hora_fin)
      values (v_id_medico, v_fecha, v_hora, v_hora + make_interval(mins => generar_franjas.duracion_minutos))
      on conflict (id_medico, fecha, hora_inicio) do nothing;
      if found then
        v_creadas := v_creadas + 1;
      end if;
      v_hora := v_hora + make_interval(mins => generar_franjas.duracion_minutos);
    end loop;
    v_fecha := v_fecha + 1;
  end loop;

  return v_creadas;
end;
$$;

grant execute on function api.generar_franjas(date, date, time, time, int) to medico;

-- ---------------------------------------------------------------------------
-- franjasLibres
-- ---------------------------------------------------------------------------
create or replace function api.franjas_libres(id_medico text, desde date, ahora timestamptz)
returns table("idFranja" text, "idMedico" text, "fecha" date, "horaInicio" text, "horaFin" text)
language sql
security invoker
stable
as $$
  select f.id_franja, f.id_medico, f.fecha,
         to_char(f.hora_inicio, 'HH24:MI'), to_char(f.hora_fin, 'HH24:MI')
  from app.franjas f
  where f.id_medico = franjas_libres.id_medico
    and f.fecha >= franjas_libres.desde
    and not exists (select 1 from app.citas c where c.id_franja = f.id_franja and c.estado <> 'CANCELADA')
    and not exists (select 1 from app.reservas r where r.id_franja = f.id_franja and r.expira_en > franjas_libres.ahora)
  order by f.fecha, f.hora_inicio;
$$;

grant execute on function api.franjas_libres(text, date, timestamptz) to paciente, medico;

-- ---------------------------------------------------------------------------
-- reservarTemporalmente — el `for update` es lo que hace la sección
-- realmente segura ante dos pacientes reservando la misma franja a la vez.
-- ---------------------------------------------------------------------------
create or replace function api.reservar_temporalmente(id_franja text, ahora timestamptz)
returns jsonb
language plpgsql
security invoker
as $$
#variable_conflict use_column
declare
  v_franja app.franjas%rowtype;
  v_id_reserva text;
  v_expira timestamptz;
  v_minutos int := app.config_entero('minutos_retencion');
begin
  select * into v_franja from app.franjas f
  where f.id_franja = reservar_temporalmente.id_franja
  for update;

  if not found then
    perform app.lanzar_error(409, 'FRANJA_OCUPADA');
  end if;

  if exists (select 1 from app.citas c where c.id_franja = v_franja.id_franja and c.estado <> 'CANCELADA')
     or exists (select 1 from app.reservas r where r.id_franja = v_franja.id_franja and r.expira_en > reservar_temporalmente.ahora)
  then
    perform app.lanzar_error(409, 'FRANJA_OCUPADA');
  end if;

  v_expira := reservar_temporalmente.ahora + make_interval(mins => v_minutos);

  insert into app.reservas (id_franja, expira_en)
  values (v_franja.id_franja, v_expira)
  returning id_reserva into v_id_reserva;

  return jsonb_build_object(
    'idReserva', v_id_reserva,
    'franja', jsonb_build_object(
      'idFranja', v_franja.id_franja, 'idMedico', v_franja.id_medico,
      'fecha', v_franja.fecha, 'horaInicio', to_char(v_franja.hora_inicio, 'HH24:MI'),
      'horaFin', to_char(v_franja.hora_fin, 'HH24:MI')
    ),
    'expiraEn', app.iso_utc(v_expira)
  );
end;
$$;

grant execute on function api.reservar_temporalmente(text, timestamptz) to paciente;

create or replace function api.liberar_reserva(id_reserva text)
returns void
language sql
security invoker
as $$
  delete from app.reservas where id_reserva = liberar_reserva.id_reserva;
$$;

grant execute on function api.liberar_reserva(text) to paciente;

-- ---------------------------------------------------------------------------
-- confirmarCita
-- ---------------------------------------------------------------------------
create or replace function api.confirmar_cita(id_reserva text, id_paciente text, contacto jsonb, ahora timestamptz)
returns jsonb
language plpgsql
security invoker
as $$
#variable_conflict use_column
declare
  v_reserva app.reservas%rowtype;
  v_franja app.franjas%rowtype;
  v_id_cita text;
begin
  select * into v_reserva from app.reservas r where r.id_reserva = confirmar_cita.id_reserva for update;
  if not found or v_reserva.expira_en <= confirmar_cita.ahora then
    if found then
      delete from app.reservas where id_reserva = v_reserva.id_reserva;
    end if;
    perform app.lanzar_error(410, 'RESERVA_EXPIRADA');
  end if;

  select * into v_franja from app.franjas f where f.id_franja = v_reserva.id_franja for update;

  insert into app.citas (
    folio, id_medico, id_paciente, id_franja, fecha, hora_inicio, hora_fin, estado,
    contacto_nombre, contacto_telefono, contacto_correo, contacto_motivo
  )
  values (
    app.generar_folio(), v_franja.id_medico, confirmar_cita.id_paciente, v_franja.id_franja,
    v_franja.fecha, v_franja.hora_inicio, v_franja.hora_fin, 'PENDIENTE',
    confirmar_cita.contacto->>'nombreCompleto', confirmar_cita.contacto->>'telefono',
    confirmar_cita.contacto->>'correo', confirmar_cita.contacto->>'motivo'
  )
  returning id_cita into v_id_cita;

  delete from app.reservas where id_reserva = v_reserva.id_reserva;

  perform app.emitir_evento('agenda:' || v_franja.id_medico, 'cita_creada', jsonb_build_object('idCita', v_id_cita));

  -- Sin integraciones de correo/WhatsApp implementadas todavía: se reporta
  -- honestamente que no se notificó ningún canal, en vez de simular éxito.
  return jsonb_build_object('cita', app.cita_a_jsonb(v_id_cita), 'canalesNotificados', '[]'::jsonb);
end;
$$;

grant execute on function api.confirmar_cita(text, text, jsonb, timestamptz) to paciente;

-- ---------------------------------------------------------------------------
-- cargarAgenda / cambiarEstado / bloquearHorario / reprogramar
-- ---------------------------------------------------------------------------
create or replace function api.cargar_agenda(id_medico text)
returns setof jsonb
language sql
security invoker
stable
as $$
  select app.cita_a_jsonb(c.id_cita)
  from app.citas c
  where c.id_medico = cargar_agenda.id_medico
  order by c.fecha, c.hora_inicio;
$$;

grant execute on function api.cargar_agenda(text) to medico;

create or replace function api.cambiar_estado(id_cita text, nuevo_estado text)
returns jsonb
language plpgsql
security invoker
as $$
#variable_conflict use_column
begin
  if not exists (
    select 1 from app.citas c
    where c.id_cita = cambiar_estado.id_cita
      and c.id_medico = current_setting('request.jwt.claims', true)::json->>'sub'
  ) then
    perform app.lanzar_error(401, 'NO_AUTORIZADO');
  end if;

  update app.citas set estado = cambiar_estado.nuevo_estado where id_cita = cambiar_estado.id_cita;

  perform app.emitir_evento(
    'agenda:' || (select c.id_medico from app.citas c where c.id_cita = cambiar_estado.id_cita),
    'cita_actualizada', jsonb_build_object('idCita', cambiar_estado.id_cita)
  );

  return app.cita_a_jsonb(cambiar_estado.id_cita);
end;
$$;

grant execute on function api.cambiar_estado(text, text) to medico;

create or replace function api.bloquear_horario(
  id_medico text, fecha date, hora_inicio time, hora_fin time, nota text
)
returns setof jsonb
language plpgsql
security invoker
as $$
#variable_conflict use_column
declare
  v_franja record;
begin
  if bloquear_horario.id_medico <> current_setting('request.jwt.claims', true)::json->>'sub' then
    perform app.lanzar_error(401, 'NO_AUTORIZADO');
  end if;

  for v_franja in
    select * from app.franjas f
    where f.id_medico = bloquear_horario.id_medico
      and f.fecha = bloquear_horario.fecha
      and f.hora_inicio >= bloquear_horario.hora_inicio
      and f.hora_inicio < bloquear_horario.hora_fin
    order by f.hora_inicio
    for update
  loop
    if exists (select 1 from app.citas c where c.id_franja = v_franja.id_franja and c.estado <> 'CANCELADA') then
      perform app.lanzar_error(409, 'FRANJA_OCUPADA');
    end if;

    insert into app.citas (folio, id_medico, id_paciente, id_franja, fecha, hora_inicio, hora_fin, estado, nota_bloqueo)
    values (
      app.generar_folio(), v_franja.id_medico, null, v_franja.id_franja,
      v_franja.fecha, v_franja.hora_inicio, v_franja.hora_fin, 'BLOQUEADO', bloquear_horario.nota
    );
  end loop;

  perform app.emitir_evento('agenda:' || bloquear_horario.id_medico, 'agenda_bloqueada', '{}'::jsonb);

  return query
    select app.cita_a_jsonb(c.id_cita)
    from app.citas c
    where c.id_medico = bloquear_horario.id_medico
      and c.fecha = bloquear_horario.fecha
      and c.hora_inicio >= bloquear_horario.hora_inicio
      and c.hora_inicio < bloquear_horario.hora_fin
      and c.estado = 'BLOQUEADO';
end;
$$;

grant execute on function api.bloquear_horario(text, date, time, time, text) to medico;

create or replace function api.reprogramar(id_cita text, id_franja_nueva text, ahora timestamptz)
returns jsonb
language plpgsql
security invoker
as $$
#variable_conflict use_column
declare
  v_cita app.citas%rowtype;
  v_franja app.franjas%rowtype;
begin
  select * into v_cita from app.citas c where c.id_cita = reprogramar.id_cita for update;
  if not found then
    perform app.lanzar_error(409, 'FRANJA_OCUPADA');
  end if;
  if v_cita.id_medico <> current_setting('request.jwt.claims', true)::json->>'sub'
     and not app.es_dueno_paciente(v_cita.id_paciente)
  then
    perform app.lanzar_error(401, 'NO_AUTORIZADO');
  end if;

  select * into v_franja from app.franjas f where f.id_franja = reprogramar.id_franja_nueva for update;
  if not found
     or exists (select 1 from app.citas c2 where c2.id_franja = v_franja.id_franja and c2.estado <> 'CANCELADA')
     or exists (select 1 from app.reservas r where r.id_franja = v_franja.id_franja and r.expira_en > reprogramar.ahora)
  then
    perform app.lanzar_error(409, 'FRANJA_OCUPADA');
  end if;

  update app.citas set
    id_franja = v_franja.id_franja, fecha = v_franja.fecha,
    hora_inicio = v_franja.hora_inicio, hora_fin = v_franja.hora_fin, estado = 'PENDIENTE'
  where id_cita = reprogramar.id_cita;

  perform app.emitir_evento('agenda:' || v_cita.id_medico, 'cita_actualizada', jsonb_build_object('idCita', reprogramar.id_cita));

  return app.cita_a_jsonb(reprogramar.id_cita);
end;
$$;

grant execute on function api.reprogramar(text, text, timestamptz) to paciente, medico;
