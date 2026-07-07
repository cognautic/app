package com.cognautic.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.cognautic.app.core.models.Workspace
import com.cognautic.app.ui.chat.ChatViewModel
import com.cognautic.app.ui.theme.*

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    viewModel: ChatViewModel,
    onWorkspaceSelected: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val workspaces by viewModel.workspaces.collectAsState()
    val activeWorkspaces by viewModel.activeWorkspaces.collectAsState()
    var selectedWorkspaceForMenu by remember { mutableStateOf<Workspace?>(null) }
    var workspaceToRename by remember { mutableStateOf<Workspace?>(null) }
    var workspaceToDelete by remember { mutableStateOf<Workspace?>(null) }
    var workspaceToEditRules by remember { mutableStateOf<Workspace?>(null) }
    
    // Folder Picker Launcher
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Ignore if not supported by provider, but usually needed for SAF
            }
            val path = it.toString()
            viewModel.onCreateWorkspace(path)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Cognautic")
                        Text(
                            "Workspaces",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { launcher.launch(null) }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Workspace")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Choose a project folder",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Each workspace keeps its own files, rules, and chat history.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .border(1.dp, Border)
                    .padding(8.dp)
                    .background(SurfaceVariant)
            ) {
                if (workspaces.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "No workspaces yet",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "Tap + to pick a folder.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(workspaces) { workspace ->
                            Box {
                                WorkspaceItem(
                                    workspace = workspace,
                                    isActive = activeWorkspaces.contains(workspace.id),
                                    onClick = {
                                        viewModel.onWorkspaceSelected(workspace)
                                        onWorkspaceSelected()
                                    },
                                    onLongClick = {
                                        selectedWorkspaceForMenu = workspace
                                    }
                                )
                                
                                DropdownMenu(
                                    expanded = selectedWorkspaceForMenu == workspace,
                                    onDismissRequest = { selectedWorkspaceForMenu = null },
                                    modifier = Modifier.background(Surface).border(1.dp, Border)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Rename") },
                                        onClick = {
                                            workspaceToRename = workspace
                                            selectedWorkspaceForMenu = null
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Rules") },
                                        onClick = {
                                            workspaceToEditRules = workspace
                                            selectedWorkspaceForMenu = null
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            workspaceToDelete = workspace
                                            selectedWorkspaceForMenu = null
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Rename Dialog
    if (workspaceToRename != null) {
        var newName by remember { mutableStateOf(workspaceToRename?.name ?: "") }
        AlertDialog(
            onDismissRequest = { workspaceToRename = null },
            title = { Text("Rename Workspace") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    workspaceToRename?.let { viewModel.onRenameWorkspace(it.id, newName) }
                    workspaceToRename = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { workspaceToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (workspaceToDelete != null) {
        AlertDialog(
            onDismissRequest = { workspaceToDelete = null },
            title = { Text("Delete Workspace?") },
            text = { Text("Are you sure you want to delete '${workspaceToDelete?.name}'? This will also clear its chat history.") },
            confirmButton = {
                Button(
                    onClick = {
                        workspaceToDelete?.let { viewModel.onDeleteWorkspace(it.id) }
                        workspaceToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { workspaceToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rules Dialog
    if (workspaceToEditRules != null) {
        var rules by remember { mutableStateOf(workspaceToEditRules?.rules ?: "") }
        AlertDialog(
            onDismissRequest = { workspaceToEditRules = null },
            title = { Text("Workspace Rules") },
            text = {
                Column {
                    Text(
                        "Specific instructions for '${workspaceToEditRules?.name}'",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rules,
                        onValueChange = { rules = it },
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        placeholder = { Text("e.g. This is a React project, use Tailwind...") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    workspaceToEditRules?.let { viewModel.onWorkspaceRulesChange(it.id, rules) }
                    workspaceToEditRules = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { workspaceToEditRules = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkspaceItem(
    workspace: Workspace, 
    isActive: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .background(
                color = if (isActive) MaterialTheme.colorScheme.primaryContainer else Surface,
                shape = androidx.compose.ui.graphics.RectangleShape
            )
            .border(1.dp, if (isActive) AccentPrimary else Border, androidx.compose.ui.graphics.RectangleShape)
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isActive) "[WS*] " else "[WS]  ",
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) AccentPrimary else TextMuted
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = workspace.name.uppercase(),
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
            Text(
                text = workspace.path,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        if (isActive) {
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .graphicsLayer(alpha = alpha)
                    .background(MaterialTheme.colorScheme.primary, androidx.compose.ui.graphics.RectangleShape)
            )
        }
    }
}
