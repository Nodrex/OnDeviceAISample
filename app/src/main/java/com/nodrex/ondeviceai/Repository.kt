package com.nodrex.ondeviceai

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/**
 * Repository wraps the Manager to provide a clean data access layer for the ViewModel.
 */
class Repository(private val aiManager: OnDeviceAiManager) {

    /**
     * Obtains the generation flow for a given text prompt.
     */
    fun observePromptExecution(prompt: String): Flow<AIResult> {
        return aiManager.processPrompt(prompt).onEach { result ->
            if (result.status == AIStatus.ANSWER_READY) {
                Log.d("OnDeviceAI", "Repository: Passing final result to ViewModel -> ${result.answer}")
            }
        }
    }
}
