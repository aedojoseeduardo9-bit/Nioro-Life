package com.nioro.life

/** Cómo se registra un ejercicio: con peso y repeticiones, solo repeticiones o por tiempo. */
const val EX_WEIGHT = 0
const val EX_REPS = 1
const val EX_TIME = 2

val MUSCLE_NAMES = listOf(
    "Pecho", "Espalda", "Hombros", "Bíceps", "Tríceps", "Antebrazos", "Abdomen",
    "Cuádriceps", "Isquios", "Glúteos", "Pantorrillas", "Cardio", "Cuerpo completo", "Movilidad"
)
val MUSCLE_EMOJI = listOf("🫁", "🔙", "🏔️", "💪", "🦾", "✊", "🔥", "🦵", "🦿", "🍑", "🦶", "❤️", "⚡", "🧘")

val EQUIP_NAMES = listOf(
    "Peso corporal", "Mancuernas", "Barra", "Máquina", "Polea", "Kettlebell", "Banda", "Otro"
)

data class Exercise(
    val id: String,
    val name: String,
    val muscle: Int,
    val equip: Int,
    val type: Int,
    val tip: String,
    val custom: Boolean = false
)

private const val CHEST = 0
private const val BACK = 1
private const val SHOULDERS = 2
private const val BICEPS = 3
private const val TRICEPS = 4
private const val FOREARMS = 5
private const val ABS = 6
private const val QUADS = 7
private const val HAMS = 8
private const val GLUTES = 9
private const val CALVES = 10
private const val CARDIO = 11
private const val FULL = 12
private const val MOBILITY = 13

private const val BW = 0
private const val DB = 1
private const val BB = 2
private const val MACHINE = 3
private const val CABLE = 4
private const val KB = 5
private const val BAND = 6
private const val OTHER = 7

private fun ex(id: String, name: String, muscle: Int, equip: Int, type: Int, tip: String) =
    Exercise(id, name, muscle, equip, type, tip)

