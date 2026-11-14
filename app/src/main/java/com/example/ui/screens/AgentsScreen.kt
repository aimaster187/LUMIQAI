package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.AgentRunStatus
import com.example.ai.WorkspaceAgent
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.LumiqGlassCard

@Composable
fun AgentsScreen(
    agents: List<WorkspaceAgent>,
    sharedContext: String,
    onDispatchTask: (String, String) -> Unit,
    onCancelAgent: (String) -> Unit,
    onEmergencyStop: () -> Unit,
    onCreateCustomAgent: (String, String, String, List<String>, Boolean) -> Unit
) {
    var selectedAgentForTask by remember { mutableStateOf<WorkspaceAgent?>(null) }
    var pendingSensitiveAgent by remember { mutableStateOf<Pair<WorkspaceAgent, String>?>(null) }
    var showCustomAgentDialog by remember { mutableStateOf(false) }

    val runningCount = agents.count { it.status == AgentRunStatus.RUNNING || it.status == AgentRunStatus.QUEUED }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 104.dp, top = 6.dp)
    ) {
        item {
            LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Five-Agent AI Workspace", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Max 5 parallel agents • Active: $runningCount/5",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricBlue
                            )
                        }
                        Button(
                            onClick = onEmergencyStop,
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonError),
                            modifier = Modifier.testTag("agents_emergency_stop")
                        ) {
                            Icon(Icons.Rounded.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Emergency Stop")
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { showCustomAgentDialog = true },
                        modifier = Modifier.fillMaxWidth().testTag("create_custom_agent_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create Custom Agent")
                    }
                    if (sharedContext.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Shared Context Active (${sharedContext.length} chars)",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldSuccess
                        )
                    }
                }
            }
        }

        items(agents, key = { it.id }) { agent ->
            val statusColor = when (agent.status) {
                AgentRunStatus.RUNNING -> ElectricBlue
                AgentRunStatus.QUEUED -> AmberWarning
                AgentRunStatus.COMPLETED -> EmeraldSuccess
                AgentRunStatus.FAILED, AgentRunStatus.CANCELLED -> CrimsonError
                AgentRunStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            LumiqGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(ElectricBlue, CyanGlow))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(agent.avatarCode, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(agent.name, style = MaterialTheme.typography.titleMedium)
                                Text(agent.roleType, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = statusColor.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = agent.status.name,
                                color = statusColor,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(agent.instructions, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    if (agent.logs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Log: ${agent.logs.last()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricBlue
                        )
                    }

                    if (agent.result.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = agent.result,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { selectedAgentForTask = agent },
                            modifier = Modifier.weight(1f).testTag("run_agent_${agent.id}")
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Assign Task")
                        }
                        if (agent.status == AgentRunStatus.RUNNING || agent.status == AgentRunStatus.QUEUED) {
                            IconButton(onClick = { onCancelAgent(agent.id) }) {
                                Icon(Icons.Rounded.Cancel, contentDescription = "Cancel Agent", tint = CrimsonError)
                            }
                        }
                    }
                }
            }
        }
    }

    selectedAgentForTask?.let { agent ->
        var taskInput by remember(agent.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { selectedAgentForTask = null },
            title = { Text("Assign to ${agent.name}") },
            text = {
                OutlinedTextField(
                    value = taskInput,
                    onValueChange = { taskInput = it },
                    label = { Text("Task prompt") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (taskInput.isNotBlank()) {
                            if (agent.requiresConfirmation) {
                                pendingSensitiveAgent = agent to taskInput
                            } else {
                                onDispatchTask(agent.id, taskInput)
                            }
                            selectedAgentForTask = null
                        }
                    }
                ) { Text("Dispatch") }
            },
            dismissButton = {
                TextButton(onClick = { selectedAgentForTask = null }) { Text("Cancel") }
            }
        )
    }

    pendingSensitiveAgent?.let { (agent, taskPrompt) ->
        AlertDialog(
            onDismissRequest = { pendingSensitiveAgent = null },
            title = { Text("Confirm Sensitive Agent Run") },
            text = {
                Text("${agent.name} involves device or messaging assistance. Confirm dispatching:\n\n\"$taskPrompt\"")
            },
            confirmButton = {
                Button(onClick = {
                    onDispatchTask(agent.id, taskPrompt)
                    pendingSensitiveAgent = null
                }) { Text("Approve & Run") }
            },
            dismissButton = {
                TextButton(onClick = { pendingSensitiveAgent = null }) { Text("Deny") }
            }
        )
    }

    if (showCustomAgentDialog) {
        var name by remember { mutableStateOf("") }
        var role by remember { mutableStateOf("") }
        var instructions by remember { mutableStateOf("") }
        var requiresConfirm by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCustomAgentDialog = false },
            title = { Text("Create Custom Agent") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Agent Name") }, singleLine = true)
                    OutlinedTextField(value = role, onValueChange = { role = it }, label = { Text("Specialty Role") }, singleLine = true)
                    OutlinedTextField(value = instructions, onValueChange = { instructions = it }, label = { Text("System Instructions") }, minLines = 2)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = requiresConfirm, onCheckedChange = { requiresConfirm = it })
                        Text("Require confirmation before run", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank() && instructions.isNotBlank()) {
                        onCreateCustomAgent(name, role.ifBlank { "Custom" }, instructions, listOf("Gemini AI"), requiresConfirm)
                        showCustomAgentDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomAgentDialog = false }) { Text("Cancel") }
            }
        )
    }
}
