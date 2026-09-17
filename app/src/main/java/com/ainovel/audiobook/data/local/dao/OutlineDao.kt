package com.ainovel.audiobook.data.local.dao

import androidx.room.*
import com.ainovel.audiobook.data.local.entity.OutlineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OutlineDao {
    @Query("SELECT * FROM outlines WHERE novelId = :novelId LIMIT 1")
    fun observeOutline(novelId: String): Flow<OutlineEntity?>

    @Query("SELECT * FROM outlines WHERE novelId = :novelId LIMIT 1")
    suspend fun getOutline(novelId: String): OutlineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateOutline(outline: OutlineEntity)

    @Delete
    suspend fun deleteOutline(outline: OutlineEntity)

    @Query("DELETE FROM outlines WHERE novelId = :novelId")
    suspend fun deleteOutlineByNovelId(novelId: String)
}
