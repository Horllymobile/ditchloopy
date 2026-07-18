package com.ditchloopy.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.ditchloopy.data.DailyCheckIn
import com.ditchloopy.data.GeminiService
import com.ditchloopy.data.Invitation
import com.ditchloopy.data.Reflection
import java.text.SimpleDateFormat
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.composed
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Build
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.media.AudioManager
import android.util.Log
import java.util.*

fun Modifier.bounceClick(onClick: () -> Unit): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bounce_scale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

enum class DitchLoopyTab(val title: String, val icon: ImageVector) {
    JOURNEY("Journey", Icons.Outlined.AutoAwesome),
    CHECKIN("Reflect", Icons.Outlined.SelfImprovement),
    LIBRARY("Discover", Icons.Outlined.Explore),
    ARCHIVE("Archive", Icons.Outlined.FavoriteBorder),
    SETTINGS("Settings", Icons.Outlined.Settings)
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

    // Request notification permission on Android 13+ and create channel
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                createNotificationChannel(context)
            }
        }
        LaunchedEffect(Unit) {
            createNotificationChannel(context)
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    } else {
        LaunchedEffect(Unit) {
            createNotificationChannel(context)
        }
    }
    
    // Auth & DB Flows
    val userState by viewModel.userState.collectAsStateWithLifecycle()
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
    var celebratedInvitation by remember { mutableStateOf<Invitation?>(null) }

    if (showOnboarding) {
        DitchLoopyOnboardingScreen(
            viewModel = viewModel,
            onEnterJourney = { showOnboarding = false }
        )
    } else {
        Scaffold(
            bottomBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .shadow(12.dp, RoundedCornerShape(32.dp))
                            .background(
                                color = Color(0xDC0A0C0A), // semi-transparent deep forest black
                                shape = RoundedCornerShape(32.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = Color(0xFF243324), // Moss Green border
                                shape = RoundedCornerShape(32.dp)
                            )
                            .padding(horizontal = 8.dp)
                            .testTag("bottom_nav_bar"),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DitchLoopyTab.values().forEach { tab ->
                            val isSelected = currentTab == tab
                            val iconColor by animateColorAsState(
                                targetValue = if (isSelected) Color(0xFFA8D5BA) else Color(0xFFA8B5A8).copy(alpha = 0.5f),
                                animationSpec = tween(300, easing = EaseInOutQuad),
                                label = "nav_icon_color"
                            )
                            val textColor by animateColorAsState(
                                targetValue = if (isSelected) Color(0xFFA8D5BA) else Color(0xFFA8B5A8).copy(alpha = 0.5f),
                                animationSpec = tween(300, easing = EaseInOutQuad),
                                label = "nav_text_color"
                            )
                            val itemBgAlpha by animateFloatAsState(
                                targetValue = if (isSelected) 0.12f else 0f,
                                animationSpec = tween(300, easing = EaseInOutQuad),
                                label = "nav_bg_alpha"
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(Color(0xFFA8D5BA).copy(alpha = itemBgAlpha))
                                    .clickable { currentTab = tab },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.title,
                                        tint = iconColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = tab.title,
                                        fontSize = 10.sp,
                                        color = textColor,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
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
                                completedInvitations = completedInvitations,
                                isGenerating = isGenerating,
                                generationStatus = generationStatus,
                                onOpenCustomizer = { showCustomizerSheet = true },
                                onCompleteInvitation = { activeReflectionInvitation = it },
                                onSkipInvitation = { viewModel.skipAssignment(it.id) },
                                onResetAllData = { viewModel.resetAllData() }
                            )
                        }
                        DitchLoopyTab.CHECKIN -> {
                            CheckInScreen(
                                viewModel = viewModel
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
                        DitchLoopyTab.SETTINGS -> {
                            SettingsScreen(
                                viewModel = viewModel,
                                userState = userState
                            )
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
                            celebratedInvitation = invitation
                            activeReflectionInvitation = null
                        }
                    )
                }

                // Celebration/Animation Dialog for completed tasks
                if (celebratedInvitation != null) {
                    CelebrationDialog(
                        invitation = celebratedInvitation!!,
                        onDismiss = { celebratedInvitation = null }
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

    val driftProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "drift"
    )

    val particles = remember {
        List(18) { index ->
            val seedX = (index * 0.17f) % 1f
            val seedY = (index * 0.23f) % 1f
            val pSize = 5f + (index % 4) * 4f // size 5f to 17f
            val color = if (index % 4 == 0) Color(0xFFC87A53) else Color(0xFFA8D5BA) // Terracotta or SageBright
            val speedY = 0.08f + (index % 3) * 0.04f
            val speedX = 0.03f + (index % 2) * 0.02f
            Triple(Offset(seedX, seedY), pSize, Pair(color, Pair(speedX, speedY)))
        }
    }

    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        // Draw the background pulsing gradient
        val center = Offset(size.width * 0.7f, size.height * 0.2f)
        val radius = size.width * 0.5f * scale
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF243324).copy(alpha = opacity), // Moss Green with animated opacity
                    Color(0xFFA8D5BA).copy(alpha = opacity * 0.5f), // Sage highlight
                    Color.Transparent
                ),
                center = center,
                radius = radius
            ),
            center = center,
            radius = radius
        )

        // Draw drifting sparks / canopy dust
        particles.forEachIndexed { idx, p ->
            val seedX = p.first.x
            val seedY = p.first.y
            val pSize = p.second
            val color = p.third.first
            val speedX = p.third.second.first
            val speedY = p.third.second.second

            // Vertical drift (upward)
            val currentYFraction = (seedY - driftProgress * speedY) % 1.0f
            val finalYFraction = if (currentYFraction < 0f) currentYFraction + 1.0f else currentYFraction

            // Horizontal sway (sine wave)
            val sway = kotlin.math.sin(driftProgress * 2 * Math.PI.toFloat() + idx) * speedX
            val currentXFraction = (seedX + sway) % 1.0f
            val finalXFraction = if (currentXFraction < 0f) currentXFraction + 1.0f else currentXFraction

            val x = finalXFraction * size.width
            val y = finalYFraction * size.height

            // Alpha fades near boundaries to avoid abrupt pop-in/pop-out
            val edgeAlpha = kotlin.math.sin(finalYFraction * Math.PI.toFloat()) * kotlin.math.sin(finalXFraction * Math.PI.toFloat())
            val pulseAlpha = (0.4f + 0.6f * kotlin.math.abs(kotlin.math.sin(driftProgress * 2 * Math.PI.toFloat() * 2f + idx)))
            val finalAlpha = (edgeAlpha * pulseAlpha * 0.4f).coerceIn(0f, 0.8f)

            drawCircle(
                color = color.copy(alpha = finalAlpha),
                radius = pSize,
                center = Offset(x, y)
            )
        }
    }
}

