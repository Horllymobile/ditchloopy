package com.ditchloopy.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ditchloopy.data.*
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class UserState(
    val uid: String? = null,
    val displayName: String? = null,
    val email: String? = null,
    val photoUrl: String? = null,
    val isLoggedIn: Boolean = false,
    val isAnonymous: Boolean = true,
    val isSandboxMode: Boolean = false
)

data class AestheticVariation(
    val titlePrefix: String,
    val descriptionSuffix: String,
    val difficultyModifier: String? = null
)

val aestheticVariations = listOf(
    AestheticVariation(
        titlePrefix = "Golden Hour: ",
        descriptionSuffix = " Perform this specifically as the sun begins to set, letting the amber light illuminate your surroundings.",
        difficultyModifier = "Easy"
    ),
    AestheticVariation(
        titlePrefix = "Candlelit ",
        descriptionSuffix = " Do this under the gentle, flickering glow of a single candle. Let the shadows slow your breathing.",
        difficultyModifier = "Medium"
    ),
    AestheticVariation(
        titlePrefix = "Silent ",
        descriptionSuffix = " Execute this in absolute silence. Pay close attention to the background noise of your own mind.",
        difficultyModifier = "Medium"
    ),
    AestheticVariation(
        titlePrefix = "Twilight: ",
        descriptionSuffix = " Embark on this during twilight. Notice how colors lose their vibrancy and shift into shades of deep blue.",
        difficultyModifier = "Deep"
    ),
    AestheticVariation(
        titlePrefix = "Sensory Focus: ",
        descriptionSuffix = " While doing this, close your eyes periodically for 10 seconds to heighten your sense of touch and hearing.",
        difficultyModifier = "Medium"
    ),
    AestheticVariation(
        titlePrefix = "Micro-Presence: ",
        descriptionSuffix = " Slow your physical movements down to half-speed during this moment. Feel every shifting weight and motion.",
        difficultyModifier = "Deep"
    ),
    AestheticVariation(
        titlePrefix = "Resonant ",
        descriptionSuffix = " Before you begin, take five deep, audible breaths, sighing out all of your lingering digital tension.",
        difficultyModifier = "Easy"
    )
)

