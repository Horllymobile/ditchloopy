package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DitchLoopyViewModel(
    application: Application,
    private val repository: InvitationRepository
) : AndroidViewModel(application) {

    val weeklyJourney: StateFlow<List<Invitation>> = repository.weeklyJourney
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val library: StateFlow<List<Invitation>> = repository.allInvitations
        .map { list -> list.filter { !it.isSelectedForWeek && !it.isCompleted } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedInvitations: StateFlow<List<Invitation>> = repository.completedInvitations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reflections: StateFlow<List<Reflection>> = repository.allReflections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _generationStatus = MutableStateFlow<String?>(null)
    val generationStatus: StateFlow<String?> = _generationStatus.asStateFlow()

    init {
        // Pre-populate database with default curated invitations on first run
        viewModelScope.launch {
            if (repository.getCount() == 0) {
                repository.insertInvitations(DefaultInvitations.list)
                // Auto-generate a beautiful initial journey for first-time onboarding
                generateLocalJourney()
            }
        }
    }

    /**
     * Build a tailored 5-day Weekly Journey.
     * Uses Gemini API for custom invitation if available; falls back to smart local curation.
     */
    fun buildWeeklyJourney(
        mood: String,
        indoorOutdoor: String,
        energyLevel: String,
        userContext: String
    ) {
        viewModelScope.launch {
            _isGenerating.value = true
            _generationStatus.value = "Consulting the Mission Intelligence Engine..."
            
            try {
                // 1. Clear current assignments
                repository.clearWeeklyJourney()

                var customInvitation: Invitation? = null

                // 2. Try to get personalized invitation from Gemini
                if (GeminiService.isApiKeyAvailable()) {
                    try {
                        customInvitation = GeminiService.generatePersonalizedInvitation(
                            mood = mood,
                            indoorOutdoor = indoorOutdoor,
                            energyLevel = energyLevel,
                            userContext = userContext
                        )
                    } catch (e: Exception) {
                        _generationStatus.value = "AI temporary anomaly. Utilizing local archives..."
                    }
                }

                // 3. Select matching or random library items
                val allLib = repository.allInvitations.first()
                val availableLib = allLib.filter { !it.isCompleted }.shuffled()

                // Filter elements that match preferences (as guide rails, not strict blocks)
                val matchingLib = availableLib.filter { invitation ->
                    (indoorOutdoor == "Either" || invitation.indoorOutdoor == "Either" || invitation.indoorOutdoor == indoorOutdoor) &&
                    (energyLevel == "Medium" || invitation.energyLevel == energyLevel)
                }

                val chosenList = mutableListOf<Invitation>()
                
                // Add Gemini custom item first if created
                if (customInvitation != null) {
                    repository.insertInvitation(customInvitation)
                    chosenList.add(customInvitation)
                }

                // Gather others from filtered list, fill with remaining if necessary
                val pool = if (matchingLib.size >= 5) matchingLib else availableLib
                for (item in pool) {
                    if (chosenList.size >= 5) break
                    if (item.id != customInvitation?.id) {
                        chosenList.add(item)
                    }
                }

                // Fallback to absolute random defaults if still empty
                if (chosenList.size < 5) {
                    for (item in DefaultInvitations.list) {
                        if (chosenList.size >= 5) break
                        if (chosenList.none { it.id == item.id }) {
                            chosenList.add(item)
                        }
                    }
                }

                // 4. Assign daily order (Day 1 to 5)
                chosenList.forEachIndexed { index, invitation ->
                    repository.updateWeeklyAssignment(invitation.id, isSelected = true, dayOfWeek = index + 1)
                }

                _generationStatus.value = "Journey tailored successfully."
            } catch (e: Exception) {
                _generationStatus.value = "Failed to build journey: ${e.message}"
            } finally {
                _isGenerating.value = false
            }
        }
    }

    /**
     * Simple local assignment fallback.
     */
    private suspend fun generateLocalJourney() {
        val all = DefaultInvitations.list.shuffled().take(5)
        all.forEachIndexed { index, invitation ->
            repository.updateWeeklyAssignment(invitation.id, isSelected = true, dayOfWeek = index + 1)
        }
    }

    /**
     * Complete an invitation, save reflection and mood.
     */
    fun completeInvitation(id: String, reflectionText: String, mood: String) {
        viewModelScope.launch {
            repository.updateCompletionStatus(id, isCompleted = true, reflectionText = reflectionText, mood = mood)
        }
    }

    /**
     * Mark incomplete (remove reflection).
     */
    fun resetInvitationCompletion(id: String) {
        viewModelScope.launch {
            repository.updateCompletionStatus(id, isCompleted = false)
        }
    }

    /**
     * Move an invitation directly into the active weekly journey.
     */
    fun addInvitationToWeek(id: String, dayOfWeek: Int) {
        viewModelScope.launch {
            repository.updateCompletionStatus(id, isCompleted = false)
            repository.updateWeeklyAssignment(id, isSelected = true, dayOfWeek = dayOfWeek)
        }
    }

    /**
     * Discard an assignment from the active weekly path.
     */
    fun skipAssignment(id: String) {
        viewModelScope.launch {
            repository.updateWeeklyAssignment(id, isSelected = false, dayOfWeek = 0)
        }
    }

    /**
     * Re-seed default database state.
     */
    fun resetAllData() {
        viewModelScope.launch {
            repository.clearWeeklyJourney()
            val all = repository.allInvitations.first()
            for (item in all) {
                repository.deleteInvitationById(item.id)
            }
            repository.insertInvitations(DefaultInvitations.list)
            generateLocalJourney()
        }
    }
}

class DitchLoopyViewModelFactory(
    private val application: Application,
    private val repository: InvitationRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DitchLoopyViewModel::class.java)) {
            return DitchLoopyViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