/** Biblioteca de ejercicios incluida en la app (funciona sin internet). */
val EXERCISES: List<Exercise> = listOf(
    // ----- Pecho
    ex("bench", "Press de banca", CHEST, BB, EX_WEIGHT, "Escápulas juntas, pies firmes; baja la barra al pecho medio y empuja en línea recta."),
    ex("incline_bench", "Press inclinado con barra", CHEST, BB, EX_WEIGHT, "Banco a 30–45°. Baja hacia la parte alta del pecho."),
    ex("decline_bench", "Press declinado", CHEST, BB, EX_WEIGHT, "Enfoca la parte baja del pecho; controla la bajada."),
    ex("db_bench", "Press con mancuernas", CHEST, DB, EX_WEIGHT, "Mayor rango que con barra; junta las mancuernas arriba sin chocarlas."),
    ex("db_incline", "Press inclinado con mancuernas", CHEST, DB, EX_WEIGHT, "Codos a unos 45° del cuerpo, no abiertos del todo."),
    ex("db_fly", "Aperturas con mancuernas", CHEST, DB, EX_WEIGHT, "Codos ligeramente flexionados; abre como abrazando un árbol."),
    ex("cable_fly", "Cruce de poleas", CHEST, CABLE, EX_WEIGHT, "Junta las manos frente al pecho y aprieta un segundo."),
    ex("pec_deck", "Contractor de pecho (pec deck)", CHEST, MACHINE, EX_WEIGHT, "Espalda pegada al respaldo, movimiento lento."),
    ex("chest_press_m", "Press de pecho en máquina", CHEST, MACHINE, EX_WEIGHT, "Ajusta el asiento para que las manijas queden a la altura del pecho medio."),
    ex("pushup", "Flexiones", CHEST, BW, EX_REPS, "Cuerpo recto como tabla; pecho casi al suelo."),
    ex("incline_pushup", "Flexiones inclinadas", CHEST, BW, EX_REPS, "Manos en un banco: versión más fácil para empezar."),
    ex("decline_pushup", "Flexiones declinadas", CHEST, BW, EX_REPS, "Pies elevados: más trabajo para la parte alta del pecho."),
    ex("wide_pushup", "Flexiones abiertas", CHEST, BW, EX_REPS, "Manos más anchas que los hombros."),
    ex("dips_chest", "Fondos en paralelas (pecho)", CHEST, BW, EX_REPS, "Inclina el torso hacia delante para enfocar el pecho."),
    ex("db_pullover", "Pullover con mancuerna", CHEST, DB, EX_WEIGHT, "Brazos casi rectos; baja detrás de la cabeza sin arquear la espalda."),

    // ----- Espalda
    ex("deadlift", "Peso muerto", BACK, BB, EX_WEIGHT, "Barra pegada a las piernas, espalda neutra; empuja el suelo con los pies."),
    ex("pullup", "Dominadas", BACK, BW, EX_REPS, "Desde brazos extendidos hasta la barbilla sobre la barra."),
    ex("chinup", "Dominadas supinas", BACK, BW, EX_REPS, "Palmas hacia ti: más trabajo de bíceps."),
    ex("lat_pulldown", "Jalón al pecho", BACK, CABLE, EX_WEIGHT, "Lleva la barra a la parte alta del pecho, codos hacia abajo."),
    ex("close_pulldown", "Jalón agarre cerrado", BACK, CABLE, EX_WEIGHT, "Agarre en V; tira con los codos pegados."),
    ex("bb_row", "Remo con barra", BACK, BB, EX_WEIGHT, "Torso inclinado ~45°, tira hacia el ombligo."),
    ex("db_row", "Remo con mancuerna a una mano", BACK, DB, EX_WEIGHT, "Apoya mano y rodilla en el banco; codo hacia la cadera."),
    ex("seated_row", "Remo sentado en polea", BACK, CABLE, EX_WEIGHT, "Pecho arriba, junta las escápulas al final."),
    ex("tbar_row", "Remo en T", BACK, BB, EX_WEIGHT, "Espalda neutra, tira con los codos."),
    ex("machine_row", "Remo en máquina", BACK, MACHINE, EX_WEIGHT, "Pecho apoyado en el soporte; aprieta atrás."),
    ex("inverted_row", "Remo invertido", BACK, BW, EX_REPS, "Bajo una barra o mesa firme, cuerpo recto."),
    ex("straight_pulldown", "Pullover en polea", BACK, CABLE, EX_WEIGHT, "Brazos casi rectos; baja la barra hasta los muslos."),
    ex("back_ext", "Hiperextensiones", BACK, MACHINE, EX_REPS, "Sube hasta alinear el cuerpo, sin hiperextender."),
    ex("superman", "Superman", BACK, BW, EX_REPS, "Boca abajo, eleva brazos y piernas y sostén un segundo."),
    ex("band_pull_apart", "Separación de banda", BACK, BAND, EX_REPS, "Brazos rectos al frente; abre la banda hasta el pecho."),
    ex("rack_pull", "Peso muerto parcial (rack pull)", BACK, BB, EX_WEIGHT, "Desde la altura de las rodillas; ideal para la espalda alta."),

    // ----- Hombros
    ex("ohp", "Press militar", SHOULDERS, BB, EX_WEIGHT, "De pie, glúteos y abdomen apretados; barra en línea con la cara."),
    ex("db_shoulder_press", "Press de hombros con mancuernas", SHOULDERS, DB, EX_WEIGHT, "Sentado con respaldo; sube sin bloquear los codos."),
    ex("arnold", "Press Arnold", SHOULDERS, DB, EX_WEIGHT, "Gira las palmas de ti hacia afuera mientras subes."),
    ex("lateral_raise", "Elevaciones laterales", SHOULDERS, DB, EX_WEIGHT, "Sube hasta la altura de los hombros, codos un poco flexionados."),
    ex("cable_lateral", "Elevación lateral en polea", SHOULDERS, CABLE, EX_WEIGHT, "Tensión constante; sube lento a un lado."),
    ex("front_raise", "Elevaciones frontales", SHOULDERS, DB, EX_WEIGHT, "Sube al frente hasta la altura de los ojos."),
    ex("rear_fly", "Pájaros (deltoides posterior)", SHOULDERS, DB, EX_WEIGHT, "Inclinado, abre los brazos hacia los lados."),
    ex("face_pull", "Face pull", SHOULDERS, CABLE, EX_WEIGHT, "Tira la cuerda hacia la frente separando las manos."),
    ex("upright_row", "Remo al mentón", SHOULDERS, BB, EX_WEIGHT, "Agarre medio; sube los codos hasta los hombros."),
    ex("shrug", "Encogimientos de hombros", SHOULDERS, DB, EX_WEIGHT, "Sube los hombros hacia las orejas, sin girarlos."),
    ex("machine_shoulder", "Press de hombros en máquina", SHOULDERS, MACHINE, EX_WEIGHT, "Asiento a la altura que deje las manijas al nivel de los hombros."),
    ex("pike_pushup", "Flexiones en pica", SHOULDERS, BW, EX_REPS, "Cadera alta en V invertida; baja la cabeza hacia el suelo."),
    ex("band_shoulder_press", "Press de hombros con banda", SHOULDERS, BAND, EX_REPS, "Pisa la banda y empuja hacia arriba."),

    // ----- Bíceps
    ex("bb_curl", "Curl con barra", BICEPS, BB, EX_WEIGHT, "Codos fijos a los costados; no balancees el cuerpo."),
    ex("db_curl", "Curl con mancuernas", BICEPS, DB, EX_WEIGHT, "Gira la palma hacia arriba mientras subes."),
    ex("hammer_curl", "Curl martillo", BICEPS, DB, EX_WEIGHT, "Palmas enfrentadas todo el recorrido."),
    ex("preacher_curl", "Curl en banco Scott", BICEPS, BB, EX_WEIGHT, "Brazos apoyados; baja hasta casi estirar."),
    ex("concentration_curl", "Curl concentrado", BICEPS, DB, EX_WEIGHT, "Codo apoyado en el muslo, sube lento."),
    ex("cable_curl", "Curl en polea", BICEPS, CABLE, EX_WEIGHT, "Tensión constante; aprieta arriba."),
    ex("incline_curl", "Curl inclinado", BICEPS, DB, EX_WEIGHT, "Recostado a 45°: gran estiramiento del bíceps."),
    ex("band_curl", "Curl con banda", BICEPS, BAND, EX_REPS, "Pisa la banda y sube con los codos quietos."),

    // ----- Tríceps
    ex("pushdown", "Extensión en polea (pushdown)", TRICEPS, CABLE, EX_WEIGHT, "Codos pegados; extiende por completo abajo."),
    ex("rope_pushdown", "Pushdown con cuerda", TRICEPS, CABLE, EX_WEIGHT, "Separa la cuerda al final del recorrido."),
    ex("skullcrusher", "Press francés", TRICEPS, BB, EX_WEIGHT, "Baja la barra hacia la frente moviendo solo los codos."),
    ex("overhead_ext", "Extensión sobre la cabeza", TRICEPS, DB, EX_WEIGHT, "Mancuerna detrás de la cabeza, codos apuntando arriba."),
    ex("close_bench", "Press de banca cerrado", TRICEPS, BB, EX_WEIGHT, "Manos al ancho de hombros, codos cerca del cuerpo."),
    ex("dips", "Fondos en paralelas", TRICEPS, BW, EX_REPS, "Torso recto para enfocar el tríceps."),
    ex("bench_dips", "Fondos en banco", TRICEPS, BW, EX_REPS, "Manos en el banco detrás de ti; baja hasta 90°."),
    ex("kickback", "Patada de tríceps", TRICEPS, DB, EX_WEIGHT, "Brazo paralelo al suelo; extiende hacia atrás."),
    ex("diamond_pushup", "Flexiones diamante", TRICEPS, BW, EX_REPS, "Manos juntas formando un rombo bajo el pecho."),

    // ----- Antebrazos
    ex("wrist_curl", "Curl de muñeca", FOREARMS, DB, EX_WEIGHT, "Antebrazos apoyados, mueve solo la muñeca."),
    ex("reverse_curl", "Curl invertido", FOREARMS, BB, EX_WEIGHT, "Palmas hacia abajo; fortalece antebrazo y agarre."),
    ex("farmer_walk", "Paseo del granjero", FOREARMS, DB, EX_TIME, "Camina erguido con peso pesado en cada mano."),
    ex("dead_hang", "Colgarse de la barra", FOREARMS, BW, EX_TIME, "Hombros activos; aguanta el máximo tiempo."),

    // ----- Abdomen
    ex("plank", "Plancha", ABS, BW, EX_TIME, "Codos bajo los hombros, cuerpo recto, aprieta glúteos."),
    ex("side_plank", "Plancha lateral", ABS, BW, EX_TIME, "Cadera alta; cambia de lado a mitad."),
    ex("crunch", "Abdominales (crunch)", ABS, BW, EX_REPS, "Despega solo los hombros; sin tirar del cuello."),
    ex("bicycle", "Bicicleta", ABS, BW, EX_REPS, "Codo hacia la rodilla contraria, lento y controlado."),
    ex("leg_raise", "Elevación de piernas", ABS, BW, EX_REPS, "Espalda baja pegada al suelo."),
    ex("hanging_leg_raise", "Elevación de piernas colgado", ABS, BW, EX_REPS, "Sin balanceo; sube las rodillas o las piernas rectas."),
    ex("russian_twist", "Giros rusos", ABS, BW, EX_REPS, "Pies elevados para más dificultad."),
    ex("mountain_climber", "Escaladores", ABS, BW, EX_TIME, "Rodillas al pecho rápido, cadera baja."),
    ex("dead_bug", "Dead bug", ABS, BW, EX_REPS, "Brazo y pierna contrarios; espalda pegada al suelo."),
    ex("ab_wheel", "Rueda abdominal", ABS, OTHER, EX_REPS, "Avanza solo hasta donde mantengas la espalda recta."),
    ex("cable_crunch", "Crunch en polea", ABS, CABLE, EX_WEIGHT, "De rodillas, flexiona el tronco llevando los codos a los muslos."),
    ex("hollow_hold", "Hollow hold", ABS, BW, EX_TIME, "Forma de banana con la espalda baja pegada al suelo."),
    ex("v_up", "V-ups", ABS, BW, EX_REPS, "Sube piernas y torso a la vez hasta tocar los pies."),
    ex("flutter_kicks", "Tijeras", ABS, BW, EX_TIME, "Piernas rectas alternando arriba y abajo."),

    // ----- Cuádriceps
    ex("squat", "Sentadilla con barra", QUADS, BB, EX_WEIGHT, "Rodillas en dirección de los pies; baja al menos a paralelo."),
    ex("front_squat", "Sentadilla frontal", QUADS, BB, EX_WEIGHT, "Codos altos, torso erguido."),
    ex("goblet_squat", "Sentadilla goblet", QUADS, DB, EX_WEIGHT, "Mancuerna o kettlebell al pecho."),
    ex("bw_squat", "Sentadilla libre", QUADS, BW, EX_REPS, "Peso en talones, pecho arriba."),
    ex("leg_press", "Prensa de piernas", QUADS, MACHINE, EX_WEIGHT, "No despegues la espalda baja del respaldo."),
    ex("leg_ext", "Extensión de cuádriceps", QUADS, MACHINE, EX_WEIGHT, "Aprieta arriba un segundo."),
    ex("lunge", "Zancadas", QUADS, DB, EX_WEIGHT, "Paso largo; rodilla trasera casi al suelo."),
    ex("walking_lunge", "Zancadas caminando", QUADS, BW, EX_REPS, "Avanza alternando piernas, torso erguido."),
    ex("bulgarian", "Sentadilla búlgara", QUADS, DB, EX_WEIGHT, "Pie trasero sobre un banco; baja vertical."),
    ex("hack_squat", "Sentadilla hack", QUADS, MACHINE, EX_WEIGHT, "Pies a media plataforma; baja profundo."),
    ex("step_up", "Subidas al cajón", QUADS, DB, EX_REPS, "Empuja con la pierna de arriba, no con la de abajo."),
    ex("wall_sit", "Sentadilla en pared", QUADS, BW, EX_TIME, "Rodillas a 90°, espalda pegada a la pared."),
    ex("jump_squat", "Sentadilla con salto", QUADS, BW, EX_REPS, "Aterriza suave flexionando las rodillas."),
    ex("pistol", "Sentadilla a una pierna (pistol)", QUADS, BW, EX_REPS, "Usa un apoyo al principio para equilibrarte."),

    // ----- Isquios
    ex("rdl", "Peso muerto rumano", HAMS, BB, EX_WEIGHT, "Rodillas casi rectas; lleva la cadera atrás."),
    ex("db_rdl", "Peso muerto rumano con mancuernas", HAMS, DB, EX_WEIGHT, "Mancuernas rozando las piernas."),
    ex("leg_curl", "Curl femoral tumbado", HAMS, MACHINE, EX_WEIGHT, "Cadera pegada al banco; baja lento."),
    ex("seated_leg_curl", "Curl femoral sentado", HAMS, MACHINE, EX_WEIGHT, "Aprieta abajo y sube controlado."),
    ex("good_morning", "Buenos días", HAMS, BB, EX_WEIGHT, "Barra en la espalda; inclínate con la espalda recta."),
    ex("nordic", "Curl nórdico", HAMS, BW, EX_REPS, "Pies sujetos; baja lo más lento que puedas."),
    ex("single_rdl", "Peso muerto a una pierna", HAMS, DB, EX_WEIGHT, "Equilibrio y control; cadera nivelada."),

    // ----- Glúteos
    ex("hip_thrust", "Hip thrust", GLUTES, BB, EX_WEIGHT, "Espalda alta en el banco; aprieta glúteos arriba."),
    ex("glute_bridge", "Puente de glúteos", GLUTES, BW, EX_REPS, "Tumbado, sube la cadera empujando con los talones."),
    ex("single_bridge", "Puente a una pierna", GLUTES, BW, EX_REPS, "Una pierna estirada; cadera nivelada."),
    ex("cable_kickback", "Patada de glúteo en polea", GLUTES, CABLE, EX_WEIGHT, "Patea hacia atrás sin arquear la espalda."),
    ex("donkey_kick", "Patada de burro", GLUTES, BW, EX_REPS, "En cuatro apoyos, sube el talón hacia el techo."),
    ex("fire_hydrant", "Hidrante", GLUTES, BW, EX_REPS, "En cuatro apoyos, abre la rodilla hacia el lado."),
    ex("hip_abduction", "Abductores en máquina", GLUTES, MACHINE, EX_WEIGHT, "Abre las piernas controlando la vuelta."),
    ex("band_walk", "Caminata lateral con banda", GLUTES, BAND, EX_REPS, "Banda en rodillas o tobillos, media sentadilla."),
    ex("sumo_deadlift", "Peso muerto sumo", GLUTES, BB, EX_WEIGHT, "Piernas abiertas, puntas afuera; empuja con la cadera."),
    ex("kb_swing", "Swing con kettlebell", GLUTES, KB, EX_REPS, "Golpe de cadera, no de brazos; la pesa flota al frente."),

    // ----- Pantorrillas
    ex("calf_raise", "Elevación de talones de pie", CALVES, MACHINE, EX_WEIGHT, "Sube al máximo y baja estirando."),
    ex("seated_calf", "Elevación de talones sentado", CALVES, MACHINE, EX_WEIGHT, "Trabaja el sóleo; pausa abajo."),
    ex("bw_calf", "Elevación de talones sin peso", CALVES, BW, EX_REPS, "En un escalón para más rango."),
    ex("jump_rope", "Saltar la cuerda", CALVES, OTHER, EX_TIME, "Saltos cortos sobre la punta de los pies."),

    // ----- Cardio
    ex("run", "Correr", CARDIO, BW, EX_TIME, "Ritmo en el que todavía puedas hablar para zona 2."),
    ex("treadmill", "Cinta", CARDIO, MACHINE, EX_TIME, "Prueba con algo de inclinación para cuidar las rodillas."),
    ex("bike", "Bicicleta estática", CARDIO, MACHINE, EX_TIME, "Ajusta el sillín a la altura de la cadera."),
    ex("rower", "Remo (máquina)", CARDIO, MACHINE, EX_TIME, "Piernas, luego tronco, luego brazos."),
    ex("elliptical", "Elíptica", CARDIO, MACHINE, EX_TIME, "Bajo impacto; mantén la espalda erguida."),
    ex("jumping_jack", "Saltos de tijera", CARDIO, BW, EX_TIME, "Abre brazos y piernas a la vez."),
    ex("high_knees", "Rodillas arriba", CARDIO, BW, EX_TIME, "Sube las rodillas a la altura de la cadera."),
    ex("stairs", "Subir escaleras", CARDIO, BW, EX_TIME, "Sin apoyarte en la baranda."),
    ex("walk", "Caminata rápida", CARDIO, BW, EX_TIME, "Paso enérgico, brazos activos."),
    ex("shadow_box", "Boxeo de sombra", CARDIO, BW, EX_TIME, "Golpes rápidos con guardia arriba."),

    // ----- Cuerpo completo
    ex("burpee", "Burpees", FULL, BW, EX_REPS, "Flexión abajo y salto arriba con palmada."),
    ex("thruster", "Thrusters", FULL, DB, EX_WEIGHT, "Sentadilla frontal y press arriba en un solo movimiento."),
    ex("clean", "Cargada de potencia", FULL, BB, EX_WEIGHT, "Extensión explosiva de cadera; recibe la barra en los hombros."),
    ex("snatch_db", "Arranque con mancuerna", FULL, DB, EX_WEIGHT, "Del suelo a sobre la cabeza en un movimiento."),
    ex("kb_clean_press", "Cargada y press con kettlebell", FULL, KB, EX_WEIGHT, "Lleva la pesa al hombro y empújala arriba."),
    ex("turkish_getup", "Levantada turca", FULL, KB, EX_REPS, "Lento y paso a paso; mira siempre la pesa."),
    ex("bear_crawl", "Paso de oso", FULL, BW, EX_TIME, "Rodillas a un palmo del suelo, espalda plana."),
    ex("man_maker", "Man maker", FULL, DB, EX_REPS, "Flexión, remo a cada lado, cargada y press."),
    ex("wall_ball", "Lanzamiento a la pared", FULL, OTHER, EX_REPS, "Sentadilla y lanza el balón a un punto alto."),
    ex("sled_push", "Empuje de trineo", FULL, OTHER, EX_TIME, "Brazos firmes, pasos cortos y potentes."),
    ex("battle_rope", "Cuerdas de batalla", FULL, OTHER, EX_TIME, "Ondas rápidas desde una media sentadilla."),
    ex("squat_press", "Sentadilla con press (banda)", FULL, BAND, EX_REPS, "Sube de la sentadilla empujando la banda arriba."),

    // ----- Movilidad
    ex("cat_cow", "Gato-camello", MOBILITY, BW, EX_TIME, "Alterna arquear y redondear la espalda con la respiración."),
    ex("worlds_greatest", "Estiramiento más grande del mundo", MOBILITY, BW, EX_TIME, "Zancada, codo al suelo y giro hacia arriba."),
    ex("hip_flexor", "Estiramiento de flexores de cadera", MOBILITY, BW, EX_TIME, "Rodilla al suelo, empuja la cadera adelante."),
    ex("hamstring_stretch", "Estiramiento de isquios", MOBILITY, BW, EX_TIME, "Pierna estirada; inclínate desde la cadera."),
    ex("pigeon", "Postura de la paloma", MOBILITY, BW, EX_TIME, "Abre la cadera; respira hondo."),
    ex("childs_pose", "Postura del niño", MOBILITY, BW, EX_TIME, "Glúteos a los talones, brazos al frente."),
    ex("thoracic_rot", "Rotación torácica", MOBILITY, BW, EX_TIME, "De lado, abre el brazo de arriba como un libro."),
    ex("shoulder_dislocate", "Rotación de hombros con palo", MOBILITY, OTHER, EX_REPS, "Agarre ancho, pasa el palo por encima de la cabeza."),
    ex("deep_squat_hold", "Sentadilla profunda sostenida", MOBILITY, BW, EX_TIME, "Talones en el suelo, codos empujando las rodillas."),
    ex("foam_roll", "Rodillo de espuma", MOBILITY, OTHER, EX_TIME, "Rueda despacio y para en los puntos tensos."),
    ex("neck_stretch", "Estiramiento de cuello", MOBILITY, BW, EX_TIME, "Inclina la cabeza a un lado con suavidad."),
    ex("cobra", "Cobra", MOBILITY, BW, EX_TIME, "Boca abajo, sube el pecho estirando el abdomen.")
)

