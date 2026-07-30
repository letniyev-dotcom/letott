package com.letify.app.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.letify.app.notifications.TaskReminders
import com.letify.app.ui.components.AccentSwitch
import com.letify.app.ui.components.NoFeedbackButton
import com.letify.app.ui.components.SettingsCard
import com.letify.app.ui.components.SettingsHeader
import com.letify.app.ui.components.SettingsRow
import com.letify.app.ui.components.SettingsRowDivider
import com.letify.app.ui.components.screenHPad
import com.letify.app.ui.icons.SolarIcon
import com.letify.app.ui.state.LocalAppState
import com.letify.app.ui.theme.Letify
import com.letify.app.ui.theme.LetifyColors

@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val state = LocalAppState.current
    val context = LocalContext.current
    val scroll = rememberScrollState()

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* no-op — reminders just stay silent until granted */ }

    Box(Modifier.fillMaxSize().background(Letify.colors.bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 6.dp, bottom = 60.dp),
        ) {
            SettingsHeader(title = "Уведомления", onBack = onBack)

            Box(Modifier.height(6.dp))

            SettingsCard(
                modifier = Modifier.screenHPad(),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                SettingsRow(
                    icon = "sun-bold",
                    iconTile = LetifyColors.TileOrange,
                    title = "Утренняя сводка",
                    showChevron = false,
                    trailing = {
                        AccentSwitch(
                            checked = state.notifyMorning,
                            onCheckedChange = { state.notifyMorning = it },
                        )
                    },
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = "calendar-bold",
                    iconTile = LetifyColors.TileViolet,
                    title = "Привычки и задачи",
                    showChevron = false,
                    trailing = {
                        AccentSwitch(
                            checked = state.notifyHabits,
                            onCheckedChange = { checked ->
                                // Flips the switch AND (re)schedules / cancels every
                                // task's reminder alarm — see AppState.notifyHabits.
                                state.notifyHabits = checked
                                if (checked) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS,
                                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                                    ) {
                                        notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    if (!TaskReminders.canScheduleExact(context)) {
                                        // "Alarms & reminders" is a special access the user
                                        // grants from Settings — there's no in-app dialog for
                                        // it. Reminders still work without it (falling back to
                                        // inexact delivery), this just gets on-time delivery.
                                        runCatching {
                                            context.startActivity(
                                                Intent(
                                                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                                    Uri.parse("package:${context.packageName}"),
                                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                            )
                                        }
                                    }
                                }
                            },
                        )
                    },
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = "waterdrop-bold",
                    iconTile = LetifyColors.TileSky,
                    title = "Напоминания о воде",
                    showChevron = false,
                    trailing = {
                        AccentSwitch(
                            checked = state.notifyWater,
                            onCheckedChange = { state.notifyWater = it },
                        )
                    },
                )
            }
        }
    }
}
