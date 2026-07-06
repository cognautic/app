package com.cognautic.app.core.models

enum class ModelProvider(val displayName: String) {
    OPENROUTER("OpenRouter"),
    GOOGLE("Google Gemini"),
    ANTHROPIC("Anthropic"),
    OPENAI("OpenAI")
}

data class LlmProvider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val protocol: ModelProvider,
    val isBuiltIn: Boolean = false
)

data class AiModel(
    val id: String,
    val name: String,
    val providerId: String
)

data class AgentConfig(
    val showThinking: Boolean = false,
    val temperature: Float = 0.2f
)

enum class Role {
    USER, ASSISTANT, SYSTEM, TOOL_CALL, THINKING, ERROR
}

enum class ToolStatus {
    PENDING, EXECUTING, DONE, FAILED
}

data class Attachment(
    val id: String,
    val name: String,
    val mimeType: String,
    val uri: String, // local uri or content provider uri
    val size: Long
)

data class ChatMessage(
    val id: String,
    val role: Role,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val toolData: Map<String, String>? = null,
    val toolStatus: ToolStatus? = null,
    val isVisible: Boolean = true,
    val attachments: List<Attachment> = emptyList()
)

data class Workspace(
    val id: String,
    val name: String,
    val path: String,
    val rules: String = ""
)
