-- Análisis de riesgo del diario y del chat del paciente.
--
-- Hasta aquí la severidad del diario la calculaba solo la app (0010) y el
-- backend la guardaba tal cual. Ahora el servidor también lee cada entrada y
-- cada mensaje del paciente contra una base de conocimiento de señales de
-- alarma (recuperación por palabras clave, con negaciones y signos vitales
-- escritos con número) y decide si el paciente puede estar en peligro.
--
--  - La app sigue calculando su color al escribir (funciona sin red). El
--    servidor guarda el suyo aparte (`severidad_servidor`) y para las alertas
--    manda el más grave de los dos: un diccionario viejo en un teléfono sin
--    actualizar ya no deja pasar un síntoma de alarma.
--  - Se calcula UNA vez, al insertar, y se guarda con la versión de la base
--    de conocimiento: igual que en 0010, una entrada vieja no cambia de color
--    sola si mañana se agregan términos.
--  - Lo recuperado (qué puede indicar, qué hacer) es el contexto que el
--    endpoint de Axum `POST /riesgo/analizar` le pasa a DeepSeek para
--    explicarlo en lenguaje natural (RAG). Sin IA configurada, el análisis
--    por reglas funciona igual.
--
-- No es un diagnóstico: es un orden de lectura para el médico.

-- ---------------------------------------------------------------------------
-- 1. Base de conocimiento
-- ---------------------------------------------------------------------------
create table app.base_conocimiento_riesgo (
  clave             text primary key,
  categoria         text not null check (categoria in
                      ('URGENCIA_FISICA', 'URGENCIA_MENTAL', 'PRECAUCION_FISICA', 'PRECAUCION_MENTAL')),
  termino           text not null,
  -- Frases ya normalizadas (minúsculas, sin acentos, sin signos).
  sinonimos         text[] not null,
  que_puede_indicar text not null,
  que_hacer         text not null
);
alter table app.base_conocimiento_riesgo enable row level security;

insert into app.base_conocimiento_riesgo (clave, categoria, termino, sinonimos, que_puede_indicar, que_hacer) values
-- Urgencias físicas
('dolor_pecho', 'URGENCIA_FISICA', 'Dolor u opresión en el pecho',
 array['dolor en el pecho', 'dolor de pecho', 'dolor toracico', 'opresion en el pecho', 'presion en el pecho',
       'me aprieta el pecho', 'me duele el pecho', 'duele el pecho', 'dolor en el brazo izquierdo'],
 'Puede ser un problema del corazón (angina o infarto), sobre todo en pacientes con diabetes o hipertensión.',
 'Llamar al 911 o acudir a urgencias de inmediato; no esperar a la consulta.'),
('falta_aire', 'URGENCIA_FISICA', 'Falta de aire',
 array['no puedo respirar', 'falta de aire', 'me falta el aire', 'me ahogo', 'ahogo', 'asfixia',
       'dificultad para respirar', 'respiro con dificultad', 'labios morados'],
 'Insuficiencia respiratoria, crisis de asma, edema pulmonar o problema cardiaco.',
 'Acudir a urgencias de inmediato.'),
('desmayo', 'URGENCIA_FISICA', 'Desmayo o pérdida del conocimiento',
 array['me desmaye', 'desmaye', 'desmayo', 'desmayos', 'perdi el conocimiento', 'perdi la conciencia', 'me desvaneci'],
 'Arritmia, hipoglucemia, presión muy baja o un problema neurológico.',
 'Valoración urgente el mismo día; si se repite o hay dolor de pecho, llamar al 911.'),
('evento_cerebral', 'URGENCIA_FISICA', 'Signos de embolia (evento vascular cerebral)',
 array['habla arrastrada', 'no puedo hablar bien', 'se me durmio la cara', 'cara chueca', 'boca chueca',
       'no puedo mover el brazo', 'no puedo mover la pierna', 'se me durmio medio cuerpo', 'adormecido', 'adormecida',
       'perdi la vista', 'vision borrosa de repente', 'vision borrosa'],
 'Posible evento vascular cerebral: cada minuto cuenta.',
 'Llamar al 911 de inmediato y anotar la hora en que empezaron los síntomas.'),
('convulsion', 'URGENCIA_FISICA', 'Convulsión',
 array['convulsion', 'convulsiones', 'convulsione', 'ataque epileptico'],
 'Crisis convulsiva por epilepsia, hipoglucemia, fiebre o lesión cerebral.',
 'Llamar al 911; no meter nada en la boca y proteger la cabeza.'),
('sangrado', 'URGENCIA_FISICA', 'Sangrado',
 array['vomito con sangre', 'vomite sangre', 'sangre en el vomito', 'heces negras', 'popo negra', 'excremento negro',
       'sangre en la orina', 'sangre en las heces', 'tosi sangre', 'escupo sangre', 'hemorragia',
       'sangrado abundante', 'sangrado', 'sangra mucho'],
 'Pérdida de sangre importante (digestiva, urinaria o respiratoria).',
 'Acudir a urgencias de inmediato.'),
('hipoglucemia', 'URGENCIA_FISICA', 'Azúcar muy baja',
 array['se me bajo el azucar', 'azucar muy baja', 'azucar baja', 'glucosa baja', 'sudor frio', 'hipoglucemia'],
 'Hipoglucemia: puede causar desmayo o convulsiones.',
 'Tomar 15 g de azúcar (medio vaso de jugo o refresco normal), medir otra vez en 15 minutos y avisar al médico; si no mejora o hay confusión, llamar al 911.'),
