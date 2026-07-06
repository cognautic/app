package com.cognautic.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import android.provider.OpenableColumns
import java.util.UUID
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.cognautic.app.R
import com.cognautic.app.core.models.*
import com.cognautic.app.ui.chat.ChatViewModel

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onSettingsClick: () -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val input by viewModel.input.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val providers by viewModel.providers.collectAsState()
    val workspace by viewModel.currentWorkspace.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val pendingTool by viewModel.pendingTool.collectAsState()
    val pendingAttachments by viewModel.pendingAttachments.collectAsState()
    val showThinking by viewModel.showThinking.collectAsState()

    val context = LocalContext.current
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { uri ->
            val attachment = getAttachmentFromUri(context, uri)
            if (attachment != null) {
                viewModel.onAddAttachment(attachment)
            }
        }
    }

    val isEmptyState = messages.isEmpty()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    BackHandler {
        viewModel.onBackToWorkspaces()
    }

    LaunchedEffect(Unit) {
        viewModel.errorEvent.collect { error ->
            snackbarHostState.showSnackbar(error, duration = SnackbarDuration.Short)
        }
    }

    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty() || isLoading) {
            val count = messages.size + if (isLoading) 1 else 0
            if (count > 0) listState.animateScrollToItem(count - 1)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (workspace != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.onBackToWorkspaces() }) {
                             Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                           text = workspace?.name ?: "Cognautic",
                           style = MaterialTheme.typography.titleMedium,
                           color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                   Row {
                       IconButton(onClick = { viewModel.onOpenInEditor() }) {
                           Icon(
                               imageVector = Icons.Default.Code,
                               contentDescription = "Open in Editor",
                               tint = MaterialTheme.colorScheme.onBackground
                           )
                       }
                       IconButton(onClick = onSettingsClick) {
                           Icon(
                               imageVector = Icons.Default.Settings,
                               contentDescription = "Settings",
                               tint = MaterialTheme.colorScheme.onBackground
                           )
                       }
                   }
                }
            } else {
                 Box(Modifier.fillMaxWidth().padding(8.dp)) {
                      IconButton(onClick = { viewModel.onBackToWorkspaces() }, modifier = Modifier.align(Alignment.TopStart)) {
                           Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                      }
                      IconButton(onClick = onSettingsClick, modifier = Modifier.align(Alignment.TopEnd)) {
                       Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onBackground)
                   }
                 }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!isEmptyState || isLoading) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 180.dp), 
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(messages) { index, message ->
                        if (message.isVisible && (message.role != Role.THINKING || showThinking)) {
                            val previousVisibleMessage = messages
                                .take(index)
                                .lastOrNull { it.isVisible && it.role != Role.THINKING }
                            val showLabel = previousVisibleMessage == null || previousVisibleMessage.role == Role.USER
                            MessageBubble(
                                message = message,
                                showLabel = showLabel,
                                isPending = pendingTool?.id == message.id,
                                onApproval = { viewModel.onToolApproval(it) }
                            )
                        }
                    }
                    if (isLoading) {
                        item {
                            Text(
                                "Working...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 12.dp, top = 4.dp)
                            )
                        }
                    }
                }
            }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = if (isEmptyState) Alignment.Center else Alignment.BottomCenter
            ) {
                 Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.widthIn(max = 600.dp)
                ) {
                    AnimatedVisibility(visible = isEmptyState, enter = fadeIn(), exit = fadeOut()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                painter = painterResource(id = R.drawable.logo),
                                contentDescription = "Logo",
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                "COGNAUTIC", 
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 28.sp, 
                                    letterSpacing = 4.sp, 
                                    fontWeight = FontWeight.Light
                                ), 
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(48.dp))
                        }
                    }
                    Box(Modifier.fillMaxWidth()) {
                        ModelSelector(
                            models = availableModels, 
                            providers = providers,
                            selectedModel = selectedModel, 
                            onModelSelected = { viewModel.onModelSelected(it) }
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    ChatInput(
                        value = input, 
                        onValueChange = { viewModel.onInputChange(it) }, 
                        onAction = { viewModel.sendMessage() },
                        onUploadClick = { filePickerLauncher.launch("*/*") },
                        isLoading = isLoading,
                        attachments = pendingAttachments,
                        onRemoveAttachment = { viewModel.onRemoveAttachment(it) }
                    )
                }
            }
        }
    }
}

@Composable
fun ModelSelector(
    models: List<AiModel>,
    providers: List<LlmProvider>,
    selectedModel: AiModel?,
    onModelSelected: (AiModel) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredModels = remember(models, searchQuery, providers) {
        models.filter { 
            searchQuery.isBlank() || 
            it.name.contains(searchQuery, ignoreCase = true) || 
            it.id.contains(searchQuery, ignoreCase = true)
        }.groupBy { 
            providers.find { p -> p.id == it.providerId }?.name ?: "Unknown"
        }
    }

    Box {
        Surface(
            modifier = Modifier
                .clickable { expanded = true }
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
            color = Color.Transparent
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedModel?.name ?: "Select Model",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = "Select Model",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .width(300.dp)
                .heightIn(max = 400.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search models...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                singleLine = true
            )
            
            Divider()

            if (models.isEmpty()) {
                 DropdownMenuItem(
                    text = { Text("No models. Add API Key in Settings.") },
                    onClick = { expanded = false }
                )
            } else {
                filteredModels.forEach { (providerName, providerModels) ->
                    DropdownMenuItem(
                        text = { 
                            Text(
                                text = providerName, 
                                style = MaterialTheme.typography.labelSmall, 
                                color = MaterialTheme.colorScheme.primary
                            ) 
                        },
                        onClick = { },
                        enabled = false
                    )
                    
                    providerModels.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model.name, style = MaterialTheme.typography.bodyMedium) },
                            onClick = {
                                onModelSelected(model)
                                expanded = false
                                searchQuery = ""
                            }
                        )
                    }
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                }
            }
        }
    }
}

