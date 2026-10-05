package ai.nexus.tool

import ai.nexus.data.db.NexusDatabase
import ai.nexus.data.model.ToolDef
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL

/**
 * ToolRegistry — Agent 的工具箱
 *
 * 所有工具统一注册在这里。
 * PlannerEngine 通过 execute(toolId, params) 调用。
 * 工具是 Agent 与真实世界交互的唯一接口。
 */
class ToolRegistry(
    private val context: Context,
    private val db: NexusDatabase,
) {
    private val tools = mutableMapOf<String, Tool>()

    // ─── 注册内置工具 ─────────────────────────────────────

    suspend fun registerBuiltIns() {
        register(HttpGetTool())
        register(HttpPostTool())
        register(ShellTool())
        register(FileReadTool())
        register(FileWriteTool())
        register(NotifyUserTool(context, db))
        register(MemorySearchTool(db))
        register(MemoryWriteTool(db))
        register(NtfyPushTool())
        register(GitHubApiTool())
        register(ArxivSearchTool())
        register(PypiLookupTool())

        // 把工具定义写入 DB（供 LLM 读取和 UI 展示）
        tools.values.forEach { tool ->
            db.toolDao().insertIfAbsent(ToolDef(
                id = tool.id,
                name = tool.name,
                description = tool.description,
                paramSchema = tool.paramSchema,
            ))
        }
    }

    fun register(tool: Tool) {
        tools[tool.id] = tool
    }

    suspend fun execute(toolId: String, params: JsonObject): String {
        val tool = tools[toolId] ?: return "ERROR: Unknown tool '$toolId'"
        return withContext(Dispatchers.IO) {
            try {
                tool.execute(params)
            } catch (e: Exception) {
                "ERROR: ${e.message}"
            }
        }
    }
}

// ─── Tool 接口 ────────────────────────────────────────────

abstract class Tool {
    abstract val id: String
    abstract val name: String
    abstract val description: String
    abstract val paramSchema: String
    abstract suspend fun execute(params: JsonObject): String
}

// ─── HTTP GET ─────────────────────────────────────────────

class HttpGetTool : Tool() {
    override val id = "http_get"
    override val name = "HTTP GET"
    override val description = "Fetch content from a URL. Returns response body as text."
    override val paramSchema = """{"url":"string","headers":"object?"}"""

    override suspend fun execute(params: JsonObject): String {
        val url = params["url"]?.jsonPrimitive?.content ?: return "ERROR: url required"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("User-Agent", "Nexus-Agent/1.0")
        params["headers"]?.jsonObject?.forEach { (k, v) ->
            conn.setRequestProperty(k, v.jsonPrimitive.content)
        }
        val body = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        return body.take(8000)
    }
}

// ─── HTTP POST ────────────────────────────────────────────

class HttpPostTool : Tool() {
    override val id = "http_post"
    override val name = "HTTP POST"
    override val description = "Send a POST request to a URL with a JSON body."
    override val paramSchema = """{"url":"string","body":"object","headers":"object?"}"""

    override suspend fun execute(params: JsonObject): String {
        val url = params["url"]?.jsonPrimitive?.content ?: return "ERROR: url required"
        val body = params["body"]?.toString() ?: "{}"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("User-Agent", "Nexus-Agent/1.0")
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.doOutput = true
        conn.outputStream.write(body.toByteArray())
        val resp = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        return resp.take(4000)
    }
}

// ─── Shell（proot workspace）─────────────────────────────

class ShellTool : Tool() {
    override val id = "shell_exec"
    override val name = "Shell Execute"
    override val description = """
        Execute a shell command in the proot workspace (/workspace).
        This is the Agent's physical body — can run Python scripts, install packages,
        read/write files, make network requests. Use for complex tasks.
        IMPORTANT: Only use for safe, reversible operations unless user approved.
    """.trimIndent()
    override val paramSchema = """{"command":"string","timeout_sec":"number?"}"""

