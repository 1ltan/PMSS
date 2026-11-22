package com.example.lr_1.analytics

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lr_1.data.local.SensorRecord
import com.example.lr_1.ml.BlindStateClass
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

private val AnalyticsSuccess = Color(0xFF19734F)
private val AnalyticsDanger = Color(0xFFB3261E)

@Composable
fun AnalyticsScreen(viewModel: SensorViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AnalyticsEvent.ShowMessage ->
                    Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
            }
        }
    }
    AnalyticsContent(state = state, onAction = viewModel::onAction)
}

@Composable
fun AnalyticsContent(state: SensorAnalyticsState, onAction: (AnalyticsAction) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            Text("Локальна аналітика", style = MaterialTheme.typography.headlineLarge)
            Text(
                "ML-стан і часовий ряд без хмарних обчислень",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryMetric("Записи", state.records.size.toString(), Modifier.weight(1f))
            SummaryMetric(
                "Актуатор",
                state.records.count(SensorRecord::actionTriggered).toString(),
                Modifier.weight(1f),
            )
            SummaryMetric(
                "Інтервал",
                "${state.recordingIntervalMs} мс",
                Modifier.weight(1f),
            )
        }

        AnalyticsPanel("AI Insights", "TensorFlow Lite · inference на пристрої") {
            val prediction = state.prediction
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(
                        prediction?.state?.label ?: "Накопичення вікна",
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (prediction?.state == BlindStateClass.ANOMALY) {
                            AnalyticsDanger
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(
                        prediction?.let {
                            "Confidence ${String.format(Locale.US, "%.1f", it.confidence * 100)} %"
                        } ?: "${state.windowSamples}/20 вимірів",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ModelBadge(state.modelReady)
            }
            LinearProgressIndicator(
                progress = {
                    prediction?.confidence ?: (state.windowSamples / 20f).coerceIn(0f, 1f)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            prediction?.let { current ->
                Text("Розподіл імовірностей", style = MaterialTheme.typography.titleMedium)
                BlindStateClass.entries.zip(current.probabilities).forEach { (blindState, probability) ->
                    ProbabilityRow(blindState.label, probability)
                }
            }
            if (state.anomalyActuated) {
                Notice("Аномалію виявлено. Віброактуатор активовано.", AnalyticsDanger)
            }
            state.modelError?.let { Notice(it, AnalyticsDanger) }
        }

        AnalyticsPanel("Динаміка кута", "Room · останні 50 вимірів") {
            if (state.records.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        "Історія порожня. Змініть нахил пристрою або емулятора.",
                        Modifier.fillMaxWidth().padding(18.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                SensorLineChart(state.records)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(state.records.first().timestamp), style = MaterialTheme.typography.bodySmall)
                    Text("Час", style = MaterialTheme.typography.bodySmall)
                    Text(formatTime(state.records.last().timestamp), style = MaterialTheme.typography.bodySmall)
                }
                val values = state.records.map(SensorRecord::sensorValue)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryMetric(
                        "Мінімум",
                        String.format(Locale.US, "%.1f°", values.min()),
                        Modifier.weight(1f),
                    )
                    SummaryMetric(
                        "Середнє",
                        String.format(Locale.US, "%.1f°", values.average()),
                        Modifier.weight(1f),
                    )
                    SummaryMetric(
                        "Максимум",
                        String.format(Locale.US, "%.1f°", values.max()),
                        Modifier.weight(1f),
                    )
                }
                Text(
                    "Вісь X показує час вимірювання, вісь Y — модельований кут світла.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.databaseError?.let { Notice(it, AnalyticsDanger) }
            OutlinedButton(
                onClick = { onAction(AnalyticsAction.ClearHistory) },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.records.isNotEmpty(),
            ) {
                Text("Очистити локальну історію")
            }
        }
    }
}

@Composable
private fun SensorLineChart(records: List<SensorRecord>) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val timesById = remember(records) { records.associate { it.id to it.timestamp } }
    val timeFormatter = remember(records, timesById) {
        CartesianValueFormatter { _, value, _ ->
            val timestamp = timesById[value.roundToLong()]
                ?: records.minByOrNull { abs(it.id.toDouble() - value) }?.timestamp
                ?: return@CartesianValueFormatter ""
            Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .format(chartTimeFormatter)
        }
    }
    LaunchedEffect(records) {
        modelProducer.runTransaction {
            lineModel {
                series(
                    x = records.map(SensorRecord::id),
                    y = records.map(SensorRecord::sensorValue),
                )
            }
        }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = timeFormatter),
        ),
        modelProducer = modelProducer,
        modifier = Modifier.fillMaxWidth().height(260.dp),
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
        animationSpec = null,
        initialAnimationSpec = null,
    )
}

@Composable
private fun AnalyticsPanel(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ModelBadge(ready: Boolean) {
    val color = if (ready) AnalyticsSuccess else AnalyticsDanger
    Surface(color = color.copy(alpha = 0.12f), contentColor = color, shape = RoundedCornerShape(50)) {
        Text(
            if (ready) "MODEL READY" else "MODEL ERROR",
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun ProbabilityRow(label: String, probability: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(
                String.format(Locale.US, "%.1f %%", probability * 100),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        LinearProgressIndicator(
            progress = { probability.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Notice(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp)) {
        Text(text, Modifier.fillMaxWidth().padding(12.dp), color = color)
    }
}

private val chartTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

private fun formatTime(timestamp: Long): String = Instant.ofEpochMilli(timestamp)
    .atZone(ZoneId.systemDefault())
    .format(chartTimeFormatter)