@Composable
fun ChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    onAction: () -> Unit,
    onUploadClick: () -> Unit,
    isLoading: Boolean,
    attachments: List<Attachment> = emptyList(),
    onRemoveAttachment: (String) -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (attachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                attachments.forEach { attachment ->
                    AttachmentThumbnail(attachment, onRemove = { onRemoveAttachment(attachment.id) })
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp),
            placeholder = { Text("Ask anything...") },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            maxLines = 15
        )
        
        // Action Button (Bottom Right)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
        ) {
            FilledIconButton(
                onClick = onAction,
                enabled = value.isNotBlank() || attachments.isNotEmpty() || isLoading,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isLoading) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                if (isLoading) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop")
                } else {
                    Icon(Icons.Default.ArrowForward, contentDescription = "Send")
                }
            }
        }

        // Upload Button (Bottom Left)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        ) {
            IconButton(onClick = onUploadClick) {
                Icon(
                    Icons.Default.Add, 
                    contentDescription = "Upload",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
    }
}

@Composable
fun MessageBubble(
    message: ChatMessage,
    showLabel: Boolean = true,
    isPending: Boolean = false,
    onApproval: (Boolean) -> Unit = {}
) {
    val isUser = message.role == Role.USER
    val isError = message.role == Role.ERROR
    val isToolCall = message.role == Role.TOOL_CALL
    val isThinking = message.role == Role.THINKING
    var isThinkingExpanded by remember(message.id) { mutableStateOf(false) }
    
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = when {
                isUser -> MaterialTheme.colorScheme.primary
                isError -> MaterialTheme.colorScheme.errorContainer
                isToolCall -> MaterialTheme.colorScheme.secondaryContainer
                isThinking -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                else -> Color.Transparent
            },
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (isThinking) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isThinkingExpanded = !isThinkingExpanded },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isThinkingExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isThinkingExpanded) "Collapse thinking" else "Expand thinking",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Thinking",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                    }
                    AnimatedVisibility(visible = isThinkingExpanded) {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                } else {
                    if (!isUser && !isError && showLabel) {
                        Text(
                            text = "AI",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isToolCall && message.toolStatus != null) {
                             val color = when (message.toolStatus) {
                                 ToolStatus.PENDING -> Color(0xFF8A8A8A)
                                 ToolStatus.EXECUTING -> MaterialTheme.colorScheme.primary
                                 ToolStatus.DONE -> Color(0xFFCFCFCF)
                                 ToolStatus.FAILED -> Color(0xFF4A4A4A)
                             }
                             Box(
                                 modifier = Modifier
                                     .size(8.dp)
                                     .background(color, androidx.compose.foundation.shape.CircleShape)
                             )
                             Spacer(modifier = Modifier.width(8.dp))
                        }
                        Column {
                            if (message.attachments.isNotEmpty()) {
                                 Row(
                                    modifier = Modifier
                                        .padding(bottom = 8.dp)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    message.attachments.forEach { att ->
                                        AttachmentBubble(att)
                                    }
                                }
                            }
                            Text(
                                text = message.content,
                                style = MaterialTheme.typography.bodyLarge,
                                color = when {
                                    isUser -> MaterialTheme.colorScheme.onPrimary
                                    isError -> MaterialTheme.colorScheme.onErrorContainer
                                    isToolCall -> MaterialTheme.colorScheme.onSecondaryContainer
                                    else -> MaterialTheme.colorScheme.onBackground
                                }
                            )
                        }
                    }
                    
                    if (isToolCall && message.toolData != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            message.toolData.forEach { (k, v) ->
                                if (k != "name" && k != "content" && k != "old_content" && k != "new_content") {
                                    Text("$k: $v", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }

                        if (isPending) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { onApproval(false) }) {
                                    Text("Reject", color = MaterialTheme.colorScheme.error)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = { onApproval(true) }) {
                                    Text("Approve")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun AttachmentThumbnail(attachment: Attachment, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(60.dp)
    ) {
        Box {
             Column(
                 modifier = Modifier.fillMaxSize().padding(4.dp),
                 horizontalAlignment = Alignment.CenterHorizontally,
                 verticalArrangement = Arrangement.Center
             ) {
                 Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(24.dp))
                 Text(
                     text = attachment.name,
                     style = MaterialTheme.typography.labelSmall,
                     maxLines = 1,
                     overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                 )
             }
             IconButton(
                 onClick = onRemove,
                 modifier = Modifier.size(20.dp).align(Alignment.TopEnd).padding(2.dp)
             ) {
                 Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
             }
        }
    }
}

@Composable
fun AttachmentBubble(attachment: Attachment) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(attachment.name, style = MaterialTheme.typography.labelSmall)
        }
    }
}

fun getAttachmentFromUri(context: android.content.Context, uri: android.net.Uri): Attachment? {
    val contentResolver = context.contentResolver
    val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
    var name = "unknown"
    var size = 0L

    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
            name = if (nameIndex != -1) cursor.getString(nameIndex) else "file"
            size = if (sizeIndex != -1) cursor.getLong(sizeIndex) else 0L
        }
    }

    return Attachment(
        id = UUID.randomUUID().toString(),
        name = name,
        mimeType = mimeType,
        uri = uri.toString(),
        size = size
    )
}
