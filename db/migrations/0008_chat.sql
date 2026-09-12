-- Módulo 8: chat de orientación. Contrato: ChatRepositorio.
--
-- Decisión de diseño: `obtenerRespuestaAutomatica` inserta el acuse con
-- autor='MEDICO' (el enum de MensajeChat solo admite PACIENTE|MEDICO, no hay
-- un tercer valor "SISTEMA") pero con tipo='ORIENTACION_INICIAL' — así el
-- cliente puede distinguir visualmente "esto es un acuse automático" de "el
-- médico ya contestó personalmente", que es el espíritu de la advertencia
-- del documento ("nunca una IA respondiendo en nombre del médico"), dado el
-- enum cerrado que sí existe.

insert into app.configuracion (clave, valor) values
  ('texto_acuse_recibo_chat', 'Hemos recibido tu mensaje. Un profesional lo revisará a la brevedad.')
on conflict (clave) do nothing;

alter table app.conversaciones
  add column leido_por_paciente_hasta timestamptz not null default '-infinity',
  add column leido_por_medico_hasta timestamptz not null default '-infinity';

create table app.mensajes (
  id_mensaje      text primary key default app.generar_id('msg'),
  id_conversacion text not null references app.conversaciones(id_conversacion) on delete cascade,
  autor           text not null check (autor in ('PACIENTE', 'MEDICO')),
  texto           text not null,
  instante        timestamptz not null default now(),
  tipo            text not null default 'NORMAL' check (tipo in ('NORMAL', 'ORIENTACION_INICIAL'))
);
create index idx_mensajes_conversacion on app.mensajes(id_conversacion, instante);

-- ---------------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------------
alter table app.mensajes enable row level security;
create policy mensajes_visibles_a_participantes on app.mensajes
  for select
  using (exists (
    select 1 from app.conversaciones c
    where c.id_conversacion = mensajes.id_conversacion
      and (app.es_dueno_paciente(c.id_paciente) or c.id_medico = current_setting('request.jwt.claims', true)::json->>'sub')
  ));
create policy mensajes_creados_por_participante on app.mensajes
  for insert
  with check (exists (
    select 1 from app.conversaciones c
    where c.id_conversacion = mensajes.id_conversacion
      and (app.es_dueno_paciente(c.id_paciente) or c.id_medico = current_setting('request.jwt.claims', true)::json->>'sub')
  ));
grant select, insert on app.mensajes to paciente, medico;

create policy conversaciones_marcada_leida on app.conversaciones
  for update
  using (app.es_dueno_paciente(id_paciente) or id_medico = current_setting('request.jwt.claims', true)::json->>'sub');
-- Grants de columna separados a propósito: un paciente no debe poder tocar
-- la marca de leído del médico ni viceversa.
grant update (leido_por_paciente_hasta) on app.conversaciones to paciente;
grant update (leido_por_medico_hasta) on app.conversaciones to medico;

-- ---------------------------------------------------------------------------
-- obtenerHistorial / enviarMensaje
-- ---------------------------------------------------------------------------
create or replace function api.obtener_historial(id_conversacion text)
returns table("idMensaje" text, "autor" text, "texto" text, "instante" text, "tipo" text)
language plpgsql
security invoker
as $$
#variable_conflict use_column
begin
  if not exists (
    select 1 from app.conversaciones c
    where c.id_conversacion = obtener_historial.id_conversacion
      and (app.es_dueno_paciente(c.id_paciente) or c.id_medico = current_setting('request.jwt.claims', true)::json->>'sub')
  ) then
    perform app.lanzar_error(401, 'NO_AUTORIZADO');
  end if;

  return query
    select m.id_mensaje, m.autor, m.texto, app.iso_utc(m.instante), m.tipo
    from app.mensajes m
    where m.id_conversacion = obtener_historial.id_conversacion
    order by m.instante;
end;
$$;

grant execute on function api.obtener_historial(text) to paciente, medico;

-- Los tres parámetros de entrada que colisionarían con columnas de salida
-- (autor, texto, instante) se renombran con sufijo — una función no puede
-- tener un parámetro IN y una columna OUT (la de RETURNS TABLE) con el mismo
-- nombre ("parameter name ... used more than once"). El shape de la
-- RESPUESTA sí respeta los nombres de campo de MensajeChat tal cual.
create or replace function api.enviar_mensaje(
  id_conversacion text, texto_mensaje text, instante_enviado timestamptz, autor_remitente text
)
returns table("idMensaje" text, "autor" text, "texto" text, "instante" text, "tipo" text)
language plpgsql
security invoker
as $$
#variable_conflict use_column
declare
  v_id_mensaje text;
