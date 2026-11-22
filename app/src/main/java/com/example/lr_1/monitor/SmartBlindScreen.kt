package com.example.lr_1.monitor

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lr_1.analytics.AnalyticsScreen
import com.example.lr_1.analytics.SensorAnalyticsState
import com.example.lr_1.analytics.SensorViewModel
import com.example.lr_1.domain.Acceleration
import com.example.lr_1.domain.BlindPosition
import com.example.lr_1.helpers.ble.BleConnectionState
import com.example.lr_1.helpers.ble.BleDevice
import com.example.lr_1.ui.theme.LR_1Theme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val SuccessColor = Color(0xFF19734F)
private val WarningColor = Color(0xFF9A6700)
private val DangerColor = Color(0xFFB3261E)

private fun number(value: Float?) =
    if (value == null || !value.isFinite()) "—" else String.format(Locale.US, "%.2f", value)

@Composable
fun SmartBlindScreen(viewModel: SmartBlindViewModel, sensorViewModel: SensorViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val analyticsState by sensorViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var analyticsVisible by remember { mutableStateOf(false) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onAction(SmartBlindAction.CameraPermissionResult(granted))
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SmartBlindEvent.ShowMessage ->
                    Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
                SmartBlindEvent.RequestCameraPermission ->
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            AppHeader(
                state = state,
                analyticsVisible = analyticsVisible,
                onMonitoring = { analyticsVisible = false },
                onAnalytics = { analyticsVisible = true },
            )
            Box(Modifier.weight(1f)) {
                if (analyticsVisible) {
                    AnalyticsScreen(sensorViewModel)
                } else {
                    SmartBlindContent(
                        state = state,
                        analyticsState = analyticsState,
                        onAction = viewModel::onAction,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppHeader(
    state: SmartBlindState,
    analyticsVisible: Boolean,
    onMonitoring: () -> Unit,
    onAnalytics: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Smart Blinds", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Автономний контролер · варіант 19",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(
                    text = if (state.isCritical) "Критичний стан" else "Система активна",
                    color = if (state.isCritical) DangerColor else SuccessColor,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompactStatus("SENSOR", state.sensorStatus == SensorStatus.ACTIVE)
                CompactStatus(
                    "CLOUD",
                    state.cloudSyncStatus == CloudSyncStatus.SYNCED,
                    state.cloudSyncStatus == CloudSyncStatus.PENDING,
                )
                CompactStatus("BLE", state.connectionState == BleConnectionState.CONNECTED)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                NavigationButton("Моніторинг", !analyticsVisible, onMonitoring, Modifier.weight(1f))
                NavigationButton("Аналітика", analyticsVisible, onAnalytics, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NavigationButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(10.dp)) {
            Text(text)
        }
    } else {
        TextButton(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(10.dp)) {
            Text(text)
        }
    }
}

@Composable
fun SmartBlindContent(
    state: SmartBlindState,
    analyticsState: SensorAnalyticsState = SensorAnalyticsState(),
    onAction: (SmartBlindAction) -> Unit,
) {
    val actualOpening = state.actuatorOpeningPercent ?: state.position?.openingPercent
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HeroCard(state, actualOpening)

        Panel("AI-аналіз", "Локальна TensorFlow Lite модель") {
            val prediction = analyticsState.prediction
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        prediction?.state?.label ?: "Накопичення даних",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        prediction?.let {
                            "Confidence ${String.format(Locale.US, "%.1f", it.confidence * 100)} %"
                        } ?: "Вікно ${analyticsState.windowSamples}/20",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(
                    if (analyticsState.modelReady) "ON-DEVICE" else "OFFLINE",
                    if (analyticsState.modelReady) SuccessColor else DangerColor,
                )
            }
            LinearProgressIndicator(
                progress = {
                    prediction?.confidence ?: (analyticsState.windowSamples / 20f).coerceIn(0f, 1f)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Room: ${analyticsState.recordingIntervalMs} мс між записами",
                style = MaterialTheme.typography.bodySmall,
            )
            analyticsState.modelError?.let { ErrorNotice(it) }
        }

        Panel("Сенсор", sensorStatusText(state.sensorStatus)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("X", number(state.raw?.x), "м/с²", Modifier.weight(1f))
                MetricCard("Y", number(state.raw?.y), "м/с²", Modifier.weight(1f))
                MetricCard("Z", number(state.raw?.z), "м/с²", Modifier.weight(1f))
            }
            Text(
                "Оброблено подій: ${state.samples}. Нахил пристрою імітує кут світла.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.invalidReading) {
                ErrorNotice("Некоректний вектор. Останній валідний стан жалюзі збережено.")
            }
        }

        Panel("Smart-сценарій", "Автоматизація та ручне керування") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Автоматичний режим", style = MaterialTheme.typography.titleMedium)
                    Text(
                        controlSourceText(state.controlSource),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.automaticControlEnabled,
                    onCheckedChange = { onAction(SmartBlindAction.SetAutomaticControl(it)) },
                )
            }
            Text(
                "Якщо кут ≥ ${state.criticalThreshold.roundToInt()}°, відкрити жалюзі на 100 %",
                color = if (state.isCritical) DangerColor else MaterialTheme.colorScheme.onSurface,
            )
            Slider(
                value = state.criticalThreshold,
                onValueChange = {
                    onAction(SmartBlindAction.SetCriticalThreshold(it.roundToInt().toFloat()))
                },
                valueRange = 5f..85f,
                steps = 15,
                enabled = state.automaticControlEnabled,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OpeningButton("15 %", 15, onAction, Modifier.weight(1f))
                OpeningButton("50 %", 50, onAction, Modifier.weight(1f))
                OpeningButton("100 %", 100, onAction, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onAction(SmartBlindAction.TriggerVibration) },
                    modifier = Modifier.weight(1f),
                ) { Text("Вібрація") }
                OutlinedButton(
                    onClick = { onAction(SmartBlindAction.SetFlashlight(!state.flashlightEnabled)) },
                    modifier = Modifier.weight(1f),
                ) { Text(if (state.flashlightEnabled) "Ліхтарик: ON" else "Ліхтарик: OFF") }
            }
            Text(
                if (state.flashlightHardwareAvailable) "Camera flash доступний"
                else "На цьому пристрої ліхтарик імітується у UI",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Panel("Хмарна синхронізація", "REST/JSON · черга зберігається в Room") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard(
                    "Статус",
                    cloudStatusText(state.cloudSyncStatus),
                    "остання: ${formatTime(state.lastCloudSyncAt)}",
                    Modifier.weight(1f),
                    cloudStatusColor(state.cloudSyncStatus),
                )
                MetricCard(
                    "Черга",
                    state.pendingRecords.toString(),
                    "записів",
                    Modifier.weight(1f),
                    if (state.pendingRecords > 0) WarningColor else SuccessColor,
                )
            }
            Text(
                "Cloud-команда: ${state.lastRemoteCommand ?: "ще не отримана"} · " +
                    remoteStatusText(state.remoteCommandStatus),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onAction(SmartBlindAction.SyncNow) },
                    enabled = state.cloudSyncStatus != CloudSyncStatus.SYNCING,
                    modifier = Modifier.weight(1f),
                ) { Text("Синхронізувати") }
                OutlinedButton(
                    onClick = { onAction(SmartBlindAction.PollRemoteCommand) },
                    enabled = state.remoteCommandStatus != RemoteCommandStatus.POLLING,
                    modifier = Modifier.weight(1f),
                ) { Text("Отримати команду") }
            }
            if (state.cloudSyncStatus == CloudSyncStatus.PENDING) {
                WarningNotice("Дані залишаються в Room і будуть надіслані після відновлення мережі.")
            }
        }

        Panel("Останні записи", "П’ять нових вимірів із Room") {
            val latestRecords = analyticsState.records.takeLast(5).asReversed()
            if (latestRecords.isEmpty()) {
                EmptyState("Історія поки порожня")
            } else {
                latestRecords.forEachIndexed { index, record ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(record.stateLabel ?: "Очікування ML", style = MaterialTheme.typography.titleMedium)
                            Text(formatTime(record.timestamp), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "${number(record.sensorValue)}°",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (record.actionTriggered) DangerColor else MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (index != latestRecords.lastIndex) HorizontalDivider()
                }
            }
        }

        Panel("Mock BLE", "Smart Blind Hub #19") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        state.selectedDevice?.name ?: "Пристрій не вибрано",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        state.selectedDevice?.let { "${it.signalStrengthDbm} dBm · ${it.id}" }
                            ?: "Виконайте пошук віртуального хаба",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(connectionText(state.connectionState), connectionColor(state.connectionState))
            }
            Button(
                onClick = { onAction(SmartBlindAction.SearchDevices) },
                enabled = !state.isScanning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isScanning) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Сканування…")
                } else {
                    Text("Пошук пристроїв")
                }
            }
            state.devices.forEach { device ->
                val selected = device.id == state.selectedDevice?.id
                OutlinedButton(
                    onClick = { onAction(SmartBlindAction.SelectDevice(device)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (selected) "Вибрано: ${device.name}" else device.name) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onAction(SmartBlindAction.ConnectSelected) },
                    enabled = state.selectedDevice != null &&
                        state.connectionState == BleConnectionState.DISCONNECTED,
                    modifier = Modifier.weight(1f),
                ) { Text("Підключити") }
                OutlinedButton(
                    onClick = { onAction(SmartBlindAction.Disconnect) },
                    enabled = state.connectionState != BleConnectionState.DISCONNECTED,
                    modifier = Modifier.weight(1f),
                ) { Text("Відключити") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onAction(SmartBlindAction.SendCurrentPosition) },
                    enabled = state.connectionState == BleConnectionState.CONNECTED && state.position != null,
                    modifier = Modifier.weight(1f),
                ) { Text("Надіслати стан") }
                OutlinedButton(
                    onClick = { onAction(SmartBlindAction.ReadRemoteStatus) },
                    enabled = state.connectionState == BleConnectionState.CONNECTED,
                    modifier = Modifier.weight(1f),
                ) { Text("Read status") }
            }
        }

        Panel("BLE-термінал", "READ · WRITE · NOTIFY") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .background(Color(0xFF101815), RoundedCornerShape(12.dp))
                    .padding(14.dp),
            ) {
                Text(
                    text = state.terminalLines.joinToString("\n").ifEmpty {
                        "Журнал обміну порожній\nLogcat tag: SmartBlindsBLE"
                    },
                    color = Color(0xFFB6F1D5),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
            TextButton(
                onClick = { onAction(SmartBlindAction.ClearTerminal) },
                modifier = Modifier.align(Alignment.End),
            ) { Text("Очистити журнал") }
        }
    }
}

