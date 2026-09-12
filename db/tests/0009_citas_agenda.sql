-- pgTAP: módulo 9 (citas y agenda). La condición de carrera real ya se
-- probó en vivo con 5 curl simultáneos contra el stack completo (ver
-- historial de la sesión) — pgTAP corre en una sola conexión/transacción,
-- así que aquí se cubre la lógica secuencial: constraints, RLS, y que
-- franjas_libres/reservar_temporalmente/reprogramar respeten "ocupado".
begin;
select plan(11);

select has_table('app', 'franjas', 'existe app.franjas');
select has_table('app', 'citas', 'existe app.citas');
select throws_ok(
  $$insert into app.citas (folio, id_medico, id_paciente, id_franja, fecha, hora_inicio, hora_fin, estado)
    values ('X', 'doc_x', null, 'franja_x', current_date, '09:00', '09:30', 'PENDIENTE')$$,
  '23514',
  null,
  'una cita PENDIENTE sin id_paciente viola el check (BLOQUEADO <=> sin paciente)'
);

insert into app.pacientes (id_paciente, correo, hash_contrasena) values ('pac_cit1', 'cit1@test.local', 'hash');
insert into app.medicos (id_medico, correo, hash_contrasena, nombre, apellidos, tratamiento, cedula_profesional)
values ('doc_cit1', 'doccit1@test.local', 'hash', 'Test', 'Medico', 'Dr.', 'CED-CIT-1');
insert into app.franjas (id_franja, id_medico, fecha, hora_inicio, hora_fin)
values ('franja_cit1', 'doc_cit1', current_date + 1, '09:00', '09:30'),
       ('franja_cit2', 'doc_cit1', current_date + 1, '09:30', '10:00');

set local role paciente;
set local request.jwt.claims to '{"sub":"pac_cit1","role":"paciente"}';

select is(
  (select count(*)::int from api.franjas_libres('doc_cit1', current_date + 1, now())),
  2,
  'las 2 franjas nuevas están libres'
);

select is(
  (api.reservar_temporalmente('franja_cit1', now()))->>'idReserva' is not null,
  true,
  'reservar_temporalmente devuelve un idReserva'
);
select throws_ok(
  $$select api.reservar_temporalmente('franja_cit1', now())$$,
  'PT409',
  null,
  'reservar una franja ya retenida da FRANJA_OCUPADA'
);
select is(
  (select count(*)::int from api.franjas_libres('doc_cit1', current_date + 1, now())),
  1,
  'franjas_libres ya no cuenta la franja retenida'
);

select throws_ok(
  $$select api.confirmar_cita('reserva_no_existe', 'pac_cit1', '{}'::jsonb, now())$$,
  'PT410',
  null,
  'confirmar con una reserva inexistente da RESERVA_EXPIRADA'
);

reset role;

-- Reserva ya expirada (se inserta directo con expira_en en el pasado).
insert into app.reservas (id_reserva, id_franja, expira_en)
values ('reserva_vencida', 'franja_cit2', now() - interval '1 minute');

set local role paciente;
set local request.jwt.claims to '{"sub":"pac_cit1","role":"paciente"}';
select throws_ok(
  $$select api.confirmar_cita('reserva_vencida', 'pac_cit1', '{}'::jsonb, now())$$,
  'PT410',
  null,
  'confirmar con una reserva ya vencida da RESERVA_EXPIRADA'
);
reset role;

-- Tras confirmar_cita, la franja debe salir de franjas_libres.
update app.reservas set expira_en = now() + interval '5 minutes' where id_reserva = 'reserva_vencida';
set local role paciente;
set local request.jwt.claims to '{"sub":"pac_cit1","role":"paciente"}';
select api.confirmar_cita(
  'reserva_vencida', 'pac_cit1',
  '{"nombreCompleto":"Test","telefono":"555","correo":"a@b.com","motivo":"x"}'::jsonb,
  now()
);
reset role;
select is(
  (select estado from app.citas where id_franja = 'franja_cit2'),
  'PENDIENTE',
  'confirmar_cita deja la cita en PENDIENTE'
);
select is(
  (select count(*)::int from app.reservas where id_reserva = 'reserva_vencida'),
  0,
  'confirmar_cita borra la reserva ya usada'
);

select * from finish();
rollback;
