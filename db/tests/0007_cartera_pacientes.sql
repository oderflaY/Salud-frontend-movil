-- pgTAP: módulo 6 (cartera de pacientes vinculados).
begin;
select plan(6);

select has_view('api', 'pacientes_vinculados', 'existe api.pacientes_vinculados');

insert into app.pacientes (id_paciente, correo, hash_contrasena)
values ('pac_cart1', 'cart1@test.local', 'hash'), ('pac_cart2', 'cart2@test.local', 'hash');
insert into app.medicos (id_medico, correo, hash_contrasena, nombre, apellidos, tratamiento, cedula_profesional)
values ('doc_cart1', 'doccart1@test.local', 'hash', 'Test', 'Medico', 'Dr.', 'CED-CART-1');
insert into app.control_accesos_medico (id_paciente, id_medico, nivel_acceso)
values ('pac_cart1', 'doc_cart1', 'lectura');
insert into app.conversaciones (id_paciente, id_medico) values ('pac_cart1', 'doc_cart1');
insert into app.tratamientos (id_tratamiento, id_paciente, medicamento, horarios_sugeridos, fecha_inicio)
values ('trt_cart1', 'pac_cart1', 'Metformina', array['08:00'], current_date - 1);

-- Sin ninguna toma registrada -> BAJO (no hay señal, no se asume lo peor).
select is(app.riesgo_paciente('pac_cart1'), 'BAJO', 'sin tomas registradas, riesgo por defecto es BAJO');

insert into app.registro_adherencia (id_tratamiento, fecha_hora_programada, estado)
values ('trt_cart1', now() - interval '1 day', 'omitido'),
       ('trt_cart1', now() - interval '2 day', 'omitido'),
       ('trt_cart1', now() - interval '3 day', 'tomado');
select is(app.riesgo_paciente('pac_cart1'), 'ALTO', '1 de 3 cumplidas (33%) da riesgo ALTO');

update app.registro_adherencia set estado = 'tomado'
  where id_tratamiento = 'trt_cart1' and fecha_hora_programada = now() - interval '2 day';
select is(app.riesgo_paciente('pac_cart1'), 'MEDIO', '2 de 3 cumplidas (66%) da riesgo MEDIO');

set local role medico;
set local request.jwt.claims to '{"sub":"doc_cart1","role":"medico"}';
select is(
  (select count(*)::int from api.pacientes_vinculados),
  1,
  'el médico solo ve en su cartera a los pacientes vinculados a él'
);
select is(
  (select "idPaciente" from api.pacientes_vinculados limit 1),
  'pac_cart1',
  'la cartera trae al paciente correcto con su idConversacion'
);
reset role;

select * from finish();
rollback;
