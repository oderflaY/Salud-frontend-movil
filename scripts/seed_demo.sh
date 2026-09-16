#!/usr/bin/env bash
# Carga datos de demostración.
#
# Las cuentas se crean por la API (POST /auth/...) y NO por SQL: el hash de la
# contraseña es argon2id con salt aleatorio y el pepper ARGON2_SECRET_KEY, que
# solo conoce el proceso de Axum. Una fila insertada a mano en hash_contrasena
# jamás puede pasar el login.
#
# Uso:  sudo bash scripts/seed_demo.sh
set -euo pipefail

API="${API:-http://localhost:8000}"
PASS="${PASS:-Demo1234}"          # misma contraseña para todas las cuentas demo
TARJETA="${TARJETA:-C38610A8}"    # UID de la tarjeta RFID física

cd "$(dirname "$0")/.."

psql_exec() { docker compose exec -T db psql -U salud_admin -d salud -v ON_ERROR_STOP=1 "$@"; }
# Administrador del panel web: solo se crea desde el servidor (no hay ruta HTTP).
crear_admin() { docker compose exec -T -e ADMIN_CONTRASENA="$PASS" backend backend crear-admin "$@"; }

crear_medico() { # correo nombre apellidos tratamiento cedula -> idMedico
  curl -sS -X POST "$API/auth/profesionales" -H 'Content-Type: application/json' \
    -d "$(jq -nc --arg c "$1" --arg p "$PASS" --arg n "$2" --arg a "$3" --arg t "$4" --arg e "$5" \
          '{correo:$c,contrasena:$p,nombre:$n,apellidos:$a,tratamiento:$t,cedulaProfesional:$e}')" \
  | jq -er '.idMedico'
}

crear_paciente() { # correo -> idPaciente
  curl -sS -X POST "$API/auth/pacientes" -H 'Content-Type: application/json' \
    -d "$(jq -nc --arg c "$1" --arg p "$PASS" '{correo:$c,contrasena:$p}')" \
  | jq -er '.idPaciente'
}

echo "==> Esperando al backend en $API"
for _ in $(seq 1 30); do
  curl -sf "$API/healthz" >/dev/null 2>&1 && break
  sleep 1
done
curl -sf "$API/healthz" >/dev/null || { echo "El backend no responde en $API"; exit 1; }

echo "==> Borrando datos previos"
psql_exec -q -c "TRUNCATE app.pacientes, app.medicos CASCADE;"

echo "==> Creando médicos"
D1=$(crear_medico dr.silva@salud.local     Carlos   "Silva Rodríguez"   "Dr."  1234567)
D2=$(crear_medico dra.lopez@salud.local    María    "López Martínez"    "Dra." 2345678)
D3=$(crear_medico dr.garcia@salud.local    Luis     "García Flores"     "Dr."  3456789)
D4=$(crear_medico dra.torres@salud.local   Patricia "Torres Sánchez"    "Dra." 4567890)
D5=$(crear_medico dr.reyes@salud.local     Roberto  "Reyes Hernández"   "Dr."  5678901)
D6=$(crear_medico dra.moreno@salud.local   Sandra   "Moreno Díaz"       "Dra." 6789012)

echo "==> Creando pacientes"
P1=$(crear_paciente paciente1@salud.local)
P2=$(crear_paciente paciente2@salud.local)
P3=$(crear_paciente paciente3@salud.local)
P4=$(crear_paciente paciente4@salud.local)

echo "==> Cargando expedientes"
psql_exec -q <<SQL
begin;

update app.medicos set estado_verificacion = 'APROBADO';

