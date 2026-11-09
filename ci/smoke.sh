#!/usr/bin/env bash
# Abre la app en un Android virtual, navega por las pantallas y guarda informe + capturas.
set +e
P=com.nioro.life
adb install -r NioroLife.apk > install.txt 2>&1
timeout 30 adb shell pm grant $P android.permission.POST_NOTIFICATIONS
adb logcat -c
(adb logcat -v time > live.log 2>&1 &)
timeout 60 adb shell am start -W -n $P/.MainActivity > start.txt 2>&1
sleep 10

ui()   { rm -f ui.xml; timeout 40 adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; timeout 20 adb pull /sdcard/ui.xml ui.xml > /dev/null 2>&1; [ -f ui.xml ] || echo "<hierarchy/>" > ui.xml; }
shot() { timeout 30 adb exec-out screencap -p > "$1.png"; }
tap()  { ui; XY=$(python3 ci/tap.py "$1") && { timeout 20 adb shell input tap $XY; echo "  tap '$1' ($XY)"; } || echo "  NO ENCONTRADO: '$1'"; sleep "${2:-2}"; }
type_in() { tap "$1" 1; timeout 20 adb shell input text "$2"; sleep 1; }
look() { ui; echo "[$1] $(python3 ci/texts.py)"; shot "$1"; }
settheme() {
  printf '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n<map>\n    <int name="theme" value="%s" />\n</map>\n' "$1" > theme.xml
  timeout 20 adb shell am force-stop $P
  timeout 30 adb push theme.xml /data/local/tmp/theme.xml > /dev/null 2>&1
  timeout 30 adb shell "run-as $P mkdir -p shared_prefs; run-as $P cp /data/local/tmp/theme.xml shared_prefs/nioro.xml"
  timeout 60 adb shell am start -W -n $P/.MainActivity > /dev/null 2>&1
  sleep 7
}
back() { timeout 20 adb shell input keyevent 4; sleep 2; }

{
  echo "== PASOS"
  look 01_home_noche

  settheme 2; look 30_home_rosa; sleep 4; shot 30b_rosa
  settheme 3; look 31_home_rojo; sleep 4; shot 31b_rojo
  settheme 4; look 32_home_morado; sleep 4; shot 32b_morado
  settheme 0

  timeout 20 adb shell input swipe 540 1800 540 900 300; sleep 1
  tap "=Listas" 2; look 20_listas_vacio
  tap "+ Nueva lista" 2; look 21_nueva_lista
  tap "=Crear" 2; look 22_lista_abierta
  type_in "=Elemento" "Leche"; timeout 20 adb shell input keyevent 66; sleep 1; timeout 20 adb shell input keyevent 66; sleep 2; look 23_lista_con_item
  tap "=Leche" 2; look 24_item_hecho; back; back; back
  tap "=Finanzas" 2; tap "Cobranzas" 2; look 25_cobranzas
  tap "+ Nueva cobranza" 2; type_in "Qui" "Juan"; type_in "Monto o cuenta" "50000"; back; tap "=Guardar" 2; look 26_cobranza_guardada
  tap "Asistente" 2; type_in "Escribe" "45%slucas%smas%siva"; timeout 20 adb shell input keyevent 66; sleep 2; look 27_asistente
  back; back; back

  tap "=Reloj"; look 02a_reloj
  tap "=Sábado" 1; tap "=Domingo" 1; look 02b_reloj_sin_finde
  tap "=Todos" 1; look 02c_reloj_todos; back

  tap "=Finanzas"; look 02_finanzas_vacio
  tap "+ Ingreso"; look 03_dialogo_ingreso
  type_in "Monto o cuenta" "450000+35000"; look 04_calculadora
  type_in "Concepto" "Sueldo"
  type_in "Por qu" "Trabajo"
  back; tap "=Guardar" 2; look 05_ingreso_guardado
  tap "+ Gasto"; type_in "Monto o cuenta" "120000"; type_in "Concepto" "Arriendo"; type_in "Por qu" "Vivienda"
  back; tap "=Guardar" 2
  tap "+ Deuda"; type_in "Monto o cuenta" "30000"; type_in "debes" "Prestamo"; type_in "Por qu" "Amigo"
  back; tap "=Guardar" 2; look 06_finanzas_noche
  back; look 07_home_con_saldo

  # los pasos de finanzas terminan con "atrás" y pueden dejar la app cerrada: se vuelve a abrir
  timeout 60 adb shell am start -W -n $P/.MainActivity > /dev/null 2>&1; sleep 5
  tap "=Entreno" 3; look 40_entreno
  tap "=Ejercicios" 2; look 41_ejercicios
  type_in "Buscar ejercicio" "press"; look 41b_buscar; back
  tap "=Timer" 2; look 42_timer
  tap "=Empezar" 3; look 42b_timer_corriendo
  tap "=Progreso" 2; look 43_progreso
  tap "=Entrenar" 2; tap "=Entrenamiento libre" 3; look 44_libre
  tap "+ Añadir ejercicio" 2; tap "=Flexiones" 1; tap "Añadir 1" 2; look 45_con_ejercicio
  back; back; look 46_home_tras_entreno

  timeout 60 adb shell am start -W -n $P/.MainActivity > /dev/null 2>&1; sleep 5
  tap "=Casa" 3; look 50_casa
  tap "+ Añadir ampolleta" 2; look 51_anadir
  tap "Por IP" 1; type_in "IP, ej" "192.168.1.77"; back; tap "=Añadir" 2; look 52_casa_con_ampolleta
  tap "Ampolleta 77" 3; look 53_detalle
  back; tap "Cómo liberar" 2; look 54_guia; back
  tap "Enlazar con Smart Life" 2; look 55_enlazar_smartlife; back
  back  # vuelve de Casa al inicio para los pasos del menú

  timeout 60 adb shell am start -W -n $P/.MainActivity > /dev/null 2>&1; sleep 5
  tap "Men" 3; look 08_menu_noche
  tap "=Día"; look 09_menu_dia
  tap "Buscar actualiz" 9; look 10_actualizacion
  back; look 11_home_dia
  tap "=Notas"; look 13_notas_dia; back
  tap "=Finanzas"; look 14_finanzas_dia; back
} > steps.txt 2>&1

timeout 60 adb logcat -d > full.log 2>&1
{
  echo "== instalacion"; cat install.txt
  echo "== arranque"; cat start.txt
  echo "== proceso vivo?"; adb shell pidof $P || echo "NO ESTA VIVO"
  echo "== errores graves"
  grep -E "FATAL EXCEPTION|ANR in|Process: $P" full.log | head -20
  grep -E -A14 "FATAL EXCEPTION" full.log | head -80
  echo; cat steps.txt
  echo "== lineas clave del log en vivo"; grep -E "nioro|AndroidRuntime|ANR|lowmemory|Out of memory|OutOfMemory|lmkd|Killing|has died|Fatal|SIGSEGV|tombstone|SurfaceSyncGroup|Choreographer|Davey" live.log | cut -c1-240 | tail -n 70; echo "== ultimas 15 lineas"; tail -n 15 live.log | cut -c1-200
} > report.txt 2>&1
