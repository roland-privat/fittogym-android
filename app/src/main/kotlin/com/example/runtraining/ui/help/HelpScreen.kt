package com.example.runtraining.ui.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Static, offline Help/FAQ + privacy summary reachable from Options (FR-041). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help & privacy") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Frequently asked questions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

            Faq(
                "How do I add a workout?",
                "Tap + on the library to pick a .fit file, share one to FitToGym from another app, or open a .fit from a file manager.",
            )
            Faq(
                "Why are my targets shown as pace or speed?",
                "Set your preference in Options (pace min:ss/km, or speed km/h). Targets and Training Stress (TSS) are based on the threshold pace you enter.",
            )
            Faq(
                "What are the beeps during a run?",
                "A five-second countdown \u2014 one beep per second \u2014 before each step change, so you know when to adjust the treadmill. They use the media volume and play even with the screen off.",
            )
            Faq(
                "How do I use a heart-rate monitor?",
                "Options \u2192 Connect HR monitor pairs a Bluetooth chest strap or armband. It's optional; everything else works without it.",
            )
            Faq(
                "What is the mini view?",
                "A small floating overlay that shows your current step and remaining time on top of other apps while a workout runs. Enable it on the Run page.",
            )
            Faq(
                "Does it work offline?",
                "Yes \u2014 completely. The app has no internet access at all.",
            )

            Text("Privacy", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "Nothing leaves your device. FitToGym does not declare the INTERNET permission, so it is technically incapable of sending data anywhere.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "Your imported workouts, settings, and heart-rate readings are stored only on this device and are removed when you uninstall. No account, no ads, no analytics, no cloud.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "Bluetooth is used only to connect a heart-rate monitor (declared neverForLocation \u2014 never used to infer your location). The overlay permission is optional and only powers the mini view.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Faq(question: String, answer: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(question, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(answer, style = MaterialTheme.typography.bodyLarge)
    }
}
