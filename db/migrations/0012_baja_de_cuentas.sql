-- Baja de cuentas. Requisito de Google Play: una app que permite crear cuenta
-- debe permitir eliminarla, desde la app y desde una pagina web.
--
-- Decision del dueno del producto (2026-09-13): la baja elimina el ACCESO y
-- los datos que no forman parte del expediente clinico, y CONSERVA el
-- expediente el plazo legal (NOM-004-SSA3-2012: minimo 5 anos). Pasado ese
-- plazo se purga solo. La politica de privacidad tiene que decir exactamente
-- esto; la pagina /auth/eliminar-cuenta lo explica al usuario antes de borrar.
--
-- El plazo se cuenta desde la baja y no desde el ultimo acto medico, como pide
-- la NOM: tras la baja ya no hay actos nuevos, asi que es la cuenta mas
-- conservadora de las dos.

alter table app.pacientes drop constraint pacientes_estado_cuenta_check;
alter table app.pacientes add constraint pacientes_estado_cuenta_check
  check (estado_cuenta in ('activa', 'bloqueada', 'eliminada'));
alter table app.pacientes add column eliminada_en timestamptz;

alter table app.medicos drop constraint medicos_estado_cuenta_check;
alter table app.medicos add constraint medicos_estado_cuenta_check
  check (estado_cuenta in ('activa', 'bloqueada', 'eliminada'));
alter table app.medicos add column eliminada_en timestamptz;

insert into app.configuracion (clave, valor) values ('anios_retencion_expediente', '5')
on conflict (clave) do nothing;

-- ---------------------------------------------------------------------------
-- Paciente
-- ---------------------------------------------------------------------------
-- SE BORRA: correo y contrasena (el correo queda libre para registrarse de
-- nuevo), telefono, contactos de emergencia (son datos de terceros),
-- CURP/NSS/aseguradora, la tarjeta RFID (se revoca: el endpoint publico deja
-- de mostrar el perfil), el acceso de todos los medicos y las citas futuras.
--
-- SE CONSERVA: nombre, fecha de nacimiento y sexo (la NOM exige que el
-- expediente identifique al paciente), alergias, diagnosticos, cirugias,
-- tratamientos, adherencia, metricas, diario de sintomas, citas pasadas y el
-- chat con el medico (es comunicacion clinica).
create or replace function app.dar_de_baja_paciente(p_id_paciente text)
returns void
language plpgsql
as $$
begin
  -- `.invalid` es un dominio reservado (RFC 2606): nunca recibe correo. Un
  -- hash vacio no se puede parsear, asi que ninguna contrasena lo verifica.
  update app.pacientes
     set correo = 'baja.' || id_paciente || '@cuenta-eliminada.invalid',
         hash_contrasena = '',
         estado_cuenta = 'eliminada',
         eliminada_en = now()
   where id_paciente = p_id_paciente;

  update app.datos_personales_paciente set telefono = null where id_paciente = p_id_paciente;
  delete from app.contactos_emergencia where id_paciente = p_id_paciente;
  delete from app.identificaciones_paciente where id_paciente = p_id_paciente;

  update app.dispositivos_rfid
     set estado = 'revocada', fecha_revocacion = current_date
   where id_paciente = p_id_paciente and estado = 'activa';

  delete from app.control_accesos_medico where id_paciente = p_id_paciente;

  -- Cancelar (y no borrar) libera el horario: franjas_libres ignora las citas
  -- canceladas, y la cita queda como constancia en la agenda del medico.
  update app.citas
     set estado = 'CANCELADA'
   where id_paciente = p_id_paciente
     and fecha >= current_date
     and estado in ('PENDIENTE', 'CONFIRMADA');
end;
$$;

-- ---------------------------------------------------------------------------
-- Medico
-- ---------------------------------------------------------------------------
-- SE BORRA: correo, contrasena, universidad y disponibilidad; sale del
-- directorio (la vista filtra estado_cuenta = 'activa'), pierde el acceso a
-- todos los expedientes, se cancelan sus citas futuras y se retiran sus
-- horarios libres.
--
-- SE CONSERVA: nombre, tratamiento y cedula. Las recetas y las citas pasadas
-- de otros pacientes lo referencian, y la identidad de quien receto es parte
-- del expediente de ESOS pacientes. Por eso la fila del medico no se purga.
create or replace function app.dar_de_baja_medico(p_id_medico text)
returns void
language plpgsql
as $$
begin
  update app.medicos
     set correo = 'baja.' || id_medico || '@cuenta-eliminada.invalid',
         hash_contrasena = '',
         estado_cuenta = 'eliminada',
         eliminada_en = now(),
         universidad = null,
         disponibilidad = null
   where id_medico = p_id_medico;

  delete from app.control_accesos_medico where id_medico = p_id_medico;

  update app.citas
     set estado = 'CANCELADA'
   where id_medico = p_id_medico
     and fecha >= current_date
     and estado in ('PENDIENTE', 'CONFIRMADA');

  delete from app.reservas r
   using app.franjas f
   where r.id_franja = f.id_franja
     and f.id_medico = p_id_medico
     and f.fecha >= current_date;

  -- Las franjas con alguna cita (aunque cancelada) se quedan: la cita las
  -- referencia y es historial.
  delete from app.franjas f
   where f.id_medico = p_id_medico
     and f.fecha >= current_date
     and not exists (select 1 from app.citas c where c.id_franja = f.id_franja);
end;
$$;

-- ---------------------------------------------------------------------------
-- Purga al vencer el plazo legal
-- ---------------------------------------------------------------------------
-- Borra el expediente completo: todas las tablas del paciente cuelgan de
-- app.pacientes con ON DELETE CASCADE, salvo app.citas, que se borra antes.
create or replace function app.purgar_expedientes_vencidos()
returns integer
language plpgsql
as $$
declare
  v_limite timestamptz := now() - make_interval(years => app.config_entero('anios_retencion_expediente'));
  v_purgados integer;
begin
  delete from app.citas c
   using app.pacientes p
   where c.id_paciente = p.id_paciente
     and p.estado_cuenta = 'eliminada'
     and p.eliminada_en < v_limite;

  delete from app.pacientes
   where estado_cuenta = 'eliminada'
     and eliminada_en < v_limite;
  get diagnostics v_purgados = row_count;
  return v_purgados;
end;
$$;

-- Solo en la imagen de produccion (db/Dockerfile instala pg_cron y el compose
-- lo precarga). Las pruebas de integracion usan postgres:16-alpine, que no lo
-- trae: sin esta guarda la migracion fallaria ahi.
do $$
begin
  if exists (select 1 from pg_available_extensions where name = 'pg_cron') then
    create extension if not exists pg_cron;
    perform cron.schedule('purgar-expedientes-vencidos', '30 3 * * *',
                          'select app.purgar_expedientes_vencidos()');
  end if;
end
$$;
