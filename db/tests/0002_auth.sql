-- pgTAP: módulos 1 y 2 (autenticación paciente / profesional)
begin;
select plan(15);

-- Estructura
select has_table('app', 'pacientes', 'existe app.pacientes');
select has_table('app', 'medicos', 'existe app.medicos');
select col_is_unique('app', 'pacientes', array['correo'], 'correo de paciente es único');
select col_is_unique('app', 'medicos', array['cedula_profesional'], 'cédula profesional es única');
select col_is_unique('app', 'medicos', array['correo'], 'correo de médico es único');

-- generar_id respeta el prefijo semántico
select matches(app.generar_id('pac'), '^pac_[0-9a-f]{32}$', 'generar_id(pac) tiene el prefijo esperado');
select matches(app.generar_id('doc'), '^doc_[0-9a-f]{32}$', 'generar_id(doc) tiene el prefijo esperado');

-- Defaults de negocio
create temp table t_paciente as
with insertado as (
  insert into app.pacientes (correo, hash_contrasena)
  values ('paciente.test@example.com', 'hash-ficticio')
  returning id_paciente as id
)
select * from insertado;

select ok(
  (select requiere_onboarding from app.pacientes where id_paciente = (select id from t_paciente)) = true,
  'nuevo paciente nace con requiereOnboarding = true'
);
select ok(
  (select estado_cuenta from app.pacientes where id_paciente = (select id from t_paciente)) = 'activa',
  'nuevo paciente nace con estado_cuenta = activa'
);

create temp table t_medico as
with insertado as (
  insert into app.medicos (correo, hash_contrasena, nombre, apellidos, tratamiento, cedula_profesional)
  values ('medico.test@example.com', 'hash-ficticio', 'Elena', 'Ruiz Santos', 'Dra.', 'CED-0001')
  returning id_medico as id
)
select * from insertado;

select ok(
  (select estado_verificacion from app.medicos where id_medico = (select id from t_medico)) = 'PENDIENTE',
  'nuevo médico nace con estado_verificacion = PENDIENTE'
);

-- Constraints que deben rechazar valores fuera del dominio
select throws_ok(
  $$insert into app.medicos (correo, hash_contrasena, nombre, apellidos, tratamiento, cedula_profesional)
    values ('otro@example.com', 'x', 'A', 'B', 'Sr.', 'CED-9999')$$,
  '23514',
  null,
  'tratamiento fuera del dominio Dr./Dra./Dr(a). se rechaza'
);

select throws_ok(
  $$insert into app.pacientes (correo, hash_contrasena, estado_cuenta) values ('otro2@example.com', 'x', 'invalida')$$,
  '23514',
  null,
  'estado_cuenta fuera de activa/bloqueada se rechaza'
);

-- Correo duplicado
select throws_ok(
  $$insert into app.pacientes (correo, hash_contrasena) values ('paciente.test@example.com', 'otro-hash')$$,
  '23505',
  null,
  'correo de paciente duplicado se rechaza'
);

-- RLS habilitada (defensa en profundidad, aunque el único consumidor hoy es Axum
-- con el rol dueño de la base, que la bypasea). Se consulta el catálogo
-- directo con `ok()` en vez de un helper de RLS de pgTAP, para no depender
-- del nombre exacto de esa función en la versión de pgTAP instalada.
select ok(
  (select relrowsecurity from pg_class where oid = 'app.pacientes'::regclass),
  'RLS habilitada en app.pacientes'
);
select ok(
  (select relrowsecurity from pg_class where oid = 'app.medicos'::regclass),
  'RLS habilitada en app.medicos'
);

select * from finish();
rollback;
