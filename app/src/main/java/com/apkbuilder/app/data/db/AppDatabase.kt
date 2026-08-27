package com.apkbuilder.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BuildHistoryDao {
    @Query("SELECT * FROM build_history ORDER BY buildDate DESC")
    fun getAll(): Flow<List<BuildHistoryEntity>>

    @Insert
    suspend fun insert(item: BuildHistoryEntity): Long

    @Query("DELETE FROM build_history")
    suspend fun clearAll()

    @Query("SELECT * FROM build_history WHERE id = :id")
    suspend fun getById(id: Long): BuildHistoryEntity?
}

@Database(entities = [BuildHistoryEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun buildHistoryDao(): BuildHistoryDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "apk_builder_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
