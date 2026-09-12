-- Módulo 10: diario de síntomas. Contrato: DiarioRepositorio.
--
-- Regla de negocio crítica del documento: `severidad` la calcula el CLIENTE
-- y se guarda tal cual — el backend NUNCA la recalcula al leer (si el
-- diccionario de términos vigilados cambia, una entrada de hace meses no
-- debe cambiar de color sola). Por eso este módulo es CRUD puro sobre una
-- vista actualizable, sin ninguna función RPC: no hay ninguna regla de
-- negocio de este lado que justifique una.
--
-- Es local-first: el paciente escribe offline y sincroniza después, así que
-- `instante` lo manda el cliente (cuándo se escribió de verdad), no
-- `now()` del servidor al sincronizar.
--
-- Trade-off aceptado a propósito: al ser una vista simple y actualizable
-- (sin RPC), `instante` se expone como el `timestamptz` nativo — PostgREST
-- lo serializa como `...+00:00`, no `...Z` (el `app.iso_utc` helper no se
-- puede aplicar aquí sin romper la actualización automática de la vista vía
-- INSERT/DELETE directo). Ambas formas son ISO-8601 UTC válidas; se prefiere
-- mantener este módulo como CRUD simple en vez de forzar una RPC solo por
-- ese detalle cosmético.

create table app.entradas_diario (
  id_entrada          text primary key default app.generar_id('entrada'),
  id_paciente         text not null references app.pacientes(id_paciente) on delete cascade,
  instante            timestamptz not null,
  fecha               date not null,
  texto               text not null,
  severidad           text not null check (severidad in ('VERDE', 'AMBAR', 'ROJO')),
  terminos_detectados text[] not null default '{}'
);
create index idx_entradas_diario_paciente on app.entradas_diario(id_paciente, instante desc);

alter table app.entradas_diario enable row level security;
create policy entradas_diario_visibles on app.entradas_diario
  for select
  using (app.es_dueno_paciente(id_paciente) or app.tiene_acceso_medico(id_paciente));
create policy entradas_diario_creadas_por_dueno on app.entradas_diario
  for insert
  with check (app.es_dueno_paciente(id_paciente));
create policy entradas_diario_eliminadas_por_dueno on app.entradas_diario
  for delete
  using (app.es_dueno_paciente(id_paciente));

grant select on app.entradas_diario to paciente, medico;
grant insert, delete on app.entradas_diario to paciente;

-- Vista simple (sin JOIN, sin expresiones en las columnas escribibles) para
-- que PostgREST la trate como automáticamente actualizable: INSERT/DELETE
-- funcionan directo contra la vista, sin necesidad de INSTEAD OF triggers.
create view api.entradas_diario with (security_invoker = true) as
select
  id_entrada as "idEntrada",
  id_paciente as "idPaciente",
  instante as "instante",
  fecha as "fecha",
  texto as "texto",
  severidad as "severidad",
  terminos_detectados as "terminosDetectados"
from app.entradas_diario;

grant select, insert, delete on api.entradas_diario to paciente;
grant select on api.entradas_diario to medico;
