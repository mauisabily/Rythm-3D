package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.game.RhythmGameViewModel
import com.example.game.RhythmNote
import com.example.game.RhythmSong
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Screen states
enum class AppScreen {
    LOGIN,
    SIGNUP,
    WAITLIST,
    DASHBOARD,
    GAMEPLAY
}

// User Profile representation
data class UserProfile(
    val username: String,
    val email: String,
    val isGoogleSSO: Boolean,
    val isApproved: Boolean,
    val artistBio: String = ""
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                RhythmAppMain()
            }
        }
    }
}

@Composable
fun RhythmAppMain() {
    var currentScreen by remember { mutableStateOf(AppScreen.LOGIN) }
    var loggedInUser by remember { mutableStateOf<UserProfile?>(null) }
    val gameViewModel: RhythmGameViewModel = viewModel()
    
    // Ambient background space color gradient
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(CyberBlack, Color(0xFF070512), CyberBlack)
                )
            )
    ) {
        Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
            when (screen) {
                AppScreen.LOGIN -> LoginScreen(
                    onLoginSuccess = { user ->
                        loggedInUser = user
                        currentScreen = if (user.isApproved) AppScreen.DASHBOARD else AppScreen.WAITLIST
                    },
                    onNavigateToSignUp = { currentScreen = AppScreen.SIGNUP }
                )
                AppScreen.SIGNUP -> SignUpScreen(
                    onSignUpSubmitted = { user ->
                        loggedInUser = user
                        currentScreen = AppScreen.WAITLIST
                    },
                    onNavigateToLogin = { currentScreen = AppScreen.LOGIN }
                )
                AppScreen.WAITLIST -> WaitlistReviewScreen(
                    userProfile = loggedInUser,
                    onBypassOrApproved = {
                        loggedInUser = loggedInUser?.copy(isApproved = true)
                        currentScreen = AppScreen.DASHBOARD
                    },
                    onCancel = { currentScreen = AppScreen.LOGIN }
                )
                AppScreen.DASHBOARD -> DashboardScreen(
                    userProfile = loggedInUser!!,
                    gameViewModel = gameViewModel,
                    onStartGame = { currentScreen = AppScreen.GAMEPLAY },
                    onLogout = {
                        loggedInUser = null
                        currentScreen = AppScreen.LOGIN
                    }
                )
                AppScreen.GAMEPLAY -> GameplayScreen(
                    gameViewModel = gameViewModel,
                    onExitGame = {
                        gameViewModel.stopPlaying()
                        currentScreen = AppScreen.DASHBOARD
                    }
                )
            }
        }
    }
}

