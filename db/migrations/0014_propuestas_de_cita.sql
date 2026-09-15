-- Propuesta de cita desde el medico (frontend/docs/CONTRATOS_BACKEND.md,
-- seccion 12). El cliente ya la tenia completa; el backend nunca la
-- implemento. Es el reverso de reservar_temporalmente + confirmar_cita: alli
-- agenda el paciente y valida el consultorio; aqui agenda el consultorio y
-- valida el paciente.
--
-- Reglas del contrato:
--  - La propuesta ocupa la franja de inmediato (ningun otro paciente puede
--    tomarla mientras el destinatario decide).
--  - Solo el paciente destinatario acepta o rechaza.
--  - El medico NUNCA confirma su propia propuesta: confirmar lo que uno mismo
--    propuso no valida nada. Puede retirarla (cancelarla), nada mas.

alter table app.citas drop constraint citas_estado_check;
alter table app.citas add constraint citas_estado_check
  check (estado in ('PENDIENTE', 'CONFIRMADA', 'EN_CURSO', 'CANCELADA', 'NO_ASISTIO', 'BLOQUEADO', 'PROPUESTA_MEDICO'));

-- SECURITY DEFINER a proposito: la politica de INSERT de app.citas solo deja
-- al medico crear bloqueos (sin paciente). Aqui el medico crea una cita PARA un
-- paciente, asi que las comprobaciones que haria RLS se hacen explicitas abajo.
create or replace function api.proponer_cita(
  id_franja text, id_paciente text, contacto jsonb, ahora timestamptz
)
returns jsonb
language plpgsql
security definer
set search_path = app, pg_temp
as $$
#variable_conflict use_column
declare
  v_claims json := current_setting('request.jwt.claims', true)::json;
  v_franja app.franjas%rowtype;
  v_id_cita text;
begin
  if v_claims->>'role' is distinct from 'medico' then
    perform app.lanzar_error(401, 'NO_AUTORIZADO');
  end if;

  select * into v_franja from app.franjas f
  where f.id_franja = proponer_cita.id_franja
  for update;

  -- La franja tiene que ser del medico que propone.
  if not found or v_franja.id_medico <> v_claims->>'sub' then
    perform app.lanzar_error(401, 'NO_AUTORIZADO');
  end if;

  -- Solo a un paciente con vinculo vigente con este medico.
  if not exists (
    select 1 from app.control_accesos_medico ca
    where ca.id_medico = v_franja.id_medico
      and ca.id_paciente = proponer_cita.id_paciente
      and (ca.fecha_expiracion is null or ca.fecha_expiracion >= current_date)
  ) then
    perform app.lanzar_error(401, 'NO_AUTORIZADO');
  end if;

  -- Misma regla de "estrictamente libre" que reservar_temporalmente.
  if exists (select 1 from app.citas c where c.id_franja = v_franja.id_franja and c.estado <> 'CANCELADA')
     or exists (select 1 from app.reservas r where r.id_franja = v_franja.id_franja and r.expira_en > proponer_cita.ahora)
  then
    perform app.lanzar_error(409, 'FRANJA_OCUPADA');
  end if;

  insert into app.citas (
    folio, id_medico, id_paciente, id_franja, fecha, hora_inicio, hora_fin, estado,
    contacto_nombre, contacto_telefono, contacto_correo, contacto_motivo
  )
  values (
    app.generar_folio(), v_franja.id_medico, proponer_cita.id_paciente, v_franja.id_franja,
    v_franja.fecha, v_franja.hora_inicio, v_franja.hora_fin, 'PROPUESTA_MEDICO',
    proponer_cita.contacto->>'nombreCompleto', proponer_cita.contacto->>'telefono',
    proponer_cita.contacto->>'correo', proponer_cita.contacto->>'motivo'
  )
  returning id_cita into v_id_cita;

  perform app.emitir_evento('agenda:' || v_franja.id_medico, 'cita_creada', jsonb_build_object('idCita', v_id_cita));

  return app.cita_a_jsonb(v_id_cita);
