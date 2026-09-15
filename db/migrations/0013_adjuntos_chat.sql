-- Adjuntos del chat: fotos, archivos y documentos escaneados
-- (frontend/docs/CONTRATOS_BACKEND.md, seccion 13). El cliente ya los tenia
-- completos -- selector de galeria, de archivos y escaner de ML Kit/VisionKit --
-- pero el backend nunca los implemento: todo mensaje con adjunto fallaba.
--
-- Flujo: el cliente sube el binario a Axum (POST /chat/{id}/adjuntos,
-- multipart; necesita leer el JWT y limitar el tamano, cosa que PostgREST no
-- hace) y despues manda el mensaje por la RPC de siempre con el id recibido.
--
-- Los bytes viven en Postgres (bytea) y no en un volumen de archivos: asi los
-- cubren el mismo respaldo, la misma baja y la misma purga que el resto del
-- expediente (0012), sin un segundo almacen que mantener sincronizado. El
-- limite de 10 MB por archivo lo impone Axum antes de que llegue aqui.

create table app.adjuntos (
  id_adjunto      text primary key default app.generar_id('adj'),
  id_conversacion text not null references app.conversaciones(id_conversacion) on delete cascade,
  subido_por      text not null,
  tipo            text not null check (tipo in ('FOTO', 'ARCHIVO', 'ESCANEO')),
  nombre          text not null,
  tipo_mime       text not null,
  tamano_bytes    integer not null,
  contenido       bytea not null,
  creado_en       timestamptz not null default now()
);
create index idx_adjuntos_conversacion on app.adjuntos(id_conversacion);

-- Los roles de PostgREST ven los METADATOS (para armar el mensaje) pero no los
-- bytes: el binario solo sale por Axum, que valida que quien lo pide participa
-- en la conversacion y lo sirve con cabeceras seguras.
alter table app.adjuntos enable row level security;
create policy adjuntos_visibles_a_participantes on app.adjuntos
  for select
  using (exists (
    select 1 from app.conversaciones c
    where c.id_conversacion = adjuntos.id_conversacion
      and (app.es_dueno_paciente(c.id_paciente) or c.id_medico = current_setting('request.jwt.claims', true)::json->>'sub')
  ));
grant select (id_adjunto, id_conversacion, subido_por, tipo, nombre, tipo_mime) on app.adjuntos to paciente, medico;

-- Un adjunto pertenece a un solo mensaje.
alter table app.mensajes add column id_adjunto text references app.adjuntos(id_adjunto) on delete set null;
create unique index ux_mensajes_adjunto on app.mensajes(id_adjunto) where id_adjunto is not null;

-- La forma `adjunto` del contrato. `url` es relativa: el backend no sabe bajo
-- que dominio lo publica Caddy, y el cliente la resuelve contra su BASE_URL.
create or replace function app.adjunto_a_jsonb(p_id_adjunto text)
returns jsonb
language sql
stable
as $$
  select jsonb_build_object(
    'idAdjunto', a.id_adjunto,
    'tipo', a.tipo,
    'nombre', a.nombre,
    'tipoMime', a.tipo_mime,
    'url', '/chat/' || a.id_conversacion || '/adjuntos/' || a.id_adjunto
  )
  from app.adjuntos a
  where a.id_adjunto = p_id_adjunto;
$$;

-- ---------------------------------------------------------------------------
-- obtener_historial y enviar_mensaje ganan el campo `adjunto`. Cambia el tipo
-- de retorno, asi que hay que borrarlas y recrearlas (y volver a dar permisos).
-- ---------------------------------------------------------------------------
drop function api.obtener_historial(text);
create function api.obtener_historial(id_conversacion text)
returns table("idMensaje" text, "autor" text, "texto" text, "instante" text, "tipo" text, "adjunto" jsonb)
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
    select m.id_mensaje, m.autor, m.texto, app.iso_utc(m.instante), m.tipo, app.adjunto_a_jsonb(m.id_adjunto)
    from app.mensajes m
    where m.id_conversacion = obtener_historial.id_conversacion
    order by m.instante;
end;
$$;
grant execute on function api.obtener_historial(text) to paciente, medico;

drop function api.enviar_mensaje(text, text, timestamptz, text);
create function api.enviar_mensaje(
  id_conversacion text,
  texto_mensaje text,
  instante_enviado timestamptz,
  autor_remitente text,
  id_adjunto text default null
)
returns table("idMensaje" text, "autor" text, "texto" text, "instante" text, "tipo" text, "adjunto" jsonb)
language plpgsql
security invoker
as $$
#variable_conflict use_column
declare
  v_claims json := current_setting('request.jwt.claims', true)::json;
  v_id_mensaje text;
begin
  if not exists (
    select 1 from app.conversaciones c
    where c.id_conversacion = enviar_mensaje.id_conversacion
      and (app.es_dueno_paciente(c.id_paciente) or c.id_medico = v_claims->>'sub')
  ) then
    perform app.lanzar_error(401, 'NO_AUTORIZADO');
  end if;

  -- El autor lo dicta la sesion, no el cliente. La version anterior solo
  -- comprobaba que fuera PACIENTE o MEDICO: un paciente podia firmar un
  -- mensaje como MEDICO dentro de su propia conversacion.
  if enviar_mensaje.autor_remitente is distinct from
     (case v_claims->>'role' when 'paciente' then 'PACIENTE' when 'medico' then 'MEDICO' end) then
    perform app.lanzar_error(400, 'SOLICITUD_INVALIDA');
  end if;

  -- El texto puede ir vacio si hay adjunto; nunca los dos vacios.
  if coalesce(btrim(enviar_mensaje.texto_mensaje), '') = '' and enviar_mensaje.id_adjunto is null then
    perform app.lanzar_error(400, 'SOLICITUD_INVALIDA');
  end if;

  -- Solo se adjunta lo que el propio remitente subio a ESTA conversacion.
  if enviar_mensaje.id_adjunto is not null and not exists (
    select 1 from app.adjuntos a
    where a.id_adjunto = enviar_mensaje.id_adjunto
      and a.id_conversacion = enviar_mensaje.id_conversacion
      and a.subido_por = v_claims->>'sub'
  ) then
    perform app.lanzar_error(400, 'SOLICITUD_INVALIDA');
  end if;

  insert into app.mensajes (id_conversacion, autor, texto, instante, id_adjunto)
  values (
    enviar_mensaje.id_conversacion, enviar_mensaje.autor_remitente,
    coalesce(enviar_mensaje.texto_mensaje, ''), enviar_mensaje.instante_enviado,
    enviar_mensaje.id_adjunto
  )
  returning id_mensaje into v_id_mensaje;

  perform app.emitir_evento(
    'chat:' || enviar_mensaje.id_conversacion,
    'mensaje_nuevo',
    jsonb_build_object('idMensaje', v_id_mensaje, 'autor', enviar_mensaje.autor_remitente, 'texto', enviar_mensaje.texto_mensaje)
  );

  return query
    select m.id_mensaje, m.autor, m.texto, app.iso_utc(m.instante), m.tipo, app.adjunto_a_jsonb(m.id_adjunto)
    from app.mensajes m where m.id_mensaje = v_id_mensaje;
end;
$$;
grant execute on function api.enviar_mensaje(text, text, timestamptz, text, text) to paciente, medico;

-- PostgREST guarda en cache las firmas de las funciones: sin esto, un stack ya
-- corriendo seguiria buscando la version de 4 parametros hasta reiniciarse.
notify pgrst, 'reload schema';