begin
  if not exists (
    select 1 from app.conversaciones c
    where c.id_conversacion = enviar_mensaje.id_conversacion
      and (app.es_dueno_paciente(c.id_paciente) or c.id_medico = current_setting('request.jwt.claims', true)::json->>'sub')
  ) then
    perform app.lanzar_error(401, 'NO_AUTORIZADO');
  end if;

  if enviar_mensaje.autor_remitente not in ('PACIENTE', 'MEDICO') then
    perform app.lanzar_error(400, 'SOLICITUD_INVALIDA');
  end if;

  insert into app.mensajes (id_conversacion, autor, texto, instante)
  values (
    enviar_mensaje.id_conversacion, enviar_mensaje.autor_remitente,
    enviar_mensaje.texto_mensaje, enviar_mensaje.instante_enviado
  )
  returning id_mensaje into v_id_mensaje;

  perform app.emitir_evento(
    'chat:' || enviar_mensaje.id_conversacion,
    'mensaje_nuevo',
    jsonb_build_object('idMensaje', v_id_mensaje, 'autor', enviar_mensaje.autor_remitente, 'texto', enviar_mensaje.texto_mensaje)
  );

  return query
    select m.id_mensaje, m.autor, m.texto, app.iso_utc(m.instante), m.tipo
    from app.mensajes m where m.id_mensaje = v_id_mensaje;
end;
$$;

grant execute on function api.enviar_mensaje(text, text, timestamptz, text) to paciente, medico;

-- ---------------------------------------------------------------------------
-- marcarConversacionLeida
-- ---------------------------------------------------------------------------
create or replace function api.marcar_conversacion_leida(id_conversacion text)
returns void
language plpgsql
security invoker
as $$
#variable_conflict use_column
declare
  v_rol text := current_setting('request.jwt.claims', true)::json->>'role';
begin
  if v_rol = 'paciente' then
    update app.conversaciones set leido_por_paciente_hasta = now()
      where id_conversacion = marcar_conversacion_leida.id_conversacion
        and app.es_dueno_paciente(id_paciente);
  elsif v_rol = 'medico' then
    update app.conversaciones set leido_por_medico_hasta = now()
      where id_conversacion = marcar_conversacion_leida.id_conversacion
        and id_medico = current_setting('request.jwt.claims', true)::json->>'sub';
  else
    perform app.lanzar_error(401, 'NO_AUTORIZADO');
  end if;

  if not found then
    perform app.lanzar_error(404, 'CONVERSACION_DESCONOCIDA');
  end if;
end;
$$;

grant execute on function api.marcar_conversacion_leida(text) to paciente, medico;

-- ---------------------------------------------------------------------------
-- obtenerRespuestaAutomatica — una sola cortesía por conversación,
-- idempotente (si ya existe, se devuelve la que ya hay, nunca se duplica).
-- ---------------------------------------------------------------------------
-- Mismo motivo que en enviar_mensaje: el parámetro de entrada no puede
-- llamarse "instante" porque colisiona con la columna OUT del mismo nombre.
create or replace function api.obtener_respuesta_automatica(id_conversacion text, instante_recibido timestamptz)
returns table("idMensaje" text, "autor" text, "texto" text, "instante" text, "tipo" text)
language plpgsql
security invoker
as $$
#variable_conflict use_column
declare
  v_id_mensaje text;
begin
  if not exists (
    select 1 from app.conversaciones c
    where c.id_conversacion = obtener_respuesta_automatica.id_conversacion
      and (app.es_dueno_paciente(c.id_paciente) or c.id_medico = current_setting('request.jwt.claims', true)::json->>'sub')
  ) then
    perform app.lanzar_error(401, 'NO_AUTORIZADO');
  end if;

  select m.id_mensaje into v_id_mensaje
  from app.mensajes m
  where m.id_conversacion = obtener_respuesta_automatica.id_conversacion and m.tipo = 'ORIENTACION_INICIAL'
  limit 1;

  if v_id_mensaje is null then
    insert into app.mensajes (id_conversacion, autor, texto, instante, tipo)
    values (
      obtener_respuesta_automatica.id_conversacion, 'MEDICO',
      app.config_texto('texto_acuse_recibo_chat'), obtener_respuesta_automatica.instante_recibido,
      'ORIENTACION_INICIAL'
    )
    returning id_mensaje into v_id_mensaje;
  end if;

  return query
    select m.id_mensaje, m.autor, m.texto, app.iso_utc(m.instante), m.tipo
    from app.mensajes m where m.id_mensaje = v_id_mensaje;
end;
$$;

grant execute on function api.obtener_respuesta_automatica(text, timestamptz) to paciente;
