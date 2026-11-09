#!/usr/bin/env bash
# Descarga las capturas y el informe de la última prueba en el Android virtual (release "diagnostico").
# Uso: bash tools/capturas.sh [carpeta_destino] [filtro]
#   bash tools/capturas.sh /tmp/shots          → todas
#   bash tools/capturas.sh /tmp/shots 4        → solo las que empiezan por "4" (entrenamiento)
set -e
DEST="${1:-./capturas}"
FILTRO="${2:-}"
REPO="aedojoseeduardo9-bit/Nioro-Life"
BASE="https://github.com/$REPO/releases/download/diagnostico"
mkdir -p "$DEST"
curl -sSL -o "$DEST/report.txt" "$BASE/report.txt"
# nombres de las capturas: salen de los pasos "[nombre]" del informe
grep -o '^\[[0-9][0-9a-z_]*\]' "$DEST/report.txt" | tr -d '[]' | sort -u | while read -r n; do
  case "$n" in "$FILTRO"*) ;; *) continue ;; esac
  curl -sSL -f -o "$DEST/$n.png" "$BASE/$n.png" && echo "ok  $n.png" || echo "sin captura: $n"
done
# capturas extra que no aparecen como paso (p. ej. 30b_rosa)
for n in 30b_rosa 31b_rojo 32b_morado; do
  case "$n" in "$FILTRO"*) curl -sSL -f -o "$DEST/$n.png" "$BASE/$n.png" 2>/dev/null && echo "ok  $n.png" ;; esac
done
echo "== errores graves del informe:"
sed -n '/== errores graves/,/== PASOS/p' "$DEST/report.txt" | sed '1d;$d'
