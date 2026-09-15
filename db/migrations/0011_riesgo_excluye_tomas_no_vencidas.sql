-- Corrige app.riesgo_paciente: solo debe puntuar tomas que ya vencieron.
--
-- La versión de 0007 contaba en el denominador toda fila con
-- `fecha_hora_programada >= now() - 7 días`, sin límite superior. Como
-- app.materializar_tomas_dia (0004) crea las tomas del día en estado
-- 'pendiente' de forma perezosa, apenas el paciente abre su agenda quedan
-- registradas dosis que todavía no le toca tomar — y esas contaban como
-- incumplidas. Efecto: cualquier paciente con tratamiento activo caía a
-- 'ALTO' en cuanto usaba la app, sin importar su adherencia real.
--
-- Ahora el corte superior es el vencimiento de la dosis: hora programada más
-- los minutos de gracia que ya define la configuración ('minutos_gracia_toma',
-- 0004). Una toma pendiente cuyo plazo ya pasó sigue contando como
-- incumplimiento — eso es exactamente lo que es; una que aún no vence
-- simplemente no entra en el cálculo.
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
      and ra.fecha_hora_programada
          <= now() - (app.config_entero('minutos_gracia_toma') || ' minutes')::interval
  ) x;
$$;
