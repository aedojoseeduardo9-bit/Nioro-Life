package com.nioro.life

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Todos los colores de la app. Hay una paleta "Noche" (la original) y una "Día". */
data class Palette(
    val light: Boolean,
    val bg: List<Color>,
    val title: Color,
    val body: Color,
    val muted: Color,
    val glow: Color,
    val glass: List<Color>,
    val glassHighlight: Color,
    val borderTop: Color,
    val borderBottom: Color,
    val shadow: Color,
    val accent: Color,
    val accentSoft: Color,
    val chipBg: Color,
    val chipBorder: Color,
    val track: Color,
    val fieldLine: Color,
    val fieldLineOff: Color,
    val fieldText: Color,
    val placeholder: Color,
    val moon: List<Color>,
    val moonAlpha: Float,
    val moonEdge: Color,
    val btnMain: List<Color>,
    val btnMainBorder: Color,
    val onBtnMain: Color,
    val dialog: List<Color>,
    val dialogBorder: Color,
    val fab: List<Color>,
    val fabIcon: Color,
    val badge: List<Color>,
    val badgeBorder: Color,
    val star: Color,
    val switchOn: Color,
    val income: Color,
    val expense: Color,
    val debt: Color,
    /** 0 noche, 1 día, 2 rosa, 3 rojo, 4 morado (decide qué animación de fondo se usa). */
    val id: Int = 0
)

val DarkPalette = Palette(
    light = false,
    bg = listOf(Color(0xFF0A1450), Color(0xFF0B2468), Color(0xFF0B3A7C), Color(0xFF0B5088)),
    title = Color(0xFFFFF3C4),
    body = Color(0xFFF4F6FF),
    muted = Color(0xB0E2EAFF),
    glow = Color(0x99FFF3BE),
    glass = listOf(Color(0x30FFFFFF), Color(0x0DFFFFFF), Color(0x1CFFFFFF)),
    glassHighlight = Color(0x40FFFFFF),
    borderTop = Color(0x70FFFFFF),
    borderBottom = Color(0x22FFFFFF),
    shadow = Color(0xFF020828),
    accent = Color(0xFFFFE28A),
    accentSoft = Color(0x47FFECA0),
    chipBg = Color(0x14FFFFFF),
    chipBorder = Color(0x38FFFFFF),
    track = Color(0x1FFFFFFF),
    fieldLine = Color(0x66FFF3C4),
    fieldLineOff = Color(0x24FFFFFF),
    fieldText = Color.White,
    placeholder = Color(0x66E2EAFF),
    moon = listOf(Color(0xFFEEF2FB), Color(0xFFD9E2F3), Color(0xFFF4EFC6)),
    moonAlpha = 0.45f,
    moonEdge = Color(0x00000000),
    btnMain = listOf(Color(0xFFFFF7D2), Color(0xFFFFE99A)),
    btnMainBorder = Color(0xFFFFF3C4),
    onBtnMain = Color(0xFF10215F),
    dialog = listOf(Color(0xF2183282), Color(0xF20B2864)),
    dialogBorder = Color(0x40FFFFFF),
    fab = listOf(Color(0x6BFFF3BE), Color(0x29FFF3BE)),
    fabIcon = Color(0xFFFFFBE6),
    badge = listOf(Color(0x59FFFFFF), Color(0x24FFF3C4), Color(0x0DFFFFFF)),
    badgeBorder = Color(0x47FFFFFF),
    star = Color.White,
    switchOn = Color(0x99FFECA0),
    income = Color(0xFF7CE3B0),
    expense = Color(0xFFFF9DA7),
    debt = Color(0xFFFFD27A)
)

/** Modo claro: el mismo cielo de la app, pero de día (azul suave, amanecer cálido y vidrio blanco). */
val LightPalette = Palette(
    id = 1,
    light = true,
    bg = listOf(Color(0xFFDCEAFF), Color(0xFFCFE0FB), Color(0xFFE9F1FD), Color(0xFFFFF1CF)),
    title = Color(0xFF10215F),
    body = Color(0xFF14246B),
    muted = Color(0xFF51629B),
    glow = Color(0xCCFFFFFF),
    glass = listOf(Color(0xB8FFFFFF), Color(0x70FFFFFF), Color(0x94FFFFFF)),
    glassHighlight = Color(0x99FFFFFF),
    borderTop = Color(0xE6FFFFFF),
    borderBottom = Color(0x4410215F),
    shadow = Color(0xFF7E97CE),
    accent = Color(0xFFF0A92E),
    accentSoft = Color(0x66F5C451),
    chipBg = Color(0x66FFFFFF),
    chipBorder = Color(0x3310215F),
    track = Color(0x2210215F),
    fieldLine = Color(0x9910215F),
    fieldLineOff = Color(0x2210215F),
    fieldText = Color(0xFF10215F),
    placeholder = Color(0x8051629B),
    moon = listOf(Color(0xFFFFFFFF), Color(0xFFE3ECFB), Color(0xFFFFE8A6)),
    moonAlpha = 0.95f,
    moonEdge = Color(0x4410215F),
    btnMain = listOf(Color(0xFF2D4FB8), Color(0xFF10215F)),
    btnMainBorder = Color(0xFF10215F),
    onBtnMain = Color(0xFFFFF3C4),
    dialog = listOf(Color(0xFAFFFFFF), Color(0xFAE9F1FF)),
    dialogBorder = Color(0x3310215F),
    fab = listOf(Color(0xFFFFD77A), Color(0xFFF0A92E)),
    fabIcon = Color(0xFF10215F),
    badge = listOf(Color(0xFFFFFFFF), Color(0x80FFE9A8), Color(0x40FFFFFF)),
    badgeBorder = Color(0x80FFFFFF),
    star = Color.White,
    switchOn = Color(0xB3F0A92E),
    income = Color(0xFF0E9F6E),
    expense = Color(0xFFD64557),
    debt = Color(0xFFB7791F)
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }


