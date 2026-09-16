-- Panel web del medico (Salud+ Panel Web, React). Reemplaza las lecturas de
-- 0016 por el contrato que el frontend ya implementa ("Contrato de API —
-- Salud+ Panel Web", 2026-09-15):
--
--  - Modulo admin en snake_case; chat en camelCase (espeja la app movil).
--  - Fechas del modulo admin en hora local de la clinica SIN zona
--    ("2026-09-08T07:30:00"); las del chat en UTC con milisegundos y Z.
--  - null explicito, nunca clave omitida ni "" (salvo `adjunto`, que el
--    contrato pide omitir cuando no hay).
--
-- Sigue siendo el medico con su cuenta de la app, viendo SOLO pacientes con
-- vinculo vigente. Lo sirve Axum (/api/v1/*); ninguna de estas funciones es
-- ejecutable por los roles de PostgREST.
--
-- Campos del contrato sin dato en el sistema (vacunas, telemetria de sueno,
-- glucosa, CIE-10...) salen en null o [] — nunca inventados.

drop function if exists app.dashboard_kpis(text);
drop function if exists app.dashboard_pacientes(text);
drop function if exists app.dashboard_alertas(text, text, text);
drop function if exists app.dashboard_auditoria(text);
drop function if exists app.alerta_a_jsonb(text);
drop function if exists app.enmascarar_correo(text);

-- ---------------------------------------------------------------------------
-- Formatos
-- ---------------------------------------------------------------------------

create or replace function app.iso_local(p_instante timestamptz)
returns text
language sql
stable
as $$
  select to_char(p_instante at time zone app.config_texto('zona_horaria_default'), 'YYYY-MM-DD"T"HH24:MI:SS');
$$;

create or replace function app.iso_utc_ms(p_instante timestamptz)
returns text
language sql
immutable
as $$
  select to_char(p_instante at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"');
$$;

create or replace function app.hoy_local()
returns date
language sql
stable
as $$
  select (now() at time zone app.config_texto('zona_horaria_default'))::date;
$$;

-- El semaforo del panel es el mismo nivel de riesgo que ve la app.
create or replace function app.semaforo(p_riesgo text)
returns text
language sql
immutable
as $$
  select case p_riesgo when 'ALTO' then 'Rojo' when 'MEDIO' then 'Amarillo' else 'Verde' end;
$$;

-- No hay un campo "diagnostico": las condiciones criticas del perfil de
-- emergencia son lo mas cercano (las dos primeras, como en "HAS + DM2").
create or replace function app.diagnostico_principal(p_id_paciente text)
returns text
language sql
stable
as $$
  select nullif(array_to_string(per.condiciones_criticas[1:2], ' + '), '')
  from app.perfil_emergencia_reducido per
  where per.id_paciente = p_id_paciente;
$$;

create or replace function app.nombre_paciente_panel(p_id_paciente text)
returns text
language sql
stable
as $$
  select coalesce(nullif(app.nombre_completo_paciente(dp.nombre, dp.apellidos), ''), 'Paciente sin nombre')
  from app.pacientes p
  left join app.datos_personales_paciente dp on dp.id_paciente = p.id_paciente
  where p.id_paciente = p_id_paciente;
$$;

-- Tomas vencidas (pasado el margen de gracia) de un tratamiento en una ventana.
create or replace function app.adherencia_tratamiento(p_id_tratamiento text, p_dias integer)
returns integer
language sql
stable
as $$
  select round(100.0 * count(*) filter (where ra.estado in ('tomado', 'tomado_tarde')) / nullif(count(*), 0))::int
  from app.registro_adherencia ra
  where ra.id_tratamiento = p_id_tratamiento
    and ra.fecha_hora_programada >= now() - make_interval(days => p_dias)
    and ra.fecha_hora_programada <= now() - make_interval(mins => app.config_entero('minutos_gracia_toma'));
$$;

-- pendiente | tomada | retrasada, segun los horarios de hoy (hora local) y
-- las tomas registradas hoy. No depende de que la app haya creado filas
-- 'pendiente' por adelantado.
create or replace function app.estado_toma_hoy(p_id_tratamiento text)
returns text
language sql
stable
as $$
  with t as (
    select tr.horarios_sugeridos as horarios from app.tratamientos tr where tr.id_tratamiento = p_id_tratamiento
  ),
  ahora as (
    select (now() at time zone app.config_texto('zona_horaria_default'))
           - make_interval(mins => app.config_entero('minutos_gracia_toma')) as limite
  ),
  vencidos as (
    select count(*)::int as n
    from t, ahora, unnest(t.horarios) h
    where h ~ '^\d{1,2}:\d{2}$' and app.hoy_local() + h::time <= ahora.limite
  ),
  cumplidas as (
    select count(*)::int as n
    from app.registro_adherencia ra
    where ra.id_tratamiento = p_id_tratamiento
      and ra.estado in ('tomado', 'tomado_tarde')
      and (ra.fecha_hora_programada at time zone app.config_texto('zona_horaria_default'))::date = app.hoy_local()
  )
  select case
    when cardinality(t.horarios) = 0 then case when cumplidas.n > 0 then 'tomada' else 'pendiente' end
    when cumplidas.n < vencidos.n then 'retrasada'
    when vencidos.n = cardinality(t.horarios) then 'tomada'
    else 'pendiente'
  end
  from t, vencidos, cumplidas;
$$;

-- ---------------------------------------------------------------------------
-- GET /admin/dashboard/kpis
-- ---------------------------------------------------------------------------

create or replace function app.panel_kpis(p_id_medico text)
returns jsonb
language sql
stable
as $$
  with vinculos as (
    select ca.id_paciente
    from app.control_accesos_medico ca
    join app.pacientes p on p.id_paciente = ca.id_paciente and p.estado_cuenta <> 'eliminada'
    where ca.id_medico = p_id_medico
      and (ca.fecha_expiracion is null or ca.fecha_expiracion >= current_date)
  ),
  riesgos as (
    select v.id_paciente, app.riesgo_paciente(v.id_paciente) as riesgo, a.programadas, a.cumplidas
    from vinculos v
    cross join lateral app.adherencia_7_dias(v.id_paciente) a
  )
  select jsonb_build_object(
    'total_pacientes', (select count(*) from vinculos),
    'riesgo_rojo', (select count(*) from riesgos where riesgo = 'ALTO'),
    'riesgo_amarillo', (select count(*) from riesgos where riesgo = 'MEDIO'),
    'riesgo_verde', (select count(*) from riesgos where riesgo = 'BAJO'),
    -- El contrato pide entero: 0 cuando ningun paciente tiene tomas vencidas.
    'adherencia_global', coalesce((select round(100.0 * sum(cumplidas) / nullif(sum(programadas), 0))::int from riesgos), 0),
    'citas_hoy', (
      select count(*) from app.citas c
      where c.id_medico = p_id_medico and c.fecha = app.hoy_local()
        and c.estado in ('PENDIENTE', 'CONFIRMADA', 'EN_CURSO')),
    'pacientes_rfid', (
      select count(*) from vinculos v
      where exists (select 1 from app.dispositivos_rfid d where d.id_paciente = v.id_paciente and d.estado = 'activa')),
    'alertas_pendientes', (
      select count(*) from app.alertas al
      join vinculos v on v.id_paciente = al.id_paciente
      where al.id_medico = p_id_medico and al.estado = 'NUEVA')
  );
$$;

-- ---------------------------------------------------------------------------
-- GET /admin/pacientes
-- ---------------------------------------------------------------------------

create or replace function app.panel_paciente(p_id_medico text, p_id_paciente text)
returns jsonb
language sql
stable
as $$
  select jsonb_build_object(
    'id', p.id_paciente,
    'nombre_completo', app.nombre_paciente_panel(p.id_paciente),
    'fecha_nacimiento', dp.fecha_nacimiento,
    'edad', extract(year from age(dp.fecha_nacimiento))::int,
    'sexo_biologico', case upper(dp.genero) when 'M' then 'Masculino' when 'F' then 'Femenino' else dp.genero end,
    'diagnostico_principal', app.diagnostico_principal(p.id_paciente),
    -- Quien le receto el tratamiento vigente mas reciente; si nadie, el
    -- medico que esta viendo el panel.
    'medico_tratante', coalesce((
      select app.nombre_completo_medico(m.tratamiento, m.nombre, m.apellidos)
      from app.tratamientos tr
      join app.medicos m on m.id_medico = tr.id_medico_receta
      where tr.id_paciente = p.id_paciente and (tr.fecha_fin is null or tr.fecha_fin >= current_date)
      order by tr.fecha_inicio desc nulls last
      limit 1),
      (select app.nombre_completo_medico(m.tratamiento, m.nombre, m.apellidos) from app.medicos m where m.id_medico = p_id_medico)),
    'nivel_riesgo', app.semaforo(app.riesgo_paciente(p.id_paciente)),
    -- null = no tiene tomas que medir en 7 dias (no es lo mismo que 0 %).
    'adherencia_porcentaje', (
      select round(100.0 * a.cumplidas / nullif(a.programadas, 0))::int from app.adherencia_7_dias(p.id_paciente) a),
    -- Lo ultimo que el paciente registro en la app: metricas, diario o una toma.
    'ultima_telemetria', app.iso_local(greatest(
      m.fecha_toma_metricas,
      (select max(e.instante) from app.entradas_diario e where e.id_paciente = p.id_paciente),
      (select max(ra.fecha_hora_real) from app.registro_adherencia ra
         join app.tratamientos tr on tr.id_tratamiento = ra.id_tratamiento
        where tr.id_paciente = p.id_paciente))),
    'rfid_estado', case when rf.id_tarjeta_rfid is not null then 'vinculada' else 'sin_rfid' end,
    -- El UID abre el perfil de emergencia sin sesion: el panel solo muestra
    -- los ultimos 4 caracteres.
    'rfid_uid', case when rf.id_tarjeta_rfid is not null then 'RFID:••••:' || right(rf.id_tarjeta_rfid, 4) end,
    'proxima_cita', (
      select to_char(c.fecha + c.hora_inicio, 'YYYY-MM-DD"T"HH24:MI:SS')
      from app.citas c
      where c.id_paciente = p.id_paciente and c.id_medico = p_id_medico
        and c.estado in ('PENDIENTE', 'CONFIRMADA', 'EN_CURSO')
        and c.fecha + c.hora_fin >= (now() at time zone app.config_texto('zona_horaria_default'))
      order by c.fecha, c.hora_inicio
      limit 1),
    'estado_cuenta', case
      when p.estado_cuenta = 'bloqueada' then 'suspendida'
      when p.debe_cambiar_contrasena then 'reseteada'
      else 'activa' end,

    'tipo_sangre', per.tipo_sangre,
    'donador_organos', coalesce(per.donador_organos, false),
    'curp', ident.curp,
    'nss', ident.nss,
    'aseguradora', ident.aseguradora,
    'telefono', dp.telefono,
    'peso_kg', m.peso_kg,
    'altura_cm', m.altura_cm,
    'imc', m.imc,

    'contactos_emergencia', coalesce((
      select jsonb_agg(jsonb_build_object(
               'id_contacto', ce.id_contacto,
               'relacion', ce.relacion,
               'nombre', ce.nombre,
               'telefono_movil', ce.telefono,
               'es_cuidador_principal', ce.prioridad = (select min(c2.prioridad) from app.contactos_emergencia c2 where c2.id_paciente = p.id_paciente),
               -- El sistema no tiene todavia esa autorizacion.
               'autorizado_para_reinicio_cuenta', false
             ) order by ce.prioridad, ce.nombre)
      from app.contactos_emergencia ce where ce.id_paciente = p.id_paciente), '[]'::jsonb),
    'medicacion_rescate', to_jsonb(coalesce(per.medicacion_rescate, '{}')),
    'condiciones_criticas', to_jsonb(coalesce(per.condiciones_criticas, '{}')),

    'historial', jsonb_build_object(
      'id_paciente', p.id_paciente,
      'antecedentes_patologicos', coalesce((
        select jsonb_agg(jsonb_build_object(
                 'id_antecedente', 'cond-' || c.n,
                 'diagnostico', c.diagnostico,
                 'cie10', null,
                 'anio_diagnostico', null,
                 'en_control', null,
                 'notas', null
               ) order by c.n)
        from unnest(per.condiciones_criticas) with ordinality c(diagnostico, n)), '[]'::jsonb),
      'antecedentes_quirurgicos', coalesce((
        select jsonb_agg(jsonb_build_object(
                 'id_cirugia', ci.id_cirugia,
                 'procedimiento', ci.procedimiento,
                 'anio', extract(year from ci.fecha)::int,
                 'complicaciones', ci.notas
               ) order by ci.fecha desc nulls last)
        from app.cirugias ci where ci.id_paciente = p.id_paciente), '[]'::jsonb),
      'alergias_y_reacciones', coalesce((
        select jsonb_agg(jsonb_build_object(
                 'id_alergia', al.id_alergia,
                 'alergeno', al.alergeno,
                 'agente', al.alergeno,
                 'tipo_reaccion', al.reaccion,
                 'reaccion', al.reaccion,
                 'severidad', initcap(lower(al.severidad)),
                 'confirmada_por_prueba', null
               ) order by al.alergeno)
        from app.alergias al where al.id_paciente = p.id_paciente), '[]'::jsonb),
      'antecedentes_heredofamiliares', to_jsonb(coalesce(hc.antecedentes_heredofamiliares, '{}')),
      'medicacion_rescate', to_jsonb(coalesce(per.medicacion_rescate, '{}')),
      'condiciones_criticas', to_jsonb(coalesce(per.condiciones_criticas, '{}')),
      'esquema_vacunacion', '[]'::jsonb,
      'medicacion_activa_y_verificacion', coalesce((
        select jsonb_agg(jsonb_build_object(
                 'id_medicamento', tr.id_tratamiento,
                 'nombre_comercial', tr.medicamento,
                 'principio_activo', null,
                 'dosis', tr.dosis,
                 'via_administracion', tr.via_administracion,
                 'frecuencia_horas', tr.frecuencia_horas,
                 'horarios_toma', to_jsonb(tr.horarios_sugeridos),
                 'indicaciones_especiales', null,
                 -- El paciente confirma cada toma en la app.
                 'metodo_verificacion', 'manual',
                 'estadisticas_adherencia_mensual', app.adherencia_tratamiento(tr.id_tratamiento, 30),
                 'efectos_secundarios_reportados', coalesce((
                   select to_jsonb(array_agg(distinct btrim(ra.sintomas_asociados)))
                   from app.registro_adherencia ra
                   where ra.id_tratamiento = tr.id_tratamiento
                     and coalesce(btrim(ra.sintomas_asociados), '') <> ''
                     and ra.fecha_hora_programada >= now() - interval '30 days'), '[]'::jsonb),
                 'ultima_toma_confirmada', (
                   select app.iso_local(max(coalesce(ra.fecha_hora_real, ra.fecha_hora_programada)))
                   from app.registro_adherencia ra
                   where ra.id_tratamiento = tr.id_tratamiento and ra.estado in ('tomado', 'tomado_tarde')),
                 'estado_toma_hoy', app.estado_toma_hoy(tr.id_tratamiento)
               ) order by tr.medicamento)
        from app.tratamientos tr
        where tr.id_paciente = p.id_paciente
          and (tr.fecha_fin is null or tr.fecha_fin >= current_date)), '[]'::jsonb)
    ),

    -- Solo hay metricas vitales capturadas en la app (peso, talla, presion).
    -- El resto de la forma se manda completa con null para que el panel no
    -- tenga que comprobar cada objeto intermedio.
    'telemetria_reciente', case when m.id_paciente is null then null else jsonb_build_object(
      'id_registro', 'tel-' || p.id_paciente,
      'id_paciente', p.id_paciente,
      'fecha', (m.fecha_toma_metricas at time zone app.config_texto('zona_horaria_default'))::date,
      'signos_vitales_cardiovasculares', jsonb_build_object(
        'presion_sistolica_matutina', case when m.ultima_presion_arterial ~ '^\s*\d{2,3}\s*/\s*\d{2,3}\s*$'
                                           then btrim(split_part(m.ultima_presion_arterial, '/', 1))::int end,
        'presion_diastolica_matutina', case when m.ultima_presion_arterial ~ '^\s*\d{2,3}\s*/\s*\d{2,3}\s*$'
                                            then btrim(split_part(m.ultima_presion_arterial, '/', 2))::int end,
        'frecuencia_cardiaca_reposo', null,
        'variabilidad_frecuencia_cardiaca_ms', null,
        'oximetria_spo2_porcentaje', null
      ),
      'metabolismo', jsonb_build_object('glucosa_ayuno_mg_dl', null, 'peso_diario_kg', m.peso_kg),
      'monitoreo_sueno_avanzado', jsonb_build_object(
        'horas_sueno_total', null,
        'fases', jsonb_build_object('sueno_profundo_minutos', null, 'sueno_rem_minutos', null, 'sueno_ligero_minutos', null),
        'interrupciones', null,
        'calidad_percibida_1_a_5', null
      ),
      'sintomatologia_geriatrica_diaria', jsonb_build_object(
        'nivel_dolor_escala_visual', null,
        'ubicacion_dolor', null,
        'episodios_confusion', null,
        'episodios_incontinencia', null,
        'mareo_ortostatico', null,
        'estado_animo', null
      )
    ) end
  )
  from app.pacientes p
  left join app.datos_personales_paciente dp on dp.id_paciente = p.id_paciente
  left join app.identificaciones_paciente ident on ident.id_paciente = p.id_paciente
  left join app.perfil_emergencia_reducido per on per.id_paciente = p.id_paciente
  left join app.metricas_vitales_actuales m on m.id_paciente = p.id_paciente
  left join app.historial_clinico hc on hc.id_paciente = p.id_paciente
  left join lateral (
    select d.id_tarjeta_rfid from app.dispositivos_rfid d
    where d.id_paciente = p.id_paciente and d.estado = 'activa'
    order by d.fecha_asignacion desc limit 1
  ) rf on true
  where p.id_paciente = p_id_paciente;
