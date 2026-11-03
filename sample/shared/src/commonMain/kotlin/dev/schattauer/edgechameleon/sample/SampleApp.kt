package dev.schattauer.edgechameleon.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.schattauer.edgechameleon.BarIcons
import dev.schattauer.edgechameleon.EdgeChameleonState
import dev.schattauer.edgechameleon.ExperimentalEdgeChameleonApi
import dev.schattauer.edgechameleon.edgeChameleon

private enum class Screen(val title: String) {
    Colors("Colours"),
    Bands("Scroll"),
    Mixed("Mixed"),
}

@OptIn(ExperimentalEdgeChameleonApi::class)
@Composable
public fun SampleApp() {
    var screen by rememberSaveable { mutableStateOf(Screen.Colors) }
    var debug by rememberSaveable { mutableStateOf(false) }
    val state = remember { EdgeChameleonState() }
    SideEffect { state.collectCells = debug }

    // The one line that the library needs
    Box(Modifier.fillMaxSize().edgeChameleon(state = state)) {
        when (screen) {
            Screen.Colors -> ColorsScreen()
            Screen.Bands -> BandsScreen()
            Screen.Mixed -> MixedScreen()
        }
        if (debug) {
            DebugPanel(state, Modifier.align(Alignment.Center))
        }
        Row(
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xE6202124))
                    .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Screen.entries.forEach { entry ->
                NavigationItem(entry.title, selected = entry == screen) { screen = entry }
            }
            NavigationItem("Debug", selected = debug) { debug = !debug }
        }
    }
}

@Composable
private fun NavigationItem(title: String, selected: Boolean, onClick: () -> Unit) {
    BasicText(
        text = title,
        style =
            TextStyle(
                color = if (selected) Color(0xFF202124) else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
        modifier =
            Modifier.clip(RoundedCornerShape(20.dp))
                .background(if (selected) Color.White else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@OptIn(ExperimentalEdgeChameleonApi::class)
@Composable
private fun DebugPanel(state: EdgeChameleonState, modifier: Modifier) {
    Column(
        modifier =
            modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF202124)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DebugRow("Status bar", state.statusBarIcons, state.statusBarCells)
        DebugRow("Navigation bar", state.navigationBarIcons, state.navigationBarCells)
    }
}

@Composable
private fun DebugRow(title: String, icons: BarIcons?, cells: List<Color>) {
    BasicText(
        text = "$title: ${icons?.name ?: "not handled"}",
        style = TextStyle(color = Color.White, fontSize = 12.sp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        cells.forEach { cell ->
            Box(Modifier.size(12.dp).background(cell).border(1.dp, Color.Gray))
        }
    }
}