@Composable
fun LoginScreen(
    onLoginSuccess: (UserProfile) -> Unit,
    onNavigateToSignUp: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isGoogleAccountChoosing by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()

    // Title pulse animate
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Glowing Neon Icon
        Box(
            modifier = Modifier
                .size(100.dp * pulseScale)
                .background(
                    Brush.radialGradient(listOf(CyberCyan.copy(alpha = 0.3f), Color.Transparent)),
                    CircleShape
                )
                .border(2.dp, CyberCyan, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Rhythm Star Icon",
                tint = CyberCyan,
                modifier = Modifier.size(54.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "RHYTHM 3D",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
                fontFamily = FontFamily.Monospace
            ),
            color = IceWhite,
            textAlign = TextAlign.Center
        )

        Text(
            text = "ABCDEFG SYNTH DECK RUNNER",
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold
            ),
            color = SynthPink,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Normal Login fields
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SilentGray.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
            color = CyberGray,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Sign in to your Synth Account",
                    style = MaterialTheme.typography.titleMedium,
                    color = IceWhite,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { 
                        email = it
                        emailError = false
                    },
                    label = { Text("Email Address", color = SilentGray) },
                    isError = emailError,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = IceWhite,
                        unfocusedTextColor = IceWhite,
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = SilentGray.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("login_email")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password", color = SilentGray) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = IceWhite,
                        unfocusedTextColor = IceWhite,
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = SilentGray.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("login_password")
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (email.isBlank()) {
                            emailError = true
                        } else {
                            // Normal credentials need waitlist review
                            onLoginSuccess(
                                UserProfile(
                                    username = email.substringBefore("@"),
                                    email = email,
                                    isGoogleSSO = false,
                                    isApproved = false // Waitlist triggers admin approval simulation
                                )
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SynthPink),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("submit_login_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("LAUNCH TO WAITLIST", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "— OR QUICK LAUNCH —", color = SilentGray, style = MaterialTheme.typography.bodySmall)

        Spacer(modifier = Modifier.height(16.dp))

        // Google SSO Button (Instant Approval!)
        Button(
            onClick = { isGoogleAccountChoosing = true },
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("google_sso_button")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Google Logo",
                    tint = CyberGray,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Sign in with Google SSO (Instant Play)",
                    color = CyberBlack,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "New artist? ", color = SilentGray)
            Text(
                text = "Apply for Admin Approval Token",
                color = CyberCyan,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { onNavigateToSignUp() }
                    .testTag("nav_signup_link")
            )
        }
    }

    // Google SSO Quick Picker simulator
    if (isGoogleAccountChoosing) {
        AlertDialog(
            onDismissRequest = { isGoogleAccountChoosing = false },
            containerColor = CyberGray,
            title = {
                Text(
                    text = "Google SSO Account Hub",
                    color = IceWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Choose a Google Synth credentials account to auto-approve instant play:",
                        color = SilentGray,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                isGoogleAccountChoosing = false
                                onLoginSuccess(
                                    UserProfile(
                                        username = "RhythmEnthusiast",
                                        email = "soirem08@gmail.com",
                                        isGoogleSSO = true,
                                        isApproved = true // GOOGLE SSO IS INSTANT AUTO-APPROVED!
                                    )
                                )
                            }
                            .border(1.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(CyberCyan, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("RE", color = CyberBlack, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("RhythmEnthusiast", color = IceWhite, fontWeight = FontWeight.Bold)
                            Text("soirem08@gmail.com", color = SilentGray, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { isGoogleAccountChoosing = false }) {
                    Text("CANCEL", color = SynthPink)
                }
            }
        )
    }
}

@Composable
fun SignUpScreen(
    onSignUpSubmitted: (UserProfile) -> Unit,
    onNavigateToLogin: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "ARTIST SIGNUP",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            ),
            color = CyberCyan
        )
        Text(
            text = "Normal signup requires manual Admin review & confirmation.",
            style = MaterialTheme.typography.bodySmall,
            color = SilentGray,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
            textAlign = TextAlign.Center
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SilentGray.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
            color = CyberGray,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                if (errorMsg.isNotBlank()) {
                    Text(
                        text = errorMsg,
                        color = SynthPink,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Display Artist Name", color = SilentGray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = IceWhite,
                        unfocusedTextColor = IceWhite,
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = SilentGray.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("signup_username")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address", color = SilentGray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = IceWhite,
                        unfocusedTextColor = IceWhite,
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = SilentGray.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("signup_email")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Enter musical background / instrument", color = SilentGray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = IceWhite,
                        unfocusedTextColor = IceWhite,
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = SilentGray.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("signup_bio")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Secret Password Key", color = SilentGray) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = IceWhite,
                        unfocusedTextColor = IceWhite,
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = SilentGray.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("signup_password")
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (email.isBlank() || username.isBlank() || password.isBlank()) {
                            errorMsg = "Please fill in all core fields."
                        } else {
                            onSignUpSubmitted(
                                UserProfile(
                                    username = username,
                                    email = email,
                                    isGoogleSSO = false,
                                    isApproved = false,
                                    artistBio = bio
                                )
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("submit_signup_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("SUBMIT PROFILE FOR APPROVAL", fontWeight = FontWeight.Bold, color = CyberBlack)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Have an validated token key? Sign In",
            color = SilentGray,
            modifier = Modifier
                .clickable { onNavigateToLogin() }
                .testTag("nav_login_link")
        )
    }
}

@Composable
fun WaitlistReviewScreen(
    userProfile: UserProfile?,
    onBypassOrApproved: () -> Unit,
    onCancel: () -> Unit
) {
    var reviewStatusLine by remember { mutableStateOf("Initializing Admin waitlist scan...") }
    val consoleLogs = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        consoleLogs.add("[SYS] Requesting token key for ${userProfile?.email ?: "new_artist"}")
        delay(1500)
        consoleLogs.add("[SYS] Check complete: Normal registration queue detected.")
        delay(1200)
        consoleLogs.add("[ADMIN] Artist background bio scan: '${userProfile?.artistBio ?: "Synthesizer Student"}'")
        delay(1500)
        consoleLogs.add("[ADMIN] Calibrating 7-Lane pitch scales for ABCDEFG...")
        delay(1800)
        consoleLogs.add("[SYS] Admin manual status check successful. PROFILES MATCHED.")
        delay(1000)
        consoleLogs.add("[STATUS] DECK APPROVED. ACCESS TOKEN DISPATCHED.")
        reviewStatusLine = "Profile Approved! Starting Synth Engine..."
        delay(800)
        onBypassOrApproved()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LinearProgressIndicator(
            color = SynthPink,
            trackColor = CyberGray,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
        )

        Spacer(modifier = Modifier.height(30.dp))

        Text(
            text = "PENDING ADMIN APPROVAL",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
            color = SynthPink
        )

        Text(
            text = "Normal accounts require review. Checking qualifications...",
            color = SilentGray,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
            textAlign = TextAlign.Center
        )

        // Cool retro terminal/console log showing real progress status
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(Color.Black, RoundedCornerShape(12.dp))
                .border(1.dp, NeonPurple.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(consoleLogs) { log ->
                    Text(
                        text = log,
                        fontFamily = FontFamily.Monospace,
                        color = if (log.contains("APPROVED") || log.contains("DISPATCHED")) CyberCyan else Color.Green,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = reviewStatusLine,
            color = IceWhite,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Bypass button
        Button(
            onClick = { onBypassOrApproved() },
            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("FORCE INSTANT BYPASS", color = CyberBlack, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onCancel) {
            Text("CANCEL & GO BACK", color = SilentGray)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    userProfile: UserProfile,
    gameViewModel: RhythmGameViewModel,
    onStartGame: () -> Unit,
    onLogout: () -> Unit
) {
    val selectedSong by gameViewModel.selectedSongName.collectAsState()
    val isRecordingCustom by gameViewModel.isRecording
    val recordedNotesCount = gameViewModel.recordedNotesList.size
    
    // Total High score tracker
    val currentScore by gameViewModel.score.collectAsState()
    val currentMaxCombo by gameViewModel.maxCombo.collectAsState()

    val isKidsArenaSelect by gameViewModel.isKidsArena.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // User profile bar heading
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                Brush.linearGradient(listOf(CyberCyan, SynthPink)),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userProfile.username.take(2).uppercase(),
                            color = CyberBlack,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = userProfile.username,
                            color = IceWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = if (userProfile.isGoogleSSO) "Google Verified Artist" else "Standard Artist Queue",
                            color = CyberCyan,
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(
                    onClick = onLogout,
                    modifier = Modifier.testTag("logout_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = "Logout",
                        tint = SynthPink
                    )
                }
            }
        }

        // Toggle Switch Choice between Cyber Arcade and Kids Playground
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .background(CyberGray, RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (!isKidsArenaSelect) CyberCyan else Color.Transparent)
                        .clickable { gameViewModel.setKidsArena(false) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "★ CYBER ARCADE",
                            color = if (!isKidsArenaSelect) CyberBlack else IceWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isKidsArenaSelect) ElectroYellow else Color.Transparent)
                        .clickable { gameViewModel.setKidsArena(true) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "🧸 KIDS ARENA",
                            color = if (isKidsArenaSelect) CyberBlack else IceWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Tactile Instrument Selection row
        item {
            val currentInstr by gameViewModel.currentInstrument.collectAsState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "SELECT MUSIC INSTRUMENT",
                    color = if (isKidsArenaSelect) ElectroYellow else CyberCyan,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberGray, RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val instruments = listOf(
                        Triple(com.example.sound.InstrumentType.PIANO, "🎹 PIANO", "Piano"),
                        Triple(com.example.sound.InstrumentType.GUITAR, "🎸 GUITAR", "Guitar"),
                        Triple(com.example.sound.InstrumentType.SYNTH, "👾 SYNTH", "Synth")
                    )
                    instruments.forEach { (type, label, nameStr) ->
                        val isSel = currentInstr == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) (if (isKidsArenaSelect) ElectroYellow else CyberCyan) else Color.Transparent)
                                .clickable { gameViewModel.changeInstrument(type) }
                                .padding(vertical = 12.dp)
                                .testTag("instrument_select_$nameStr"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSel) CyberBlack else IceWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Feature Banner Header
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .border(
                        width = 1.dp,
                        color = if (isKidsArenaSelect) ElectroYellow.copy(alpha = 0.5f) else NeonPurple.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(bottom = 24.dp),
                colors = CardDefaults.cardColors(containerColor = CyberGray)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = if (isKidsArenaSelect) "KIDS MAGIC ENTRANCE" else "SYNTH ARCHIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isKidsArenaSelect) ElectroYellow else SynthPink,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = if (isKidsArenaSelect) "Super Easy Kids Mode 🎈" else "Experience 7-Lane Rhythm",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = IceWhite
                    )
                    Text(
                        text = if (isKidsArenaSelect)
                            "Designed for 8-12 year olds! Big buttons, slow-scrolling tracks (balloons, dinosaurs & unicorns) with highly generous scoring! Enjoy cute bubble splash hits without any worry."
                            else "Feel notes pulsate down the cosmic vanishing grid. Tap keys in exact alignment with musical pitch waves to unlock glowing, high-intensity sound multipliers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SilentGray,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("HIGHEST SCORE", fontSize = 10.sp, color = SilentGray)
                            Text("$currentScore pts", color = if (isKidsArenaSelect) ElectroYellow else ElectroYellow, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        }
                        Column {
                            Text("BEST COMBO", fontSize = 10.sp, color = SilentGray)
                            Text("$currentMaxCombo hits", color = if (isKidsArenaSelect) ElectroYellow else CyberCyan, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        }
                    }
                }
            }
        }

        // Speed Level Selection Row
        item {
            val scrollMultiplier by gameViewModel.scrollSpeedMultiplier.collectAsState()
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .border(
                        width = 1.dp,
                        color = if (isKidsArenaSelect) ElectroYellow.copy(alpha = 0.3f) else CyberCyan.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(16.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = CyberGray)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "GLIDEPATH NOTES SPEED (REACTION LEVEL)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isKidsArenaSelect) ElectroYellow else CyberCyan,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Adjust how early active note items appear at the horizon so they glide down at a comfortable, human-reactable pace.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SilentGray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val speeds = listOf(
                            Triple(1, "1x Extreme", "1.2s Glide"),
                            Triple(2, "2x Normal", "2.4s Glide"),
                            Triple(3, "3x Slower", "3.6s Glide"),
                            Triple(5, "5x Slower", "6.0s Glide")
                        )
                        speeds.forEach { (mult, label, time) ->
                            val isSelected = scrollMultiplier == mult
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) {
                                            if (isKidsArenaSelect) ElectroYellow else CyberCyan
                                        } else {
                                            CyberSurface
                                        }
                                    )
                                    .clickable { gameViewModel.setScrollSpeedMultiplier(mult) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) CyberBlack else IceWhite,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Text(
                                        text = time,
                                        color = if (isSelected) CyberBlack.copy(alpha = 0.7f) else SilentGray,
                                        fontSize = 9.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Custom Beat Synthesis Recorder (For Music Enthusiasts!)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyberCyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(bottom = 24.dp),
                colors = CardDefaults.cardColors(containerColor = CyberGray)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("BEAT WAVE MAKER", fontWeight = FontWeight.Bold, color = IceWhite)
                            Text("Tap custom note scales, record & play", fontSize = 11.sp, color = SilentGray)
                        }
                        
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(if (isRecordingCustom) Color.Red else SilentGray, CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isRecordingCustom) {
                        Text(
                            text = "RECORDING KEYBOARD ACTIVE: $recordedNotesCount TAPS STORED",
                            color = SynthPink,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Cool grid of ABCDEFG buttons so players can tap & record exact pitches natively!
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            val notes = listOf("A", "B", "C", "D", "E", "F", "G")
                            notes.forEachIndexed { idx, name ->
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(CyberSurface)
                                        .clickable { gameViewModel.triggerLaneHit(idx) }
                                        .border(1.dp, CyberCyan, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(name, color = CyberCyan, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { gameViewModel.stopRecordingCustom() },
                            colors = ButtonDefaults.buttonColors(containerColor = SynthPink),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("COMPILE RECORDED TRACKS", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { gameViewModel.startRecordingCustom() },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Record icon", tint = CyberBlack)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("OPEN DYNAMIC SYNTH CREATOR", fontWeight = FontWeight.Bold, color = CyberBlack)
                        }
                    }
                }
            }
        }

        item {
            if (isKidsArenaSelect) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val annotatedTitle = buildAnnotatedString {
                        withStyle(style = SpanStyle(color = Color(0xFFFF7043), fontWeight = FontWeight.Black)) {
                            append("A")
                        }
                        withStyle(style = SpanStyle(color = Color(0xFFFFCA28), fontWeight = FontWeight.Black)) {
                            append("B")
                        }
                        withStyle(style = SpanStyle(color = Color(0xFF26A69A), fontWeight = FontWeight.Black)) {
                            append("C")
                        }
                        withStyle(style = SpanStyle(color = Color(0xFF7E57C2), fontWeight = FontWeight.Bold)) {
                            append(" Adventure")
                        }
                    }
                    Text(
                        text = annotatedTitle,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.5f),
                                offset = Offset(2f, 2f),
                                blurRadius = 4f
                            )
                        ),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "Pick a song to start playing!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = SilentGray.copy(alpha = 0.8f)
                    )
                }
            } else {
                Text(
                    text = "SELECT SYNTH RUN DECKS",
                    style = MaterialTheme.typography.titleMedium,
                    color = IceWhite,
                    modifier = Modifier.padding(bottom = 12.dp),
                    letterSpacing = 1.sp
                )
            }
        }

        // Grid/List of Preset Tracks
        val songsToShow = if (isKidsArenaSelect) RhythmGameViewModel.getKidsPresetSongs() else RhythmGameViewModel.getPresetSongs()
        items(songsToShow) { song ->
            val isSelected = selectedSong == song.title
            val themeCardBorderColor = if (isSelected) {
                if (isKidsArenaSelect) ElectroYellow else CyberCyan
            } else {
                SilentGray.copy(alpha = 0.2f)
            }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = themeCardBorderColor,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .background(if (isSelected) CyberSurface else CyberGray)
                    .clickable { gameViewModel.selectSong(song.title) }
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = song.title,
                                color = IceWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (!isKidsArenaSelect) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .background(
                                            when (song.difficulty) {
                                                "Easy" -> Color.Green.copy(alpha = 0.2f)
                                                "Medium" -> ElectroYellow.copy(alpha = 0.2f)
                                                "Kids Super Easy" -> Color(0xFF00BFA5).copy(alpha = 0.2f)
                                                "Kids Playful" -> Color(0xFFFFB74D).copy(alpha = 0.2f)
                                                "Kids Magical" -> Color(0xFF9C27B0).copy(alpha = 0.2f)
                                                else -> SynthPink.copy(alpha = 0.2f)
                                            },
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = song.difficulty,
                                        fontSize = 10.sp,
                                        color = when (song.difficulty) {
                                            "Easy" -> Color.Green
                                            "Medium" -> ElectroYellow
                                            "Kids Super Easy" -> Color(0xFF1DE9B6)
                                            "Kids Playful" -> Color(0xFFFFB74D)
                                            "Kids Magical" -> Color(0xFFE040FB)
                                            else -> SynthPink
                                        },
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        val subtitleText = if (isKidsArenaSelect) {
                            "${song.difficulty} • ${song.artist}"
                        } else {
                            "${song.artist} • ${song.bpm} BPM • ${song.notes.size} beats"
                        }
                        val subtitleColor = if (isKidsArenaSelect) {
                            Color(0xFF81C784)
                        } else {
                            SilentGray
                        }
                        Text(
                            text = subtitleText,
                            color = subtitleColor,
                            fontSize = 12.sp,
                            fontWeight = if (isKidsArenaSelect) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    if (isKidsArenaSelect) {
                        IconButton(
                            onClick = {
                                gameViewModel.selectSong(song.title)
                                onStartGame()
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("start_game_button_${song.title.replace(" ", "_").replace("⭐", "").replace("🐑", "").replace("🛶", "")}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play Track",
                                tint = Color.White
                            )
                        }
                    } else {
                        if (isSelected) {
                            IconButton(
                                onClick = onStartGame,
                                modifier = Modifier
                                    .background(CyberCyan, CircleShape)
                                    .size(40.dp)
                                    .testTag("start_game_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play Track",
                                    tint = CyberBlack
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = "Selected Arrow",
                                tint = SilentGray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GameplayScreen(
    gameViewModel: RhythmGameViewModel,
    onExitGame: () -> Unit
) {
    val score by gameViewModel.score.collectAsState()
    val combo by gameViewModel.combo.collectAsState()
    val maxCombo by gameViewModel.maxCombo.collectAsState()
    val songProgress by gameViewModel.songProgress.collectAsState()
    val isPlaying by gameViewModel.isPlaying.collectAsState()
    val ratingText by gameViewModel.gameRating.collectAsState()

    val scope = rememberCoroutineScope()

    // Trigger start playing automatic note runs
    LaunchedEffect(Unit) {
        gameViewModel.startPlaying()
    }

    // Dynamic scale for rating flash on screen success
    val ratingScale = remember { Animatable(0f) }
    LaunchedEffect(ratingText) {
        if (ratingText.isNotBlank()) {
            ratingScale.snapTo(0.6f)
            ratingScale.animateTo(
                targetValue = 1.5f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBlack)
    ) {
        // Core interactive 3D perspective Canvas field
        RhythmHighwayCanvas(
            gameViewModel = gameViewModel,
            modifier = Modifier.fillMaxSize()
        )

        // Overlay Game HUD Head-up panel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onExitGame,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .testTag("exit_gameplay_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close gameplay view",
                    tint = Color.White
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = gameViewModel.currentSong.title,
                    color = IceWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = gameViewModel.currentSong.artist,
                    color = SilentGray,
                    fontSize = 11.sp
                )
            }
        }

        // Real-time Live Instrument Selection Overlay
        val currentInstr by gameViewModel.currentInstrument.collectAsState()
        val isKidsSong = gameViewModel.currentSong.isKidsSong
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 68.dp)
        ) {
            Row(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                    .border(1.dp, (if (isKidsSong) ElectroYellow else CyberCyan).copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    Pair(com.example.sound.InstrumentType.PIANO, "🎹 PIANO"),
                    Pair(com.example.sound.InstrumentType.GUITAR, "🎸 GUITAR"),
                    Pair(com.example.sound.InstrumentType.SYNTH, "👾 SYNTH")
                ).forEach { (type, label) ->
                    val isSel = currentInstr == type
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSel) (if (isKidsSong) ElectroYellow.copy(alpha = 0.8f) else CyberCyan.copy(alpha = 0.8f)) else Color.Transparent)
                            .clickable { gameViewModel.changeInstrument(type) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSel) CyberBlack else Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }
        }

        // Rating Flasher center overlays (PERFECT / GOOD / MISS!)
        if (ratingText.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .offset(y = (-40).dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = ratingText,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    ),
                    color = when {
                        ratingText.contains("PERFECT") -> ElectroYellow
                        ratingText.contains("GOOD") -> CyberCyan
                        else -> SynthPink
                    },
                    modifier = Modifier.graphicsLayer {
                        scaleX = ratingScale.value
                        scaleY = ratingScale.value
                        alpha = (2f - ratingScale.value).coerceIn(0f, 1f)
                    }
                )
            }
        }

        // Core score HUD running at the center top
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SCORE",
                color = SilentGray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Text(
                text = String.format("%06d", score),
                color = IceWhite,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
            
            if (combo > 0) {
                Row(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .background(SynthPink.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Star beat",
                        tint = SynthPink,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$combo COMBO",
                        color = SynthPink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Interactive key-tapping row at bottom representing notes A B C D E F G
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .fillMaxWidth()
                .padding(bottom = 16.dp, start = 8.dp, end = 8.dp)
        ) {
            // Song Progress Track line
            LinearProgressIndicator(
                progress = { songProgress },
                color = CyberCyan,
                trackColor = CyberGray.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .height(3.dp)
                    .clip(CircleShape)
            )

            // ABCDEFG or cute kid-friendly trigger keyboard keys
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val isKidsSong = gameViewModel.currentSong.isKidsSong
                val currentKidsTheme = gameViewModel.currentSong.kidsTheme
                val kidsLanesCount = if (isKidsSong) gameViewModel.currentSong.kidsLaneCount else 7
                val lanesLabels = listOf("A", "B", "C", "D", "E", "F", "G")
                
                lanesLabels.take(kidsLanesCount).forEachIndexed { idx, value ->
                    val lastHitTime = gameViewModel.laneFeedback.getOrElse(idx) { 0L }
                    val isLaneGlowing = System.currentTimeMillis() - lastHitTime < 180
                    val laneFeedbackType = gameViewModel.laneHitStatus[idx]
                    
                    val kidsNormalColor = when (currentKidsTheme) {
                        "balloon" -> when (idx % 7) {
                            0 -> Color(0xFFEF5350)
                            1 -> Color(0xFF42A5F5)
                            2 -> Color(0xFFFFCA28)
                            3 -> Color(0xFF66BB6A)
                            4 -> Color(0xFFEC407A)
                            5 -> Color(0xFFAB47BC)
                            else -> Color(0xFFFF7043)
                        }
                        "dino" -> when (idx % 7) {
                            0 -> Color(0xFF4CAF50)
                            1 -> Color(0xFFFF9800)
                            2 -> Color(0xFF795548)
                            3 -> Color(0xFFC0CA33)
                            4 -> Color(0xFF2E7D32)
                            5 -> Color(0xFFBF360C)
                            else -> Color(0xFF5D4037)
                        }
                        "boat" -> when (idx % 7) {
                            0 -> Color(0xFF29B6F6)
                            1 -> Color(0xFF0288D1)
                            2 -> Color(0xFF26A69A)
                            3 -> Color(0xFF00ACC1)
                            4 -> Color(0xFF00796B)
                            5 -> Color(0xFF00E5FF)
                            else -> Color(0xFF4DD0E1)
                        }
                        else -> when (idx % 7) {
                            0 -> Color(0xFFE91E63)
                            1 -> Color(0xFF9C27B0)
                            2 -> Color(0xFF00BFA5)
                            3 -> Color(0xFFFFD54F)
                            4 -> Color(0xFF2196F3)
                            5 -> Color(0xFFFF5722)
                            else -> Color(0xFFE040FB)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                            .height(if (isKidsSong) 88.dp else 72.dp)
                            .clip(RoundedCornerShape(if (isKidsSong) 16.dp else 8.dp))
                            .background(
                                if (isKidsSong) {
                                    if (isLaneGlowing) kidsNormalColor else kidsNormalColor.copy(alpha = 0.35f)
                                } else {
                                    if (isLaneGlowing) {
                                        val laneColor = when (laneFeedbackType) {
                                            "PERFECT" -> ElectroYellow.copy(alpha = 0.4f)
                                            "GOOD" -> CyberCyan.copy(alpha = 0.4f)
                                            else -> SynthPink.copy(alpha = 0.4f)
                                        }
                                        laneColor
                                    } else CyberGray
                                }
                            )
                            .border(
                                width = if (isLaneGlowing) 3.dp else 1.dp,
                                color = if (isKidsSong) {
                                    if (isLaneGlowing) Color.White else kidsNormalColor
                                } else {
                                    if (isLaneGlowing) {
                                        when (laneFeedbackType) {
                                            "PERFECT" -> ElectroYellow
                                            "GOOD" -> CyberCyan
                                            else -> SynthPink
                                        }
                                    } else SilentGray.copy(alpha = 0.4f)
                                },
                                shape = RoundedCornerShape(if (isKidsSong) 16.dp else 8.dp)
                            )
                            .clickable { gameViewModel.triggerLaneHit(idx) }
                            .testTag("lane_pad_$idx"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = value,
                                color = if (isKidsSong) {
                                    if (isLaneGlowing) Color.White else Color.White.copy(alpha = 0.9f)
                                } else {
                                    if (isLaneGlowing) Color.Black else CyberCyan
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = if (isKidsSong) 14.sp else 18.sp,
                                fontFamily = FontFamily.Monospace,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Text(
                                text = if (isKidsSong) "TAP!" else "Note",
                                color = if (isKidsSong) Color.White.copy(alpha = 0.6f) else if (isLaneGlowing) Color.Black.copy(alpha = 0.6f) else SilentGray,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
        }

        // If song finished
        if (!isPlaying && songProgress >= 0.99f) {
            AlertDialog(
                onDismissRequest = onExitGame,
                containerColor = CyberGray,
                title = {
                    Text(
                        text = "SYNTH TRACK FINISHED!",
                        color = CyberCyan,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "RHYTHM SCORE METRICS",
                            fontSize = 11.sp,
                            color = SilentGray,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Score:", color = IceWhite)
                            Text("$score pts", color = ElectroYellow, fontWeight = FontWeight.Black)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Max Combo Stream:", color = IceWhite)
                            Text("$maxCombo hits", color = SynthPink, fontWeight = FontWeight.Black)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = onExitGame,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                    ) {
                        Text("RETURN TO DECKS", color = CyberBlack, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

@Composable
fun RhythmHighwayCanvas(
    gameViewModel: RhythmGameViewModel,
    modifier: Modifier = Modifier
) {
    val elapsedTrackTime by gameViewModel.elapsedTimeMs.collectAsState()
    val activeNotes = gameViewModel.activeNotes
    val scrollMultiplier by gameViewModel.scrollSpeedMultiplier.collectAsState()

    // Pulse effects
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val gridPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gridPulse"
    )

    val currentSong = gameViewModel.currentSong
    val isKids = currentSong.isKidsSong
    val lanesCount = if (isKids) currentSong.kidsLaneCount else 7
    val kidsTheme = currentSong.kidsTheme

    Canvas(
        modifier = modifier
            .background(Color(0xFF060310))
            .pointerInput(lanesCount) {
                // Allows direct tapping on lanes on screen for mobile responsiveness!
                detectTapGestures { offset ->
                    if (offset.y >= size.height * 0.15f && offset.y <= size.height * 0.78f) {
                        val laneWidth = size.width / lanesCount.toFloat()
                        val clickedLane = (offset.x / laneWidth).toInt().coerceIn(0, lanesCount - 1)
                        gameViewModel.triggerLaneHit(clickedLane)
                    }
                }
            }
    ) {
        val width = size.width
        val height = size.height

        // Define our 3D perspective geometry parameters
        val topY = height * 0.18f
        val bottomY = height * 0.76f

        // Vanishing point narrow span representing far digital horizon
        val topWidth = width * 0.15f
        val topLeftX = (width - topWidth) / 2
        val topRightX = topLeftX + topWidth

        // Broad bottom span representing extreme touch highway
        val bottomWidth = width * 0.94f
        val bottomLeftX = (width - bottomWidth) / 2
        val bottomRightX = bottomLeftX + bottomWidth

        // Draw Ambient space backdrop starry dust and radial sky aurora glow
        val ambientColor = if (isKids) {
            when (kidsTheme) {
                "balloon" -> Color(0xFF152A38)
                "dino" -> Color(0xFF102D15)
                "boat" -> Color(0xFF0D253F)
                else -> Color(0xFF2E1A3C)
            }
        } else {
            NeonPurple
        }

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(ambientColor.copy(alpha = 0.25f), Color.Transparent),
                center = Offset(width / 2, topY),
                radius = (width * 0.7f).coerceAtLeast(0.01f)
            )
        )

        // Draw 3D side barriers / neon walls
        val leftWallPath = Path().apply {
            moveTo(0f, topY)
            lineTo(topLeftX, topY)
            lineTo(bottomLeftX, bottomY)
            lineTo(0f, bottomY)
            close()
        }
        drawPath(
            path = leftWallPath,
            brush = Brush.horizontalGradient(listOf(SynthPink.copy(alpha = 0.12f), Color.Transparent))
        )

        val rightWallPath = Path().apply {
            moveTo(width, topY)
            lineTo(topRightX, topY)
            lineTo(bottomRightX, bottomY)
            lineTo(width, bottomY)
            close()
        }
        drawPath(
            path = rightWallPath,
            brush = Brush.horizontalGradient(listOf(Color.Transparent, SynthPink.copy(alpha = 0.12f)))
        )

        // Draw dynamic lanes extending towards perspective horizon
        for (i in 0..lanesCount) {
            val f = i.toFloat() / lanesCount.toFloat()
            val startX = topLeftX + (topRightX - topLeftX) * f
            val endX = bottomLeftX + (bottomRightX - bottomLeftX) * f

            drawLine(
                color = if (i == 0 || i == lanesCount) {
                    if (isKids) ElectroYellow.copy(alpha = 0.8f) else SynthPink.copy(alpha = 0.8f)
                } else {
                    CyberCyan.copy(alpha = 0.25f)
                },
                start = Offset(startX, topY),
                end = Offset(endX, bottomY),
                strokeWidth = if (i == 0 || i == lanesCount) 9f else 3f
            )
        }

        // Draw dynamic scrolling neon horizontal synth grid lines (giving extreme 3D movement speed effect!)
        val numGridLines = 7
        for (g in 0 until numGridLines) {
            val progress = (g.toFloat() + gridPulse) / numGridLines.toFloat()
            // Perspective non-linear depth curve
            val curveProgress = Math.pow(progress.toDouble(), 1.8).toFloat()

            val y = topY + (bottomY - topY) * curveProgress
            val lineLeftX = topLeftX + (bottomLeftX - topLeftX) * curveProgress
            val lineRightX = topRightX + (bottomRightX - topRightX) * curveProgress

            drawLine(
                color = if (isKids) Color(0xFF4FC3F7).copy(alpha = (1f - curveProgress) * 0.3f) else CobaltBlue.copy(alpha = (1f - curveProgress) * 0.35f),
                start = Offset(lineLeftX, y),
                end = Offset(lineRightX, y),
                strokeWidth = 3f
            )
        }

        // Draw bottom target Hit pad bar
        drawLine(
            color = if (isKids) ElectroYellow else CyberCyan,
            start = Offset(bottomLeftX, bottomY),
            end = Offset(bottomRightX, bottomY),
            strokeWidth = 9f
        )

        // Draw target pad circles for visual feedback on beat alignment lanes
        for (lane in 0 until lanesCount) {
            val centerF = (lane.toFloat() + 0.5f) / lanesCount.toFloat()
            val padX = bottomLeftX + (bottomRightX - bottomLeftX) * centerF
            
            // Pulse circle glow representing responsive touch action
            val isGlowing = System.currentTimeMillis() - gameViewModel.laneFeedback[lane] < 150
            
            val normalPadColor = if (isKids) Color(0xFF81C784).copy(alpha = 0.2f) else CyberCyan.copy(alpha = 0.15f)
            val glowPadColor = if (isKids) Color(0xFFFF8A65).copy(alpha = 0.5f) else ElectroYellow.copy(alpha = 0.4f)
            val padOutlineColor = if (isGlowing) {
                if (isKids) Color(0xFFFF5722) else ElectroYellow
            } else {
                if (isKids) Color(0xFF81C784) else CyberCyan
            }

            drawCircle(
                color = if (isGlowing) glowPadColor else normalPadColor,
                radius = 54f,
                center = Offset(padX, bottomY)
            )

            drawCircle(
                color = padOutlineColor,
                radius = 54f,
                center = Offset(padX, bottomY),
                style = Stroke(width = 4f)
            )
        }

        // Render active flowing 3D Rhythmic note blocks on the highway track!
        activeNotes.forEach { note ->
            val noteSpawnDelay = 1200L * scrollMultiplier // Render limit window before hitting bottompad (ms)
            
            // Check time delta representing distance down the 3D track
            val timeOffset = note.hitTimeMs - elapsedTrackTime

            // Note is traveling towards target bar
            if (timeOffset in -200..noteSpawnDelay) {
                // Normalize progress from 0.0 (farthest top horizon) to 1.0 (bottom hit zone)
                val progress = (1.0f - (timeOffset.toFloat() / noteSpawnDelay)).coerceIn(0f, 1.5f)
                
                // Perfect perspective depth scaling (using power curve to emulate 3D zoom accelerating speed physics!)
                val curveScale = Math.pow(progress.toDouble(), 2.1).toFloat()
                
                val y = topY + (bottomY - topY) * curveScale
                
                // Interpolate note position left to right bounding lanes at this specific depth
                val lineLeftX = topLeftX + (bottomLeftX - topLeftX) * curveScale
                val lineRightX = topRightX + (bottomRightX - topRightX) * curveScale

                val laneCenterFraction = (note.lane.toFloat() + 0.5f) / lanesCount.toFloat()
                val x = lineLeftX + (lineRightX - lineLeftX) * laneCenterFraction

                // Scale width & height based on perspective depth
                val noteWidth = (width * 0.10f) * curveScale
                val noteHeight = 40f * curveScale

                val noteColors = when (kidsTheme) {
                    "balloon" -> when (note.lane % 7) {
                        0 -> listOf(Color(0xFFFF8A80), Color(0xFFEF5350)) // Red
                        1 -> listOf(Color(0xFF90CAF9), Color(0xFF42A5F5)) // Blue
                        2 -> listOf(Color(0xFFFFE082), Color(0xFFFFCA28)) // Yellow
                        3 -> listOf(Color(0xFFA5D6A7), Color(0xFF66BB6A)) // Green
                        4 -> listOf(Color(0xFFF48FB1), Color(0xFFEC407A)) // Pink
                        5 -> listOf(Color(0xFFCE93D8), Color(0xFFAB47BC)) // Purple
                        else -> listOf(Color(0xFFFFAB91), Color(0xFFFF7043)) // Orange
                    }
                    "dino" -> when (note.lane % 7) {
                        0 -> listOf(Color(0xFFA5D6A7), Color(0xFF4CAF50)) // Green step
                        1 -> listOf(Color(0xFFFFCC80), Color(0xFFFF9800)) // Orange step
                        2 -> listOf(Color(0xFFBCAAA4), Color(0xFF795548)) // Wood step
                        3 -> listOf(Color(0xFFE6EE9C), Color(0xFFC0CA33)) // Lime dino
                        4 -> listOf(Color(0xFF81C784), Color(0xFF2E7D32)) // Forest green
                        5 -> listOf(Color(0xFFFFAB91), Color(0xFFBF360C)) // Lava orange
                        else -> listOf(Color(0xFF8D6E63), Color(0xFF5D4037)) // Bark brown
                    }
                    "boat" -> when (note.lane % 7) {
                        0 -> listOf(Color(0xFF81D4FA), Color(0xFF29B6F6)) // Sky blue
                        1 -> listOf(Color(0xFF4FC3F7), Color(0xFF0288D1)) // Ocean blue
                        2 -> listOf(Color(0xFF80CBC4), Color(0xFF26A69A)) // Teal water
                        3 -> listOf(Color(0xFF80DEEA), Color(0xFF00ACC1)) // Deep wave cyan
                        4 -> listOf(Color(0xFF4DB6AC), Color(0xFF00796B)) // Seaweed teal
                        5 -> listOf(Color(0xFF82B1FF), Color(0xFF00E5FF)) // Neon blue
                        else -> listOf(Color(0xFFB2EBF2), Color(0xFF4DD0E1)) // Shallow cyan
                    }
                    else -> when (note.lane % 7) { // unicorn
                        0 -> listOf(Color(0xFFF06292), Color(0xFFE91E63)) // Pink magic
                        1 -> listOf(Color(0xFFE040FB), Color(0xFFD500F9)) // Purple star
                        2 -> listOf(Color(0xFF1DE9B6), Color(0xFF00BFA5)) // Mint glide
                        3 -> listOf(Color(0xFFFFF176), Color(0xFFFFEE58)) // Golden light
                        4 -> listOf(Color(0xFF64B5F6), Color(0xFF2196F3)) // Light blue glide
                        5 -> listOf(Color(0xFFFF8A65), Color(0xFFFF5722)) // Bright coral
                        else -> listOf(Color(0xFFF3E5F5), Color(0xFFE040FB)) // Violet shimmer
                    }
                }

                // If note has been hit, draw gorgeous glowing sparks & intense splash overlays!
                if (note.isHit) {
                    val glowPulse = note.glowIntensity
                    if (glowPulse > 0.0f) {
                        if (isKids) {
                            // Kids mode giant colorful bubble burst splashes!
                            drawCircle(
                                color = noteColors[0].copy(alpha = glowPulse * 0.7f),
                                radius = (noteWidth * 2.5f) * glowPulse,
                                center = Offset(x, y)
                            )
                            // Draw flying candy sparkles
                            for (i in 0..4) {
                                val angle = (i.toFloat() / 5f) * (Math.PI * 2)
                                val sparkRadius = 15f * curveScale * glowPulse
                                val sx = x + (Math.cos(angle) * noteWidth * 1.5 * (1.5 - glowPulse)).toFloat()
                                val sy = y + (Math.sin(angle) * noteWidth * 1.5 * (1.5 - glowPulse)).toFloat()
                                drawCircle(
                                    color = if (i % 2 == 0) Color.White.copy(alpha = glowPulse) else noteColors[1].copy(alpha = glowPulse),
                                    radius = sparkRadius,
                                    center = Offset(sx, sy)
                                )
                            }
                        } else {
                            drawCircle(
                                color = if (note.scoreValue == "PERFECT") ElectroYellow.copy(alpha = glowPulse) else CyberCyan.copy(alpha = glowPulse),
                                radius = (noteWidth * 1.8f) * glowPulse,
                                center = Offset(x, y)
                            )
                            
                            // Radiant bloom particle lines exploding outwards
                            for (i in 0..5) {
                                val angle = (i.toFloat() / 6f) * (Math.PI * 2)
                                val rx = x + (Math.cos(angle) * noteWidth * 1.2 * (2.0 - glowPulse)).toFloat()
                                val ry = y + (Math.sin(angle) * noteWidth * 1.2 * (2.0 - glowPulse)).toFloat()
                                drawLine(
                                    color = ElectroYellow.copy(alpha = glowPulse),
                                    start = Offset(x, y),
                                    end = Offset(rx, ry),
                                    strokeWidth = 6f
                                )
                            }
                        }
                    }
                } else if (!note.isMissed) {
                    val pulse = 1f + (Math.sin((elapsedTrackTime.toDouble() / 150.0)).toFloat() * 0.12f)
                    val noteXSize = noteWidth * pulse
                    val noteYSize = noteHeight * pulse

                    if (isKids) {
                        when (kidsTheme) {
                            "balloon" -> {
                                // Draw a lovely floating rounded balloon!
                                val balloonRadius = (noteXSize * 0.65f).coerceAtLeast(0.01f)
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(Color.White.copy(alpha = 0.5f), noteColors[0]),
                                        center = Offset(x - balloonRadius * 0.2f, y - balloonRadius * 0.2f),
                                        radius = balloonRadius
                                    ),
                                    radius = balloonRadius,
                                    center = Offset(x, y),
                                    alpha = note.fadeAlpha
                                )
                                // Balloon string
                                drawLine(
                                    color = Color.White.copy(alpha = 0.5f * note.fadeAlpha),
                                    start = Offset(x, y + balloonRadius),
                                    end = Offset(x + (Math.sin(elapsedTrackTime.toDouble() / 100.0) * 12).toFloat(), y + balloonRadius + 40f * curveScale),
                                    strokeWidth = 3f * curveScale
                                )
                            }
                            "dino" -> {
                                // Draw cute dinosaur toe step pads!
                                val dinoRadius = noteXSize * 0.55f
                                drawCircle(
                                    color = noteColors[0],
                                    radius = dinoRadius,
                                    center = Offset(x, y),
                                    alpha = note.fadeAlpha
                                )
                                // Draw tiny toes around the step
                                for (i in -1..1) {
                                    val toeAngle = Math.PI / 2 + (i * 0.5)
                                    val tx = x + (Math.cos(toeAngle) * dinoRadius * 0.9).toFloat()
                                    val ty = y - (Math.sin(toeAngle) * dinoRadius * 0.9).toFloat()
                                    drawCircle(
                                        color = noteColors[1],
                                        radius = dinoRadius * 0.3f,
                                        center = Offset(tx, ty),
                                        alpha = note.fadeAlpha
                                    )
                                }
                            }
                            "boat" -> {
                                val boatSize = noteXSize * 0.7f
                                drawCircle(
                                    color = Color(0xFF00B0FF).copy(alpha = 0.4f * note.fadeAlpha),
                                    radius = boatSize * 1.1f,
                                    center = Offset(x, y + 10f),
                                )
                                val path = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(x - boatSize * 0.7f, y)
                                    lineTo(x + boatSize * 0.7f, y)
                                    quadraticBezierTo(
                                        x + boatSize * 0.4f, y + boatSize * 0.5f,
                                        x, y + boatSize * 0.5f
                                    )
                                    quadraticBezierTo(
                                        x - boatSize * 0.4f, y + boatSize * 0.5f,
                                        x - boatSize * 0.7f, y
                                    )
                                    close()
                                }
                                drawPath(
                                    path = path,
                                    color = noteColors[1],
                                    alpha = note.fadeAlpha
                                )
                                val sailPath = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(x, y)
                                    lineTo(x, y - boatSize * 0.8f)
                                    lineTo(x + boatSize * 0.5f, y - boatSize * 0.2f)
                                    close()
                                }
                                drawPath(
                                    path = sailPath,
                                    color = Color.White,
                                    alpha = note.fadeAlpha
                                )
                            }
                            else -> { // "unicorn" star glider!
                                // Draw a gorgeous star or shiny light sphere
                                val starRadius = noteXSize * 0.6f
                                drawCircle(
                                    brush = Brush.linearGradient(noteColors),
                                    radius = starRadius,
                                    center = Offset(x, y),
                                    alpha = note.fadeAlpha
                                )
                                // Highlight core
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.7f * note.fadeAlpha),
                                    radius = starRadius * 0.4f,
                                    center = Offset(x, y)
                                )
                            }
                        }
                    } else {
                        // Regular classic active drifting notes
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = when (note.lane) {
                                    0, 6 -> listOf(SynthPink, Color(0xFFFF5280)) // Lane A, G: Pink Synth
                                    1, 5 -> listOf(CyberCyan, Color(0xFF52F4FF))  // Lane B, F: Cyan Wave
                                    else -> listOf(NeonPurple, Color(0xFFDF52FF)) // Lane C, D, E: Violet Core
                                }
                            ),
                            topLeft = Offset(x - (noteXSize / 2), y - (noteYSize / 2)),
                            size = Size(noteXSize, noteYSize),
                            cornerRadius = CornerRadius(12f * curveScale),
                            alpha = note.fadeAlpha
                        )

                        // Draw high-gloss specular cybernetic core line on the note block
                        drawLine(
                            color = Color.White.copy(alpha = 0.8f * note.fadeAlpha),
                            start = Offset(x - (noteXSize / 3), y),
                            end = Offset(x + (noteXSize / 3), y),
                            strokeWidth = (4f * curveScale)
                        )
                    }
                }
            }
        }
    }
}

// Extra static values helper for UI styling
val CobaltBlue = Color(0xFF1B6CFF)
