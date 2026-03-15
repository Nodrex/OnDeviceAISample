package com.nodrex.ondeviceai


import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel orchestrates the Unidirectional Data Flow between UI and Repository.
 */
class MainViewModel(private val repository: Repository) : ViewModel() {

    // Internal mutable state
    private val _aiResultState = MutableStateFlow(AIResult("", AIStatus.IDLE))

    // Public immutable state consumed by Compose
    val aiResultState: StateFlow<AIResult> = _aiResultState.asStateFlow()

    /**
     * Triggered by user interaction. Collects the Repository flow and posts upstream.
     */
    fun sendPrompt(prompt: String) {
        if (prompt.isBlank()) return

        val startTime = System.currentTimeMillis()

        viewModelScope.launch {
            repository.observePromptExecution(prompt).collect { result ->
                if (result.status == AIStatus.ANSWER_READY) {
                    val delay = System.currentTimeMillis() - startTime
                    val finalResult = result.copy(delayMs = delay)
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
