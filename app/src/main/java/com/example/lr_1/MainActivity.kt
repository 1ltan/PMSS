package com.example.lr_1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.lr_1.analytics.SensorViewModel
import com.example.lr_1.monitor.SmartBlindAction
import com.example.lr_1.monitor.SmartBlindScreen
import com.example.lr_1.monitor.SmartBlindViewModel
import com.example.lr_1.ui.theme.LR_1Theme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: SmartBlindViewModel by viewModels()
    private val sensorViewModel: SensorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.sensorSamples.collect(sensorViewModel::onSensorSample)
            }
        }
        setContent { LR_1Theme { SmartBlindScreen(viewModel, sensorViewModel) } }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAction(SmartBlindAction.StartSensor)
    }

    override fun onPause() {
        viewModel.onAction(SmartBlindAction.StopSensor)
        super.onPause()
    }
}
