package ai.nexus.service

import ai.nexus.data.db.NexusDatabase
import ai.nexus.data.model.*
import ai.nexus.planner.PlannerEngine
import ai.nexus.sensor.SensorHub
import ai.nexus.tool.ToolRegistry
import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AgentService — 这是 Nexus 的心脏
 *
 * 它是 Android ForegroundService：
 * - 开机自启
 * - 不因 App 关闭而停止
 * - 有自己的协程作用域
 * - 维护 Agent 的感知→规划→执行循环
 *
 * 与 RikkaHub 的根本区别：
 * RikkaHub 的 AI 存在于对话里，对话结束即消亡。
 * Nexus 的 AI 存在于 Service 里，只要手机开着就活着。
 */
class AgentService : LifecycleService() {

    companion object {
        const val CHANNEL_ID = "nexus_agent"
        const val NOTIF_ID = 1001
        const val ACTION_STOP = "ai.nexus.STOP"
        const val ACTION_RUN_NOW = "ai.nexus.RUN_NOW"
        const val ACTION_GOAL_ADDED = "ai.nexus.GOAL_ADDED"
    }

    private lateinit var db: NexusDatabase
    private lateinit var sensorHub: SensorHub
    private lateinit var planner: PlannerEngine
    private lateinit var toolRegistry: ToolRegistry

    private val isRunning = AtomicBoolean(false)
    private var agentLoopJob: Job? = null

    // ─── 生命周期 ──────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        db = NexusDatabase.getInstance(this)
        toolRegistry = ToolRegistry(this, db)
        sensorHub = SensorHub(this, db)
        planner = PlannerEngine(this, db, toolRegistry)

        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Agent 初始化中..."))

        lifecycleScope.launch {
            initialize()
            startAgentLoop()
            startSensorLoop()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_RUN_NOW -> lifecycleScope.launch { runAgentCycle() }
            ACTION_GOAL_ADDED -> {
                val goalId = intent.getLongExtra("goalId", -1)
                if (goalId > 0) lifecycleScope.launch { triggerGoal(goalId) }
            }
        }
        return START_STICKY  // 被系统杀死后自动重启
    }

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)

    // ─── 初始化 ────────────────────────────────────────────

    private suspend fun initialize() {
        // 注册内置工具
        toolRegistry.registerBuiltIns()

        // 恢复上次未完成的任务
        val pendingTasks = db.taskDao().pendingTasks()
        if (pendingTasks.isNotEmpty()) {
            updateNotification("恢复 ${pendingTasks.size} 个未完成任务")
            // 重置 RUNNING → PENDING（Service 重启了，RUNNING 状态是脏的）
            pendingTasks.filter { it.status == TaskStatus.RUNNING }.forEach { task ->
                db.taskDao().update(task.copy(status = TaskStatus.PENDING))
            }
        }

        // 监听需要用户决策的消息
        lifecycleScope.launch {
            db.messageDao().pendingActions().collectLatest { msgs ->
                if (msgs.isNotEmpty()) {
                    updateNotification("需要你的决策：${msgs.first().content.take(40)}")
                }
            }
        }
    }

    // ─── Agent 主循环 ──────────────────────────────────────

    private fun startAgentLoop() {
        agentLoopJob = lifecycleScope.launch {
            while (isActive) {
                runAgentCycle()
                delay(30_000)  // 每 30 秒检查一次
            }
        }
    }

    private suspend fun runAgentCycle() {
        if (isRunning.getAndSet(true)) return
        try {
            updateNotification("Agent 运行中...")

            // 1. 处理传感器事件
            val events = db.sensorDao().unprocessedEvents()
            events.forEach { event ->
                handleSensorEvent(event)
                db.sensorDao().markProcessed(event.id)
            }

            // 2. 检查所有活跃目标的调度
            val goals = db.goalDao().activeGoals().let {
                // 直接查一次，不 collect
                db.goalDao().let { dao ->
                    withContext(Dispatchers.IO) {
                        // 用同步方式拿一次当前值
                        emptyList<Goal>() // placeholder，实际用 DAO 的同步方法
                    }
                }
            }
            // 检查 Schedule 类型 Goal 是否到点
            checkScheduledGoals()

            // 3. 执行待处理任务
            val pendingTasks = db.taskDao().pendingTasks()
            if (pendingTasks.isNotEmpty()) {
                executePendingTasks(pendingTasks)
            }

            updateNotification("待命中 — ${pendingTasks.size} 个任务完成")

        } finally {
            isRunning.set(false)
        }
    }

    // ─── 调度检查 ──────────────────────────────────────────

    private suspend fun checkScheduledGoals() {
        // 从 DB 拿所有活跃的 Schedule 类型 Goal
        // 判断 cron 是否触发
        // 若触发 → 调用 planner 分解为 Tasks
        // （cron 解析逻辑在 CronScheduler 里）
        CronScheduler.checkAndTrigger(db, planner)
    }

    private suspend fun triggerGoal(goalId: Long) {
        // 立即触发某个 Goal
        planner.decomposeGoal(goalId)
    }

    // ─── 传感器事件处理 ────────────────────────────────────

    private suspend fun handleSensorEvent(event: SensorEvent) {
        // 找到监听这个事件类型的 Goal
        // 触发对应的 Plan
        planner.handleEvent(event)
    }

    // ─── 任务执行 ──────────────────────────────────────────

    private suspend fun executePendingTasks(tasks: List<Task>) {
        for (task in tasks) {
            if (task.status != TaskStatus.PENDING) continue

            // 更新为 RUNNING
            db.taskDao().update(task.copy(
                status = TaskStatus.RUNNING,
                startedAt = System.currentTimeMillis()
            ))

            try {
                val result = planner.executeTask(task)
                db.taskDao().update(task.copy(
                    status = TaskStatus.DONE,
                    output = result,
                    finishedAt = System.currentTimeMillis()
                ))
            } catch (e: Exception) {
                db.taskDao().update(task.copy(
                    status = TaskStatus.FAILED,
                    error = e.message ?: "Unknown error",
                    finishedAt = System.currentTimeMillis()
                ))
            }
        }
    }

    // ─── 传感器循环（独立于 Agent 循环）──────────────────

    private fun startSensorLoop() {
        lifecycleScope.launch {
            sensorHub.start()
        }
    }

    // ─── 通知 ──────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Nexus Agent",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Agent 后台运行状态"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(status: String): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, AgentService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Nexus Agent")
            .setContentText(status)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, "停止", stopIntent)
            .build()
    }

    private fun updateNotification(status: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(status))
    }
}