-- Perfil de directorio: sin especialidad, el filtro del directorio de la app
-- sale vacio en la demo. Cada especialidad cuadra con los pacientes que ese
-- medico lleva; hay dos cardiologos a proposito, para que el filtro muestre
-- que devuelve varios resultados.
update app.medicos m set especialidad = v.esp, universidad = v.uni, disponibilidad = v.disp
from (values
  ('$D1','MEDICINA_GENERAL','UNAM',                       'Lunes a viernes, 9:00 a 14:00'),
  ('$D2','NEUMOLOGIA',      'IPN',                        'Lunes, miércoles y viernes, 10:00 a 18:00'),
  ('$D3','CARDIOLOGIA',     'Universidad de Guadalajara', 'Martes y jueves, 8:00 a 15:00'),
  ('$D4','ENDOCRINOLOGIA',  'UANL',                       'Lunes a viernes, 16:00 a 20:00'),
  ('$D5','CARDIOLOGIA',     'BUAP',                       'Sábados, 9:00 a 13:00'),
  ('$D6','GINECOLOGIA',     'UNAM',                       'Miércoles y viernes, 11:00 a 17:00')
) as v(id, esp, uni, disp)
where m.id_medico = v.id;
update app.pacientes set requiere_onboarding = false;

insert into app.datos_personales_paciente (id_paciente, nombre, apellidos, fecha_nacimiento, genero, telefono) values
  ('$P1','Juan','Pérez García','1985-06-15','M','5551234567'),
  ('$P2','María','González López','1990-03-22','F','5552345678'),
  ('$P3','Carlos','Rodríguez Martín','1978-11-08','M','5553456789'),
  ('$P4','Ana','Martínez Ruiz','1995-08-30','F','5554567890');

insert into app.identificaciones_paciente (id_paciente, curp, nss, aseguradora) values
  ('$P1','PEGJ850615HDFRRL09','12345678901','IMSS'),
  ('$P2','GOGL900322MDFRNR08','98765432101','ISSSTE'),
  ('$P3','ROMM780108HDFRRR03','55555555501','IMSS'),
  ('$P4','MARU950830MDFRRN07','77777777701','Privada');

-- La tarjeta física de la demo apunta al paciente 1.
insert into app.dispositivos_rfid (id_tarjeta_rfid, id_paciente, estado) values
  ('$TARJETA','$P1','activa'),
  ('RFID-0002','$P2','activa'),
  ('RFID-0003','$P3','activa'),
  ('RFID-0004','$P4','activa');

insert into app.perfil_emergencia_reducido (id_paciente, tipo_sangre, donador_organos, condiciones_criticas, medicacion_rescate) values
  ('$P1','O+',false, array['Hipertensión','Diabetes tipo 2','Colesterol alto'], array['Metformina 500mg','Losartán 50mg']),
  ('$P2','A-',true,  array['Asma moderada','Rinitis alérgica'],                 array['Salbutamol 100mcg']),
  ('$P3','B+',false, array['Insuficiencia cardíaca','Fibrilación auricular'],   array['Furosemida 40mg','Warfarina 5mg']),
  ('$P4','AB+',true, array['Hipotiroidismo'],                                   array['Levotiroxina 75mcg']);

insert into app.alergias (id_paciente, alergeno, severidad, reaccion) values
  ('$P1','Penicilina','Alta','Anafilaxia'),
  ('$P1','Frutos secos','Media','Angioedema'),
  ('$P1','Codeína','Alta','Reacción severa'),
  ('$P2','Látex','Alta','Urticaria generalizada'),
  ('$P2','Ibuprofeno','Media','Gastritis'),
  ('$P3','Aspirina','Alta','Sangrado gastrointestinal'),
  ('$P3','Enalapril','Media','Tos seca persistente'),
  ('$P4','AINES','Media','Gastritis');

insert into app.metricas_vitales_actuales (id_paciente, peso_kg, altura_cm, imc, ultima_presion_arterial, fecha_toma_metricas) values
  ('$P1',75.5,178,23.8,'128/82', now() - interval '2 days'),
  ('$P2',62.0,165,22.8,'115/75', now() - interval '1 day'),
  ('$P3',88.0,175,28.7,'145/95', now() - interval '3 days'),
  ('$P4',58.0,162,22.1,'110/70', now() - interval '5 days');

insert into app.historial_clinico (id_paciente, antecedentes_heredofamiliares) values
  ('$P1', array['Diabetes tipo 2 en madre','Hipertensión en padre','Infarto en abuelo paterno']),
  ('$P2', array['Asma en hermano','Rinitis alérgica en madre']),
  ('$P3', array['Cardiopatía isquémica en padre','Colesterol alto en madre']),
  ('$P4', array['Hipotiroidismo en madre','Diabetes en abuela materna']);