('confusion', 'URGENCIA_FISICA', 'Confusión',
 array['confundido', 'confundida', 'desorientado', 'desorientada', 'no se donde estoy', 'no reconozco'],
 'Hipoglucemia, infección grave, deshidratación o un problema neurológico.',
 'Valoración urgente; si aparece de golpe, llamar al 911.'),
('dolor_insoportable', 'URGENCIA_FISICA', 'Dolor súbito e insoportable',
 array['el peor dolor de mi vida', 'peor dolor de cabeza', 'dolor insoportable', 'dolor muy fuerte en el estomago',
       'dolor muy fuerte'],
 'Un dolor súbito e intenso puede ser una urgencia (apendicitis, aneurisma, hemorragia).',
 'Acudir a urgencias.'),
('alergia_grave', 'URGENCIA_FISICA', 'Reacción alérgica grave',
 array['se me cierra la garganta', 'se me cerro la garganta', 'se me hincho la cara', 'se me hincharon los labios',
       'se me hincho la lengua', 'reaccion alergica fuerte'],
 'Anafilaxia: puede cerrar la vía respiratoria en minutos.',
 'Llamar al 911 de inmediato.'),
-- Urgencias de salud mental
('ideacion_suicida', 'URGENCIA_MENTAL', 'Riesgo para la propia vida',
 array['quitarme la vida', 'ya no quiero vivir', 'no quiero vivir', 'me quiero morir', 'quiero morirme', 'quiero morir',
       'suicidarme', 'suicidio', 'matarme', 'hacerme dano', 'autolesionarme', 'autolesion', 'cortarme',
       'mejor muerto', 'mejor muerta', 'acabar con todo', 'no vale la pena vivir'],
 'Ideas de hacerse daño o de quitarse la vida.',
 'Contactar al paciente hoy mismo. Línea de la Vida 800 911 2000 (gratuita, 24 horas) o 911 si hay peligro inmediato.'),
-- Precaución física
('fiebre', 'PRECAUCION_FISICA', 'Fiebre',
 array['fiebre', 'calentura', 'temperatura alta', 'escalofrios'],
 'Infección en curso.',
 'Vigilar la temperatura; si pasa de 39 °C, dura más de 3 días o hay otros síntomas, consulta pronto.'),
('vomito', 'PRECAUCION_FISICA', 'Vómito o náuseas',
 array['vomito', 'vomitos', 'vomitando', 'vomite', 'nauseas', 'nausea', 'ganas de vomitar'],
 'Infección digestiva, efecto de un medicamento o descontrol de la glucosa; riesgo de deshidratación.',
 'Hidratarse en sorbos pequeños; si no tolera líquidos o no puede tomar su medicamento, avisar al médico.'),
('mareo', 'PRECAUCION_FISICA', 'Mareo',
 array['mareo', 'mareos', 'mareado', 'mareada', 'vertigo', 'se me mueve todo'],
 'Presión baja o alta, glucosa baja, deshidratación o efecto de un medicamento.',
 'Sentarse, medir presión y glucosa si tiene aparato y avisar al médico si se repite.'),
('presion_alta', 'PRECAUCION_FISICA', 'Síntomas de presión alta',
 array['presion alta', 'se me subio la presion', 'zumbido en los oidos', 'veo lucecitas', 'veo lucesitas'],
 'Posible descontrol de la presión arterial.',
 'Medir la presión; si es de 180/120 o más, o hay dolor de pecho o de cabeza intenso, acudir a urgencias.'),
('azucar_alta', 'PRECAUCION_FISICA', 'Síntomas de azúcar alta',
 array['azucar alta', 'glucosa alta', 'se me subio el azucar', 'mucha sed', 'orino mucho', 'boca seca'],
 'Hiperglucemia (descontrol de la diabetes).',
 'Medir la glucosa; si pasa de 250 mg/dL en dos mediciones, avisar al médico hoy.'),
('palpitaciones', 'PRECAUCION_FISICA', 'Palpitaciones',
 array['palpitaciones', 'corazon acelerado', 'taquicardia', 'latidos rapidos', 'el corazon me late muy rapido'],
 'Arritmia, ansiedad, anemia o efecto de un medicamento.',
 'Medir el pulso; si hay mareo, desmayo o dolor de pecho, acudir a urgencias.'),
('hinchazon', 'PRECAUCION_FISICA', 'Hinchazón',
 array['pies hinchados', 'piernas hinchadas', 'tobillos hinchados', 'hinchazon', 'hinchados', 'hinchadas'],
 'Retención de líquidos (corazón o riñón) o efecto de un medicamento.',
 'Revisar en la próxima consulta; si aparece con falta de aire, acudir a urgencias.'),
('dolor_fuerte', 'PRECAUCION_FISICA', 'Dolor fuerte',
 array['dolor fuerte', 'mucho dolor', 'me duele mucho'],
 'Dolor que requiere valoración.',
 'Consulta pronto; si es súbito e insoportable, acudir a urgencias.'),
('dolor_abdominal', 'PRECAUCION_FISICA', 'Dolor de estómago',
 array['dolor de estomago', 'me duele el estomago', 'duele el estomago', 'dolor abdominal', 'dolor de panza',
       'me duele la panza', 'colico', 'colicos'],
 'Gastritis, infección, efecto de un medicamento (por ejemplo metformina) u otro problema abdominal.',
 'Dar seguimiento; si el dolor es intenso, hay fiebre, vómito o sangre, acudir a urgencias.'),
