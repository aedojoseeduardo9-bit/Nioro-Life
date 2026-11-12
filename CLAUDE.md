# Nioro Life — guía para Claude

App Android nativa (Kotlin + Jetpack Compose), sin web. El dueño habla **español**: responde siempre en español,
claro y sin tecnicismos innecesarios.

## Ver la app (los "ojos")
No hay SDK de Android en el contenedor, pero la app **sí se puede ver**:

1. Cada push a `main` corre `.github/workflows/build.yml`. El job `smoke` abre la app en un Android virtual
   (Pixel 5, API 30), recorre las pantallas con `ci/smoke.sh` y sube **capturas PNG + `report.txt`**
   al release **`diagnostico`** (unos 8–10 min después del push).
2. Para mirarlas, descárgalas a tu scratchpad y ábrelas con la herramienta **Read** (lee imágenes):
   ```bash
   bash tools/capturas.sh <carpeta_scratchpad>        # todas
   bash tools/capturas.sh <carpeta_scratchpad> 4      # solo las que empiezan por 4 (entrenamiento)
   ```
   Las URLs son `https://github.com/aedojoseeduardo9-bit/Nioro-Life/releases/download/diagnostico/<nombre>.png`.
3. `report.txt` trae: instalación, arranque, si el proceso sigue vivo, **errores graves** (FATAL EXCEPTION / ANR)
   y el texto visible en cada paso.
4. Para ver una pantalla nueva, añade pasos en `ci/smoke.sh` (`tap "=Texto exacto"`, `tap "texto parcial"`,
   `type_in`, `look <nombre>` = captura + texto). Ojo: `back` en el inicio cierra la app; reábrela con
   `adb shell am start -W -n $P/.MainActivity`.
5. **Después de cualquier cambio visual, mira las capturas** antes de dar el trabajo por terminado.
   Si el usuario manda capturas de su teléfono (Samsung S21) en el chat, úsalas también.

## Compilar y probar
- En el contenedor **no se puede compilar**: `dl.google.com` (Google Maven / SDK) está bloqueado.
- Push a cualquier rama que no sea `main` → workflow **"Verificar rama"** (`check.yml`): `assembleDebug` +
  `testDebugUnitTest`, sin publicar nada. Revisa el resultado con las herramientas `mcp__github__actions_*`
  (y `get_job_logs` si falla). Espera con `sleep` en segundo plano, no en primer plano.
- Pruebas unitarias en `app/src/test/java/com/nioro/life/` (lógica pura; `org.json` no funciona en ellas).

## Publicar una actualización
El usuario quiere que **las actualizaciones le lleguen solas** a la app. El flujo:
1. Trabajar en la rama asignada → push → esperar "Verificar rama" en verde.
2. Crear PR a `main` y fusionarlo (`mcp__github__create_pull_request` + `merge_pull_request`).
3. `build.yml` compila y publica el release `latest` con `NioroLife.apk` y `build:<número>` en las notas;
   el actualizador de la app (`Updater.kt`) compara ese número con su `versionCode` y avisa.
4. Si la rama ya se fusionó antes, reiníciala desde `origin/main` antes de seguir.
5. Cambios solo de documentación (`*.md`, `tools/`) no disparan una compilación nueva.

## Mapa del código (`app/src/main/java/com/nioro/life/`)
- `MainActivity.kt` — navegación (`screen`), transiciones, dock del inicio.
- `Screens.kt` — Inicio, Reloj, Notas. `Ui.kt` — Glass, Chip, botones, íconos, `Modifier.rise()`.
- `Dock.kt` — dock flotante (Entreno, Finanzas, Listas). `Theme.kt` / `Ambient.kt` — temas y fondos animados.
- `Training.kt` (pestañas, biblioteca, rutinas, timer, progreso), `ActiveWorkout.kt` (entrenamiento en curso,
  descanso, `Buzz`), `Workout.kt` (datos, cálculos, `TrainStore`), `ExerciseCatalog.kt` (ejercicios y rutinas).
- `Casa.kt` (ampolletas Wi-Fi: lista, detalle, rueda de color, escenas, añadir, guía) y `SmartHome.kt`
  (red: comandos HTTP Tasmota/OpenBeken `/cm?cmnd=`, búsqueda en la red, Wi-Fi de ampolleta nueva).
  `TuyaCloud.kt`: puente con Smart Life por la API oficial de Tuya (proyecto gratuito en iot.tuya.com enlazado
  a la cuenta de Smart Life con un QR; Access ID + Secret, firma HMAC-SHA256). Traduce `LightCmd` a códigos
  Tuya (`switch_led`, `work_mode`, `bright_value_v2`, `temp_value_v2`, `colour_data_v2`, `scene_data_v2`,
  `countdown_1`). Efectos animados (`LIGHT_EFFECTS` + propios) y apagado programado solo en equipos Smart Life.
- Asistente **Nioro IA** (cerebro: Gemini, clave gratis de aistudio.google.com guardada en el teléfono):
  `Bubble.kt` (burbuja flotante arrastrable que abre el asistente; `greeting(hora)`), `Assistant.kt` (pantalla:
  esfera `NioroOrb`, apertura animada, saludo hablado, voz con `SpeechRecognizer`/`TextToSpeech`, chat, tarjetas de
  confirmación, ajustes y permisos), `AssistantTools.kt` (las acciones que Gemini puede llamar: contactos, llamar,
  SMS, WhatsApp, leer/responder mensajes, correo, alarmas, calendario, mapas, búsqueda web, notas, finanzas,
  listas, luces, sueño, entreno), `GeminiClient.kt` (API REST `generateContent`, historial con firmas de
  pensamiento intactas) y `NotiListener.kt` (lee notificaciones y responde con su acción "Responder").
  Llamar, enviar SMS y responder mensajes SIEMPRE piden confirmación en pantalla.
- `Finance.kt`, `FinanceAI.kt`, `Export.kt`, `Lists.kt`, `Alarms.kt`, `Updater.kt`, `Data.kt` (`Store`).

## Pendiente conocido
- En las capturas se ve un rectángulo más claro detrás del texto dentro de algunas tarjetas de vidrio
  (anillo del sueño, Reloj/Notas, Meta semanal, filas de ejercicios). Falta investigar si es de la app o del emulador.