$$;

create or replace function app.panel_pacientes(p_id_medico text)
returns jsonb
language sql
stable
as $$
  select coalesce(jsonb_agg(app.panel_paciente(p_id_medico, x.id_paciente) order by x.orden, x.nombre), '[]'::jsonb)
  from (
    select ca.id_paciente,
           case app.riesgo_paciente(ca.id_paciente) when 'ALTO' then 0 when 'MEDIO' then 1 else 2 end as orden,
           app.nombre_paciente_panel(ca.id_paciente) as nombre
    from app.control_accesos_medico ca
    join app.pacientes p on p.id_paciente = ca.id_paciente and p.estado_cuenta <> 'eliminada'
    where ca.id_medico = p_id_medico
      and (ca.fecha_expiracion is null or ca.fecha_expiracion >= current_date)
  ) x;
$$;

-- ---------------------------------------------------------------------------
-- Alertas: GET /admin/alertas y PATCH /alerts/{id}
-- ---------------------------------------------------------------------------

-- NUEVA -> pendiente, EN_REVISION -> en_revision, ATENDIDA/DESCARTADA -> resuelto.
create or replace function app.estado_atencion(p_estado text)
returns text
language sql
immutable
as $$
  select case p_estado when 'NUEVA' then 'pendiente' when 'EN_REVISION' then 'en_revision' else 'resuelto' end;
