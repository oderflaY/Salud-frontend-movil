-- pgTAP: módulo 10 (diario de síntomas). Foco: severidad nunca se recalcula
-- (se guarda tal cual llega) y la segmentación de RLS.
begin;
select plan(7);

select has_table('app', 'entradas_diario', 'existe app.entradas_diario');
select has_view('api', 'entradas_diario', 'existe la vista actualizable api.entradas_diario');
select throws_ok(
  $$insert into app.entradas_diario (id_paciente, instante, fecha, texto, severidad)
    values ('pac_x', now(), current_date, 'x', 'AZUL')$$,
  '23514',
  null,
  'severidad fuera de VERDE/AMBAR/ROJO se rechaza'
);

insert into app.pacientes (id_paciente, correo, hash_contrasena) values ('pac_diario1', 'diario1@test.local', 'hash');

set local role paciente;
set local request.jwt.claims to '{"sub":"pac_diario1","role":"paciente"}';
insert into api.entradas_diario ("idPaciente", "instante", "fecha", "texto", "severidad", "terminosDetectados")
values ('pac_diario1', now() - interval '2 days', current_date - 2, 'dolor leve', 'VERDE', '{}');
insert into api.entradas_diario ("idPaciente", "instante", "fecha", "texto", "severidad", "terminosDetectados")
values ('pac_diario1', now(), current_date, 'dolor fuerte en el pecho', 'ROJO', array['dolor en el pecho']);

select is(
  (select count(*)::int from api.entradas_diario where "idPaciente" = 'pac_diario1'),
  2,
  'el paciente ve sus 2 entradas recién creadas'
);
select is(
  (select "severidad" from api.entradas_diario where "idPaciente" = 'pac_diario1' order by "instante" desc limit 1),
  'ROJO',
  'la entrada más reciente (ultimaEntradaDe) es la ROJO'
);

delete from api.entradas_diario where "idPaciente" = 'pac_diario1' and "severidad" = 'VERDE';
select is(
  (select count(*)::int from api.entradas_diario where "idPaciente" = 'pac_diario1'),
  1,
  'eliminar borra solo la entrada indicada'
);
reset role;

insert into app.medicos (id_medico, correo, hash_contrasena, nombre, apellidos, tratamiento, cedula_profesional)
values ('doc_diario1', 'docdiario1@test.local', 'hash', 'Test', 'Medico', 'Dr.', 'CED-DIA-1');
set local role medico;
set local request.jwt.claims to '{"sub":"doc_diario1","role":"medico"}';
select is(
  (select count(*)::int from api.entradas_diario where "idPaciente" = 'pac_diario1'),
  0,
  'un médico sin vínculo no ve las entradas del diario'
);
reset role;

select * from finish();
rollback;