insert into app.cirugias (id_paciente, procedimiento, fecha, notas) values
  ('$P1','Apendicectomía','2010-03-15','Sin complicaciones'),
  ('$P1','Colecistectomía laparoscópica','2018-07-20','Cálculos biliares'),
  ('$P2','Septoplastia','2012-11-10','Desviación del tabique'),
  ('$P3','Cateterismo con colocación de stents','2022-06-05','Dos stents en descendente anterior'),
  ('$P3','Ablación por radiofrecuencia','2023-02-14','Por fibrilación auricular'),
  ('$P4','Histerectomía total','2015-09-30','Miomatosis uterina');

insert into app.tratamientos (id_tratamiento, id_paciente, medicamento, dosis, frecuencia_horas, horarios_sugeridos, via_administracion, fecha_inicio, id_medico_receta, cantidad_restante, umbral_alerta) values
  ('trt_001','$P1','Metformina','500mg',12, array['08:00','20:00'],'Oral', current_date - 180, '$D1', 45, 10),
  ('trt_002','$P1','Atorvastatina','20mg',24, array['21:00'],'Oral',       current_date - 150, '$D1', 8,  10),
  ('trt_003','$P1','Losartán','50mg',24, array['08:00'],'Oral',            current_date - 200, '$D1', 30, 5),
  ('trt_004','$P2','Salbutamol','100mcg',6, array['06:00','12:00','18:00','00:00'],'Inhalado', current_date - 90, '$D2', 180, 50),
  ('trt_005','$P2','Fluticasona','250mcg',12, array['08:00','20:00'],'Inhalado', current_date - 100,'$D2', 24, 5),
  ('trt_006','$P3','Furosemida','40mg',12, array['08:00','20:00'],'Oral',  current_date - 300, '$D3', 45, 10),
  ('trt_007','$P3','Warfarina','5mg',24, array['18:00'],'Oral',            current_date - 180, '$D3', 4,  5),
  ('trt_008','$P4','Levotiroxina','75mcg',24, array['07:00'],'Oral',       current_date - 365, '$D4', 25, 5);

-- Adherencia de los últimos 7 días.
--
-- Las filas se generan en el MISMO instante que usaría
-- app.materializar_tomas_dia (fecha + horarios_sugeridos, en la zona horaria
-- de app.configuracion). Es imprescindible: esa función materializa el día de
-- forma perezosa con ON CONFLICT DO NOTHING sobre
-- (id_tratamiento, fecha_hora_programada). Si el seed usara horas inventadas,
-- no chocarían y quedarían dos calendarios paralelos — el real entero en
-- 'pendiente', hundiendo el riesgo de todos los pacientes a ALTO.
--
-- Solo se siembran dosis ya vencidas; las de más tarde hoy las materializa la
-- app cuando toque, y app.riesgo_paciente (0011) ya no las penaliza.
with programadas as (
  select t.id_paciente, t.id_tratamiento,
         (d.dia::date::text || ' ' || h.horario)::timestamp
           at time zone app.config_texto('zona_horaria_default') as momento
  from app.tratamientos t
  cross join generate_series(current_date - 7, current_date, interval '1 day') as d(dia)
  cross join unnest(t.horarios_sugeridos) as h(horario)
),
vencidas as (
  select p.*, row_number() over (partition by p.id_paciente order by p.momento) as n
  from programadas p
  where p.momento <= now() - interval '1 hour'
),
calificadas as (
  -- Un nivel de riesgo distinto por paciente, para que la cartera del médico
  -- muestre los tres colores en la demostración.
  select v.*,
         case v.id_paciente
           when '$P3' then (v.n % 4) = 0    -- 25% cumplidas  -> ALTO
           when '$P2' then (v.n % 3) <> 0   -- 67% cumplidas  -> MEDIO
           else (v.n % 9) <> 0              -- 89% cumplidas  -> BAJO
         end as cumple,
         (v.n % 7) = 0 as tarde
  from vencidas v
)
insert into app.registro_adherencia (id_tratamiento, fecha_hora_programada, fecha_hora_real, estado)
select c.id_tratamiento, c.momento,
       case when not c.cumple then null
            when c.tarde then c.momento + interval '95 minutes'
            else c.momento + interval '7 minutes' end,
       case when not c.cumple then 'omitido'
            when c.tarde then 'tomado_tarde'
            else 'tomado' end
