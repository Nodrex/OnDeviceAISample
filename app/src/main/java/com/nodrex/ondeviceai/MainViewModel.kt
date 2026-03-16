package com.nodrex.ondeviceai


import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * ViewModel orchestrates the Unidirectional Data Flow between UI and Repository.
 */
class MainViewModel(private val repository: Repository) : ViewModel() {

    // Internal mutable state
    private val _aiResultState = MutableStateFlow(AIResult("", AIStatus.AI_IDLE))

    // Public immutable state consumed by Compose
    val aiResultState: StateFlow<AIResult> = _aiResultState.asStateFlow()

    // UI Control States
    private val _isAutoRefreshChecked = MutableStateFlow(false)
    val isAutoRefreshChecked: StateFlow<Boolean> = _isAutoRefreshChecked.asStateFlow()

    private val _isNotificationsEnabled = MutableStateFlow(false)
    val isNotificationsEnabled: StateFlow<Boolean> = _isNotificationsEnabled.asStateFlow()

    // Snackbar Event Flow
    private val _snackbarEvent = MutableSharedFlow<String>()
    val snackbarEvent = _snackbarEvent.asSharedFlow()

    fun triggerInfoSnackbar() {
        viewModelScope.launch {
            _snackbarEvent.emit("This is AI core demo app")
        }
    }

    fun setAutoRefresh(checked: Boolean) {
        _isAutoRefreshChecked.value = checked
    }

    fun setNotifications(enabled: Boolean) {
        _isNotificationsEnabled.value = enabled
    }

    /**
     * Triggered by user interaction. Collects the Repository flow and posts upstream.
     */
    fun sendPrompt(prompt: String) {
        if (prompt.isBlank()) return

        val startTime = System.currentTimeMillis()

        viewModelScope.launch {
            repository.observePromptExecution(prompt).collect { result ->
                if (result.status == AIStatus.GENERATED_ANSWER_READY) {
                    val delay = System.currentTimeMillis() - startTime
                    
                    var finalAnswer = result.answer
                    // --- JSON Intent Parsing Logic ---
                    // Check if the AI returned what looks like our expected JSON
                    if (finalAnswer.contains("{\"event\":")) {
                        try {
                            // Extract JSON block in case there's surrounding text
                            val startIndex = finalAnswer.indexOf("{")
                            val endIndex = finalAnswer.lastIndexOf("}")
                            if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                                val jsonString = finalAnswer.substring(startIndex, endIndex + 1)
                                val jsonObject = JSONObject(jsonString)
                                val eventName = jsonObject.optString("event")
                                
                                when (eventName) {
                                    "SHOW_INFO_SNACKBAR" -> triggerInfoSnackbar()
                                    "SELECT_AUTO_REFRESH" -> _isAutoRefreshChecked.value = true
                                    "TURN_ON_NOTIFICATIONS" -> _isNotificationsEnabled.value = true
                                }
                                // Do not show the raw JSON to the user
                                finalAnswer = ""
                            }
                        } catch (e: Exception) {
                            // If parsing fails, fall back to treating it as regular text
                        }
                    }

                    val finalResult = result.copy(delayMs = delay, answer = finalAnswer)
                    Util.log("ViewModel: Received final result in ${delay}ms and updating UI state -> ${finalResult.answer}")
                    _aiResultState.value = finalResult
                } else {
                    _aiResultState.value = result
                }
            }
        }
    }
}

/**
 * Basic Factory to instantiate our ViewModel without dependency injection frameworks.
 */
class MainViewModelFactory(private val context: android.content.Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            val manager = OnDeviceAiManager(context.applicationContext)
            val repository = Repository(manager)
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