private val EX_BY_ID: Map<String, Exercise> = EXERCISES.associateBy { it.id }

fun exerciseById(id: String, custom: List<Exercise> = emptyList()): Exercise? =
    EX_BY_ID[id] ?: custom.firstOrNull { it.id == id }

/** Un ejercicio dentro de una rutina: series, repeticiones (o segundos) objetivo y descanso. */
data class RoutineItem(val exId: String, val sets: Int, val reps: Int, val restSec: Int)

data class Routine(
    val id: Long,
    val name: String,
    val items: List<RoutineItem>,
    val preset: Boolean = false,
    val desc: String = ""
)

private fun r(exId: String, sets: Int, reps: Int, rest: Int = 90) = RoutineItem(exId, sets, reps, rest)

/** Rutinas listas para usar (como los "programas" de otras apps). */
val PRESET_ROUTINES: List<Routine> = listOf(
    Routine(-1, "Cuerpo completo · Principiante", listOf(
        r("goblet_squat", 3, 10), r("db_bench", 3, 10), r("db_row", 3, 10),
        r("db_shoulder_press", 3, 10), r("glute_bridge", 3, 12, 60), r("plank", 3, 30, 45)
    ), true, "3 días por semana, ideal para empezar."),
    Routine(-2, "Empuje (Push)", listOf(
        r("bench", 4, 8, 120), r("db_incline", 3, 10), r("ohp", 3, 8, 120),
        r("lateral_raise", 3, 15, 60), r("pushdown", 3, 12, 60), r("overhead_ext", 3, 12, 60)
    ), true, "Pecho, hombros y tríceps."),
    Routine(-3, "Tirón (Pull)", listOf(
        r("deadlift", 3, 5, 180), r("pullup", 4, 8, 120), r("bb_row", 3, 10),
        r("face_pull", 3, 15, 60), r("bb_curl", 3, 10, 60), r("hammer_curl", 3, 12, 60)
    ), true, "Espalda y bíceps."),
    Routine(-4, "Piernas (Legs)", listOf(
        r("squat", 4, 8, 150), r("rdl", 3, 10, 120), r("leg_press", 3, 12),
        r("leg_curl", 3, 12, 60), r("hip_thrust", 3, 10), r("calf_raise", 4, 15, 45)
    ), true, "Cuádriceps, isquios, glúteos y pantorrillas."),
    Routine(-5, "En casa sin equipo", listOf(
        r("pushup", 3, 12, 60), r("bw_squat", 3, 20, 60), r("inverted_row", 3, 10, 60),
        r("walking_lunge", 3, 12, 60), r("pike_pushup", 3, 8, 60), r("plank", 3, 40, 45),
        r("burpee", 3, 10, 60)
    ), true, "Solo con tu peso, en cualquier lugar."),
    Routine(-6, "Abdomen de acero", listOf(
        r("crunch", 3, 20, 30), r("leg_raise", 3, 15, 30), r("russian_twist", 3, 20, 30),
        r("bicycle", 3, 20, 30), r("plank", 3, 45, 30), r("mountain_climber", 3, 30, 30)
    ), true, "15 minutos para el core."),
    Routine(-7, "Glúteos y piernas", listOf(
        r("hip_thrust", 4, 10), r("bulgarian", 3, 10), r("sumo_deadlift", 3, 8, 120),
        r("cable_kickback", 3, 15, 60), r("hip_abduction", 3, 15, 60), r("band_walk", 3, 20, 45)
    ), true, "Enfocada en glúteos."),
    Routine(-8, "HIIT quema grasa", listOf(
        r("jumping_jack", 4, 40, 20), r("burpee", 4, 10, 20), r("high_knees", 4, 40, 20),
        r("jump_squat", 4, 15, 20), r("mountain_climber", 4, 40, 20)
    ), true, "Intervalos cortos con poco descanso."),
    Routine(-9, "Movilidad y estiramiento", listOf(
        r("cat_cow", 1, 60, 10), r("worlds_greatest", 2, 45, 10), r("hip_flexor", 2, 45, 10),
        r("hamstring_stretch", 2, 45, 10), r("pigeon", 2, 45, 10), r("thoracic_rot", 2, 45, 10),
        r("childs_pose", 1, 60, 0)
    ), true, "Para los días de descanso o después de entrenar."),
    Routine(-10, "Fuerza 5×5", listOf(
        r("squat", 5, 5, 180), r("bench", 5, 5, 180), r("bb_row", 5, 5, 150)
    ), true, "Clásico de fuerza: sube un poco el peso cada semana.")
)
