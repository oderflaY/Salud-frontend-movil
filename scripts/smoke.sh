#!/usr/bin/env bash
# Smoke test end-to-end contra el stack completo levantado con
# `docker compose up`. Recorre los 11 módulos del documento de contratos
# usando los endpoints reales (proxy en :8000). No sustituye a pgTAP/cargo
# test — es la última verificación, contra el sistema desplegado tal cual.
set -euo pipefail

BASE="${SMOKE_BASE_URL:-http://localhost:8000}"
CORREO_PACIENTE="smoke.paciente.$$@example.com"
CORREO_MEDICO="smoke.medico.$$@example.com"
CEDULA="CED-SMOKE-$$"
fallos=0

paso() { echo "== $1 =="; }
verificar() {
  # verificar <descripcion> <status_esperado> <status_obtenido>
  if [ "$3" = "$2" ]; then
    echo "  ok: $1 ($3)"
  else
    echo "  FALLO: $1 (esperado $2, obtuvo $3)"
    fallos=$((fallos + 1))
  fi
}

paso "1-2: autenticación paciente y profesional"
TOKEN_P=$(curl -s -X POST "$BASE/auth/pacientes" -H "Content-Type: application/json" \
  -d "{\"correo\":\"$CORREO_PACIENTE\",\"contrasena\":\"clave-super-segura\"}" | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")
ID_PACIENTE=$(echo "$TOKEN_P" | python3 -c "import sys,base64,json;t=sys.stdin.read().strip().split('.')[1];t+='='*(-len(t)%4);print(json.loads(base64.urlsafe_b64decode(t))['sub'])")
TOKEN_M=$(curl -s -X POST "$BASE/auth/profesionales" -H "Content-Type: application/json" \
  -d "{\"correo\":\"$CORREO_MEDICO\",\"contrasena\":\"clave-super-segura\",\"nombre\":\"Elena\",\"apellidos\":\"Ruiz\",\"tratamiento\":\"Dra.\",\"cedulaProfesional\":\"$CEDULA\"}" | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")
ID_MEDICO=$(echo "$TOKEN_M" | python3 -c "import sys,base64,json;t=sys.stdin.read().strip().split('.')[1];t+='='*(-len(t)%4);print(json.loads(base64.urlsafe_b64decode(t))['sub'])")
[ -n "$ID_PACIENTE" ] && [ -n "$ID_MEDICO" ] && echo "  ok: tokens obtenidos" || { echo "  FALLO: no se obtuvieron tokens"; fallos=$((fallos+1)); }

paso "3: expediente del paciente"
S=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/rpc/registrar_paciente" -H "Authorization: Bearer $TOKEN_P" -H "Content-Type: application/json" -H "Prefer: params=single-object" \
  -d '{"datosPersonales":{"nombre":"Ana","apellidos":"Torres"},"tratamientosActivos":[{"medicamento":"Metformina","horariosSugeridos":["08:00"],"fechaInicio":"2020-01-01"}]}')
verificar "registrar_paciente" 200 "$S"

paso "5: emergencia RFID (tarjeta desconocida, sin sesión)"
S=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/tarjetas/rfid_no_existe/perfil-supervivencia")
verificar "tarjeta desconocida" 404 "$S"

paso "4: adherencia"
FECHA=$(date -u +%F)
S=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/rpc/tomas_del_dia" -H "Authorization: Bearer $TOKEN_P" -H "Content-Type: application/json" -d "{\"id_paciente\":\"$ID_PACIENTE\",\"fecha\":\"$FECHA\"}")
verificar "tomas_del_dia" 200 "$S"

paso "7: directorio y vinculación"
curl -s -o /dev/null -X POST "$BASE/rpc/actualizar_mi_perfil_directorio" -H "Authorization: Bearer $TOKEN_M" -H "Content-Type: application/json" -H "Prefer: params=single-object" -d '{"especialidad":"CARDIOLOGIA"}'
CONV=$(curl -s -X POST "$BASE/rpc/solicitar_vinculacion" -H "Authorization: Bearer $TOKEN_P" -H "Content-Type: application/json" -d "{\"id_medico\":\"$ID_MEDICO\"}" | python3 -c "import sys,json;print(json.load(sys.stdin).get('idConversacion',''))")
[ -n "$CONV" ] && echo "  ok: vinculación creó conversación $CONV" || { echo "  FALLO: solicitar_vinculacion"; fallos=$((fallos+1)); }

paso "6: cartera de pacientes del médico"
S=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/pacientes_vinculados" -H "Authorization: Bearer $TOKEN_M")
verificar "pacientes_vinculados" 200 "$S"

paso "8: chat"
S=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/rpc/enviar_mensaje" -H "Authorization: Bearer $TOKEN_P" -H "Content-Type: application/json" \
  -d "{\"id_conversacion\":\"$CONV\",\"texto_mensaje\":\"hola\",\"instante_enviado\":\"$(date -u +%FT%TZ)\",\"autor_remitente\":\"PACIENTE\"}")
verificar "enviar_mensaje" 200 "$S"

paso "9: citas y agenda"
MANANA=$(date -u -d '+1 day' +%F 2>/dev/null || date -v+1d +%F)
curl -s -o /dev/null -X POST "$BASE/rpc/generar_franjas" -H "Authorization: Bearer $TOKEN_M" -H "Content-Type: application/json" \
  -d "{\"fecha_inicio\":\"$MANANA\",\"fecha_fin\":\"$MANANA\",\"hora_inicio\":\"09:00\",\"hora_fin\":\"10:00\",\"duracion_minutos\":30}"
FRANJA=$(curl -s -X POST "$BASE/rpc/franjas_libres" -H "Authorization: Bearer $TOKEN_M" -H "Content-Type: application/json" -d "{\"id_medico\":\"$ID_MEDICO\",\"desde\":\"$MANANA\",\"ahora\":\"$(date -u +%FT%TZ)\"}" | python3 -c "import sys,json;print(json.load(sys.stdin)[0]['idFranja'])")
RESERVA=$(curl -s -X POST "$BASE/rpc/reservar_temporalmente" -H "Authorization: Bearer $TOKEN_P" -H "Content-Type: application/json" -d "{\"id_franja\":\"$FRANJA\",\"ahora\":\"$(date -u +%FT%TZ)\"}" | python3 -c "import sys,json;print(json.load(sys.stdin)['idReserva'])")
S=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/rpc/confirmar_cita" -H "Authorization: Bearer $TOKEN_P" -H "Content-Type: application/json" \
  -d "{\"id_reserva\":\"$RESERVA\",\"id_paciente\":\"$ID_PACIENTE\",\"contacto\":{\"nombreCompleto\":\"Ana\",\"telefono\":\"555\",\"correo\":\"a@b.com\",\"motivo\":\"chequeo\"},\"ahora\":\"$(date -u +%FT%TZ)\"}")
verificar "confirmar_cita" 200 "$S"

paso "10: diario de síntomas"
S=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/entradas_diario" -H "Authorization: Bearer $TOKEN_P" -H "Content-Type: application/json" \
  -d "{\"idPaciente\":\"$ID_PACIENTE\",\"instante\":\"$(date -u +%FT%TZ)\",\"fecha\":\"$FECHA\",\"texto\":\"todo bien\",\"severidad\":\"VERDE\",\"terminosDetectados\":[]}")
verificar "guardar entrada de diario" 201 "$S"

echo
if [ "$fallos" -eq 0 ]; then
  echo "SMOKE TEST: todo en verde (11 módulos)"
else
  echo "SMOKE TEST: $fallos fallo(s)"
  exit 1
fi