from calificadas c
on conflict (id_tratamiento, fecha_hora_programada) do nothing;

insert into app.contactos_emergencia (id_paciente, nombre, relacion, telefono, prioridad) values
  ('$P1','Rosa María García','Esposa','5551111111',1),
  ('$P1','Pedro Pérez García','Hermano','5552222222',2),
  ('$P2','Roberto González López','Padre','5554444444',1),
  ('$P2','Carmen López Díaz','Madre','5555555555',2),
  ('$P3','Francisca Martín López','Esposa','5557777777',1),
  ('$P3','Manuel Rodríguez Martín','Hijo','5559999999',2),
  ('$P4','David Ruiz Martínez','Esposo','5560000000',1);

insert into app.control_accesos_medico (id_paciente, id_medico, nivel_acceso) values
  ('$P1','$D1','lectura_escritura'),
  ('$P1','$D2','lectura'),
  ('$P1','$D3','lectura'),
  ('$P2','$D2','lectura_escritura'),
  ('$P2','$D1','lectura'),
  ('$P3','$D3','lectura_escritura'),
  ('$P3','$D4','lectura_escritura'),
  ('$P3','$D5','lectura'),
  ('$P4','$D4','lectura_escritura'),
  ('$P4','$D6','lectura');

-- La cartera del médico exige conversación con cada paciente vinculado.
insert into app.conversaciones (id_conversacion, id_paciente, id_medico, creado_en) values
  ('conv_001','$P1','$D1', now() - interval '60 days'),
  ('conv_002','$P1','$D2', now() - interval '40 days'),
  ('conv_003','$P1','$D3', now() - interval '35 days'),
  ('conv_004','$P2','$D2', now() - interval '45 days'),
  ('conv_005','$P2','$D1', now() - interval '30 days'),
  ('conv_006','$P3','$D3', now() - interval '50 days'),
  ('conv_007','$P3','$D4', now() - interval '25 days'),
  ('conv_008','$P3','$D5', now() - interval '20 days'),
  ('conv_009','$P4','$D4', now() - interval '20 days'),
  ('conv_010','$P4','$D6', now() - interval '15 days');

insert into app.mensajes (id_conversacion, autor, texto, instante, tipo) values
  ('conv_001','PACIENTE','Buenas tardes doctor, la glucosa en ayunas me salió en 142 esta mañana.', now() - interval '5 days 3 hours','NORMAL'),
  ('conv_001','MEDICO','Hemos recibido tu mensaje. Un profesional lo revisará a la brevedad.', now() - interval '5 days 3 hours' + interval '4 seconds','ORIENTACION_INICIAL'),
  ('conv_001','MEDICO','Gracias Juan. Sigue con la metformina sin cambios y toma la lectura tres días seguidos antes del desayuno.', now() - interval '5 days 1 hour','NORMAL'),
  ('conv_001','PACIENTE','De acuerdo. Le comparto los valores el viernes.', now() - interval '4 days 22 hours','NORMAL'),
  ('conv_001','PACIENTE','Doctor, hoy amanecí en 118. Bajó bastante.', now() - interval '2 days','NORMAL'),
  ('conv_001','MEDICO','Excelente. Mantén la dosis y nos vemos en la cita de control.', now() - interval '1 day 20 hours','NORMAL'),
  ('conv_004','PACIENTE','Doctora, usé el inhalador tres veces anoche, sentí opresión en el pecho.', now() - interval '3 days','NORMAL'),
  ('conv_004','MEDICO','Hemos recibido tu mensaje. Un profesional lo revisará a la brevedad.', now() - interval '3 days' + interval '3 seconds','ORIENTACION_INICIAL'),
  ('conv_004','MEDICO','María, si necesitas el rescate más de dos veces por semana hay que ajustar el control. Agenda cita esta semana.', now() - interval '2 days 18 hours','NORMAL'),
  ('conv_004','PACIENTE','Ya la agendé para el jueves, gracias.', now() - interval '2 days 10 hours','NORMAL'),
  ('conv_006','PACIENTE','Doctor, se me hincharon los tobillos otra vez y subí dos kilos en cuatro días.', now() - interval '6 days','NORMAL'),
  ('conv_006','MEDICO','Hemos recibido tu mensaje. Un profesional lo revisará a la brevedad.', now() - interval '6 days' + interval '5 seconds','ORIENTACION_INICIAL'),
  ('conv_006','MEDICO','Carlos, eso sugiere retención de líquido. No suspendas la furosemida y pésate todas las mañanas.', now() - interval '5 days 20 hours','NORMAL'),
  ('conv_006','PACIENTE','Entendido. A veces se me olvida la toma de la noche.', now() - interval '5 days','NORMAL'),
  ('conv_006','MEDICO','Activa el recordatorio en la app, es importante que no falten tomas.', now() - interval '4 days','NORMAL'),
  ('conv_009','PACIENTE','Doctora, ¿la levotiroxina se puede tomar junto con el calcio?', now() - interval '8 days','NORMAL'),
  ('conv_009','MEDICO','No. Deja al menos cuatro horas entre una y otra, el calcio reduce la absorción.', now() - interval '7 days 20 hours','NORMAL'),
  ('conv_009','PACIENTE','Perfecto, lo cambio al mediodía entonces.', now() - interval '7 days 12 hours','NORMAL');

