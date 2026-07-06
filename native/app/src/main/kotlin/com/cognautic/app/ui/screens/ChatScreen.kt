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

@OptIn(ExperimentalMaterial3Api::class)
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
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = workspace?.name ?: "Cognautic",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        if (workspace != null) {
                            Text(
                                text = "Coding agent",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onBackToWorkspaces() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (workspace != null) {
                        IconButton(onClick = { viewModel.onOpenInEditor() }) {
                            Icon(Icons.Default.Code, contentDescription = "Open in editor")
                        }
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    ModelSelector(
                        models = availableModels,
                        providers = providers,
                        selectedModel = selectedModel,
                        onModelSelected = { viewModel.onModelSelected(it) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
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
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (!isEmptyState || isLoading) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                contentAlignment = Alignment.Center
            ) {
                 Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.widthIn(max = 600.dp)
                ) {
                    AnimatedVisibility(visible = isEmptyState, enter = fadeIn(), exit = fadeOut()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                tonalElevation = 2.dp
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.logo),
                                    contentDescription = "Logo",
                                    modifier = Modifier
                                        .size(88.dp)
                                        .padding(12.dp)
                                        .clip(RoundedCornerShape(18.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                "Cognautic",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Ask for a coding task in this workspace",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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
        AssistChip(
            onClick = { expanded = true },
            label = {
                Text(
                    text = selectedModel?.name ?: "Select model",
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select model")
            },
            modifier = Modifier.fillMaxWidth()
        )

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
            
            HorizontalDivider()

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
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
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
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                    shape = RoundedCornerShape(28.dp)
            ),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(onClick = onUploadClick) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Upload",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                TextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp, max = 160.dp),
                    placeholder = { Text("Message Cognautic") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    maxLines = 8
                )
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
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Send")
                    }
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
            shape = RoundedCornerShape(
                topStart = 22.dp,
                topEnd = 22.dp,
                bottomStart = if (isUser) 22.dp else 6.dp,
                bottomEnd = if (isUser) 6.dp else 22.dp
            ),
            color = when {
                isUser -> MaterialTheme.colorScheme.primary
                isError -> MaterialTheme.colorScheme.errorContainer
                isToolCall -> MaterialTheme.colorScheme.secondaryContainer
                isThinking -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            },
            tonalElevation = if (isUser) 0.dp else 1.dp,
            modifier = Modifier.widthIn(max = 520.dp)
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                        )
                    }
                    AnimatedVisibility(visible = isThinkingExpanded) {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
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
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
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