$$;

-- Reglas fijas por tipo de alerta, no un modelo de IA: el texto lo dice asi
-- para no presentarlo como algo que no es.
create or replace function app.sugerencia_alerta(p_tipo text, p_prioridad text, p_titulo text)
returns text
language sql
immutable
as $$
  select case
    when p_tipo = 'SINTOMA_DE_ALARMA' and p_prioridad = 'ALTA' then
      'URGENTE: el paciente reportó un síntoma de alarma en su diario. Contactarlo de inmediato y valorar si requiere atención presencial.'
    when p_tipo = 'SINTOMA_DE_ALARMA' then
      'Revisar la entrada del diario y darle seguimiento; si el síntoma persiste, adelantar la consulta.'
    when p_tipo = 'RIESGO_ADHERENCIA' and p_prioridad = 'ALTA' then
      'Contactar al paciente hoy: está omitiendo la mayoría de sus tomas. Identificar la causa (efectos adversos, costo, olvido) y ajustar el plan.'
    when p_tipo = 'RIESGO_ADHERENCIA' then
      'La adherencia va a la baja. Reforzar la importancia del tratamiento en el próximo contacto.'
    when p_tipo = 'INVENTARIO_BAJO' then
      'Renovar la receta de ' || regexp_replace(p_titulo, '^Se (está terminando|terminó) ', '') || ' antes de que el paciente se quede sin medicamento.'
    else 'Revisar el caso.'
  end;
