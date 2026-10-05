package ai.nexus.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * BootReceiver — 开机自启
 *
 * 手机重启后，自动拉起 AgentService。
 * Agent 不需要用户主动打开 App 才能运行。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AgentService::class.java),
            )
        }
    }
}
