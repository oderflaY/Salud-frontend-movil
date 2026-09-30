#!/usr/bin/env bash
# Enciende todo el sistema Salud y comprueba que quedó funcionando.
#
#   sudo bash scripts/arrancar.sh
#
# Levanta la base, aplica las migraciones pendientes, recompila el backend si
# cambió el código, enciende PostgREST y el proxy, y al final revisa cada
# pieza: servidor, traducción y la dirección que usan el teléfono y el panel.
set -uo pipefail

cd "$(dirname "$0")/.."

verde() { printf '\033[32m  ✔ %s\033[0m\n' "$1"; }
rojo() { printf '\033[31m  ✘ %s\033[0m\n' "$1"; }
aviso() { printf '\033[33m  ! %s\033[0m\n' "$1"; }

if [ ! -f .env ]; then
  rojo "Falta el archivo .env (cópialo de .env.example y llénalo)."
  exit 1
fi

echo "== Encendiendo (la primera vez tarda: compila el backend) =="
if ! docker compose up -d --build db migrate postgrest backend proxy; then
  rojo "docker compose falló. Revisa el mensaje de arriba."
  echo "   Si fue una migración: docker compose logs migrate"
  exit 1
fi

puerto=$(grep -E '^PROXY_PORT=' .env | cut -d= -f2)
puerto=${puerto:-8000}

echo
echo "== Esperando a que el servidor responda =="
listo=0
for _ in $(seq 1 90); do
  if [ "$(curl -s -m 2 "http://localhost:$puerto/healthz")" = "ok" ]; then
    listo=1
    break
  fi
  sleep 2
done

echo
echo "== Revisión =="
if [ "$listo" = 1 ]; then
  verde "Servidor encendido en el puerto $puerto"
else
  rojo "El servidor no respondió en 3 minutos."
  echo "   Revisa: docker compose logs --tail=50 backend"
  exit 1
fi

if [ "$(docker compose ps -a --format '{{.Service}} {{.ExitCode}}' 2>/dev/null | awk '$1=="migrate"{print $2}')" = "0" ]; then
  verde "Migraciones aplicadas"
else
  aviso "No pude confirmar las migraciones: docker compose logs migrate"
fi

url_ollama=$(grep -E '^OLLAMA_URL=' .env | cut -d= -f2-)
if [ -z "$url_ollama" ]; then
  aviso "Traducción sin configurar (falta OLLAMA_URL en .env)"
elif curl -s -m 15 -o /dev/null -w '%{http_code}' -H 'ngrok-skip-browser-warning: true' "$url_ollama/api/tags" | grep -q 200; then
  verde "Traducción lista ($url_ollama)"
else
  aviso "El servidor de traducción no responde: ¿están encendidos Ollama y ngrok?"
  echo "   Si ngrok te dio otra dirección, cámbiala en OLLAMA_URL (.env) y vuelve a correr este script."
fi

ip=$(ip -4 addr show 2>/dev/null | awk '/inet / && $2 !~ /^127\./ {sub(/\/.*/, "", $2); print $2; exit}')
echo
echo "== Cómo conectarse =="
echo "  App del teléfono : misma red wifi; busca el servidor sola (http://${ip:-TU_IP}:$puerto)"
echo "  Panel web        : npm run dev en Salud-Frontend y abre http://localhost:5173"
echo "  Apagar todo      : sudo docker compose down"
