package com.cognautic.app.core.network

import android.content.Context
import android.util.Base64
import com.cognautic.app.core.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

// A basic HTTP Client using standard libraries to avoid dependency issues in this environment
object LlmClient {
    data class StreamChunk(
        val content: String,
        val isThinking: Boolean = false
    )

    fun generateStreamingCompletion(
        context: Context,
        model: AiModel,
        provider: LlmProvider,
        messages: List<ChatMessage>,
        systemPrompt: String? = null,
        temperature: Float = 0.2f
    ): Flow<StreamChunk> = flow {
        val safeTemperature = temperature.coerceIn(0f, 2f)
        val baseUrl = provider.baseUrl.trimEnd('/')
        val url = when (provider.protocol) {
            ModelProvider.OPENROUTER -> URL("$baseUrl/chat/completions")
            ModelProvider.OPENAI -> URL("$baseUrl/chat/completions")
            ModelProvider.ANTHROPIC -> URL("$baseUrl/messages")
            ModelProvider.GOOGLE -> URL("$baseUrl/models/${model.id}:streamGenerateContent?key=${provider.apiKey}")
        }

        val body = JSONObject().apply {
            if (provider.protocol == ModelProvider.GOOGLE) {
                put("contents", JSONArray().apply {
                    val mergedMessages = mutableListOf<JSONObject>()
                    messages.filter { it.role != Role.THINKING && it.role != Role.ERROR }.forEach { msg ->
                        val role = when (msg.role) {
                            Role.USER, Role.SYSTEM -> "user"
                            Role.ASSISTANT, Role.TOOL_CALL -> "model"
                            Role.THINKING -> "model"
                            else -> "user"
                        }
                        
                        val last = mergedMessages.lastOrNull()
                        val partsJson = JSONArray().apply { 
                            put(JSONObject().apply { put("text", msg.content) }) 
                            msg.attachments.forEach { att ->
                                val data = resolveAttachment(context, att)
                                if (data != null) {
                                    if (att.mimeType.startsWith("image/")) {
                                        put(JSONObject().apply {
                                            put("inlineData", JSONObject().apply {
                                                put("mimeType", att.mimeType)
                                                put("data", data)
                                            })
                                        })
                                    } else {
                                        // For non-images, just append info to text part or handle as needed
                                        // Some models support document upload, but for now we'll just append text if possible
                                        // or just reference the file name.
                                        put(JSONObject().apply { put("text", "\n[Attached File: ${att.name}]\n") })
                                    }
                                }
                            }
                        }

                        if (last != null && last.getString("role") == role) {
                            val existingParts = last.getJSONArray("parts")
                            for (i in 0 until partsJson.length()) {
                                existingParts.put(partsJson.get(i))
                            }
                        } else {
                            mergedMessages.add(JSONObject().apply {
                                put("role", role)
                                put("parts", partsJson)
                            })
                        }
                    }
                    mergedMessages.forEach { put(it) }
                })
                
                if (systemPrompt != null) {
                    put("system_instruction", JSONObject().apply { 
                        put("parts", JSONArray().apply { put(JSONObject().apply { put("text", systemPrompt) }) }) 
                    })
                }
                
                put("safetySettings", JSONArray().apply {
                    listOf("HARM_CATEGORY_HARASSMENT", "HARM_CATEGORY_HATE_SPEECH", "HARM_CATEGORY_SEXUALLY_EXPLICIT", "HARM_CATEGORY_DANGEROUS_CONTENT").forEach { category ->
                        put(JSONObject().apply {
                            put("category", category)
                            put("threshold", "BLOCK_NONE")
                        })
                    }
                })
                
                put("generationConfig", JSONObject().apply {
                    put("temperature", safeTemperature.toDouble())
                    put("maxOutputTokens", 4096)
                })
            } else if (provider.protocol == ModelProvider.ANTHROPIC) {
                if (systemPrompt != null) {
                    put("system", systemPrompt)
                }
                put("max_tokens", 4096)
                put("temperature", safeTemperature.toDouble())
                put("stream", true)
                put("messages", JSONArray().apply {
                    messages.filter { it.role != Role.THINKING && it.role != Role.ERROR }.forEach { msg ->
                        put(JSONObject().apply {
                            put("role", when(msg.role) {
                                Role.ASSISTANT, Role.TOOL_CALL -> "assistant"
                                Role.THINKING -> "assistant"
                                else -> "user" // SYSTEM and USER both map to user
                            })
                            put("content", msg.content)
                        })
                    }
                })
            } else { // OpenAI / OpenRouter
                put("model", model.id)
                put("stream", true)
                put("temperature", safeTemperature.toDouble())
                put("messages", JSONArray().apply {
                    if (systemPrompt != null) {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                    }
                    messages.filter { it.role != Role.THINKING && it.role != Role.ERROR }.forEach { msg ->
                        put(JSONObject().apply {
                            put("role", when(msg.role) {
                                Role.USER -> "user"
                                Role.ASSISTANT -> "assistant"
                                Role.TOOL_CALL -> "assistant"
                                Role.THINKING -> "assistant"
                                else -> "user"
                            })
                            
                            if (msg.attachments.any { it.mimeType.startsWith("image/") }) {
                                val contentArray = JSONArray()
                                contentArray.put(JSONObject().apply { put("type", "text"); put("text", msg.content) })
                                msg.attachments.forEach { att ->
                                    val data = resolveAttachment(context, att)
                                    if (data != null && att.mimeType.startsWith("image/")) {
                                        contentArray.put(JSONObject().apply {
                                            put("type", "image_url")
                                            put("image_url", JSONObject().apply { 
                                                put("url", "data:${att.mimeType};base64,$data") 
                                            })
                                        })
                                    }
                                }
                                put("content", contentArray)
                            } else {
                                put("content", msg.content)
                            }
                        })
                    }
                })
            }
        }

        // Perform IO operations in IO context
        val reader = withContext(Dispatchers.IO) {
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            
            if (provider.protocol != ModelProvider.GOOGLE) {
                conn.setRequestProperty("Authorization", "Bearer ${provider.apiKey}")
            }
            if (provider.protocol == ModelProvider.ANTHROPIC) {
                conn.setRequestProperty("x-api-key", provider.apiKey)
                conn.setRequestProperty("anthropic-version", "2023-06-01")
            }

            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            if (conn.responseCode == 200) {
                BufferedReader(InputStreamReader(conn.inputStream))
            } else {
                val error = conn.errorStream?.bufferedReader()?.readText() ?: "Unknown API Error"
                throw Exception("API Error ${conn.responseCode}: $error")
            }
        }

        try {
            val charBuffer = CharArray(8192)
            var currentBuffer = ""
            var readSize: Int
            
            while (withContext(Dispatchers.IO) { reader.read(charBuffer).also { readSize = it } } != -1) {
                currentBuffer += String(charBuffer, 0, readSize)
                               if (provider.protocol == ModelProvider.GOOGLE) {
                    // Google's stream is a JSON array: [ {...}, {...} ]
                    // We need to extract the individual {...} objects.
                    while (true) {
                        val start = currentBuffer.indexOf("{")
                        if (start == -1) break // Wait for more data
                        
                        var braceCount = 0
                        var end = -1
                        var inString = false
                        var escaped = false
                        
                        for (i in start until currentBuffer.length) {
                            val c = currentBuffer[i]
                            if (!inString) {
                                if (c == '{') braceCount++
                                else if (c == '}') braceCount--
                                
                                if (braceCount == 0) {
                                    end = i
                                    break
                                }
                                if (c == '"') inString = true
                            } else {
                                if (escaped) escaped = false
                                else if (c == '\\') escaped = true
                                else if (c == '"') inString = false
                            }
                        }
                        
                        if (end != -1) {
                            val jsonStr = currentBuffer.substring(start, end + 1)
                            currentBuffer = currentBuffer.substring(end + 1)
                            parseGoogleJson(jsonStr).forEach { emit(it) }
                        } else {
                            // Incomplete object, keep waiting
                            break
                        }
                    }
                } else {
                    // OpenAI/Anthropic/OpenRouter use SSE (data: {...}\n\n)
                    val lines = currentBuffer.split("\n")
                    // Keep the last partial line in the buffer
                    currentBuffer = lines.last()
                    for (i in 0 until lines.size - 1) {
                        val line = lines[i].trim()
                        if (line.isEmpty()) continue
                        val content = parseStreamingLine(line, provider.protocol)
                        if (content != null) emit(content)
                    }
                }
            }
        } finally {
            withContext(Dispatchers.IO) { reader.close() }
        }
    }

