-- pgTAP: módulo 8 (chat de orientación).
begin;
select plan(8);

select has_table('app', 'mensajes', 'existe app.mensajes');

insert into app.pacientes (id_paciente, correo, hash_contrasena) values ('pac_chat1', 'chat1@test.local', 'hash');
insert into app.medicos (id_medico, correo, hash_contrasena, nombre, apellidos, tratamiento, cedula_profesional)
values ('doc_chat1', 'docchat1@test.local', 'hash', 'Test', 'Medico', 'Dr.', 'CED-CHAT-1');
insert into app.conversaciones (id_conversacion, id_paciente, id_medico)
values ('conv_chat1', 'pac_chat1', 'doc_chat1');

set local role paciente;
set local request.jwt.claims to '{"sub":"pac_chat1","role":"paciente"}';
select is(
  (select "autor" from api.enviar_mensaje('conv_chat1', 'hola', now(), 'PACIENTE')),
  'PACIENTE',
  'enviar_mensaje devuelve el mensaje recién creado con su autor'
);
select throws_ok(
  $$select api.enviar_mensaje('conv_chat1', 'x', now(), 'ROBOT')$$,
  'PT400',
  null,
  'un autor fuera de PACIENTE/MEDICO es SOLICITUD_INVALIDA'
);
select is(
  (select "tipo" from api.obtener_respuesta_automatica('conv_chat1', now())),
  'ORIENTACION_INICIAL',
  'obtener_respuesta_automatica devuelve un mensaje de tipo ORIENTACION_INICIAL'
);
select is(
  (select count(*)::int from app.mensajes where id_conversacion = 'conv_chat1' and tipo = 'ORIENTACION_INICIAL'),
  1,
  'una segunda llamada no duplica el acuse'
);
select api.obtener_respuesta_automatica('conv_chat1', now());
select is(
  (select count(*)::int from app.mensajes where id_conversacion = 'conv_chat1' and tipo = 'ORIENTACION_INICIAL'),
  1,
  'sigue habiendo un solo acuse tras una segunda llamada'
);
reset role;

-- Un tercero ajeno a la conversación no puede leer el historial.
insert into app.pacientes (id_paciente, correo, hash_contrasena) values ('pac_chat2', 'chat2@test.local', 'hash');
set local role paciente;
set local request.jwt.claims to '{"sub":"pac_chat2","role":"paciente"}';
select throws_ok(
  $$select api.obtener_historial('conv_chat1')$$,
  'PT401',
  null,
  'un paciente ajeno a la conversación no puede leer su historial'
);
reset role;

set local role paciente;
set local request.jwt.claims to '{"sub":"pac_chat1","role":"paciente"}';
select is(
  (select count(*)::int from api.obtener_historial('conv_chat1')),
  2,
  'el historial trae el mensaje del paciente y el acuse automático'
);
reset role;

select * from finish();
rollback;