@Composable
private fun HeroCard(state: SmartBlindState, openingPercent: Int?) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text(
                        "ПОТОЧНИЙ СТАН",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                    Text(blindStateText(openingPercent), style = MaterialTheme.typography.headlineLarge)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${number(state.angle)}°", style = MaterialTheme.typography.headlineLarge)
                    Text("кут світла", style = MaterialTheme.typography.bodySmall)
                }
            }
            BlindsCanvas(openingPercent, state.angle)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroMetric("Відкриття", openingPercent?.let { "$it %" } ?: "—", Modifier.weight(1f))
                HeroMetric("Поріг", "${state.criticalThreshold.roundToInt()}°", Modifier.weight(1f))
                HeroMetric(
                    "Режим",
                    if (state.automaticControlEnabled) "AUTO" else "MANUAL",
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Panel(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor)
            Text(unit, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun HeroMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.12f), contentColor = color, shape = RoundedCornerShape(50)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun CompactStatus(label: String, active: Boolean, warning: Boolean = false) {
    val color = when {
        active -> SuccessColor
        warning -> WarningColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(7.dp)) { drawCircle(color) }
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun OpeningButton(
    label: String,
    percent: Int,
    onAction: (SmartBlindAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = { onAction(SmartBlindAction.SetManualOpening(percent)) },
        modifier = modifier,
    ) { Text(label) }
}

@Composable
private fun ErrorNotice(text: String) {
    Surface(color = DangerColor.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp)) {
        Text(text, Modifier.fillMaxWidth().padding(12.dp), color = DangerColor)
    }
}

@Composable
private fun WarningNotice(text: String) {
    Surface(color = WarningColor.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp)) {
        Text(text, Modifier.fillMaxWidth().padding(12.dp), color = WarningColor)
    }
}

