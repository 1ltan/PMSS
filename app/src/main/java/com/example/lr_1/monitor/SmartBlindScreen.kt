package com.example.lr_1.monitor

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lr_1.domain.Acceleration
import com.example.lr_1.domain.BlindPosition
import com.example.lr_1.helpers.ble.BleConnectionState
import com.example.lr_1.helpers.ble.BleDevice
import com.example.lr_1.ui.theme.LR_1Theme
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private fun number(value: Float?) =
    if (value == null || !value.isFinite()) "—" else String.format(Locale.US, "%.2f", value)

@Composable
fun SmartBlindScreen(viewModel: SmartBlindViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SmartBlindEvent.ShowMessage ->
                    Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
            }
        }
    }

    SmartBlindContent(state = state, onAction = viewModel::onAction)
}

/** Чистий MVI-екран: State на вході, Action на виході. */
@Composable
fun SmartBlindContent(state: SmartBlindState, onAction: (SmartBlindAction) -> Unit) {
    Scaffold { insets ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Smart Blind Controller", style = MaterialTheme.typography.headlineLarge)
            Text("ЛР №1–2 · сенсор кута та Mock BLE-керування")

            Panel("Акселерометр · X / Y / Z") {
                Text(sensorStatusText(state.sensorStatus), color = MaterialTheme.colorScheme.primary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("X\n${number(state.raw?.x)}")
                    Text("Y\n${number(state.raw?.y)}")
                    Text("Z\n${number(state.raw?.z)}")
                }
                Text(
                    "Сирі значення, м/с² · Подій: ${state.samples}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Panel("Змодельований кут світла") {
                Text("${number(state.angle)}°", style = MaterialTheme.typography.displaySmall)
                LinearProgressIndicator(
                    progress = { (state.angle ?: 0f) / 90f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("0° — пряме падіння; 90° — ковзне.")
                Text(
                    "Нахил імітує кут світла. Реальний напрямок та освітленість не вимірюються.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (state.invalidReading) {
                    Text(
                        "Некоректний або нульовий вектор: останній стан жалюзі збережено.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Panel("Віртуальні жалюзі") {
                Text(
                    state.position?.label ?: "Очікування даних",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text("Відкриття: ${state.position?.openingPercent?.let { "$it %" } ?: "—"}")
                BlindsCanvas(state.position?.openingPercent, state.angle)
                Text("Пороги: 30° / 60° · Гістерезис: ±3°", style = MaterialTheme.typography.bodySmall)
            }

            Panel("Mock BLE · пошук пристроїв") {
                Button(
                    onClick = { onAction(SmartBlindAction.SearchDevices) },
                    enabled = !state.isScanning,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Text("  Сканування…")
                    } else {
                        Text("Пошук пристроїв")
                    }
                }

                if (!state.isScanning && state.devices.isEmpty()) {
                    Text("Список порожній. Пошук триває 2 секунди.")
                }
                state.devices.forEach { device ->
                    val selected = device.id == state.selectedDevice?.id
                    OutlinedButton(
                        onClick = { onAction(SmartBlindAction.SelectDevice(device)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "${if (selected) "✓ " else ""}${device.name} · " +
                                "${device.signalStrengthDbm} dBm\n${device.id}"
                        )
                    }
                }
            }

            Panel("З’єднання та передача даних") {
                Text(
                    "Статус: ${connectionText(state.connectionState)}",
                    color = connectionColor(state.connectionState),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text("Пристрій: ${state.selectedDevice?.name ?: "не вибрано"}")

                Button(
                    onClick = { onAction(SmartBlindAction.ConnectSelected) },
                    enabled = state.selectedDevice != null &&
                        state.connectionState == BleConnectionState.DISCONNECTED,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Підключити")
                }
                OutlinedButton(
                    onClick = { onAction(SmartBlindAction.Disconnect) },
                    enabled = state.connectionState != BleConnectionState.DISCONNECTED,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Відключити")
                }
                Button(
                    onClick = { onAction(SmartBlindAction.SendCurrentPosition) },
                    enabled = state.connectionState == BleConnectionState.CONNECTED &&
                        state.position != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Надіслати положення жалюзі")
                }
                OutlinedButton(
                    onClick = { onAction(SmartBlindAction.ReadRemoteStatus) },
                    enabled = state.connectionState == BleConnectionState.CONNECTED,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Read status")
                }
            }

            Panel("BLE-термінал · WRITE / NOTIFY") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Color(0xFF101418))
                        .padding(12.dp),
                ) {
                    Text(
                        text = state.terminalLines.joinToString("\n").ifEmpty {
                            "Тут з’явиться журнал обміну.\nLogcat tag: SmartBlindsBLE"
                        },
                        color = Color(0xFFB8F5C8),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                OutlinedButton(
                    onClick = { onAction(SmartBlindAction.ClearTerminal) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Очистити термінал")
                }
            }
        }
    }
}

@Composable
private fun Panel(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun BlindsCanvas(openingPercent: Int?, angle: Float?) {
    Canvas(Modifier.fillMaxWidth().height(190.dp).semantics {
        contentDescription = if (openingPercent == null) "Жалюзі очікують дані"
        else "Відкриття жалюзі $openingPercent відсотків, кут ${number(angle)} градусів"
    }) {
        val origin = Offset(size.width * 0.08f, size.height * 0.08f)
        val area = Size(size.width * 0.84f, size.height * 0.84f)
        drawRect(Color(0xFFCEE8F0), origin, area)
        if (angle != null) {
            val radians = Math.toRadians(angle.toDouble())
            val end = Offset(origin.x + area.width / 2, origin.y + area.height / 2)
            val length = area.height * 0.42f
            val start = Offset(
                end.x - (sin(radians) * length).toFloat(),
                end.y - (cos(radians) * length).toFloat(),
            )
            drawLine(Color(0xFFEDA924), start, end, strokeWidth = 6.dp.toPx())
        }
        if (openingPercent != null) {
            val step = area.height / 8
            val thickness = step * (0.88f - openingPercent / 100f * 0.75f)
            repeat(8) {
                drawRect(
                    Color(0xFF247D70),
                    Offset(origin.x, origin.y + it * step + (step - thickness) / 2),
                    Size(area.width, thickness),
                )
            }
        }
        drawRect(Color(0xFF365B55), origin, area, style = Stroke(3.dp.toPx()))
    }
}

private fun sensorStatusText(status: SensorStatus) = when (status) {
    SensorStatus.WAITING -> "Очікування першої події…"
    SensorStatus.ACTIVE -> "Зчитування активне"
    SensorStatus.PAUSED -> "Зчитування призупинено"
    SensorStatus.UNAVAILABLE -> "Акселерометр відсутній. Оберіть інший AVD."
    SensorStatus.ERROR -> "Помилка реєстрації сенсора. Перезапустіть додаток."
}

private fun connectionText(status: BleConnectionState) = when (status) {
    BleConnectionState.DISCONNECTED -> "Disconnected"
    BleConnectionState.CONNECTING -> "Connecting"
    BleConnectionState.CONNECTED -> "Connected"
}

private fun connectionColor(status: BleConnectionState) = when (status) {
    BleConnectionState.DISCONNECTED -> Color(0xFFB3261E)
    BleConnectionState.CONNECTING -> Color(0xFF8A6200)
    BleConnectionState.CONNECTED -> Color(0xFF1B7F3A)
}

@Preview(showBackground = true)
@Composable
private fun SmartBlindPreview() {
    LR_1Theme(dynamicColor = false) {
        SmartBlindContent(
            state = SmartBlindState(
                raw = Acceleration(6.94f, 0f, 6.94f),
                angle = 45f,
                position = BlindPosition.HALF_OPEN,
                sensorStatus = SensorStatus.ACTIVE,
                samples = 120,
                devices = listOf(BleDevice("MOCK-SBC-19", "Smart Blind Hub #19", -47)),
                connectionState = BleConnectionState.CONNECTED,
                terminalLines = listOf("12:00:00  NOTIFY ACK|SET_OPENING|50"),
            ),
            onAction = {},
        )
    }
}
