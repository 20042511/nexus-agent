package ai.nexus.service

import ai.nexus.data.db.NexusDatabase
import ai.nexus.planner.PlannerEngine
import java.util.Calendar

/**
 * CronScheduler — Cron 表达式调度器
 * 格式: 分 时 日 月 周  (标准5字段)
 * 示例: "0 8 * * *" = 每天8点
 */
object CronScheduler {

    suspend fun checkAndTrigger(db: NexusDatabase, planner: PlannerEngine) {
        val cal = Calendar.getInstance()
        val minute = cal.get(Calendar.MINUTE)
        val hour   = cal.get(Calendar.HOUR_OF_DAY)
        val dom    = cal.get(Calendar.DAY_OF_MONTH)
        val month  = cal.get(Calendar.MONTH) + 1
        val dow    = cal.get(Calendar.DAY_OF_WEEK) - 1
        // TODO: 查 DB 中 Schedule 类型的 Goal 并判断是否触发
        _ = minute + hour + dom + month + dow  // suppress unused
    }

    fun matches(cron: String, minute: Int, hour: Int, dom: Int, month: Int, dow: Int): Boolean {
        val parts = cron.trim().split("\\s+".toRegex())
        if (parts.size != 5) return false
        return matchField(parts[0], minute) &&
               matchField(parts[1], hour) &&
               matchField(parts[2], dom) &&
               matchField(parts[3], month) &&
               matchField(parts[4], dow)
    }

    private fun matchField(expr: String, value: Int): Boolean {
        if (expr == "*") return true
        if (expr.startsWith("*/")) {
            val step = expr.substring(2).toIntOrNull() ?: return false
            return value % step == 0
        }
        if (expr.contains(",")) return expr.split(",").any { it.trim().toIntOrNull() == value }
        if (expr.contains("-")) {
            val parts = expr.split("-")
            val from = parts.getOrNull(0)?.toIntOrNull() ?: return false
            val to   = parts.getOrNull(1)?.toIntOrNull() ?: return false
            return value in from..to
        }
        return expr.toIntOrNull() == value
    }
}