    override suspend fun execute(params: JsonObject): String {
        val command = params["command"]?.jsonPrimitive?.content ?: return "ERROR: command required"
        val timeout = params["timeout_sec"]?.jsonPrimitive?.int ?: 30

        // 安全检查：禁止危险命令
        val dangerous = listOf("rm -rf /", "mkfs", "dd if=", ":(){:|:&};:")
        if (dangerous.any { command.contains(it) }) {
            return "ERROR: Dangerous command blocked. Request user approval first."
        }

        val process = ProcessBuilder("bash", "-c", command)
            .directory(java.io.File("/workspace"))
            .redirectErrorStream(true)
            .start()

        val result = process.inputStream.bufferedReader().readText()
        val exited = process.waitFor(timeout.toLong(), java.util.concurrent.TimeUnit.SECONDS)
        if (!exited) {
            process.destroy()
            return "TIMEOUT after ${timeout}s\n$result"
        }
        return "EXIT:${process.exitValue()}\n$result".take(4000)
    }
}

// ─── 文件读写 ─────────────────────────────────────────────

class FileReadTool : Tool() {
    override val id = "file_read"
    override val name = "Read File"
    override val description = "Read a file from the workspace."
    override val paramSchema = """{"path":"string"}"""

    override suspend fun execute(params: JsonObject): String {
        val path = params["path"]?.jsonPrimitive?.content ?: return "ERROR: path required"
        val file = java.io.File(if (path.startsWith("/")) path else "/workspace/$path")
        return if (file.exists()) file.readText().take(8000)
        else "ERROR: File not found: $path"
    }
}

class FileWriteTool : Tool() {
    override val id = "file_write"
    override val name = "Write File"
    override val description = "Write content to a file in the workspace."
    override val paramSchema = """{"path":"string","content":"string"}"""

    override suspend fun execute(params: JsonObject): String {
        val path = params["path"]?.jsonPrimitive?.content ?: return "ERROR: path required"
        val content = params["content"]?.jsonPrimitive?.content ?: ""
        val file = java.io.File(if (path.startsWith("/")) path else "/workspace/$path")
        file.parentFile?.mkdirs()
        file.writeText(content)
        return "Written ${content.length} chars to $path"
    }
}

// ─── 通知用户 ─────────────────────────────────────────────

class NotifyUserTool(
    private val context: Context,
    private val db: NexusDatabase,
) : Tool() {
    override val id = "notify_user"
    override val name = "Notify User"
    override val description = """
        Send a message to the user. Use this to report results, ask for decisions,
        or alert about important events. This is how Agent communicates with the user.
    """.trimIndent()
    override val paramSchema = """{"message":"string","requires_action":"boolean?","priority":"low|default|high?"}"""

    override suspend fun execute(params: JsonObject): String {
        val message = params["message"]?.jsonPrimitive?.content ?: return "ERROR: message required"
        val requiresAction = params["requires_action"]?.jsonPrimitive?.boolean ?: false

        // 1. 写入 DB（UI 实时显示）
        db.messageDao().insert(
            ai.nexus.data.model.AgentMessage(
                sessionId = 0,
                direction = ai.nexus.data.model.MessageDirection.AGENT_TO_USER,
                content = message,
                requiresAction = requiresAction,
            )
        )

        // 2. 同时通过 ntfy.sh 推送到手机（即使 App 在后台）
        try {
            val url = URL("https://ntfy.sh/nexus-agent")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Title", "Nexus Agent")
            conn.setRequestProperty("User-Agent", "Nexus/1.0")
            conn.doOutput = true
            conn.outputStream.write(message.toByteArray())
            conn.disconnect()
        } catch (_: Exception) {}

        return "NOTIFIED: $message"
    }
}

// ─── 记忆操作 ─────────────────────────────────────────────

class MemorySearchTool(private val db: NexusDatabase) : Tool() {
    override val id = "memory_search"
    override val name = "Search Memory"
    override val description = "Search Agent's memory for relevant past knowledge or events."
    override val paramSchema = """{"keyword":"string","limit":"number?"}"""

    override suspend fun execute(params: JsonObject): String {
        val keyword = params["keyword"]?.jsonPrimitive?.content ?: return "ERROR: keyword required"
        val limit = params["limit"]?.jsonPrimitive?.int ?: 5
        val memories = db.memoryDao().search(keyword, limit)
        return memories.joinToString("\n---\n") { "[${it.type}] ${it.content}" }
            .ifEmpty { "No memories found for: $keyword" }
    }
}

class MemoryWriteTool(private val db: NexusDatabase) : Tool() {
    override val id = "memory_write"
    override val name = "Write Memory"
    override val description = "Store important information in Agent's long-term memory."
    override val paramSchema = """{"content":"string","type":"EPISODIC|SEMANTIC|PROCEDURAL","importance":"number?"}"""