$$;

create or replace function app.panel_alerta(p_id_alerta text)
returns jsonb
language sql
stable
as $$
  select jsonb_build_object(
    'id_alerta', al.id_alerta,
    'id_paciente', al.id_paciente,
    'nombre_paciente', app.nombre_paciente_panel(al.id_paciente),
    'edad', extract(year from age(dp.fecha_nacimiento))::int,
    'diagnostico_principal', app.diagnostico_principal(al.id_paciente),
    'nivel_riesgo', case al.prioridad when 'ALTA' then 'Rojo' when 'MEDIA' then 'Amarillo' else 'Verde' end,
    'fecha_deteccion', app.iso_local(al.creado_en),
    'vectores_de_prediccion', jsonb_build_array(al.titulo, al.detalle),
    'sugerencia_ia_clinica', app.sugerencia_alerta(al.tipo, al.prioridad, al.titulo),
    'estado_atencion', app.estado_atencion(al.estado)
  )
  from app.alertas al
  left join app.datos_personales_paciente dp on dp.id_paciente = al.id_paciente
  where al.id_alerta = p_id_alerta;
$$;

create or replace function app.panel_alertas(p_id_medico text)
returns jsonb
language sql
stable
as $$
  select coalesce(jsonb_agg(app.panel_alerta(x.id_alerta) order by x.abierta desc, x.orden, x.creado_en desc), '[]'::jsonb)
  from (
    select al.id_alerta, al.creado_en,
           al.estado in ('NUEVA', 'EN_REVISION') as abierta,
           case al.prioridad when 'ALTA' then 0 when 'MEDIA' then 1 else 2 end as orden
    from app.alertas al
    join app.pacientes p on p.id_paciente = al.id_paciente and p.estado_cuenta <> 'eliminada'
    where al.id_medico = p_id_medico
      and app.vinculo_vigente(p_id_medico, al.id_paciente)
    order by abierta desc, orden, al.creado_en desc
    limit 500
  ) x;
