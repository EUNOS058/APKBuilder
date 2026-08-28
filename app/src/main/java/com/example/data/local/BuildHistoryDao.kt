package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BuildHistoryDao {
    @Query("SELECT * FROM build_history ORDER BY timestamp DESC")
    fun getAllBuilds(): Flow<List<BuildHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBuild(build: BuildHistoryEntity): Long

    @Update
    suspend fun updateBuild(build: BuildHistoryEntity)

    @Query("DELETE FROM build_history WHERE id = :id")
    suspend fun deleteBuildById(id: Int)

    @Query("DELETE FROM build_history")
    suspend fun clearAll()
}
