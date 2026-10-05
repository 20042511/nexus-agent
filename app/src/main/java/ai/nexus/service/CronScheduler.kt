package ai.nexus.service

import ai.nexus.data.db.NexusDatabase
import ai.nexus.data.model.*
import ai.nexus.planner.PlannerEngine

/**
 * CronScheduler — Cron 表达式调度器
 *
 * 每分钟被 TimeSensor 的 TICK 事件触发。
 * 检查所有 Schedule 类型的 Goal，判断是否到了执行时间。
 *
 * 支持标准 5 字段 Cron：分 时 日 月 周
 * "0 8 * * *"  = 每天 8:00
 * "0 9 * * 1"  = 每周一 9:00
 * "*/30 * * * *" = 每 30 分钟
 */
object CronScheduler {

    suspend fun checkAndTrigger(db: NexusDatabase, planner: PlannerEngine) {
        val now = java.util.Calendar.getInstance()
        val minute = now.get(java.util.Calendar.MINUTE)
        val hour = now.get(java.util.Calendar.HOUR_OF_DAY)
        val dayOfMonth = now.get(java.util.Calendar.DAY_OF_MONTH)
        val month = now.get(java.util.Calendar.MONTH) + 1
        val dayOfWeek = now.get(java.util.Calendar.DAY_OF_WEEK) - 1  // 0=Sun

        // 从 DB 拿所有 Schedule 类型的活跃 Goal
        // 逐个检查 cron 表达式是否匹配当前时间
        // 若匹配且今分钟还没跑过 → 触发

        // TODO: 实现完整 Goal 查询
        // 简化版：从 sensor_events 里找 TICK，对比 goal 的 lastRunAt
    }

    /**
     * 解析并判断 cron 表达式是否在给定时间点触发
     * 格式：分 时 日 月 周
     */
    fun matches(cron: String, minute: Int, hour: Int, dom: Int, month: Int, dow: Int): Boolean {
        val parts = cron.trim().split("\\s+".toRegex())
        if (parts.size != 5) return false
        return matchField(parts[0], minute, 0, 59) &&
               matchField(parts[1], hour, 0, 23) &&
               matchField(parts[2], dom, 1, 31) &&
               matchField(parts[3], month, 1, 12) &&
               matchField(parts[4], dow, 0, 7)
    }

    private fun matchField(expr: String, value: Int, min: Int, max: Int): Boolean {
        if (expr == "*") return true

        // */n 步进
        if (expr.startsWith("*/")) {
            val step = expr.substring(2).toIntOrNull() ?: return false
            return value % step == 0
        }

        // 逗号分隔列表
        if (expr.contains(",")) {
            return expr.split(",").any { matchSingle(it, value) }
        }

        // 范围 n-m
        if (expr.contains("-")) {
            val (from, to) = expr.split("-").map { it.toIntOrNull() ?: return false }
            return value in from..to
        }

        return matchSingle(expr, value)
    }

    private fun matchSingle(expr: String, value: Int): Boolean {
        val n = expr.toIntOrNull() ?: return false
        return n == value
    }
}
