package com.aptdesk.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme() // Enforce dark theme for premium feel
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state by AptDeskState.state.collectAsState()
                    val progress by AptDeskState.progress.collectAsState()
                    
                    when (state) {
                        AptDeskState.State.Idle -> MainScreen(state)
                        is AptDeskState.State.Running -> MainScreen(state)
                        is AptDeskState.State.Error -> ErrorScreen((state as AptDeskState.State.Error).message)
                        else -> SetupScreen(state, progress)
                    }
                }
            }
        }
    }
}

@Composable
private fun MainScreen(state: AptDeskState.State) {
    val context = LocalContext.current
    val isRunning = state is AptDeskState.State.Running
    var showResetDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "AptDesk", style = MaterialTheme.typography.displayLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Linux Desktop in your Browser",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(48.dp))

            if (isRunning) {
                val ip = (state as AptDeskState.State.Running).ip
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Desktop URL", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "http://$ip:8080",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(48.dp))
                Button(
                    onClick = {
                        val intent = Intent(context, MainService::class.java).apply {
                            action = MainService.ACTION_STOP
                        }
                        context.startService(intent)
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Stop Backend")
                }
            } else {
                Button(
                    onClick = {
                        val intent = Intent(context, MainService::class.java).apply {
                            action = MainService.ACTION_START
                        }
                        ContextCompat.startForegroundService(context, intent)
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("Start Backend")
                }
            }
        }

        IconButton(
            onClick = { showResetDialog = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Factory Reset Services",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("Factory Reset Services") },
                text = { Text("Are you sure you want to reset all configurations to factory defaults? Your personal files will be preserved, but desktop UI and server settings will be wiped.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showResetDialog = false
                            val intent = Intent(context, MainService::class.java).apply {
                                action = MainService.ACTION_RESET
                            }
                            context.startService(intent)
                        }
                    ) {
                        Text("Reset", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun SetupScreen(state: AptDeskState.State, progress: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val statusText = when (state) {
            AptDeskState.State.DownloadingRootfs -> "Downloading Linux File System... $progress%"
            AptDeskState.State.ExtractingRootfs -> "Extracting Linux File System...\nThis might take a few minutes."
            AptDeskState.State.ExtractingAssets -> "Extracting Web Assets..."
            AptDeskState.State.StartingBackend -> "Starting Backend Services..."
            else -> "Preparing..."
        }

        CircularProgressIndicator(modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = statusText,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        if (state == AptDeskState.State.DownloadingRootfs) {
            Spacer(modifier = Modifier.height(24.dp))
            LinearProgressIndicator(
                progress = progress / 100f,
                modifier = Modifier.fillMaxWidth().height(8.dp)
            )
        }
    }
}

@Composable
private fun ErrorScreen(message: String) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Error",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = {
            AptDeskState.state.value = AptDeskState.State.Idle
            val intent = Intent(context, MainService::class.java).apply {
                action = MainService.ACTION_STOP
            }
            context.startService(intent)
        }) {
            Text("Reset")
        }
    }
}
