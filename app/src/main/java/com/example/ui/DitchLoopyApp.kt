package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.GeminiService
import com.example.data.Invitation
import com.example.data.Reflection
import java.text.SimpleDateFormat
import java.util.*

enum class DitchLoopyTab(val title: String, val icon: ImageVector) {
    JOURNEY("Journey", Icons.Outlined.AutoAwesome),
    LIBRARY("Discover", Icons.Outlined.Explore),
    ARCHIVE("Archive", Icons.Outlined.FavoriteBorder),
    MANIFESTO("Philosophy", Icons.Outlined.MenuBook)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DitchLoopyApp(
    viewModel: DitchLoopyViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showOnboarding by remember { mutableStateOf(true) }
    var currentTab by remember { mutableStateOf(DitchLoopyTab.JOURNEY) }
    
    // DB Flows
    val weeklyJourney by viewModel.weeklyJourney.collectAsStateWithLifecycle()
    val libraryInvitations by viewModel.library.collectAsStateWithLifecycle()
    val completedInvitations by viewModel.completedInvitations.collectAsStateWithLifecycle()
    val reflections by viewModel.reflections.collectAsStateWithLifecycle()
    
    // Status Flows
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val generationStatus by viewModel.generationStatus.collectAsStateWithLifecycle()

    // Dialog state for Customization
    var showCustomizerSheet by remember { mutableStateOf(false) }
    
    // Dialog state for completion reflection
    var activeReflectionInvitation by remember { mutableStateOf<Invitation?>(null) }

    if (showOnboarding) {
        DitchLoopyOnboardingScreen(
            onEnterJourney = { showOnboarding = false }
        )
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .testTag("bottom_nav_bar"),
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 8.dp
                ) {
                    DitchLoopyTab.values().forEach { tab ->
                        NavigationBarItem(
                            selected = currentTab == tab,
                            onClick = { currentTab = tab },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title
                                )
                            },
                            label = {
                                Text(
                                    text = tab.title,
                                    fontFamily = FontFamily.Default,
                                    fontSize = 11.sp
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                unselectedIconColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                unselectedTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            },
            modifier = modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Background subtle warm organic ambient circle (pulsing)
                CalmAmbientBackdrop(isPulsingFast = isGenerating)

                Crossfade(targetState = currentTab, label = "tab_fade") { tab ->
                    when (tab) {
                        DitchLoopyTab.JOURNEY -> {
                            JourneyDashboard(
                                weeklyJourney = weeklyJourney,
                                isGenerating = isGenerating,
                                generationStatus = generationStatus,
                                onOpenCustomizer = { showCustomizerSheet = true },
                                onCompleteInvitation = { activeReflectionInvitation = it },
                                onSkipInvitation = { viewModel.skipAssignment(it.id) },
                                onResetAllData = { viewModel.resetAllData() }
                            )
                        }
                        DitchLoopyTab.LIBRARY -> {
                            LibraryExploreScreen(
                                libraryInvitations = libraryInvitations,
                                onAddToWeek = { invitation ->
                                    // Assign to first open day in week
                                    val assignedDays = weeklyJourney.map { it.dayOfWeek }
                                    val firstOpenDay = (1..7).firstOrNull { it !in assignedDays } ?: 1
                                    viewModel.addInvitationToWeek(invitation.id, firstOpenDay)
                                }
                            )
                        }
                        DitchLoopyTab.ARCHIVE -> {
                            ArchiveTimelineScreen(
                                completedInvitations = completedInvitations,
                                reflections = reflections,
                                onResetCompletion = { viewModel.resetInvitationCompletion(it.id) }
                            )
                        }
                        DitchLoopyTab.MANIFESTO -> {
                            ManifestoScreeen()
                        }
                    }
                }

                // Customizer sliding bottom-sheet dialog
                if (showCustomizerSheet) {
                    PersonalizationDialog(
                        isGenerating = isGenerating,
                        generationStatus = generationStatus,
                        onDismiss = { showCustomizerSheet = false },
                        onTailorJourney = { mood, indoorOutdoor, energyLevel, extraContext ->
                            viewModel.buildWeeklyJourney(mood, indoorOutdoor, energyLevel, extraContext)
                            showCustomizerSheet = false
                        }
                    )
                }

                // Reflection entry dialog
                if (activeReflectionInvitation != null) {
                    val invitation = activeReflectionInvitation!!
                    ReflectionEntryDialog(
                        invitationTitle = invitation.title,
                        onDismiss = { activeReflectionInvitation = null },
                        onSaveReflection = { text, mood ->
                            viewModel.completeInvitation(invitation.id, text, mood)
                            activeReflectionInvitation = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CalmAmbientBackdrop(isPulsingFast: Boolean) {
    val duration = if (isPulsingFast) 1500 else 6000
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(duration, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "backdrop_scale"
    )

    val opacity by infiniteTransition.animateFloat(
        initialValue = 0.04f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(duration, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "backdrop_opacity"
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .alpha(opacity)
    ) {
        val center = Offset(size.width * 0.7f, size.height * 0.2f)
        val radius = size.width * 0.5f * scale
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF243324), // Moss Green
                    Color(0xFFA8D5BA), // Bright Sage highlight
                    Color.Transparent
                ),
                center = center,
                radius = radius
            ),
            center = center,
            radius = radius
        )
    }
}

@Composable
fun JourneyDashboard(
    weeklyJourney: List<Invitation>,
    isGenerating: Boolean,
    generationStatus: String?,
    onOpenCustomizer: () -> Unit,
    onCompleteInvitation: (Invitation) -> Unit,
    onSkipInvitation: (Invitation) -> Unit,
    onResetAllData: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentPadding = PaddingValues(vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CURRENT LOOP: THE ROUTINE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFA8B5A8),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "Escape the ordinary,",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Light,
                            fontFamily = FontFamily.Serif
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Your Weekly Journey.",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Normal,
                            fontFamily = FontFamily.Serif
                        ),
                        color = Color(0xFFA8D5BA) // SageBright
                    )
                }

                IconButton(
                    onClick = onResetAllData,
                    modifier = Modifier.testTag("reset_data_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset journey state",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                }
            }
        }

