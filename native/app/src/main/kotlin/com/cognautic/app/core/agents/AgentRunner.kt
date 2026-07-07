package com.cognautic.app.core.agents

import com.cognautic.app.core.models.AiModel
import com.cognautic.app.core.models.AgentConfig
import com.cognautic.app.core.models.ChatMessage
import com.cognautic.app.core.models.Role
import com.cognautic.app.core.network.LlmClient
import com.cognautic.app.core.tools.ToolRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONObject
import java.util.UUID
import com.cognautic.app.core.models.ToolStatus
import com.cognautic.app.core.models.LlmProvider

class AgentRunner(private val context: android.content.Context, private val workspacePath: String) {

    private val toolRegistry = ToolRegistry(context, workspacePath)
    
    // Returns a Flow of partial updates (thoughts, tool executions) and finally the result
    fun runAgentTask(
        task: String, 
        model: AiModel, 
        provider: LlmProvider,
        history: List<ChatMessage>,
        config: AgentConfig = AgentConfig(),
        globalRules: String = "",
        workspaceRules: String = "",
        approvalCallback: suspend (String, String, Map<String, String>) -> Boolean
    ): Flow<ChatMessage> = flow {
        
        val systemPrompt = """
            You are Cognautic, an expert coding agent.

            Workspace: $workspacePath
            ${toolRegistry.getSystemPromptSupplement()}
            ${if (globalRules.isNotBlank()) "RULES:\n$globalRules\n" else ""}
            ${if (workspaceRules.isNotBlank()) "WS-RULES:\n$workspaceRules\n" else ""}

            INSTRUCTIONS:
            - Use 'list_files' to explore, 'read_file' to read, 'write_file' to create/modify
            - Use 'replace_content' for precise edits, 'apply_edits' for multiple changes
            - Use 'run_command' for shell commands, tests, builds
            - Prefer small steps: inspect before editing, verify after
            - To use a tool, respond ONLY with JSON: { "tool": "name", "args": {...} }
            - For user messages, reply with plain text only.
        """.trimIndent()

        val activeMessages = history.toMutableList()
        var loops = 0
        val MAX_LOOPS = 5
        
        while (loops < MAX_LOOPS) {
            loops++
            
            var rawResponse = ""
            var currentResponse = ""
            val messageId = UUID.randomUUID().toString()
            val thinkingId = UUID.randomUUID().toString()
            var providerThinking = ""
            var tagThinking = ""
            
            try {
                LlmClient.generateStreamingCompletion(
                    context = context,
                    model = model,
                    provider = provider,
                    messages = activeMessages,
                    systemPrompt = systemPrompt,
                    temperature = config.temperature
                )
                .collect { chunk ->
                    if (chunk.isThinking) {
                        providerThinking += chunk.content
                        if (config.showThinking) {
                            val combinedThinking = combineThinking(providerThinking, tagThinking)
                            if (combinedThinking.isNotBlank()) {
                                emit(ChatMessage(thinkingId, Role.THINKING, combinedThinking))
                            }
                        }
                    } else {
                        rawResponse += chunk.content
                        val parsed = splitVisibleAndThinking(rawResponse)
                        currentResponse = parsed.visible
                        tagThinking = parsed.thinking
                        if (config.showThinking) {
                            val combinedThinking = combineThinking(providerThinking, tagThinking)
                            if (combinedThinking.isNotBlank()) {
                                emit(ChatMessage(thinkingId, Role.THINKING, combinedThinking))
                            }
                        }
                        val trimmed = currentResponse.trim()
                        // Don't emit the message while it's being typed if it looks like a tool call (JSON or MD-JSON)
                        val isLikelyTool = trimmed.startsWith("{") || trimmed.startsWith("```json") || trimmed.startsWith("```")
                        if (!isLikelyTool && trimmed.isNotBlank()) {
                            emit(ChatMessage(messageId, Role.ASSISTANT, currentResponse))
                        }
                    }
                }
            } catch (e: Exception) {
                emit(ChatMessage(UUID.randomUUID().toString(), Role.ERROR, "Network Error: ${e.message}"))
                break
            }

            val toolCall = parseToolCall(currentResponse)
            if (toolCall != null) {
                // 1. Emit PENDING Tool Call
                val toolCallMsg = ChatMessage(
                    id = messageId,
                    role = Role.TOOL_CALL,
                    content = "Using tool: ${toolCall.name}",
                    toolData = mapOf("name" to toolCall.name) + toolCall.args,
                    toolStatus = ToolStatus.PENDING
                )
                emit(toolCallMsg)
                
                // 3. Ask for Approval
                val approved = approvalCallback(messageId, toolCall.name, toolCall.args)
                
                if (approved) {
                    // Update to EXECUTING
                    emit(toolCallMsg.copy(toolStatus = ToolStatus.EXECUTING, content = "Executing: ${toolCall.name}..."))
                    
                    val tool = toolRegistry.getTool(toolCall.name)
                    var isSuccess = true
                    val toolOutput = try {
                         if (tool != null) tool.execute(toolCall.args) else {
                             isSuccess = false
                             "Error: Tool not found."
                         }
                    } catch (e: Exception) {
                         isSuccess = false
                         "Error executing tool: ${e.message}"
                    }
                    
                    // Update Tool Call to DONE/FAILED
                    val finalStatus = if (isSuccess) ToolStatus.DONE else ToolStatus.FAILED
                    val resultText = if (isSuccess) "Finished: ${toolCall.name}" else "Failed: ${toolCall.name}"
                    emit(toolCallMsg.copy(toolStatus = finalStatus, content = resultText))
                    
                    // 4. Update History
                    activeMessages.add(ChatMessage(UUID.randomUUID().toString(), Role.ASSISTANT, currentResponse))
                    val toolOutputMsg = "Tool Response (${toolCall.name}):\n$toolOutput"
                    activeMessages.add(ChatMessage(UUID.randomUUID().toString(), Role.SYSTEM, toolOutputMsg))
                    
                    // Emit system output for history/UI - but hide from user
                    emit(ChatMessage(UUID.randomUUID().toString(), Role.SYSTEM, toolOutputMsg, isVisible = false))
                } else {
                    // Rejected
                    emit(ChatMessage(UUID.randomUUID().toString(), Role.SYSTEM, "User rejected tool: ${toolCall.name}", isVisible = false))
                    activeMessages.add(ChatMessage(UUID.randomUUID().toString(), Role.ASSISTANT, currentResponse))
                    activeMessages.add(ChatMessage(UUID.randomUUID().toString(), Role.SYSTEM, "User rejected this tool execution. Stop or ask for different instructions."))
                }
            } else {
                // Final Answer (Standard Text) - already emitted by streaming loop 
                break // Stop loop
            }
        }
        
        if (loops >= MAX_LOOPS) {
             emit(ChatMessage(UUID.randomUUID().toString(), Role.ERROR, "Agent loop limit reached."))
        }
    }

