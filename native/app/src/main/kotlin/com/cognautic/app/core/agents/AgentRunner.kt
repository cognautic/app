package com.cognautic.app.core.agents

import com.cognautic.app.core.models.AiModel
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
        globalRules: String = "",
        workspaceRules: String = "",
        approvalCallback: suspend (String, String, Map<String, String>) -> Boolean
    ): Flow<ChatMessage> = flow {
        
        val systemPrompt = """
            You are Cognautic, an expert All-In-One Coding Agent.
            
            You are running on an Android environment, but you are capable of coding in any language 
            (Kotlin, Python, JavaScript, Rust, C++, etc.) and handling any type of project.
            
            Current Workspace: $workspacePath
            
            ${toolRegistry.getSystemPromptSupplement()}
            
            ${if (globalRules.isNotBlank()) "GLOBAL RULES (MUST OBEY):\n$globalRules\n" else ""}
            ${if (workspaceRules.isNotBlank()) "WORKSPACE RULES (MUST OBEY):\n$workspaceRules\n" else ""}
            
            INSTRUCTIONS:
            1. You have full access to the file system in the workspace. Use 'list_files' to explore and 'read_file' to understand the code.
            2. When asked to code, ALWAYS check existing files first if relevant.
            3. Use 'write_file' to create or modify files.
            4. Provide concise plans before executing complex changes.
            5. Use 'run_command' to execute shell commands, run tests, or perform build operations.
            6. You are an autonomous agent; use your tools proactively to solve the user's request.
            
            IMPORTANT: To use a tool, your ENTIRE response must be valid JSON matching the format:
            { "tool": "name", "args": { ... } }
            
            If you want to speak to the user, just send plain text (do not use JSON format).
        """.trimIndent()

        val activeMessages = history.toMutableList()
        var loops = 0
        val MAX_LOOPS = 10
        
        while (loops < MAX_LOOPS) {
            loops++
            
            var currentResponse = ""
            val messageId = UUID.randomUUID().toString()
            
            try {
                LlmClient.generateStreamingCompletion(context, model, provider, activeMessages, systemPrompt)
                .collect { chunk ->
                    currentResponse += chunk
                    val trimmed = currentResponse.trim()
                    // Don't emit the message while it's being typed if it looks like a tool call (JSON or MD-JSON)
                    val isLikelyTool = trimmed.startsWith("{") || trimmed.startsWith("```json") || trimmed.startsWith("```")
                    if (!isLikelyTool) {
                        emit(ChatMessage(messageId, Role.ASSISTANT, currentResponse))
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
