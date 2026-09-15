-- Contador de mensajes sin leer, para el globo de la barra inferior y la
-- bandeja de conversaciones.
--
-- El cliente esperaba recibir `{"noLeidos": N}` en cada evento del canal
-- `chat:{id}`, pero el backend emite `mensaje_nuevo` con el mensaje, no un
-- conteo -- y el conteo ademas depende de QUIEN mira (lo no leido del paciente
-- son los mensajes del medico, y viceversa), asi que no cabe en un evento que
-- reciben los dos. Se resuelve como el resto de los datos en vivo: el evento
-- avisa que algo cambio y el cliente vuelve a preguntar esto.
create or replace function api.mensajes_sin_leer(id_conversacion text)
returns integer
language sql
stable
security invoker
as $$
  select count(*)::int
  from app.mensajes m
  join app.conversaciones c on c.id_conversacion = m.id_conversacion
  where m.id_conversacion = mensajes_sin_leer.id_conversacion
    and (
      (app.es_dueno_paciente(c.id_paciente)
        and m.autor = 'MEDICO' and m.instante > c.leido_por_paciente_hasta)
      or (c.id_medico = current_setting('request.jwt.claims', true)::json->>'sub'
        and m.autor = 'PACIENTE' and m.instante > c.leido_por_medico_hasta)
    );
$$;
grant execute on function api.mensajes_sin_leer(text) to paciente, medico;

notify pgrst, 'reload schema';
