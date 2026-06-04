package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tasks")
data class DbTask(
    @PrimaryKey val id: String,
    val title: String,
    val category: String, // "DAILY", "SAGE_TRUNK_DAILY", "SAGE_TRUNK_WEEKLY"
    val isCompleted: Boolean,
    val lastCompletedTimestamp: Long = 0L
)

@Entity(tableName = "timers")
data class DbTimer(
    @PrimaryKey val id: String, // "7star", "recasting"
    val title: String,
    val targetTimestamp: Long
)

@Entity(tableName = "cultivation_stats")
data class DbStats(
    @PrimaryKey val id: String = "singleton_stats",
    val strength: Int = 100000,
    val agility: Int = 100000,
    val physique: Int = 100000,
    val intellect: Int = 100000,
    val elemental: Int = 50000,
    
    val baselineStrength: Int = 100000,
    val baselineAgility: Int = 100000,
    val baselinePhysique: Int = 100000,
    val baselineIntellect: Int = 100000,
    val baselineElemental: Int = 50000,
    
    val lastUpdated: Long = 0L
)

@Dao
interface CompanionDao {
    @Query("SELECT * FROM tasks")
    fun getAllTasksFlow(): Flow<List<DbTask>>

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasks(): List<DbTask>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: DbTask)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<DbTask>)

    @Query("UPDATE tasks SET isCompleted = :isCompleted, lastCompletedTimestamp = :timestamp WHERE id = :id")
    suspend fun updateTaskStatus(id: String, isCompleted: Boolean, timestamp: Long)

    @Query("UPDATE tasks SET isCompleted = 0")
    suspend fun resetAllTasks()

    @Query("SELECT * FROM timers")
    fun getAllTimersFlow(): Flow<List<DbTimer>>

    @Query("SELECT * FROM timers WHERE id = :id")
    suspend fun getTimerById(id: String): DbTimer?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimer(timer: DbTimer)

    @Query("SELECT * FROM cultivation_stats WHERE id = :id")
    suspend fun getStatsById(id: String): DbStats?

    @Query("SELECT * FROM cultivation_stats")
    fun getStatsFlow(): Flow<List<DbStats>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStats(stats: DbStats)
}

@Database(entities = [DbTask::class, DbTimer::class, DbStats::class], version = 2, exportSchema = false)
abstract class CompanionDatabase : RoomDatabase() {
    abstract fun dao(): CompanionDao

    companion object {
        @Volatile
        private var INSTANCE: CompanionDatabase? = null

        fun getDatabase(context: Context): CompanionDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CompanionDatabase::class.java,
                    "companion_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class CompanionRepository(private val dao: CompanionDao) {
    val allTasks: Flow<List<DbTask>> = dao.getAllTasksFlow()
    val allTimers: Flow<List<DbTimer>> = dao.getAllTimersFlow()
    val allStats: Flow<List<DbStats>> = dao.getStatsFlow()

    suspend fun insertTask(task: DbTask) {
        dao.insertTask(task)
    }

    suspend fun insertTasks(tasks: List<DbTask>) {
        dao.insertTasks(tasks)
    }

    suspend fun updateTaskStatus(id: String, isCompleted: Boolean, timestamp: Long) {
        dao.updateTaskStatus(id, isCompleted, timestamp)
    }

    suspend fun resetAllTasks() {
        dao.resetAllTasks()
    }

    suspend fun getTimerById(id: String): DbTimer? {
        return dao.getTimerById(id)
    }

    suspend fun insertTimer(timer: DbTimer) {
        dao.insertTimer(timer)
    }

    suspend fun getAllTasksDirect(): List<DbTask> {
        return dao.getAllTasks()
    }

    suspend fun getStatsById(id: String): DbStats? {
        return dao.getStatsById(id)
    }

    suspend fun insertStats(stats: DbStats) {
        dao.insertStats(stats)
    }
}