@Composable
fun ZenBreathingWidget() {
    var isActive by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(0) } // 0: Idle, 1: Inhale, 2: Hold In, 3: Exhale, 4: Hold Out
    var secondsLeft by remember { mutableStateOf(0) }

    LaunchedEffect(isActive) {
        if (isActive) {
            while (isActive) {
                // Inhale (4s)
                phase = 1
                for (i in 4 downTo 1) {
                    secondsLeft = i
                    delay(1000)
                }
                // Hold In (4s)
                phase = 2
                for (i in 4 downTo 1) {
                    secondsLeft = i
                    delay(1000)
                }
                // Exhale (4s)
                phase = 3
                for (i in 4 downTo 1) {
                    secondsLeft = i
                    delay(1000)
                }
                // Hold Out (4s)
                phase = 4
                for (i in 4 downTo 1) {
                    secondsLeft = i
                    delay(1000)
                }
            }
        } else {
            phase = 0
            secondsLeft = 0
        }
    }

    val instruction = when (phase) {
        1 -> "Inhale slowly"
        2 -> "Hold your breath"
        3 -> "Exhale gently"
        4 -> "Rest"
        else -> "Tap to begin breathing"
    }

    val targetScale = when (phase) {
        1 -> 1.7f - (secondsLeft - 1) * 0.17f
        2 -> 1.7f
        3 -> 1.0f + (secondsLeft - 1) * 0.17f
        4 -> 1.0f
        else -> 1.0f
    }

    val overallScale by animateFloatAsState(
        targetValue = if (isActive) targetScale else 1.0f,
        animationSpec = tween(1000, easing = EaseInOutQuad),
        label = "overall_scale"
    )

    val colorPhase = when (phase) {
        1 -> Color(0xFFA8D5BA)
        2 -> Color(0xFFC87A53)
        3 -> Color(0xFF6C9E84)
        4 -> Color(0xFF243324)
        else -> Color(0xFFA8D5BA)
    }

    val animatedColorPhase by animateColorAsState(
        targetValue = colorPhase,
        animationSpec = tween(1000, easing = EaseInOutQuad),
        label = "phase_color"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "breathing_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isActive) Color(0xFF2D452D) else Color(0xFF1E261E),
                shape = RoundedCornerShape(28.dp)
            )
            .shadow(if (isActive) 12.dp else 4.dp, RoundedCornerShape(28.dp)),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0F140F)
        ),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SelfImprovement,
                        contentDescription = null,
                        tint = Color(0xFFA8D5BA),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "ORGANIC BREATHING WIDGET",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFA8D5BA),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }

                if (isActive) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF243324), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "ACTIVE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFA8D5BA),
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(170.dp)
                    .clip(CircleShape)
                    .bounceClick { isActive = !isActive }
                    .background(Color(0xFF0A0C0A)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val r = size.width * 0.28f * overallScale

                    if (isActive) {
                        drawCircle(
                            color = animatedColorPhase.copy(alpha = pulseAlpha * 0.4f),
                            radius = r * 1.35f
                        )
                    }

                    drawCircle(
                        color = animatedColorPhase.copy(alpha = 0.15f),
                        radius = r * 1.15f
                    )

                    drawCircle(
                        color = animatedColorPhase,
                        radius = r,
                        style = Stroke(width = 3.dp.toPx())
                    )

                    drawCircle(
                        color = animatedColorPhase.copy(alpha = 0.08f),
                        radius = r * 0.85f
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (isActive) {
                        Text(
                            text = secondsLeft.toString(),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Light,
                            fontFamily = FontFamily.Serif,
                            color = Color(0xFFE8EDE8)
                        )
                        Text(
                            text = "seconds",
                            fontSize = 10.sp,
                            color = Color(0xFFA8B5A8)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.PlayArrow,
                            contentDescription = "Start breathing",
                            tint = Color(0xFFA8D5BA),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = instruction,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Light
                    ),
                    color = Color(0xFFE8EDE8),
                    textAlign = TextAlign.Center
                )
                if (isActive) {
                    Text(
                        text = "Follow the circle. Breathe in harmony.",
                        fontSize = 11.sp,
                        color = Color(0xFFA8B5A8),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    Text(
                        text = "Tap circle to begin a 4-4-4-4 box breathing cycle.",
                        fontSize = 11.sp,
                        color = Color(0xFFA8B5A8),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CosmicSievingLoader(statusText: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "cosmic_siever")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = Color(0xFF2D452D),
                shape = RoundedCornerShape(28.dp)
            )
            .shadow(12.dp, RoundedCornerShape(28.dp)),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0F140F)
        ),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Box(
                modifier = Modifier.size(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = rotation
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                ) {
                    val radius = size.width * 0.45f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFA8D5BA).copy(alpha = 0.35f),
                                Color(0xFF243324).copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        ),
                        radius = radius * 1.2f
                    )

                    drawCircle(
                        color = Color(0xFFA8D5BA),
                        radius = radius,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                floatArrayOf(15f, 15f),
                                0f
                            )
                        )
                    )

                    val sparkCount = 4
                    for (i in 0 until sparkCount) {
                        val angle = (i * (2 * Math.PI) / sparkCount).toFloat()
                        val sparkX = center.x + radius * kotlin.math.cos(angle)
                        val sparkY = center.y + radius * kotlin.math.sin(angle)
                        drawCircle(
                            color = if (i % 2 == 0) Color(0xFFC87A53) else Color(0xFFA8D5BA),
                            radius = 6f,
                            center = Offset(sparkX, sparkY)
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFFA8D5BA),
                    modifier = Modifier
                        .size(32.dp)
                        .graphicsLayer {
                            scaleX = 1f / pulseScale
                            scaleY = 1f / pulseScale
                        }
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = statusText.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    ),
                    color = Color(0xFFA8D5BA)
                )

                Text(
                    text = "Crafting custom daily invitations directly with the Gemini oracle...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFA8B5A8),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun JourneyDashboard(
    weeklyJourney: List<Invitation>,
    completedInvitations: List<Invitation>,
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
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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

                // Visual Stats Bar showing XP and Novelty Score
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F140F), RoundedCornerShape(20.dp))
                        .border(1.dp, Color(0xFF243324), RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val totalXp = completedInvitations.sumOf { it.xp }
                    val avgNovelty = if (completedInvitations.isNotEmpty()) {
                        completedInvitations.map { it.noveltyScore }.average().toInt()
                    } else 0
                    val count = completedInvitations.size

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "TOTAL ESCAPES",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFA8B5A8),
                            fontSize = 8.sp,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$count",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif
                            ),
                            color = Color(0xFFA8D5BA)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(1.dp, 28.dp)
                            .background(Color(0xFF243324))
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "TOTAL XP",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFA8B5A8),
                            fontSize = 8.sp,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$totalXp XP",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif
                            ),
                            color = Color(0xFFA8D5BA)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(1.dp, 28.dp)
                            .background(Color(0xFF243324))
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "AVG NOVELTY",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFD5B8A8),
                            fontSize = 8.sp,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$avgNovelty%",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif
                            ),
                            color = Color(0xFFC87A53)
                        )
                    }
                }
            }
        }

        item {
            ZenBreathingWidget()
        }

        item {
            LifeBalanceCard(completedInvitations = completedInvitations)
        }

        if (isGenerating) {
            item {
                CosmicSievingLoader(generationStatus ?: "Sieving the ambient archives...")
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
            itemsIndexed(weeklyJourney) { index, invitation ->
                InvitationDashboardCard(
                    index = index,
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
fun LifeBalanceCard(
    completedInvitations: List<Invitation>,
    modifier: Modifier = Modifier
) {
    val completedByCategory = completedInvitations.groupBy { it.category.lowercase().trim() }
    val discoverCount = completedByCategory["discover"]?.size ?: 0
    val experienceCount = completedByCategory["experience"]?.size ?: 0
    val reflectCount = completedByCategory["reflect"]?.size ?: 0
    val growCount = completedByCategory["grow"]?.size ?: 0

    val maxTarget = 4f
    val discoverProgress = kotlin.math.min(1f, discoverCount / maxTarget)
    val experienceProgress = kotlin.math.min(1f, experienceCount / maxTarget)
    val reflectProgress = kotlin.math.min(1f, reflectCount / maxTarget)
    val growProgress = kotlin.math.min(1f, growCount / maxTarget)

    val dVal = kotlin.math.max(0.12f, discoverProgress)
    val eVal = kotlin.math.max(0.12f, experienceProgress)
    val rVal = kotlin.math.max(0.12f, reflectProgress)
    val gVal = kotlin.math.max(0.12f, growProgress)

    val dAnim by animateFloatAsState(targetValue = dVal, label = "d_anim")
    val eAnim by animateFloatAsState(targetValue = eVal, label = "e_anim")
    val rAnim by animateFloatAsState(targetValue = rVal, label = "r_anim")
    val gAnim by animateFloatAsState(targetValue = gVal, label = "g_anim")

    val score = (((discoverProgress + experienceProgress + reflectProgress + growProgress) / 4f) * 100f).toInt()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF243324), RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F140F)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "LIFE BALANCE INDEX",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        ),
                        color = Color(0xFFA8D5BA)
                    )
                    Text(
                        text = "Experience Pillars Score",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Light
                        ),
                        color = Color(0xFFE8EDE8)
                    )
                }
                
                Box(
                    modifier = Modifier
                        .background(Color(0xFF243324), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "$score / 100",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFA8D5BA)
                    )
                }
            }

            Text(
                text = "Breaking autopilot loops requires attention to both outward exploration and inward introspection. Expand each pillar's reach.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFA8B5A8)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val maxRadius = size.minDimension * 0.42f

                        val gridLevels = listOf(0.25f, 0.5f, 0.75f, 1f)
                        gridLevels.forEach { level ->
                            val radius = maxRadius * level
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(centerX, centerY - radius)
                                lineTo(centerX + radius, centerY)
                                lineTo(centerX, centerY + radius)
                                lineTo(centerX - radius, centerY)
                                close()
                            }
                            drawPath(
                                path = path,
                                color = Color(0xFF243324).copy(alpha = 0.4f),
                                style = Stroke(width = 1f)
                            )
                        }

                        drawLine(
                            color = Color(0xFF243324).copy(alpha = 0.5f),
                            start = Offset(centerX, centerY - maxRadius),
                            end = Offset(centerX, centerY + maxRadius),
                            strokeWidth = 1f
                        )
                        drawLine(
                            color = Color(0xFF243324).copy(alpha = 0.5f),
                            start = Offset(centerX - maxRadius, centerY),
                            end = Offset(centerX + maxRadius, centerY),
                            strokeWidth = 1f
                        )

                        val userPath = androidx.compose.ui.graphics.Path().apply {
                            moveTo(centerX, centerY - (maxRadius * dAnim))
                            lineTo(centerX + (maxRadius * eAnim), centerY)
                            lineTo(centerX, centerY + (maxRadius * rAnim))
                            lineTo(centerX - (maxRadius * gAnim), centerY)
                            close()
                        }

                        drawPath(
                            path = userPath,
                            color = Color(0xFFA8D5BA).copy(alpha = 0.25f)
                        )

                        drawPath(
                            path = userPath,
                            color = Color(0xFFA8D5BA),
                            style = Stroke(width = 2f)
                        )

                        drawCircle(color = Color(0xFFA8D5BA), radius = 4f, center = Offset(centerX, centerY - (maxRadius * dAnim)))
                        drawCircle(color = Color(0xFFC87A53), radius = 4f, center = Offset(centerX + (maxRadius * eAnim), centerY))
                        drawCircle(color = Color(0xFF6C9E84), radius = 4f, center = Offset(centerX, centerY + (maxRadius * rAnim)))
                        drawCircle(color = Color(0xFFD5B8A8), radius = 4f, center = Offset(centerX - (maxRadius * gAnim), centerY))
                    }
                    
                    Text(
                        text = "$score%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = Color(0xFFA8D5BA).copy(alpha = 0.8f)
                    )
                }

                Column(
                    modifier = Modifier.weight(1.1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LegendItem(
                        name = "Discover",
                        count = discoverCount,
                        color = Color(0xFFA8D5BA),
                        percent = (discoverProgress * 100).toInt()
                    )
                    LegendItem(
                        name = "Experience",
                        count = experienceCount,
                        color = Color(0xFFC87A53),
                        percent = (experienceProgress * 100).toInt()
                    )
                    LegendItem(
                        name = "Reflect",
                        count = reflectCount,
                        color = Color(0xFF6C9E84),
                        percent = (reflectProgress * 100).toInt()
                    )
                    LegendItem(
                        name = "Grow",
                        count = growCount,
                        color = Color(0xFFD5B8A8),
                        percent = (growProgress * 100).toInt()
                    )
                }
            }
        }
    }
}