$$;

-- ---------------------------------------------------------------------------
-- GET /admin/auditoria
-- ---------------------------------------------------------------------------

-- Resets hechos por este medico o sobre sus pacientes (si otro medico reseteo
-- a un paciente comun, este tambien debe verlo).
create or replace function app.panel_auditoria(p_id_medico text)
returns jsonb
language sql
stable
as $$
  select coalesce(jsonb_agg(x.fila order by x.creado_en desc), '[]'::jsonb)
  from (
    select ar.creado_en,
           jsonb_build_object(
             'id', ar.id_auditoria,
             'tipo', 'reset_cuenta',
             'id_paciente', ar.id_paciente,
             'nombre_paciente', app.nombre_paciente_panel(ar.id_paciente),
             'realizado_por', app.nombre_completo_medico(m.tratamiento, m.nombre, m.apellidos),
             'fecha', app.iso_local(ar.creado_en),
             'motivo', ar.motivo
           ) as fila
    from app.auditoria_reset_cuenta ar
    join app.medicos m on m.id_medico = ar.id_medico
    where ar.id_medico = p_id_medico or app.vinculo_vigente(p_id_medico, ar.id_paciente)
    order by ar.creado_en desc
    limit 500
  ) x;
$$;

-- ---------------------------------------------------------------------------
-- Chat (camelCase, como la app movil)
-- ---------------------------------------------------------------------------

