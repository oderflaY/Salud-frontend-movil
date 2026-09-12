-- pgTAP: módulo 3 (expediente del paciente) — el foco es la segmentación de
-- permisos (RLS + control_accesos_medico), que es la parte de este módulo
-- que de verdad puede fallar en silencio.
begin;
select plan(12);

select has_table('app', 'datos_personales_paciente', 'existe tabla datos_personales_paciente');
select has_table('app', 'control_accesos_medico', 'existe tabla control_accesos_medico');
select has_view('api', 'paciente_expediente', 'existe la vista api.paciente_expediente');

select is(
  app.iso_utc('2026-09-12T19:00:00+00:00'::timestamptz),
  '2026-09-12T19:00:00Z',
  'iso_utc formatea con sufijo Z, no +00:00'
);

-- Fixtures directas (como salud_admin, dueño de las tablas, bypasea RLS).
insert into app.pacientes (id_paciente, correo, hash_contrasena)
values ('pac_test1', 'paciente1@test.local', 'hash'),
       ('pac_test2', 'paciente2@test.local', 'hash');
insert into app.medicos (id_medico, correo, hash_contrasena, nombre, apellidos, tratamiento, cedula_profesional)
values ('doc_test1', 'medico1@test.local', 'hash', 'Test', 'Medico', 'Dr.', 'CED-TEST-1');
insert into app.datos_personales_paciente (id_paciente, nombre, apellidos)
values ('pac_test1', 'Paciente', 'Uno'), ('pac_test2', 'Paciente', 'Dos');

-- El paciente ve su propio expediente...
set local role paciente;
set local request.jwt.claims to '{"sub":"pac_test1","role":"paciente"}';
select is(
  (select count(*)::int from api.paciente_expediente where "idPaciente" = 'pac_test1'),
  1,
  'el paciente ve su propio expediente'
);
-- ...pero no el de otro paciente.
select is(
  (select count(*)::int from api.paciente_expediente where "idPaciente" = 'pac_test2'),
  0,
  'el paciente NO ve el expediente de otro paciente'
);
reset role;

-- Un médico sin vínculo en control_accesos_medico no ve nada.
set local role medico;
set local request.jwt.claims to '{"sub":"doc_test1","role":"medico"}';
select is(
  (select count(*)::int from api.paciente_expediente where "idPaciente" = 'pac_test1'),
  0,
  'un médico sin vínculo no ve el expediente del paciente'
);
-- ...y tampoco puede escribir en él (NO_AUTORIZADO / PT401).
select throws_ok(
  $$select api.actualizar_historial('{"idPaciente":"pac_test1"}'::jsonb)$$,
  'PT401',
  null,
  'un médico sin vínculo no puede editar el historial (401)'
);
reset role;

-- Se otorga acceso de solo lectura.
insert into app.control_accesos_medico (id_paciente, id_medico, nivel_acceso)
values ('pac_test1', 'doc_test1', 'lectura');

set local role medico;
set local request.jwt.claims to '{"sub":"doc_test1","role":"medico"}';
select is(
  (select count(*)::int from api.paciente_expediente where "idPaciente" = 'pac_test1'),
  1,
  'con vínculo de lectura, el médico sí ve el expediente'
);
select throws_ok(
  $$select api.actualizar_historial('{"idPaciente":"pac_test1"}'::jsonb)$$,
  'PT401',
  null,
  'con solo nivel "lectura", el médico no puede editar (401)'
);
reset role;

-- Se sube el nivel a lectura_escritura: ahora sí puede editar.
update app.control_accesos_medico set nivel_acceso = 'lectura_escritura'
  where id_paciente = 'pac_test1' and id_medico = 'doc_test1';

set local role medico;
set local request.jwt.claims to '{"sub":"doc_test1","role":"medico"}';
select lives_ok(
  $$select api.actualizar_historial('{"idPaciente":"pac_test1","datosPersonales":{"nombre":"Paciente","apellidos":"Uno Editado"}}'::jsonb)$$,
  'con nivel lectura_escritura, el médico sí puede editar el historial'
);
reset role;

select is(
  (select apellidos from app.datos_personales_paciente where id_paciente = 'pac_test1'),
  'Uno Editado',
  'la edición del médico autorizado efectivamente se guardó'
);

select * from finish();
rollback;
