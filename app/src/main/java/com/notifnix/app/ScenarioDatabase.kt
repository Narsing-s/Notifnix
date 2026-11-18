package com.notifnix.app

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "scenarios")
data class ScenarioEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val detail: String,
    val createdAt: Long = System.currentTimeMillis(),
    val scheduledAt: Long? = null,
    val favorite: Boolean = false
)

@Dao
interface ScenarioDao {
    @Query("SELECT * FROM scenarios ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ScenarioEntity>>
    @Upsert suspend fun upsert(item: ScenarioEntity)
    @Upsert suspend fun upsertAll(items: List<ScenarioEntity>)
    @Query("DELETE FROM scenarios WHERE id = :id") suspend fun delete(id: String)
    @Query("UPDATE scenarios SET scheduledAt = NULL WHERE id = :id") suspend fun clearScheduledAt(id: String)
    @Query("DELETE FROM scenarios") suspend fun clear()
    @Query("SELECT * FROM scenarios WHERE id = :id LIMIT 1") suspend fun find(id: String): ScenarioEntity?
}

@Database(entities = [ScenarioEntity::class], version = 1, exportSchema = false)
abstract class ScenarioDatabase : RoomDatabase() {
    abstract fun scenarios(): ScenarioDao
    companion object {
        @Volatile private var INSTANCE: ScenarioDatabase? = null
        fun get(context: Context): ScenarioDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, ScenarioDatabase::class.java, "notifnix.db")
                .build().also { INSTANCE = it }
        }
    }
}