-- Conversacion del medico con un paciente vinculado; null si no.
create or replace function app.panel_conversacion_permitida(p_id_medico text, p_id_conversacion text)
returns boolean
language sql
stable
as $$
  select exists (
    select 1 from app.conversaciones c
    join app.pacientes p on p.id_paciente = c.id_paciente and p.estado_cuenta <> 'eliminada'
    where c.id_conversacion = p_id_conversacion
      and c.id_medico = p_id_medico
      and app.vinculo_vigente(p_id_medico, c.id_paciente)
  );
$$;

create or replace function app.panel_conversaciones(p_id_medico text)
returns jsonb
language sql
stable
as $$
  select coalesce(jsonb_agg(x.fila order by x.orden desc), '[]'::jsonb)
  from (
    select coalesce(ult.instante, c.creado_en) as orden,
           jsonb_build_object(
             'id', c.id_conversacion,
             'idPaciente', c.id_paciente,
             'nombrePaciente', app.nombre_paciente_panel(c.id_paciente),
             'iniciales', coalesce(nullif(upper(left(coalesce(dp.nombre, ''), 1) || left(coalesce(dp.apellidos, ''), 1)), ''), '?'),
             'diagnosticoPrincipal', app.diagnostico_principal(c.id_paciente),
             'ultimoMensaje', ult.vista,
             'ultimoMensajeTimestamp', app.iso_utc_ms(coalesce(ult.instante, c.creado_en)),
             'noLeidos', (
               select count(*) from app.mensajes m
               where m.id_conversacion = c.id_conversacion
                 and m.autor = 'PACIENTE' and m.instante > c.leido_por_medico_hasta),
             -- pendiente = el ultimo mensaje es del paciente y espera respuesta.
             'estado', case when ult.autor = 'PACIENTE' then 'pendiente' else 'activa' end,
             'medicoEscribiendo', false
           ) as fila
    from app.conversaciones c
    join app.pacientes p on p.id_paciente = c.id_paciente and p.estado_cuenta <> 'eliminada'
    left join app.datos_personales_paciente dp on dp.id_paciente = c.id_paciente
    left join lateral (
      select m.autor, m.instante,
             case when coalesce(btrim(m.texto), '') = '' and a.nombre is not null then 'Adjunto: ' || a.nombre
                  else nullif(m.texto, '') end as vista
      from app.mensajes m
      left join app.adjuntos a on a.id_adjunto = m.id_adjunto
      where m.id_conversacion = c.id_conversacion
      order by m.instante desc
      limit 1
    ) ult on true
    where c.id_medico = p_id_medico
      and app.vinculo_vigente(p_id_medico, c.id_paciente)
  ) x;
