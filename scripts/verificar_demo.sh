#!/usr/bin/env bash
# Verifica, contra el stack corriendo, que la demo funcione y que los datos
# clinicos esten aislados. Correrlo despues de seed_demo.sh y antes de cada
# presentacion. No necesita Docker ni sudo: todo va por la API.
#
# Uso:  bash scripts/verificar_demo.sh
set -uo pipefail

API="${API:-http://localhost:8000}"
PASS="${PASS:-Demo1234}"
TARJETA="${TARJETA:-C38610A8}"
ok=0; fail=0
declare -a FALLOS

chk() { # chk <descripcion> <esperado> <obtenido>
  if [ "$2" = "$3" ]; then
    printf '  \033[32mok\033[0m    %s\n' "$1"; ok=$((ok+1))
  else
    printf '  \033[31mFALLA\033[0m %s  (esperado: %s | obtuvo: %s)\n' "$1" "$2" "$3"
    fail=$((fail+1)); FALLOS+=("$1 -> esperado $2, obtuvo $3")
  fi
}

login_p() { curl -sS -X POST "$API/auth/pacientes/sesion" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg c "$1" --arg p "${2:-$PASS}" '{correo:$c,contrasena:$p}')"; }
login_m() { curl -sS -X POST "$API/auth/profesionales/sesion" -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg c "$1" --arg p "${2:-$PASS}" '{correo:$c,contrasena:$p}')"; }
status_p() { curl -sS -o /dev/null -w '%{http_code}' -X POST "$API/auth/pacientes/sesion" \
  -H 'Content-Type: application/json' -d "$(jq -nc --arg c "$1" --arg p "$2" '{correo:$c,contrasena:$p}')"; }
sub() { echo "$1" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null | jq -r '.sub'; }
GET() { curl -sS "$API$1" -H "Authorization: Bearer $2"; }
GETS() { curl -sS -o /dev/null -w '%{http_code}' "$API$1" -H "Authorization: Bearer $2"; }
POSTS() { curl -sS -o /dev/null -w '%{http_code}' -X POST "$API$1" -H "Authorization: Bearer $2" -H 'Content-Type: application/json' -d "$3"; }
RPC() { curl -sS -X POST "$API/rpc/$1" -H "Authorization: Bearer $2" -H 'Content-Type: application/json' -d "$3"; }

curl -sf "$API/healthz" >/dev/null || { echo "El backend no responde en $API"; exit 1; }

echo
echo "A. AUTENTICACIÓN"
R=$(login_p paciente1@salud.local); TP1=$(echo "$R" | jq -r '.token'); P1=$(sub "$TP1")
chk "login de paciente devuelve token" "si" "$([ ${#TP1} -gt 50 ] && echo si || echo no)"
chk "el paciente ya no requiere onboarding" "false" "$(echo "$R" | jq -r '.requiereOnboarding')"
chk "contraseña incorrecta -> 401" "401" "$(status_p paciente1@salud.local NoEsLaClave99)"
chk "correo inexistente -> 401" "401" "$(status_p nadie@salud.local "$PASS")"
RM=$(login_m dr.silva@salud.local); TM1=$(echo "$RM" | jq -r '.token'); D1=$(sub "$TM1")
chk "login de médico devuelve token" "si" "$([ ${#TM1} -gt 50 ] && echo si || echo no)"
chk "el médico está APROBADO" "APROBADO" "$(echo "$RM" | jq -r '.estadoVerificacion')"
chk "alta con correo duplicado -> 409" "409" "$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$API/auth/pacientes" -H 'Content-Type: application/json' -d '{"correo":"paciente1@salud.local","contrasena":"OtraClave123"}')"
chk "alta de médico con cédula duplicada -> 409" "409" "$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$API/auth/profesionales" -H 'Content-Type: application/json' -d '{"correo":"nuevo.medico@salud.local","contrasena":"OtraClave123","nombre":"X","apellidos":"Y","tratamiento":"Dr.","cedulaProfesional":"1234567"}')"
chk "tratamiento fuera de dominio -> 400" "400" "$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$API/auth/profesionales" -H 'Content-Type: application/json' -d '{"correo":"otro.medico@salud.local","contrasena":"OtraClave123","nombre":"X","apellidos":"Y","tratamiento":"Licenciado","cedulaProfesional":"9999999"}')"
TP2=$(login_p paciente2@salud.local | jq -r '.token'); P2=$(sub "$TP2")
TP3=$(login_p paciente3@salud.local | jq -r '.token'); P3=$(sub "$TP3")
TP4=$(login_p paciente4@salud.local | jq -r '.token'); P4=$(sub "$TP4")
TM2=$(login_m dra.lopez@salud.local | jq -r '.token')
TM3=$(login_m dr.garcia@salud.local | jq -r '.token')
TM6=$(login_m dra.moreno@salud.local | jq -r '.token')
chk "las 4 cuentas de paciente inician sesión" "4" "$(for t in "$TP1" "$TP2" "$TP3" "$TP4"; do [ ${#t} -gt 50 ] && echo x; done | wc -l | tr -d ' ')"