('dolor_cabeza', 'PRECAUCION_FISICA', 'Dolor de cabeza',
 array['dolor de cabeza', 'me duele la cabeza', 'duele la cabeza', 'migrana', 'jaqueca'],
 'Tensión, presión alta o migraña.',
 'Medir la presión; si es el peor dolor de su vida o viene con visión borrosa, acudir a urgencias.'),
('diarrea', 'PRECAUCION_FISICA', 'Diarrea',
 array['diarrea', 'evacuaciones liquidas'],
 'Infección digestiva o efecto de un medicamento; riesgo de deshidratación.',
 'Hidratarse con suero oral; si dura más de 2 días o hay sangre, consultar.'),
('debilidad', 'PRECAUCION_FISICA', 'Debilidad o cansancio',
 array['debilidad', 'cansancio extremo', 'muy cansado', 'muy cansada', 'sin fuerzas', 'agotado', 'agotada'],
 'Anemia, descontrol de la glucosa, infección o efecto de un medicamento.',
 'Revisar en la próxima consulta; avisar antes si empeora.'),
('herida_pie', 'PRECAUCION_FISICA', 'Herida que no sana',
 array['herida en el pie', 'llaga en el pie', 'ulcera', 'no cicatriza', 'no me sana la herida'],
 'En diabetes, una herida que no sana puede infectarse gravemente.',
 'Revisión presencial en los próximos días; no caminar descalzo.'),
('efecto_adverso', 'PRECAUCION_FISICA', 'Posible reacción a un medicamento',
 array['me cayo mal la medicina', 'me cayo mal el medicamento', 'reaccion al medicamento', 'ronchas', 'comezon', 'salpullido'],
 'Efecto adverso o alergia a un medicamento.',
 'Avisar al médico antes de la siguiente dosis; si hay hinchazón de cara o dificultad para respirar, llamar al 911.'),
('tos', 'PRECAUCION_FISICA', 'Tos',
 array['tos con flema', 'tos persistente', 'tos'],
 'Infección respiratoria o efecto de un medicamento (por ejemplo enalapril).',
 'Si dura más de 2 semanas o hay fiebre o falta de aire, consultar.'),
('abandono_tratamiento', 'PRECAUCION_FISICA', 'Dejó de tomar su tratamiento',
 array['deje de tomar', 'no me tome', 'no me he tomado', 'se me olvido tomar', 'se me acabo la medicina',
       'se me acabo el medicamento', 'ya no tengo medicina', 'no tengo para la medicina'],
 'Interrupción del tratamiento.',
 'Contactar al paciente para conocer la causa (efectos adversos, costo, olvido) y renovar la receta si hace falta.'),
-- Precaución de salud mental
('ansiedad', 'PRECAUCION_MENTAL', 'Ansiedad',
 array['ataques de panico', 'ataque de panico', 'panico', 'ansiedad', 'angustia', 'muy nervioso', 'muy nerviosa'],
 'Trastorno de ansiedad o síntoma de otro problema (por ejemplo del corazón o la tiroides).',
 'Preguntar en la próxima consulta; valorar apoyo psicológico.'),
('animo_bajo', 'PRECAUCION_MENTAL', 'Ánimo muy bajo',
 array['tristeza profunda', 'muy triste', 'deprimido', 'deprimida', 'depresion', 'sin ganas de nada',
       'lloro todo el dia', 'ya no puedo mas', 'desesperacion', 'sin esperanza'],
 'Posible depresión; aumenta el riesgo de abandonar el tratamiento.',
 'Contactar al paciente y valorar apoyo psicológico; preguntar directamente por ideas de hacerse daño.'),
('insomnio', 'PRECAUCION_MENTAL', 'No puede dormir',
 array['insomnio', 'no puedo dormir', 'no duermo', 'no he dormido'],
 'Ansiedad, depresión, dolor o efecto de un medicamento.',
 'Revisar en la próxima consulta.')
on conflict (clave) do update set
  categoria = excluded.categoria, termino = excluded.termino, sinonimos = excluded.sinonimos,
  que_puede_indicar = excluded.que_puede_indicar, que_hacer = excluded.que_hacer;

-- ---------------------------------------------------------------------------
-- 2. Motor de análisis
-- ---------------------------------------------------------------------------

-- " me duele el estomago " : minúsculas, sin acentos ni signos, con un espacio
-- a cada lado para buscar por palabra completa (" tos " no está en " estos ").
create or replace function app.normalizar_texto_clinico(p_texto text)
returns text
language sql
immutable
as $$
  select ' ' || btrim(regexp_replace(
           translate(lower(coalesce(p_texto, '')), 'áéíóúüñàèìòù', 'aeiouunaeiou'),
           '[^a-z0-9]+', ' ', 'g')) || ' ';
$$;

-- ¿Alguna de las tres palabras previas niega el término? "no tengo fiebre",
-- "sin dolor de pecho", "ya no me duele el estomago". Una conjunción corta la
-- negación: en "no tengo fiebre pero me duele el pecho" el pecho sí cuenta.
create or replace function app.negado_antes_de(p_normalizado text, p_posicion integer)
returns boolean
language plpgsql
immutable
as $$
declare
  v_palabras text[] := regexp_split_to_array(btrim(left(p_normalizado, p_posicion)), '\s+');
  v_total integer := coalesce(array_length(v_palabras, 1), 0);
  i integer;
