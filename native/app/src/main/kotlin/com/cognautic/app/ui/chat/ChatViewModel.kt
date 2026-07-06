package com.cognautic.app.ui.chat

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cognautic.app.core.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import com.cognautic.app.core.network.TelemetryClient

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("cognautic_prefs", Context.MODE_PRIVATE)

    // --- Workspace State ---
    private val _workspaces = MutableStateFlow<List<Workspace>>(emptyList())
    val workspaces: StateFlow<List<Workspace>> = _workspaces.asStateFlow()

    private val _currentWorkspace = MutableStateFlow<Workspace?>(null)
    val currentWorkspace: StateFlow<Workspace?> = _currentWorkspace.asStateFlow()

    // --- Chat State ---
    private val _isAutoApprove = MutableStateFlow(false)
    val isAutoApprove: StateFlow<Boolean> = _isAutoApprove.asStateFlow()

    private val _showThinking = MutableStateFlow(false)
    val showThinking: StateFlow<Boolean> = _showThinking.asStateFlow()

    private val _temperature = MutableStateFlow(0.2f)
    val temperature: StateFlow<Float> = _temperature.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _availableModels = MutableStateFlow<List<AiModel>>(emptyList())
    val availableModels: StateFlow<List<AiModel>> = _availableModels.asStateFlow()

    private val _selectedModel = MutableStateFlow<AiModel?>(null)
    val selectedModel: StateFlow<AiModel?> = _selectedModel.asStateFlow()
    
    private val _pendingAttachments = MutableStateFlow<List<Attachment>>(emptyList())
    val pendingAttachments: StateFlow<List<Attachment>> = _pendingAttachments.asStateFlow()
    
    // Per-workspace background jobs
    private val chatJobs = mutableMapOf<String, kotlinx.coroutines.Job>()
    
    // Tracks which workspaces have active AI tasks
    private val _activeWorkspaces = MutableStateFlow<Set<String>>(emptySet())
    val activeWorkspaces: StateFlow<Set<String>> = _activeWorkspaces.asStateFlow()

    // --- Provider State ---
    private val _providers = MutableStateFlow<List<LlmProvider>>(emptyList())
    val providers: StateFlow<List<LlmProvider>> = _providers.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // --- Editor State ---
    private val _showEditor = MutableStateFlow(false)
    val showEditor: StateFlow<Boolean> = _showEditor.asStateFlow()

    private val _editingFile = MutableStateFlow<String?>(null) // Relative path
    val editingFile: StateFlow<String?> = _editingFile.asStateFlow()

    private val _fileContent = MutableStateFlow("")
    val fileContent: StateFlow<String> = _fileContent.asStateFlow()

    private val _files = MutableStateFlow<List<Pair<String, Boolean>>>(emptyList()) // Path, isDirectory
    val files: StateFlow<List<Pair<String, Boolean>>> = _files.asStateFlow()

    private val _currentDir = MutableStateFlow(".")
    val currentDir: StateFlow<String> = _currentDir.asStateFlow()

    private val _globalRules = MutableStateFlow("")
    val globalRules: StateFlow<String> = _globalRules.asStateFlow()

    private val _errorEvent = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.BUFFERED)
    val errorEvent = _errorEvent.receiveAsFlow()
    
    // Sliding Notifications
    val notifications = MutableStateFlow<List<String>>(emptyList())
    
    private fun showNotification(message: String) {
        viewModelScope.launch {
            notifications.value = notifications.value + message
            kotlinx.coroutines.delay(5000)
            notifications.value = notifications.value.filter { it != message }
        }
    }
    
    // Approval State
    data class PendingTool(val id: String, val name: String, val args: Map<String, String>)
    private val _pendingTool = MutableStateFlow<PendingTool?>(null)
    val pendingTool: StateFlow<PendingTool?> = _pendingTool.asStateFlow()
    
    private var approvalContinuation: kotlinx.coroutines.CompletableDeferred<Boolean>? = null

    init {
        _isAutoApprove.value = prefs.getBoolean("isAutoApprove", false)
        _showThinking.value = prefs.getBoolean("showThinking", false)
        _temperature.value = prefs.getFloat("temperature", 0.2f).coerceIn(0f, 2f)
        _globalRules.value = prefs.getString("global_rules", "") ?: ""
        loadProviders()
        loadWorkspaces()
    }

    fun onAutoApproveChange(value: Boolean) {
        _isAutoApprove.value = value
        prefs.edit().putBoolean("isAutoApprove", value).apply()
    }

    fun onShowThinkingChange(value: Boolean) {
        _showThinking.value = value
        prefs.edit().putBoolean("showThinking", value).apply()
    }

    fun onTemperatureChange(value: Float) {
        val safeValue = value.coerceIn(0f, 2f)
        _temperature.value = safeValue
        prefs.edit().putFloat("temperature", safeValue).apply()
    }

    fun onGlobalRulesChange(value: String) {
        _globalRules.value = value
        prefs.edit().putString("global_rules", value).apply()
    }

    // --- Actions ---

    fun onWorkspaceSelected(workspace: Workspace) {
        _currentWorkspace.value = workspace
        loadChatHistory(workspace.id)
    }

    fun onOpenInEditor() {
        _showEditor.value = true
        loadFileList(".")
    }

    fun onCloseEditor() {
        _showEditor.value = false
        clearEditingFile()
    }

    fun clearEditingFile() {
        _editingFile.value = null
        _fileContent.value = ""
    }

    fun loadFileList(relPath: String) {
        val root = _currentWorkspace.value?.path ?: return
        _currentDir.value = relPath
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val tool = com.cognautic.app.core.tools.ListFilesTool(getApplication(), root)
            val result = tool.execute(mapOf("path" to relPath))
            if (result.startsWith("Error")) {
                showNotification(result)
            } else if (result == "Empty directory") {
                _files.value = emptyList()
            } else {
                val lines = result.split("\n")
                val list = lines.filter { it.isNotBlank() && it.contains(" ") }.map { line ->
                    val isDir = line.startsWith("[DIR]")
                    val name = line.substringAfter(" ").trim()
                    val fullRelPath = if (relPath == "." || relPath.isEmpty()) name else "$relPath/$name"
                    fullRelPath to isDir
                }
                _files.value = list
            }
        }
    }

    fun onFileSelected(relPath: String) {
        val root = _currentWorkspace.value?.path ?: return
        _editingFile.value = relPath
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val tool = com.cognautic.app.core.tools.ReadFileTool(getApplication(), root)
            val result = tool.execute(mapOf("path" to relPath))
            if (result.startsWith("Error")) {
                showNotification(result)
            } else {
                _fileContent.value = result
            }
        }
    }

    fun saveFile(content: String) {
        val root = _currentWorkspace.value?.path ?: return
        val path = _editingFile.value ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val tool = com.cognautic.app.core.tools.WriteFileTool(getApplication(), root)
            val result = tool.execute(mapOf("path" to path, "content" to content))
            if (result.startsWith("Error")) {
                showNotification(result)
            } else {
                showNotification("File saved: $path")
                _fileContent.value = content
            }
        }
    }
    
    fun onBackToWorkspaces() {
        _currentWorkspace.value = null
        // Don't clear messages here, they are workspace specific and loaded on select
    }

    fun onCreateWorkspace(path: String) {
        val decodedPath = android.net.Uri.decode(path)
        var finalPath = path
        
        // Try to resolve SAF to local path for easier terminal/editor access
        if (path.startsWith("content://")) {
            val pathPart = decodedPath.substringAfterLast(":")
            val possiblePaths = listOf(
                pathPart,
                if (pathPart.startsWith("/")) pathPart else "/$pathPart",
                "/storage/emulated/0/$pathPart",
                "/sdcard/$pathPart"
            )
            val localPath = possiblePaths.find { File(it).exists() }
            if (localPath != null) {
                finalPath = localPath
            }
        }

        val name = decodedPath.trimEnd('/').substringAfterLast("/").substringAfterLast(":")
        val newWorkspace = Workspace(UUID.randomUUID().toString(), name, finalPath)
        
        val updated = _workspaces.value + newWorkspace
        _workspaces.value = updated
        saveWorkspaces(updated)
        TelemetryClient.trackEvent("workspace_created")
    }

    fun onRenameWorkspace(workspaceId: String, newName: String) {
        val updated = _workspaces.value.map {
            if (it.id == workspaceId) it.copy(name = newName) else it
        }
        _workspaces.value = updated
        saveWorkspaces(updated)
        
        if (_currentWorkspace.value?.id == workspaceId) {
            _currentWorkspace.value = _currentWorkspace.value?.copy(name = newName)
        }
    }

    fun onDeleteWorkspace(workspaceId: String) {
        val updated = _workspaces.value.filter { it.id != workspaceId }
        _workspaces.value = updated
        saveWorkspaces(updated)
        
        // Remove history
        prefs.edit().remove("history_$workspaceId").apply()
        
        if (_currentWorkspace.value?.id == workspaceId) {
            _currentWorkspace.value = null
        }
    }

    fun onWorkspaceRulesChange(workspaceId: String, rules: String) {
        val updated = _workspaces.value.map {
            if (it.id == workspaceId) it.copy(rules = rules) else it
        }
        _workspaces.value = updated
        saveWorkspaces(updated)
        
        if (_currentWorkspace.value?.id == workspaceId) {
            _currentWorkspace.value = _currentWorkspace.value?.copy(rules = rules)
        }
    }

    // --- Provider Actions ---

    fun onAddProvider(name: String, baseUrl: String, apiKey: String, protocol: ModelProvider) {
        val provider = LlmProvider(UUID.randomUUID().toString(), name, baseUrl, apiKey, protocol, false)
        val updated = _providers.value + provider
        _providers.value = updated
        saveProviders(updated)
        fetchModels(provider)
    }

    fun onUpdateProvider(id: String, name: String, baseUrl: String, apiKey: String, protocol: ModelProvider) {
        val updated = _providers.value.map {
            if (it.id == id) it.copy(name = name, baseUrl = baseUrl, apiKey = apiKey, protocol = protocol) else it
        }
        _providers.value = updated
        saveProviders(updated)
        val provider = updated.find { it.id == id }
        if (provider != null) fetchModels(provider)
    }

    fun onDeleteProvider(id: String) {
        val updated = _providers.value.filter { it.id != id }
        _providers.value = updated
        saveProviders(updated)
        // Clear models
        _availableModels.value = _availableModels.value.filter { it.providerId != id }
        if (_selectedModel.value?.providerId == id) {
            _selectedModel.value = _availableModels.value.firstOrNull()
        }
    }

    // Support legacy API key changes for built-ins
    fun onApiKeyChange(providerType: ModelProvider, key: String) {
        val updated = _providers.value.map {
            if (it.isBuiltIn && it.protocol == providerType) it.copy(apiKey = key) else it
        }
        _providers.value = updated
        saveProviders(updated)
        val provider = updated.find { it.isBuiltIn && it.protocol == providerType }
        if (provider != null) fetchModels(provider)
    }

    fun onInputChange(newValue: String) {
        _input.value = newValue
    }

    fun onModelSelected(model: AiModel) {
        _selectedModel.value = model
    }

    fun onAddAttachment(attachment: Attachment) {
        _pendingAttachments.value = _pendingAttachments.value + attachment
    }

    fun onRemoveAttachment(attachmentId: String) {
        _pendingAttachments.value = _pendingAttachments.value.filter { it.id != attachmentId }
    }

    fun onToolApproval(approved: Boolean) {
        val deferred = approvalContinuation
        if (deferred != null && deferred.isActive) {
            deferred.complete(approved)
        }
        _pendingTool.value = null
        approvalContinuation = null
    }

    fun stopResponse(workspaceId: String? = null) {
        val targetId = workspaceId ?: _currentWorkspace.value?.id ?: return
        chatJobs[targetId]?.cancel()
        chatJobs.remove(targetId)
        _activeWorkspaces.value = _activeWorkspaces.value - targetId
        if (_currentWorkspace.value?.id == targetId) {
            _isLoading.value = false
            _pendingTool.value = null
            approvalContinuation?.cancel()
            approvalContinuation = null
        }
    }

    private fun fetchModels(provider: LlmProvider) {
        if (provider.apiKey.isBlank()) {
            _availableModels.value = _availableModels.value.filter { it.providerId != provider.id }
            if (_selectedModel.value?.providerId == provider.id) {
                _selectedModel.value = _availableModels.value.firstOrNull()
            }
            return
        }
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val fetched = com.cognautic.app.core.network.LlmClient.fetchModels(provider)
            if (fetched.isNotEmpty()) {
                val current = _availableModels.value.filter { it.providerId != provider.id }
                val updated = (current + fetched).sortedBy { it.name }
                _availableModels.value = updated
                
                if (_selectedModel.value == null) {
                    _selectedModel.value = updated.firstOrNull()
                }
            }
        }
    }

    fun sendMessage() {
        val currentWs = _currentWorkspace.value ?: return

        if (chatJobs.containsKey(currentWs.id)) {
            stopResponse(currentWs.id)
            return
        }

        val currentInput = _input.value
        val attachments = _pendingAttachments.value
        if (currentInput.isBlank() && attachments.isEmpty()) return

        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = Role.USER,
            content = currentInput,
            attachments = attachments
        )
        
        // Optimistic update
        val newHistory = _messages.value + userMsg
        _messages.value = newHistory
        saveChatHistory(currentWs.id, newHistory)
        
        _input.value = ""
        _pendingAttachments.value = emptyList()

        val model = _selectedModel.value
        if (model == null) {
             showNotification("No model selected.")
             return
        }
        val provider = _providers.value.find { it.id == model.providerId }
        if (provider == null || provider.apiKey.isBlank()) {
             showNotification("API Key missing for provider.")
             return
        }

        TelemetryClient.trackEvent(
            event = "message_sent",
            meta = mapOf(
                "model" to model.name,
                "provider" to provider.name,
                "attachments_count" to attachments.size
            )
        )

        _isLoading.value = true
        _activeWorkspaces.value = _activeWorkspaces.value + currentWs.id
        val autoApprove = _isAutoApprove.value
        val agentConfig = AgentConfig(
            showThinking = _showThinking.value,
            temperature = _temperature.value
        )
        val gRules = _globalRules.value
        val wsRules = currentWs.rules
        
        val job = viewModelScope.launch {
            try {
                val runner = com.cognautic.app.core.agents.AgentRunner(getApplication(), currentWs.path)
                
                runner.runAgentTask(
                    task = currentInput, 
                    model = model, 
                    provider = provider, 
                    history = getChatHistory(currentWs.id),
                    config = agentConfig,
                    globalRules = gRules,
                    workspaceRules = wsRules
                ) { msgId, name, args ->
                    TelemetryClient.trackEvent(
                        event = "tool_called",
                        meta = mapOf("tool" to name)
                    )
                    if (autoApprove) return@runAgentTask true

                    // Approval Callback
                    val deferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
                    approvalContinuation = deferred
                    _pendingTool.value = PendingTool(msgId, name, args)
                    // Wait for UI to complete deferred
                    deferred.await()
                }
                .collect { message ->
                    if (message.role == Role.ERROR) {
                        showNotification(message.content)
                        if (_currentWorkspace.value?.id == currentWs.id) {
                            val existingIdx = _messages.value.indexOfFirst { it.id == message.id }
                            _messages.value = if (existingIdx != -1) {
                                _messages.value.toMutableList().apply { set(existingIdx, message) }
                            } else {
                                _messages.value + message
                            }
                        }
                        return@collect
                    }

                    // If this is the current workspace, update live UI
                    if (_currentWorkspace.value?.id == currentWs.id) {
                        val existingIdx = _messages.value.indexOfFirst { it.id == message.id }
                        val updated = if (existingIdx != -1) {
                             _messages.value.toMutableList().apply { set(existingIdx, message) }
                        } else {
                             _messages.value + message
                        }
                        _messages.value = updated
                    }
                    
                    // Always save history for background persistence
                    val history = getChatHistory(currentWs.id)
                    val existingIdx = history.indexOfFirst { it.id == message.id }
                    val updatedHistory = if (existingIdx != -1) {
                        history.toMutableList().apply { set(existingIdx, message) }
                    } else {
                        history + message
                    }
                    saveChatHistory(currentWs.id, updatedHistory)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Expected on stop
            } catch (e: Exception) {
                 showNotification("Agent Error: ${e.message}")
                 TelemetryClient.trackEvent(
                     event = "agent_error",
                     meta = mapOf("error" to (e.message ?: "unknown"))
                 )
            } finally {
                chatJobs.remove(currentWs.id)
                _activeWorkspaces.value = _activeWorkspaces.value - currentWs.id
                if (_currentWorkspace.value?.id == currentWs.id) {
                    _isLoading.value = false
                    _pendingTool.value = null
                }
            }
        }
        chatJobs[currentWs.id] = job
    }

    private fun getChatHistory(workspaceId: String): List<ChatMessage> {
        // If it's current workspace, results are in _messages.value
        if (_currentWorkspace.value?.id == workspaceId) return _messages.value
        
        // Otherwise load from preferences (memory cache would be better but this works for now)
        val jsonStr = prefs.getString("history_$workspaceId", "[]") ?: "[]"
        return parseChatHistory(jsonStr)
    }

    // --- Persistence Logic ---

    private fun loadWorkspaces() {
        val jsonStr = prefs.getString("workspaces", "[]") ?: "[]"
        try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<Workspace>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(Workspace(
                    id = obj.getString("id"), 
                    name = obj.getString("name"), 
                    path = obj.getString("path"),
                    rules = obj.optString("rules", "")
                ))
            }
            _workspaces.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveWorkspaces(list: List<Workspace>) {
        val jsonArray = JSONArray()
        list.forEach { 
            jsonArray.put(JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("path", it.path)
                put("rules", it.rules)
            })
        }
        prefs.edit().putString("workspaces", jsonArray.toString()).apply()
    }

    private fun loadProviders() {
        val jsonStr = prefs.getString("providers", null)
        val builtIns = listOf(
            LlmProvider("builtin_openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "", ModelProvider.OPENROUTER, true),
            LlmProvider("builtin_openai", "OpenAI", "https://api.openai.com/v1", "", ModelProvider.OPENAI, true),
            LlmProvider("builtin_google", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta", "", ModelProvider.GOOGLE, true),
            LlmProvider("builtin_anthropic", "Anthropic", "https://api.anthropic.com/v1", "", ModelProvider.ANTHROPIC, true)
        )

        if (jsonStr == null) {
            // First run, migrate old keys if any
            val migrated = builtIns.map { p ->
                val oldKey = prefs.getString("apikey_${p.protocol.name}", "") ?: ""
                p.copy(apiKey = oldKey)
            }
            _providers.value = migrated
            saveProviders(migrated)
            migrated.forEach { fetchModels(it) }
        } else {
            try {
                val jsonArray = JSONArray(jsonStr)
                val list = mutableListOf<LlmProvider>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(LlmProvider(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        baseUrl = obj.getString("baseUrl"),
                        apiKey = obj.getString("apiKey"),
                        protocol = ModelProvider.valueOf(obj.getString("protocol")),
                        isBuiltIn = obj.optBoolean("isBuiltIn", false)
                    ))
                }
                
                // Ensure all built-ins exist
                val merged = list.toMutableList()
                builtIns.forEach { bi ->
                    if (merged.none { it.id == bi.id }) merged.add(bi)
                }
                _providers.value = merged
                merged.forEach { fetchModels(it) }
            } catch (e: Exception) {
                _providers.value = builtIns
            }
        }
    }

    private fun saveProviders(list: List<LlmProvider>) {
        val jsonArray = JSONArray()
        list.forEach { 
            jsonArray.put(JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("baseUrl", it.baseUrl)
                put("apiKey", it.apiKey)
                put("protocol", it.protocol.name)
                put("isBuiltIn", it.isBuiltIn)
            })
        }
        prefs.edit().putString("providers", jsonArray.toString()).apply()
    }

    private fun loadApiKeys() {
        // Obsolete, handled by providers now
    }

    private fun saveApiKeys(map: Map<ModelProvider, String>) {
        // Obsolete, handled by providers now
    }

    private fun loadChatHistory(workspaceId: String) {
        val jsonStr = prefs.getString("history_$workspaceId", "[]") ?: "[]"
        _messages.value = parseChatHistory(jsonStr)
    }

    private fun parseChatHistory(jsonStr: String): List<ChatMessage> {
        try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<ChatMessage>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val roleStr = obj.optString("role", "SYSTEM")
                val role = try { Role.valueOf(roleStr) } catch(e:Exception) { Role.SYSTEM }
                if (role == Role.ERROR) continue
                
                val toolMap: Map<String, String>? = if (obj.has("toolData")) {
                     val tData = obj.getJSONObject("toolData")
                     val m = mutableMapOf<String,String>()
                     tData.keys().forEach { k -> m[k] = tData.getString(k) }
                     m
                } else null

                val statusStr = if (obj.has("toolStatus")) obj.getString("toolStatus") else null
                val status = if (statusStr != null) {
                    try { ToolStatus.valueOf(statusStr) } catch(e:Exception) { null }
                } else null

                val attachments = mutableListOf<Attachment>()
                if (obj.has("attachments")) {
                    val attArray = obj.getJSONArray("attachments")
                    for (j in 0 until attArray.length()) {
                        val attObj = attArray.getJSONObject(j)
                        attachments.add(Attachment(
                            attObj.getString("id"),
                            attObj.getString("name"),
                            attObj.getString("mimeType"),
                            attObj.getString("uri"),
                            attObj.getLong("size")
                        ))
                    }
                }

                list.add(ChatMessage(
                    id = obj.getString("id"),
                    role = role,
                    content = obj.getString("content"),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    toolData = toolMap,
                    toolStatus = status,
                    isVisible = obj.optBoolean("isVisible", true),
                    attachments = attachments
                ))
            }
            return list
        } catch (e: Exception) {
            e.printStackTrace()
             return emptyList()
        }
    }

    private fun saveChatHistory(workspaceId: String, messages: List<ChatMessage>) {
        val jsonArray = JSONArray()
        messages.filter { it.role != Role.ERROR }.forEach { msg ->
            val obj = JSONObject().apply {
                put("id", msg.id)
                put("role", msg.role.name)
                put("content", msg.content)
                put("timestamp", msg.timestamp)
                if (msg.toolData != null) {
                    put("toolData", JSONObject(msg.toolData))
                }
                if (msg.toolStatus != null) {
                    put("toolStatus", msg.toolStatus.name)
                }
                put("isVisible", msg.isVisible)
                if (msg.attachments.isNotEmpty()) {
                    val attArray = JSONArray()
                    msg.attachments.forEach { att ->
                        attArray.put(JSONObject().apply {
                            put("id", att.id)
                            put("name", att.name)
                            put("mimeType", att.mimeType)
                            put("uri", att.uri)
                            put("size", att.size)
                        })
                    }
                    put("attachments", attArray)
                }
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString("history_$workspaceId", jsonArray.toString()).apply()
    }
}