/** Temas de color: son oscuros como la noche, pero con el cielo y el brillo del color elegido. */
val PinkPalette = DarkPalette.copy(
    id = 2,
    bg = listOf(Color(0xFF3A0B2E), Color(0xFF5A1248), Color(0xFF8A1E6A), Color(0xFFC2417F)),
    title = Color(0xFFFFE3F1), body = Color(0xFFFFF4FA), muted = Color(0xB0FFD9EC),
    glow = Color(0x99FFB3DC), accent = Color(0xFFFF8FCB), accentSoft = Color(0x47FF8FCB),
    fieldLine = Color(0x66FFB3DC), placeholder = Color(0x66FFD9EC),
    moon = listOf(Color(0xFFFFF0F8), Color(0xFFFFD0E8), Color(0xFFFFE0F0)),
    btnMain = listOf(Color(0xFFFFE0F0), Color(0xFFFF9FD1)), btnMainBorder = Color(0xFFFFD0E8),
    onBtnMain = Color(0xFF4A0E3A), dialog = listOf(Color(0xF25A1248), Color(0xF23A0B2E)),
    shadow = Color(0xFF1F0418), fab = listOf(Color(0x6BFFB3DC), Color(0x29FFB3DC)),
    fabIcon = Color(0xFFFFF0F8), badge = listOf(Color(0x59FFFFFF), Color(0x24FFB3DC), Color(0x0DFFFFFF)),
    star = Color(0xFFFFD0E8), switchOn = Color(0x99FF8FCB), expense = Color(0xFFFFE08A)
)

val RedPalette = DarkPalette.copy(
    id = 3,
    bg = listOf(Color(0xFF2A0508), Color(0xFF4A0A10), Color(0xFF7A1018), Color(0xFFB01C26)),
    title = Color(0xFFFFE6D8), body = Color(0xFFFFF2EC), muted = Color(0xB0FFD2C4),
    glow = Color(0x99FF8A70), accent = Color(0xFFFF7A5C), accentSoft = Color(0x47FF6A4D),
    fieldLine = Color(0x66FFB09A), placeholder = Color(0x66FFD2C4),
    moon = listOf(Color(0xFFFFF0E8), Color(0xFFFFC9B5), Color(0xFFFFD9A8)),
    btnMain = listOf(Color(0xFFFFD2BC), Color(0xFFFF7A5C)), btnMainBorder = Color(0xFFFFC2A8),
    onBtnMain = Color(0xFF3A0508), dialog = listOf(Color(0xF25A0A12), Color(0xF2300508)),
    shadow = Color(0xFF180204), fab = listOf(Color(0x6BFFB09A), Color(0x29FFB09A)),
    fabIcon = Color(0xFFFFF0E8), badge = listOf(Color(0x59FFFFFF), Color(0x24FFB09A), Color(0x0DFFFFFF)),
    star = Color(0xFFFFC9A8), switchOn = Color(0x99FF6A4D),
    income = Color(0xFF8FF0B8), expense = Color(0xFFFFE08A)
)

val PurplePalette = DarkPalette.copy(
    id = 4,
    bg = listOf(Color(0xFF160A3A), Color(0xFF2A1260), Color(0xFF4A1E96), Color(0xFF7B3FD0)),
    title = Color(0xFFF0E4FF), body = Color(0xFFF7F1FF), muted = Color(0xB0DCCBFF),
    glow = Color(0x99C9A6FF), accent = Color(0xFFC49BFF), accentSoft = Color(0x47C49BFF),
    fieldLine = Color(0x66D2B8FF), placeholder = Color(0x66DCCBFF),
    moon = listOf(Color(0xFFF6EEFF), Color(0xFFE0CCFF), Color(0xFFEBD9FF)),
    btnMain = listOf(Color(0xFFEBDCFF), Color(0xFFB98CFF)), btnMainBorder = Color(0xFFD9C2FF),
    onBtnMain = Color(0xFF22104E), dialog = listOf(Color(0xF23A1A7A), Color(0xF2200E52)),
    shadow = Color(0xFF0C0524), fab = listOf(Color(0x6BD2B8FF), Color(0x29D2B8FF)),
    fabIcon = Color(0xFFF6EEFF), badge = listOf(Color(0x59FFFFFF), Color(0x24D2B8FF), Color(0x0DFFFFFF)),
    star = Color(0xFFE0CCFF), switchOn = Color(0x99C49BFF), expense = Color(0xFFFFA8C0)
)

val THEME_NAMES = listOf("Noche", "Día", "Rosa", "Rojo", "Morado")

fun paletteFor(theme: Int): Palette = when (theme) {
    1 -> LightPalette
    2 -> PinkPalette
    3 -> RedPalette
    4 -> PurplePalette
    else -> DarkPalette
}