begin
  for i in reverse v_total .. greatest(v_total - 2, 1) loop
    if v_palabras[i] in ('pero', 'aunque', 'sino', 'y', 'e', 'ahora', 'hoy') then
      return false;
    end if;
    if v_palabras[i] in ('no', 'sin', 'nunca', 'ni', 'tampoco', 'jamas', 'ningun', 'ninguna', 'niego') then
      return true;
    end if;
  end loop;
  return false;
end;
$$;

-- Signos vitales escritos con número (el dictado de la app ya los deja así:
-- "38.5 °C", "88 %", "180/110"). Umbrales de uso general en triage.
create or replace function app.signos_vitales_de_riesgo(p_texto text)
returns jsonb
language plpgsql
immutable
as $$
declare
  v_t text := replace(translate(lower(coalesce(p_texto, '')), 'áéíóúüñ', 'aeiouun'), ',', '.');
  v_hallazgos jsonb := '[]'::jsonb;
  m text[];
  v numeric;
  v2 numeric;
begin
  -- Temperatura
  for m in select regexp_matches(v_t, '(?:fiebre|temperatura|calentura)[^0-9]{0,20}([0-9]{2}(?:\.[0-9])?)|([0-9]{2}(?:\.[0-9])?)\s*(?:°|grados)', 'g') loop
    v := coalesce(m[1], m[2])::numeric;
    continue when v < 30 or v > 45;
    if v >= 40 then
      v_hallazgos := v_hallazgos || jsonb_build_object('clave', 'temperatura', 'categoria', 'URGENCIA_FISICA',
        'termino', format('Fiebre muy alta (%s °C)', v), 'frase_detectada', v::text,
        'que_puede_indicar', 'Infección grave; riesgo de deshidratación y convulsiones.',
        'que_hacer', 'Bajar la temperatura con paracetamol y medios físicos y acudir a urgencias.');
    elsif v >= 38 then
      v_hallazgos := v_hallazgos || jsonb_build_object('clave', 'temperatura', 'categoria', 'PRECAUCION_FISICA',
        'termino', format('Fiebre (%s °C)', v), 'frase_detectada', v::text,
        'que_puede_indicar', 'Infección en curso.',
        'que_hacer', 'Vigilar la temperatura; si pasa de 39 °C o dura más de 3 días, consulta pronto.');
    elsif v <= 35 then
      v_hallazgos := v_hallazgos || jsonb_build_object('clave', 'temperatura', 'categoria', 'URGENCIA_FISICA',
        'termino', format('Temperatura muy baja (%s °C)', v), 'frase_detectada', v::text,
        'que_puede_indicar', 'Hipotermia o infección grave.',
        'que_hacer', 'Acudir a urgencias.');
    end if;
    exit;
  end loop;

  -- Saturación de oxígeno
  for m in select regexp_matches(v_t, '(?:saturacion|oxigenacion|oxigeno|spo2|oximetro)[^0-9]{0,20}([0-9]{2,3})', 'g') loop
    v := m[1]::numeric;
    continue when v < 50 or v > 100;
    if v < 90 then
      v_hallazgos := v_hallazgos || jsonb_build_object('clave', 'saturacion', 'categoria', 'URGENCIA_FISICA',
        'termino', format('Oxigenación baja (%s %%)', v), 'frase_detectada', v::text,
        'que_puede_indicar', 'Falta de oxígeno en la sangre.',
        'que_hacer', 'Acudir a urgencias de inmediato.');
    elsif v < 94 then
      v_hallazgos := v_hallazgos || jsonb_build_object('clave', 'saturacion', 'categoria', 'PRECAUCION_FISICA',
        'termino', format('Oxigenación limítrofe (%s %%)', v), 'frase_detectada', v::text,
        'que_puede_indicar', 'Oxigenación por debajo de lo normal.',
        'que_hacer', 'Repetir la medición en reposo; si baja de 90 % o hay falta de aire, acudir a urgencias.');
    end if;
    exit;
  end loop;

  -- Presión arterial (solo si el texto habla de presión: "3/4 de pastilla" no es una presión)
  if v_t ~ '(presion|tension|mmhg|mm hg)' then
    for m in select regexp_matches(v_t, '([0-9]{2,3})\s*/\s*([0-9]{2,3})', 'g') loop
      v := m[1]::numeric;
      v2 := m[2]::numeric;
      continue when v < 60 or v > 260 or v2 < 30 or v2 > 160;
      if v >= 180 or v2 >= 120 then
        v_hallazgos := v_hallazgos || jsonb_build_object('clave', 'presion', 'categoria', 'URGENCIA_FISICA',
          'termino', format('Presión muy alta (%s/%s)', v, v2), 'frase_detectada', m[1] || '/' || m[2],
          'que_puede_indicar', 'Crisis hipertensiva: riesgo de infarto o evento cerebral.',
          'que_hacer', 'Repetir en 5 minutos en reposo; si sigue igual o hay dolor de pecho, de cabeza o visión borrosa, acudir a urgencias.');
      elsif v >= 160 or v2 >= 100 then
        v_hallazgos := v_hallazgos || jsonb_build_object('clave', 'presion', 'categoria', 'PRECAUCION_FISICA',
          'termino', format('Presión alta (%s/%s)', v, v2), 'frase_detectada', m[1] || '/' || m[2],
          'que_puede_indicar', 'Hipertensión descontrolada.',
          'que_hacer', 'Revisar el tratamiento pronto; confirmar que toma su medicamento.');
      elsif v < 90 then
        v_hallazgos := v_hallazgos || jsonb_build_object('clave', 'presion', 'categoria', 'PRECAUCION_FISICA',
          'termino', format('Presión baja (%s/%s)', v, v2), 'frase_detectada', m[1] || '/' || m[2],
          'que_puede_indicar', 'Deshidratación, sangrado o exceso de medicamento para la presión.',
          'que_hacer', 'Recostarse, hidratarse y avisar al médico; si hay desmayo, acudir a urgencias.');
      end if;
      exit;
    end loop;
  end if;

  -- Glucosa
  for m in select regexp_matches(v_t, '(?:glucosa|azucar|glicemia|glucemia|dextrostix)[^0-9]{0,25}([0-9]{2,3})', 'g') loop
    v := m[1]::numeric;
    continue when v < 20 or v > 700;
    if v < 54 or v > 400 then
      v_hallazgos := v_hallazgos || jsonb_build_object('clave', 'glucosa', 'categoria', 'URGENCIA_FISICA',
        'termino', format('Glucosa peligrosa (%s mg/dL)', v), 'frase_detectada', v::text,
        'que_puede_indicar', case when v < 54 then 'Hipoglucemia grave.' else 'Hiperglucemia grave (posible cetoacidosis o estado hiperosmolar).' end,
        'que_hacer', case when v < 54
          then 'Tomar 15 g de azúcar de inmediato, medir otra vez en 15 minutos; si no mejora o hay confusión, llamar al 911.'
          else 'Acudir a urgencias.' end);
    elsif v < 70 or v > 250 then
      v_hallazgos := v_hallazgos || jsonb_build_object('clave', 'glucosa', 'categoria', 'PRECAUCION_FISICA',
        'termino', format('Glucosa fuera de rango (%s mg/dL)', v), 'frase_detectada', v::text,
        'que_puede_indicar', case when v < 70 then 'Hipoglucemia.' else 'Hiperglucemia.' end,
        'que_hacer', case when v < 70
          then 'Tomar 15 g de azúcar y volver a medir en 15 minutos.'
          else 'Volver a medir; si sigue arriba de 250 mg/dL, avisar al médico hoy.' end);
    end if;
    exit;
  end loop;

  -- Frecuencia cardiaca
  for m in select regexp_matches(v_t, '(?:pulso|frecuencia cardiaca|ritmo cardiaco)[^0-9]{0,20}([0-9]{2,3})|([0-9]{2,3})\s*(?:lpm|latidos)', 'g') loop
    v := coalesce(m[1], m[2])::numeric;
    continue when v < 20 or v > 250;
    if v > 130 or v < 40 then
      v_hallazgos := v_hallazgos || jsonb_build_object('clave', 'pulso', 'categoria', 'URGENCIA_FISICA',
        'termino', format('Pulso peligroso (%s lpm)', v), 'frase_detectada', v::text,
        'que_puede_indicar', 'Arritmia.',
        'que_hacer', 'Acudir a urgencias, sobre todo si hay mareo, desmayo o dolor de pecho.');
    elsif v > 110 or v < 50 then
      v_hallazgos := v_hallazgos || jsonb_build_object('clave', 'pulso', 'categoria', 'PRECAUCION_FISICA',
        'termino', format('Pulso fuera de rango (%s lpm)', v), 'frase_detectada', v::text,
        'que_puede_indicar', 'Taquicardia o bradicardia.',
        'que_hacer', 'Repetir en reposo y avisar al médico si se mantiene.');
    end if;
    exit;
  end loop;

  return v_hallazgos;