insert into app.entradas_diario (id_paciente, instante, fecha, texto, severidad, terminos_detectados) values
  ('$P1', now() - interval '6 days', (now() - interval '6 days')::date, 'Día tranquilo, sin mareos. Caminé 30 minutos.', 'VERDE', array[]::text[]),
  ('$P1', now() - interval '4 days', (now() - interval '4 days')::date, 'Sentí un poco de sed y fui al baño más veces de lo normal.', 'AMBAR', array['sed','poliuria']),
  ('$P1', now() - interval '1 day',  (now() - interval '1 day')::date,  'Me sentí bien todo el día, la glucosa amaneció normal.', 'VERDE', array[]::text[]),
  ('$P2', now() - interval '3 days', (now() - interval '3 days')::date, 'Desperté con silbido en el pecho y tuve que usar el inhalador de rescate.', 'AMBAR', array['sibilancias','disnea']),
  ('$P2', now() - interval '2 days', (now() - interval '2 days')::date, 'Mejor que ayer, solo una vez el inhalador.', 'VERDE', array[]::text[]),
  ('$P3', now() - interval '6 days', (now() - interval '6 days')::date, 'Tobillos muy hinchados y me falta el aire al subir escaleras.', 'ROJO', array['edema','disnea de esfuerzo']),
  ('$P3', now() - interval '3 days', (now() - interval '3 days')::date, 'La hinchazón bajó algo pero sigo cansado.', 'AMBAR', array['edema','fatiga']),
  ('$P3', now() - interval '12 hours', (now() - interval '12 hours')::date, 'Palpitaciones por la noche, me costó dormir.', 'ROJO', array['palpitaciones']),
  ('$P4', now() - interval '5 days', (now() - interval '5 days')::date, 'Con energía, sin síntomas.', 'VERDE', array[]::text[]),
  ('$P4', now() - interval '2 days', (now() - interval '2 days')::date, 'Algo de frío y cansancio por la tarde.', 'AMBAR', array['intolerancia al frío','fatiga']);

-- Agenda: dos semanas de franjas para los médicos principales.
insert into app.franjas (id_franja, id_medico, fecha, hora_inicio, hora_fin)
select 'franja_' || m.doc || '_' || to_char(d.dia,'YYYYMMDD') || '_' || replace(h.hi::text,':',''),
       m.id, d.dia, h.hi, h.hi + interval '30 minutes'
from (values ('$D1','d1'), ('$D2','d2'), ('$D3','d3'), ('$D4','d4')) as m(id, doc),
     generate_series(current_date, current_date + 13, interval '1 day') as d(dia),
     (values (time '09:00'), (time '10:00'), (time '11:00'), (time '16:00'), (time '17:00')) as h(hi)
