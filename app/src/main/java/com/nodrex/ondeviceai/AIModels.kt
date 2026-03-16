package com.nodrex.ondeviceai

/**
 * Represents the lifecycle stages of the on-device AI process.
 */
enum class AIStatus {
    AI_IDLE,                      // Initial state
    LOADING_AI_MODEL,          // Model is being downloaded or instantiated
    AI_MODEL_LOADED_SUCCESSFULLY,        // Model was loaded successfully
    FAILED_TO_LOAD_AI_MODEL,   // Model failed to download or load into memory
    AI_MODEL_READY,            // Model is initialized and ready to take prompts
    ANALYZING_PROMPT,         // Model is currently inferencing
    GENERATED_ANSWER_READY,              // Model has completed inference successfully
    FAILED_TO_GENERATE_ANSWER           // Model encountered an error during inference
}

/**
 * Encapsulates the state and output of the AI generation request.
 */
data class AIResult(
    val answer: String,
    val status: AIStatus,
    val delayMs: Long? = null
)