@Composable
private fun EmptyState(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
        Text(
            text,
            Modifier.fillMaxWidth().padding(18.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BlindsCanvas(openingPercent: Int?, angle: Float?) {
    val windowColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    val frameColor = MaterialTheme.colorScheme.primary
    val blindColor = MaterialTheme.colorScheme.secondary
    val sunColor = MaterialTheme.colorScheme.tertiary
    Canvas(Modifier.fillMaxWidth().height(180.dp).semantics {
        contentDescription = if (openingPercent == null) "Жалюзі очікують дані"
        else "Відкриття жалюзі $openingPercent відсотків, кут ${number(angle)} градусів"
    }) {
        val origin = Offset(size.width * 0.05f, size.height * 0.08f)
        val area = Size(size.width * 0.9f, size.height * 0.84f)
        val radius = androidx.compose.ui.geometry.CornerRadius(18f)
        drawRoundRect(windowColor, origin, area, cornerRadius = radius)
        drawCircle(sunColor, radius = 14.dp.toPx(), center = Offset(area.width * 0.82f, area.height * 0.28f))
        if (angle != null) {
            val radians = Math.toRadians(angle.toDouble())
            val end = Offset(origin.x + area.width / 2, origin.y + area.height / 2)
            val length = area.height * 0.4f
            val start = Offset(
                end.x - (sin(radians) * length).toFloat(),
                end.y - (cos(radians) * length).toFloat(),
            )
            drawLine(sunColor, start, end, strokeWidth = 5.dp.toPx())
        }
        if (openingPercent != null) {
            val step = area.height / 8
            val thickness = step * (0.88f - openingPercent / 100f * 0.75f)
            repeat(8) {
                drawRoundRect(
                    blindColor,
                    Offset(origin.x + 6.dp.toPx(), origin.y + it * step + (step - thickness) / 2),
                    Size(area.width - 12.dp.toPx(), thickness),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f),
                )
            }
        }
        drawRoundRect(frameColor, origin, area, radius, style = Stroke(3.dp.toPx()))
    }
}

private fun blindStateText(openingPercent: Int?) = when (openingPercent) {
    15 -> "Прикрито"
    50 -> "Напіввідкрито"
    100 -> "Відкрито"
    null -> "Очікування даних"
    else -> "Відкрито на $openingPercent %"
}

private fun sensorStatusText(status: SensorStatus) = when (status) {
    SensorStatus.WAITING -> "Очікування першої події"
    SensorStatus.ACTIVE -> "Зчитування активне"
    SensorStatus.PAUSED -> "Зчитування призупинено"
    SensorStatus.UNAVAILABLE -> "Акселерометр відсутній. Оберіть інший AVD."
    SensorStatus.ERROR -> "Помилка реєстрації сенсора"
}

private fun connectionText(status: BleConnectionState) = when (status) {
    BleConnectionState.DISCONNECTED -> "Disconnected"
    BleConnectionState.CONNECTING -> "Connecting"
    BleConnectionState.CONNECTED -> "Connected"
}

private fun connectionColor(status: BleConnectionState) = when (status) {
    BleConnectionState.DISCONNECTED -> DangerColor
    BleConnectionState.CONNECTING -> WarningColor
    BleConnectionState.CONNECTED -> SuccessColor
}

private fun cloudStatusText(status: CloudSyncStatus) = when (status) {
    CloudSyncStatus.IDLE -> "Очікування"
    CloudSyncStatus.SYNCING -> "Відправлення"
    CloudSyncStatus.SYNCED -> "Синхронізовано"
    CloudSyncStatus.PENDING -> "Pending"
}

@Composable
private fun cloudStatusColor(status: CloudSyncStatus) = when (status) {
    CloudSyncStatus.SYNCED -> SuccessColor
    CloudSyncStatus.PENDING -> DangerColor
    CloudSyncStatus.SYNCING -> WarningColor
    CloudSyncStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun remoteStatusText(status: RemoteCommandStatus) = when (status) {
    RemoteCommandStatus.IDLE -> "очікування"
    RemoteCommandStatus.POLLING -> "запит"
    RemoteCommandStatus.RECEIVED -> "отримано"
    RemoteCommandStatus.ERROR -> "помилка"
}

private fun controlSourceText(source: ControlSource) = when (source) {
    ControlSource.AUTOMATIC -> "Керує DecisionEngine"
    ControlSource.MANUAL -> "Остання команда введена вручну"
    ControlSource.CLOUD -> "Остання команда отримана з хмари"
}

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

private fun formatTime(timestamp: Long?): String = timestamp?.let {
    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(timeFormatter)
} ?: "—"

@Preview(showBackground = true)
@Composable
private fun SmartBlindPreview() {
    LR_1Theme {
        SmartBlindContent(
            state = SmartBlindState(
                raw = Acceleration(6.94f, 0f, 6.94f),
                angle = 45f,
                position = BlindPosition.HALF_OPEN,
                sensorStatus = SensorStatus.ACTIVE,
                samples = 120,
                actuatorOpeningPercent = 50,
                cloudSyncStatus = CloudSyncStatus.SYNCED,
                devices = listOf(BleDevice("MOCK-SBC-19", "Smart Blind Hub #19", -47)),
                selectedDevice = BleDevice("MOCK-SBC-19", "Smart Blind Hub #19", -47),
                connectionState = BleConnectionState.CONNECTED,
                terminalLines = listOf("12:00:00  NOTIFY ACK|SET_OPENING|50"),
            ),
            onAction = {},
        )
    }
}
