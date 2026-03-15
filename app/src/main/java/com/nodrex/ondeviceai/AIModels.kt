package com.nodrex.ondeviceai

/**
 * Represents the lifecycle stages of the on-device AI process.
 */
enum class AIStatus {
    IDLE,                      // Initial state
    LOADING_AI_MODEL,          // Model is being downloaded or instantiated
    FAILED_TO_LOAD_AI_MODEL,   // Model failed to download or load into memory
    AI_MODEL_READY,            // Model is initialized and ready to take prompts
    GENERATING_ANSWER,         // Model is currently inferencing
    ANSWER_READY,              // Model has completed inference successfully
    FAILED_TO_ANSWER           // Model encountered an error during inference
}

/**
 * Encapsulates the state and output of the AI generation request.
 */
data class AIResult(
    val answer: String,
    val status: AIStatus
)
