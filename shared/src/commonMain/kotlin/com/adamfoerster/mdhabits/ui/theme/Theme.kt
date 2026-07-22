package com.adamfoerster.mdhabits.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import mdhabits.shared.generated.resources.Res
import mdhabits.shared.generated.resources.caveat_bold
import mdhabits.shared.generated.resources.caveat_medium
import mdhabits.shared.generated.resources.caveat_semibold
import mdhabits.shared.generated.resources.karla_bold
import mdhabits.shared.generated.resources.karla_medium
import mdhabits.shared.generated.resources.karla_regular
import mdhabits.shared.generated.resources.karla_semibold
import mdhabits.shared.generated.resources.lora_italic
import org.jetbrains.compose.resources.Font

/**
 * "Paper journal" palette from the Claude Design mock (App Hábitos). The app is intentionally
 * light-only: the design language is warm paper, ink and terracotta.
 */
object Paper {
    // Surfaces
    val bg = Color(0xFFF7F1E4)          // page background
    val card = Color(0xFFFDFAF2)        // cards / inputs
    val inset = Color(0xFFF0E7D2)       // chips, icon plates, selected cards

    // Ink
    val inkDeep = Color(0xFF2F2519)     // hand-written titles
    val ink = Color(0xFF3A3128)         // primary text / dark buttons
    val body = Color(0xFF6B5B45)        // body copy
    val subtle = Color(0xFF8A7A5F)      // secondary text, icons
    val muted = Color(0xFF9C8B6F)       // hints / meta
    val faded = Color(0xFFB09E7E)       // kickers, disabled-ish text

    // Accents
    val accent = Color(0xFFB4562F)      // terracotta
    val danger = Color(0xFFA3492B)      // penalties
    val green = Color(0xFF4B7C43)       // success / earned points   (≈ oklch(0.5 .09 140))
    val greenBright = Color(0xFF5C9552) // progress bar               (≈ oklch(0.6 .11 140))
    val greenCircle = Color(0xFF588A50) // done circle                (≈ oklch(0.55 .09 140))
    val greenSoft = Color(0xFFDEEBD3)   // green icon plates          (≈ oklch(0.92 .04 140))
    val clay = Color(0xFF8B4B33)        // numbered objective circles (≈ oklch(0.46 .09 40))
    val claySoft = Color(0xFFF8E0D4)    // terracotta icon plates     (≈ oklch(0.93 .04 40))
    val dangerSoft = Color(0xFFF5E1D8)  // penalty icon plates        (≈ oklch(0.93 .03 40))

    // Lines
    val border = Color(0xFFEBE0C9)      // card borders
    val borderStrong = Color(0xFFE4D8BE) // inputs / list dividers
    val divider = Color(0xFFE8DEC9)     // section dividers (2dp)
    val dashed = Color(0xFFC7B592)      // dashed outlines
    val buttonBorder = Color(0xFFD8C9AC) // secondary button borders
    val badgeBorder = Color(0xFFC0AE8F) // week badge dashed border

    // Effects
    val shadow = Color(0xFFC8B896)      // hard offset shadow under dark buttons
    val shadowSoft = Color(0xFFE5DAC2)  // hard offset shadow under light buttons
    val marginRed = Color(0x59C45A4A)   // notebook margin line (35% terracotta)
    val progressTrack = Color(0xFFEFE6D2)

    // Completed-task states
    val doneText = Color(0xFFA99878)
    val donePoints = Color(0xFFB9A883)
    val pointsTint = Color(0xFF8A7A5F)

    // Selected commit card
    val selectedBorder = Color(0xFFD8C29A)

    // Dark surfaces (balance card, primary buttons)
    val onDark = Color(0xFFF7F1E4)
    val onDarkFaded = Color(0xFFC8B896)
    val scrim = Color(0x6B2F2519)
}

/** The three font families of the design: hand-written titles, sans body, serif italic quotes. */
data class PaperFonts(
    val hand: FontFamily,
    val sans: FontFamily,
    val serifItalic: FontFamily,
)

val LocalPaperFonts = staticCompositionLocalOf<PaperFonts> {
    error("PaperFonts not provided — wrap content in MdHabitsTheme")
}

@Composable
private fun paperFonts(): PaperFonts = PaperFonts(
    hand = FontFamily(
        Font(Res.font.caveat_medium, weight = FontWeight.Medium),
        Font(Res.font.caveat_semibold, weight = FontWeight.SemiBold),
        Font(Res.font.caveat_bold, weight = FontWeight.Bold),
    ),
    sans = FontFamily(
        Font(Res.font.karla_regular, weight = FontWeight.Normal),
        Font(Res.font.karla_medium, weight = FontWeight.Medium),
        Font(Res.font.karla_semibold, weight = FontWeight.SemiBold),
        Font(Res.font.karla_bold, weight = FontWeight.Bold),
    ),
    serifItalic = FontFamily(
        Font(Res.font.lora_italic, weight = FontWeight.Normal, style = FontStyle.Italic),
    ),
)

private val PaperColorScheme = lightColorScheme(
    primary = Paper.ink,
    onPrimary = Paper.onDark,
    secondary = Paper.accent,
    onSecondary = Paper.onDark,
    background = Paper.bg,
    onBackground = Paper.ink,
    surface = Paper.bg,
    onSurface = Paper.ink,
    surfaceVariant = Paper.card,
    onSurfaceVariant = Paper.subtle,
    outline = Paper.borderStrong,
    error = Paper.danger,
)

@Composable
fun MdHabitsTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPaperFonts provides paperFonts()) {
        MaterialTheme(
            colorScheme = PaperColorScheme,
            content = content,
        )
    }
}
