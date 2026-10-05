package ai.nexus.ui.screen

import ai.nexus.data.model.*
import ai.nexus.service.AgentService
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * HomeScreen — Agent 控制中心
 *
 * 不是聊天界面。是一个仪表盘：
 * - 左上：Agent 状态（活跃/暂停/执行中）
 * - 主体：Agent 发来的消息 + 需要用户决策的卡片
 * - 底部：目标管理入口
 *
 * 用户不主动发消息，Agent 主动汇报。
 * 用户需要决策时，才会看到"授权"按钮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val agentStatus by viewModel.agentStatus.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Nexus", fontWeight = FontWeight.Bold)
                        Text(
                            text = agentStatus,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    // Agent 状态指示器
                    AgentStatusDot(agentStatus)
                    IconButton(onClick = { /* 设置 */ }) {
                        Icon(Icons.Default.Settings, "设置")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showAddGoal() },
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Default.Add, "新建目标")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // ── 活跃目标快览 ─────────────────────────────
            if (goals.isNotEmpty()) {
                ActiveGoalsBar(goals)
                HorizontalDivider()
            }

            // ── Agent 消息流 ──────────────────────────────
            LazyColumn(
                modifier = Modifier.weight(1f),
                reverseLayout = true,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(messages, key = { it.id }) { message ->
                    AgentMessageCard(
                        message = message,
                        onApprove = { viewModel.approve(message) },
                        onReject = { viewModel.reject(message) },
                    )
                }

                if (messages.isEmpty()) {
                    item {
                        EmptyState()
                    }
                }
            }
        }
    }

    // ── 新建目标对话框 ────────────────────────────────────
    if (viewModel.showingAddGoal) {
        AddGoalDialog(
            onConfirm = { title, trigger ->
                viewModel.addGoal(title, trigger)
            },
            onDismiss = { viewModel.hideAddGoal() },
        )
    }
}

// ─── Agent 状态指示点 ─────────────────────────────────────

@Composable
fun AgentStatusDot(status: String) {
    val color = when {
        status.contains("执行") -> Color(0xFF4CAF50)   // 绿色：运行中
        status.contains("待命") -> Color(0xFF2196F3)   // 蓝色：就绪
        status.contains("暂停") -> Color(0xFFFF9800)   // 橙色：暂停
        else -> Color(0xFF9E9E9E)                       // 灰色：未知
    }
    Box(
        Modifier
            .padding(end = 8.dp)
            .size(10.dp)
            .background(color, shape = RoundedCornerShape(50))
    )
}

// ─── 活跃目标横条 ─────────────────────────────────────────

@Composable
fun ActiveGoalsBar(goals: List<Goal>) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(goals) { goal ->
            SuggestionChip(
                onClick = {},
                label = { Text(goal.title, maxLines = 1) },
                icon = {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }
    }
}

// ─── Agent 消息卡片 ───────────────────────────────────────

@Composable
fun AgentMessageCard(
    message: AgentMessage,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    val isFromAgent = message.direction == MessageDirection.AGENT_TO_USER

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (message.requiresAction)
                MaterialTheme.colorScheme.errorContainer
            else if (isFromAgent)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isFromAgent) Icons.Default.Android else Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isFromAgent) "Agent" else "你",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    formatTime(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(message.content, style = MaterialTheme.typography.bodyMedium)

            // 需要授权的卡片显示操作按钮
            if (message.requiresAction && message.actionTaken.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onReject) {
                        Text("拒绝")
                    }
                    Button(onClick = onApprove) {
                        Text("授权执行")
                    }
                }
            } else if (message.requiresAction && message.actionTaken.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "已${message.actionTaken}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─── 空状态 ───────────────────────────────────────────────

@Composable
fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.Android,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Agent 待命中",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "点击 + 下达一个目标，Agent 会自主规划并执行，完成后来找你汇报。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

// ─── 添加目标对话框 ───────────────────────────────────────

@Composable
fun AddGoalDialog(
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var triggerType by remember { mutableStateOf("立即执行") }
    var cronExpr by remember { mutableStateOf("0 8 * * *") }

    val triggers = listOf("立即执行", "每天定时", "外部事件触发")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("下达目标给 Agent") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("目标描述") },
                    placeholder = { Text("例：每天早上给我一份 AI 领域简报") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("触发方式", style = MaterialTheme.typography.labelMedium)
                triggers.forEach { t ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { triggerType = t },
                    ) {
                        RadioButton(selected = triggerType == t, onClick = { triggerType = t })
                        Text(t, modifier = Modifier.padding(start = 4.dp))
                    }
                }

                if (triggerType == "每天定时") {
                    OutlinedTextField(
                        value = cronExpr,
                        onValueChange = { cronExpr = it },
                        label = { Text("Cron 表达式") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // 说明卡片
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Text(
                        "Agent 会自主规划步骤、调用工具、完成后来找你汇报。" +
                        "涉及不可逆操作时会先请求你授权。",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(title, if (triggerType == "每天定时") cronExpr else triggerType)
                    }
                }
            ) { Text("委托给 Agent") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private fun formatTime(ms: Long): String {
    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(ms))
}
