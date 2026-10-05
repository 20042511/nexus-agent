package ai.nexus.sensor

import ai.nexus.data.db.NexusDatabase
import ai.nexus.data.model.SensorEvent
import android.content.Context
import kotlinx.coroutines.*
import java.security.MessageDigest

/**
 * SensorHub — Agent 的感知系统
 *
 * 持续运行多路传感器，检测到变化时写入 sensor_events 表。
 * AgentService 的主循环会消费这些事件并触发相应 Goal。
 *
 * 关键设计：传感器与执行解耦。
 * 传感器只负责"发现变化"，不直接触发任何逻辑。
 */
class SensorHub(
    private val context: Context,
    private val db: NexusDatabase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sensors = mutableListOf<BaseSensor>()

    fun start() {
        // 注册所有传感器
        sensors += HttpPollSensor(db, scope)
        sensors += TimeSensor(db, scope)
        sensors += NotificationSensor(context, db)

        sensors.forEach { it.start() }
    }

    fun stop() {
        sensors.forEach { it.stop() }
        scope.cancel()
    }

    // 动态添加传感器（用户添加新 Goal 时）
    fun addHttpWatch(id: String, url: String, intervalMs: Long = 300_000L) {
        val sensor = HttpPollSensor(db, scope)
        sensor.addTarget(id, url, intervalMs)
        sensor.start()
        sensors += sensor
    }
}

// ─── 传感器基类 ──────────────────────────────────────────

abstract class BaseSensor {
    abstract fun start()
    abstract fun stop()

    protected suspend fun emit(db: NexusDatabase, sensorId: String, eventType: String, payload: String) {
        db.sensorDao().insert(SensorEvent(
            sensorId = sensorId,
            eventType = eventType,
            payload = payload,
        ))
    }
}

// ─── HTTP 轮询传感器 ─────────────────────────────────────

class HttpPollSensor(
    private val db: NexusDatabase,
    private val scope: CoroutineScope,
) : BaseSensor() {

    data class Target(
        val id: String,
        val url: String,
        val intervalMs: Long,
        var lastFingerprint: String = "",
        var lastCheckMs: Long = 0,
    )

    private val targets = mutableListOf<Target>()
    private var job: Job? = null

    // 内置监视目标
    init {
        addTarget(
            "github.ollama.release",
            "https://api.github.com/repos/ollama/ollama/releases/latest",
            600_000L  // 10分钟
        )
        addTarget(
            "arxiv.cs.AI",
            "https://export.arxiv.org/api/query?search_query=cat:cs.AI&max_results=3&sortBy=submittedDate&sortOrder=descending",
            1_800_000L  // 30分钟
        )
        addTarget(
            "github.rikkahub.release",
            "https://api.github.com/repos/rikkahub/rikkahub/releases/latest",
            600_000L
        )
    }

    fun addTarget(id: String, url: String, intervalMs: Long) {
        targets.add(Target(id, url, intervalMs))
    }

    override fun start() {
        job = scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                targets.filter { now - it.lastCheckMs >= it.intervalMs }.forEach { target ->
                    checkTarget(target, now)
                }
                delay(10_000)  // 每10秒检查一次是否有目标需要轮询
            }
        }
    }

    override fun stop() { job?.cancel() }

    private suspend fun checkTarget(target: Target, now: Long) {
        target.lastCheckMs = now
        try {
            val content = fetch(target.url)
            val fp = fingerprint(content)
            if (fp != target.lastFingerprint && target.lastFingerprint.isNotEmpty()) {
                emit(db, target.id, "CHANGED",
                    """{"url":"${target.url}","old_fp":"${target.lastFingerprint}","new_fp":"$fp","preview":${
                        kotlinx.serialization.json.Json.encodeToString(
                            kotlinx.serialization.builtins.serializer<String>(),
                            content.take(500)
                        )
                    }}""")
            }
            target.lastFingerprint = fp
        } catch (e: Exception) {
            // 网络错误不记录（太频繁）
        }
    }

    private fun fetch(url: String): String {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.setRequestProperty("User-Agent", "Nexus-Agent/1.0")
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        return conn.inputStream.bufferedReader().readText().also { conn.disconnect() }
    }

    private fun fingerprint(content: String): String {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(content.toByteArray()).take(6)
            .joinToString("") { "%02x".format(it) }
    }
}

// ─── 时间传感器（Cron）──────────────────────────────────

class TimeSensor(
    private val db: NexusDatabase,
    private val scope: CoroutineScope,
) : BaseSensor() {

    private var job: Job? = null

    override fun start() {
        job = scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val cal = java.util.Calendar.getInstance()
                val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
                val minute = cal.get(java.util.Calendar.MINUTE)

                // 每整分触发一次时间事件，让 CronScheduler 判断
                emit(db, "time.clock", "TICK",
                    """{"hour":$hour,"minute":$minute,"ts":$now}""")

                // 等到下一分钟的开始
                val secondsLeft = 60 - cal.get(java.util.Calendar.SECOND)
                delay(secondsLeft * 1000L)
            }
        }
    }

    override fun stop() { job?.cancel() }
}

// ─── 通知监听传感器 ──────────────────────────────────────

class NotificationSensor(
    private val context: Context,
    private val db: NexusDatabase,
) : BaseSensor() {
    // 通过 NotificationListenerService 实现
    // 当用户收到特定 App 的通知时，Agent 可以感知并响应
    // 例如：收到微信消息 → Agent 帮你分类/摘要

    override fun start() {
        // NotificationListenerService 由系统管理，这里只做配置
    }

    override fun stop() {}
}