        if (isGenerating) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(24.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = generationStatus ?: "Sieving the ambient archives...",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        if (weeklyJourney.isEmpty() && !isGenerating) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(24.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "A blank canvas awaits.",
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "No invitations assigned for this week. Tap the builder to compose an organic journey tailored to your mood.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onOpenCustomizer,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .testTag("compose_journey_button")
                                .padding(top = 8.dp)
                        ) {
                            Text("Compose Custom Journey")
                        }
                    }
                }
            }
        } else {
            // Display active journey invitations in vertical days
            items(weeklyJourney) { invitation ->
                InvitationDashboardCard(
                    invitation = invitation,
                    onComplete = { onCompleteInvitation(invitation) },
                    onSkip = { onSkipInvitation(invitation) }
                )
            }

            // Button to customize / personalize next weekly journey
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    OutlinedButton(
                        onClick = onOpenCustomizer,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("recompose_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Personalize / Tailor Journey")
                    }
                }
            }
        }
    }
}

@Composable
fun InvitationDashboardCard(
    invitation: Invitation,
    onComplete: () -> Unit,
    onSkip: () -> Unit
) {
    val dayName = when (invitation.dayOfWeek) {
        1 -> "First Moment"
        2 -> "Second Moment"
        3 -> "Third Moment"
        4 -> "Fourth Moment"
        5 -> "Fifth Moment"
        6 -> "Sixth Moment"
        7 -> "Seventh Moment"
        else -> "Journey Moment"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = Color(0xFF2A352A),
                shape = RoundedCornerShape(24.dp)
            )
            .shadow(4.dp, RoundedCornerShape(24.dp))
            .testTag("invitation_card_${invitation.id}"),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = if (invitation.isCompleted) {
                            listOf(
                                Color(0xFF0C110C),
                                Color(0xFF0A0C0A)
                            )
                        } else {
                            listOf(
                                Color(0xFF162016),
                                Color(0xFF0F140F)
                            )
                        }
                    )
                )
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dayName.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFA8B5A8),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )

                if (invitation.isCustom) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color(0xFF243324),
                                shape = RoundedCornerShape(50)
                            )
                            .border(
                                width = 1.dp,
                                color = Color(0xFF2D452D),
                                shape = RoundedCornerShape(50)
                            )
                            .padding(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "AI TAILORED",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = Color(0xFFA8D5BA)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color(0xFF243324),
                                shape = RoundedCornerShape(50)
                            )
                            .border(
                                width = 1.dp,
                                color = Color(0xFF2D452D),
                                shape = RoundedCornerShape(50)
                            )
                            .padding(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = invitation.category.uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = Color(0xFFA8D5BA)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = invitation.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Light
                ),
                color = if (invitation.isCompleted) {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.onBackground
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = invitation.description,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFA8B5A8),
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Meta indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetaIndicator(icon = Icons.Outlined.Timer, text = invitation.duration)
                MetaIndicator(icon = Icons.Outlined.Terrain, text = invitation.indoorOutdoor)
                MetaIndicator(icon = Icons.Outlined.Bolt, text = invitation.difficulty)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (invitation.isCompleted) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFFA8D5BA)
                        )
                        Text(
                            text = "Preserved in Memories",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFA8D5BA)
                        )
                    }
                } else {
                    TextButton(
                        onClick = onSkip,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color(0xFFA8B5A8)
                        ),
                        modifier = Modifier.testTag("skip_button_${invitation.id}")
                    ) {
                        Text("Skip Moment", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onComplete,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE8EDE8),
                            contentColor = Color(0xFF0A0C0A)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .height(46.dp)
                            .testTag("complete_button_${invitation.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reflect & Complete", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun MetaIndicator(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .background(
                color = Color(0xFF0C110C).copy(alpha = 0.5f),
                shape = RoundedCornerShape(50)
            )
            .border(
                width = 1.dp,
                color = Color(0xFF2A352A),
                shape = RoundedCornerShape(50)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFFA8D5BA),
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = text,
            fontSize = 11.sp,
            color = Color(0xFFE8EDE8),
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryExploreScreen(
    libraryInvitations: List<Invitation>,
    onAddToWeek: (Invitation) -> Unit
) {
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = listOf("All", "Discover", "Experience", "Reflect", "Grow")

    val filteredList = if (selectedCategory == "All") {
        libraryInvitations
    } else {
        libraryInvitations.filter { it.category.equals(selectedCategory, ignoreCase = true) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentPadding = PaddingValues(vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Moment Library",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Light
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Pick and add a custom invitation directly into your week.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFA8B5A8)
                )
            }
        }

        // Category filter row
        item {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { category ->
                    val isSelected = selectedCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = category },
                        label = { Text(category, fontWeight = FontWeight.Medium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF243324),
                            selectedLabelColor = Color(0xFFA8D5BA),
                            containerColor = Color(0xFF0A0C0A),
                            labelColor = Color(0xFFA8B5A8)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) Color(0xFF2D452D) else Color(0xFF2A352A)
                        )
                    )
                }
            }
        }

        if (filteredList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "All items in this category are active in your journey.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFA8B5A8).copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(filteredList) { invitation ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = Color(0xFF2A352A),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .shadow(4.dp, RoundedCornerShape(24.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF162016),
                                        Color(0xFF0F140F)
                                    )
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = Color(0xFF243324),
                                        shape = RoundedCornerShape(50)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = Color(0xFF2D452D),
                                        shape = RoundedCornerShape(50)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = invitation.category.uppercase(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = Color(0xFFA8D5BA)
                                )
                            }

                            IconButton(
                                onClick = { onAddToWeek(invitation) },
                                modifier = Modifier.testTag("add_to_week_${invitation.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add to Journey",
                                    tint = Color(0xFFA8D5BA)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = invitation.title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Light
                            ),
                            color = Color(0xFFE8EDE8)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = invitation.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFA8B5A8),
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MetaIndicator(icon = Icons.Outlined.Timer, text = invitation.duration)
                            MetaIndicator(icon = Icons.Outlined.Terrain, text = invitation.indoorOutdoor)
                            MetaIndicator(icon = Icons.Outlined.AttachMoney, text = invitation.cost)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArchiveTimelineScreen(
    completedInvitations: List<Invitation>,
    reflections: List<Reflection>,
    onResetCompletion: (Invitation) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentPadding = PaddingValues(vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Journey Archive",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "A catalog of your interruptions. Nostalgic snaps of real presence.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        if (completedInvitations.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "No memories saved yet.",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "Your reflections will accumulate here, forming a record of mindful disruptions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(completedInvitations) { invitation ->
                val reflection = reflections.find { it.invitationId == invitation.id }
                PolaroidMemoryCard(
                    invitation = invitation,
                    reflection = reflection,
                    onUndo = { onResetCompletion(invitation) }
                )
            }
        }
    }
}

