package ai.nexus

import android.app.Application
import androidx.work.*
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class NexusApp : Application() {
    override fun onCreate() {
        super.onCreate()
        schedulePeriodicWork()
    }

    // WorkManager 兜底：即使 ForegroundService 被系统杀死
    // 每 15 分钟用 WorkManager 确保 AgentService 还活着
    private fun schedulePeriodicWork() {
        val req = PeriodicWorkRequestBuilder<AgentKeepaliveWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "nexus_keepalive",
            ExistingPeriodicWorkPolicy.KEEP,
            req,
        )
    }
}
