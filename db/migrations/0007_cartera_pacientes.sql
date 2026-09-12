-- Módulo 6: cartera de pacientes vinculados del profesional. Contrato:
-- PacientesVinculadosRepositorio.
--
-- Nota de alcance: "riesgo" hoy solo considera adherencia (módulo 4), porque
-- el diario de síntomas (módulo 10, la señal más urgente según el propio
-- documento) todavía no existe en esta migración. Se documenta aquí a
-- propósito para no olvidar ampliar `app.riesgo_paciente` cuando se
-- implemente el módulo 10.

create or replace function app.nombre_completo_paciente(p_nombre text, p_apellidos text)
returns text
language sql
immutable
as $$
  select trim(both ' ' from coalesce(p_nombre, '') || ' ' || coalesce(p_apellidos, ''));
$$;

-- Lee directo de app.registro_adherencia (sin pasar por
-- api.adherencia_resumen/adherencia_semana): esas funciones materializan
-- perezosamente filas nuevas (INSERT), y esta vista se sirve por GET, que
-- PostgREST corre en una transacción de solo lectura — un INSERT ahí
-- adentro tumbaría la consulta completa con "cannot execute INSERT in a
-- read-only transaction". Esta función se queda con lo que ya esté
-- materializado, sin forzar nada nuevo.
create or replace function app.riesgo_paciente(id_paciente text)
returns text
language sql
stable
as $$
  select case
    when x.total = 0 then 'BAJO'
    when x.cumplidas::numeric / x.total >= 0.8 then 'BAJO'
    when x.cumplidas::numeric / x.total >= 0.5 then 'MEDIO'
    else 'ALTO'
  end
  from (
    select
      count(ra.id_toma) as total,
      count(ra.id_toma) filter (where ra.estado in ('tomado', 'tomado_tarde')) as cumplidas
    from app.tratamientos t
    join app.registro_adherencia ra on ra.id_tratamiento = t.id_tratamiento
    where t.id_paciente = riesgo_paciente.id_paciente
      and ra.fecha_hora_programada >= now() - interval '7 days'
  ) x;
$$;

grant execute on function app.riesgo_paciente(text) to medico;

create view api.pacientes_vinculados with (security_invoker = true) as
select
  ca.id_paciente as "idPaciente",
  app.nombre_completo_paciente(dp.nombre, dp.apellidos) as "nombreCompleto",
  app.riesgo_paciente(ca.id_paciente) as "riesgo",
  c.id_conversacion as "idConversacion"
from app.control_accesos_medico ca
join app.conversaciones c on c.id_paciente = ca.id_paciente and c.id_medico = ca.id_medico
left join app.datos_personales_paciente dp on dp.id_paciente = ca.id_paciente;

-- Solo el profesional ve su propia cartera — RLS de control_accesos_medico
-- (0003) ya restringe "id_medico = sub", así que no hace falta repetir el
-- filtro aquí; sí hace falta que solo `medico` tenga el GRANT (un paciente
-- viendo esta vista vería una fila "vinculada a sí mismo" sin sentido).
grant select on api.pacientes_vinculados to medico;
