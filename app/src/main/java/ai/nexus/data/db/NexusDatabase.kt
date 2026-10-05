package ai.nexus.data.db

import ai.nexus.data.model.*
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Database(
    entities = [Goal::class, Task::class, Memory::class, SensorEvent::class,
                AgentSession::class, ToolDef::class, AgentMessage::class],
    version = 1,
    exportSchema = false,
)
abstract class NexusDatabase : RoomDatabase() {
    abstract fun goalDao(): GoalDao
    abstract fun taskDao(): TaskDao
    abstract fun memoryDao(): MemoryDao
    abstract fun sensorDao(): SensorDao
    abstract fun sessionDao(): SessionDao
    abstract fun toolDao(): ToolDao
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile private var INSTANCE: NexusDatabase? = null
        fun getInstance(context: android.content.Context): NexusDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, NexusDatabase::class.java, "nexus.db")
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}

@Dao interface GoalDao {
    @Query("SELECT * FROM goals WHERE statusStr = 'ACTIVE' ORDER BY createdAt DESC")
    fun activeGoals(): Flow<List<Goal>>
    @Query("SELECT * FROM goals ORDER BY createdAt DESC")
    fun allGoals(): Flow<List<Goal>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(g: Goal): Long
    @Update suspend fun update(g: Goal)
    @Query("UPDATE goals SET statusStr = :s WHERE id = :id") suspend fun updateStatus(id: Long, s: String)
    @Query("UPDATE goals SET lastRunAt = :t, runCount = runCount+1 WHERE id = :id")
    suspend fun markRan(id: Long, t: Long = System.currentTimeMillis())
    @Delete suspend fun delete(g: Goal)
}

@Dao interface TaskDao {
    @Query("SELECT * FROM tasks WHERE goalId = :gid ORDER BY step ASC") suspend fun tasksForGoal(gid: Long): List<Task>
    @Query("SELECT * FROM tasks WHERE statusStr IN ('PENDING','RUNNING') ORDER BY id ASC") suspend fun pendingTasks(): List<Task>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(t: Task): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(ts: List<Task>)
    @Update suspend fun update(t: Task)
    @Query("DELETE FROM tasks WHERE goalId = :gid") suspend fun deleteForGoal(gid: Long)
}

@Dao interface MemoryDao {
    @Query("SELECT * FROM memories WHERE typeStr = :t ORDER BY importance DESC, lastAccessedAt DESC LIMIT :limit")
    suspend fun query(t: String, limit: Int = 20): List<Memory>
    @Query("SELECT * FROM memories WHERE content LIKE '%' || :kw || '%' ORDER BY importance DESC LIMIT :limit")
    suspend fun search(kw: String, limit: Int = 10): List<Memory>
    @Query("SELECT * FROM memories ORDER BY createdAt DESC LIMIT :limit")
    fun recentMemories(limit: Int = 50): Flow<List<Memory>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(m: Memory): Long
    @Update suspend fun update(m: Memory)
    @Query("UPDATE memories SET accessCount = accessCount+1, lastAccessedAt = :t WHERE id = :id")
    suspend fun markAccessed(id: Long, t: Long = System.currentTimeMillis())
}

@Dao interface SensorDao {
    @Query("SELECT * FROM sensor_events WHERE processed = 0 ORDER BY createdAt ASC") suspend fun unprocessedEvents(): List<SensorEvent>
    @Query("SELECT * FROM sensor_events ORDER BY createdAt DESC LIMIT :limit") fun recentEvents(limit: Int = 100): Flow<List<SensorEvent>>
    @Insert suspend fun insert(e: SensorEvent): Long
    @Query("UPDATE sensor_events SET processed = 1 WHERE id = :id") suspend fun markProcessed(id: Long)
}

@Dao interface SessionDao {
    @Query("SELECT * FROM agent_sessions ORDER BY startedAt DESC LIMIT :limit") fun recentSessions(limit: Int = 50): Flow<List<AgentSession>>
    @Query("SELECT * FROM agent_sessions WHERE goalId = :gid ORDER BY startedAt DESC") suspend fun sessionsForGoal(gid: Long): List<AgentSession>
    @Insert suspend fun insert(s: AgentSession): Long
    @Update suspend fun update(s: AgentSession)
}

@Dao interface ToolDao {
    @Query("SELECT * FROM tools WHERE enabled = 1") fun enabledTools(): Flow<List<ToolDef>>
    @Query("SELECT * FROM tools WHERE enabled = 1") suspend fun enabledToolsOnce(): List<ToolDef>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertIfAbsent(t: ToolDef)
    @Update suspend fun update(t: ToolDef)
}

@Dao interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY createdAt DESC LIMIT :limit") fun recentMessages(limit: Int = 100): Flow<List<AgentMessage>>
    @Query("SELECT * FROM messages WHERE requiresAction = 1 AND actionTaken = '' ORDER BY createdAt DESC") fun pendingActions(): Flow<List<AgentMessage>>
    @Insert suspend fun insert(m: AgentMessage): Long
    @Update suspend fun update(m: AgentMessage)
}
