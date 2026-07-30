package com.letify.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.letify.app.ui.components.FieldLabel
import com.letify.app.ui.components.LetifyBottomSheet
import com.letify.app.ui.components.NoFeedbackButton
import com.letify.app.ui.components.WheelPicker
import com.letify.app.ui.icons.SolarIcon
import com.letify.app.ui.state.LocalAppState
import com.letify.app.ui.theme.LetifyColors
import com.letify.app.ui.theme.Letify

private val HOURS = (0..23).toList()
private val MINUTES_5 = (0..55 step 5).toList()

/**
 * Sleep entry as a modal bottom sheet — rebuilt 1:1 from
 * `sleep-sheet-concept.html`. Replaces the old +/- stepper layout
 * entirely: a live "7ч 30м" hero readout with a status pill (below
 * norm / within norm / long sleep) sits above two wheel-picker time
 * cards ("Лёг спать" / "Проснулся"), followed by emoji quality chips.
 *
 * The wheels reuse the shared [WheelPicker] — its fade mask already
 * dissolves the top/bottom rows smoothly via a gradient alpha instead
 * of a hard clip, so the digits melt away rather than getting cut off.
 */
@Composable
fun AddSleepScreen(onBack: () -> Unit) {
    val state = LocalAppState.current
    var fromH by remember { mutableIntStateOf(23) }
    var fromM by remember { mutableIntStateOf(30) }
    var toH by remember { mutableIntStateOf(7) }
    var toM by remember { mutableIntStateOf(0) }
    var quality by remember { mutableIntStateOf(2) }

    val fromTotal = fromH * 60 + fromM
    val toTotal = toH * 60 + toM
    var diff = toTotal - fromTotal
    if (diff <= 0) diff += 24 * 60
    val durH = diff / 60
    val durM = diff % 60

    val (pillLabel, pillColor) = when {
        diff < 6 * 60 -> "Меньше нормы" to LetifyColors.AccentAmber
        diff > 9 * 60 -> "Долгий сон" to LetifyColors.AccentAmber
        else -> "В пределах нормы" to Letify.colors.accent
    }

    LetifyBottomSheet(
        title = "Записать сон",
        onDismiss = onBack,
        primaryLabel = "Сохранить",
        onPrimary = {
            state.logSleep(fromTotal, toTotal, quality)
        },
    ) {
        // ---- Hero: moon icon + live duration + status pill ----
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SolarIcon(
                name = "moon-sleep-bold-duotone",
                tint = Letify.colors.accent,
                size = 56.dp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                text = "${durH}ч ${durM}м",
                color = Letify.colors.text,
                style = Letify.typography.displayMedium,
            )
            Box(
                modifier = Modifier
                    .padding(top = 9.dp)
                    .background(pillColor.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(pillLabel, color = pillColor, style = Letify.typography.labelMedium)
            }
        }

        FieldLabel("Время сна")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TimeCard(
                icon = "moon-bold-duotone",
                label = "Лёг спать",
                hour = fromH,
                minute = fromM,
                modifier = Modifier.weight(1f),
                onHourChange = { fromH = it },
                onMinuteChange = { fromM = it },
            )
            TimeCard(
                icon = "sun-bold-duotone",
                label = "Проснулся",
                hour = toH,
                minute = toM,
                modifier = Modifier.weight(1f),
                onHourChange = { toH = it },
                onMinuteChange = { toM = it },
            )
        }

        FieldLabel("Качество сна")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val options = listOf("😣" to "Плохо", "😐" to "Так себе", "🙂" to "Норм", "😴" to "Отлично")
            options.forEachIndexed { idx, (emoji, label) ->
                val active = idx == quality
                NoFeedbackButton(onClick = { quality = idx }, modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (active) Letify.colors.accentSoft else Letify.colors.track,
                                RoundedCornerShape(14.dp),
                            )
                            .padding(vertical = 11.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(emoji, style = Letify.typography.titleLarge)
                        Box(Modifier.height(5.dp))
                        Text(
                            label,
                            color = if (active) Letify.colors.accent else Letify.colors.muted,
                            style = Letify.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeCard(
    icon: String,
    label: String,
    hour: Int,
    minute: Int,
    modifier: Modifier = Modifier,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
) {
    Column(
        modifier = modifier
            .background(Letify.colors.track, RoundedCornerShape(18.dp))
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SolarIcon(name = icon, tint = Letify.colors.muted, size = 14.dp)
            Box(Modifier.width(5.dp))
            Text(label, color = Letify.colors.muted, style = Letify.typography.labelMedium)
        }
        Box(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            WheelPicker(
                values = HOURS,
                initialIndex = HOURS.indexOf(hour).coerceAtLeast(0),
                modifier = Modifier.width(44.dp),
                itemHeight = 40.dp,
                visibleItems = 3,
                textStyle = Letify.typography.titleMedium,
                onSelected = { _, v -> onHourChange(v) },
                label = { "%02d".format(it) },
            )
            Text(
                ":",
                color = Letify.colors.muted,
                style = Letify.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
            WheelPicker(
                values = MINUTES_5,
                initialIndex = MINUTES_5.indexOf(minute).coerceAtLeast(0),
                modifier = Modifier.width(44.dp),
                itemHeight = 40.dp,
                visibleItems = 3,
                textStyle = Letify.typography.titleMedium,
                onSelected = { _, v -> onMinuteChange(v) },
                label = { "%02d".format(it) },
            )
        }
    }
}
