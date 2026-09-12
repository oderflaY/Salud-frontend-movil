-- pgTAP: módulo 5 (emergencia RFID). La lógica de negocio real (deliberadamente
-- pobre, rate limit, auditoría) vive en Axum y se verificó con curl contra el
-- stack real; aquí solo se cubre lo que sí vive en SQL: la estructura de
-- auditoría y que jamás quede expuesta vía PostgREST.
begin;
select plan(3);

select has_table('app', 'auditoria_rfid', 'existe app.auditoria_rfid');
select col_is_pk('app', 'auditoria_rfid', 'id_auditoria', 'id_auditoria es la PK');
select throws_ok(
  $$insert into app.auditoria_rfid (id_tarjeta_rfid, resultado) values ('rfid_x', 'invalido')$$,
  '23514',
  null,
  'resultado fuera de encontrada/desconocida/revocada se rechaza'
);

select * from finish();
rollback;