echo
echo "B. TARJETA RFID (sin sesión)"
T=$(curl -sS "$API/tarjetas/$TARJETA/perfil-supervivencia")
chk "la tarjeta de la demo responde" "200" "$(curl -sS -o /dev/null -w '%{http_code}' "$API/tarjetas/$TARJETA/perfil-supervivencia")"
chk "la tarjeta abre el paciente correcto" "Juan" "$(echo "$T" | jq -r '.datosPersonales.nombre')"
chk "trae tipo de sangre" "O+" "$(echo "$T" | jq -r '.perfilEmergenciaReducido.tipoSangre')"
chk "trae alergias" "3" "$(echo "$T" | jq '.perfilEmergenciaReducido.alergias|length')"
chk "trae medicación de rescate" "2" "$(echo "$T" | jq '.perfilEmergenciaReducido.medicacionRescate|length')"
chk "tarjeta desconocida -> 404" "404" "$(curl -sS -o /dev/null -w '%{http_code}' "$API/tarjetas/NO_EXISTE/perfil-supervivencia")"
# Endpoint sin sesion: solo lo imprescindible para atender a alguien inconsciente.
chk "sin sesión NO se filtran teléfonos de familiares" "0" "$(echo "$T" | grep -o '5551111111\|5552222222' | wc -l | tr -d ' ')"
chk "sin sesión NO se filtran CURP ni NSS" "0" "$(echo "$T" | grep -o 'PEGJ850615HDFRRL09\|12345678901' | wc -l | tr -d ' ')"
chk "sin sesión NO se filtra el correo" "0" "$(echo "$T" | grep -c 'paciente1@salud.local' | tr -d ' ')"

echo
echo "C. AISLAMIENTO DEL EXPEDIENTE (RLS)"
chk "expediente sin token -> 401" "401" "$(curl -sS -o /dev/null -w '%{http_code}' "$API/paciente_expediente")"
chk "un paciente ve 1 expediente" "1" "$(GET "/paciente_expediente" "$TP1" | jq 'length')"
chk "y es el suyo" "$P1" "$(GET "/paciente_expediente" "$TP1" | jq -r '.[0].idPaciente')"
chk "un paciente NO lee el expediente de otro" "0" "$(GET "/paciente_expediente?idPaciente=eq.$P3" "$TP1" | jq 'length')"
chk "un médico vinculado SÍ lo lee" "1" "$(GET "/paciente_expediente?idPaciente=eq.$P1" "$TM1" | jq 'length')"
chk "un médico NO vinculado no lo lee" "0" "$(GET "/paciente_expediente?idPaciente=eq.$P1" "$TM6" | jq 'length')"
chk "ese médico sí lee a su propio paciente" "1" "$(GET "/paciente_expediente?idPaciente=eq.$P4" "$TM6" | jq 'length')"
E=$(GET "/paciente_expediente?idPaciente=eq.$P1" "$TP1")
chk "el expediente trae tratamientos" "3" "$(echo "$E" | jq '.[0].tratamientosActivos|length')"
chk "el expediente trae cirugías" "2" "$(echo "$E" | jq '.[0].historialClinico.cirugias|length')"
chk "el expediente trae la tarjeta" "$TARJETA" "$(echo "$E" | jq -r '.[0].dispositivosRfid[0].idTarjetaRfid')"
chk "el expediente trae médicos autorizados" "3" "$(echo "$E" | jq '.[0].controlAccesos.medicosAutorizados|length')"

echo
echo "D. CARTERA DEL MÉDICO Y RIESGO"
C=$(GET "/pacientes_vinculados" "$TM1")
chk "dr.silva ve su cartera" "2" "$(echo "$C" | jq 'length')"
chk "la cartera trae el nombre completo" "Juan Pérez García" "$(echo "$C" | jq -r --arg p "$P1" '.[]|select(.idPaciente==$p)|.nombreCompleto')"
chk "riesgo paciente 1 (buena adherencia)" "BAJO" "$(echo "$C" | jq -r --arg p "$P1" '.[]|select(.idPaciente==$p)|.riesgo')"
chk "riesgo paciente 2 (adherencia media)" "MEDIO" "$(GET "/pacientes_vinculados" "$TM2" | jq -r --arg p "$P2" '.[]|select(.idPaciente==$p)|.riesgo')"
chk "riesgo paciente 3 (adherencia baja)" "ALTO" "$(GET "/pacientes_vinculados" "$TM3" | jq -r --arg p "$P3" '.[]|select(.idPaciente==$p)|.riesgo')"
chk "un paciente no lee la cartera médica -> 403" "403" "$(GETS "/pacientes_vinculados" "$TP1")"

