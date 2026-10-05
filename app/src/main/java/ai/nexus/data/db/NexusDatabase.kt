package ai.nexus.data.db

import ai.nexus.data.model.*
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Database(
    entities = [
        Goal::class,
        Task::class,
        Memory::class,
        SensorEvent::class,
        AgentSession::class,
        ToolDef::class,
        AgentMessage::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class NexusDatabase : RoomDatabase() {
    abstract fun goalDao(): GoalDao
    abstract fun taskDao(): TaskDao
    abstract fun memoryDao(): MemoryDao
    abstract fun sensorDao(): SensorDao
    abstract fun sessionDao(): SessionDao
    abstract fun toolDao(): ToolDao
    abstract fun messageDao(): MessageDao
}

// ─── DAOs ────────────────────────────────────────────────

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE status = 'ACTIVE' ORDER BY createdAt DESC")
    fun activeGoals(): Flow<List<Goal>>

    @Query("SELECT * FROM goals ORDER BY createdAt DESC")
    fun allGoals(): Flow<List<Goal>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: Goal): Long

    @Update
    suspend fun update(goal: Goal)

    @Query("UPDATE goals SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: GoalStatus)

    @Query("UPDATE goals SET lastRunAt = :time, runCount = runCount + 1 WHERE id = :id")
    suspend fun markRan(id: Long, time: Long = System.currentTimeMillis())

    @Delete
    suspend fun delete(goal: Goal)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE goalId = :goalId ORDER BY step ASC")
    suspend fun tasksForGoal(goalId: Long): List<Task>

    @Query("SELECT * FROM tasks WHERE status = 'PENDING' OR status = 'RUNNING' ORDER BY id ASC")
    suspend fun pendingTasks(): List<Task>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: Task): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<Task>)

    @Update
    suspend fun update(task: Task)

    @Query("DELETE FROM tasks WHERE goalId = :goalId")
    suspend fun deleteForGoal(goalId: Long)
}

@Dao
interface MemoryDao {
    @Query("""
        SELECT * FROM memories 
        WHERE type = :type 
        ORDER BY importance DESC, lastAccessedAt DESC 
        LIMIT :limit
    """)
    suspend fun query(type: MemoryType, limit: Int = 20): List<Memory>

    @Query("""
        SELECT * FROM memories 
        WHERE content LIKE '%' || :keyword || '%' 
        ORDER BY importance DESC 
        LIMIT :limit
    """)
    suspend fun search(keyword: String, limit: Int = 10): List<Memory>

    @Query("SELECT * FROM memories ORDER BY createdAt DESC LIMIT :limit")
    fun recentMemories(limit: Int = 50): Flow<List<Memory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: Memory): Long

    @Update
    suspend fun update(memory: Memory)

    @Query("UPDATE memories SET accessCount = accessCount + 1, lastAccessedAt = :time WHERE id = :id")
    suspend fun markAccessed(id: Long, time: Long = System.currentTimeMillis())

    @Query("DELETE FROM memories WHERE importance < 0.2 AND createdAt < :before")
    suspend fun pruneOld(before: Long)
}

@Dao
interface SensorDao {
    @Query("SELECT * FROM sensor_events WHERE processed = 0 ORDER BY createdAt ASC")
    suspend fun unprocessedEvents(): List<SensorEvent>

    @Query("SELECT * FROM sensor_events ORDER BY createdAt DESC LIMIT :limit")
    fun recentEvents(limit: Int = 100): Flow<List<SensorEvent>>

    @Insert
    suspend fun insert(event: SensorEvent): Long

    @Query("UPDATE sensor_events SET processed = 1 WHERE id = :id")
    suspend fun markProcessed(id: Long)

    @Query("DELETE FROM sensor_events WHERE createdAt < :before AND processed = 1")
    suspend fun pruneOld(before: Long)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM agent_sessions ORDER BY startedAt DESC LIMIT :limit")
    fun recentSessions(limit: Int = 50): Flow<List<AgentSession>>

    @Query("SELECT * FROM agent_sessions WHERE goalId = :goalId ORDER BY startedAt DESC")
    suspend fun sessionsForGoal(goalId: Long): List<AgentSession>

    @Insert
    suspend fun insert(session: AgentSession): Long

    @Update
    suspend fun update(session: AgentSession)
}

@Dao
interface ToolDao {
    @Query("SELECT * FROM tools WHERE enabled = 1")
    fun enabledTools(): Flow<List<ToolDef>>

    @Query("SELECT * FROM tools WHERE enabled = 1")
    suspend fun enabledToolsOnce(): List<ToolDef>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(tool: ToolDef)

    @Update
    suspend fun update(tool: ToolDef)

    @Query("UPDATE tools SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY createdAt DESC LIMIT :limit")
    fun recentMessages(limit: Int = 100): Flow<List<AgentMessage>>

    @Query("SELECT * FROM messages WHERE requiresAction = 1 AND actionTaken = '' ORDER BY createdAt DESC")
    fun pendingActions(): Flow<List<AgentMessage>>

    @Insert
    suspend fun insert(msg: AgentMessage): Long

    @Update
    suspend fun update(msg: AgentMessage)
}