$$;

-- `adjunto.idAdjunto` es interno: Axum lo cambia por `urlPreview` (un enlace
-- firmado que sirve en un <img>) antes de responder.
create or replace function app.panel_mensaje(p_id_mensaje text)
returns jsonb
language sql
stable
as $$
  select jsonb_build_object(
           'id', m.id_mensaje,
           'idConversacion', m.id_conversacion,
           'autor', lower(m.autor),
           'texto', m.texto,
           'timestamp', app.iso_utc_ms(m.instante),
           'leido', case m.autor when 'PACIENTE' then m.instante <= c.leido_por_medico_hasta
                                 else m.instante <= c.leido_por_paciente_hasta end
         )
         || case when a.id_adjunto is null then '{}'::jsonb else jsonb_build_object(
              'adjunto', jsonb_build_object('idAdjunto', a.id_adjunto, 'nombre', a.nombre, 'tipo', lower(a.tipo)))
            end
  from app.mensajes m
  join app.conversaciones c on c.id_conversacion = m.id_conversacion
  left join app.adjuntos a on a.id_adjunto = m.id_adjunto
  where m.id_mensaje = p_id_mensaje;
$$;

create or replace function app.panel_mensajes(p_id_conversacion text)
returns jsonb
language sql
stable
as $$
  select coalesce(jsonb_agg(app.panel_mensaje(x.id_mensaje) order by x.instante), '[]'::jsonb)
  from (
    select m.id_mensaje, m.instante
    from app.mensajes m
    where m.id_conversacion = p_id_conversacion
    order by m.instante desc
    limit 500
  ) x;
