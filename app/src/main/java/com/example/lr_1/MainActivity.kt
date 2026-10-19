package com.example.lr_1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.lr_1.monitor.SmartBlindAction
import com.example.lr_1.monitor.SmartBlindScreen
import com.example.lr_1.monitor.SmartBlindViewModel
import com.example.lr_1.ui.theme.LR_1Theme

class MainActivity : ComponentActivity() {
    private val viewModel: SmartBlindViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LR_1Theme { SmartBlindScreen(viewModel) } }
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
