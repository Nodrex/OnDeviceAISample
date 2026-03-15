package com.nodrex.ondeviceai

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.nodrex.ondeviceai.ui.theme.OnDeviceAITheme
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
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    OnDeviceAiScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
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

    // Local state to track what the user is currently typing
    var inputText by remember { mutableStateOf("") }

    // Scroll state so the results are scrollable when large
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Input Area ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isBusy = resultState.status == AIStatus.LOADING_AI_MODEL ||
                    resultState.status == AIStatus.GENERATING_ANSWER

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                label = { Text("Ask the On-Device AI...") },
                enabled = !isBusy // Disable input while AI is working
            )

            IconButton(
                onClick = {
                    viewModel.sendPrompt(inputText)
                    // Optional: Clear or keep input text after sending
                    // inputText = ""
                },
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

        // --- Status Display ---
        Text(
            text = "Status: ${formatStatus(resultState.status)}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        // --- Result Display ---
        // Only show the result area if we have an answer or an error message
        val showResultBox = resultState.status == AIStatus.ANSWER_READY ||
                resultState.status == AIStatus.FAILED_TO_ANSWER ||
                resultState.status == AIStatus.FAILED_TO_LOAD_AI_MODEL

        if (showResultBox && resultState.answer.isNotBlank()) {
            val textColor = if (resultState.status == AIStatus.ANSWER_READY) {
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

@Preview(showBackground = true)
@Composable
@SuppressWarnings("ViewModelConstructorInComposable") // Safe for dummy preview instantiation
fun OnDeviceAiScreenPreview() {
    OnDeviceAITheme {
        // Creating a dummy repository and default view model just to satisfy the preview signature
        // Passing null or dummy context isn't ideal for preview but context is required now
        // Using LocalContext in Compose to inject it
        val context = androidx.compose.ui.platform.LocalContext.current
        val dumyRepo = Repository(OnDeviceAiManager(context))
        OnDeviceAiScreen(MainViewModel(dumyRepo))
    }
}