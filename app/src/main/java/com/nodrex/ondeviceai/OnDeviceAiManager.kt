package com.nodrex.ondeviceai


import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Handles direct interaction with Google ML Kit's GenerativeModel.
 * Responsible for managing the AI's instance and execution safely.
 */
class OnDeviceAiManager(private val context: android.content.Context) {

    /**
     * Executes the full pipeline: initializes the model (downloading if needed),
     * and performs the inference if successful. Returns a flow emitting state updates.
     */
    fun processPrompt(prompt: String): Flow<AIResult> = flow {
        // Step 1: Initialize/Load the Model
        emit(AIResult(answer = "", status = AIStatus.LOADING_AI_MODEL))

        val model = try {
            // Using Generation.getClient() as per the Jetpack/ML Kit Compose integration.
            // Under the hood, this obtains access to Gemini Nano on device.
            // As found via reflection, getClient() has a no-args version and one taking GenerationConfig.
            com.google.mlkit.genai.prompt.Generation.getClient()
        } catch (e: Exception) {
            // If download fails (e.g., no internet for initial download, unsupported device), abort.
            emit(
                AIResult(
                    answer = "Failed to load model: ${e.message}",
                    status = AIStatus.FAILED_TO_LOAD_AI_MODEL
                )
            )
            return@flow
        }

        // Model initialized successfully
        emit(AIResult(answer = "", status = AIStatus.AI_MODEL_READY))

        // Step 2: Generate Answer
        emit(AIResult(answer = "", status = AIStatus.GENERATING_ANSWER))

        try {
            // Send the prompt to the on-device model and await the output
            val response = model.generateContent(prompt)
            val resultText = try {
                // For now, we will simply use first candidate from returned results
                response.candidates[0].text
            } catch (e: Exception) {
                "Generated."
            }

            Util.log("AiManager: Generated result ready -> $resultText")

            emit(
                AIResult(
                    answer = resultText,
                    status = AIStatus.ANSWER_READY
                )
            )
        } catch (e: Exception) {
            // Inference failed
            emit(
                AIResult(
                    answer = "Failed to answer: ${e.message}",
                    status = AIStatus.FAILED_TO_ANSWER
                )
            )
        }
    }
}