echo
echo "E. CHAT"
H=$(RPC obtener_historial "$TP1" '{"id_conversacion":"conv_001"}')
chk "el paciente lee su conversación" "6" "$(echo "$H" | jq 'length')"
chk "el acuse automático va marcado como tal" "1" "$(echo "$H" | jq '[.[]|select(.tipo=="ORIENTACION_INICIAL")]|length')"
chk "el médico lee la misma conversación" "6" "$(RPC obtener_historial "$TM1" '{"id_conversacion":"conv_001"}' | jq 'length')"
chk "otro paciente NO la lee -> 401" "401" "$(POSTS /rpc/obtener_historial "$TP2" '{"id_conversacion":"conv_001"}')"

echo
echo "F. DIARIO DE SÍNTOMAS"
chk "paciente 1 ve sus 3 entradas" "3" "$(GET "/entradas_diario" "$TP1" | jq 'length')"
chk "paciente 3 ve sus 3 entradas" "3" "$(GET "/entradas_diario" "$TP3" | jq 'length')"
chk "paciente 3 tiene entradas en ROJO" "2" "$(GET "/entradas_diario" "$TP3" | jq '[.[]|select(.severidad=="ROJO")]|length')"
chk "el médico vinculado ve el diario" "3" "$(GET "/entradas_diario?idPaciente=eq.$P1" "$TM1" | jq 'length')"
chk "un médico NO vinculado no lo ve" "0" "$(GET "/entradas_diario?idPaciente=eq.$P1" "$TM6" | jq 'length')"

echo
echo "G. ADHERENCIA"
HOY=$(date -u +%F)
chk "tomas_del_dia responde" "200" "$(POSTS /rpc/tomas_del_dia "$TP1" "{\"id_paciente\":\"$P1\",\"fecha\":\"$HOY\"}")"
chk "adherencia_resumen responde" "200" "$(POSTS /rpc/adherencia_resumen "$TP1" "{\"id_paciente\":\"$P1\",\"fecha_final\":\"$HOY\"}")"
chk "adherencia_semana responde" "200" "$(POSTS /rpc/adherencia_semana "$TP1" "{\"id_paciente\":\"$P1\",\"fecha_final\":\"$HOY\"}")"
chk "un paciente no lee la adherencia de otro -> 401" "401" "$(POSTS /rpc/adherencia_resumen "$TP1" "{\"id_paciente\":\"$P3\",\"fecha_final\":\"$HOY\"}")"

echo
echo "H. AGENDA Y DIRECTORIO"
A=$(RPC cargar_agenda "$TM1" "{\"id_medico\":\"$D1\"}")
chk "el médico carga su agenda con la cita de la demo" "1" "$(echo "$A" | jq 'length')"
chk "la cita está confirmada" "CONFIRMADA" "$(echo "$A" | jq -r '.[0].estado // .[0].value.estado // "?"')"
DIR=$(GET "/directorio_medico" "$TP1")
chk "el directorio muestra a los 6 médicos" "6" "$(echo "$DIR" | jq 'length')"
chk "todos tienen especialidad" "0" "$(echo "$DIR" | jq '[.[]|select(.especialidad==null)]|length')"
chk "el filtro de cardiología trae 2" "2" "$(GET "/directorio_medico?especialidad=eq.CARDIOLOGIA" "$TP1" | jq 'length')"

echo
echo "I. CHAT CON ADJUNTOS Y BAJA DE CUENTA (con un paciente desechable: la demo no se toca)"
if [ "$(curl -sS -o /dev/null -w '%{http_code}' "$API/auth/eliminar-cuenta")" = "404" ]; then
  echo "  (se omite: este backend todavía no tiene la baja; reconstrúyelo)"