class DitchLoopyViewModel(
    application: Application,
    private val repository: InvitationRepository
) : AndroidViewModel(application) {

    private val _userState = MutableStateFlow(UserState())
    val userState: StateFlow<UserState> = _userState.asStateFlow()

    private var firebaseAuth: FirebaseAuth? = null

    val weeklyJourney: StateFlow<List<Invitation>> = repository.weeklyJourney
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val library: StateFlow<List<Invitation>> = repository.allInvitations
        .map { list -> list.filter { !it.isSelectedForWeek && !it.isCompleted } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedInvitations: StateFlow<List<Invitation>> = repository.completedInvitations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reflections: StateFlow<List<Reflection>> = repository.allReflections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailyCheckIns: StateFlow<List<DailyCheckIn>> = repository.allDailyCheckIns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _generationStatus = MutableStateFlow<String?>(null)
    val generationStatus: StateFlow<String?> = _generationStatus.asStateFlow()

    init {
        // Safe Firebase Auth initialisation
        try {
            val app = FirebaseApp.initializeApp(application)
            if (app != null) {
                firebaseAuth = FirebaseAuth.getInstance()
                firebaseAuth?.addAuthStateListener { auth ->
                    val user = auth.currentUser
                    if (user != null) {
                        _userState.value = UserState(
                            uid = user.uid,
                            displayName = user.displayName ?: "Journey Companion",
                            email = user.email,
                            photoUrl = user.photoUrl?.toString(),
                            isLoggedIn = true,
                            isAnonymous = user.isAnonymous,
                            isSandboxMode = false
                        )
                    } else {
                        _userState.value = UserState(
                            uid = null,
                            displayName = null,
                            email = null,
                            photoUrl = null,
                            isLoggedIn = false,
                            isAnonymous = true,
                            isSandboxMode = false
                        )
                    }
                }
            } else {
                setupSandboxMode()
            }
        } catch (e: Exception) {
            setupSandboxMode()
        }

        // Pre-populate database with default curated invitations on first run
        viewModelScope.launch {
            if (repository.getCount() == 0) {
                repository.insertInvitations(DefaultInvitations.list)
                // Auto-generate a beautiful initial journey for first-time onboarding
                generateLocalJourney()
            }
        }
    }

    private fun setupSandboxMode() {
        _userState.value = UserState(
            uid = "sandbox_user_123",
            displayName = "Preserved Guest",
            email = "sandbox@ditchloopy.com",
            photoUrl = null,
            isLoggedIn = false,
            isAnonymous = true,
            isSandboxMode = true
        )
    }

    fun signInWithGoogleToken(idToken: String) {
        val auth = firebaseAuth
        if (auth != null) {
            viewModelScope.launch {
                _generationStatus.value = "Authenticating with Google..."
                try {
                    val credential = GoogleAuthProvider.getCredential(idToken, null)
                    auth.signInWithCredential(credential)
                    _generationStatus.value = "Welcome back!"
                } catch (e: Exception) {
                    _generationStatus.value = "Authentication failed: ${e.message}"
                }
            }
        } else {
            // Simulated Sandbox sign in for live emulator experience
            viewModelScope.launch {
                _generationStatus.value = "Simulating Google Sync..."
                _userState.value = UserState(
                    uid = "mock_google_user",
                    displayName = "Astral Explorer",
                    email = "explorer@gmail.com",
                    photoUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?q=80&w=120&auto=format&fit=crop",
                    isLoggedIn = true,
                    isAnonymous = false,
                    isSandboxMode = true
                )
                _generationStatus.value = "Signed in as Astral Explorer"
            }
        }
    }

    fun signInWithEmailAndPassword(email: String, password: String, isSignUp: Boolean) {
        val auth = firebaseAuth
        if (auth != null) {
            viewModelScope.launch {
                _generationStatus.value = if (isSignUp) "Creating your vessel..." else "Accessing your vessel..."
                try {
                    if (isSignUp) {
                        auth.createUserWithEmailAndPassword(email, password)
                    } else {
                        auth.signInWithEmailAndPassword(email, password)
                    }
                    _generationStatus.value = "Secure entry successful."
                } catch (e: Exception) {
                    _generationStatus.value = "Authentication failed: ${e.message}"
                }
            }
        } else {
            // Sandbox simulation
            viewModelScope.launch {
                _generationStatus.value = "Simulating Vault Sync..."
                val name = email.substringBefore("@").replaceFirstChar { it.uppercase() }
                _userState.value = UserState(
                    uid = "mock_email_user_" + email.hashCode(),
                    displayName = "$name of DitchLoopy",
                    email = email,
                    photoUrl = null,
                    isLoggedIn = true,
                    isAnonymous = false,
                    isSandboxMode = true
                )
                _generationStatus.value = "Signed in as $name"
            }
        }
    }

    fun signOut() {
        val auth = firebaseAuth
        if (auth != null) {
            auth.signOut()
        } else {
            setupSandboxMode()
        }
    }

    /**
     * Build a tailored 5-day Weekly Journey.
     * Uses Gemini API for custom batch generation if available; falls back to smart local curation.
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

                val completed = repository.completedInvitations.first()
                val completedTitles = completed.map { it.title.lowercase().trim() }

                val chosenList = mutableListOf<Invitation>()

                // 2. Try to get a batch of personalized, non-repeated invitations from Gemini
                if (GeminiService.isApiKeyAvailable()) {
                    try {
                        val batch = GeminiService.generatePersonalizedInvitationsBatch(
                            count = 5,
                            mood = mood,
                            indoorOutdoor = indoorOutdoor,
                            energyLevel = energyLevel,
                            userContext = userContext,
                            completedTitles = completed.map { it.title }
                        )
                        if (batch.isNotEmpty()) {
                            repository.insertInvitations(batch)
                            chosenList.addAll(batch)
                        }
                    } catch (e: Exception) {
                        _generationStatus.value = "AI temporary anomaly. Utilizing local archives..."
                    }
                }

                // 3. Fallback/Fill in with non-completed local library items
                if (chosenList.size < 5) {
                    val allLib = repository.allInvitations.first()
                    val availableLib = allLib.filter { 
                        !it.isCompleted && !completedTitles.contains(it.title.lowercase().trim()) 
                    }.shuffled()

                    // Filter elements that match preferences (as guide rails, not strict blocks)
                    val matchingLib = availableLib.filter { invitation ->
                        (indoorOutdoor == "Either" || invitation.indoorOutdoor == "Either" || invitation.indoorOutdoor == indoorOutdoor) &&
                        (energyLevel == "Medium" || invitation.energyLevel == energyLevel)
                    }

                    val pool = if (matchingLib.size >= 5 - chosenList.size) matchingLib else availableLib
                    for (item in pool) {
                        if (chosenList.size >= 5) break
                        if (chosenList.none { it.id == item.id || it.title.lowercase().trim() == item.title.lowercase().trim() }) {
                            chosenList.add(item)
                        }
                    }
                }

                // 4. If we STILL need items (e.g. user completed almost everything, or we are in offline mode),
                // we dynamically generate fresh, non-repeating variations of default invitations!
                if (chosenList.size < 5) {
                    val shuffledDefaults = DefaultInvitations.list.shuffled()
                    for (defaultItem in shuffledDefaults) {
                        if (chosenList.size >= 5) break
                        
                        // Pick a random aesthetic variation to transform the default item
                        val variation = aestheticVariations.random()
                        val newTitle = variation.titlePrefix + defaultItem.title
                        val newDescription = defaultItem.description + " " + variation.descriptionSuffix
                        val newId = "dynamic_var_${defaultItem.id}_${System.currentTimeMillis()}"

                        // Only add if not already in our chosen list or completed
                        val isTitleCompleted = completedTitles.contains(newTitle.lowercase().trim())
                        val isAlreadyChosen = chosenList.any { it.title.lowercase().trim() == newTitle.lowercase().trim() }
                        
                        if (!isTitleCompleted && !isAlreadyChosen) {
                            val dynamicItem = defaultItem.copy(
                                id = newId,
                                title = newTitle,
                                description = newDescription,
                                difficulty = variation.difficultyModifier ?: defaultItem.difficulty,
                                isCompleted = false,
                                isSelectedForWeek = false,
                                isCustom = true
                            )
                            repository.insertInvitation(dynamicItem)
                            chosenList.add(dynamicItem)
                        }
                    }
                }

                // 5. Extreme safety check: fill with any non-chosen defaults
                if (chosenList.size < 5) {
                    for (item in DefaultInvitations.list) {
                        if (chosenList.size >= 5) break
                        if (chosenList.none { it.id == item.id || it.title.lowercase().trim() == item.title.lowercase().trim() }) {
                            chosenList.add(item)
                        }
                    }
                }

                // 6. Assign daily order (Day 1 to 5)
                chosenList.take(5).forEachIndexed { index, invitation ->
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

    fun addDailyCheckIn(text: String, mood: String, category: String, presenceRating: Int) {
        viewModelScope.launch {
            repository.insertDailyCheckIn(
                DailyCheckIn(
                    text = text,
                    mood = mood,
                    category = category,
                    presenceRating = presenceRating
                )
            )
        }
    }

    fun deleteDailyCheckIn(id: Int) {
        viewModelScope.launch {
            repository.deleteDailyCheckInById(id)
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