where extract(isodow from d.dia) < 6;

insert into app.citas (folio, id_medico, id_paciente, id_franja, fecha, hora_inicio, hora_fin, estado, contacto_nombre, contacto_telefono, contacto_correo, contacto_motivo)
select 'CITA-' || lpad((row_number() over (order by f.fecha, f.hora_inicio))::text, 4, '0'),
       f.id_medico, c.id_paciente, f.id_franja, f.fecha, f.hora_inicio, f.hora_fin, c.estado,
       c.nombre, c.tel, c.correo, c.motivo
from (values
    ('$D1','$P1','CONFIRMADA','Juan Pérez García','5551234567','paciente1@salud.local','Control de diabetes e hipertensión', 1),
    ('$D2','$P2','CONFIRMADA','María González López','5552345678','paciente2@salud.local','Revisión de asma tras crisis nocturna', 2),
    ('$D3','$P3','PENDIENTE','Carlos Rodríguez Martín','5553456789','paciente3@salud.local','Edema en miembros inferiores', 3),
    ('$D4','$P4','CONFIRMADA','Ana Martínez Ruiz','5554567890','paciente4@salud.local','Control de tiroides y ajuste de dosis', 4)
  ) as c(id_medico, id_paciente, estado, nombre, tel, correo, motivo, orden)
join lateral (
  select * from app.franjas f2
  where f2.id_medico = c.id_medico and f2.fecha >= current_date + 1
  order by f2.fecha, f2.hora_inicio
  offset c.orden limit 1
) f on true;

commit;
SQL

echo "==> Administrador del panel"
crear_admin admin@salud.local Administración Salud

echo "==> Verificando"
TOKEN_P=$(curl -sS -X POST "$API/auth/pacientes/sesion" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg p "$PASS" '{correo:"paciente1@salud.local",contrasena:$p}')" | jq -er '.token' | cut -c1-24)
TOKEN_M=$(curl -sS -X POST "$API/auth/profesionales/sesion" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg p "$PASS" '{correo:"dr.silva@salud.local",contrasena:$p}')" | jq -er '.token' | cut -c1-24)
NOMBRE_TARJETA=$(curl -sS "$API/tarjetas/$TARJETA/perfil-supervivencia" | jq -r '.nombreCompleto // .datosPersonales.nombre // "(revisar respuesta)"')

echo
echo "  login paciente  -> OK (token ${TOKEN_P}...)"
echo "  login médico    -> OK (token ${TOKEN_M}...)"
echo "  tarjeta $TARJETA -> $NOMBRE_TARJETA"
echo "  riesgo por paciente (esperado BAJO / MEDIO / ALTO / BAJO):"
psql_exec -tA -F'  ' -c "select dp.nombre, app.riesgo_paciente(p.id_paciente)
  from app.pacientes p join app.datos_personales_paciente dp on dp.id_paciente = p.id_paciente
  order by dp.nombre;" | sed 's/^/    /'
echo
psql_exec -c "select 'pacientes' t, count(*) from app.pacientes
 union all select 'médicos', count(*) from app.medicos
 union all select 'tratamientos', count(*) from app.tratamientos
 union all select 'tomas', count(*) from app.registro_adherencia
 union all select 'mensajes', count(*) from app.mensajes
 union all select 'diario', count(*) from app.entradas_diario
 union all select 'franjas', count(*) from app.franjas
 union all select 'citas', count(*) from app.citas;"

cat <<RESUMEN

  Contraseña de TODAS las cuentas: $PASS

  Pacientes                       Médicos
  paciente1@salud.local  $P1      dr.silva@salud.local     $D1
  paciente2@salud.local  $P2      dra.lopez@salud.local    $D2
  paciente3@salud.local  $P3      dr.garcia@salud.local    $D3
  paciente4@salud.local  $P4      dra.torres@salud.local   $D4
                                  dr.reyes@salud.local     $D5
                                  dra.moreno@salud.local   $D6

  Administrador del panel web: admin@salud.local (gestiona cuentas, no ve expedientes)

  Tarjeta RFID de la demo: $TARJETA  ->  paciente1 (Juan Pérez)
RESUMEN
