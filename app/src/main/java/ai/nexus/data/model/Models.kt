package ai.nexus.data.model

import androidx.room.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// ─── Goal ─────────────────────────────────────────────
@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val triggerJson: String = "{}",       // GoalTrigger 序列化后存为 String
    val statusStr: String = "ACTIVE",     // GoalStatus 存为 String
    val createdAt: Long = System.currentTimeMillis(),
    val lastRunAt: Long = 0,
    val runCount: Int = 0,
)

@Serializable
sealed class GoalTrigger {
    @Serializable data class Schedule(val cron: String) : GoalTrigger()
    @Serializable data class OnEvent(val eventType: String) : GoalTrigger()
    @Serializable data class Immediate(val once: Boolean = true) : GoalTrigger()
    @Serializable data class Conditional(val condition: String) : GoalTrigger()
}

enum class GoalStatus { ACTIVE, PAUSED, COMPLETED, FAILED }

// ─── Task ─────────────────────────────────────────────
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val step: Int,
    val action: String,
    val input: String,
    val output: String = "",
    val statusStr: String = "PENDING",
    val startedAt: Long = 0,
    val finishedAt: Long = 0,
    val error: String = "",
)

enum class TaskStatus { PENDING, RUNNING, DONE, FAILED, WAITING_USER }

// ─── Memory ────────────────────────────────────────────
@Entity(tableName = "memories")
data class Memory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val typeStr: String = "SEMANTIC",
    val content: String,
    val tags: String = "",
    val importance: Float = 0.5f,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = 0,
    val accessCount: Int = 0,
    val relatedGoalId: Long? = null,
)

enum class MemoryType { EPISODIC, SEMANTIC, PROCEDURAL, WORKING }

// ─── SensorEvent ───────────────────────────────────────
@Entity(tableName = "sensor_events")
data class SensorEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sensorId: String,
    val eventType: String,
    val payload: String,
    val processed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

// ─── AgentSession ──────────────────────────────────────
@Entity(tableName = "agent_sessions")
data class AgentSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val trigger: String,
    val reasoningTrace: String = "",
    val result: String = "",
    val tokensUsed: Int = 0,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long = 0,
    val success: Boolean = false,
)

// ─── ToolDef ────────────────────────────────────────────
@Entity(tableName = "tools")
data class ToolDef(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val paramSchema: String,
    val enabled: Boolean = true,
    val builtIn: Boolean = true,
)

// ─── ReAct Step ─────────────────────────────────────────
@Serializable
data class ReActStep(
    val type: String,                     // "THOUGHT","ACTION","OBSERVATION","FINAL_ANSWER","ERROR"
    val thought: String = "",
    val action: String = "",
    val actionInput: String = "",
    val observation: String = "",
    val timestamp: Long = System.currentTimeMillis(),
)

// ─── AgentMessage ───────────────────────────────────────
@Entity(tableName = "messages")
data class AgentMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val direction: String = "AGENT_TO_USER",  // "AGENT_TO_USER" | "USER_TO_AGENT"
    val content: String,
    val requiresAction: Boolean = false,
    val actionTaken: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

// ─── String 常量（替代已删除的 enum/sealed class）─────
object MessageDirection {
    const val AGENT_TO_USER = "AGENT_TO_USER"
    const val USER_TO_AGENT = "USER_TO_AGENT"
}

object StepType {
    const val THOUGHT = "THOUGHT"
    const val ACTION = "ACTION"
    const val OBSERVATION = "OBSERVATION"
    const val FINAL_ANSWER = "FINAL_ANSWER"
    const val ERROR = "ERROR"
}

object TaskStatus {
    const val PENDING = "PENDING"
    const val RUNNING = "RUNNING"
    const val DONE = "DONE"
    const val FAILED = "FAILED"
    const val WAITING_USER = "WAITING_USER"
}

// ─── 扩展属性（简化访问）────────────────────────────────
val Goal.trigger: GoalTrigger
    get() = Json.decodeFromString(GoalTrigger.serializer(), triggerJson)

val Task.status: String
    get() = statusStr
