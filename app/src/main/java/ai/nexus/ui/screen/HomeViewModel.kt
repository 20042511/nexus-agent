package ai.nexus.ui.screen

import ai.nexus.data.db.NexusDatabase
import ai.nexus.data.model.*
import ai.nexus.service.AgentService
import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    private val db: NexusDatabase,
) : AndroidViewModel(application) {

    val messages: StateFlow<List<AgentMessage>> =
        db.messageDao().recentMessages(100)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val goals: StateFlow<List<Goal>> =
        db.goalDao().activeGoals()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val agentStatus: StateFlow<String> = MutableStateFlow("待命中")

    var showingAddGoal by mutableStateOf(false)
        private set

    fun showAddGoal() { showingAddGoal = true }
    fun hideAddGoal() { showingAddGoal = false }

    fun addGoal(title: String, triggerStr: String) {
        viewModelScope.launch {
            val trigger = when {
                triggerStr == "立即执行" -> GoalTrigger.Immediate(once = true)
                triggerStr == "外部事件触发" -> GoalTrigger.OnEvent("manual")
                triggerStr.contains(" ") || triggerStr.contains("*") ->
                    GoalTrigger.Schedule(cron = triggerStr)
                else -> GoalTrigger.Immediate(once = false)
            }

            val goalId = db.goalDao().insert(
                Goal(title = title, description = title, trigger = trigger)
            )

            // 如果是立即执行，触发 AgentService
            if (trigger is GoalTrigger.Immediate) {
                val intent = Intent(getApplication(), AgentService::class.java).apply {
                    action = AgentService.ACTION_GOAL_ADDED
                    putExtra("goalId", goalId)
                }
                ContextCompat.startForegroundService(getApplication(), intent)
            }
        }
        showingAddGoal = false
    }

    fun approve(message: AgentMessage) {
        viewModelScope.launch {
            db.messageDao().update(message.copy(actionTaken = "授权"))
            // 恢复等待授权的 Task 继续执行
            db.taskDao().pendingTasks()
                .filter { it.status == TaskStatus.WAITING_USER }
                .forEach { task ->
                    db.taskDao().update(task.copy(status = TaskStatus.PENDING))
                }
            // 通知 AgentService 继续
            ContextCompat.startForegroundService(
                getApplication(),
                Intent(getApplication(), AgentService::class.java).apply {
                    action = AgentService.ACTION_RUN_NOW
                }
            )
        }
    }

    fun reject(message: AgentMessage) {
        viewModelScope.launch {
            db.messageDao().update(message.copy(actionTaken = "拒绝"))
            db.taskDao().pendingTasks()
                .filter { it.status == TaskStatus.WAITING_USER }
                .forEach { task ->
                    db.taskDao().update(task.copy(status = TaskStatus.FAILED, error = "用户拒绝"))
                }
        }
    }
}