else
  CB="baja.$$.$(date +%s)@salud.local"; TARJ="BAJA-$$"
  TB=$(curl -sS -X POST "$API/auth/pacientes" -H 'Content-Type: application/json' -d "{\"correo\":\"$CB\",\"contrasena\":\"$PASS\"}" | jq -r '.token')
  PB=$(sub "$TB")
  curl -sS -o /dev/null -X POST "$API/rpc/registrar_paciente" -H "Authorization: Bearer $TB" -H 'Content-Type: application/json' -H 'Prefer: params=single-object' \
    -d "{\"datosPersonales\":{\"nombre\":\"Baja\",\"apellidos\":\"Prueba\",\"telefono\":\"5550000000\"},\"contactosEmergencia\":[{\"nombre\":\"Contacto\",\"relacion\":\"Hermano\",\"telefono\":\"5551112222\",\"prioridad\":1}],\"dispositivosRfid\":[{\"idTarjetaRfid\":\"$TARJ\"}],\"perfilEmergenciaReducido\":{\"tipoSangre\":\"A+\",\"alergias\":[{\"alergeno\":\"Penicilina\"}]}}"
  CONVB=$(curl -sS -X POST "$API/rpc/solicitar_vinculacion" -H "Authorization: Bearer $TB" -H 'Content-Type: application/json' -d "{\"id_medico\":\"$D1\"}" | jq -r '.idConversacion // empty')

  echo "  -- chat con adjuntos, en su conversación con dr.silva --"
  if [ "$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$API/chat/sonda/adjuntos")" = "404" ]; then
    echo "  (se omite: este backend todavía no tiene adjuntos; reconstrúyelo)"
  else
    AHORA=$(date -u +%FT%TZ)
    chk "el paciente envía texto (RPC real de la app)" "PACIENTE" "$(RPC enviar_mensaje "$TB" "{\"id_conversacion\":\"$CONVB\",\"texto_mensaje\":\"Hola doctor\",\"instante_enviado\":\"$AHORA\",\"autor_remitente\":\"PACIENTE\"}" | jq -r '.[0].autor')"
    chk "un paciente NO puede firmar como MEDICO -> 400" "400" "$(POSTS /rpc/enviar_mensaje "$TB" "{\"id_conversacion\":\"$CONVB\",\"texto_mensaje\":\"Duplique la dosis\",\"instante_enviado\":\"$AHORA\",\"autor_remitente\":\"MEDICO\"}")"
    ARCH=$(mktemp); printf 'ESTUDIO-ESCANEADO-%s' "$$" > "$ARCH"
    SUB=$(curl -sS -X POST "$API/chat/$CONVB/adjuntos" -H "Authorization: Bearer $TB" -F "tipo=ESCANEO" -F "archivo=@$ARCH;type=image/jpeg;filename=estudio.jpg")
    ADJ=$(echo "$SUB" | jq -r '.idAdjunto // empty'); URLADJ=$(echo "$SUB" | jq -r '.url // empty')
    chk "sube un documento escaneado" "si" "$([ -n "$ADJ" ] && echo si || echo "no: $SUB")"
    chk "tipo de adjunto inválido -> 400" "400" "$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$API/chat/$CONVB/adjuntos" -H "Authorization: Bearer $TB" -F "tipo=VIDEO" -F "archivo=@$ARCH;type=image/jpeg;filename=x.jpg")"
    chk "subir sin sesión -> 401" "401" "$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$API/chat/$CONVB/adjuntos" -F "tipo=FOTO" -F "archivo=@$ARCH;type=image/jpeg;filename=x.jpg")"
    chk "envía el mensaje con el escaneo" "estudio.jpg" "$(RPC enviar_mensaje "$TB" "{\"id_conversacion\":\"$CONVB\",\"texto_mensaje\":\"\",\"instante_enviado\":\"$AHORA\",\"autor_remitente\":\"PACIENTE\",\"id_adjunto\":\"$ADJ\"}" | jq -r '.[0].adjunto.nombre')"
    chk "el historial del médico trae el adjunto" "$ADJ" "$(RPC obtener_historial "$TM1" "{\"id_conversacion\":\"$CONVB\"}" | jq -r '[.[]|select(.adjunto!=null)][0].adjunto.idAdjunto')"
    HDR=$(mktemp); BAJADO=$(curl -sS -D "$HDR" "$API$URLADJ" -H "Authorization: Bearer $TM1")
    chk "el médico lo descarga idéntico" "ESTUDIO-ESCANEADO-$$" "$BAJADO"
    chk "se descarga como adjunto, sin que el navegador lo interprete" "2" "$(grep -ciE '^(content-disposition: attachment|x-content-type-options: nosniff)' "$HDR")"
    chk "otro paciente NO puede descargarlo -> 401" "401" "$(curl -sS -o /dev/null -w '%{http_code}' "$API$URLADJ" -H "Authorization: Bearer $TP2")"
    rm -f "$ARCH" "$HDR"
  fi

  if [ "$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$API/rpc/mensajes_sin_leer" -H "Authorization: Bearer $TB" -H 'Content-Type: application/json' -d '{"id_conversacion":"x"}')" = "404" ]; then
    echo "  (se omiten citas, propuestas, no leídos y diario sincronizado: faltan las migraciones 0014 y 0015)"
  else
    echo "  -- mensajes sin leer --"
    AHORA=$(date -u +%FT%TZ)
    RPC enviar_mensaje "$TB" "{\"id_conversacion\":\"$CONVB\",\"texto_mensaje\":\"Otra duda\",\"instante_enviado\":\"$AHORA\",\"autor_remitente\":\"PACIENTE\"}" >/dev/null
    chk "el médico tiene mensajes sin leer del paciente" "si" "$([ "$(RPC mensajes_sin_leer "$TM1" "{\"id_conversacion\":\"$CONVB\"}")" -gt 0 ] 2>/dev/null && echo si || echo no)"
    POSTS /rpc/marcar_conversacion_leida "$TM1" "{\"id_conversacion\":\"$CONVB\"}" >/dev/null
    chk "al marcar como leída, baja a 0" "0" "$(RPC mensajes_sin_leer "$TM1" "{\"id_conversacion\":\"$CONVB\"}")"

    echo "  -- citas: el paciente agenda como lo hace la app --"
    MANANA=$(date -u -d '+1 day' +%F)
    LIBRES=$(RPC franjas_libres "$TB" "{\"id_medico\":\"$D1\",\"desde\":\"$MANANA\",\"ahora\":\"$AHORA\"}")
    F1=$(echo "$LIBRES" | jq -r '.[0].idFranja // empty'); F2=$(echo "$LIBRES" | jq -r '.[1].idFranja // empty')
    chk "hay horarios libres del médico" "si" "$([ -n "$F1" ] && [ -n "$F2" ] && echo si || echo no)"
    RES=$(RPC reservar_temporalmente "$TB" "{\"id_franja\":\"$F1\",\"ahora\":\"$AHORA\"}" | jq -r '.idReserva // empty')
    chk "reserva temporal de un horario" "si" "$([ -n "$RES" ] && echo si || echo no)"
    CONF=$(RPC confirmar_cita "$TB" "{\"id_reserva\":\"$RES\",\"id_paciente\":\"$PB\",\"contacto\":{\"nombreCompleto\":\"Baja Prueba\",\"telefono\":\"555\",\"correo\":\"$CB\",\"motivo\":\"Revision\"},\"ahora\":\"$AHORA\"}")
    chk "confirma la cita" "PENDIENTE" "$(echo "$CONF" | jq -r '.cita.estado')"
    chk "otro paciente ya no puede reservar ese horario -> 409" "409" "$(POSTS /rpc/reservar_temporalmente "$TP2" "{\"id_franja\":\"$F1\",\"ahora\":\"$AHORA\"}")"

    echo "  -- propuesta de cita del médico --"
    PROP=$(RPC proponer_cita "$TM1" "{\"id_franja\":\"$F2\",\"id_paciente\":\"$PB\",\"contacto\":{\"nombreCompleto\":\"Baja Prueba\",\"telefono\":\"555\",\"correo\":\"$CB\",\"motivo\":\"Control\"},\"ahora\":\"$AHORA\"}")
    CP=$(echo "$PROP" | jq -r '.idCita // empty')
    chk "el médico propone una cita" "PROPUESTA_MEDICO" "$(echo "$PROP" | jq -r '.estado')"
    chk "el médico NO puede confirmar su propia propuesta -> 409" "409" "$(POSTS /rpc/cambiar_estado "$TM1" "{\"id_cita\":\"$CP\",\"nuevo_estado\":\"CONFIRMADA\"}")"
    chk "otro paciente NO puede aceptarla -> 409" "409" "$(POSTS /rpc/aceptar_propuesta "$TP2" "{\"id_cita\":\"$CP\"}")"
    chk "el paciente la acepta" "CONFIRMADA" "$(RPC aceptar_propuesta "$TB" "{\"id_cita\":\"$CP\"}" | jq -r '.estado')"
    chk "aceptarla otra vez -> PROPUESTA_NO_DISPONIBLE" "PROPUESTA_NO_DISPONIBLE" "$(RPC aceptar_propuesta "$TB" "{\"id_cita\":\"$CP\"}" | jq -r '.message')"
    chk "el paciente ve sus 2 citas en la agenda del médico" "2" "$(RPC cargar_agenda "$TB" "{\"id_medico\":\"$D1\"}" | jq 'length')"

    echo "  -- diario: lo que escribe el paciente le llega al médico --"
    curl -sS -o /dev/null -X POST "$API/entradas_diario" -H "Authorization: Bearer $TB" -H 'Content-Type: application/json' -H 'Prefer: return=minimal' \
      -d "{\"idEntrada\":\"ent_baja_$$\",\"idPaciente\":\"$PB\",\"instante\":\"$AHORA\",\"fecha\":\"$(date -u +%F)\",\"texto\":\"Dolor en el pecho\",\"severidad\":\"ROJO\",\"terminosDetectados\":[\"dolor toracico\"]}"
    chk "subir la misma entrada otra vez no la duplica -> 409" "409" "$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$API/entradas_diario" -H "Authorization: Bearer $TB" -H 'Content-Type: application/json' -d "{\"idEntrada\":\"ent_baja_$$\",\"idPaciente\":\"$PB\",\"instante\":\"$AHORA\",\"fecha\":\"$(date -u +%F)\",\"texto\":\"x\",\"severidad\":\"ROJO\"}")"
    chk "el médico ve la alerta ROJA del paciente" "ROJO" "$(GET "/entradas_diario?idPaciente=eq.$PB&order=instante.desc&limit=1" "$TM1" | jq -r '.[0].severidad')"

    echo "  -- resumen con IA --"
    MID=$(RPC obtener_historial "$TM1" "{\"id_conversacion\":\"$CONVB\"}" | jq -r '[.[]|select(.autor=="PACIENTE")][0].idMensaje')
    chk "resumen sin sesión -> 401" "401" "$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$API/chat/mensajes/$MID/resumen-ia")"
    chk "resumen de un mensaje ajeno -> 401" "401" "$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$API/chat/mensajes/$MID/resumen-ia" -H "Authorization: Bearer $TP2")"
    RIA=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$API/chat/mensajes/$MID/resumen-ia" -H "Authorization: Bearer $TM1")
    chk "resumen del médico participante (200, o 503 si no hay DEEPSEEK_API_KEY)" "si" "$([ "$RIA" = "200" ] || [ "$RIA" = "503" ] && echo si || echo "no ($RIA)")"
  fi
  echo "  -- baja de la cuenta --"
  chk "preparación: su tarjeta abre el perfil" "200" "$(curl -sS -o /dev/null -w '%{http_code}' "$API/tarjetas/$TARJ/perfil-supervivencia")"
  chk "preparación: está en la cartera del médico" "1" "$(GET "/pacientes_vinculados" "$TM1" | jq --arg p "$PB" '[.[]|select(.idPaciente==$p)]|length')"
  chk "baja con contraseña incorrecta -> 401" "401" "$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$API/auth/pacientes/baja" -H 'Content-Type: application/json' -d "{\"correo\":\"$CB\",\"contrasena\":\"NoEsLaClave1\"}")"
  chk "baja con sus credenciales -> 204" "204" "$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$API/auth/pacientes/baja" -H 'Content-Type: application/json' -d "{\"correo\":\"$CB\",\"contrasena\":\"$PASS\"}")"
  chk "después: ya no inicia sesión" "401" "$(status_p "$CB" "$PASS")"
  chk "después: la tarjeta queda revocada -> 403" "403" "$(curl -sS -o /dev/null -w '%{http_code}' "$API/tarjetas/$TARJ/perfil-supervivencia")"
  chk "después: sale de la cartera del médico" "0" "$(GET "/pacientes_vinculados" "$TM1" | jq --arg p "$PB" '[.[]|select(.idPaciente==$p)]|length')"
  chk "después: el correo queda libre" "200" "$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$API/auth/pacientes" -H 'Content-Type: application/json' -d "{\"correo\":\"$CB\",\"contrasena\":\"$PASS\"}")"
  chk "la página pública explica qué se conserva" "1" "$(curl -sS "$API/auth/eliminar-cuenta" | grep -c 'Qué se conserva')"
  echo "  (paciente de prueba dado de baja: $PB)"
