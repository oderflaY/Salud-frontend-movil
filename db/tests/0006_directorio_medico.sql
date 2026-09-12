-- pgTAP: módulo 7 (directorio médico + vinculación).
begin;
select plan(9);

select has_table('app', 'conversaciones', 'existe app.conversaciones');
select has_view('api', 'directorio_medico', 'existe la vista api.directorio_medico');
select col_is_unique(
  'app', 'conversaciones', array['id_paciente', 'id_medico'],
  'un paciente y un médico comparten a lo más una conversación'
);

insert into app.pacientes (id_paciente, correo, hash_contrasena)
values ('pac_dir1', 'dir1@test.local', 'hash');
insert into app.medicos (id_medico, correo, hash_contrasena, nombre, apellidos, tratamiento, cedula_profesional, estado_verificacion, especialidad)
values ('doc_dir1', 'docdir1@test.local', 'hash', 'Elena', 'Ruiz', 'Dra.', 'CED-DIR-1', 'APROBADO', 'CARDIOLOGIA'),
       ('doc_dir2', 'docdir2@test.local', 'hash', 'Juan', 'Perez', 'Dr.', 'CED-DIR-2', 'PENDIENTE', 'PEDIATRIA');

select throws_ok(
  $$update app.medicos set especialidad = 'ONCOLOGIA' where id_medico = 'doc_dir1'$$,
  '23514',
  null,
  'especialidad fuera del enum cerrado de 10 valores se rechaza'
);

set local role paciente;
set local request.jwt.claims to '{"sub":"pac_dir1","role":"paciente"}';
select is(
  (select count(*)::int from api.directorio_medico where "idMedico" = 'doc_dir1'),
  1,
  'el directorio muestra a un médico aprobado'
);
select is(
  (select count(*)::int from api.directorio_medico where "idMedico" = 'doc_dir2'),
  0,
  'el directorio NO muestra a un médico pendiente de aprobación'
);

select is(
  (api.solicitar_vinculacion('doc_dir1'))."idMedico",
  'doc_dir1',
  'solicitar_vinculacion devuelve el MedicoVinculado correcto'
);
select is(
  (select count(*)::int from app.control_accesos_medico where id_paciente = 'pac_dir1' and id_medico = 'doc_dir1'),
  1,
  'solicitar_vinculacion crea el control de acceso'
);

-- Repetirla no debe duplicar la conversación (unique + on conflict do nothing).
select api.solicitar_vinculacion('doc_dir1');
select is(
  (select count(*)::int from app.conversaciones where id_paciente = 'pac_dir1' and id_medico = 'doc_dir1'),
  1,
  'solicitar_vinculacion es idempotente: no duplica la conversación'
);
reset role;

select * from finish();
rollback;
