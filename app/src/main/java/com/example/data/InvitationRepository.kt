package com.example.data

import kotlinx.coroutines.flow.Flow

class InvitationRepository(
    private val invitationDao: InvitationDao,
    private val reflectionDao: ReflectionDao
) {
    val allInvitations: Flow<List<Invitation>> = invitationDao.getAllInvitations()
    val weeklyJourney: Flow<List<Invitation>> = invitationDao.getWeeklyJourney()
    val completedInvitations: Flow<List<Invitation>> = invitationDao.getCompletedInvitations()
    val allReflections: Flow<List<Reflection>> = reflectionDao.getAllReflections()

    suspend fun insertInvitation(invitation: Invitation) {
        invitationDao.insertInvitation(invitation)
    }

    suspend fun insertInvitations(invitations: List<Invitation>) {
        invitationDao.insertInvitations(invitations)
    }

    suspend fun updateCompletionStatus(id: String, isCompleted: Boolean, reflectionText: String? = null, mood: String? = null) {
        val completedAt = if (isCompleted) System.currentTimeMillis() else null
        invitationDao.updateCompletionStatus(id, isCompleted, completedAt)

        if (isCompleted && reflectionText != null && mood != null) {
            val reflection = Reflection(
                invitationId = id,
                text = reflectionText,
                mood = mood,
                timestamp = System.currentTimeMillis()
            )
            reflectionDao.insertReflection(reflection)
        } else if (!isCompleted) {
            reflectionDao.deleteReflectionByInvitationId(id)
        }
    }

    suspend fun updateWeeklyAssignment(id: String, isSelected: Boolean, dayOfWeek: Int) {
        invitationDao.updateWeeklyAssignment(id, isSelected, dayOfWeek)
    }

    suspend fun clearWeeklyJourney() {
        invitationDao.clearWeeklyJourney()
    }

    suspend fun getCount(): Int {
        return invitationDao.getCount()
    }

    suspend fun getReflectionForInvitation(invitationId: String): Reflection? {
        return reflectionDao.getReflectionForInvitation(invitationId)
    }

    suspend fun deleteInvitationById(id: String) {
        invitationDao.deleteInvitationById(id)
        reflectionDao.deleteReflectionByInvitationId(id)
    }
}
