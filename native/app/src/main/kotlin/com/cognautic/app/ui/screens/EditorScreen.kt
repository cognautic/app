package com.cognautic.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cognautic.app.ui.chat.ChatViewModel
import com.cognautic.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: ChatViewModel,
    onClose: () -> Unit
) {
    val files by viewModel.files.collectAsState()
    val currentDir by viewModel.currentDir.collectAsState()
    val editingFile by viewModel.editingFile.collectAsState()
    val fileContent by viewModel.fileContent.collectAsState()
    
    var editedContent by remember(fileContent) { mutableStateOf(fileContent) }

    BackHandler {
        if (editingFile != null) {
            viewModel.clearEditingFile()
        } else if (currentDir != ".") {
            val parent = currentDir.substringBeforeLast("/", ".")
            viewModel.loadFileList(parent)
        } else {
            onClose()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = editingFile?.uppercase() ?: "EDITOR",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { 
                        if (editingFile != null) {
                            viewModel.clearEditingFile()
                        } else if (currentDir != ".") {
                            val parent = currentDir.substringBeforeLast("/", ".")
                            viewModel.loadFileList(parent)
                        } else {
                            onClose()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    if (editingFile != null) {
                        IconButton(onClick = { viewModel.saveFile(editedContent) }) {
                            Icon(Icons.Default.Save, contentDescription = "Save", tint = TextPrimary)
                        }
                    } else {
                        IconButton(onClick = { viewModel.loadFileList(currentDir) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary,
                    actionIconContentColor = TextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Border)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (editingFile == null) {
                // File List
                Text(
                    text = "[DIR] $currentDir",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentPrimary
                )
                Box(modifier = Modifier
                    .fillMaxSize()
                    .border(1.dp, Border)
                    .padding(8.dp)
                    .background(SurfaceVariant)
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (files.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillParentMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "[ EMPTY DIRECTORY ]",
                                        color = TextMuted,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                        items(files) { (path, isDir) ->
                            FileItem(
                                name = path.substringAfterLast("/"),
                                isDir = isDir,
                                onClick = {
                                    if (isDir) {
                                        viewModel.loadFileList(path)
                                    } else {
                                        viewModel.onFileSelected(path)
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                // Text Editor
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.dp, Border)
                        .padding(8.dp)
                        .background(SurfaceVariant)
                ) {
                    TextField(
                        value = editedContent,
                        onValueChange = { editedContent = it },
                        modifier = Modifier.fillMaxSize(),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = TextPrimary
                        ),
                        colors = TextFieldDefaults.textFieldColors(
                            containerColor = Color.Transparent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedPlaceholderColor = TextMuted,
                            unfocusedPlaceholderColor = TextMuted,
                            cursorColor = AccentPrimary
                        ),
                        placeholder = { Text("[ EMPTY FILE ]", color = TextMuted) },
                        maxLines = Int.MAX_VALUE,
                        singleLine = false
                    )
                }
            }
        }
    }
}

@Composable
fun FileItem(name: String, isDir: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .background(
                color = SurfaceVariant,
                shape = RoundedCornerShape(0.dp)
            )
            .padding(vertical = 2.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isDir) "[DIR] " else "[FILE] ",
            style = MaterialTheme.typography.labelSmall,
            color = if (isDir) AccentSecondary else TextMuted
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isDir) TextPrimary else TextSecondary
        )
    }
}