end;
$$;

-- Versión de las reglas; se guarda con cada análisis.
create or replace function app.version_analisis_riesgo()
returns integer
language sql
immutable
as $$ select 1 $$;

create or replace function app.analizar_texto_riesgo(p_texto text)
returns jsonb
language plpgsql
stable
security definer
set search_path = app, pg_temp
as $$
declare
  v_t text := app.normalizar_texto_clinico(p_texto);
  v_hallazgos jsonb := '[]'::jsonb;
  v_claves text[] := '{}';
  v_negados text[] := '{}';
  v_cubiertos int4range[] := '{}';
  r record;
  v_desde integer;
  v_pos integer;
  v_rango int4range;
  v_urgencias integer;
  v_precauciones integer;
  v_nivel text;
  v_mental boolean;
  v_terminos text;
  v_recomendacion text;
begin
  -- Frases largas primero: "dolor muy fuerte en el estomago" gana a "dolor fuerte",
  -- y la frase corta contenida en una ya hallada no cuenta dos veces.
  for r in
    select b.clave, b.categoria, b.termino, b.que_puede_indicar, b.que_hacer, s.sinonimo
    from app.base_conocimiento_riesgo b
    cross join lateral unnest(b.sinonimos) as s(sinonimo)
    order by length(s.sinonimo) desc, b.clave
  loop
    continue when r.clave = any(v_claves);
    v_desde := 1;
    loop
      v_pos := strpos(substr(v_t, v_desde), ' ' || r.sinonimo || ' ');
      exit when v_pos = 0;
      v_pos := v_pos + v_desde - 1;
      v_rango := int4range(v_pos, v_pos + length(r.sinonimo) + 2);
      if not exists (select 1 from unnest(v_cubiertos) c where c @> v_rango) then
        -- Una frase que ya empieza con "no" ("no puedo respirar") no se niega a sí misma.
        if r.sinonimo not like 'no %' and app.negado_antes_de(v_t, v_pos) then
          -- La frase negada tampoco cuenta por sus pedazos ("sin dolor de pecho" no deja "dolor").
          v_negados := array_append(v_negados, r.termino);
          v_cubiertos := array_append(v_cubiertos, v_rango);
        else
          v_cubiertos := array_append(v_cubiertos, v_rango);
          v_claves := array_append(v_claves, r.clave);
          v_hallazgos := v_hallazgos || jsonb_build_object(
            'clave', r.clave, 'categoria', r.categoria, 'termino', r.termino, 'frase_detectada', r.sinonimo,
            'que_puede_indicar', r.que_puede_indicar, 'que_hacer', r.que_hacer);
          exit;
        end if;
      end if;
      v_desde := v_pos + 1;
    end loop;
  end loop;

  v_hallazgos := v_hallazgos || app.signos_vitales_de_riesgo(p_texto);

  -- Urgencias primero: lo que el médico debe leer antes.
  select coalesce(jsonb_agg(h order by (h->>'categoria') like 'URGENCIA%' desc, ordinality), '[]'::jsonb)
    into v_hallazgos
  from jsonb_array_elements(v_hallazgos) with ordinality as x(h, ordinality);

  select count(*) filter (where h->>'categoria' like 'URGENCIA%'),
         count(*) filter (where h->>'categoria' like 'PRECAUCION%'),
         coalesce(bool_or(h->>'categoria' = 'URGENCIA_MENTAL'), false),
         string_agg(h->>'termino', ', ')
    into v_urgencias, v_precauciones, v_mental, v_terminos
  from jsonb_array_elements(v_hallazgos) h;

  -- Mismas reglas que el triage de la app: una urgencia, o tres precauciones juntas.
  v_nivel := case
    when v_urgencias > 0 or v_precauciones >= 3 then 'ROJO'
    when v_precauciones > 0 then 'AMBAR'
    else 'VERDE'
  end;

  v_recomendacion := case
    when v_mental then (select que_hacer from app.base_conocimiento_riesgo where clave = 'ideacion_suicida')
    when v_nivel = 'ROJO' and v_urgencias > 0 then v_hallazgos->0->>'que_hacer'
    when v_nivel = 'ROJO' then 'Varios síntomas a la vez: contactar al paciente hoy y valorar consulta presencial.'
    when v_nivel = 'AMBAR' then v_hallazgos->0->>'que_hacer'
    else 'Sin señales de alarma: seguimiento habitual.'
  end;

  return jsonb_build_object(
    'version', app.version_analisis_riesgo(),
    'nivel', v_nivel,
    'en_peligro', v_nivel = 'ROJO',
    'requiere_linea_de_crisis', v_mental,
    'resumen', case v_nivel
      when 'ROJO' then 'Posible situación de peligro: ' || v_terminos || '.'
      when 'AMBAR' then 'Síntomas a vigilar: ' || v_terminos || '.'
      else 'Sin señales de alarma detectadas.'
    end,
    'recomendacion', v_recomendacion,
    'hallazgos', v_hallazgos,
    'descartados_por_negacion', to_jsonb(array(
      select distinct n from unnest(v_negados) n
      where n not in (select h->>'termino' from jsonb_array_elements(v_hallazgos) h)
    ))
  );
