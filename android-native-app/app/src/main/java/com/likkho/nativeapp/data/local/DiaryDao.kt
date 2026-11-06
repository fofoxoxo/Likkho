package com.likkho.nativeapp.data.local

import androidx.room.*
import com.likkho.nativeapp.data.model.CustomFontItem
import com.likkho.nativeapp.data.model.DiaryLog
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {

    @Query("SELECT * FROM diary_logs WHERE vaultMode = :vaultMode ORDER BY pinned DESC, createdAt DESC")
    fun getAllLogs(vaultMode: String): Flow<List<DiaryLog>>

    @Query("SELECT * FROM diary_logs WHERE vaultMode = :vaultMode ORDER BY pinned DESC, createdAt DESC")
    suspend fun getAllLogsList(vaultMode: String): List<DiaryLog>

    @Query("SELECT * FROM diary_logs WHERE id = :id LIMIT 1")
    suspend fun getLogById(id: String): DiaryLog?

    @Query("""
        SELECT * FROM diary_logs 
        WHERE vaultMode = :vaultMode AND (
            heading LIKE '%' || :query || '%' OR 
            plainPreview LIKE '%' || :query || '%' OR 
            contentHtml LIKE '%' || :query || '%'
        )
        ORDER BY pinned DESC, createdAt DESC
    """)
    fun searchLogs(vaultMode: String, query: String): Flow<List<DiaryLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLog(log: DiaryLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLogs(logs: List<DiaryLog>)

    @Query("DELETE FROM diary_logs WHERE id = :id")
    suspend fun deleteLogById(id: String)

    @Query("UPDATE diary_logs SET pinned = NOT pinned WHERE id = :id")
    suspend fun togglePin(id: String)

    @Query("DELETE FROM diary_logs WHERE vaultMode = :vaultMode")
    suspend fun clearVaultLogs(vaultMode: String)

    @Query("DELETE FROM diary_logs")
    suspend fun clearAllData()

    // Custom Fonts
    @Query("SELECT * FROM custom_fonts WHERE vaultMode = :vaultMode")
    fun getCustomFonts(vaultMode: String): Flow<List<CustomFontItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFont(font: CustomFontItem)

    @Query("DELETE FROM custom_fonts WHERE id = :id")
    suspend fun deleteFontById(id: String)

    @Query("DELETE FROM custom_fonts")
    suspend fun clearAllFonts()
}