fi

echo
echo "J. PANEL WEB DEL MÉDICO (/api/v1, contrato Salud+ Panel Web) — el reinicio usa un paciente desechable"
V1="$API/api/v1"
if [ "$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$V1/auth/login" -H 'Content-Type: application/json' -d '{}')" = "404" ]; then
  echo "  (se omite: este backend todavía no tiene el panel; aplica las migraciones 0016-0017 y reconstrúyelo)"
else
  PANEL() { curl -sS -X "$1" "$V1$2" -H "Authorization: Bearer $3" -H 'Content-Type: application/json' ${4:+-d "$4"}; }
  PANELS() { curl -sS -o /dev/null -w '%{http_code}' -X "$1" "$V1$2" -H "Authorization: Bearer $3" -H 'Content-Type: application/json' ${4:+-d "$4"}; }
  RD=$(curl -sS -X POST "$V1/auth/login" -H 'Content-Type: application/json' -d "$(jq -nc --arg p "$PASS" '{correo:"dr.silva@salud.local",contrasena:$p}')")
  TD=$(echo "$RD" | jq -r '.token // empty')
  chk "el médico entra al panel con su cuenta de la app" "doctor" "$(echo "$RD" | jq -r '.usuario.rol')"
  chk "/auth/me es dr.silva" "$D1|medico" "$(PANEL GET /auth/me "$TD" | jq -r '"\(.id)|\(.rol)"')"
  chk "/auth/me sin token -> 401" "401" "$(curl -sS -o /dev/null -w '%{http_code}' "$V1/auth/me")"
  chk "un paciente no entra al panel -> 403" "403" "$(PANELS GET /auth/me "$TP1")"
  PRE=$(curl -sS -o /dev/null -D - -X OPTIONS "$V1/admin/pacientes" -H 'Origin: http://localhost:5173' -H 'Access-Control-Request-Method: PATCH' -H 'Access-Control-Request-Headers: authorization,content-type,x-request-id')
  chk "CORS: el panel en :5173 tiene permiso, con X-Request-ID" "2" "$(echo "$PRE" | grep -icE '^access-control-allow-origin: http://localhost:5173|^access-control-allow-headers:.*x-request-id')"
  chk "CORS: un sitio ajeno no" "0" "$(curl -sS -o /dev/null -D - -X OPTIONS "$V1/admin/pacientes" -H 'Origin: http://sitio-ajeno.example' -H 'Access-Control-Request-Method: GET' | grep -ic '^access-control-allow-origin')"
  chk "el puerto 8081 (el que usa el panel) responde" "200" "$(curl -sS -o /dev/null -w '%{http_code}' "${API%:*}:8081/api/v1/auth/me" -H "Authorization: Bearer $TD")"
  K=$(PANEL GET /admin/dashboard/kpis "$TD")
  chk "KPIs: las 8 claves del contrato" '["adherencia_global","alertas_pendientes","citas_hoy","pacientes_rfid","riesgo_amarillo","riesgo_rojo","riesgo_verde","total_pacientes"]' "$(echo "$K" | jq -c 'keys')"
  PS=$(PANEL GET /admin/pacientes "$TD")
  chk "pacientes: incluye a paciente1 con sus 28 claves" "28" "$(echo "$PS" | jq --arg p "$P1" '.[]|select(.id==$p)|keys|length')"
  chk "pacientes: NO incluye a paciente4 (no vinculado)" "0" "$(echo "$PS" | jq --arg p "$P4" '[.[]|select(.id==$p)]|length')"
  AL=$(PANEL GET /admin/alertas "$TD")
  chk "alertas: estado_atencion del contrato" "true" "$(echo "$AL" | jq '[.[].estado_atencion|IN("pendiente","en_revision","resuelto")]|all')"
  IDA=$(echo "$AL" | jq -r '[.[]|select(.estado_atencion=="pendiente")][0].id_alerta // empty')
  if [ -n "$IDA" ]; then
    chk "PATCH /alerts/{id} -> en_revision" "en_revision" "$(PANEL PATCH "/alerts/$IDA" "$TD" '{"status":"en_revision"}' | jq -r '.estado_atencion')"
    PANEL PATCH "/alerts/$IDA" "$TD" '{"status":"pendiente"}' >/dev/null
    chk "otro médico no la toca -> 403 (un 404 haría caer al panel a sus mocks)" "403" "$(PANELS PATCH "/alerts/$IDA" "$TM6" '{"status":"resuelto"}')"
  fi
  CV=$(PANEL GET /admin/conversaciones "$TD")
  CONV1=$(echo "$CV" | jq -r --arg p "$P1" '.[]|select(.idPaciente==$p).id')
  chk "chat: la conversación con paciente1" "si" "$([ -n "$CONV1" ] && echo si || echo "no: $CV")"
  chk "chat: sus mensajes en camelCase" "true" "$(PANEL GET "/admin/conversaciones/$CONV1/mensajes" "$TD" | jq 'length > 0 and (.[0]|has("idConversacion") and has("timestamp"))')"
  chk "chat: otro médico no la lee -> 403" "403" "$(PANELS GET "/admin/conversaciones/$CONV1/mensajes" "$TM6")"

  echo "  -- administrador del panel (gestiona cuentas, no ve expedientes) --"
  RA=$(curl -sS -X POST "$V1/auth/login" -H 'Content-Type: application/json' -d "$(jq -nc --arg p "$PASS" '{correo:"admin@salud.local",contrasena:$p}')")
  TA=$(echo "$RA" | jq -r '.token // empty')
  chk "admin@salud.local entra como administrador" "administrator" "$(echo "$RA" | jq -r '.usuario.rol')"
  chk "el administrador ve las cuentas de médicos y pacientes" "6|4" "$(PANEL GET /admin/cuentas/medicos "$TA" | jq length)|$(PANEL GET /admin/cuentas/pacientes "$TA" | jq length)"
  chk "el administrador NO ve expedientes -> 403" "403" "$(PANELS GET /admin/pacientes "$TA")"
  chk "un médico NO gestiona cuentas -> 403" "403" "$(PANELS GET /admin/cuentas/medicos "$TD")"

  echo "  -- reinicio de cuenta (paciente desechable vinculado a dr.silva) --"
  CR="reset.$$.$(date +%s)@salud.local"
  TR=$(curl -sS -X POST "$API/auth/pacientes" -H 'Content-Type: application/json' -d "{\"correo\":\"$CR\",\"contrasena\":\"$PASS\"}" | jq -r '.token'); PR=$(sub "$TR")
  curl -sS -o /dev/null -X POST "$API/rpc/registrar_paciente" -H "Authorization: Bearer $TR" -H 'Content-Type: application/json' -H 'Prefer: params=single-object' \
    -d '{"datosPersonales":{"nombre":"Reset","apellidos":"Prueba"},"perfilEmergenciaReducido":{"tipoSangre":"A+"}}'
  curl -sS -o /dev/null -X POST "$API/rpc/solicitar_vinculacion" -H "Authorization: Bearer $TR" -H 'Content-Type: application/json' -d "{\"id_medico\":\"$D1\"}"
  sleep 1
  CUERPO() { jq -nc --arg p "$PR" --arg c "$1" --arg o "$2" '{id_paciente:$p, motivo:"Prueba de verificar_demo", codigo_confirmacion:$c, password_operador:$o}'; }
  chk "sin escribir REINICIO -> 422" "422" "$(PANELS PATCH "/admin/usuarios/$PR/reset-cuenta" "$TD" "$(CUERPO NO "$PASS")")"
  chk "con la contraseña del médico mal -> 422 (y no cierra su sesión)" "422|200" "$(PANELS PATCH "/admin/usuarios/$PR/reset-cuenta" "$TD" "$(CUERPO REINICIO NoEsLaClave)")|$(PANELS GET /auth/me "$TD")"
  chk "un médico sin vínculo no puede -> 403" "403" "$(PANELS PATCH "/admin/usuarios/$PR/reset-cuenta" "$TM6" "$(CUERPO REINICIO "$PASS")")"
  RR=$(PANEL PATCH "/admin/usuarios/$PR/reset-cuenta" "$TD" "$(CUERPO REINICIO "$PASS")")
  TEMP=$(echo "$RR" | jq -r '.password_temporal // empty')
  chk "el reinicio devuelve {ok, evento_id, password_temporal}" '["evento_id","ok","password_temporal"]' "$(echo "$RR" | jq -c 'keys')"
  chk "el token viejo queda cerrado también en PostgREST" "SESION_REVOCADA" "$(GET /paciente_expediente "$TR" | jq -r '.message')"
  chk "la contraseña anterior ya no entra" "401" "$(status_p "$CR" "$PASS")"
  LT=$(login_p "$CR" "$TEMP"); TT=$(echo "$LT" | jq -r '.token')
  chk "entra con la temporal y debe cambiarla" "true" "$(echo "$LT" | jq '.requiereCambioContrasena')"
  chk "hasta cambiarla, PostgREST le responde 403" "403" "$(GETS /paciente_expediente "$TT")"
  chk "el panel lo muestra como reseteada" "reseteada" "$(PANEL GET /admin/pacientes "$TD" | jq -r --arg p "$PR" '.[]|select(.id==$p).estado_cuenta')"
  NUEVA="Nueva$$Clave"
  CN=$(curl -sS -X POST "$API/auth/pacientes/contrasena" -H "Authorization: Bearer $TT" -H 'Content-Type: application/json' -d "{\"contrasenaNueva\":\"$NUEVA\"}")
  chk "elige su contraseña nueva" "false" "$(echo "$CN" | jq '.requiereCambioContrasena')"
  chk "con el token nuevo ve su expediente" "1" "$(GET /paciente_expediente "$(echo "$CN" | jq -r '.token')" | jq 'length')"
  chk "la auditoría registra el reinicio" "reset_cuenta|Prueba de verificar_demo" "$(PANEL GET /admin/auditoria "$TD" | jq -r --arg p "$PR" '[.[]|select(.id_paciente==$p)][0]|"\(.tipo)|\(.motivo)"')"
  curl -sS -o /dev/null -X POST "$API/auth/pacientes/baja" -H 'Content-Type: application/json' -d "{\"correo\":\"$CR\",\"contrasena\":\"$NUEVA\"}"
  echo "  (paciente desechable dado de baja: $PR)"
fi

echo
echo "──────────────────────────────────────────"
printf '  %s en verde, %s en rojo\n' "$ok" "$fail"
if [ "$fail" -gt 0 ]; then
  echo; echo "  Fallos:"
  for f in "${FALLOS[@]}"; do echo "   - $f"; done
  exit 1
fi
echo
