-- Módulo 5: emergencia por tarjeta RFID. Contrato: PerfilEmergenciaRepositorio.
-- Vive enteramente en Axum (no PostgREST): es el único endpoint sin sesión,
-- necesita rate limiting por IP y auditoría de cada consulta — ver
-- docs/contratos-datos-backend.md sección 5 ("deliberadamente pobre").
--
-- Sin RLS a propósito: esta tabla nunca se expone vía `api.*`, la consulta
-- solo la hace Axum con su conexión privilegiada. El control de acceso real
-- es que Axum decide explícitamente qué columnas devolver, no un rol de
-- Postgres.
create table app.auditoria_rfid (
  id_auditoria    text primary key default app.generar_id('audrfid'),
  id_tarjeta_rfid text not null,
  resultado       text not null check (resultado in ('encontrada', 'desconocida', 'revocada')),
  ip_origen       text,
  creado_en       timestamptz not null default now()
);
create index idx_auditoria_rfid_tarjeta on app.auditoria_rfid(id_tarjeta_rfid);