@Composable
fun LegendItem(
    name: String,
    count: Int,
    color: Color,
    percent: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, androidx.compose.foundation.shape.CircleShape)
        )
        
        Column(verticalArrangement = Arrangement.Center) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = Color(0xFFE8EDE8)
                )
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = color
                )
            }
            Text(
                text = if (count == 1) "$count completion" else "$count completions",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFA8B5A8),
                fontSize = 10.sp
            )
        }
    }
}

fun parseDurationToSeconds(duration: String): Int {
    val clean = duration.lowercase().trim()
    val digits = clean.filter { it.isDigit() }.toIntOrNull() ?: 5
    return when {
        clean.contains("h") -> digits * 3600
        clean.contains("m") -> digits * 60
        clean.contains("s") -> digits
        else -> digits * 60
    }
}

@Composable
fun InvitationDashboardCard(
    index: Int,
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

    val context = LocalContext.current
    var isTimerRunning by remember(invitation.id) { mutableStateOf(false) }
    val totalSeconds = remember(invitation.id, invitation.duration) { parseDurationToSeconds(invitation.duration) }
    var secondsLeft by remember(invitation.id, totalSeconds) { mutableStateOf(totalSeconds) }

    LaunchedEffect(isTimerRunning, secondsLeft) {
        if (isTimerRunning && secondsLeft > 0) {
            delay(1000L)
            secondsLeft -= 1
            if (secondsLeft == 0) {
                isTimerRunning = false
                playSystemSound(context)
                showPresenceNotification(
                    context,
                    title = "DitchLoopy: Moment Achieved!",
                    message = "You have completed your loop escape \"${invitation.title}\"! Tap to complete and reflect."
                )
            }
        }
    }

    var pauseReminded by remember(invitation.id) { mutableStateOf(false) }

    LaunchedEffect(isTimerRunning) {
        if (isTimerRunning) {
            pauseReminded = false
        }
    }

    LaunchedEffect(isTimerRunning, secondsLeft) {
        if (!isTimerRunning && secondsLeft > 0 && secondsLeft < totalSeconds && !pauseReminded) {
            delay(15000L) // Wait 15 seconds to remind the user
            if (!isTimerRunning && secondsLeft > 0 && secondsLeft < totalSeconds) {
                pauseReminded = true
                playSystemSound(context)
                showPresenceNotification(
                    context,
                    title = "DitchLoopy: Escape Paused",
                    message = "Don't slip back into autopilot! Stay present and resume your moment."
                )
            }
        }
    }

    val minutesStr = (secondsLeft / 60).toString().padStart(2, '0')
    val secondsStr = (secondsLeft % 60).toString().padStart(2, '0')
    val timerText = if (totalSeconds >= 3600) {
        val hoursStr = (secondsLeft / 3600).toString().padStart(2, '0')
        val remainingMinutes = ((secondsLeft % 3600) / 60).toString().padStart(2, '0')
        "$hoursStr:$remainingMinutes:$secondsStr"
    } else {
        "$minutesStr:$secondsStr"
    }

    // Staggered enter animation
    val visibleState = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) {
        delay(index * 150L)
        visibleState.targetState = true
    }

    // Completion ripple/glow animation
    var completedTriggered by remember { mutableStateOf(invitation.isCompleted) }
    val rippleProgress = remember { Animatable(0f) }

    LaunchedEffect(invitation.isCompleted) {
        if (invitation.isCompleted && !completedTriggered) {
            completedTriggered = true
            rippleProgress.snapTo(0f)
            rippleProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1500, easing = EaseOutQuad)
            )
        } else if (!invitation.isCompleted) {
            completedTriggered = false
        }
    }

    // Color animations for morphing state
    val startBgColor by animateColorAsState(
        targetValue = if (invitation.isCompleted) Color(0xFF0C110C) else Color(0xFF162016),
        animationSpec = tween(1200, easing = EaseInOutQuad),
        label = "start_bg"
    )
    val endBgColor by animateColorAsState(
        targetValue = if (invitation.isCompleted) Color(0xFF0A0C0A) else Color(0xFF0F140F),
        animationSpec = tween(1200, easing = EaseInOutQuad),
        label = "end_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (invitation.isCompleted) Color(0xFF1A241A) else Color(0xFF2A352A),
        animationSpec = tween(1200, easing = EaseInOutQuad),
        label = "border_color"
    )

    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                slideInVertically(initialOffsetY = { 80 }, animationSpec = spring(stiffness = Spring.StiffnessLow)),
        exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
               slideOutVertically(targetOffsetY = { -80 }, animationSpec = spring(stiffness = Spring.StiffnessLow))
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(24.dp)
                )
                .shadow(4.dp, RoundedCornerShape(24.dp))
                .drawWithContent {
                    drawContent()
                    // Draw the custom SageBright border ripple when completed
                    if (rippleProgress.value > 0f && rippleProgress.value < 1f) {
                        val progress = rippleProgress.value
                        val glowAlpha = (1f - progress) * 0.8f
                        val strokeWidth = 2.dp.toPx() + progress * 8.dp.toPx()
                        drawRoundRect(
                            color = Color(0xFFA8D5BA).copy(alpha = glowAlpha),
                            cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
                            style = Stroke(width = strokeWidth)
                        )
                    }
                }
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
                            colors = listOf(startBgColor, endBgColor)
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

                Spacer(modifier = Modifier.height(16.dp))

                // Start timer that ends exactly when the time of the task is reached
                if (!invitation.isCompleted) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F140F), RoundedCornerShape(16.dp))
                            .border(1.dp, Color(0xFF1E281E), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Timer,
                                    contentDescription = null,
                                    tint = if (isTimerRunning) Color(0xFFA8D5BA) else if (secondsLeft < totalSeconds && secondsLeft > 0) Color(0xFFD5B8A8) else Color(0xFFA8B5A8),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (secondsLeft == 0) "Presence Achieved!" else if (isTimerRunning) "Escaping Autopilot..." else if (secondsLeft < totalSeconds) "⚠️ Escape Paused..." else "Presence Timer",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (secondsLeft == 0) Color(0xFFA8D5BA) else if (isTimerRunning) Color(0xFFA8D5BA) else if (secondsLeft < totalSeconds) Color(0xFFC87A53) else Color(0xFFE8EDE8)
                                )
                            }

                            Text(
                                text = timerText,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (secondsLeft == 0) Color(0xFFA8D5BA) else if (isTimerRunning) Color(0xFFA8D5BA) else if (secondsLeft < totalSeconds) Color(0xFFC87A53) else Color(0xFFA8B5A8)
                            )
                        }

                        // Progress indicator
                        val progressFraction = if (totalSeconds > 0) secondsLeft.toFloat() / totalSeconds.toFloat() else 0f
                        val animatedProgress by animateFloatAsState(
                            targetValue = progressFraction,
                            animationSpec = tween(durationMillis = 300),
                            label = "timer_progress"
                        )
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = if (isTimerRunning) Color(0xFFA8D5BA) else if (secondsLeft < totalSeconds && secondsLeft > 0) Color(0xFFC87A53) else Color(0xFFA8D5BA),
                            trackColor = Color(0xFF1C241C),
                        )

                        if (!isTimerRunning && secondsLeft > 0 && secondsLeft < totalSeconds) {
                            Text(
                                text = "Reminder: Stay mindful! Resume the timer to continue escaping autopilot.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFD5B8A8),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                textAlign = TextAlign.Center
                            )
                        }

                        // Control Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (secondsLeft > 0) {
                                val playPauseInteraction = remember { MutableInteractionSource() }
                                val playPausePressed by playPauseInteraction.collectIsPressedAsState()
                                val playPauseScale by animateFloatAsState(targetValue = if (playPausePressed) 0.95f else 1f, label = "play_pause")

                                Button(
                                    onClick = { isTimerRunning = !isTimerRunning },
                                    interactionSource = playPauseInteraction,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isTimerRunning) Color(0xFF332018) else Color(0xFF243324),
                                        contentColor = if (isTimerRunning) Color(0xFFD5B8A8) else Color(0xFFA8D5BA)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .graphicsLayer {
                                            scaleX = playPauseScale
                                            scaleY = playPauseScale
                                        }
                                ) {
                                    Icon(
                                        imageVector = if (isTimerRunning) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isTimerRunning) "Pause" else "Start Timer",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (secondsLeft < totalSeconds) {
                                    val resetInteraction = remember { MutableInteractionSource() }
                                    val resetPressed by resetInteraction.collectIsPressedAsState()
                                    val resetScale by animateFloatAsState(targetValue = if (resetPressed) 0.95f else 1f, label = "reset")

                                    OutlinedButton(
                                        onClick = {
                                            isTimerRunning = false
                                            secondsLeft = totalSeconds
                                        },
                                        interactionSource = resetInteraction,
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color(0xFFA8B5A8)
                                        ),
                                        border = BorderStroke(1.dp, Color(0xFF243324)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .height(38.dp)
                                            .graphicsLayer {
                                                scaleX = resetScale
                                                scaleY = resetScale
                                            }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Reset", fontSize = 12.sp)
                                    }
                                }
                            } else {
                                // Timer finished!
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color(0xFFA8D5BA),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Time reached! Take a slow breath and capture your presence.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFA8D5BA),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                Spacer(modifier = Modifier.height(4.dp))

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
                        val skipInteraction = remember { MutableInteractionSource() }
                        val skipPressed by skipInteraction.collectIsPressedAsState()
                        val skipScale by animateFloatAsState(targetValue = if (skipPressed) 0.94f else 1f, label = "skip_scale")

                        TextButton(
                            onClick = onSkip,
                            interactionSource = skipInteraction,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = Color(0xFFA8B5A8)
                            ),
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = skipScale
                                    scaleY = skipScale
                                }
                                .testTag("skip_button_${invitation.id}")
                        ) {
                            Text("Skip Moment", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        val completeInteraction = remember { MutableInteractionSource() }
                        val completePressed by completeInteraction.collectIsPressedAsState()
                        val isCompleteEnabled = isTimerRunning || secondsLeft < totalSeconds
                        val completeScale by animateFloatAsState(targetValue = if (completePressed && isCompleteEnabled) 0.94f else 1f, label = "complete_scale")

                        Button(
                            onClick = onComplete,
                            enabled = isCompleteEnabled,
                            interactionSource = completeInteraction,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE8EDE8),
                                contentColor = Color(0xFF0A0C0A),
                                disabledContainerColor = Color(0xFF1E241E),
                                disabledContentColor = Color(0xFF4C584C)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .height(46.dp)
                                .graphicsLayer {
                                    scaleX = completeScale
                                    scaleY = completeScale
                                }
                                .testTag("complete_button_${invitation.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                              )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Complete", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
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

                            val addInteraction = remember { MutableInteractionSource() }
                            val addPressed by addInteraction.collectIsPressedAsState()
                            val addScale by animateFloatAsState(targetValue = if (addPressed) 0.82f else 1f, label = "add_scale")

                            IconButton(
                                onClick = { onAddToWeek(invitation) },
                                interactionSource = addInteraction,
                                modifier = Modifier
                                    .graphicsLayer {
                                        scaleX = addScale
                                        scaleY = addScale
                                    }
                                    .testTag("add_to_week_${invitation.id}")
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
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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

                if (completedInvitations.isNotEmpty()) {
                    // Visual Stats Bar showing XP and Novelty Score
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F140F), RoundedCornerShape(20.dp))
                            .border(1.dp, Color(0xFF243324), RoundedCornerShape(20.dp))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val totalXp = completedInvitations.sumOf { it.xp }
                        val avgNovelty = completedInvitations.map { it.noveltyScore }.average().toInt()
                        val count = completedInvitations.size

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "TOTAL ESCAPES",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFA8B5A8),
                                fontSize = 8.sp,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$count",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Serif
                                ),
                                color = Color(0xFFA8D5BA)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(1.dp, 28.dp)
                                .background(Color(0xFF243324))
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "TOTAL XP",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFA8B5A8),
                                fontSize = 8.sp,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$totalXp XP",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Serif
                                ),
                                color = Color(0xFFA8D5BA)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(1.dp, 28.dp)
                                .background(Color(0xFF243324))
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "AVG NOVELTY",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFD5B8A8),
                                fontSize = 8.sp,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$avgNovelty%",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Serif
                                ),
                                color = Color(0xFFC87A53)
                            )
                        }
                    }
                }
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
            itemsIndexed(completedInvitations) { index, invitation ->
                val reflection = reflections.find { it.invitationId == invitation.id }
                PolaroidMemoryCard(
                    index = index,
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
    index: Int,
    invitation: Invitation,
    reflection: Reflection?,
    onUndo: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val dateString = reflection?.let { dateFormat.format(Date(it.timestamp)) } ?: ""

    // Staggered enter animation
    val visibleState = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) {
        delay(index * 120L)
        visibleState.targetState = true
    }

    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                slideInVertically(initialOffsetY = { 60 }, animationSpec = spring(stiffness = Spring.StiffnessLow)),
        exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
               slideOutVertically(targetOffsetY = { -60 }, animationSpec = spring(stiffness = Spring.StiffnessLow))
    ) {
        // Redesigned: Obsidian-Linen Polaroid variant!
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(8.dp))
                .border(
                    width = 1.dp,
                    color = Color(0xFF2A352A), // Sophisticated moss/sage border
                    shape = RoundedCornerShape(8.dp)
                )
                .testTag("polaroid_${invitation.id}"),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF0F140F) // Obsidian-Linen dark stock cardboard background
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // "Photo" Area with glowing amber-sepia gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.6f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF241C15), // Deep warm sepia dark
                                    Color(0xFF16110D)  // Pitch black sepia
                                )
                            )
                        )
                        .border(
                            width = 0.5.dp,
                            color = Color(0xFFC56D48).copy(alpha = 0.3f), // Glowing copper line
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.Camera,
                            contentDescription = null,
                            tint = Color(0xFFC56D48).copy(alpha = 0.6f),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = invitation.title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Light
                            ),
                            color = Color(0xFFE8EDE8), // Silver text
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Vibe: ${reflection?.mood ?: "Calm"}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC56D48),
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Reflection Text mimicking handwriting
                Text(
                    text = reflection?.text ?: "I stepped away and remembered who I was.",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        letterSpacing = 0.3.sp
                    ),
                    color = Color(0xFFA8D5BA), // SageBright handwriting-styled ink
                    fontFamily = FontFamily.Cursive, // pair display with cursive/organic handwriting font
                    lineHeight = 24.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                HorizontalDivider(color = Color(0xFF2A352A), thickness = 1.dp)

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
                            color = Color(0xFFA8B5A8),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Original Assignment: ${invitation.category}",
                            fontSize = 9.sp,
                            color = Color(0xFFA8B5A8).copy(alpha = 0.6f)
                        )
                    }

                    val undoInteraction = remember { MutableInteractionSource() }
                    val undoPressed by undoInteraction.collectIsPressedAsState()
                    val undoScale by animateFloatAsState(targetValue = if (undoPressed) 0.94f else 1f, label = "undo_scale")

                    TextButton(
                        onClick = onUndo,
                        interactionSource = undoInteraction,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color(0xFFC56D48)
                        ),
                        modifier = Modifier.graphicsLayer {
                            scaleX = undoScale
                            scaleY = undoScale
                        }
                    ) {
                        Text("Undo Clear", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: DitchLoopyViewModel,
    userState: UserState
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUpMode by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    fun triggerGoogleSignIn() {
        coroutineScope.launch {
            try {
                val credentialManager = androidx.credentials.CredentialManager.create(context)
                val googleIdOption = com.google.android.libraries.identity.googleid.GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId("dummy-client-id-for-sandbox.apps.googleusercontent.com")
                    .setAutoSelectEnabled(false)
                    .build()
                val request = androidx.credentials.GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()
                
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential
                if (credential is androidx.credentials.CustomCredential && credential.type == com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.createFrom(credential.data)
                    viewModel.signInWithGoogleToken(googleIdTokenCredential.idToken)
                } else {
                    viewModel.signInWithGoogleToken("")
                }
            } catch (e: Exception) {
                // Safe simulation on Sandbox / emulator environment
                viewModel.signInWithGoogleToken("")
            }
        }
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
                    text = "Settings",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Light
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Configure your vessel, security, and connection engine.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFA8B5A8)
                )
            }
        }

        // 1. Account & Security Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = Color(0xFF2A352A),
                        shape = RoundedCornerShape(24.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F140F)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = null,
                            tint = Color(0xFFA8D5BA)
                        )
                        Text(
                            text = "Identity & Sync",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE8EDE8)
                        )
                    }

                    if (userState.isLoggedIn && !userState.isAnonymous) {
                        // Logged In user details
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF162016), RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            if (userState.photoUrl != null) {
                                AsyncImage(
                                    model = userState.photoUrl,
                                    contentDescription = "Profile Photo",
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .border(1.dp, Color(0xFFA8D5BA), CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color(0xFF243324), CircleShape)
                                        .border(1.dp, Color(0xFFA8D5BA), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = (userState.displayName ?: "J").take(1).uppercase(),
                                        color = Color(0xFFA8D5BA),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = userState.displayName ?: "Astral Companion",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE8EDE8)
                                )
                                Text(
                                    text = userState.email ?: "No email assigned",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFA8B5A8)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (userState.isSandboxMode) Color(0xFF332424) else Color(0xFF243324),
                                            RoundedCornerShape(50)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (userState.isSandboxMode) "LOCAL SANDBOX" else "CLOUD VERIFIED",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (userState.isSandboxMode) Color(0xFFD5A8A8) else Color(0xFFA8D5BA),
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { viewModel.signOut() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF332424),
                                contentColor = Color(0xFFD5A8A8)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text("Disconnect Identity", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        // Logged Out / Anonymous
                        Text(
                            text = "Secure your journey and synchronize your reflections across all devices safely.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFA8B5A8)
                        )

                        // Google Sign-In Button
                        Button(
                            onClick = { triggerGoogleSignIn() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE8EDE8),
                                contentColor = Color(0xFF0A0C0A)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("google_sign_in_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CloudQueue,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Continue with Google",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        // Separator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF2A352A))
                            Text("OR SECURE WITH EMAIL", fontSize = 9.sp, color = Color(0xFFA8B5A8), fontWeight = FontWeight.Bold)
                            HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF2A352A))
                        }

                        // Email Text Field
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email Vessel") },
                            leadingIcon = { Icon(Icons.Outlined.Mail, contentDescription = null, tint = Color(0xFFA8B5A8)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFA8D5BA),
                                unfocusedBorderColor = Color(0xFF2A352A),
                                focusedLabelColor = Color(0xFFA8D5BA),
                                unfocusedLabelColor = Color(0xFFA8B5A8)
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Password Text Field
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Secret Password") },
                            leadingIcon = { Icon(Icons.Outlined.VpnKey, contentDescription = null, tint = Color(0xFFA8B5A8)) },
                            trailingIcon = {
                                val image = if (passwordVisible) Icons.Filled.Close else Icons.Filled.Refresh
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(imageVector = image, contentDescription = null, tint = Color(0xFFA8B5A8))
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFA8D5BA),
                                unfocusedBorderColor = Color(0xFF2A352A),
                                focusedLabelColor = Color(0xFFA8D5BA),
                                unfocusedLabelColor = Color(0xFFA8B5A8)
                            ),
                            singleLine = true,
                            visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { viewModel.signInWithEmailAndPassword(email, password, isSignUp = isSignUpMode) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF243324),
                                    contentColor = Color(0xFFA8D5BA)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                enabled = email.isNotEmpty() && password.isNotEmpty()
                            ) {
                                Text(
                                    text = if (isSignUpMode) "Register Vault" else "Access Vault",
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            TextButton(
                                onClick = { isSignUpMode = !isSignUpMode },
                                modifier = Modifier.align(Alignment.CenterVertically)
                            ) {
                                Text(
                                    text = if (isSignUpMode) "Need Login?" else "Need Signup?",
                                    color = Color(0xFFA8D5BA),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Engine API Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = Color(0xFF2A352A),
                        shape = RoundedCornerShape(24.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F140F)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = null,
                            tint = Color(0xFFA8D5BA)
                        )
                        Text(
                            text = "Mission Intelligence Engine",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE8EDE8)
                        )
                    }

                    val keyAvailable = GeminiService.isApiKeyAvailable()

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (keyAvailable) Color(0xFF1C2D1F) else Color(0xFF2E241F),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Icon(
                            imageVector = if (keyAvailable) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
                            contentDescription = null,
                            tint = if (keyAvailable) Color(0xFFA8D5BA) else Color(0xFFD5A88A)
                        )
                        Column {
                            Text(
                                text = if (keyAvailable) "Gemini Engine Connected" else "Gemini API Key Offline",
                                fontWeight = FontWeight.Bold,
                                color = if (keyAvailable) Color(0xFFA8D5BA) else Color(0xFFD5A88A),
                                fontSize = 12.sp
                            )
                            Text(
                                text = if (keyAvailable) "Personalised generation active." else "Using curated local guides fallback.",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (keyAvailable) Color(0xFFA8D5BA).copy(alpha = 0.8f) else Color(0xFFD5A88A).copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }

        // 4. Safe Reset Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = Color(0xFF332424),
                        shape = RoundedCornerShape(24.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F140F)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = Color(0xFFD5A8A8)
                        )
                        Text(
                            text = "Danger Zone",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE8EDE8)
                        )
                    }

                    Text(
                        text = "Resetting the vessel clears all active paths, reflection logs, and returns the journey to initial status.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFA8B5A8)
                    )

                    Button(
                        onClick = { viewModel.resetAllData() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF332424),
                            contentColor = Color(0xFFD5A8A8)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Reset Vessel Database", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun DitchLoopyOnboardingScreen(
    viewModel: DitchLoopyViewModel,
    onEnterJourney: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var currentPage by remember { mutableStateOf(0) }
    
    // Page 0: Breathing states
    val breatheInteractionSource = remember { MutableInteractionSource() }
    val isPressed by breatheInteractionSource.collectIsPressedAsState()
    var breathCyclesCompleted by remember { mutableStateOf(0) }
    var breatheProgress by remember { mutableStateOf(0f) }
    var breathingDirection by remember { mutableStateOf("Hold to Inhale") } // Inhale, Exhale, Hold

    // Handle tactile breath holding
    LaunchedEffect(isPressed) {
        if (isPressed) {
            breathingDirection = "Inhaling... Keep holding"
            while (breatheProgress < 1f && isPressed) {
                delay(30)
                breatheProgress = kotlin.math.min(1f, breatheProgress + 0.015f)
            }
            if (breatheProgress >= 1f) {
                breathingDirection = "Exhale! Release your finger"
            }
        } else {
            if (breatheProgress >= 0.95f) {
                breathCyclesCompleted++
            }
            breathingDirection = "Hold to Inhale"
            while (breatheProgress > 0f && !isPressed) {
                delay(30)
                breatheProgress = kotlin.math.max(0f, breatheProgress - 0.02f)
            }
        }
    }

    // Page 1: Autopilot Traps
    val traps = remember {
        listOf(
            Triple("📱 Endless Scrolling", "Chasing micro-dopamine hits in loop feeds.", Color(0xFFA8D5BA)),
            Triple("🔔 Notification Rush", "Instantly reacting to red badges and alerts.", Color(0xFFC87A53)),
            Triple("📺 Screen Blindness", "Staring at screens all day without stepping outdoors.", Color(0xFF6C9E84)),
            Triple("⏳ Mental Hustle", "Always chasing the next hour, skipping the present.", Color(0xFFD5B8A8))
        )
    }
    val selectedTraps = remember { mutableStateListOf<Int>() }

    // Page 2: Identity & Pledge
    var companionName by remember { mutableStateOf("") }
    var pledgeSigned by remember { mutableStateOf(false) }

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
            .navigationBarsPadding()
            .statusBarsPadding()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    val width by animateDpAsState(targetValue = if (currentPage == index) 24.dp else 8.dp, label = "indicator")
                    val color = if (currentPage == index) Color(0xFFA8D5BA) else Color(0xFF243324)
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(6.dp)
                            .width(width)
                            .background(color, RoundedCornerShape(3.dp))
                    )
                }
            }

            // Core Page Content
            Crossfade(
                targetState = currentPage,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                label = "page_transition"
            ) { page ->
                when (page) {
                    0 -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Spa,
                                contentDescription = null,
                                tint = Color(0xFFA8D5BA),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Arrive Here First",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Light
                                ),
                                color = Color(0xFFE8EDE8),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "The loop of autopilot is fast. To break it, we begin with a slower rhythm. Practice 2 cycles of slow, deep breathing.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFA8B5A8),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(40.dp))

                            // Interactive breathing ring
                            Box(
                                modifier = Modifier
                                    .size(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // Pulsing ambient rings
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val centerX = size.width / 2f
                                    val centerY = size.height / 2f
                                    val baseRadius = 60.dp.toPx()
                                    val activeRadius = baseRadius + (40.dp.toPx() * breatheProgress)

                                    // Outer soft glow
                                    drawCircle(
                                        color = Color(0xFFA8D5BA).copy(alpha = 0.08f + (0.15f * breatheProgress)),
                                        radius = activeRadius + 20.dp.toPx(),
                                        center = Offset(centerX, centerY)
                                    )
                                    // Intermediate stroke
                                    drawCircle(
                                        color = Color(0xFFA8D5BA).copy(alpha = 0.3f),
                                        radius = activeRadius,
                                        center = Offset(centerX, centerY),
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                    // Base solid core
                                    drawCircle(
                                        color = Color(0xFF243324).copy(alpha = 0.8f),
                                        radius = baseRadius,
                                        center = Offset(centerX, centerY)
                                    )
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = breathingDirection,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFFE8EDE8),
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "$breathCyclesCompleted / 2 cycles",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFA8D5BA)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(40.dp))

                            // Breathing Touch Pad
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.8f)
                                    .height(64.dp)
                                    .clip(RoundedCornerShape(32.dp))
                                    .border(1.dp, Color(0xFF243324), RoundedCornerShape(32.dp))
                                    .background(Color(0xFF0F140F))
                                    .clickable(
                                        interactionSource = breatheInteractionSource,
                                        indication = null
                                    ) { /* empty click, handled by interactionSource */ },
                                contentAlignment = Alignment.Center
                            ) {
                                val padText = if (isPressed) "Inhaling deeply..." else "TOUCH & HOLD HERE"
                                Text(
                                    text = padText,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp
                                    ),
                                    color = if (isPressed) Color(0xFFA8D5BA) else Color(0xFFA8B5A8)
                                )
                            }
                        }
                    }
                    1 -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Explore,
                                contentDescription = null,
                                tint = Color(0xFFA8D5BA),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "What Loops Trap You?",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Light
                                ),
                                color = Color(0xFFE8EDE8),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "DitchLoopy invites you to break specific routines. Choose the autopilot triggers you face most often.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFA8B5A8),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(32.dp))

                            // Grid of Traps
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                traps.forEachIndexed { index, (title, desc, accentColor) ->
                                    val isSelected = selectedTraps.contains(index)
                                    val cardBorderColor = if (isSelected) Color(0xFFA8D5BA) else Color(0xFF243324)
                                    val cardBgColor = if (isSelected) Color(0xFF162016) else Color(0xFF0F140F)
                                    
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (isSelected) selectedTraps.remove(index) else selectedTraps.add(index)
                                            }
                                            .border(1.dp, cardBorderColor, RoundedCornerShape(16.dp)),
                                        colors = CardDefaults.cardColors(containerColor = cardBgColor),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .padding(16.dp)
                                                .fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .border(1.dp, if (isSelected) Color(0xFFA8D5BA) else Color(0xFFA8B5A8), CircleShape)
                                                    .background(if (isSelected) Color(0xFFA8D5BA) else Color.Transparent, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Check,
                                                        contentDescription = null,
                                                        tint = Color(0xFF0A0C0A),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                            Column {
                                                Text(
                                                    text = title,
                                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                                    color = Color(0xFFE8EDE8)
                                                )
                                                Text(
                                                    text = desc,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color(0xFFA8B5A8)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Fingerprint,
                                contentDescription = null,
                                tint = Color(0xFFA8D5BA),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Declare Your Intent",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Light
                                ),
                                color = Color(0xFFE8EDE8),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Sign your presence contract to commence your transition to sensory awareness.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFA8B5A8),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(28.dp))

                            OutlinedTextField(
                                value = companionName,
                                onValueChange = { companionName = it },
                                label = { Text("Companion Name") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFA8D5BA),
                                    unfocusedBorderColor = Color(0xFF243324),
                                    focusedTextColor = Color(0xFFE8EDE8),
                                    unfocusedTextColor = Color(0xFFE8EDE8),
                                    focusedLabelColor = Color(0xFFA8D5BA),
                                    unfocusedLabelColor = Color(0xFFA8B5A8),
                                    focusedContainerColor = Color(0xFF0F140F),
                                    unfocusedContainerColor = Color(0xFF0F140F)
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF162016)),
                                border = BorderStroke(1.dp, Color(0xFF243324)),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "\"I pledge to notice the physical light, to answer the silence, to seek novelty beyond screens, and to anchor my moments of presence.\"",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontFamily = FontFamily.Serif,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                            fontWeight = FontWeight.Light,
                                            lineHeight = 24.sp
                                        ),
                                        color = Color(0xFFA8D5BA),
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.clickable { pledgeSigned = !pledgeSigned }
                                    ) {
                                        Checkbox(
                                            checked = pledgeSigned,
                                            onCheckedChange = { pledgeSigned = it },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = Color(0xFFA8D5BA),
                                                uncheckedColor = Color(0xFFA8B5A8)
                                            )
                                        )
                                        Text(
                                            text = "I sign this pledge",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color(0xFFE8EDE8)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Navigation Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentPage > 0) {
                    OutlinedButton(
                        onClick = { currentPage-- },
                        border = BorderStroke(1.dp, Color(0xFF243324)),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFA8D5BA))
                    ) {
                        Text("Back")
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                val isNextEnabled = when (currentPage) {
                    0 -> breathCyclesCompleted >= 2
                    1 -> selectedTraps.isNotEmpty()
                    2 -> companionName.isNotBlank() && pledgeSigned
                    else -> false
                }

                val buttonText = if (currentPage == 2) "Ditch the Loop" else "Next"

                Button(
                    onClick = {
                        if (currentPage < 2) {
                            currentPage++
                        } else {
                            val nameToSave = companionName.trim()
                            viewModel.signInWithEmailAndPassword(
                                email = "$nameToSave@ditchloopy.com",
                                password = "secured_password_123",
                                isSignUp = true
                            )
                            onEnterJourney()
                        }
                    },
                    enabled = isNextEnabled,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isNextEnabled) Color(0xFFA8D5BA) else Color(0xFF243324),
                        contentColor = Color(0xFF0A0C0A),
                        disabledContainerColor = Color(0xFF1E261E),
                        disabledContentColor = Color(0xFF4A5A4A)
                    ),
                    modifier = Modifier.testTag("onboarding_next_button")
                ) {
                    Text(
                        text = buttonText,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckInScreen(
    viewModel: DitchLoopyViewModel
) {
    val dailyCheckIns by viewModel.dailyCheckIns.collectAsStateWithLifecycle()
    
    var reflectionText by remember { mutableStateOf("") }
    var selectedMood by remember { mutableStateOf("Calm") }
    var selectedCategory by remember { mutableStateOf("Quietness") }
    var presenceRating by remember { mutableStateOf(3) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val moods = listOf(
        Pair("Calm", "🌌"),
        Pair("Grateful", "✨"),
        Pair("Inspired", "🔥"),
        Pair("Curious", "🌿"),
        Pair("Nostalgic", "🍂"),
        Pair("Restless", "⏳"),
        Pair("Tired", "💤")
    )

    val categories = listOf("Quietness", "Nature", "Connection", "Small Win", "Wonder", "Creative", "Other")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentPadding = PaddingValues(vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "DAILY CHECK-IN",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    ),
                    color = Color(0xFFA8D5BA)
                )
                Text(
                    text = "Reflect on a Moment",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Light
                    ),
                    color = Color(0xFFE8EDE8)
                )
                Text(
                    text = "Ditch autopilot loops by capturing one specific, sensory-rich moment of presence from your day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFA8B5A8)
                )
            }
        }

        // New Check-In Form
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF243324), RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F140F)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Mood Row
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Current Vibe / Mood",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFE8EDE8)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            moods.forEach { (name, emoji) ->
                                val isSelected = selectedMood == name
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) Color(0xFF243324) else Color(0xFF161A16))
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) Color(0xFFA8D5BA) else Color.Transparent,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { selectedMood = name }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = emoji, fontSize = 14.sp)
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected) Color(0xFFA8D5BA) else Color(0xFFA8B5A8)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Category Row
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Moment Theme",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFE8EDE8)
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            categories.forEach { name ->
                                val isSelected = selectedCategory == name
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Color(0xFF243324).copy(alpha = 0.5f) else Color.Transparent)
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) Color(0xFFA8D5BA) else Color(0xFF243324),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { selectedCategory = name }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) Color(0xFFA8D5BA) else Color(0xFFA8B5A8)
                                    )
                                }
                            }
                        }
                    }

                    // Presence rating dots (1 to 5)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Level of Presence",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFE8EDE8)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            (1..5).forEach { rate ->
                                val isSelected = rate <= presenceRating
                                val color = if (isSelected) Color(0xFFA8D5BA) else Color(0xFF243324)
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(color.copy(alpha = if (isSelected) 0.2f else 0.4f))
                                        .border(1.dp, color, CircleShape)
                                        .clickable { presenceRating = rate },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = rate.toString(),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (isSelected) Color(0xFFA8D5BA) else Color(0xFFA8B5A8)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = when (presenceRating) {
                                    1 -> "Fading Autopilot"
                                    2 -> "Brief Awareness"
                                    3 -> "Centered"
                                    4 -> "Fully Immersed"
                                    5 -> "Deep Cosmic Union"
                                    else -> "Neutral"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFA8D5BA),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Text input field for reflection
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Meaningful Moment Reflection",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFE8EDE8)
                        )
                        OutlinedTextField(
                            value = reflectionText,
                            onValueChange = { reflectionText = it },
                            placeholder = {
                                Text(
                                    text = "Write down one specific sensory moment... how the air felt, the direct sunlight, the quiet sound of a passing breeze, or the simple taste of coffee...",
                                    fontSize = 12.sp,
                                    color = Color(0xFFA8B5A8).copy(alpha = 0.5f)
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFA8D5BA),
                                unfocusedBorderColor = Color(0xFF243324),
                                focusedTextColor = Color(0xFFE8EDE8),
                                unfocusedTextColor = Color(0xFFE8EDE8),
                                focusedContainerColor = Color(0xFF0A0C0A),
                                unfocusedContainerColor = Color(0xFF0A0C0A)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .testTag("meaningful_moment_input")
                        )
                    }

                    // Success Feedback message
                    successMessage?.let { msg ->
                        Text(
                            text = msg,
                            color = Color(0xFFA8D5BA),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }

                    // Submit Button
                    Button(
                        onClick = {
                            if (reflectionText.isNotBlank()) {
                                viewModel.addDailyCheckIn(
                                    text = reflectionText.trim(),
                                    mood = selectedMood,
                                    category = selectedCategory,
                                    presenceRating = presenceRating
                                )
                                reflectionText = ""
                                successMessage = "Moment anchored successfully into the present."
                                coroutineScope.launch {
                                    delay(3000)
                                    successMessage = null
                                }
                            }
                        },
                        enabled = reflectionText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFA8D5BA),
                            contentColor = Color(0xFF0A0C0A),
                            disabledContainerColor = Color(0xFF243324),
                            disabledContentColor = Color(0xFF4A5A4A)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("anchor_moment_button")
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text("Anchor This Moment", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Presence History Heading
        item {
            Text(
                text = "My Anchored Moments History",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Light
                ),
                color = Color(0xFFE8EDE8),
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (dailyCheckIns.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF243324).copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F140F).copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Timeline,
                            contentDescription = null,
                            tint = Color(0xFFA8B5A8).copy(alpha = 0.6f),
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "No Anchors Recorded Yet",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFE8EDE8)
                        )
                        Text(
                            text = "Your anchored presence reflections will be preserved here as a physical record of sensory mindfulness.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFA8B5A8),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(dailyCheckIns) { checkIn ->
                val dateStr = remember(checkIn.timestamp) {
                    val sdf = SimpleDateFormat("EEE, MMM dd yyyy • h:mm a", java.util.Locale.getDefault())
                    sdf.format(java.util.Date(checkIn.timestamp))
                }

                val emoji = when (checkIn.mood) {
                    "Calm" -> "🌌"
                    "Grateful" -> "✨"
                    "Inspired" -> "🔥"
                    "Curious" -> "🌿"
                    "Nostalgic" -> "🍂"
                    "Restless" -> "⏳"
                    "Tired" -> "💤"
                    else -> "🧘"
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF243324), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0C0A)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF162016), CircleShape)
                                        .padding(6.dp)
                                ) {
                                    Text(text = emoji, fontSize = 14.sp)
                                }
                                Column {
                                    Text(
                                        text = checkIn.mood,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFFA8D5BA)
                                    )
                                    Text(
                                        text = dateStr,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFA8B5A8),
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            // Presence ratings representation as dots
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                repeat(5) { rateIndex ->
                                    val active = rateIndex < checkIn.presenceRating
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(
                                                color = if (active) Color(0xFFA8D5BA) else Color(0xFF243324),
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }
                        }

                        // Reflection Text
                        Text(
                            text = checkIn.text,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Serif,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            ),
                            color = Color(0xFFE8EDE8)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Category Tag
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF243324).copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                    .border(1.dp, Color(0xFF243324), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = checkIn.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFA8B5A8),
                                    fontSize = 10.sp
                                )
                            }

                            // Delete Action
                            IconButton(
                                onClick = { viewModel.deleteDailyCheckIn(checkIn.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Delete check-in",
                                    tint = Color(0xFFC87A53),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
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
                    val tailorInteraction = remember { MutableInteractionSource() }
                    val tailorPressed by tailorInteraction.collectIsPressedAsState()
                    val tailorScale by animateFloatAsState(targetValue = if (tailorPressed) 0.94f else 1f, label = "tailor_scale")

                    Button(
                        onClick = {
                            onTailorJourney(selectedMood, selectedPreference, selectedEnergy, userThoughts)
                        },
                        interactionSource = tailorInteraction,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .graphicsLayer {
                                scaleX = tailorScale
                                scaleY = tailorScale
                            }
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
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
                            text = "Preserve the Memory",
                            style = MaterialTheme.typography.titleLarge
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                item {
                    Text(
                        text = "Reflecting on: \"$invitationTitle\"",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Mood selector
                item {
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
                }

                // Reflection Text Field
                item {
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
                }

                // Submit Button
                item {
                    val saveInteraction = remember { MutableInteractionSource() }
                    val savePressed by saveInteraction.collectIsPressedAsState()
                    val saveScale by animateFloatAsState(targetValue = if (savePressed) 0.94f else 1f, label = "save_scale")

                    Button(
                        onClick = {
                            val finalReflection = reflectionText.ifEmpty { "I stepped away from autopilot and found peace in the details." }
                            onSaveReflection(finalReflection, selectedMood)
                        },
                        interactionSource = saveInteraction,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .graphicsLayer {
                                scaleX = saveScale
                                scaleY = saveScale
                            }
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
}

@Composable
fun CelebrationDialog(
    invitation: Invitation,
    onDismiss: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1800, easing = EaseOutCubic)
        )
    }

    // Generate random particle velocities and angles once
    val particles = remember {
        List(35) { index ->
            val angle = (index * 360f / 35) + (Math.random() * 15f).toFloat()
            val speed = 100f + (Math.random() * 250f).toFloat()
            val color = when (index % 3) {
                0 -> Color(0xFFA8D5BA) // Sage
                1 -> Color(0xFFC87A53) // Terracotta
                else -> Color(0xFFE8EDE8) // Warm cream
            }
            val radius = 4f + (Math.random() * 8f).toFloat()
            Triple(angle, speed, Pair(color, radius))
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            color = Color(0xFF0F140F)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Celebration animation canvas
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val currentProgress = progress.value
                            
                            // Draw spinning background glow
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFFA8D5BA).copy(alpha = 0.2f * (1f - currentProgress)),
                                        Color.Transparent
                                    )
                                ),
                                radius = size.width * 0.3f * (1f + currentProgress * 0.5f)
                            )

                            // Draw particle burst
                            particles.forEach { (angle, speed, styling) ->
                                val color = styling.first
                                val baseRadius = styling.second
                                
                                // Physics: movement is angle * speed * progress
                                val radians = Math.toRadians(angle.toDouble())
                                val distance = speed * currentProgress
                                val dx = (distance * kotlin.math.cos(radians)).toFloat()
                                val dy = (distance * kotlin.math.sin(radians)).toFloat() + (currentProgress * currentProgress * 80f) // gravity pull downwards
                                
                                val alpha = 1f - (currentProgress * currentProgress)
                                
                                drawCircle(
                                    color = color.copy(alpha = kotlin.math.max(0f, alpha)),
                                    radius = baseRadius * (1f - currentProgress * 0.5f),
                                    center = Offset(center.x + dx, center.y + dy)
                                )
                            }
                        }

                        // Rotating/scaling crown/trophy or star icon
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = "Celebration",
                            tint = Color(0xFFA8D5BA),
                            modifier = Modifier
                                .size(64.dp)
                                .graphicsLayer {
                                    scaleX = 1f + (1f - progress.value) * 0.5f
                                    scaleY = 1f + (1f - progress.value) * 0.5f
                                    rotationZ = progress.value * 720f
                                }
                        )
                    }
                }

                // Header
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "AESTHETIC ESCAPE RECORDED",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.5.sp
                            ),
                            color = Color(0xFFA8D5BA)
                        )

                        Text(
                            text = "Moment Achieved!",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Light
                            ),
                            color = Color(0xFFE8EDE8),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Stats Cards (XP and Novelty Score)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // XP Earned card
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, Color(0xFF243324), RoundedCornerShape(16.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A13)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "EXPERIENCE EARNED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFA8B5A8),
                                    fontSize = 8.sp,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "+${invitation.xp} XP",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Serif
                                    ),
                                    color = Color(0xFFA8D5BA)
                                )
                            }
                        }

                        // Novelty Score card
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, Color(0xFF332A24), RoundedCornerShape(16.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1613)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "NOVELTY SCORE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFD5B8A8),
                                    fontSize = 8.sp,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${invitation.noveltyScore}%",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Serif
                                    ),
                                    color = Color(0xFFC87A53)
                                )
                            }
                        }
                    }
                }

                // Reflection info / affirmation
                item {
                    Text(
                        text = "You broke the loop of autopilot by embarking on \"${invitation.title}\". Your presence expands the local aesthetic archive.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFA8B5A8),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Continue Button
                item {
                    val dismissInteraction = remember { MutableInteractionSource() }
                    val dismissPressed by dismissInteraction.collectIsPressedAsState()
                    val dismissScale by animateFloatAsState(targetValue = if (dismissPressed) 0.95f else 1f, label = "dismiss_scale")

                    Button(
                        onClick = onDismiss,
                        interactionSource = dismissInteraction,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .graphicsLayer {
                                scaleX = dismissScale
                                scaleY = dismissScale
                            },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE8EDE8),
                            contentColor = Color(0xFF0F140F)
                        )
                    ) {
                        Text("Continue Journey", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

fun playSystemSound(context: android.content.Context) {
    try {
        val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val ringtone = RingtoneManager.getRingtone(context, notificationUri)
        if (ringtone != null) {
            ringtone.play()
        } else {
            playBeep()
        }
    } catch (e: Exception) {
        Log.e("DitchLoopyAudio", "Failed to play ringtone", e)
        playBeep()
    }
}

fun playBeep() {
    try {
        val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 400)
    } catch (e: Exception) {
        Log.e("DitchLoopyAudio", "Failed to play backup beep", e)
    }
}

fun createNotificationChannel(context: android.content.Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val name = "DitchLoopy Alerts"
        val descriptionText = "Timer alarms and reminders to keep you mindful."
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel("ditchloopy_channel", name, importance).apply {
            description = descriptionText
        }
        val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}

fun showPresenceNotification(context: android.content.Context, title: String, message: String) {
    createNotificationChannel(context)
    val builder = NotificationCompat.Builder(context, "ditchloopy_channel")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
    
    try {
        val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    } catch (e: Exception) {
        Log.e("DitchLoopyNotification", "Failed to send notification", e)
    }
}
