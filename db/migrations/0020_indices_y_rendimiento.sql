-- Índices que faltaban y ajustes de rendimiento.
--
-- Postgres no indexa solo las llaves foráneas. Sin índice, cada consulta "los
-- pacientes de este médico" recorre la tabla completa, y borrar un médico o un
-- paciente (on delete cascade) bloquea mientras la recorre.

-- Casi todo el portal del médico y el panel filtran por médico
-- (app.vinculo_vigente, sincronizar_alertas, cartera de pacientes). La llave
-- primaria (id_paciente, id_medico) no sirve para buscar solo por médico.
create index if not exists idx_control_accesos_medico_medico
  on app.control_accesos_medico (id_medico, id_paciente);

-- Bandeja de conversaciones del médico (app y panel).
create index if not exists idx_conversaciones_medico
  on app.conversaciones (id_medico);

-- Agenda del paciente.
create index if not exists idx_citas_paciente
  on app.citas (id_paciente);

-- Cascada al borrar un paciente y alertas por paciente en el panel.
create index if not exists idx_alertas_paciente
  on app.alertas (id_paciente);

create index if not exists idx_tratamientos_medico_receta
  on app.tratamientos (id_medico_receta);

create index if not exists idx_solicitudes_recuperacion_evento
  on app.solicitudes_recuperacion (id_evento)
  where id_evento is not null;

-- sincronizar_alertas (0019) busca en cada llamada los mensajes del paciente
-- con señal de alarma de la última semana: índice parcial solo con esos.
create index if not exists idx_mensajes_con_riesgo
  on app.mensajes (id_conversacion, instante desc)
  where autor = 'PACIENTE' and analisis_riesgo->>'nivel' in ('ROJO', 'AMBAR');

-- Igual para el diario: solo las entradas que pueden generar alerta.
create index if not exists idx_entradas_diario_con_riesgo
  on app.entradas_diario (id_paciente, instante desc)
  where severidad in ('ROJO', 'AMBAR') or severidad_servidor in ('ROJO', 'AMBAR');

-- Estadísticas al día para que el planificador use los índices nuevos.
analyze app.control_accesos_medico;
analyze app.conversaciones;
analyze app.citas;
analyze app.alertas;
analyze app.tratamientos;
analyze app.mensajes;
analyze app.entradas_diario;
