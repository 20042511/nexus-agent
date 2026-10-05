package ai.nexus.data.model

import androidx.room.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// ─── Goal（用户下达的目标）────────────────────────────────
@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,                    // "每天早上给我 AI 简报"
    val description: String,              // 详细说明
    val trigger: GoalTrigger,             // 触发方式
    val status: GoalStatus = GoalStatus.ACTIVE,
    val createdAt: Long = System.currentTimeMillis(),
    val lastRunAt: Long = 0,
    val runCount: Int = 0,
)

@Serializable
sealed class GoalTrigger {
    @Serializable data class Schedule(val cron: String) : GoalTrigger()       // "0 8 * * *"
    @Serializable data class OnEvent(val eventType: String) : GoalTrigger()   // "API_CHANGED:ollama"
    @Serializable data class Immediate(val once: Boolean = true) : GoalTrigger()
    @Serializable data class Conditional(val condition: String) : GoalTrigger()
}

enum class GoalStatus { ACTIVE, PAUSED, COMPLETED, FAILED }

// ─── Task（Goal 分解出的子任务）──────────────────────────
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val step: Int,                        // 第几步
    val action: String,                   // "CALL_TOOL" / "LLM_REASON" / "NOTIFY_USER"
    val input: String,                    // JSON
    val output: String = "",
    val status: TaskStatus = TaskStatus.PENDING,
    val startedAt: Long = 0,
    val finishedAt: Long = 0,
    val error: String = "",
)

enum class TaskStatus { PENDING, RUNNING, DONE, FAILED, WAITING_USER }

// ─── Memory（记忆条目）───────────────────────────────────
@Entity(tableName = "memories")
data class Memory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: MemoryType,
    val content: String,                  // 自然语言描述
    val embedding: String = "",           // 向量（JSON float array）
    val tags: String = "",                // comma separated
    val importance: Float = 0.5f,         // 0~1
    val createdAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = 0,
    val accessCount: Int = 0,
    val relatedGoalId: Long? = null,
)

enum class MemoryType { EPISODIC, SEMANTIC, PROCEDURAL, WORKING }

// ─── SensorEvent（传感器产生的事件）──────────────────────
@Entity(tableName = "sensor_events")
data class SensorEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sensorId: String,                 // "github.ollama.release"
    val eventType: String,                // "CHANGED" / "THRESHOLD" / "SCHEDULE"
    val payload: String,                  // JSON
    val processed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

// ─── AgentSession（每次 Agent 运行记录）──────────────────
@Entity(tableName = "agent_sessions")
data class AgentSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val trigger: String,                  // 什么触发了这次运行
    val reasoningTrace: String = "",      // ReAct 推理链（JSON array）
    val result: String = "",
    val tokensUsed: Int = 0,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long = 0,
    val success: Boolean = false,
)

// ─── Tool（已注册的工具）──────────────────────────────────
@Entity(tableName = "tools")
data class ToolDef(
    @PrimaryKey val id: String,           // "http_get" / "shell_exec" / "mcp_call"
    val name: String,
    val description: String,             // LLM 看到的描述（影响它选不选这个工具）
    val paramSchema: String,             // JSON Schema
    val enabled: Boolean = true,
    val builtIn: Boolean = true,
)

// ─── ReAct 推理步骤 ─────────────────────────────────────
@Serializable
data class ReActStep(
    val type: StepType,
    val thought: String = "",            // Reason 阶段的想法
    val action: String = "",             // 选择哪个工具
    val actionInput: String = "",        // 工具参数（JSON）
    val observation: String = "",        // 工具返回结果
    val timestamp: Long = System.currentTimeMillis(),
)

enum class StepType { THOUGHT, ACTION, OBSERVATION, FINAL_ANSWER, ERROR }

// ─── 用户与 Agent 的消息（非聊天，是汇报/授权请求）────────
@Entity(tableName = "messages")
data class AgentMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val direction: MessageDirection,     // AGENT_TO_USER / USER_TO_AGENT
    val content: String,
    val requiresAction: Boolean = false, // 需要用户授权才能继续
    val actionTaken: String = "",        // 用户的决定
    val createdAt: Long = System.currentTimeMillis(),
)

enum class MessageDirection { AGENT_TO_USER, USER_TO_AGENT }
