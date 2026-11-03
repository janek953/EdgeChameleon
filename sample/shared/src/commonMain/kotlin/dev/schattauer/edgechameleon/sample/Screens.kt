package dev.schattauer.edgechameleon.sample

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Palette =
    listOf(
        Color.White,
        Color(0xFF101418),
        Color(0xFFFFD54F),
        Color(0xFF0D47A1),
        Color(0xFFE53935),
        Color(0xFFA5D6A7),
    )

@Composable
private fun CenteredLabel(text: String, background: Color, modifier: Modifier = Modifier) {
    BasicText(
        text = text,
        style =
            TextStyle(
                color = if (background.luminance() < 0.179f) Color.White else Color.Black,
                fontSize = 18.sp,
            ),
        modifier = modifier,
    )
}

/** Tap anywhere to animate to the next background colour. */
@Composable
internal fun ColorsScreen() {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val target = Palette[index]
    val color by animateColorAsState(target)
    Box(
        modifier =
            Modifier.fillMaxSize().background(color).clickable {
                index = (index + 1) % Palette.size
            },
        contentAlignment = Alignment.Center,
    ) {
        CenteredLabel("Tap to change the colour", target)
    }
}

/** Dark and light bands that pass behind both bars while scrolling. */
@Composable
internal fun BandsScreen() {
    LazyColumn(Modifier.fillMaxSize()) {
        items(40) { band ->
            val color = if (band % 2 == 0) Color(0xFF101418) else Color(0xFFF5F5F5)
            Box(
                modifier = Modifier.fillMaxWidth().height(220.dp).background(color),
                contentAlignment = Alignment.Center,
            ) {
                CenteredLabel("Band ${band + 1}", color)
            }
        }
    }
}

private enum class MixedContent(val title: String) {
    Split("Left half dark, right half white"),
    DarkEdges("Dark edges, white middle"),
    DarkMiddle("White edges, dark middle"),
    Gradient("Horizontal gradient"),
    Picture("Drawn picture"),
}

/** Backgrounds where no tint is right everywhere, to check the zone weighting. Tap to switch. */
@Composable
internal fun MixedScreen() {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val content = MixedContent.entries[index]
    Box(
        modifier =
            Modifier.fillMaxSize().clickable { index = (index + 1) % MixedContent.entries.size },
        contentAlignment = Alignment.Center,
    ) {
        when (content) {
            MixedContent.Split -> Stripes(1f to Color.Black, 1f to Color.White)
            MixedContent.DarkEdges ->
                Stripes(1f to Color.Black, 2f to Color.White, 1f to Color.Black)
            MixedContent.DarkMiddle ->
                Stripes(1f to Color.White, 2f to Color.Black, 1f to Color.White)
            MixedContent.Gradient ->
                Box(
                    Modifier.fillMaxSize()
                        .background(Brush.horizontalGradient(listOf(Color.Black, Color.White)))
                )
            MixedContent.Picture -> DrawnPicture()
        }
        Box(Modifier.background(Color(0xCCFFFFFF))) { CenteredLabel(content.title, Color.White) }
    }
}

@Composable
private fun Stripes(vararg stripes: Pair<Float, Color>) {
    Row(Modifier.fillMaxSize()) {
        stripes.forEach { (weight, color) ->
            Box(Modifier.weight(weight).fillMaxHeight().background(color))
        }
    }
}

/** A drawn dusk landscape, which has gradients and small bright details like a photo. */
@Composable
private fun DrawnPicture() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            Brush.verticalGradient(
                0f to Color(0xFF0B1026),
                0.45f to Color(0xFF5B3A70),
                0.65f to Color(0xFFF4A261),
                0.66f to Color(0xFF1B2A1F),
                1f to Color(0xFF0A120C),
            )
        )
        drawCircle(
            Color(0xFFFFE9B0),
            radius = size.minDimension * 0.09f,
            center = Offset(size.width * 0.72f, size.height * 0.56f),
        )
        for (star in 0 until 60) {
            val x = (star * 137 % 100) / 100f * size.width
            val y = (star * 61 % 100) / 100f * size.height * 0.4f
            drawCircle(Color.White, radius = 2.dp.toPx(), center = Offset(x, y))
        }
    }
}