end;
$$;
revoke all on function api.proponer_cita(text, text, jsonb, timestamptz) from public;
grant execute on function api.proponer_cita(text, text, jsonb, timestamptz) to medico;

-- Aceptar y rechazar: SECURITY INVOKER basta, porque la politica de UPDATE de
-- app.citas ya deja al paciente dueno actualizar su cita. Se exige ademas que
-- quien llama sea ESE paciente (no el medico) y que siga en PROPUESTA_MEDICO.
create or replace function app._resolver_propuesta(p_id_cita text, p_nuevo_estado text)
returns jsonb
language plpgsql
security invoker
as $$
declare
  v_id_medico text;
begin
  select c.id_medico into v_id_medico
  from app.citas c
  where c.id_cita = p_id_cita
    and app.es_dueno_paciente(c.id_paciente)
    and c.estado = 'PROPUESTA_MEDICO'
  for update;

  if not found then
    -- Ya no esta en propuesta (el medico la retiro, o ya se resolvio) o no es
    -- de este paciente: un motivo propio para que el cliente no lo confunda
    -- con falta de red.
    perform app.lanzar_error(409, 'PROPUESTA_NO_DISPONIBLE');
  end if;

  update app.citas set estado = p_nuevo_estado where id_cita = p_id_cita;
  perform app.emitir_evento('agenda:' || v_id_medico, 'cita_actualizada', jsonb_build_object('idCita', p_id_cita));
  return app.cita_a_jsonb(p_id_cita);
end;
$$;

create or replace function api.aceptar_propuesta(id_cita text)
returns jsonb
language sql
security invoker
as $$ select app._resolver_propuesta(aceptar_propuesta.id_cita, 'CONFIRMADA'); $$;

-- Cancelar libera la franja: franjas_libres ignora las citas canceladas.
create or replace function api.rechazar_propuesta(id_cita text)
returns jsonb
language sql
security invoker
as $$ select app._resolver_propuesta(rechazar_propuesta.id_cita, 'CANCELADA'); $$;

grant execute on function api.aceptar_propuesta(text) to paciente;
grant execute on function api.rechazar_propuesta(text) to paciente;

-- cambiar_estado deja al medico mover su cita a cualquier estado. Con las
-- propuestas eso abria un atajo: pasar su propia PROPUESTA_MEDICO a CONFIRMADA
-- sin que el paciente dijera nada. Sobre una propuesta, el medico solo puede
-- retirarla (CANCELADA); y nunca puede crear una propuesta por esta via.
create or replace function api.cambiar_estado(id_cita text, nuevo_estado text)
returns jsonb
language plpgsql
security invoker
as $$
#variable_conflict use_column
declare
  v_estado_actual text;
begin
  select c.estado into v_estado_actual
  from app.citas c
  where c.id_cita = cambiar_estado.id_cita
    and c.id_medico = current_setting('request.jwt.claims', true)::json->>'sub';

  if not found then
    perform app.lanzar_error(401, 'NO_AUTORIZADO');
  end if;

  if (v_estado_actual = 'PROPUESTA_MEDICO' and cambiar_estado.nuevo_estado <> 'CANCELADA')
     or cambiar_estado.nuevo_estado = 'PROPUESTA_MEDICO' then
    perform app.lanzar_error(409, 'PROPUESTA_NO_DISPONIBLE');
  end if;

  update app.citas set estado = cambiar_estado.nuevo_estado where id_cita = cambiar_estado.id_cita;

  perform app.emitir_evento(
    'agenda:' || (select c.id_medico from app.citas c where c.id_cita = cambiar_estado.id_cita),
    'cita_actualizada', jsonb_build_object('idCita', cambiar_estado.id_cita)
  );

  return app.cita_a_jsonb(cambiar_estado.id_cita);
end;
$$;

notify pgrst, 'reload schema';