end;
$$;

create or replace function app.severidad_mayor(p_a text, p_b text)
returns text
language sql
immutable
as $$
  select case
    when 'ROJO' in (p_a, p_b) then 'ROJO'
    when 'AMBAR' in (p_a, p_b) then 'AMBAR'
    else 'VERDE'
  end;
$$;

create or replace function app.terminos_de_analisis(p_analisis jsonb)
returns text
language sql
immutable
as $$
  select string_agg(h->>'termino', ', ')
  from jsonb_array_elements(coalesce(p_analisis->'hallazgos', '[]'::jsonb)) h;
$$;

-- ---------------------------------------------------------------------------
-- 3. Diario y chat: se analizan al guardarse
-- ---------------------------------------------------------------------------
alter table app.entradas_diario
  add column severidad_servidor text check (severidad_servidor in ('VERDE', 'AMBAR', 'ROJO')),
  add column analisis_riesgo jsonb;

alter table app.mensajes
  add column analisis_riesgo jsonb;

create or replace function app.analizar_entrada_diario()
returns trigger
language plpgsql
as $$
begin
  new.analisis_riesgo := app.analizar_texto_riesgo(new.texto);
  new.severidad_servidor := new.analisis_riesgo->>'nivel';
  return new;
end;
$$;

create trigger trg_entradas_diario_analisis_riesgo
  before insert or update of texto on app.entradas_diario
  for each row execute function app.analizar_entrada_diario();

-- Solo lo que escribe el paciente: los mensajes del médico hablan de síntomas
-- sin que el paciente esté en peligro.
create or replace function app.analizar_mensaje_paciente()
returns trigger
language plpgsql
as $$
begin
  if new.autor = 'PACIENTE' and btrim(coalesce(new.texto, '')) <> '' then
    new.analisis_riesgo := app.analizar_texto_riesgo(new.texto);
  end if;
  return new;
end;
$$;

create trigger trg_mensajes_analisis_riesgo
  before insert on app.mensajes
  for each row execute function app.analizar_mensaje_paciente();

-- Lo ya escrito se analiza una vez (nunca tuvo análisis del servidor).
update app.entradas_diario
   set analisis_riesgo = app.analizar_texto_riesgo(texto),
       severidad_servidor = app.analizar_texto_riesgo(texto)->>'nivel'
 where analisis_riesgo is null;

