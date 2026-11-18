package com.notifnix.app

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

@Entity(tableName = "simulated_sms")
data class SimulatedSmsEntity(
    @PrimaryKey val id: String,
    val sender: String,
    val phoneNumber: String,
    val body: String,
    val folder: String,
    val timestamp: Long
)

@Dao
interface SimulatedSmsDao {
    @Query("SELECT * FROM simulated_sms ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<SimulatedSmsEntity>>
    @Upsert suspend fun upsertAll(items: List<SimulatedSmsEntity>)
    @Query("DELETE FROM simulated_sms WHERE id = :id")
    suspend fun delete(id: String)
    @Query("DELETE FROM simulated_sms")
    suspend fun clear()
}

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

@Database(entities = [ScenarioEntity::class, SimulatedSmsEntity::class], version = 2, exportSchema = false)
abstract class ScenarioDatabase : RoomDatabase() {
    abstract fun scenarios(): ScenarioDao
    abstract fun smsMessages(): SimulatedSmsDao
    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""CREATE TABLE IF NOT EXISTS simulated_sms (id TEXT NOT NULL, sender TEXT NOT NULL, phoneNumber TEXT NOT NULL, body TEXT NOT NULL, folder TEXT NOT NULL, timestamp INTEGER NOT NULL, PRIMARY KEY(id))""")
            }
        }
        @Volatile private var INSTANCE: ScenarioDatabase? = null
        fun get(context: Context): ScenarioDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, ScenarioDatabase::class.java, "notifnix.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { INSTANCE = it }
        }
    }
}