@Composable
fun PolaroidMemoryCard(
    invitation: Invitation,
    reflection: Reflection?,
    onUndo: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val dateString = reflection?.let { dateFormat.format(Date(it.timestamp)) } ?: ""

    // Polaroid Styled Card
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(4.dp))
            .testTag("polaroid_${invitation.id}"),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFAF7F2) // Organic polaroid paper color
        ),
        shape = RoundedCornerShape(4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // "Photo" Area with soft sepia-terracotta background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.6f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFECE4D9),
                                Color(0xFFDFD4C4)
                            )
                        )
                    )
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.Camera,
                        contentDescription = null,
                        tint = Color(0xFFC56D48).copy(alpha = 0.5f),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = invitation.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFF5C5247),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Vibe: ${reflection?.mood ?: "Calm"}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC56D48)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Reflection Text mimicking handwriting
            Text(
                text = reflection?.text ?: "I stepped away and remembered who I was.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF3C352E),
                fontFamily = FontFamily.Serif,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Divider(color = Color(0xFFE8DFD0), thickness = 1.dp)

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = dateString,
                        fontSize = 11.sp,
                        color = Color(0xFF8E8375),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Original Assignment: ${invitation.category}",
                        fontSize = 9.sp,
                        color = Color(0xFF8E8375).copy(alpha = 0.8f)
                    )
                }

                TextButton(
                    onClick = onUndo,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFFC56D48)
                    )
                ) {
                    Text("Undo Clear", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ManifestoScreeen() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentPadding = PaddingValues(vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        item {
            Text(
                text = "The Manifesto",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Somewhere along the way, life became a checklist.",
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    val manifestoLines = listOf(
                        "Wake up. Work. Scroll. Sleep. Repeat.",
                        "The problem isn't that people don't have goals. The problem is that they rarely experience anything new.",
                        "Memories aren't created by routine. They're created by moments that interrupt it.",
                        "DitchLoopy exists to create those moments.",
                        "We don't build habits. We build lives worth remembering."
                    )

                    manifestoLines.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyLarge,
                            lineHeight = 24.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Product Constitution",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                val constitutionQuestions = listOf(
                    "Does this help someone experience something meaningful?",
                    "Does it reduce or increase stress?",
                    "Would we be proud if this was someone's first interaction today?",
                    "Is this encouraging curiosity rather than obligation?",
                    "Can this feature be explained in one sentence?",
                    "Does it make the app feel more like a guide than a manager?",
                    "If we removed points and rewards, would this still be valuable?"
                )

                constitutionQuestions.forEachIndexed { index, question ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Column {
                            Text(
                                text = question,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Every core app feature must align perfectly with this rule or face structural redesign.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DitchLoopyOnboardingScreen(
    onEnterJourney: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0C0A), // SophisticatedDarkBackground
                        Color(0xFF0F140F) // CardSurfaceDarkSecondary
                    )
                )
            )
            .padding(28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Calm animated lotus or ambient icon
            Icon(
                imageVector = Icons.Outlined.Spa,
                contentDescription = null,
                tint = Color(0xFFA8D5BA), // SageBright
                modifier = Modifier.size(80.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "DitchLoopy",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Light
                ),
                color = Color(0xFFE8EDE8), // TextPrimaryDark
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Break autopilot. Embrace real presence.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFA8B5A8), // MutedSageText
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Serif
            )

            Spacer(modifier = Modifier.height(48.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF162016)), // CardSurfaceDark
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = Color(0xFF2A352A), // SophisticatedBorder
                        shape = RoundedCornerShape(20.dp)
                    )
                    .shadow(4.dp, RoundedCornerShape(20.dp))
            ) {
                Text(
                    text = "\"We don't build habits. We build lives worth remembering.\"",
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Medium,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = Color(0xFFA8D5BA), // SageBright
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onEnterJourney,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE8EDE8), // Off-white
                    contentColor = Color(0xFF0A0C0A) // Deep forest black
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("enter_journey_button")
            ) {
                Text("Enter the Journey", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonalizationDialog(
    isGenerating: Boolean,
    generationStatus: String?,
    onDismiss: () -> Unit,
    onTailorJourney: (String, String, String, String) -> Unit
) {
    var selectedMood by remember { mutableStateOf("Calm") }
    var selectedPreference by remember { mutableStateOf("Either") }
    var selectedEnergy by remember { mutableStateOf("Medium") }
    var userThoughts by remember { mutableStateOf("") }

    val moods = listOf("Calm", "Busy", "Blue", "Curious", "Tired")
    val preferences = listOf("Indoor", "Outdoor", "Either")
    val energies = listOf("Low", "Medium", "High")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .shadow(8.dp, RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tailor Your Vibe",
                            style = MaterialTheme.typography.titleLarge
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                // Mood selector
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Current Mood / Vibe", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            moods.forEach { mood ->
                                val isSelected = selectedMood == mood
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedMood = mood },
                                    label = { Text(mood) }
                                )
                            }
                        }
                    }
                }

                // Location Preference
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Setting Preference", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            preferences.forEach { pref ->
                                val isSelected = selectedPreference == pref
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedPreference = pref },
                                    label = { Text(pref) }
                                )
                            }
                        }
                    }
                }

                // Energy Levels
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Current Energy Level", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            energies.forEach { energy ->
                                val isSelected = selectedEnergy == energy
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedEnergy = energy },
                                    label = { Text(energy) }
                                )
                            }
                        }
                    }
                }

                // Additional input text
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Additional Mind State (Optional)", style = MaterialTheme.typography.labelLarge)
                        OutlinedTextField(
                            value = userThoughts,
                            onValueChange = { userThoughts = it },
                            placeholder = { Text("e.g. raining outside, feeling lonely, with a friend...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                                .testTag("thoughts_input_field"),
                            maxLines = 3,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                // Info about API key
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            text = if (GeminiService.isApiKeyAvailable()) {
                                "✨ Mission Intelligence Engine (Gemini API) is active for fully custom generation."
                            } else {
                                "💡 Using local organic curation engine. To enable active Gemini AI personalization, insert your API key in the Secrets Panel."
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            lineHeight = 16.sp
                        )
                    }
                }

                // Action Button
                item {
                    Button(
                        onClick = {
                            onTailorJourney(selectedMood, selectedPreference, selectedEnergy, userThoughts)
                        },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("generate_personalized_journey_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Taylor Weekly Journey", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReflectionEntryDialog(
    invitationTitle: String,
    onDismiss: () -> Unit,
    onSaveReflection: (String, String) -> Unit
) {
    var reflectionText by remember { mutableStateOf("") }
    var selectedMood by remember { mutableStateOf("Grateful") }
    val moods = listOf("Calm", "Grateful", "Inspired", "Curious", "Nostalgic")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .shadow(8.dp, RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Preserve the Memory",
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Text(
                    text = "Reflecting on: \"$invitationTitle\"",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )

                // Mood selector
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("How did you feel?", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        moods.forEach { mood ->
                            val isSelected = selectedMood == mood
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedMood = mood },
                                label = { Text(mood) }
                            )
                        }
                    }
                }

                // Reflection Text Field
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your Reflection / Sensation", style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(
                        value = reflectionText,
                        onValueChange = { reflectionText = it },
                        placeholder = { Text("What did you notice? Any new details, sounds, or feelings? Keep it sincere...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .testTag("reflection_input_field"),
                        maxLines = 6,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Submit Button
                Button(
                    onClick = {
                        val finalReflection = reflectionText.ifEmpty { "I stepped away from autopilot and found peace in the details." }
                        onSaveReflection(finalReflection, selectedMood)
                    },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_reflection_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Preserve Memory", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