    private data class ParsedTool(val name: String, val args: Map<String, String>)

    private data class ParsedResponse(val visible: String, val thinking: String)

    private fun combineThinking(providerThinking: String, taggedThinking: String): String {
        return listOf(providerThinking, taggedThinking)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
    }

    private fun splitVisibleAndThinking(text: String): ParsedResponse {
        val visible = StringBuilder()
        val thinking = StringBuilder()
        var index = 0

        while (index < text.length) {
            val thinkStart = findNextThinkingStart(text, index)
            if (thinkStart == -1) {
                visible.append(text.substring(index))
                break
            }

            visible.append(text.substring(index, thinkStart))
            val startTag = if (text.startsWith("<thinking>", thinkStart, ignoreCase = true)) {
                "<thinking>"
            } else {
                "<think>"
            }
            val contentStart = thinkStart + startTag.length
            val endTag = if (startTag == "<thinking>") "</thinking>" else "</think>"
            val thinkEnd = text.indexOf(endTag, contentStart, ignoreCase = true)
            if (thinkEnd == -1) {
                thinking.append(text.substring(contentStart))
                break
            }

            thinking.append(text.substring(contentStart, thinkEnd))
            index = thinkEnd + endTag.length
        }

        return ParsedResponse(
            visible = visible.toString(),
            thinking = thinking.toString()
        )
    }

    private fun findNextThinkingStart(text: String, startIndex: Int): Int {
        val think = text.indexOf("<think>", startIndex, ignoreCase = true)
        val thinking = text.indexOf("<thinking>", startIndex, ignoreCase = true)
        return when {
            think == -1 -> thinking
            thinking == -1 -> think
            else -> minOf(think, thinking)
        }
    }

    private fun parseToolCall(text: String): ParsedTool? {
        var cleaned = text.trim()
        
        // Handle markdown code blocks
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.removePrefix("```json").removePrefix("```").trim()
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.removeSuffix("```").trim()
            }
        }
        
        // If it still doesn't look like JSON, try to find the first { and last }
        if (!cleaned.startsWith("{")) {
            val start = cleaned.indexOf("{")
            val end = cleaned.lastIndexOf("}")
            if (start != -1 && end != -1 && end > start) {
                cleaned = cleaned.substring(start, end + 1)
            } else {
                return null
            }
        }
        
        return try {
            val json = JSONObject(cleaned)
            if (json.has("tool")) {
                val name = json.getString("tool")
                val argsJson = json.optJSONObject("args")
                val argsMap = mutableMapOf<String, String>()
                argsJson?.keys()?.forEach { key ->
                    argsMap[key] = argsJson.getString(key)
                }
                ParsedTool(name, argsMap)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