$$;

-- Mismo evento que api.enviar_mensaje (0013): la app movil lo recibe por el
-- WebSocket igual que si el medico escribiera desde su telefono.
create or replace function app.panel_enviar_mensaje(p_id_conversacion text, p_texto text)
returns text
language plpgsql
as $$
declare
  v_id_mensaje text;
begin
  insert into app.mensajes (id_conversacion, autor, texto)
  values (p_id_conversacion, 'MEDICO', p_texto)
  returning id_mensaje into v_id_mensaje;

  perform app.emitir_evento(
    'chat:' || p_id_conversacion,
    'mensaje_nuevo',
    jsonb_build_object('idMensaje', v_id_mensaje, 'autor', 'MEDICO', 'texto', p_texto)
  );
  return v_id_mensaje;
end;
$$;

revoke all on function app.iso_local(timestamptz) from public;
revoke all on function app.iso_utc_ms(timestamptz) from public;
revoke all on function app.hoy_local() from public;
revoke all on function app.semaforo(text) from public;
revoke all on function app.diagnostico_principal(text) from public;
revoke all on function app.nombre_paciente_panel(text) from public;
revoke all on function app.adherencia_tratamiento(text, integer) from public;
revoke all on function app.estado_toma_hoy(text) from public;
revoke all on function app.panel_kpis(text) from public;
revoke all on function app.panel_paciente(text, text) from public;
revoke all on function app.panel_pacientes(text) from public;
revoke all on function app.estado_atencion(text) from public;
revoke all on function app.sugerencia_alerta(text, text, text) from public;
revoke all on function app.panel_alerta(text) from public;
revoke all on function app.panel_alertas(text) from public;
revoke all on function app.panel_auditoria(text) from public;
revoke all on function app.panel_conversacion_permitida(text, text) from public;
revoke all on function app.panel_conversaciones(text) from public;
revoke all on function app.panel_mensaje(text) from public;
revoke all on function app.panel_mensajes(text) from public;
revoke all on function app.panel_enviar_mensaje(text, text) from public;

notify pgrst, 'reload schema';
