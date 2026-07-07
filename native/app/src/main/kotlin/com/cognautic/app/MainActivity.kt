package com.cognautic.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import android.view.View
import android.os.Build
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cognautic.app.core.models.*
import com.cognautic.app.ui.chat.ChatViewModel
import com.cognautic.app.ui.screens.ChatScreen
import com.cognautic.app.ui.screens.WorkspaceScreen
import com.cognautic.app.ui.screens.EditorScreen
import com.cognautic.app.ui.theme.CognauticTheme
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import android.app.Activity
import com.google.android.gms.ads.MobileAds
import com.cognautic.app.ui.components.AdMobBanner
import com.cognautic.app.core.network.TelemetryClient

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MobileAds.initialize(this) {}
        TelemetryClient.trackEvent("app_opened")
        setContent {
            CognauticApp()
        }
    }
}

@Composable
fun CognauticApp() {
    CognauticTheme {
        val viewModel: ChatViewModel = viewModel()
        val currentWorkspace by viewModel.currentWorkspace.collectAsState()
        val showEditor by viewModel.showEditor.collectAsState()
        val notifications by viewModel.notifications.collectAsState()
        var showSettings by remember { mutableStateOf(false) }
        val view = LocalView.current
        val colorScheme = MaterialTheme.colorScheme

        LaunchedEffect(colorScheme) {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = colorScheme.surface.luminance() > 0.5f
            
            // Check for MANAGE_EXTERNAL_STORAGE on Android 11+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!android.os.Environment.isExternalStorageManager()) {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:" + view.context.packageName)
                    (view.context as Activity).startActivity(intent)
                }
            } else {
                // For older versions, request common storage permissions
                val permissions = arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
                androidx.core.app.ActivityCompat.requestPermissions(view.context as Activity, permissions, 100)
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (currentWorkspace == null) {
                        WorkspaceScreen(
                            viewModel = viewModel,
                            onWorkspaceSelected = { /* Screen automatically updates via currentWorkspace flow */ },
                            onSettingsClick = { showSettings = true }
                        )
                    } else if (showEditor) {
                        EditorScreen(
                            viewModel = viewModel,
                            onClose = { viewModel.onCloseEditor() }
                        )
                    } else {
                        ChatScreen(
                            viewModel = viewModel,
                            onSettingsClick = { showSettings = true }
                        )
                    }

                    if (showSettings) {
                        SettingsDialog(
                            onDismiss = { showSettings = false },
                            viewModel = viewModel
                        )
                    }
                }

                // In-app Notifications Overlay (Sliding from top)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp, start = 16.dp, end = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    notifications.forEach { notification ->
                        key(notification) {
                            AnimatedVisibility(
                                visible = true,
                                enter = slideInVertically() + fadeIn(),
                                exit = slideOutVertically() + fadeOut()
                            ) {
                                Surface(
                                    shape = androidx.compose.ui.graphics.RectangleShape,
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                    tonalElevation = 0.dp,
                                    shadowElevation = 0.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = notification,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Banner Ad at the bottom
            AdMobBanner(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    viewModel: ChatViewModel
) {
    val providers by viewModel.providers.collectAsState()
    val isAutoApprove by viewModel.isAutoApprove.collectAsState()
    val showThinking by viewModel.showThinking.collectAsState()
    val temperature by viewModel.temperature.collectAsState()
    var providerToEdit by remember { mutableStateOf<LlmProvider?>(null) }
    var showAddProvider by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("LLM Providers", style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = { showAddProvider = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Provider")
                    }
                }
                
                providers.forEach { provider ->
                    ProviderItem(
                        provider = provider,
                        onEdit = { providerToEdit = it },
                        onDelete = { 
                            if (it.isBuiltIn) viewModel.onApiKeyChange(it.protocol, "")
                            else viewModel.onDeleteProvider(it.id) 
                        },
                        onKeyChange = { viewModel.onApiKeyChange(provider.protocol, it) }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Approve Tools", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Execute tool calls without manual confirmation",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = isAutoApprove,
                        onCheckedChange = { viewModel.onAutoApproveChange(it) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Show Thinking", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Display provider-supplied model reasoning when available",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = showThinking,
                        onCheckedChange = { viewModel.onShowThinkingChange(it) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Temperature ${"%.1f".format(temperature)}", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Lower values are steadier for coding; higher values explore more alternatives",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Slider(
                    value = temperature,
                    onValueChange = { viewModel.onTemperatureChange(it) },
                    valueRange = 0f..2f,
                    steps = 19,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))
                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(16.dp))

                val gRules by viewModel.globalRules.collectAsState()
                Text("Global Rules", style = MaterialTheme.typography.titleSmall)
                Text(
                    "AI MUST obey these rules across all workspaces",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = gRules,
                    onValueChange = { viewModel.onGlobalRulesChange(it) },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    placeholder = { Text("e.g. Always use functional approach...") }
                )

                val currentWorkspace by viewModel.currentWorkspace.collectAsState()
                if (currentWorkspace != null) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Workspace Rules", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Add workspace rules for the current workspace (${currentWorkspace!!.name})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = currentWorkspace!!.rules,
                        onValueChange = { viewModel.onWorkspaceRulesChange(currentWorkspace!!.id, it) },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        placeholder = { Text("Instructions for this project...") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )

    if (showAddProvider) {
        ProviderEditDialog(
            onDismiss = { showAddProvider = false },
            onSave = { name, url, key, proto ->
                viewModel.onAddProvider(name, url, key, proto)
                showAddProvider = false
            }
        )
    }

    if (providerToEdit != null) {
        ProviderEditDialog(
            provider = providerToEdit,
            onDismiss = { providerToEdit = null },
            onSave = { name, url, key, proto ->
                viewModel.onUpdateProvider(providerToEdit!!.id, name, url, key, proto)
                providerToEdit = null
            }
        )
    }
}

@Composable
fun ProviderItem(
    provider: LlmProvider,
    onEdit: (LlmProvider) -> Unit,
    onDelete: (LlmProvider) -> Unit,
    onKeyChange: (String) -> Unit
) {
    var keyState by remember(provider.apiKey) { mutableStateOf(provider.apiKey) }
    
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(provider.name, style = MaterialTheme.typography.bodyLarge)
                Text(provider.protocol.displayName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (!provider.isBuiltIn) {
                IconButton(onClick = { onEdit(provider) }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp))
                }
            }
            if (!provider.isBuiltIn || provider.apiKey.isNotEmpty()) {
                IconButton(onClick = { onDelete(provider) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
            }
        }
        OutlinedTextField(
            value = keyState,
            onValueChange = { 
                keyState = it
                onKeyChange(it)
            },
            label = { Text("API Key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            trailingIcon = {
                if (provider.isBuiltIn) {
                    Icon(Icons.Default.Lock, contentDescription = "Built-in", modifier = Modifier.size(16.dp))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderEditDialog(
    provider: LlmProvider? = null,
    onDismiss: () -> Unit,
    onSave: (String, String, String, ModelProvider) -> Unit
) {
    var name by remember { mutableStateOf(provider?.name ?: "") }
    var baseUrl by remember { mutableStateOf(provider?.baseUrl ?: "") }
    var apiKey by remember { mutableStateOf(provider?.apiKey ?: "") }
    var protocol by remember { mutableStateOf(provider?.protocol ?: ModelProvider.OPENAI) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (provider == null) "Add Provider" else "Edit Provider") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("Base URL (e.g. https://api.proxy.com/v1)") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text("API Key") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation())
                Spacer(modifier = Modifier.height(8.dp))
                
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = protocol.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Protocol") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        ModelProvider.values().forEach { proto ->
                            DropdownMenuItem(
                                text = { Text(proto.displayName) },
                                onClick = {
                                    protocol = proto
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, baseUrl, apiKey, protocol) }, enabled = name.isNotBlank() && baseUrl.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
