package com.cognautic.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import com.cognautic.app.ui.chat.ChatViewModel

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
                        text = editingFile ?: "Editor",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
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
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (editingFile != null) {
                        IconButton(onClick = { 
                            viewModel.saveFile(editedContent)
                        }) {
                            Icon(Icons.Default.Save, contentDescription = "Save")
                        }
                    } else {
                        IconButton(onClick = { viewModel.loadFileList(currentDir) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (editingFile == null) {
                // File List (Existing logic)
                Text(
                    text = "Location: $currentDir",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (files.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillParentMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No files found",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
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
            } else {
                // Simple Text Editor (No highlighting)
                TextField(
                    value = editedContent,
                    onValueChange = { editedContent = it },
                    modifier = Modifier.fillMaxSize(),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
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
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isDir) Icons.Default.Folder else Icons.Default.Description,
            contentDescription = null,
            tint = if (isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = name, style = MaterialTheme.typography.bodyLarge)
    }
}
