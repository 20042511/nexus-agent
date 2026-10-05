package ai.nexus

import ai.nexus.service.AgentService
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager 兜底 Worker
 *
 * 当 AgentService 被系统资源回收后，
 * WorkManager 每 15 分钟自动将其拉起。
 * 双重保险：ForegroundService + WorkManager。
 */
@HiltWorker
class AgentKeepaliveWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // 检查 AgentService 是否还活着，不在就拉起
        ContextCompat.startForegroundService(
            applicationContext,
            Intent(applicationContext, AgentService::class.java),
        )
        return Result.success()
    }
}