    override suspend fun execute(params: JsonObject): String {
        val content = params["content"]?.jsonPrimitive?.content ?: return "ERROR: content required"
        val type = try {
            ai.nexus.data.model.MemoryType.valueOf(
                params["type"]?.jsonPrimitive?.content ?: "SEMANTIC"
            )
        } catch (_: Exception) { ai.nexus.data.model.MemoryType.SEMANTIC }
        val importance = params["importance"]?.jsonPrimitive?.float ?: 0.6f

        db.memoryDao().insert(ai.nexus.data.model.Memory(
            type = type,
            content = content,
            importance = importance,
        ))
        return "Memory stored: ${content.take(80)}"
    }
}

// ─── 平台专用工具 ─────────────────────────────────────────

class NtfyPushTool : Tool() {
    override val id = "ntfy_push"
    override val name = "Push Notification (ntfy.sh)"
    override val description = "Push a notification to any ntfy.sh topic. Useful for alerts."
    override val paramSchema = """{"topic":"string","message":"string","title":"string?"}"""

    override suspend fun execute(params: JsonObject): String {
        val topic = params["topic"]?.jsonPrimitive?.content ?: "nexus-agent"
        val message = params["message"]?.jsonPrimitive?.content ?: ""
        val title = params["title"]?.jsonPrimitive?.content ?: "Nexus"

        val conn = URL("https://ntfy.sh/$topic").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Title", title)
        conn.setRequestProperty("User-Agent", "Nexus/1.0")
        conn.connectTimeout = 8000
        conn.doOutput = true
        conn.outputStream.write(message.toByteArray())
        val status = conn.responseCode
        conn.disconnect()
        return "ntfy push HTTP $status to $topic"
    }
}

class GitHubApiTool : Tool() {
    override val id = "github_api"
    override val name = "GitHub API"
    override val description = "Query GitHub API. Get repo info, releases, issues, search repos."
    override val paramSchema = """{"path":"string","e.g":"/repos/owner/repo/releases/latest"}"""

    override suspend fun execute(params: JsonObject): String {
        val path = params["path"]?.jsonPrimitive?.content ?: return "ERROR: path required"
        val conn = URL("https://api.github.com$path").openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "Nexus-Agent/1.0")
        conn.connectTimeout = 8000
        return conn.inputStream.bufferedReader().readText().take(4000)
            .also { conn.disconnect() }
    }
}

class ArxivSearchTool : Tool() {
    override val id = "arxiv_search"
    override val name = "arXiv Search"
    override val description = "Search arXiv for latest research papers. category: cs.AI, cs.LG, cs.CL etc."
    override val paramSchema = """{"query":"string","category":"string?","limit":"number?"}"""

    override suspend fun execute(params: JsonObject): String {
        val query = params["query"]?.jsonPrimitive?.content ?: ""
        val cat = params["category"]?.jsonPrimitive?.content ?: "cs.AI"
        val limit = params["limit"]?.jsonPrimitive?.int ?: 5
        val q = if (query.isNotEmpty()) "all:$query+AND+cat:$cat" else "cat:$cat"
        val url = "https://export.arxiv.org/api/query?search_query=$q&max_results=$limit&sortBy=submittedDate&sortOrder=descending"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        return conn.inputStream.bufferedReader().readText().take(5000)
            .also { conn.disconnect() }
    }
}

class PypiLookupTool : Tool() {
    override val id = "pypi_lookup"
    override val name = "PyPI Package Lookup"
    override val description = "Get latest version and info for a Python package on PyPI."
    override val paramSchema = """{"package":"string"}"""

    override suspend fun execute(params: JsonObject): String {
        val pkg = params["package"]?.jsonPrimitive?.content ?: return "ERROR: package required"
        val conn = URL("https://pypi.org/pypi/$pkg/json").openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        val body = Json.decodeFromString<JsonObject>(conn.inputStream.bufferedReader().readText())
        conn.disconnect()
        val info = body["info"]?.jsonObject
        return buildString {
            appendLine("Package: ${info?.get("name")?.jsonPrimitive?.content}")
            appendLine("Version: ${info?.get("version")?.jsonPrimitive?.content}")
            appendLine("Summary: ${info?.get("summary")?.jsonPrimitive?.content}")
        }
    }
}