    private fun parseGoogleJson(jsonStr: String): List<StreamChunk> {
        return try {
            val json = JSONObject(jsonStr)
            val candidates = json.optJSONArray("candidates") ?: return emptyList()
            if (candidates.length() == 0) return emptyList()
            
            val content = candidates.getJSONObject(0).optJSONObject("content") ?: return emptyList()
            val parts = content.optJSONArray("parts") ?: return emptyList()
            if (parts.length() == 0) return emptyList()
            
            val visible = StringBuilder()
            val thinking = StringBuilder()
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (part.has("text")) {
                    if (part.optBoolean("thought", false)) {
                        thinking.append(part.getString("text"))
                    } else {
                        visible.append(part.getString("text"))
                    }
                }
            }
            buildList {
                if (thinking.isNotBlank()) add(StreamChunk(thinking.toString(), isThinking = true))
                if (visible.isNotBlank()) add(StreamChunk(visible.toString()))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseStreamingLine(line: String, provider: ModelProvider): StreamChunk? {
        val trimmed = line.trim()
        if (trimmed.isBlank()) return null
        
        return try {
            if (!trimmed.startsWith("data: ")) return null
            val data = trimmed.substring(6).trim()
            if (data == "[DONE]") return null
            val json = JSONObject(data)
            
            if (provider == ModelProvider.ANTHROPIC) {
                val type = json.optString("type")
                if (type == "content_block_delta") {
                    val delta = json.optJSONObject("delta")
                    when (delta?.optString("type")) {
                        "text_delta" -> StreamChunk(delta.optString("text"))
                        "thinking_delta" -> StreamChunk(delta.optString("thinking"), isThinking = true)
                        else -> null
                    }
                } else null
            } else { // OpenAI / OpenRouter
                val choices = json.optJSONArray("choices")
                if (choices == null || choices.length() == 0) return null
                val delta = choices.getJSONObject(0).optJSONObject("delta")
                if (delta == null) return null
                val reasoning = when {
                    delta.has("reasoning_content") -> delta.optString("reasoning_content")
                    delta.has("reasoning") -> delta.optString("reasoning")
                    delta.has("thinking") -> delta.optString("thinking")
                    else -> ""
                }
                when {
                    reasoning.isNotBlank() -> StreamChunk(reasoning, isThinking = true)
                    delta.has("content") -> StreamChunk(delta.getString("content"))
                    else -> null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchModels(provider: LlmProvider): List<AiModel> = withContext(Dispatchers.IO) {
        val baseUrl = provider.baseUrl.trimEnd('/')
        val urlStr = when (provider.protocol) {
             ModelProvider.OPENROUTER -> "$baseUrl/models"
             ModelProvider.OPENAI -> "$baseUrl/models"
             ModelProvider.GOOGLE -> "$baseUrl/models?key=${provider.apiKey}"
             else -> return@withContext emptyList()
        }
        
        try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            if (provider.protocol != ModelProvider.GOOGLE) {
                conn.setRequestProperty("Authorization", "Bearer ${provider.apiKey}")
            }
            
            if (conn.responseCode == 200) {
                 val resp = conn.inputStream.bufferedReader().readText()
                 val json = JSONObject(resp)
                 val list = mutableListOf<AiModel>()
                 
                 if (provider.protocol == ModelProvider.GOOGLE) {
                     val models = json.getJSONArray("models")
                     for (i in 0 until models.length()) {
                         val item = models.getJSONObject(i)
                         val id = item.getString("name").substringAfter("models/")
                         val displayName = item.optString("displayName", id)
                         val methods = item.getJSONArray("supportedGenerationMethods")
                         var supportsContent = false
                         for (j in 0 until methods.length()) {
                             if (methods.getString(j) == "generateContent") supportsContent = true
                         }
                         if (supportsContent) {
                             list.add(AiModel(id, displayName, provider.id))
                         }
                     }
                 } else {
                     val data = json.getJSONArray("data")
                     for (i in 0 until data.length()) {
                         val item = data.getJSONObject(i)
                         val id = item.getString("id")
                         val name = item.optString("name", id)
                         list.add(AiModel(id, name, provider.id))
                     }
                 }
                 list
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    private fun resolveAttachment(context: Context, attachment: Attachment): String? {
        return try {
            val uri = android.net.Uri.parse(attachment.uri)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bytes = inputStream.readBytes()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            null
        }
    }
}
