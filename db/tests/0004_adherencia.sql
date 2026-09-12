-- pgTAP: módulo 4 (adherencia). Foco: la regla de negocio crítica
-- (tomado vs tomado_tarde según la ventana de gracia) y que obtenerSemana
-- siempre entregue 7 días exactos.
begin;
select plan(10);

select has_table('app', 'registro_adherencia', 'existe app.registro_adherencia');
select col_is_unique(
  'app', 'registro_adherencia', array['id_tratamiento', 'fecha_hora_programada'],
  'un tratamiento no puede tener dos tomas programadas al mismo instante'
);

insert into app.pacientes (id_paciente, correo, hash_contrasena)
values ('pac_adh1', 'adh1@test.local', 'hash');
insert into app.tratamientos (id_tratamiento, id_paciente, medicamento, horarios_sugeridos, fecha_inicio)
values ('trt_adh1', 'pac_adh1', 'Metformina', array['08:00'], '2026-01-01');

-- Materializar dos veces el mismo día no debe duplicar filas (idempotencia).
select app.materializar_tomas_dia('pac_adh1', '2026-09-12');
select app.materializar_tomas_dia('pac_adh1', '2026-09-12');
select is(
  (select count(*)::int from app.registro_adherencia where id_tratamiento = 'trt_adh1'),
  1,
  'materializar_tomas_dia es idempotente: no duplica la toma del día'
);

set local role paciente;
set local request.jwt.claims to '{"sub":"pac_adh1","role":"paciente"}';

-- Dentro de la ventana de gracia (60 min por default) -> "tomado".
select lives_ok(
  $$select api.registrar_toma(
      (select id_toma from app.registro_adherencia where id_tratamiento = 'trt_adh1'),
      'tomado', '2026-09-12 08:30:00-06'
    )$$,
  'registrar_toma dentro de la ventana de gracia no lanza error'
);
reset role;
select is(
  (select estado from app.registro_adherencia where id_tratamiento = 'trt_adh1'),
  'tomado',
  'a 30 minutos del horario programado (gracia=60) queda "tomado"'
);

-- Reset para probar el caso "tarde": nueva toma al día siguiente.
select app.materializar_tomas_dia('pac_adh1', '2026-09-13');
set local role paciente;
set local request.jwt.claims to '{"sub":"pac_adh1","role":"paciente"}';
select api.registrar_toma(
  (select id_toma from app.registro_adherencia
     where id_tratamiento = 'trt_adh1'
       and (fecha_hora_programada at time zone 'America/Mexico_City')::date = '2026-09-13'),
  'tomado', '2026-09-13 10:00:00-06'
);
reset role;
select is(
  (select estado from app.registro_adherencia
     where id_tratamiento = 'trt_adh1'
       and (fecha_hora_programada at time zone 'America/Mexico_City')::date = '2026-09-13'),
  'tomado_tarde',
  'a 2 horas del horario programado (gracia=60) queda "tomado_tarde"'
);

-- Otro paciente ni siquiera "ve" la toma ajena por RLS: da 404, no 401 —
-- evita confirmar que un id_toma existe cuando no es visible para ti.
insert into app.pacientes (id_paciente, correo, hash_contrasena)
values ('pac_adh2', 'adh2@test.local', 'hash');
set local role paciente;
set local request.jwt.claims to '{"sub":"pac_adh2","role":"paciente"}';
select throws_ok(
  $$select api.registrar_toma(
      (select id_toma from app.registro_adherencia where id_tratamiento = 'trt_adh1' limit 1),
      'omitido', now()
    )$$,
  'PT404',
  null,
  'una toma que RLS no deja ver a este paciente da 404, no 401'
);
select throws_ok(
  $$select api.registrar_toma('toma_no_existe', 'tomado', now())$$,
  'PT404',
  null,
  'registrar una toma inexistente da 404 TOMA_DESCONOCIDA'
);
reset role;

-- Un médico SÍ puede ver la toma (tiene vínculo de lectura), pero
-- registrar_toma es una acción exclusiva del paciente dueño -> 401.
insert into app.medicos (id_medico, correo, hash_contrasena, nombre, apellidos, tratamiento, cedula_profesional)
values ('doc_adh1', 'docadh1@test.local', 'hash', 'Test', 'Medico', 'Dr.', 'CED-ADH-1');
insert into app.control_accesos_medico (id_paciente, id_medico, nivel_acceso)
values ('pac_adh1', 'doc_adh1', 'lectura_escritura');
set local role medico;
set local request.jwt.claims to '{"sub":"doc_adh1","role":"medico"}';
select throws_ok(
  $$select api.registrar_toma(
      (select id_toma from app.registro_adherencia where id_tratamiento = 'trt_adh1' limit 1),
      'omitido', now()
    )$$,
  'PT401',
  null,
  'un médico vinculado puede ver la toma pero no registrarla (solo el paciente dueño puede)'
);
reset role;

-- obtenerSemana siempre trae 7 filas, con o sin tratamiento.
set local role paciente;
set local request.jwt.claims to '{"sub":"pac_adh1","role":"paciente"}';
select is(
  (select count(*)::int from api.adherencia_semana('pac_adh1', '2026-09-12')),
  7,
  'adherencia_semana siempre devuelve exactamente 7 días'
);
reset role;

select * from finish();
rollback;
