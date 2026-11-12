# Nioro Life — app Android nativa (Kotlin + Jetpack Compose)

App 100 % nativa: sin web, sin WebView. Funciona sin internet.

- **Inicio:** horas, minutos y segundos que te quedan para dormir, con anillo y luna de vidrio.
- **Reloj:** hora de dormir y de despertar, alarma de despertar, aviso previo y ciclos de sueño.
- **Notas:** botón flotante "+" para crear las que quieras, con recordatorio opcional (fecha y hora).
- **Entrenamiento:** más de 140 ejercicios con indicaciones, 10 rutinas listas (Push/Pull/Legs,
  5×5, en casa, HIIT, abdomen, glúteos, movilidad…) y rutinas propias. Registro de series con kg y reps,
  columna "Anterior", tipos de serie (calentamiento, drop set, al fallo), superseries, notas y
  temporizador de descanso automático con pitido y vibración. Temporizador Tabata / EMOM / HIIT y
  cronómetro, calculadoras de 1RM y de discos, meta semanal con racha, récords personales,
  volumen por semana, músculos trabajados, peso corporal e historial.
- **Casa:** controla ampolletas Wi-Fi con firmware libre (OpenBeken o Tasmota) por tu red, sin Smart Life:
  encender/apagar, brillo, rueda de color, blanco cálido/frío y escenas. Las busca en tu Wi-Fi, les envía
  tu Wi-Fi cuando son nuevas e incluye una guía para liberar las ampolletas de Tuya.
- **Puente con Smart Life:** enlaza tu cuenta de Smart Life (gratis, con un proyecto en iot.tuya.com) y controla
  tus equipos desde Casa, con efectos animados (arcoíris, fuego, aurora, respirar…), efectos propios, apagado
  programado y "encender/apagar todo".
- **Nioro IA (asistente):** una burbuja flotante abre el asistente con una animación y te saluda según la hora.
  Le hablas o escribes: llama a tus contactos, envía SMS y WhatsApp, lee y responde tus mensajes y correos
  nuevos, pone alarmas, crea eventos, busca en internet y maneja Nioro (notas, gastos, listas, luces, sueño).
  Usa Gemini de Google con tu propia clave gratuita. Llamadas, SMS y respuestas siempre te piden confirmación.
- **Alarmas del sistema (AlarmManager):** suenan con la app cerrada y el teléfono bloqueado, y se reprograman tras reiniciar.

## Descargar el APK
Cada vez que se sube un cambio, GitHub lo compila solo (pestaña **Actions**, 5–10 min) y publica:

`https://github.com/aedojoseeduardo9-bit/Nioro-Life/releases/latest/download/NioroLife.apk`

Convierte ese enlace en QR y escanéalo con el S21. Al abrir el APK, Android pide permitir
"Instalar apps desconocidas". Acepta las notificaciones cuando la app las pida.

## Compilar en Android Studio
Abre esta carpeta con Android Studio → espera a que Gradle sincronice →
**Build → Build APK(s)** (o el botón ▶ con el móvil conectado).
