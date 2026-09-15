#!/usr/bin/env bash
# Regenera los iconos de la app y los graficos de la ficha de Play Store a
# partir de la misma geometria que androidApp/src/main/res/drawable/
# ic_launcher_foreground.xml. Si se cambia el glifo alla, se cambia aqui.
#
# Uso:  bash frontend/playstore/generar.sh        (requiere rsvg-convert)
set -euo pipefail

AQUI="$(cd "$(dirname "$0")" && pwd)"
RES="$AQUI/../androidApp/src/main/res"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

DEGRADADO='<linearGradient id="fondo" x1="0" y1="0" x2="108" y2="108" gradientUnits="userSpaceOnUse">
  <stop offset="0" stop-color="#0E5EA3"/><stop offset="1" stop-color="#083B69"/></linearGradient>'

# Cruz medica con electrocardiograma, en el espacio de 108x108 del icono adaptativo.
glifo() { # escala
  cat <<EOF
<g transform="translate(54 54) scale($1) translate(-54 -54)">
  <rect x="44.5" y="30" width="19" height="48" rx="5" fill="#FFFFFF"/>
  <rect x="30" y="44.5" width="48" height="19" rx="5" fill="#FFFFFF"/>
  <path d="M34,54H45.5L49,47.5L53.5,60.5L57,50.5L59,54H74" fill="none" stroke="#0A4C86"
        stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round"/>
</g>
EOF
}

svg() { # nombre contenido
  printf '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108"><defs>%s</defs>%s</svg>' \
    "$DEGRADADO" "$2" > "$TMP/$1.svg"
}

# Launcher de Android 7 (API 24-25): el adaptativo solo existe desde Android 8.
svg cuadrado "<rect x=\"4\" y=\"4\" width=\"100\" height=\"100\" rx=\"20\" fill=\"url(#fondo)\"/>$(glifo 1.3)"
svg redondo  "<circle cx=\"54\" cy=\"54\" r=\"50\" fill=\"url(#fondo)\"/>$(glifo 1.25)"

for par in mdpi:48 hdpi:72 xhdpi:96 xxhdpi:144 xxxhdpi:192; do
  densidad=${par%%:*}; px=${par##*:}
  rsvg-convert -w "$px" -h "$px" "$TMP/cuadrado.svg" -o "$RES/mipmap-$densidad/ic_launcher.png"
  rsvg-convert -w "$px" -h "$px" "$TMP/redondo.svg"  -o "$RES/mipmap-$densidad/ic_launcher_round.png"
done

# Icono de alta resolucion para Play Console: 512x512, cuadrado completo
# (Play aplica su propia mascara redondeada).
svg play "<rect width=\"108\" height=\"108\" fill=\"url(#fondo)\"/>$(glifo 1.2)"
rsvg-convert -w 512 -h 512 "$TMP/play.svg" -o "$AQUI/icono-512.png"

# Grafico destacado de la ficha: 1024x500, obligatorio para publicar.
cat > "$TMP/destacado.svg" <<'EOF'
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 500" width="1024" height="500">
  <defs>
    <linearGradient id="f" x1="0" y1="0" x2="1024" y2="500" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#0E5EA3"/><stop offset="1" stop-color="#083B69"/>
    </linearGradient>
  </defs>
  <rect width="1024" height="500" fill="url(#f)"/>
  <!-- Pulso tenue al pie, lejos del logotipo para no competir con el. -->
  <path d="M0,440H730L752,402L782,476L806,420L820,440H1024" fill="none" stroke="#4DA3FF"
        stroke-opacity="0.2" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/>
  <!-- Logotipo centrado: glifo de 200px alineado al centro del bloque de texto. -->
  <g transform="translate(90 162) scale(4.1667) translate(-30 -30)">
    <rect x="44.5" y="30" width="19" height="48" rx="5" fill="#FFFFFF"/>
    <rect x="30" y="44.5" width="48" height="19" rx="5" fill="#FFFFFF"/>
    <path d="M34,54H45.5L49,47.5L53.5,60.5L57,50.5L59,54H74" fill="none" stroke="#0A4C86"
          stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round"/>
  </g>
  <text x="348" y="250" font-family="Noto Sans, DejaVu Sans, sans-serif" font-size="112"
        font-weight="800" fill="#FFFFFF" letter-spacing="-2">Salud</text>
  <text x="352" y="310" font-family="Noto Sans, DejaVu Sans, sans-serif" font-size="30"
        font-weight="500" fill="#FFFFFF" fill-opacity="0.86">Expediente, medicación y emergencias</text>
  <text x="352" y="350" font-family="Noto Sans, DejaVu Sans, sans-serif" font-size="30"
        font-weight="500" fill="#FFFFFF" fill-opacity="0.86">en un solo lugar.</text>
</svg>
EOF
rsvg-convert -w 1024 -h 500 "$TMP/destacado.svg" -o "$AQUI/grafico-destacado-1024x500.png"

echo "listo: iconos del launcher en res/mipmap-*, icono-512.png y grafico-destacado-1024x500.png en $AQUI"
