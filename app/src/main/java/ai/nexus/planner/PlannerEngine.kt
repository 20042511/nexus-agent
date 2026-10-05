package ai.nexus.planner

import ai.nexus.data.db.NexusDatabase
import ai.nexus.data.model.*
import ai.nexus.tool.ToolRegistry
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL

/**
 * PlannerEngine — Agent 的大脑
 *
 * 实现 ReAct（Reason + Act）循环：
 *   1. Reason：调用 LLM，给它当前目标 + 工具列表 + 记忆上下文
 *              LLM 返回：思考过程 + 选择哪个工具 + 工具参数
 *   2. Act：    执行工具，拿到结果（Observation）
 *   3. 把 Observation 加入上下文，回到 step 1
 *   4. 直到 LLM 输出 Final Answer 或达到步骤上限
 *
 * 这个循环让 Agent 能处理多步骤、需要中间推理的复杂任务。
 * 不是一问一答，是真正的推理链。
 */
class PlannerEngine(
    private val context: Context,
    private val db: NexusDatabase,
    private val tools: ToolRegistry,
) {
    companion object {
        const val MAX_REACT_STEPS = 10
        const val MAX_TOKENS = 4096
    }

    // ─── Goal 分解 ─────────────────────────────────────────

    suspend fun decomposeGoal(goalId: Long) = withContext(Dispatchers.IO) {
        val goal = db.goalDao().let { /* 从 DB 读 goal */ } ?: return@withContext

        // 创建一次 AgentSession
        val sessionId = db.sessionDao().insert(AgentSession(
            goalId = goalId,
            trigger = "MANUAL",
        ))

        // 运行 ReAct 循环
        runReActLoop(goalId, sessionId)
    }

    suspend fun handleEvent(event: SensorEvent) = withContext(Dispatchers.IO) {
        // 找监听这个 sensorId 的所有活跃 Goal
        // 分解并运行
        // TODO: 从 DB 查 Goal.trigger 匹配 event.sensorId
    }

    suspend fun executeTask(task: Task): String = withContext(Dispatchers.IO) {
        when (task.action) {
            "LLM_REASON" -> {
                val input = Json.decodeFromString<JsonObject>(task.input)
                val prompt = input["prompt"]?.jsonPrimitive?.content ?: ""
                callLLM(prompt)
            }
            "CALL_TOOL" -> {
                val input = Json.decodeFromString<JsonObject>(task.input)
                val toolId = input["tool"]?.jsonPrimitive?.content ?: ""
                val params = input["params"]?.jsonObject ?: buildJsonObject {}
                tools.execute(toolId, params)
            }
            "NOTIFY_USER" -> {
                val input = Json.decodeFromString<JsonObject>(task.input)
                val message = input["message"]?.jsonPrimitive?.content ?: ""
                notifyUser(task.goalId, message)
                "NOTIFIED"
            }
            "WAIT_USER_APPROVAL" -> {
                // 暂停，等用户在 UI 里授权
                db.messageDao().insert(AgentMessage(
                    sessionId = 0,
                    direction = MessageDirection.AGENT_TO_USER,
                    content = Json.decodeFromString<JsonObject>(task.input)["message"]
                        ?.jsonPrimitive?.content ?: "",
                    requiresAction = true,
                ))
                "WAITING"
            }
            else -> "UNKNOWN_ACTION: ${task.action}"
        }
    }

    // ─── ReAct 核心循环 ────────────────────────────────────

    private suspend fun runReActLoop(goalId: Long, sessionId: Long): String {
        val steps = mutableListOf<ReActStep>()
        val toolDefs = db.toolDao().enabledToolsOnce()

        // 构建系统提示
        val systemPrompt = buildSystemPrompt(toolDefs)

        // 从记忆里拉相关上下文
        val memoryContext = buildMemoryContext(goalId)

        var userMessage = buildInitialMessage(goalId, memoryContext)
        var finalAnswer = ""

        for (stepIdx in 0 until MAX_REACT_STEPS) {
            // Reason：让 LLM 决定下一步
            val llmResponse = callLLM(
                userMessage = userMessage,
                systemPrompt = systemPrompt,
                history = stepsToMessages(steps),
            )

            // 解析 LLM 输出
            val parsed = parseReActResponse(llmResponse)
            steps.add(parsed)

            when (parsed.type) {
                StepType.FINAL_ANSWER -> {
                    finalAnswer = parsed.thought
                    break
                }
                StepType.ACTION -> {
                    // Act：执行工具
                    val observation = try {
                        tools.execute(parsed.action, Json.decodeFromString(parsed.actionInput))
                    } catch (e: Exception) {
                        "ERROR: ${e.message}"
                    }

                    val obsStep = ReActStep(
                        type = StepType.OBSERVATION,
                        observation = observation,
                    )
                    steps.add(obsStep)

                    // 把 Observation 加入下一轮上下文
                    userMessage = "Observation: $observation\n\nContinue."
                }
                StepType.ERROR -> break
                else -> break
            }
        }

        // 保存推理链
        val trace = Json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(ReActStep.serializer()),
            steps
        )
        db.sessionDao().update(
            db.sessionDao().sessionsForGoal(goalId).firstOrNull()?.copy(
                reasoningTrace = trace,
                result = finalAnswer,
                finishedAt = System.currentTimeMillis(),
                success = finalAnswer.isNotEmpty(),
            ) ?: return finalAnswer
        )

        // 存入记忆
        if (finalAnswer.isNotEmpty()) {
            db.memoryDao().insert(Memory(
                type = MemoryType.EPISODIC,
                content = "Goal $goalId 执行结果: $finalAnswer",
                importance = 0.7f,
                relatedGoalId = goalId,
            ))
        }

        return finalAnswer
    }

    // ─── LLM 调用 ──────────────────────────────────────────

    private fun callLLM(
        userMessage: String,
        systemPrompt: String = "",
        history: List<Pair<String, String>> = emptyList(),
    ): String {
        // 从 App 设置里读 LLM 配置（endpoint + key + model）
        // 这里先用 OpenAI 兼容格式
        val config = LLMConfig.load(context)

        val messages = buildJsonArray {
            if (systemPrompt.isNotEmpty()) {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                })
            }
            history.forEach { (role, content) ->
                add(buildJsonObject {
                    put("role", role)
                    put("content", content)
                })
            }
            add(buildJsonObject {
                put("role", "user")
                put("content", userMessage)
            })
        }

        val requestBody = buildJsonObject {
            put("model", config.model)
            put("messages", messages)
            put("max_tokens", MAX_TOKENS)
            put("temperature", 0.3)
        }

        val conn = URL(config.endpoint).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        conn.connectTimeout = 30_000
        conn.readTimeout = 60_000
        conn.doOutput = true

        conn.outputStream.write(requestBody.toString().toByteArray())

        val response = Json.decodeFromString<JsonObject>(
            conn.inputStream.bufferedReader().readText()
        )

        return response["choices"]?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject
            ?.get("content")?.jsonPrimitive?.content
            ?: ""
    }

    // ─── 工具系统提示构建 ──────────────────────────────────

    private fun buildSystemPrompt(toolDefs: List<ToolDef>): String {
        val toolsDesc = toolDefs.joinToString("\n") { tool ->
            "- ${tool.id}: ${tool.description}\n  params: ${tool.paramSchema}"
        }
        return """
You are Nexus, an autonomous AI agent running on Android.
You operate in a ReAct loop: Thought → Action → Observation → Thought → ...

Available tools:
$toolsDesc

Output format (strict JSON):
For reasoning: {"type":"THOUGHT","thought":"your reasoning"}
For action: {"type":"ACTION","thought":"why","action":"tool_id","action_input":{"param":"value"}}
For final answer: {"type":"FINAL_ANSWER","thought":"the final result for the user"}

Rules:
1. Always think before acting
2. Use tools to gather real information, never make up facts
3. If a task requires user approval (irreversible actions), use the notify_user tool first
4. Be concise in final answers
        """.trimIndent()
    }

    private fun buildMemoryContext(goalId: Long): String {
        // 从记忆层拉最相关的上下文
        // 简化版：返回最近的 episodic memories
        return ""  // TODO: 实现向量检索
    }

    private suspend fun buildInitialMessage(goalId: Long, memoryContext: String): String {
        // 从 DB 读 Goal 详情
        return "Execute the assigned goal. Memory context: $memoryContext"
    }

    private fun stepsToMessages(steps: List<ReActStep>): List<Pair<String, String>> {
        return steps.map { step ->
            when (step.type) {
                StepType.THOUGHT, StepType.ACTION ->
                    "assistant" to Json.encodeToString(ReActStep.serializer(), step)
                StepType.OBSERVATION ->
                    "user" to "Observation: ${step.observation}"
                else -> "assistant" to step.thought
            }
        }
    }

    private fun parseReActResponse(response: String): ReActStep {
        return try {
            // 提取 JSON（LLM 可能在 JSON 外面加文字）
            val jsonStr = response.substringAfter("{").substringBeforeLast("}").let { "{$it}" }
            val obj = Json.decodeFromString<JsonObject>(jsonStr)
            val type = StepType.valueOf(obj["type"]?.jsonPrimitive?.content ?: "ERROR")
            ReActStep(
                type = type,
                thought = obj["thought"]?.jsonPrimitive?.content ?: "",
                action = obj["action"]?.jsonPrimitive?.content ?: "",
                actionInput = obj["action_input"]?.toString() ?: "{}",
            )
        } catch (e: Exception) {
            ReActStep(type = StepType.ERROR, thought = "Parse failed: ${e.message}")
        }
    }

    private suspend fun notifyUser(goalId: Long, message: String) {
        db.messageDao().insert(AgentMessage(
            sessionId = 0,
            direction = MessageDirection.AGENT_TO_USER,
            content = message,
        ))
        // 同时发系统通知
    }
}

// ─── LLM 配置 ─────────────────────────────────────────────

data class LLMConfig(
    val endpoint: String,
    val apiKey: String,
    val model: String,
) {
    companion object {
        fun load(context: Context): LLMConfig {
            val prefs = context.getSharedPreferences("nexus_config", Context.MODE_PRIVATE)
            return LLMConfig(
                endpoint = prefs.getString("llm_endpoint",
                    "https://api.anthropic.com/v1/messages") ?: "",
                apiKey = prefs.getString("llm_api_key", "") ?: "",
                model = prefs.getString("llm_model", "claude-haiku-4-5-20251001") ?: "",
            )
        }
    }
}
