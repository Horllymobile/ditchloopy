package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReflectionDao {
    @Query("SELECT * FROM reflections ORDER BY timestamp DESC")
    fun getAllReflections(): Flow<List<Reflection>>

    @Query("SELECT * FROM reflections WHERE invitationId = :invitationId LIMIT 1")
    suspend fun getReflectionForInvitation(invitationId: String): Reflection?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReflection(reflection: Reflection)

    @Query("DELETE FROM reflections WHERE invitationId = :invitationId")
    suspend fun deleteReflectionByInvitationId(invitationId: String)
}
