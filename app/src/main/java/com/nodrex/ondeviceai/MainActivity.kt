package com.nodrex.ondeviceai

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.nodrex.ondeviceai.ui.theme.OnDeviceAITheme
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Checkbox
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width

// AI Models, Manager, Repository, and ViewModel have been moved to separate files.

// --- Presentation UI (Activity / Compose) ---

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Provide ViewModel via our custom Factory so it gets its dependencies
        val viewModel = ViewModelProvider(this, MainViewModelFactory(this))[MainViewModel::class.java]

        setContent {
            OnDeviceAITheme {
                OnDeviceAiScreen(viewModel = viewModel)
            }
        }
    }
}

/**
 * Main Composable Screen rendering the AI chat interface.
 */
@Composable
fun OnDeviceAiScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    // Collect the StateFlow from ViewModel into Compose state
    val resultState by viewModel.aiResultState.collectAsState()
    val isAutoRefreshChecked by viewModel.isAutoRefreshChecked.collectAsState()
    val isNotificationsEnabled by viewModel.isNotificationsEnabled.collectAsState()

    // Local state to track what the user is currently typing
    var inputText by remember { mutableStateOf("") }

    // Scroll state so the results are scrollable when large
    val scrollState = rememberScrollState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Input Area ---
            val isBusy = resultState.status == AIStatus.LOADING_AI_MODEL ||
                    resultState.status == AIStatus.ANALYZING_PROMPT

            PromptInputArea(
                inputText = inputText,
                isBusy = isBusy,
                onInputTextChanged = { inputText = it },
                onSendClicked = {
                    viewModel.sendPrompt(inputText)
                }
            )

            // --- Status Display ---
            StatusDisplay(status = resultState.status)

            // --- Delay Display ---
            DelayDisplay(delayMs = resultState.delayMs, status = resultState.status)

            // --- UI Controls Row (NEW) ---
            UiControlsRow(
                isAutoRefreshChecked = isAutoRefreshChecked,
                isNotificationsEnabled = isNotificationsEnabled,
                onInfoClicked = { viewModel.triggerInfoSnackbar() },
                onAutoRefreshChanged = { viewModel.setAutoRefresh(it) },
                onNotificationsChanged = { viewModel.setNotifications(it) }
            )

            // --- Result Display ---
            ResultDisplay(resultState = resultState, scrollState = scrollState)
        }
    }
}

@Composable
fun UiControlsRow(
    isAutoRefreshChecked: Boolean,
    isNotificationsEnabled: Boolean,
    onInfoClicked: () -> Unit,
    onAutoRefreshChanged: (Boolean) -> Unit,
    onNotificationsChanged: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onInfoClicked) {
            Icon(Icons.Default.Info, contentDescription = "Info")
        }
        
        Spacer(Modifier.width(8.dp))
        
        Text("Auto Refresh")
        Checkbox(
            checked = isAutoRefreshChecked,
            onCheckedChange = onAutoRefreshChanged
        )

        Spacer(Modifier.width(16.dp))
        
        Text("Notifications")
        Switch(
            checked = isNotificationsEnabled,
            onCheckedChange = onNotificationsChanged
        )
    }
}

@Composable
fun PromptInputArea(
    inputText: String,
    isBusy: Boolean,
    onInputTextChanged: (String) -> Unit,
    onSendClicked: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = inputText,
            onValueChange = onInputTextChanged,
            modifier = Modifier.weight(1f),
            label = { Text("Ask the On-Device AI...") },
            enabled = !isBusy // Disable input while AI is working
        )

        IconButton(
            onClick = onSendClicked,
            enabled = inputText.isNotBlank() && !isBusy
        ) {
            if (isBusy) {
                // Show a small spinner in place of the button while working
                CircularProgressIndicator(modifier = Modifier.padding(8.dp))
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send prompt"
                )
            }
        }
    }
}

@Composable
fun StatusDisplay(status: AIStatus) {
    Text(
        text = "Status: ${formatStatus(status)}",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
}

@SuppressLint("DefaultLocale")
@Composable
fun DelayDisplay(delayMs: Long?, status: AIStatus) {
    if (delayMs != null && status == AIStatus.GENERATED_ANSWER_READY) {
        val (delayText, delayColor) = when {
            delayMs < 10000 -> {
                "${String.format("%.1f", delayMs / 1000f)}s" to MaterialTheme.colorScheme.primary // Green/Primary as status color
            }
            delayMs <= 20000 -> {
                "${String.format("%.1f", delayMs / 1000f)}s" to Color(0xFFF57F17) // Yellow (Darker for readability)
            }
            else -> {
                "${String.format("%.1f", delayMs / 1000f)}s" to MaterialTheme.colorScheme.error // Red
            }
        }

        Text(
            text = "Delay: $delayText",
            style = MaterialTheme.typography.labelLarge,
            color = delayColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ResultDisplay(resultState: AIResult, scrollState: androidx.compose.foundation.ScrollState) {
    // Only show the result area if we have an answer or an error message
    val showResultBox = resultState.status == AIStatus.GENERATED_ANSWER_READY ||
            resultState.status == AIStatus.FAILED_TO_GENERATE_ANSWER ||
            resultState.status == AIStatus.FAILED_TO_LOAD_AI_MODEL

    if (showResultBox && resultState.answer.isNotBlank()) {
        val textColor = if (resultState.status == AIStatus.GENERATED_ANSWER_READY) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.error // Red text for failures
        }

        Text(
            text = resultState.answer,
            fontSize = 20.sp,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
        )
    }
}

/**
 * Simple helper to format the raw Enum name into a readable string.
 */
fun formatStatus(status: AIStatus): String {
    return status.name
        .replace("_", " ")
        .lowercase()
        .replaceFirstChar { it.titlecase() }
}
