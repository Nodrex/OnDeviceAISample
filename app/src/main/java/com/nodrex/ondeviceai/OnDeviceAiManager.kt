package com.nodrex.ondeviceai


import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
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

        // Step 1: Get the model instance
        val model = getPromptApi(this)
        model ?: return@flow

        // Step 2: Check status of the model and download if necessary
        val isDownloaded = checkAIModelStatus(model, this)
        if (isDownloaded) {
            emit(AIResult(answer = "", status = AIStatus.AI_MODEL_READY))
        } else return@flow

        // Step 3: Generate Answer
        generateAnswer(prompt, model, this)
    }

    private suspend fun getPromptApi(flow: FlowCollector<AIResult>): GenerativeModel? {
        return try {
            // Using Generation.getClient() as per the Jetpack/ML Kit Compose integration.
            // Under the hood, this obtains access to Gemini Nano on device.
            Generation.getClient()
        } catch (e: Exception) {
            // If download fails (e.g., no internet for initial download, unsupported device), abort.
            flow.emit(
                AIResult(
                    answer = "Failed to load model: ${e.message}",
                    status = AIStatus.FAILED_TO_LOAD_AI_MODEL
                )
            )
            return null
        }
    }

    private suspend fun checkAIModelStatus(model: GenerativeModel, flow: FlowCollector<AIResult>) =
        when (model.checkStatus()) {
            FeatureStatus.AVAILABLE -> {
                // Model initialized successfully and ready for inference
                true
            }

            FeatureStatus.DOWNLOADABLE -> {
                downloadAIModel(model, flow)
            }

            FeatureStatus.UNAVAILABLE -> {
                val reason = "Gemini Nano is not available on this device. " +
                        "Supported devices: Pixel 9/10 series"
                flow.emit(
                    AIResult(
                        answer = reason,
                        status = AIStatus.FAILED_TO_LOAD_AI_MODEL
                    )
                )
                true
            }

            else -> false
        }


    private suspend fun downloadAIModel(
        model: GenerativeModel,
        flow: FlowCollector<AIResult>
    ): Boolean {
        try {
            var isDownloaded = false
            model.download().collect { status ->
                when (status) {
                    is DownloadStatus.DownloadStarted -> {
                        flow.emit(
                            AIResult(
                                answer = "Download started...",
                                status = AIStatus.LOADING_AI_MODEL
                            )
                        )
                    }

                    is DownloadStatus.DownloadProgress -> {
                        flow.emit(
                            AIResult(
                                answer = "Download progress: ${status.totalBytesDownloaded} bytes",
                                status = AIStatus.LOADING_AI_MODEL
                            )
                        )
                    }

                    DownloadStatus.DownloadCompleted -> {
                        flow.emit(
                            AIResult(
                                answer = "",
                                status = AIStatus.AI_MODEL_LOADED_SUCCESSFULLY
                            )
                        )
                        isDownloaded = true
                    }

                    is DownloadStatus.DownloadFailed -> {
                        flow.emit(
                            AIResult(
                                answer = status.e.message ?: "",
                                status = AIStatus.FAILED_TO_LOAD_AI_MODEL
                            )
                        )
                        isDownloaded = false
                    }
                }
            }
            return isDownloaded
        } catch (e: Exception) {
            flow.emit(
                AIResult(
                    answer = e.stackTrace.joinToString(separator = "\n"),
                    status = AIStatus.FAILED_TO_LOAD_AI_MODEL
                )
            )
        }
        return false
    }

    private suspend fun generateAnswer(
        prompt: String,
        model: GenerativeModel,
        flow: FlowCollector<AIResult>
    ) {
        flow.emit(AIResult(answer = "", status = AIStatus.ANALYZING_PROMPT))

        try {
            // Send the prompt to the on-device model and await the output
            val response = model.generateContent(prompt)
            // For now, we will simply use first candidate from returned results
            val resultText = response.candidates[0].text


            Util.log("AiManager: Generated result ready -> $resultText")

            flow.emit(
                AIResult(
                    answer = resultText,
                    status = AIStatus.GENERATED_ANSWER_READY
                )
            )


        } catch (e: Exception) {
            // Inference failed
            flow.emit(
                AIResult(
                    answer = "Failed to answer: ${e.message}",
                    status = AIStatus.FAILED_TO_GENERATE_ANSWER
                )
            )
        }
    }

}
