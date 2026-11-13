package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioClipDao {
    @Query("SELECT * FROM audio_clips ORDER BY createdAt DESC")
    fun observeAllClips(): Flow<List<AudioClipEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClip(clip: AudioClipEntity): Long

    @Update
    suspend fun updateClip(clip: AudioClipEntity)

    @Query("UPDATE audio_clips SET title = :newTitle WHERE id = :id")
    suspend fun renameClip(id: Long, newTitle: String)

    @Query("UPDATE audio_clips SET isProtected = :isProtected WHERE id = :id")
    suspend fun setClipProtected(id: Long, isProtected: Boolean)

    @Query("DELETE FROM audio_clips WHERE id = :id")
    suspend fun deleteClipById(id: Long)

    @Query("SELECT * FROM audio_clips WHERE id = :id LIMIT 1")
    suspend fun getClipById(id: Long): AudioClipEntity?

    @Query("DELETE FROM audio_clips WHERE sourceType IN (:sourceTypes)")
    suspend fun deleteClipsBySources(sourceTypes: List<String>)
}

@Dao
interface VoiceProfileDao {
    @Query("SELECT * FROM voice_profiles ORDER BY createdAt DESC")
    fun observeAllProfiles(): Flow<List<VoiceProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: VoiceProfileEntity): Long

    @Query("UPDATE voice_profiles SET profileName = :newName WHERE id = :id")
    suspend fun renameProfile(id: Long, newName: String)

    @Query("UPDATE voice_profiles SET isProtected = :isProtected WHERE id = :id")
    suspend fun setProfileProtected(id: Long, isProtected: Boolean)

    @Query("UPDATE voice_profiles SET remoteVoiceId = :remoteId, cloningStatus = :status WHERE id = :id")
    suspend fun updateRemoteStatus(id: Long, remoteId: String?, status: String)

    @Query("DELETE FROM voice_profiles WHERE id = :id")
    suspend fun deleteProfileById(id: Long)

    @Query("SELECT * FROM voice_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: Long): VoiceProfileEntity?
}

@Dao
interface ApiUsageDao {
    @Query("SELECT * FROM api_usage WHERE dateKey = :dateKey LIMIT 1")
    fun observeUsageForDate(dateKey: String): Flow<ApiUsageEntity?>

    @Query("SELECT * FROM api_usage WHERE dateKey = :dateKey LIMIT 1")
    suspend fun getUsageForDate(dateKey: String): ApiUsageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUsage(usage: ApiUsageEntity)
}