update app.mensajes
   set analisis_riesgo = app.analizar_texto_riesgo(texto)
 where analisis_riesgo is null and autor = 'PACIENTE' and btrim(texto) <> ''
   and instante >= now() - interval '30 days';

-- La app del médico lee el análisis junto a la entrada. Columnas al final:
-- `create or replace view` solo deja agregar, y la vista sigue siendo
-- actualizable (la app inserta sin mandar estas dos).
create or replace view api.entradas_diario with (security_invoker = true) as
select
  id_entrada as "idEntrada",
  id_paciente as "idPaciente",
  instante as "instante",
  fecha as "fecha",
  texto as "texto",
  severidad as "severidad",
  terminos_detectados as "terminosDetectados",
  severidad_servidor as "severidadServidor",
  analisis_riesgo as "analisisRiesgo"
from app.entradas_diario;

-- Para la app: analizar un texto antes de guardarlo (mismo motor del servidor).
create or replace function api.analizar_riesgo(texto text)
returns jsonb
language sql
stable
as $$
  select app.analizar_texto_riesgo(texto);
$$;
grant execute on function api.analizar_riesgo(text) to paciente, medico;

-- ---------------------------------------------------------------------------
-- 4. Alertas: diario con la severidad más grave y mensajes del chat
-- ---------------------------------------------------------------------------
create or replace function app.sincronizar_alertas(p_id_medico text)
returns integer
language plpgsql
as $$
declare
  v_semana text := to_char(now() at time zone app.config_texto('zona_horaria_default'), 'IYYY-"S"IW');
  v_nuevas integer := 0;
  v_filas integer;
begin
  -- a) Diario: la severidad más grave entre la de la app y la del servidor.
  insert into app.alertas (id_medico, id_paciente, tipo, prioridad, titulo, detalle, clave_origen, creado_en)
  select p_id_medico, e.id_paciente, 'SINTOMA_DE_ALARMA',
         case s.severidad when 'ROJO' then 'ALTA' else 'MEDIA' end,
         case s.severidad when 'ROJO' then 'Síntoma de alarma en el diario' else 'Síntoma a vigilar en el diario' end,
         left(e.texto, 280)
           || coalesce(' — Detectado: ' || app.terminos_de_analisis(e.analisis_riesgo),
                       case when cardinality(e.terminos_detectados) > 0
                            then ' (' || array_to_string(e.terminos_detectados, ', ') || ')' else '' end),
         'diario:' || e.id_entrada,
         e.instante
  from app.control_accesos_medico ca
  join app.entradas_diario e on e.id_paciente = ca.id_paciente
  cross join lateral (select app.severidad_mayor(e.severidad, e.severidad_servidor) as severidad) s
  where ca.id_medico = p_id_medico
    and (ca.fecha_expiracion is null or ca.fecha_expiracion >= current_date)
    and s.severidad in ('ROJO', 'AMBAR')
    and e.instante >= now() - interval '7 days'
  on conflict (id_medico, clave_origen) do nothing;
  get diagnostics v_filas = row_count;
  v_nuevas := v_nuevas + v_filas;

  -- a2) Chat: lo que el paciente le escribe a ESTE médico.
  insert into app.alertas (id_medico, id_paciente, tipo, prioridad, titulo, detalle, clave_origen, creado_en)
  select p_id_medico, c.id_paciente, 'SINTOMA_DE_ALARMA',
         case m.analisis_riesgo->>'nivel' when 'ROJO' then 'ALTA' else 'MEDIA' end,
         case m.analisis_riesgo->>'nivel' when 'ROJO' then 'Señal de alarma en un mensaje' else 'Síntoma a vigilar en un mensaje' end,
         left(m.texto, 280) || coalesce(' — Detectado: ' || app.terminos_de_analisis(m.analisis_riesgo), ''),
         'chat:' || m.id_mensaje,
         m.instante
  from app.conversaciones c
  join app.mensajes m on m.id_conversacion = c.id_conversacion
  where c.id_medico = p_id_medico
    and app.vinculo_vigente(p_id_medico, c.id_paciente)
    and m.autor = 'PACIENTE'
    and m.analisis_riesgo->>'nivel' in ('ROJO', 'AMBAR')
    and m.instante >= now() - interval '7 days'
  on conflict (id_medico, clave_origen) do nothing;
  get diagnostics v_filas = row_count;
  v_nuevas := v_nuevas + v_filas;

  -- b) Riesgo de abandono (sin cambios respecto a 0016).
  update app.alertas al
     set prioridad = 'ALTA',
         titulo = 'Riesgo alto de abandono del tratamiento',
         detalle = (
           select format('Tomó %s de %s dosis programadas en los últimos 7 días (%s %%).',
                         a.cumplidas, a.programadas, round(100.0 * a.cumplidas / a.programadas))
           from app.adherencia_7_dias(al.id_paciente) a)
   where al.id_medico = p_id_medico
     and al.tipo = 'RIESGO_ADHERENCIA'
     and al.estado in ('NUEVA', 'EN_REVISION')
     and al.prioridad = 'MEDIA'
     and app.riesgo_paciente(al.id_paciente) = 'ALTO'
     and exists (select 1 from app.adherencia_7_dias(al.id_paciente) a where a.programadas > 0);

  insert into app.alertas (id_medico, id_paciente, tipo, prioridad, titulo, detalle, clave_origen)
  select p_id_medico, ca.id_paciente, 'RIESGO_ADHERENCIA',
         case r.riesgo when 'ALTO' then 'ALTA' else 'MEDIA' end,
         case r.riesgo when 'ALTO' then 'Riesgo alto de abandono del tratamiento' else 'Adherencia en descenso' end,
         format('Tomó %s de %s dosis programadas en los últimos 7 días (%s %%).',
                a.cumplidas, a.programadas, round(100.0 * a.cumplidas / a.programadas)),
         'riesgo:' || ca.id_paciente || ':' || v_semana
  from app.control_accesos_medico ca
  cross join lateral (select app.riesgo_paciente(ca.id_paciente) as riesgo) r
  cross join lateral app.adherencia_7_dias(ca.id_paciente) a
  where ca.id_medico = p_id_medico
    and (ca.fecha_expiracion is null or ca.fecha_expiracion >= current_date)
    and r.riesgo in ('ALTO', 'MEDIO')
    and a.programadas > 0
    and not exists (
      select 1 from app.alertas al
      where al.id_medico = p_id_medico and al.id_paciente = ca.id_paciente
        and al.tipo = 'RIESGO_ADHERENCIA' and al.estado in ('NUEVA', 'EN_REVISION')
    )
  on conflict (id_medico, clave_origen) do nothing;
  get diagnostics v_filas = row_count;
  v_nuevas := v_nuevas + v_filas;

  -- c) Medicamento por terminarse (sin cambios respecto a 0016).
  insert into app.alertas (id_medico, id_paciente, tipo, prioridad, titulo, detalle, clave_origen)
  select p_id_medico, t.id_paciente, 'INVENTARIO_BAJO',
         case when t.cantidad_restante <= 0 or d.dias < 2 then 'ALTA' else 'MEDIA' end,
         case when t.cantidad_restante <= 0 then 'Se terminó ' || t.medicamento
              else 'Se está terminando ' || t.medicamento end,
         case
           when t.cantidad_restante <= 0 then 'Ya no le quedan dosis.'
           when t.cantidad_restante = 1 then 'Le queda 1 dosis.'
           else format('Le quedan %s dosis.', t.cantidad_restante)
         end
         || case
              when t.cantidad_restante <= 0 or d.dias is null then ''
              when d.dias < 1 then ' No le alcanza para un día completo.'
              when d.dias = 1 then ' Le alcanza para 1 día.'
              else format(' Le alcanza para unos %s días.', d.dias)
            end,
         'inventario:' || t.id_tratamiento || ':' || v_semana
  from app.control_accesos_medico ca
  join app.tratamientos t on t.id_paciente = ca.id_paciente
  cross join lateral (
    select floor(t.cantidad_restante / app.dosis_por_dia(t.horarios_sugeridos, t.frecuencia_horas))::int as dias
  ) d
  where ca.id_medico = p_id_medico
    and (ca.fecha_expiracion is null or ca.fecha_expiracion >= current_date)
    and t.cantidad_restante is not null
    and t.cantidad_restante <= t.umbral_alerta
    and (t.fecha_fin is null or t.fecha_fin >= current_date)
    and not exists (
      select 1 from app.alertas al
      where al.id_medico = p_id_medico
        and al.clave_origen like 'inventario:' || t.id_tratamiento || ':%'
        and al.estado in ('NUEVA', 'EN_REVISION')
    )
  on conflict (id_medico, clave_origen) do nothing;
  get diagnostics v_filas = row_count;
  v_nuevas := v_nuevas + v_filas;

  return v_nuevas;
