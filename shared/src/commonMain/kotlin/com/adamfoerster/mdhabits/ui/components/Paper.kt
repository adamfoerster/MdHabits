package com.adamfoerster.mdhabits.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamfoerster.mdhabits.ui.theme.LocalPaperFonts
import com.adamfoerster.mdhabits.ui.theme.Paper
import kotlinx.coroutines.delay

/* ---------------------------------------------------------------- typography */

/** Hand-written (Caveat) style used for titles like the design's h1/h2. */
@Composable
fun handStyle(
    size: TextUnit,
    color: Color = Paper.inkDeep,
    weight: FontWeight = FontWeight.SemiBold,
): TextStyle = TextStyle(
    fontFamily = LocalPaperFonts.current.hand,
    fontWeight = weight,
    fontSize = size,
    lineHeight = size,
    color = color,
)

/** Karla sans style, the design's body font. */
@Composable
fun sansStyle(
    size: TextUnit,
    color: Color = Paper.ink,
    weight: FontWeight = FontWeight.Normal,
    lineHeight: TextUnit = TextUnit.Unspecified,
): TextStyle = TextStyle(
    fontFamily = LocalPaperFonts.current.sans,
    fontWeight = weight,
    fontSize = size,
    lineHeight = lineHeight,
    color = color,
)

/** Small uppercase letter-spaced label, e.g. "CADASTROS", "PASSO 1 DE 6". */
@Composable
fun Kicker(text: String, modifier: Modifier = Modifier, color: Color = Paper.faded, size: TextUnit = 12.sp) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = sansStyle(size, color, FontWeight.Bold).copy(letterSpacing = 1.2.sp),
    )
}

/** Uppercase form/section label (11sp) used above inputs and card groups. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = sansStyle(11.sp, Paper.faded, FontWeight.SemiBold).copy(letterSpacing = 1.sp),
    )
}

/* ---------------------------------------------------------------- surfaces */

/** Click without ripple — the design's paper aesthetic has no ink splash. */
fun Modifier.paperClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = this.clickable(
    interactionSource = MutableInteractionSource(),
    indication = null,
    enabled = enabled,
    onClick = onClick,
)

/** Card surface: warm white with a hairline border. */
fun Modifier.paperCard(radius: Int = 13, border: Color = Paper.border, bg: Color = Paper.card): Modifier =
    this
        .clip(RoundedCornerShape(radius.dp))
        .background(bg)
        .border(1.dp, border, RoundedCornerShape(radius.dp))

/** Dashed rounded outline (Compose's BorderStroke can't dash). */
fun Modifier.dashedBorder(color: Color = Paper.dashed, radius: Int = 12, strokeWidth: Float = 1.5f): Modifier =
    drawBehind {
        drawRoundRect(
            color = color,
            style = Stroke(
                width = strokeWidth.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
            ),
            cornerRadius = CornerRadius(radius.dp.toPx()),
        )
    }

/** Hard offset shadow under buttons ("box-shadow: 0 3px 0"). Apply before background. */
fun Modifier.hardShadow(color: Color = Paper.shadow, radius: Int = 14, dy: Float = 3f, dx: Float = 0f): Modifier =
    drawBehind {
        drawRoundRect(
            color = color,
            topLeft = Offset(dx.dp.toPx(), dy.dp.toPx()),
            size = Size(size.width, size.height),
            cornerRadius = CornerRadius(radius.dp.toPx()),
        )
    }

/* ---------------------------------------------------------------- buttons */

/** Full-width dark button with the tan hard shadow. */
@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(50.dp)
            .hardShadow()
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) Paper.ink else Paper.faded)
            .paperClick(enabled, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = sansStyle(15.sp, Paper.onDark, FontWeight.Bold))
    }
}

/** Square bordered button holding a back chevron; pairs with [PrimaryButton]. */
@Composable
fun BackSquareButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .size(width = 52.dp, height = 50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Paper.card)
            .border(1.5.dp, Paper.buttonBorder, RoundedCornerShape(14.dp))
            .paperClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Chevron(left = true, color = Paper.subtle, size = 20.dp)
    }
}

/** Dashed "+ add" affordance. */
@Composable
fun DashedAddButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .dashedBorder(radius = 12)
            .clip(RoundedCornerShape(12.dp))
            .paperClick(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = sansStyle(14.sp, Paper.accent, FontWeight.SemiBold))
    }
}

/** Selectable pill chip (recurrence, links, cadastro tabs). */
@Composable
fun SelectChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg = if (selected) Paper.ink else Paper.card
    val border = if (selected) Paper.ink else Paper.borderStrong
    val color = if (selected) Paper.onDark else Paper.muted
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .background(bg)
            .border(1.5.dp, border, RoundedCornerShape(99.dp))
            .paperClick(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = sansStyle(13.sp, color, FontWeight.SemiBold))
    }
}

/* ---------------------------------------------------------------- glyphs */

