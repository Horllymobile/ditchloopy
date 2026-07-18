package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InvitationDao {
    @Query("SELECT * FROM invitations")
    fun getAllInvitations(): Flow<List<Invitation>>

    @Query("SELECT * FROM invitations WHERE isSelectedForWeek = 1 ORDER BY dayOfWeek ASC")
    fun getWeeklyJourney(): Flow<List<Invitation>>

    @Query("SELECT * FROM invitations WHERE isCompleted = 1 ORDER BY completedAt DESC")
    fun getCompletedInvitations(): Flow<List<Invitation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvitation(invitation: Invitation)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvitations(invitations: List<Invitation>)

    @Query("UPDATE invitations SET isCompleted = :isCompleted, completedAt = :completedAt WHERE id = :id")
    suspend fun updateCompletionStatus(id: String, isCompleted: Boolean, completedAt: Long?)

    @Query("UPDATE invitations SET isSelectedForWeek = :isSelected, dayOfWeek = :dayOfWeek WHERE id = :id")
    suspend fun updateWeeklyAssignment(id: String, isSelected: Boolean, dayOfWeek: Int)

    @Query("UPDATE invitations SET isSelectedForWeek = 0, dayOfWeek = 0")
    suspend fun clearWeeklyJourney()

    @Query("SELECT COUNT(*) FROM invitations")
    suspend fun getCount(): Int

    @Query("DELETE FROM invitations WHERE id = :id")
    suspend fun deleteInvitationById(id: String)
}