end;
$$;

-- El análisis guardado de lo que originó la alerta (diario o chat), si lo hay.
create or replace function app.analisis_de_origen(p_clave_origen text)
returns jsonb
language sql
stable
as $$
  select case
    when p_clave_origen like 'diario:%' then
      (select e.analisis_riesgo from app.entradas_diario e where e.id_entrada = substr(p_clave_origen, 8))
    when p_clave_origen like 'chat:%' then
      (select m.analisis_riesgo from app.mensajes m where m.id_mensaje = substr(p_clave_origen, 6))
  end;
$$;

-- Panel web: la sugerencia sale de la base de conocimiento (qué hacer ante lo
-- detectado) y los vectores dicen qué puede indicar cada hallazgo.
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
    'vectores_de_prediccion', jsonb_build_array(al.titulo, al.detalle) || coalesce((
      select jsonb_agg((h->>'termino') || ': ' || (h->>'que_puede_indicar'))
      from jsonb_array_elements(an.analisis->'hallazgos') h
    ), '[]'::jsonb),
    'sugerencia_ia_clinica', coalesce(an.analisis->>'recomendacion', app.sugerencia_alerta(al.tipo, al.prioridad, al.titulo)),
    'estado_atencion', app.estado_atencion(al.estado)
  )
  from app.alertas al
  left join app.datos_personales_paciente dp on dp.id_paciente = al.id_paciente
  left join lateral (select app.analisis_de_origen(al.clave_origen) as analisis) an
    on an.analisis->>'nivel' in ('ROJO', 'AMBAR')
  where al.id_alerta = p_id_alerta;
$$;

revoke all on function app.normalizar_texto_clinico(text) from public;
revoke all on function app.negado_antes_de(text, integer) from public;
revoke all on function app.signos_vitales_de_riesgo(text) from public;
revoke all on function app.analizar_texto_riesgo(text) from public;
grant execute on function app.analizar_texto_riesgo(text) to paciente, medico;
revoke all on function app.analisis_de_origen(text) from public;
revoke all on function app.terminos_de_analisis(jsonb) from public;

notify pgrst, 'reload schema';