/** Simple stroked chevron used across the design. */
@Composable
fun Chevron(left: Boolean, color: Color, size: androidx.compose.ui.unit.Dp, stroke: Float = 2f) {
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            if (left) {
                moveTo(w * 0.62f, h * 0.2f); lineTo(w * 0.34f, h * 0.5f); lineTo(w * 0.62f, h * 0.8f)
            } else {
                moveTo(w * 0.38f, h * 0.2f); lineTo(w * 0.66f, h * 0.5f); lineTo(w * 0.38f, h * 0.8f)
            }
        }
        drawPath(path, color, style = Stroke(stroke.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

/** Hand-drawn checkmark inside a square outline; the Home task checkbox. */
@Composable
fun HandCheckbox(checked: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(if (checked) Paper.inset else Paper.card)
            .border(1.7.dp, Paper.body, RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) CheckMark(color = Paper.accent, size = 13.dp, stroke = 2.4f)
    }
}

/** Filled-square checkbox used in the review commit list. */
@Composable
fun CommitCheckbox(checked: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (checked) Paper.accent else Paper.card)
            .border(1.7.dp, if (checked) Paper.accent else Paper.dashed, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) CheckMark(color = Paper.onDark, size = 13.dp, stroke = 2.6f)
    }
}

/** On/off pill switch, e.g. for enabling the mdPrayer integration in Settings. */
@Composable
fun PaperSwitch(checked: Boolean, modifier: Modifier = Modifier, onCheckedChange: (Boolean) -> Unit) {
    Box(
        modifier = modifier
            .width(46.dp)
            .height(27.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(if (checked) Paper.accent else Paper.dashed)
            .paperClick { onCheckedChange(!checked) }
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .size(21.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Paper.card),
        )
    }
}

@Composable
fun CheckMark(color: Color, size: androidx.compose.ui.unit.Dp, stroke: Float = 2.4f) {
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.12f, h * 0.55f); lineTo(w * 0.4f, h * 0.82f); lineTo(w * 0.9f, h * 0.15f)
        }
        drawPath(path, color, style = Stroke(stroke.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Small square icon plate (34dp) holding a text glyph like ★ ✎ −. */
@Composable
fun GlyphPlate(
    glyph: String,
    modifier: Modifier = Modifier,
    bg: Color = Paper.inset,
    color: Color = Paper.accent,
    size: androidx.compose.ui.unit.Dp = 34.dp,
    glyphSize: TextUnit = 17.sp,
    hand: Boolean = true,
) {
    Box(
        modifier = modifier.size(size).clip(RoundedCornerShape(9.dp)).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        val style = if (hand) handStyle(glyphSize, color) else sansStyle(glyphSize, color, FontWeight.Bold)
        Text(glyph, style = style, textAlign = TextAlign.Center)
    }
}

/* ---------------------------------------------------------------- text field */

@Composable
fun PaperTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    bold: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    minHeight: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val textStyle = sansStyle(
        15.sp,
        Paper.ink,
        if (bold) FontWeight.SemiBold else FontWeight.Normal,
        lineHeight = if (singleLine) TextUnit.Unspecified else 22.sp,
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        textStyle = textStyle,
        keyboardOptions = keyboardOptions,
        singleLine = singleLine,
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(Paper.card)
                    .border(1.5.dp, Paper.borderStrong, RoundedCornerShape(11.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .then(if (minHeight > 0.dp) Modifier.height(minHeight) else Modifier),
                contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, style = textStyle.copy(color = Paper.faded))
                }
                inner()
            }
        },
    )
}

/* ---------------------------------------------------------------- toast */

/** Design toast: dark pill floating above the tab bar, auto-dismissed after ~1.9s. */
class ToastState {
    var message by mutableStateOf<String?>(null)
        private set
    private var stamp by mutableStateOf(0)

    fun show(text: String) {
        message = text
        stamp++
    }

    internal val key: Int get() = stamp

    internal fun clear() {
        message = null
    }
}

@Composable
fun rememberToastState(): ToastState = remember { ToastState() }

/** Place inside a Box that covers the screen. */
@Composable
fun BoxScope.PaperToast(state: ToastState, bottomPadding: androidx.compose.ui.unit.Dp = 96.dp) {
    val message = state.message ?: return
    LaunchedEffect(state.key) {
        delay(1900)
        state.clear()
    }
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = bottomPadding)
            .clip(RoundedCornerShape(99.dp))
            .background(Paper.inkDeep)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(message, style = sansStyle(13.5.sp, Paper.onDark, FontWeight.SemiBold))
    }
}

/* ---------------------------------------------------------------- bottom sheet */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperSheet(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Paper.bg,
        scrimColor = Paper.scrim,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 14.dp, bottom = 2.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Paper.buttonBorder),
            )
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(title, style = handStyle(30.sp))
            Text(subtitle, Modifier.padding(top = 4.dp, bottom = 18.dp), style = sansStyle(13.sp, Paper.muted))
            content()
        }
    }
}
