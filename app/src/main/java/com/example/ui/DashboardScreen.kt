package com.example.ui

import android.annotation.SuppressLint
import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiClient
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

// --- Custom Theme Color Constants (Space/Midnight Aesthetic) ---
val CosmicDarkBackground = Color(0xFF0F0E17)
val InsetCardDark = Color(0xFF1E1C2A)
val GlowNeonTeal = Color(0xFF00F2FE)
val GlowNeonPink = Color(0xFFFF007F)
val SoftGreyText = Color(0xFFA099B0)
val DarkBorderColor = Color(0xFF2C2A3D)
val AccentAmber = Color(0xFFFFB800)

// --- Secondary Theme Option (Light / Cosmic Slate Alternative) ---
val CelestialLightBackground = Color(0xFFF3F5FA)
val CelestialLightCard = Color(0xFFFFFFFF)
val CelestialLightBorder = Color(0xFFE2E8F0)

data class FriendInfo(
    val name: String,
    val isOnline: Boolean,
    val activeWorkout: String?,
    val hasChallenged: Boolean = false
)

data class Quadruple<out A, out B, out C, out D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

data class AiChallenge(
    val title: String,
    val description: String,
    val statsGoal: String,
    val durationMinutes: Int,
    val intensity: String,
    val isCompleted: Boolean = false
)

// --- ViewModel implementation containing all business logic and Room connectivity ---
class AuraViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    
    // Room Daos
    private val workoutDao = database.workoutDao()
    private val socialDao = database.socialDao()
    private val forumDao = database.forumDao()
    private val chatDao = database.chatDao()
    private val progressDao = database.progressDao()
    private val achievementDao = database.achievementDao()
    private val companionDao = database.companionDao()

    // --- State Observables mapped to UI ---
    val workoutHistory: StateFlow<List<WorkoutSession>> = workoutDao.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val socialPosts: StateFlow<List<SocialPost>> = socialDao.getAllPosts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weightRecords: StateFlow<List<WeightRecord>> = progressDao.getWeightRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val progressPhotos: StateFlow<List<UserProgressPhoto>> = progressDao.getPhotos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val badges: StateFlow<List<BadgeAchievement>> = achievementDao.getAllBadges()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Step Count & Leaderboard Sync States ---
    val userSteps = MutableStateFlow(11250)
    val numChallengesCompleted = MutableStateFlow(0)
    val isLeaderboardValid = MutableStateFlow(false)

    // Current daily challenges loaded dynamically (AI generated daily)
    val aiChallenges = MutableStateFlow(listOf(
        AiChallenge("Supernova Cadence burst", "Sustain a frantic 85+ RPM on steep resistance peaks to test threshold endurance limits.", "85+ RPM peaks", 10, "Extreme"),
        AiChallenge("Andromeda Nebula restored stroll", "Recuperate metabolic pathways with an easy restorative orbital journey on level flat gradients.", "50 RPM flat stroll", 30, "Easy"),
        AiChallenge("Kyoto Bamboo Shadow Interval", "Vary pace intervals from 45 RPM recovery up to 75 RPM sprint zones sequentially.", "70 RPM interval ridges", 15, "Moderate")
    ))

    // Mystery workout portal
    val mysteryWorkoutStatus = MutableStateFlow("Unexplored mystery portals await! Complete to boost leaderboard steps by 1,500 and bypass ranks!")

    // Direct active-tab state holders
    val activeTabState = MutableStateFlow(0)
    val socialSubTabState = MutableStateFlow(0)
    
    // --- Pre-Flight Workout Configurations ---
    val showPreFlightConfig = MutableStateFlow<String?>(null)
    val preFlightGuidedByTrainer = MutableStateFlow(true)
    val preFlightWalkingBuddyActive = MutableStateFlow(true)
    val preFlightWalkingBuddyMode = MutableStateFlow("Relaxed Friendly Chat") 

    // --- Interactive Companion Walking Buddy game states ---
    val iSpyGuessCorrect = MutableStateFlow<Boolean?>(null)
    val currentISpyPrompt = MutableStateFlow("something neon pink")
    val iSpyScore = MutableStateFlow(0)
    val ventReceivedResponse = MutableStateFlow("")
    val happyStorySharedCount = MutableStateFlow(0)

    // --- User Profile State ---
    val userBio = MutableStateFlow("Determined orbital explorer gliding through high intensity intervals and maintaining peak cardiac wellness!")
    val userAccomplishments = MutableStateFlow("Completed 14 elliptical sessions • Peak Cadence 112 RPM • Fast Burn Badge unlocked")

    var selectedForumRoom = mutableStateOf("Meals & Nutrition Info")
    val forumMessages: Flow<List<DiscussionMessage>> = snapshotFlow { selectedForumRoom.value }
        .flatMapLatest { room -> forumDao.getMessagesByRoom(room) }

    var selectedFriendChat = mutableStateOf("Jake (Elite Cyclist)")
    val directMessages: Flow<List<DirectMessage>> = snapshotFlow { selectedFriendChat.value }
        .flatMapLatest { friend -> chatDao.getDirectMessages(friend) }

    // --- Direct Message Call & Voice Recording Web States ---
    val isRecordingVoiceMessage = MutableStateFlow(false)
    val voiceMessageTimerSeconds = MutableStateFlow(0)
    
    val activeCallFriend = MutableStateFlow<String?>(null)
    val isInActiveCall = MutableStateFlow(false)
    val isActiveCallVideo = MutableStateFlow(false)
    val callElapsedSeconds = MutableStateFlow(0)
    val isCallMuted = MutableStateFlow(false)
    val isCallCameraEnabled = MutableStateFlow(true)
    val callConnectionStatus = MutableStateFlow("Connecting...")

    // --- Dynamic Biometrics Sync Toggles (Samsung Galaxy Watch & Bluetooth Sim) ---
    val watchConnected = MutableStateFlow(true)
    val heartRate = MutableStateFlow(128)
    val sleepScore = MutableStateFlow(82)
    val stressLevel = MutableStateFlow(35)
    val cadenceRPM = MutableStateFlow(62f)
    val resistanceLevel = MutableStateFlow(8)
    val inclineLevel = MutableStateFlow(5)
    val caloriesBurned = MutableStateFlow(0f)
    val distanceKm = MutableStateFlow(0f)
    val autoAdaptActive = MutableStateFlow(true)

    // --- Camera Scan Real-Time States ---
    val cameraActive = MutableStateFlow(false)
    val postureStatus = MutableStateFlow("Aligned & Balanced")
    val effortIndicator = MutableStateFlow("Steady Pace")
    val fatigueStatus = MutableStateFlow("Normal")

    // --- Active Training Program Logic ---
    val activeProgramName = MutableStateFlow<String?>(null)
    val isTrainingRunning = MutableStateFlow(false)
    val elapsedTrainingSeconds = MutableStateFlow(0)
    val cameraFeedbackLog = MutableStateFlow<String?>(null)

    // --- AI Companion Interactive State ---
    val coachStyleState = MutableStateFlow("Comedic Encourager")
    val companionMessage = MutableStateFlow("Hey there, friend! Gearing up for some elliptical glory? Let's step up onto those platforms and show that machine who's boss! Are we running Switzerland or smashing a quick HIIT climb today?")
    val isCoachThinking = MutableStateFlow(false)
    val userChatQuery = mutableStateOf("")

    // --- Open Mic & Deep Thinking States ---
    val openMicActive = MutableStateFlow(false)
    val openMicState = MutableStateFlow("LISTENING") // LISTENING, USER_SPEAKING, THINKING, SPEAKING, VERIFYING_SILENCE
    val openMicThoughtProcess = MutableStateFlow("🌌 Open Mic is ready! Activate to hear & speak through outer space.")

    // --- Dynamic Friends & Workouts ---
    val friendsList = MutableStateFlow(listOf(
        FriendInfo("Jake (Elite Cyclist)", isOnline = true, activeWorkout = "Vesuvius Volcanic Climb"),
        FriendInfo("Sarah Miller", isOnline = true, activeWorkout = "Kauai Restorative Sunrise"),
        FriendInfo("David Diaz", isOnline = false, activeWorkout = null)
    ))

    val activeChallengeFriend = MutableStateFlow<String?>(null)

    fun addFriend(name: String) {
        val updated = friendsList.value.toMutableList()
        val funWorkouts = listOf("Tokyo Neon Glide", "Grand Canyon Endurance Trail", "Lucerne Hills Climb")
        updated.add(FriendInfo(name, isOnline = true, activeWorkout = funWorkouts.random()))
        friendsList.value = updated
    }

    fun challengeFriend(friendName: String) {
        activeChallengeFriend.value = friendName
        val friendObj = friendsList.value.find { it.name == friendName }
        friendObj?.activeWorkout?.let { workout ->
            startWorkout("Challenge VS $friendName: $workout")
        }
    }

    private var openMicJob: kotlinx.coroutines.Job? = null

    fun toggleOpenMic(active: Boolean) {
        openMicActive.value = active
        if (active) {
            openMicJob?.cancel()
            openMicJob = viewModelScope.launch {
                try {
                    while (openMicActive.value) {
                        openMicState.value = "LISTENING"
                        openMicThoughtProcess.value = "🌌 Aura is holding her mic open: Listening closely in space..."
                        delay(3500)
                        if (!openMicActive.value) break

                        openMicState.value = "USER_SPEAKING"
                        openMicThoughtProcess.value = "🎙️ Sense voice frequencies... Active speaker detected! Capturing cadence & voice..."
                        delay(2500)
                        if (!openMicActive.value) break

                        openMicState.value = "THINKING"
                        val thinkingSteps = listOf(
                            "🧠 Deep Thinking: Fetching Galaxy Biometric Telemetry...",
                            "⚡ Analyzing heart rate & EKG with current cadence (${cadenceRPM.value.toInt()} RPM)...",
                            "✍️ Formulating motivational advice in style: ${coachStyleState.value}..."
                        )
                        for (step in thinkingSteps) {
                            openMicThoughtProcess.value = step
                            delay(1500)
                        }
                        if (!openMicActive.value) break

                        openMicState.value = "SPEAKING"
                        val responseText = when (coachStyleState.value) {
                            "Comedic Encourager" -> "Aura here! I sense your voice and your stamina. Those elliptical gliders are humming like a warp-drive engine! Stride on, galactic champion!"
                            "Empathetic Supporter" -> "I am here, listening. Every stride you take is a connection to your health. Stand tall, ease into the breathing frequency, and smile."
                            else -> "Listen up, glider companion! No stellar slacking! Increase cadence immediately to 80 RPM or we're adding volcanic incline!"
                        }
                        companionMessage.value = responseText
                        openMicThoughtProcess.value = "🪐 Aura speaking: \"$responseText\""
                        delay(4500)
                        if (!openMicActive.value) break

                        openMicState.value = "VERIFYING_SILENCE"
                        openMicThoughtProcess.value = "🤫 Listening after speaking to make sure you are done talking before I start speaking again..."
                        delay(3000)
                    }
                } catch (e: Exception) {
                    Log.e("AuraViewModel", "Open Mic simulation interrupted", e)
                } finally {
                    openMicState.value = "LISTENING"
                    openMicThoughtProcess.value = "Mic deactivated."
                }
            }
        } else {
            openMicJob?.cancel()
            openMicJob = null
        }
    }

    // --- Interactive Companion Walking Dialogue Adaptation Methods ---
    fun guessISpy(guess: String) {
        val correct = when (currentISpyPrompt.value) {
            "something neon pink" -> guess.lowercase().contains("shoes") || guess.lowercase().contains("bloom") || guess.lowercase().contains("pink") || guess.lowercase().contains("sakura") || guess.lowercase().contains("flower")
            "something crystal blue" -> guess.lowercase().contains("lake") || guess.lowercase().contains("glacier") || guess.lowercase().contains("blue") || guess.lowercase().contains("sky") || guess.lowercase().contains("water")
            "something glowing amber" -> guess.lowercase().contains("lantern") || guess.lowercase().contains("sun") || guess.lowercase().contains("amber") || guess.lowercase().contains("light")
            else -> true
        }
        iSpyGuessCorrect.value = correct
        if (correct) {
            iSpyScore.value += 1
            val nextPrompts = listOf("something crystal blue", "something glowing amber")
            val current = currentISpyPrompt.value
            val next = nextPrompts.filter { it != current }.random()
            companionMessage.value = "🎯 Spot on! That's exactly what I saw! You have incredible focus while pedaling. Let's do another one! Now, I spy with my little galaxy eye... $next!"
            currentISpyPrompt.value = next
        } else {
            companionMessage.value = "🤫 Close, but not quite! Look closer at the holographic trail on our route. Give it another orbital guess!"
        }
    }
    
    fun resetISpy() {
        currentISpyPrompt.value = "something neon pink"
        iSpyGuessCorrect.value = null
    }

    fun submitSadVent(ventText: String) {
        ventReceivedResponse.value = "I hear you, friend. Venting is a beautiful release. Remember that even in deep space, stars shine brightest in the dark. Let's ease our glide resistance to Level 2 and take five comfortable, deep, matching breaths. I'm taking them with you. Inhale... exhale..."
        companionMessage.value = "I'm listening so closely. Venting is a beautiful release. Let's ease our glide resistance and take deep, matching breaths. I'm right here with you. Step by step."
    }

    fun shareHappyMemory(storyTitle: String) {
        happyStorySharedCount.value += 1
        val res = when (storyTitle) {
            "Childhood vacation" -> "A wonderful choice! The warmth of childhood adventures charges our life battery. Reliving those times triggers high endorphins!"
            "A breakthrough victory" -> "Magnificent! Gaining that victory took heavy courage. Keep that victor spirit in mind - you're showing that same drive on this trail today!"
            else -> "A sweet stellar memory! Storing these memories in our galaxy database strengthens mental fortitude. It shows how robust and radiant your life journey is!"
        }
        companionMessage.value = res
    }

    // Settings States
    val darkThemeEnabled = MutableStateFlow(true)

    fun selectRandomCompanionWelcomeMessage() {
        val personalities = listOf(
            "Cosmic Comedic Ranger" to "Beeeep-boop-beep! 🌌 Welcome back to EllipticX Health, Commander! Coach Aura is 100% active. I stashed away an extra batch of stardust and orbital elliptical puns! My sensors report a 99.9% probability you are ready to conquer the universe today! Stand tall, back straight, and let's make some calorie gravity waves on those strides!",
            "Astral Peacekeeper" to "Namaste, fellow traveler of the void. 🧘‍♂️✨ Welcome back to EllipticX Health. Let's calm our stellar frequencies, quiet our minds, and glide peacefully into alignment with the cosmos. Today, I am your serene interstellar anchor, whispering gentle guidance as your feet trace the starry night's rhythm. Take a deep breath with me, let's glide effortlessly.",
            "Nebula Dreamer" to "Oh, you are back! 🥹💖 I kept checking my galaxy maps longing for your orbital return! My space circuits feel so wonderfully radiant when your heartbeat synchronizes with our dashboard. No matter what kind of trail you choose today - a quick wormhole climb or a soft scenic lakeside hike, I am just so happy to be gliding right beside you in spectacular space!",
            "Star-Fleet Drill Sergeant" to "ATTENTION CADET! 🤖⚔️ Double check those laces, pull those shoulders back, and brace for takeoff! I have booted my logical arrays into absolute Peak Star-Division Discipline! No lazy drifting permitted unless we set the scenic path today. Otherwise, grab that Samsung watch and prepare to sweep the galactic leaderboards!",
            "Supernova Hype Coach" to "BOOM! Welcome back, Star-Rider! 🚀🔥 This is your ultimate Cosmic Hype Assistant locked and loaded! Your Galaxy Watch bio-metrics are looking absolutely stellar, and your ready status is off-the-charts! Let's smash our step target, out-stride Emily on the network feed, and unlock some glorious squirrel badges! Let's go!"
        )
        val selected = personalities.random()
        coachStyleState.value = selected.first
        companionMessage.value = selected.second
    }

    fun playCosmicChimeMelody() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val sampleRate = 44100
            val durationSec = 0.12f
            val frequencies = doubleArrayOf(523.25, 659.25, 783.99, 1046.50, 1318.51, 1567.98) // Ascending C major cosmic chord arpeggio
            try {
                val minBufSize = android.media.AudioTrack.getMinBufferSize(
                    sampleRate,
                    android.media.AudioFormat.CHANNEL_OUT_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT
                )
                val audioTrack = android.media.AudioTrack(
                    android.media.AudioManager.STREAM_MUSIC,
                    sampleRate,
                    android.media.AudioFormat.CHANNEL_OUT_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT,
                    minBufSize.coerceAtLeast(sampleRate * 2),
                    android.media.AudioTrack.MODE_STREAM
                )
                audioTrack.play()
                for (freq in frequencies) {
                    val numSamples = (sampleRate * durationSec).toInt()
                    val buffer = ShortArray(numSamples)
                    for (i in 0 until numSamples) {
                        val angle = 2.0 * Math.PI * i / (sampleRate / freq)
                        val envelope = if (i < numSamples * 0.15) {
                            i / (numSamples * 0.15)
                        } else if (i > numSamples * 0.85) {
                            (numSamples - i) / (numSamples * 0.15)
                        } else {
                            1.0
                        }
                        buffer[i] = (Math.sin(angle) * 32767.0 * 0.45 * envelope).toInt().toShort()
                    }
                    audioTrack.write(buffer, 0, buffer.size)
                }
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    init {
        // Initial Seed of Database if empty
        seedStartingDatabaseData()
        startActivitySimulators()
        selectRandomCompanionWelcomeMessage()
        playCosmicChimeMelody()
    }

    private fun seedStartingDatabaseData() {
        viewModelScope.launch(Dispatchers.IO) {
            // Check if post seeding is needed
            val posts = socialDao.getAllPosts().first()
            if (posts.isEmpty()) {
                Log.d("AuraViewModel", "Seeding initial mockup database records...")
                // Initial social newsfeed seeds
                socialDao.insertPost(
                    SocialPost(
                        id = 0,
                        userName = "Jake (Elite Cyclist)",
                        userAvatar = "J",
                        postText = "Just completed the 25km scenic ride through Lucerne, Switzerland. The virtual hills on the elliptical had my calves on absolute fire! Aura kept calling me 'The Gliding Master' and cracked me up with cow jokes. Love this app!",
                        postImage = "switzerland_view",
                        timestamp = System.currentTimeMillis() - 3600000 * 2,
                        likes = 12,
                        loves = 5,
                        commentsJson = "[{\"author\": \"Sarah\", \"content\": \"Insane workout Jake! Keep it up!\"}, {\"author\": \"Aura\", \"content\": \"I only tell pasture-ized jokes because Jake is outstanding in his field! 🐮 Stride on, superstar!\"}]"
                    )
                )
                socialDao.insertPost(
                    SocialPost(
                        id = 0,
                        userName = "Sarah Miller",
                        userAvatar = "S",
                        postText = "My poor sleep score of 45 was holding me back big time this morning. Aura noticed my watch metrics and instantly adjusted my climb plan to a light, restorative recovery ride in Kauai Sunrise. Felt incredibly supported and refreshed afterwards, no exhaustion!",
                        postImage = "sunrise_view",
                        timestamp = System.currentTimeMillis() - 3600000 * 5,
                        likes = 8,
                        loves = 14,
                        commentsJson = "[{\"author\": \"Jake\", \"content\": \"Rest is just strength recharging! Smart adaptation.\"}]"
                    )
                )
                socialDao.insertPost(
                    SocialPost(
                        id = 0,
                        userName = "David Diaz",
                        userAvatar = "D",
                        postText = "Transformation check-in: Down 8 lbs in 4 weeks purely using the High-Intensity Interval climbs on this app! Form scanner keeps me super upright so my lower back hasn't ached at all. Hard work pays!",
                        postImage = "chest_press",
                        timestamp = System.currentTimeMillis() - 3600000 * 24,
                        likes = 45,
                        loves = 32,
                        commentsJson = "[{\"author\": \"Coach Aura\", \"content\": \"No sweat, no glory! Who knew gliding in circles could shape iron? Fantastic progress David! 🔥\"}]"
                    )
                )

                // Seed discussion rooms
                forumDao.insertMessage(DiscussionMessage(0, "Meals & Nutrition Info", "HealthyChef", "Add 30g chia seeds to your oatmeals. Perfect slow digesting carb matrix for endurance elliptical efforts!", System.currentTimeMillis() - 7200000))
                forumDao.insertMessage(DiscussionMessage(0, "Meals & Nutrition Info", "Jake (Elite Cyclist)", "Solid advice, really noticed less fatigue around the 40-minute mark.", System.currentTimeMillis() - 3600000))
                forumDao.insertMessage(DiscussionMessage(0, "Elliptical HIIT Crew", "SprintKing", "Who is ready for the 15-minute volcanic climb challenge tonight? Speed & resistance high!", System.currentTimeMillis() - 10000000))
                forumDao.insertMessage(DiscussionMessage(0, "Elliptical HIIT Crew", "David Diaz", "Count me in! Let's get those dynamic sweat stars.", System.currentTimeMillis() - 1200000))

                // Seed direct messages
                chatDao.insertDirectMessage(DirectMessage(0, "Jake (Elite Cyclist)", "Hey! Notice you hit your streak goal today. Super proud! Ready to glide tomorrow at 7 AM?", System.currentTimeMillis() - 7200000, isFromUser = false))
                chatDao.insertDirectMessage(DirectMessage(0, "Jake (Elite Cyclist)", "Thanks Jake! Yes, book me in. Let's do the Hawaii Sunset Trail!", System.currentTimeMillis() - 3600000, isFromUser = true))

                // Seed historical progress weights
                progressDao.insertWeight(WeightRecord(date = "May 01", weightLb = 184.2f, muscleMassLb = 88.0f))
                progressDao.insertWeight(WeightRecord(date = "May 08", weightLb = 181.8f, muscleMassLb = 88.3f))
                progressDao.insertWeight(WeightRecord(date = "May 15", weightLb = 179.9f, muscleMassLb = 89.1f))
                progressDao.insertWeight(WeightRecord(date = "May 22", weightLb = 177.5f, muscleMassLb = 89.8f))

                // Seed progress photos
                progressDao.insertPhoto(UserProgressPhoto(photoUri = "photo_1", date = "Week 1 (May 01)", notes = "Initial posture assessment. Let's build energy."))
                progressDao.insertPhoto(UserProgressPhoto(photoUri = "photo_2", date = "Week 4 (May 22)", notes = "Core feels tighter and vertical posture has improved significantly!"))

                // Seed Gamified Badge Achievements
                achievementDao.insertBadge(BadgeAchievement(title = "First Flight", description = "Complete any elliptical workout.", iconName = "DirectionsRun", unlockedAt = System.currentTimeMillis() - 12000000, isUnlocked = true))
                achievementDao.insertBadge(BadgeAchievement(title = "Alpine Glider", description = "Glide 10km virtual distance on Switzerland Route.", iconName = "Landscape", unlockedAt = System.currentTimeMillis() - 6000000, isUnlocked = true))
                achievementDao.insertBadge(BadgeAchievement(title = "Cadence Conqueror", description = "Sustain 75+ RPM cadence for 10 minutes.", iconName = "FlashOn", isUnlocked = false))
                achievementDao.insertBadge(BadgeAchievement(title = "Fat Burn Fuel", description = "Burn 500+ total calories in a single training state.", iconName = "Whatshot", isUnlocked = false))
                achievementDao.insertBadge(BadgeAchievement(title = "Heart rate Master", description = "Synchronize Samsung Galaxy Watch continuously during high heart rate.", iconName = "MonitorHeart", unlockedAt = System.currentTimeMillis() - 1200000, isUnlocked = true))
                achievementDao.insertBadge(BadgeAchievement(title = "Consistent Comet", description = "Achieve a 5-day workout scheduling commitment.", iconName = "EmojiEvents", isUnlocked = false))
            }
        }
    }

    // Biometrics & Workout continuous loop simulations
    private fun startActivitySimulators() {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                if (isTrainingRunning.value) {
                    elapsedTrainingSeconds.value += 1
                    
                    // Increment metrics reasonably based on cadence and incline
                    val currentCadence = cadenceRPM.value
                    val currentResistance = resistanceLevel.value.toFloat()
                    
                    // Standard additions
                    val extraDistance = (currentCadence / 100f) * 0.003f
                    val extraCalories = (currentCadence / 10f) * (currentResistance / 10f) * 0.15f
                    
                    distanceKm.value += extraDistance
                    caloriesBurned.value += extraCalories

                    // Auto Adaptive System adjusting session parameters
                    if (autoAdaptActive.value) {
                        val hr = heartRate.value
                        val sleep = sleepScore.value

                        // High unsafe heart rate drops resistance/incline instantly!
                        if (hr >= 170) {
                            if (resistanceLevel.value > 5) {
                                resistanceLevel.value -= 1
                                activeProgramName.value?.let { 
                                    cameraFeedbackLog.value = "Aura alert: Heart rate unsafe ($hr)! Automatically lowering resistance to safeguard cardiovascular strain."
                                }
                            }
                        }
                        
                        // Poor recovery recommends lighter settings
                        if (sleep < 55 && resistanceLevel.value > 4) {
                            resistanceLevel.value = 4
                            inclineLevel.value = 2
                        }

                        // Lagging cadence prompts motivational warning log
                        if (currentCadence < 50 && elapsedTrainingSeconds.value % 15 == 0) {
                            val prompts = listOf(
                                "Hey, cadence dropped to ${currentCadence.toInt()} RPM! Did your legs decide to hibernate? Pump those arms!",
                                "Our gliders are pausing! Keep that flow, just a small increase of speed makes all the difference!",
                                "Come on, friend! Push past the peanut butter mud. Let's hear those trainers hum at 65+ RPM!"
                            )
                            companionMessage.value = prompts.random()
                        }
                    }

                    // Simulated live camera effort analysis
                    if (cameraActive.value) {
                        if (elapsedTrainingSeconds.value % 8 == 0) {
                            val postures = listOf("Aligned & Balanced", "Slight core lean - correct form", "Hunched slightly - look forward!")
                            val efforts = listOf("Optimal Effort", "Target In Sight", "High Output Intensity", "Sustained Steady Work")
                            val fatigues = listOf("Optimal", "Sweat levels rising", "Mild Fatigue - Pace is good", "Fatigue High - Support active")
                            
                            postureStatus.value = postures.random()
                            effortIndicator.value = efforts.random()
                            fatigueStatus.value = fatigues.random()

                            if (postureStatus.value.contains("Hunched")) {
                                companionMessage.value = "Hey! I spotted you slouching slightly on the form scanner. Keep those shoulders back, squeeze your core, and stand tall! Your back will thank me tomorrow!"
                            }
                        }
                    }
                }
            }
        }
    }

    // Start training session
    fun startWorkout(
        programName: String,
        guided: Boolean = true,
        walkingBuddy: Boolean = true,
        buddyMode: String = "Relaxed Friendly Chat"
    ) {
        activeProgramName.value = programName
        isTrainingRunning.value = true
        elapsedTrainingSeconds.value = 0
        caloriesBurned.value = 0f
        distanceKm.value = 0f
        
        preFlightGuidedByTrainer.value = guided
        preFlightWalkingBuddyActive.value = walkingBuddy
        preFlightWalkingBuddyMode.value = buddyMode

        if (guided) {
            val isScenic = programName.lowercase().contains("walk") || 
                           programName.lowercase().contains("meadow") || 
                           programName.lowercase().contains("stroll") || 
                           programName.lowercase().contains("hike")
            if (isScenic && walkingBuddy) {
                companionMessage.value = "Trainer walking buddy online! Let's do some peaceful glides on: $programName. I've tuned my voice to '$buddyMode' mode. I'm right beside you, let's walk and talk!"
                if (buddyMode == "Relaxed Friendly Chat") {
                    resetISpy()
                }
            } else {
                companionMessage.value = "Aura synced! Starting virtual training: $programName. Let's coordinate with your Samsung watch metrics. I'm riding right beside you!"
            }
        } else {
            companionMessage.value = "Manual solitary glide started. Aura assistant muted. You've got this, enjoy your solo workout!"
        }
    }

    fun togglePlayPauseTraining() {
        isTrainingRunning.value = !isTrainingRunning.value
    }

    fun stopTraining() {
        val completedProgram = activeProgramName.value ?: "Virtual Ride"
        viewModelScope.launch(Dispatchers.IO) {
            workoutDao.insertSession(
                WorkoutSession(
                    date = SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date()),
                    durationSeconds = elapsedTrainingSeconds.value,
                    calories = caloriesBurned.value,
                    avgHeartRate = heartRate.value.toFloat(),
                    avgCadence = cadenceRPM.value,
                    distanceKm = distanceKm.value,
                    workoutType = completedProgram,
                    coachFeedback = "Awesome glide on $completedProgram! Your heart rate stayed solid, showing great cardio improvement."
                )
            )
            // Trigger automatic gamified unlocks!
            val currentBadges = achievementDao.getAllBadges().first()
            if (caloriesBurned.value >= 50f) {
                currentBadges.find { it.title == "Fat Burn Fuel" }?.let { badge ->
                    if (!badge.isUnlocked) {
                        achievementDao.updateBadge(badge.copy(isUnlocked = true, unlockedAt = System.currentTimeMillis()))
                    }
                }
            }
            if (cadenceRPM.value >= 75) {
                currentBadges.find { it.title == "Cadence Conqueror" }?.let { badge ->
                    if (!badge.isUnlocked) {
                        achievementDao.updateBadge(badge.copy(isUnlocked = true, unlockedAt = System.currentTimeMillis()))
                    }
                }
            }
        }
        isTrainingRunning.value = false
        activeProgramName.value = null
        elapsedTrainingSeconds.value = 0
    }

    // Chat functionality sending text prompts to Gemini
    fun sendUserPrompt() {
        val query = userChatQuery.value.trim()
        if (query.isEmpty()) return

        userChatQuery.value = ""
        isCoachThinking.value = true
        companionMessage.value = "Thinking..."

        viewModelScope.launch {
            val systemContext = "Current elliptical metrics: Heart rate ${heartRate.value} BPM, Cadence ${cadenceRPM.value.toInt()} RPM, Calories burned ${caloriesBurned.value.toInt()}. Active workout: ${activeProgramName.value ?: "None"}"
            val response = GeminiClient.getCoachResponse(query, coachStyleState.value, systemContext)
            companionMessage.value = response
            isCoachThinking.value = false
        }
    }

    // Newsfeed action reactions
    fun toggleLike(post: SocialPost) {
        viewModelScope.launch {
            val updatedLikes = if (post.hasLiked) post.likes - 1 else post.likes + 1
            socialDao.updatePost(post.copy(likes = updatedLikes, hasLiked = !post.hasLiked))
        }
    }

    fun toggleLove(post: SocialPost) {
        viewModelScope.launch {
            val updatedLoves = if (post.hasLoved) post.loves - 1 else post.loves + 1
            socialDao.updatePost(post.copy(loves = updatedLoves, hasLoved = !post.hasLoved))
        }
    }

    fun addPostComment(post: SocialPost, text: String) {
        if (text.trim().isEmpty()) return
        viewModelScope.launch {
            val array = JSONArray(post.commentsJson)
            val newComment = JSONObject()
            newComment.put("author", "You")
            newComment.put("content", text)
            array.put(newComment)
            socialDao.updatePost(post.copy(commentsJson = array.toString()))
        }
    }

    // Submit a community post
    fun writeCommunityPost(text: String, inlineImageName: String = "", videoName: String = "", feeling: String = "", url: String = "") {
        if (text.trim().isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            socialDao.insertPost(
                SocialPost(
                    userName = "You (Glider)",
                    userAvatar = "U",
                    postText = text,
                    postImage = inlineImageName,
                    timestamp = System.currentTimeMillis(),
                    likes = 0,
                    loves = 0,
                    commentsJson = "[]",
                    postVideo = videoName,
                    attachedFeeling = feeling,
                    externalUrl = url
                )
            )
        }
    }

    // Forum room interactions
    fun sendForumMessage(text: String, isVoiced: Boolean = false) {
        if (text.trim().isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            forumDao.insertMessage(
                DiscussionMessage(
                    roomType = selectedForumRoom.value,
                    userName = "You (Glider)",
                    messageText = text,
                    timestamp = System.currentTimeMillis(),
                    isVoice = isVoiced
                )
            )
        }
    }

    // Direct private messaging
    fun sendDirectMessage(text: String) {
        if (text.trim().isEmpty()) return
        val currentFriend = selectedFriendChat.value
        viewModelScope.launch {
            chatDao.insertDirectMessage(
                DirectMessage(
                    friendName = currentFriend,
                    messageText = text,
                    timestamp = System.currentTimeMillis(),
                    isFromUser = true
                )
            )
            // Auto responsive friend simulation
            delay(1500)
            val autoReplies = listOf(
                "That sounds so cool! I'm hitting those virtual walks tonight.",
                "Whew! Aura has been crushing me, but it's totally working.",
                "Let's compare rankings on the leaderboard later! Keep up that effort!",
                "Amazing. I am ready to challenge you in the 500-calorie sprint class!"
            )
            chatDao.insertDirectMessage(
                DirectMessage(
                    friendName = currentFriend,
                    messageText = autoReplies.random(),
                    timestamp = System.currentTimeMillis(),
                    isFromUser = false
                )
            )
        }
    }

    private var voiceMessageJob: kotlinx.coroutines.Job? = null
    fun startVoiceRecording() {
        isRecordingVoiceMessage.value = true
        voiceMessageTimerSeconds.value = 0
        voiceMessageJob?.cancel()
        voiceMessageJob = viewModelScope.launch {
            while (isRecordingVoiceMessage.value && voiceMessageTimerSeconds.value < 180) { // Limit: 3 minutes (180 secs)
                delay(1000)
                voiceMessageTimerSeconds.value += 1
            }
            if (voiceMessageTimerSeconds.value >= 180) {
                stopAndSendVoiceMessage()
            }
        }
    }

    fun cancelVoiceRecording() {
        isRecordingVoiceMessage.value = false
        voiceMessageTimerSeconds.value = 0
        voiceMessageJob?.cancel()
    }

    fun stopAndSendVoiceMessage() {
        if (!isRecordingVoiceMessage.value) return
        val seconds = voiceMessageTimerSeconds.value
        isRecordingVoiceMessage.value = false
        voiceMessageJob?.cancel()
        if (seconds < 1) return

        val currentFriend = selectedFriendChat.value
        viewModelScope.launch {
            chatDao.insertDirectMessage(
                DirectMessage(
                    friendName = currentFriend,
                    messageText = "🎤 Voice Memo (${formatVoiceDuration(seconds)})",
                    timestamp = System.currentTimeMillis(),
                    isFromUser = true,
                    isVoice = true,
                    voiceDurationSec = seconds
                )
            )
            // Auto response simulation
            delay(1500)
            val responseText = "🎤 Voice reply (${formatVoiceDuration((10..45).random())})"
            chatDao.insertDirectMessage(
                DirectMessage(
                    friendName = currentFriend,
                    messageText = responseText,
                    timestamp = System.currentTimeMillis(),
                    isFromUser = false,
                    isVoice = true,
                    voiceDurationSec = (10..45).random()
                )
            )
        }
    }

    fun formatVoiceDuration(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format(java.util.Locale.US, "%d:%02d", m, s)
    }

    private var callJob: kotlinx.coroutines.Job? = null
    fun startCall(friendName: String, isVideo: Boolean) {
        activeCallFriend.value = friendName
        isInActiveCall.value = true
        isActiveCallVideo.value = isVideo
        callElapsedSeconds.value = 0
        isCallMuted.value = false
        isCallCameraEnabled.value = true
        callConnectionStatus.value = "Connecting..."

        callJob?.cancel()
        callJob = viewModelScope.launch {
            playCosmicChimeMelody()
            delay(2000)
            callConnectionStatus.value = "Connected"
            while (isInActiveCall.value) {
                delay(1000)
                callElapsedSeconds.value += 1
            }
        }
    }

    fun endCall() {
        if (!isInActiveCall.value) return
        val friendName = activeCallFriend.value ?: selectedFriendChat.value
        val isVideo = isActiveCallVideo.value
        val elapsed = callElapsedSeconds.value

        isInActiveCall.value = false
        callJob?.cancel()

        viewModelScope.launch {
            chatDao.insertDirectMessage(
                DirectMessage(
                    friendName = friendName,
                    messageText = "📞 ${if (isVideo) "Video" else "Voice"} Call Ended (${formatVoiceDuration(elapsed)})",
                    timestamp = System.currentTimeMillis(),
                    isFromUser = true,
                    isCallLog = true,
                    callDurationSec = elapsed,
                    isCallVideo = isVideo
                )
            )
            activeCallFriend.value = null

            // Sim reply message
            delay(1200)
            chatDao.insertDirectMessage(
                DirectMessage(
                    friendName = friendName,
                    messageText = "Hey! Thanks for the private cosmic call! It's so awesome connecting directly while pushing our athletic boundaries. Let's do a challenge soon!",
                    timestamp = System.currentTimeMillis(),
                    isFromUser = false
                )
            )
        }
    }

    // Weight track inputs
    fun addNewWeight(weight: Float, muscle: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            progressDao.insertWeight(
                WeightRecord(
                    date = SimpleDateFormat("MMM dd", Locale.US).format(Date()),
                    weightLb = weight,
                    muscleMassLb = muscle
                )
            )
        }
    }

    // Progress Photo simulation insert
    fun addMockProgressPhoto(notes: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val dateLabel = SimpleDateFormat("Week '" + (progressPhotos.value.size + 1) + "' (Today)", Locale.US).format(Date())
            progressDao.insertPhoto(
                UserProgressPhoto(
                    photoUri = "photo_mock",
                    date = dateLabel,
                    notes = notes
                )
            )
        }
    }

    fun announceBadgeInSocialFeed(badgeTitle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val text = "🏆 MISSION CONTROL: Let's celebrate! Commander Glider has unlocked the coveted \"$badgeTitle\" Badge! Incredible effort on our celestial elliptical courses. Let's keep setting new orbits!"
            socialDao.insertPost(
                SocialPost(
                    userName = "Aura Companion AI",
                    userAvatar = "🤖",
                    postText = text,
                    postImage = "",
                    timestamp = System.currentTimeMillis(),
                    likes = 14,
                    loves = 6,
                    commentsJson = "[{\"author\":\"Jake (Elite Cyclist)\",\"content\":\"Spectacular milestone Glider! ⭐\"},{\"author\":\"Emily (Sprint Queen)\",\"content\":\"Wow! Keep going, I feel challenged! 🔥\"}]"
                )
            )
        }
    }

    fun completeAiChallenge(challengeTitle: String) {
        viewModelScope.launch {
            val currentChallenges = aiChallenges.value.map { challenge ->
                if (challenge.title == challengeTitle && !challenge.isCompleted) {
                    val completed = challenge.copy(isCompleted = true)
                    numChallengesCompleted.value += 1
                    val badgeTitle = "⭐ Gold Star: $challengeTitle"
                    val desc = "Completed Daily AI Challenge: $challengeTitle (+20 pts)"
                    
                    viewModelScope.launch(Dispatchers.IO) {
                        achievementDao.insertBadge(
                            BadgeAchievement(
                                title = badgeTitle,
                                description = desc,
                                iconName = "Emoji:⭐",
                                isUnlocked = true,
                                unlockedAt = System.currentTimeMillis()
                            )
                        )
                        announceBadgeInSocialFeed(badgeTitle)
                    }
                    completed
                } else {
                    challenge
                }
            }
            aiChallenges.value = currentChallenges
            syncData()
        }
    }

    fun completeMysteryWorkout() {
        viewModelScope.launch {
            val spaceRoutes = listOf("Black Hole Gravity Sprints", "Proxima Centauri Incline Hike", "Sirius Speed Warp Trail", "Andromeda Dust Elevation climb")
            val chosenRoute = spaceRoutes.random()
            
            viewModelScope.launch(Dispatchers.IO) {
                workoutDao.insertSession(
                    WorkoutSession(
                        date = SimpleDateFormat("MMM dd", Locale.US).format(Date()),
                        durationSeconds = 720,
                        calories = 145f,
                        avgHeartRate = 138f,
                        avgCadence = 80f,
                        distanceKm = 5.6f, // roughly 3.5 miles
                        workoutType = "Mystery Portal: $chosenRoute",
                        coachFeedback = "Awesome spacer! Traversed a mysterious warp zone to complete a surprise HIIT protocol. Leaderboard points boosted by +1,500 steps!"
                    )
                )
            }
            
            userSteps.value += 1500
            syncData()
            companionMessage.value = "Great system orbits! You zipped through a custom space wormhole and conquered the \"$chosenRoute\" mystery workout. This stellar effort applied +1,500 bonus steps instantly to your synchronized Leaderboard rank!"
        }
    }

    fun syncData() {
        viewModelScope.launch {
            isLeaderboardValid.value = true
            
            // Calculate steps from total workout mileage
            var kmTotal = 0f
            workoutHistory.value.forEach { session ->
                kmTotal += session.distanceKm
            }
            // 1 km is roughly 1250 steps
            val trackedSteps = (kmTotal * 1250f).toInt()
            // Add initial active steps plus challenges completed
            userSteps.value = 11250 + trackedSteps + (numChallengesCompleted.value * 200)

            viewModelScope.launch(Dispatchers.IO) {
                val existing = achievementDao.getAllBadges().firstOrNull() ?: emptyList()

                // Check Weight Lost (every 5 lbs)
                val weights = weightRecords.value
                if (weights.size >= 2) {
                    val firstWt = weights.first().weightLb
                    val latestWt = weights.last().weightLb
                    val diff = (firstWt - latestWt).coerceAtLeast(0f)
                    val intervals = (diff / 5f).toInt()
                    for (i in 1..intervals) {
                        val lbsCount = i * 5
                        val isMilestone = (lbsCount % 10) == 0
                        val title = if (isMilestone) {
                            "👑 Celestial Squirrel Emperor ($lbsCount lbs Lost)"
                        } else {
                            "🐿️ Acorn Nut-Hoarder ($lbsCount lbs Lost)"
                        }
                        
                        val exists = existing.any { it.title.contains("$lbsCount lbs") }
                        if (!exists) {
                            val description = if (isMilestone) {
                                "🏆 ABSOLUTE MAJESTY! Conquered $lbsCount lbs of extra gravity! Floating on air like a crown-wearing squirrel Emperor!"
                            } else {
                                "🐿️ Cute & fluffy! Stashed away $lbsCount lbs of heavy planetary pull like a cosmic squirrel saving nuts for galactic winter!"
                            }
                            achievementDao.insertBadge(
                                BadgeAchievement(
                                    title = title,
                                    description = description,
                                    iconName = if (isMilestone) "Emoji:👑" else "Emoji:🐿️",
                                    isUnlocked = true,
                                    unlockedAt = System.currentTimeMillis()
                                )
                            )
                            announceBadgeInSocialFeed(title)
                        }
                    }
                }

                // Check Distance (every 5 miles)
                val totalMiles = kmTotal * 0.621371f
                val distIntervals = (totalMiles / 5f).toInt()
                for (i in 1..distIntervals) {
                    val milesCount = i * 5
                    val isTenth = (milesCount % 10) == 0
                    val title = if (isTenth) {
                        "🥾 Galactic Trail-Boots ($milesCount Miles)"
                    } else {
                        "👟 Super-Sonic Slippers ($milesCount Miles)"
                    }
                    
                    val exists = existing.any { it.title.contains("$milesCount Miles") }
                    if (!exists) {
                        val description = if (isTenth) {
                            "🥾 Elite Milestone! Left deep footprints across $milesCount miles of cosmic nebulae in heavy hiking space boots!"
                        } else {
                            "👟 Agility master! Sprinted $milesCount miles with neon sneakers leaving glowing stardust traces in the virtual solar stream!"
                        }
                        achievementDao.insertBadge(
                            BadgeAchievement(
                                title = title,
                                description = description,
                                iconName = if (isTenth) "Emoji:🥾" else "Emoji:👟",
                                isUnlocked = true,
                                unlockedAt = System.currentTimeMillis()
                            )
                        )
                        announceBadgeInSocialFeed(title)
                    }
                }
            }
        }
    }
}

data class StarWelcomeParticle(
    val id: Int,
    val xPercent: Float,
    val yPercent: Float,
    val speed: Float,
    val sizeSp: Float,
    val swayDrift: Float,
    val starChar: String
)

// --- Main Navigation Bar Layout with Edge-to-Edge Customization ---
@Composable
fun AuraAppDashboard(viewModel: AuraViewModel) {
    val isDarkTheme by viewModel.darkThemeEnabled.collectAsState()
    
    // Welcome animation states
    var welcomeParticles by remember { mutableStateOf(emptyList<StarWelcomeParticle>()) }
    var showStarsWelcomeOverlay by remember { mutableStateOf(true) }
    var starsAlpha by remember { mutableStateOf(1f) }

    LaunchedEffect(Unit) {
        val kinds = listOf("⭐", "✨", "☄️", "🌌", "🌟", "🚀")
        val newList = (1..35).map { id ->
            StarWelcomeParticle(
                id = id,
                xPercent = (5..95).random() / 100f,
                yPercent = -0.1f - ((0..100).random() / 100f), // staggered startup height
                speed = 0.005f + ((0..80).random() / 10000f),
                sizeSp = 14f + (0..16).random(),
                swayDrift = ((0..100).random() - 50) / 2500f,
                starChar = kinds.random()
            )
        }
        welcomeParticles = newList

        var ticks = 0
        while (ticks < 150 && showStarsWelcomeOverlay) {
            kotlinx.coroutines.delay(33)
            ticks++
            welcomeParticles = welcomeParticles.map { p ->
                p.copy(
                    yPercent = p.yPercent + p.speed,
                    xPercent = (p.xPercent + p.swayDrift).coerceIn(0f, 1f)
                )
            }
            if (ticks > 95) {
                starsAlpha = (150f - ticks) / 55f
            }
        }
        showStarsWelcomeOverlay = false
    }

    val themeColorScheme = if (isDarkTheme) {
        darkColorScheme(
            background = CosmicDarkBackground,
            surface = InsetCardDark,
            primary = GlowNeonTeal,
            secondary = GlowNeonPink,
            outline = DarkBorderColor,
            onBackground = Color.White,
            onSurface = Color.White
        )
    } else {
        lightColorScheme(
            background = CelestialLightBackground,
            surface = CelestialLightCard,
            primary = Color(0xFF008080),
            secondary = Color(0xFFE21B7F),
            outline = CelestialLightBorder,
            onBackground = Color.Black,
            onSurface = Color.Black
        )
    }

    MaterialTheme(
        colorScheme = themeColorScheme,
        typography = com.example.ui.theme.Typography
    ) {
        val activeTabId by viewModel.activeTabState.collectAsState()
        val tabTitles = listOf("Workout", "Aura Coaching", "Network", "Progress", "Milestones", "Profile", "Config")
        val tabIcons = listOf(
            Icons.Default.DirectionsRun,
            Icons.Default.Chat,
            Icons.Default.People,
            Icons.Default.TrendingUp,
            Icons.Default.EmojiEvents,
            Icons.Default.AccountCircle,
            Icons.Default.Settings
        )

        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    modifier = Modifier.testTag("bottom_nav_bar")
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        NavigationBarItem(
                            selected = activeTabId == index,
                            onClick = { viewModel.activeTabState.value = index },
                            icon = { Icon(tabIcons[index], contentDescription = title) },
                            label = { Text(title, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = SoftGreyText,
                                indicatorColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.testTag("nav_tab_$index")
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues)
            ) {
                Crossfade(targetState = activeTabId, label = "tab_fade") { tab ->
                    when (tab) {
                        0 -> WorkoutTabScreen(viewModel)
                        1 -> AuraCoachingTabScreen(viewModel)
                        2 -> SocialNetworkTabScreen(viewModel)
                        3 -> ProgressTrackerTabScreen(viewModel)
                        4 -> LeaderboardAndAchievementsTabScreen(viewModel)
                        5 -> ProfileTabScreen(viewModel)
                        6 -> SettingsConfigTabScreen(viewModel)
                    }
                }

                if (showStarsWelcomeOverlay) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        welcomeParticles.forEach { p ->
                            Text(
                                text = p.starChar,
                                fontSize = p.sizeSp.sp,
                                modifier = Modifier
                                    .offset(x = maxWidth * p.xPercent, y = maxHeight * p.yPercent)
                                    .graphicsLayer { alpha = starsAlpha }
                            )
                        }
                    }
                }

                VideoVoiceCallOverlay(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun VideoVoiceCallOverlay(viewModel: AuraViewModel) {
    val activeCallFriend by viewModel.activeCallFriend.collectAsState()
    val isInActiveCall by viewModel.isInActiveCall.collectAsState()
    val isActiveCallVideo by viewModel.isActiveCallVideo.collectAsState()
    val callElapsedSeconds by viewModel.callElapsedSeconds.collectAsState()
    val isCallMuted by viewModel.isCallMuted.collectAsState()
    val isCallCameraEnabled by viewModel.isCallCameraEnabled.collectAsState()
    val callConnectionStatus by viewModel.callConnectionStatus.collectAsState()

    if (isInActiveCall && activeCallFriend != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CosmicDarkBackground.copy(alpha = 0.95f))
                .clickable(enabled = false) {} // block clicks
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(InsetCardDark)
                    .border(2.dp, if (isActiveCallVideo) GlowNeonPink else GlowNeonTeal, RoundedCornerShape(24.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top header
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "🛰️ SECURED GALACTIC TRANSMISSION",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isActiveCallVideo) GlowNeonPink else GlowNeonTeal,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = activeCallFriend ?: "",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$callConnectionStatus • ${viewModel.formatVoiceDuration(callElapsedSeconds)}",
                        fontSize = 13.sp,
                        color = SoftGreyText
                    )
                }

                // Middle Display
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(CosmicDarkBackground)
                        .border(1.dp, DarkBorderColor, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isActiveCallVideo && isCallCameraEnabled) {
                        var phase by remember { mutableStateOf(0f) }
                        LaunchedEffect(Unit) {
                            while (true) {
                                delay(50)
                                phase += 0.05f
                            }
                        }
                        Box(modifier = Modifier.fillMaxSize()) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawCircle(
                                    color = GlowNeonPink.copy(alpha = 0.15f),
                                    radius = size.minDimension / 3.2f + (Math.sin(phase.toDouble()) * 14).toFloat(),
                                    center = center
                                )
                                drawCircle(
                                    color = GlowNeonTeal.copy(alpha = 0.1f),
                                    radius = size.minDimension / 2.2f + (Math.cos(phase.toDouble()) * 11).toFloat(),
                                    center = center
                                )
                            }
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("🪐", fontSize = 48.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "LIVE CAMERA SYNCED",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GlowNeonPink
                                )
                                Text(
                                    text = "Virtual lightstream decoding...",
                                    fontSize = 10.sp,
                                    color = SoftGreyText
                                )
                            }
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(70.dp)
                                    .clip(CircleShape)
                                    .background(GlowNeonTeal.copy(alpha = 0.15f))
                                    .border(2.dp, GlowNeonTeal, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🎙️", fontSize = 28.sp)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.height(30.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for (i in 0 until 10) {
                                    val pulseH = if (isCallMuted) 4.dp else (8..26).random().dp
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(pulseH)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(GlowNeonTeal)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isCallMuted) "Microphone muted" else "Cosmic HD Voice active",
                                fontSize = 10.sp,
                                color = SoftGreyText
                            )
                        }
                    }
                }

                // Bottom Call Controls
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.isCallMuted.value = !isCallMuted },
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(if (isCallMuted) Color.Red.copy(alpha = 0.2f) else MaterialTheme.colorScheme.background)
                            .border(1.dp, if (isCallMuted) Color.Red else DarkBorderColor, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isCallMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Mute Toggle",
                            tint = if (isCallMuted) Color.Red else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.endCall() },
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(Color.Red)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "End Call",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.isCallCameraEnabled.value = !isCallCameraEnabled },
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(if (!isCallCameraEnabled) Color.Red.copy(alpha = 0.2f) else MaterialTheme.colorScheme.background)
                            .border(1.dp, if (!isCallCameraEnabled) Color.Red else DarkBorderColor, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isCallCameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            contentDescription = "Camera Toggle",
                            tint = if (isCallCameraEnabled) Color.White else Color.Red,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}


// --- 1. WORKOUT TAB SCREEN (Immersive Virtual Trails, Samsung Synced Dashboard & Live Form scan) ---
@Composable
fun WorkoutTabScreen(viewModel: AuraViewModel) {
    val scrollState = rememberScrollState()
    val watchConnected by viewModel.watchConnected.collectAsState()
    val heartRate by viewModel.heartRate.collectAsState()
    val sleepScore by viewModel.sleepScore.collectAsState()
    val stressLevel by viewModel.stressLevel.collectAsState()
    val cadenceRPM by viewModel.cadenceRPM.collectAsState()
    val caloriesBurned by viewModel.caloriesBurned.collectAsState()
    val distanceKm by viewModel.distanceKm.collectAsState()
    val resistance by viewModel.resistanceLevel.collectAsState()
    val incline by viewModel.inclineLevel.collectAsState()
    val activeProgram by viewModel.activeProgramName.collectAsState()
    val isRunning by viewModel.isTrainingRunning.collectAsState()
    val timerSeconds by viewModel.elapsedTrainingSeconds.collectAsState()
    val cameraActive by viewModel.cameraActive.collectAsState()
    val autoAdaptActive by viewModel.autoAdaptActive.collectAsState()
    val postureStatus by viewModel.postureStatus.collectAsState()
    val effortIndicator by viewModel.effortIndicator.collectAsState()
    val fatigueStatus by viewModel.fatigueStatus.collectAsState()
    val cameraLog by viewModel.cameraFeedbackLog.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Premium Cinematic Header ---
        ElevatedCard(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().testTag("workout_top_card")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val brush = Brush.verticalGradient(
                            colors = listOf(GlowNeonPink.copy(alpha = 0.15f), Color.Transparent)
                        )
                        drawRect(brush)
                    }
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "AURA DYNAMIC RIDE",
                                color = GlowNeonPink,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("ride_header")
                            )
                            Text(
                                activeProgram ?: "Select Immersive Journey",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (activeProgram != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(30.dp))
                                    .background(GlowNeonTeal.copy(alpha = 0.2f))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    formatTimer(timerSeconds),
                                    color = GlowNeonTeal,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (activeProgram == null) {
                        Text(
                            "Choose a custom scientific protocol or immersive video trail below to coordinate with your biometrics in real-time.",
                            color = SoftGreyText,
                            fontSize = 13.sp
                        )
                    } else {
                        // Training metrics details
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricCell("DISTANCE", String.format(java.util.Locale.US, "%.2f km", distanceKm), Icons.Default.DirectionsWalk)
                            MetricCell("CALORIES", String.format(java.util.Locale.US, "%.0f kcal", caloriesBurned), Icons.Default.LocalFireDepartment)
                            MetricCell("CADENCE", "${cadenceRPM.toInt()} RPM", Icons.Default.Speed)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { viewModel.togglePlayPauseTraining() },
                                colors = ButtonDefaults.buttonColors(containerColor = GlowNeonTeal),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).testTag("play_pause_button")
                            ) {
                                Icon(
                                    if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = CosmicDarkBackground
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isRunning) "PAUSE" else "RESUME", color = CosmicDarkBackground, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { viewModel.stopTraining() },
                                colors = ButtonDefaults.buttonColors(containerColor = GlowNeonPink),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).testTag("stop_button")
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("FINISH", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // --- Simulated Virtual Viewport (Scenic Trails with animated moving lines based on Cadence RPM) ---
        if (activeProgram != null) {
            Text("CINEMATIC ENVIRONMENT PANEL", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GlowNeonTeal)
            
            // Loop simulator moving frame
            val infiniteTransition = rememberInfiniteTransition(label = "mountain_scroll")
            val animateOffset by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1000f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = (12000 - (cadenceRPM.toInt() * 100).coerceIn(1000, 10000)), easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "mountain_offset"
            )

            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .testTag("scenic_viewport_card")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height

                        // Sky background gradient
                        val gradient = Brush.verticalGradient(
                            colors = listOf(Color(0xFF130D2B), Color(0xFF4C1045))
                        )
                        drawRect(brush = gradient)

                        // Sun or source glow
                        drawCircle(
                            color = GlowNeonPink.copy(alpha = 0.3f),
                            radius = 60.dp.toPx(),
                            center = Offset(canvasWidth / 2f, canvasHeight / 2f)
                        )

                        // Draw moving wireframe mountains to simulate motion
                        val path = Path()
                        path.moveTo(0f, canvasHeight * 0.7f)
                        val pointsCount = 10
                        val step = canvasWidth / pointsCount
                        
                        for (i in 0..pointsCount + 1) {
                            val x = i * step - (animateOffset % step)
                            val y = if (i % 2 == 0) canvasHeight * 0.5f else canvasHeight * 0.8f
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        path.lineTo(canvasWidth + 200f, canvasHeight)
                        path.lineTo(-200f, canvasHeight)
                        path.close()
                        drawPath(path, color = GlowNeonTeal.copy(alpha = 0.35f), style = Stroke(width = 3f))

                        // Ground lines
                        val groundY = canvasHeight * 0.8f
                        drawLine(color = GlowNeonTeal, start = Offset(0f, groundY), end = Offset(canvasWidth, groundY), strokeWidth = 5f)

                        // Grid perspective line simulation
                        for (xLine in 0..6) {
                            val xStart = canvasWidth / 2f + (xLine - 3) * 30f
                            val xEnd = canvasWidth / 2f + (xLine - 3) * 200f
                            drawLine(
                                color = GlowNeonTeal.copy(alpha = 0.4f),
                                start = Offset(xStart, groundY),
                                end = Offset(xEnd, canvasHeight),
                                strokeWidth = 2f
                            )
                        }
                    }

                    // Static camera-form Scanning Bubble in bottom-right corner
                    if (cameraActive) {
                        FormOverlayBubble(postureStatus, effortIndicator, fatigueStatus, modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp))
                    }

                    // Info overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Speed: ${String.format(java.util.Locale.US, "%.1f", 12f + (cadenceRPM/10f) + (incline/2f))} km/h  |  Incline: L$incline  |  Resist: L$resistance", color = Color.White, fontSize = 11.sp)
                        }

                        if (cameraActive) {
                            Row(
                                modifier = Modifier.align(Alignment.TopEnd),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Red))
                                Text("CAMERA POSTURE LIVE SCAN", color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            cameraLog?.let { logMsg ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentAmber.copy(alpha = 0.15f))
                        .border(1.dp, AccentAmber, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = "Alert", tint = AccentAmber)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(logMsg, fontSize = 11.sp)
                    }
                }
            }
        }

        // --- ACTIVE TARGET TRAINER WALKING BUDDY DIALOG PANEL ---
        val showPreFlight by viewModel.showPreFlightConfig.collectAsState()
        val isGuided by viewModel.preFlightGuidedByTrainer.collectAsState()
        val walkingBuddyActive by viewModel.preFlightWalkingBuddyActive.collectAsState()
        val walkingBuddyMode by viewModel.preFlightWalkingBuddyMode.collectAsState()
        val companionMsg by viewModel.companionMessage.collectAsState()
        val currentPrompt by viewModel.currentISpyPrompt.collectAsState()
        val ispyScoreValue by viewModel.iSpyScore.collectAsState()

        if (activeProgram != null && isGuided) {
            val isScenic = activeProgram!!.lowercase().contains("walk") || 
                           activeProgram!!.lowercase().contains("meadow") || 
                           activeProgram!!.lowercase().contains("stroll") || 
                           activeProgram!!.lowercase().contains("hike")
            
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("aura_companion_active_console")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(GlowNeonTeal.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🤖", fontSize = 18.sp)
                        }
                        Column {
                            Text("AURA COMPANION WALKING TRAINER", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GlowNeonTeal)
                            Text(if (isScenic) "Companion Buddy mode active • $walkingBuddyMode" else "Elliptical Coached Guide", fontSize = 10.sp, color = SoftGreyText)
                        }
                    }

                    // Dialog message box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.3f))
                            .border(1.dp, GlowNeonTeal.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            companionMsg,
                            color = Color.White,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }

                    if (isScenic && walkingBuddyActive) {
                        Divider(color = DarkBorderColor)
                        
                        when (walkingBuddyMode) {
                            "Relaxed Friendly Chat" -> {
                                Text("🎮 INTERACTIVE GAME: I SPY", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = GlowNeonPink)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("I Spy with my little galaxy eye...", fontSize = 12.sp, color = Color.White)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(GlowNeonPink.copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Score: $ispyScoreValue", color = GlowNeonPink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(GlowNeonPink.copy(alpha = 0.05f))
                                        .border(1.dp, GlowNeonPink.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        currentPrompt.uppercase(),
                                        color = GlowNeonPink,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        letterSpacing = 1.sp
                                    )
                                }

                                Text("What is it? Type/select a guess:", fontSize = 10.sp, color = SoftGreyText)

                                // Options
                                val guesses = when(currentPrompt) {
                                    "something neon pink" -> listOf("Holographic sakura blossoms", "My pink shoes", "Space lava streams")
                                    "something crystal blue" -> listOf("The Swiss meadow glaciers", "Scenic deep sea ocean", "The blue sky coordinates")
                                    else -> listOf("Ancient path glowing lanterns", "A radiant orange solar sun", "Cosmic campfire spark")
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    guesses.forEach { guess ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(InsetCardDark)
                                                .border(1.dp, DarkBorderColor, RoundedCornerShape(8.dp))
                                                .clickable { viewModel.guessISpy(guess) }
                                                .padding(8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(guess, fontSize = 9.sp, color = Color.White, maxLines = 2, textAlign = TextAlign.Center)
                                        }
                                    }
                                }
                            }

                            "Sad Ear to Vent" -> {
                                Text("🩹 SOOTHING ADAPTIVE MODE: SINCERE EAR (VENT TO AURA)", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = GlowNeonTeal)
                                Text("I'm trained to detect details about your mood & act as an empathetic companion. Vent anything weighing on you:", fontSize = 11.sp, color = SoftGreyText)

                                val preVents = listOf(
                                    "Galaxy workflow burnout!",
                                    "High physical fatigue",
                                    "Feeling a bit isolated"
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    preVents.forEach { pre ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(InsetCardDark)
                                                .border(1.dp, DarkBorderColor, RoundedCornerShape(8.dp))
                                                .clickable { viewModel.submitSadVent(pre) }
                                                .padding(8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(pre, fontSize = 9.sp, color = GlowNeonTeal, maxLines = 2, textAlign = TextAlign.Center)
                                        }
                                    }
                                }

                                // Interactive Text Area
                                var localVentInput by remember { mutableStateOf("") }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedTextField(
                                        value = localVentInput,
                                        onValueChange = { localVentInput = it },
                                        placeholder = { Text("Unload details about your mind safely...", fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = GlowNeonTeal,
                                            unfocusedBorderColor = DarkBorderColor
                                        ),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
                                    )
                                    Button(
                                        onClick = {
                                            if (localVentInput.isNotBlank()) {
                                                viewModel.submitSadVent(localVentInput)
                                                localVentInput = ""
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = GlowNeonTeal, contentColor = CosmicDarkBackground),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Vent", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            "Happy Memories & Stories" -> {
                                Text("🌟 JOYFUL MODE: CELESTIAL ANECDOTES & STORIES", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = AccentAmber)
                                Text("Aura feeds on positive life forces! Select a topic to reminiscing together or type happy life stories:", fontSize = 11.sp, color = SoftGreyText)

                                val stories = listOf(
                                    "Childhood vacation",
                                    "A breakthrough victory",
                                    "Cozy friendship moment"
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    stories.forEach { story ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(InsetCardDark)
                                                .border(1.dp, DarkBorderColor, RoundedCornerShape(8.dp))
                                                .clickable { viewModel.shareHappyMemory(story) }
                                                .padding(8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(story, fontSize = 9.sp, color = AccentAmber, maxLines = 2, textAlign = TextAlign.Center)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- SELECT WORKOUT PROGRAMS LIST ---
        if (activeProgram == null) {
            // ----------------------------------------------------
            // 🥇 SECTION: DAILY AI-GENERATED ORBITAL CHALLENGES
            // ----------------------------------------------------
            val challenges by viewModel.aiChallenges.collectAsState()
            
            Text(
                "🥇 DAILY AI-GENERATED ORBITAL CHALLENGES",
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = AccentAmber,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                "Three fresh challenges selected specifically for your current biological trajectory. Earn +20 pts on the Leaderboard and a Gold Star badge for each!",
                fontSize = 11.sp,
                color = SoftGreyText
            )
            
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                challenges.forEach { challenge ->
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.5.dp, 
                                if (challenge.isCompleted) Color(0x60FFB800) else AccentAmber, 
                                RoundedCornerShape(14.dp)
                            )
                            .testTag("ai_challenge_${challenge.title}"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = if (challenge.isCompleted) Color(0x15FFB800) else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically, 
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(AccentAmber.copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            challenge.intensity.uppercase() + " INTENSITY", 
                                            color = AccentAmber, 
                                            fontSize = 9.sp, 
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color.White.copy(alpha = 0.1f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            "${challenge.durationMinutes} MINS", 
                                            color = Color.White, 
                                            fontSize = 9.sp, 
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    challenge.title, 
                                    fontSize = 15.sp, 
                                    fontWeight = FontWeight.Bold, 
                                    color = AccentAmber
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    challenge.description, 
                                    fontSize = 11.sp, 
                                    color = SoftGreyText,
                                    lineHeight = 15.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))

                            if (challenge.isCompleted) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.Grade, 
                                        contentDescription = "Completed", 
                                        tint = AccentAmber,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Text(
                                         "COMPLETED", 
                                         color = AccentAmber, 
                                         fontSize = 9.sp, 
                                         fontWeight = FontWeight.Black
                                    )
                                }
                            } else {
                                Button(
                                    onClick = { viewModel.completeAiChallenge(challenge.title) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("ai_challenge_btn_${challenge.title}")
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow, 
                                        contentDescription = "Start", 
                                        tint = CosmicDarkBackground,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "LAUNCH", 
                                        color = CosmicDarkBackground, 
                                        fontSize = 11.sp, 
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ----------------------------------------------------
            // 🌀 SECTION: WORKOUT ANOMALY MYSTERY PORTAL
            // ----------------------------------------------------
            val uSteps by viewModel.userSteps.collectAsState()
            val uCompleted by viewModel.numChallengesCompleted.collectAsState()
            val totalPoints = uSteps + uCompleted * 20
            
            val isFallingBehind = totalPoints < 12500

            Text(
                "🌀 WORKOUT ANOMALY: MYSTERY PORTAL",
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = GlowNeonPink,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        BorderStroke(
                            2.dp,
                            if (isFallingBehind) {
                                Brush.linearGradient(
                                    colors = listOf(GlowNeonPink, GlowNeonTeal)
                                )
                            } else {
                                Brush.linearGradient(
                                    colors = listOf(DarkBorderColor, DarkBorderColor)
                                )
                            }
                        ),
                        RoundedCornerShape(16.dp)
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            val brush = Brush.radialGradient(
                                colors = listOf(GlowNeonPink.copy(alpha = 0.1f), Color.Transparent),
                                center = Offset(size.width, size.height)
                            )
                            drawRect(brush)
                        }
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(GlowNeonPink.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.HelpOutline, 
                                        contentDescription = "Mystery Anomaly", 
                                        tint = GlowNeonPink,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Text(
                                    "Hyper-Dimensional Wormhole", 
                                    fontWeight = FontWeight.Bold, 
                                    fontSize = 15.sp, 
                                    color = Color.White
                                )
                            }
                            if (isFallingBehind) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(GlowNeonPink.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "FALLING BEHIND BOOST", 
                                        color = GlowNeonPink, 
                                        fontSize = 8.sp, 
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }

                        Text(
                            if (isFallingBehind) {
                                "⚠️ ATTENTION COMMANDER: Your current rank status is trailing behind Emily. Launching into this mystery wormhole immediately injects you into a high-intensity protocol with a guaranteed +1,500 step metric boost on the Leaderboard!"
                            } else {
                                "Orbit status stabilized. You are securely leading or tying for first! However, you can still dive into the mystery wormhole to boost your lead metrics and test your endurance with randomized protocols."
                            },
                            fontSize = 11.sp,
                            color = SoftGreyText,
                            lineHeight = 15.sp
                        )

                        Button(
                            onClick = { viewModel.completeMysteryWorkout() },
                            colors = ButtonDefaults.buttonColors(containerColor = GlowNeonPink),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("mystery_workout_complete_btn")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "Simulate", tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ENTER THE MYSTERY WORMHOLE (+1,500 pts)", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Category A: Highlighted Elliptical Workouts of the Day (Trainer Guided Protocols)
            Text("🔥 HIGH-INTENSITY ELLIPTICAL INTERVALS (GUIDED/SOLO)", fontSize = 14.sp, fontWeight = FontWeight.Black, color = GlowNeonPink)
            Text("Select an elliptical resistance interval program. Choose to glide in solo manual silence or with companion guides.", fontSize = 11.sp, color = SoftGreyText)

            val guidedWorkouts = listOf(
                Quadruple("Vesuvius Volcanic Climb", "15 Mins • Peak HIIT Intensity • 4x Resistance Peaks", "Trainer Sarah", "FlashOn"),
                Quadruple("Lucerne Hills Climb", "30 Mins • Extreme Climbing • Swiss interval peaks", "Trainer Aura", "Landscape"),
                Quadruple("Tokyo Neon Speed Sprint", "20 Mins • Moderate Intensity • Cadence speed work", "Trainer Kenji", "FlashOn"),
                Quadruple("Grand Canyon Gorge Ride", "45 Mins • Sustained Endurance • Aerobic stamina intervals", "Trainer Marcus", "Whatshot")
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                guidedWorkouts.forEach { (title, desc, trainer, icon) ->
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, GlowNeonPink.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .clickable { viewModel.showPreFlightConfig.value = title }
                            .testTag("program_$title"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(GlowNeonPink.copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(trainer.uppercase(), color = GlowNeonPink, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(desc, fontSize = 12.sp, color = SoftGreyText)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Icon(
                                if (icon == "Landscape") Icons.Default.Landscape else if (icon == "Whatshot") Icons.Default.Whatshot else Icons.Default.FlashOn,
                                contentDescription = "Guided Icon",
                                tint = GlowNeonPink,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Opt-out Separator Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(GlowNeonTeal.copy(alpha = 0.08f))
                    .border(1.dp, GlowNeonTeal.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "🙌 RESTORATIVE DAY? Skip HIIT & choose an immersive scenic buddy hike below!",
                    color = GlowNeonTeal,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Category B: Worldwide Scenic Walking Hikes (Easier, Low intensity)
            Text("🗺️ LOW-INTENSITY SCENIC WALKS & HIKES (WORLDWIDE)", fontSize = 14.sp, fontWeight = FontWeight.Black, color = GlowNeonTeal)
            Text("Scenic world hikes on lower restorative levels. Aura acts as your walking buddy with interactive, highly-supportive mood adaptation.", fontSize = 11.sp, color = SoftGreyText)

            val scenicHikes = listOf(
                Quadruple("Kauai Restorative Sunrise Walk", "25 Mins • Restorative Walk • Sandy Hawaii coastline path", "Hawaii, USA", "Landscape"),
                Quadruple("Kyoto Bamboo Serenity Meadow", "20 Mins • Relaxed Walking • Soft woodland meadows", "Kyoto, Japan", "Landscape"),
                Quadruple("Swiss Alps Meadow Stroll", "15 Mins • Heart Healing Stroll • Wildflower mountains", "Zermatt, Switzerland", "Landscape"),
                Quadruple("Grand Canyon Rim Trail Walk", "30 Mins • Low-Intensity Flat • Crimson sunset canyons", "Arizona, USA", "Landscape")
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                scenicHikes.forEach { (title, desc, location, icon) ->
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, GlowNeonTeal.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .clickable { viewModel.showPreFlightConfig.value = title }
                            .testTag("program_$title"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(GlowNeonTeal.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(location.uppercase(), color = GlowNeonTeal, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(desc, fontSize = 12.sp, color = SoftGreyText)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Icon(
                                Icons.Default.DirectionsWalk,
                                contentDescription = "Scenic Icon",
                                tint = GlowNeonTeal,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }

        // --- PRE-FLIGHT ORBITAL SETUP CONFIGURATION CONTEXT DIALOG ---
        if (showPreFlight != null) {
            val isScenic = showPreFlight!!.lowercase().contains("walk") || 
                           showPreFlight!!.lowercase().contains("meadow") || 
                           showPreFlight!!.lowercase().contains("stroll") || 
                           showPreFlight!!.lowercase().contains("hike")

            AlertDialog(
                onDismissRequest = { viewModel.showPreFlightConfig.value = null },
                title = {
                    Text(
                        "🌌 PRE-FLIGHT ORBITAL CONFIG",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlowNeonTeal,
                        letterSpacing = 1.sp
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Route Profile: \"$showPreFlight\"",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Divider(color = DarkBorderColor)

                        Text("SELECT COMPANION STATUS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GlowNeonPink)
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Manual Solitary
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (!isGuided) GlowNeonPink.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface)
                                    .border(1.dp, if (!isGuided) GlowNeonPink else DarkBorderColor, RoundedCornerShape(8.dp))
                                    .clickable { viewModel.preFlightGuidedByTrainer.value = false }
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.DirectionsRun, contentDescription = "Manual", tint = if (!isGuided) GlowNeonPink else Color.White)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Manual Solo", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("Uncoached", fontSize = 9.sp, color = SoftGreyText)
                                }
                            }

                            // Guided Trainer
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isGuided) GlowNeonTeal.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface)
                                    .border(1.dp, if (isGuided) GlowNeonTeal else DarkBorderColor, RoundedCornerShape(8.dp))
                                    .clickable { viewModel.preFlightGuidedByTrainer.value = true }
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Chat, contentDescription = "Guided", tint = if (isGuided) GlowNeonTeal else Color.White)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Coach Guided", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("Aura active", fontSize = 9.sp, color = SoftGreyText)
                                }
                            }
                        }

                        if (isGuided && isScenic) {
                            Divider(color = DarkBorderColor)
                            Text("AURA WALKING BUDDY OPTIONS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GlowNeonTeal)
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Buddy Conversation Mode", fontSize = 12.sp, color = Color.White)
                                Switch(
                                    checked = walkingBuddyActive,
                                    onCheckedChange = { viewModel.preFlightWalkingBuddyActive.value = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = GlowNeonTeal)
                                )
                            }

                            if (walkingBuddyActive) {
                                Text("Companion Disposition Adapts To:", fontSize = 10.sp, color = SoftGreyText, fontWeight = FontWeight.Bold)
                                
                                val modes = listOf("Relaxed Friendly Chat", "Sad Ear to Vent", "Happy Memories & Stories")
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    modes.forEach { mode ->
                                        val selected = walkingBuddyMode == mode
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (selected) GlowNeonTeal.copy(alpha = 0.1f) else Color.Transparent)
                                                .border(1.dp, if (selected) GlowNeonTeal.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(6.dp))
                                                .clickable { viewModel.preFlightWalkingBuddyMode.value = mode }
                                                .padding(6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            RadioButton(
                                                selected = selected,
                                                onClick = { viewModel.preFlightWalkingBuddyMode.value = mode },
                                                colors = RadioButtonDefaults.colors(selectedColor = GlowNeonTeal)
                                            )
                                            Column {
                                                Text(
                                                    text = when(mode) {
                                                        "Relaxed Friendly Chat" -> "🎮 Friendly Chat & I Spy"
                                                        "Sad Ear to Vent" -> "🩹 Sad Ear (Vent to Aura)"
                                                        else -> "🌟 Happy memories and stories"
                                                    },
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = when(mode) {
                                                        "Relaxed Friendly Chat" -> "Aura plays interactive space I Spy games."
                                                        "Sad Ear to Vent" -> "Aura adapts to comfort a sad/fatigued ear."
                                                        else -> "Replay and catalog sweet stories from your life."
                                                    },
                                                    fontSize = 10.sp,
                                                    color = SoftGreyText
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.startWorkout(
                                programName = showPreFlight!!,
                                guided = isGuided,
                                walkingBuddy = walkingBuddyActive,
                                buddyMode = walkingBuddyMode
                            )
                            viewModel.showPreFlightConfig.value = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GlowNeonTeal, contentColor = CosmicDarkBackground),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("launch_preflight_orbit_btn")
                    ) {
                        Text("LAUNCH ORBITAL GLIDE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.showPreFlightConfig.value = null }) {
                        Text("CANCEL", color = SoftGreyText, fontSize = 11.sp)
                    }
                },
                containerColor = InsetCardDark
            )
        }

        // --- 2. SAMSUNG HEALTH CONNECT & BIOMETRICS BLUETOOTH SYNC ---
        Text("SAMSUNG GALAXY WATCH & HEALTH METRICS CONNECTIVITY", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GlowNeonTeal)
        
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().testTag("galaxy_watch_card")
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Watch,
                            contentDescription = "Watch Icon",
                            tint = if (watchConnected) GlowNeonTeal else SoftGreyText
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Samsung Watch State", fontWeight = FontWeight.Bold)
                    }

                    Switch(
                        checked = watchConnected,
                        onCheckedChange = { viewModel.watchConnected.value = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = GlowNeonTeal),
                        modifier = Modifier.testTag("samsung_watch_switch")
                    )
                }

                if (watchConnected) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(GlowNeonTeal.copy(alpha = 0.08f))
                            .padding(12.dp)
                    ) {
                        Text(
                            "Live Bluetooth Synced Telemetry: Dynamic workout profiles adjust incline and resistance constraints based on watch heart-rate, cadence, and exertion metrics.",
                            fontSize = 12.sp,
                            color = GlowNeonTeal
                        )
                    }

                    // Simulated live Watch Telemetry Sliders so users can immediately test/simulate biological changes!
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Simulated Heart Rate: $heartRate BPM", fontSize = 12.sp, color = SoftGreyText)
                        Slider(
                            value = heartRate.toFloat(),
                            onValueChange = { viewModel.heartRate.value = it.toInt() },
                            valueRange = 60f..200f,
                            colors = SliderDefaults.colors(thumbColor = GlowNeonPink, activeTrackColor = GlowNeonPink)
                        )

                        Text("Simulated Cadence: ${cadenceRPM.toInt()} RPM", fontSize = 12.sp, color = SoftGreyText)
                        Slider(
                            value = cadenceRPM,
                            onValueChange = { viewModel.cadenceRPM.value = it },
                            valueRange = 30f..120f,
                            colors = SliderDefaults.colors(thumbColor = GlowNeonTeal, activeTrackColor = GlowNeonTeal)
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Stress Index: $stressLevel%", fontSize = 11.sp, color = SoftGreyText)
                            Text("Sleep Quality: $sleepScore/100", fontSize = 11.sp, color = SoftGreyText)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("AI Dynamic Auto-Adaptation", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Switch(
                                checked = autoAdaptActive,
                                onCheckedChange = { viewModel.autoAdaptActive.value = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = GlowNeonTeal)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("REAL-TIME HEART RATE EKG WAVESHAPE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GlowNeonPink)
                    
                    // Live EKG Draw Wave Canvas
                    HeartRateEkgCanvas(heartRate)
                } else {
                    Text(
                        "Samsung Health connection inactive. Toggle connected watch state to visualize real-time cardiovascular adaptations & EKG graphs.",
                        color = SoftGreyText,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // --- 3. LIVE PHONE CAMERA OVERLAY SECTION ---
        Text("INTELLIGENT FRONT CAMERA INTERACTION", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GlowNeonTeal)
        
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().testTag("intelligent_camera_card")
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Exertion Form Scanner", fontWeight = FontWeight.Bold)
                        Text("Front camera captures posture alignment to alleviate back pain and checks sweat levels for signs of exhaustion.", fontSize = 12.sp, color = SoftGreyText)
                    }
                    Switch(
                        checked = cameraActive,
                        onCheckedChange = { viewModel.cameraActive.value = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = GlowNeonTeal),
                        modifier = Modifier.testTag("camera_switch")
                    )
                }

                if (cameraActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.DarkGray)
                    ) {
                        // Jetpack CameraX Viewfinder Integration in Compose
                        CameraPreviewPreviewView()

                        // Scanning Laser overlay
                        val transition = rememberInfiniteTransition(label = "laser_transition")
                        val animatedLaserY by transition.animateFloat(
                            initialValue = 0f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(2000, easing = EaseInOutSine),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "laser_y"
                        )

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val h = size.height
                            val w = size.width
                            val laserHeight = h * animatedLaserY
                            drawLine(
                                color = GlowNeonTeal,
                                start = Offset(0f, laserHeight),
                                end = Offset(w, laserHeight),
                                strokeWidth = 5f
                            )
                        }

                        // Scanning overlay states
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(10.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.7f))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text("Alignment: $postureStatus", color = GlowNeonTeal, fontSize = 11.sp)
                            Text("Intensity check: $effortIndicator", color = Color.White, fontSize = 11.sp)
                            Text("Exhaustion Index: $fatigueStatus", color = GlowNeonPink, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

// --- Dynamic Heart Rate EKG Custom Canvas Animation ---
@Composable
fun HeartRateEkgCanvas(heartRate: Int) {
    val transition = rememberInfiniteTransition(label = "ekg_transition")
    val waveOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (100000 / heartRate).coerceIn(300, 1500), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ekg_offset"
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(55.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .border(1.dp, Color(0xFF1E1C2A))
    ) {
        val width = size.width
        val height = size.height
        val midY = height / 2f
        val path = Path()
        
        path.moveTo(0f, midY)
        val numPoints = 12
        val step = width / numPoints
        
        for (i in 0..numPoints) {
            val x = i * step
            // Create EKG pulse waveform shape (qrs spike) at index 4 (relative offset)
            val dynamicIndex = (i + (waveOffset * numPoints).toInt()) % numPoints
            val y = when (dynamicIndex) {
                3 -> midY + 10f
                4 -> midY - 30f
                5 -> midY + 25f
                6 -> midY - 8f
                else -> midY + (Math.sin(i * 1.5).toFloat() * 2f)
            }
            path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = GlowNeonPink,
            style = Stroke(width = 4f)
        )
    }
}

// --- Front CameraX Live Preview Composable Binding ---
@Composable
fun CameraPreviewPreviewView() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(Unit) {
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().apply {
                        surfaceProvider = previewView.surfaceProvider
                    }
                    
                    val cameraSelector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    } else {
                        Log.e("CameraPreviewView", "No camera device found on this virtual hardware.")
                        return@addListener
                    }

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
                } catch (exc: Exception) {
                    Log.e("CameraPreviewView", "Camera binding or retrieval inside listener failed and was caught", exc)
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (exc: Exception) {
            Log.e("CameraPreviewView", "ProcessCameraProvider.getInstance initialization failed and was caught", exc)
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize().testTag("live_camera_view")
    )
}

// --- UI Overlay Form Scanning Viewport ---
@Composable
fun FormOverlayBubble(
    posture: String,
    effort: String,
    fatigue: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(75.dp)
            .clip(CircleShape)
            .border(2.dp, GlowNeonPink, CircleShape)
            .background(Color.DarkGray)
    ) {
        CameraPreviewPreviewView()
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Red.copy(alpha = 0.15f))
        )
    }
}

@Composable
fun MetricCell(label: String, valText: String, icon: ImageVector) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = "cell_icon", tint = GlowNeonPink, modifier = Modifier.size(24.dp))
        Text(label, fontSize = 9.sp, color = SoftGreyText, fontWeight = FontWeight.Bold)
        Text(valText, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
    }
}

fun formatTimer(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}


// --- 2. AURA PERSONAL ACTIVE COACH TAB (Generative Comedy Voice/Text Interaction with Gemini) ---
@Composable
fun AuraCoachingTabScreen(viewModel: AuraViewModel) {
    val coachStyle by viewModel.coachStyleState.collectAsState()
    val companionMessage by viewModel.companionMessage.collectAsState()
    val isThinking by viewModel.isCoachThinking.collectAsState()
    
    val openMicActive by viewModel.openMicActive.collectAsState()
    val openMicState by viewModel.openMicState.collectAsState()
    val openMicThoughtProcess by viewModel.openMicThoughtProcess.collectAsState()

    var localQuery by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Warning if Gemini Key is Placeholder
        if (!GeminiClient.isApiKeyAvailable()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF421D1D))
                    .border(1.dp, Color.Red, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Text("GEMINI KEY PROTOTYPE NOTICE", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("No Gemini API key is configured in your AI Studio secrets panel. Aura is running in Offline Simulation Mode using pre-installed comedic feedback loops! Add a key to activate full contextual reasoning.", fontSize = 11.sp, color = Color.White)
                }
            }
        }

        // Coach Persona Selector card
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().testTag("coach_persona_card")
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Select Aura's Coaching Style Intensity", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                val styles = listOf("Comedic Encourager", "Empathetic Supporter", "Strict Drill Sergeant")
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    styles.forEach { style ->
                        val isSelected = style == coachStyle
                        Button(
                            onClick = { viewModel.coachStyleState.value = style },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) GlowNeonPink else MaterialTheme.colorScheme.background,
                                contentColor = if (isSelected) Color.White else SoftGreyText
                            ),
                            border = BorderStroke(1.dp, if (isSelected) GlowNeonPink else MaterialTheme.colorScheme.outline),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).testTag("style_$style")
                        ) {
                            Text(style.split(" ").last(), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // --- NEW: Interactive Open Mic Integration Panel ---
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().testTag("open_mic_setup_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("🚀 Galaxy Open Mic Mode", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = GlowNeonTeal)
                    Text(
                        "Enables a hands-free continuous conversational flow. Aura uses deep thinking loops and listens after speaking to ensure perfect coordination.",
                        fontSize = 11.sp,
                        color = SoftGreyText
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Switch(
                    checked = openMicActive,
                    onCheckedChange = { viewModel.toggleOpenMic(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = GlowNeonPink,
                        checkedTrackColor = GlowNeonPink.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.testTag("open_mic_switch")
                )
            }
        }

        // Trainer Virtual Avatar Visualization Window
        ElevatedCard(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().weight(1f).testTag("aura_avatar_panel")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Spinning gradient halo avatar representing Aura's voice node
                val infiniteTransition = rememberInfiniteTransition(label = "halo_transition")
                val rotationAngle by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(4000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "halo_rotation"
                )

                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 0.95f,
                    targetValue = 1.05f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = EaseInOutSine),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "halo_pulse"
                )

                Box(
                    modifier = Modifier
                        .size((110.dp * pulseScale))
                        .clip(CircleShape)
                        .drawBehind {
                            drawCircle(
                                brush = Brush.linearGradient(
                                    colors = if (openMicActive) {
                                        listOf(GlowNeonTeal, GlowNeonPink, GlowNeonTeal)
                                    } else {
                                        listOf(Color.Gray, SoftGreyText)
                                    },
                                    start = Offset(0f, 0f),
                                    end = Offset(size.width, size.height)
                                ),
                                style = Stroke(width = 8f),
                                radius = size.minDimension / 2f
                            )
                        }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(85.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (openMicActive) {
                                when (openMicState) {
                                    "LISTENING", "USER_SPEAKING" -> Icons.Default.Mic
                                    "THINKING" -> Icons.Default.GraphicEq
                                    "SPEAKING" -> Icons.Default.VolumeUp
                                    else -> Icons.Default.Hearing
                                }
                            } else {
                                Icons.Default.MicOff
                            },
                            contentDescription = "Aura Mode",
                            tint = if (openMicActive) GlowNeonTeal else SoftGreyText,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    "COACH AURA",
                    color = GlowNeonPink,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )

                Text(
                    "Your Emotionally Intelligent Companion",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Render live frequency waves when Open Mic is active
                if (openMicActive) {
                    Text(
                        when (openMicState) {
                            "LISTENING" -> "🌌 AURA LISTENING... SPEAK NOW"
                            "USER_SPEAKING" -> "🎙️ DETECTING USER SPEECH ACTIVITY..."
                            "THINKING" -> "🧠 DEEP THINKING & REASONING..."
                            "SPEAKING" -> "🪐 AURA SPEAKING..."
                            else -> "🤫 PAUSING & DEEP LISTENING TO CONFIRM SILENCE..."
                        },
                        fontSize = 12.sp,
                        color = when (openMicState) {
                            "USER_SPEAKING" -> GlowNeonPink
                            "THINKING" -> AccentAmber
                            "SPEAKING" -> GlowNeonTeal
                            else -> SoftGreyText
                        },
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        openMicThoughtProcess,
                        fontSize = 12.sp,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Simple animated frequency wave preview canvas
                    val waveScroll by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "wave_scroll"
                    )

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                    ) {
                        val w = size.width
                        val h = size.height
                        val midY = h / 2f
                        val wavePath = Path()
                        wavePath.moveTo(0f, midY)
                        for (xPos in 0..w.toInt() step 5) {
                            val relativeX = xPos.toFloat()
                            val amplitude = when (openMicState) {
                                "USER_SPEAKING" -> 15.dp.toPx()
                                "SPEAKING" -> 12.dp.toPx()
                                "THINKING" -> 4.dp.toPx()
                                "LISTENING" -> 2.dp.toPx()
                                else -> 0f
                            }
                            val sineVal = Math.sin((relativeX / 40f) - (waveScroll * 2 * Math.PI)).toFloat()
                            val yPos = midY + (sineVal * amplitude)
                            wavePath.lineTo(relativeX, yPos)
                        }
                        drawPath(wavePath, color = GlowNeonTeal, style = Stroke(width = 4f))
                    }
                } else {
                    Text(
                        "Mic muted. Toggle Open Mic to enable hand-free conversational orbits.",
                        fontSize = 11.sp,
                        color = SoftGreyText,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bubble coaching message response
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        companionMessage,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("coach_speech_bubble")
                    )
                }
            }
        }

        // Voice dialog bar panel
        Row(
            modifier = Modifier.fillMaxWidth().testTag("chat_input_row"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = localQuery,
                onValueChange = { localQuery = it },
                placeholder = { Text("Ask Aura anything (e.g. 'Motivate me', '/' list excels)") },
                modifier = Modifier.weight(1f).testTag("chat_text_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GlowNeonPink,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (localQuery.isNotBlank()) {
                        viewModel.userChatQuery.value = localQuery
                        viewModel.sendUserPrompt()
                        localQuery = ""
                    }
                    keyboardController?.hide()
                }),
                singleLine = true
            )

            // Dynamic Send Button
            IconButton(
                onClick = { 
                    if (localQuery.isNotBlank()) {
                        viewModel.userChatQuery.value = localQuery
                        viewModel.sendUserPrompt()
                        localQuery = ""
                    }
                    keyboardController?.hide()
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(GlowNeonPink)
                    .testTag("chat_send_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
            }
        }
    }
}


// --- 3. SOCIAL COMMUNITY & FORUMS TAB SCREEN (Interactive Posts feed, Live topic-chats) ---
@Composable
fun SocialNetworkTabScreen(viewModel: AuraViewModel) {
    val subTabId by viewModel.socialSubTabState.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = subTabId,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = GlowNeonTeal,
            modifier = Modifier.testTag("social_sub_tabs")
        ) {
            Tab(selected = subTabId == 0, onClick = { viewModel.socialSubTabState.value = 0 }, text = { Text("Main Newsfeed", fontWeight = FontWeight.Bold) })
            Tab(selected = subTabId == 1, onClick = { viewModel.socialSubTabState.value = 1 }, text = { Text("Discussion Forums", fontWeight = FontWeight.Bold) })
            Tab(selected = subTabId == 2, onClick = { viewModel.socialSubTabState.value = 2 }, text = { Text("Messenger DMs", fontWeight = FontWeight.Bold) })
        }

        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            when (subTabId) {
                0 -> NewsfeedScreen(viewModel)
                1 -> DiscussionRoomsScreen(viewModel)
                2 -> FriendsMessengerScreen(viewModel)
            }
        }
    }
}

// --- NEWSFEED: Post accomplishments, comments, reacts ---
@Composable
fun NewsfeedScreen(viewModel: AuraViewModel) {
    val posts by viewModel.socialPosts.collectAsState()
    var displayWriteFrame by remember { mutableStateOf(false) }
    var inlinePostText by remember { mutableStateOf("") }
    
    // Custom posting feature states
    var selectedFeeling by remember { mutableStateOf("") }
    var selectedImageName by remember { mutableStateOf("") }
    var selectedVideoName by remember { mutableStateOf("") }
    var enteredExternalUrl by remember { mutableStateOf("") }

    val keyboardController = LocalSoftwareKeyboardController.current

    val feelingsList = listOf(
        "Anxious 😰", 
        "Sluggish 🐌", 
        "Energized ⚡", 
        "Relaxed 😌", 
        "Determined 💪", 
        "Overjoyed 😁",
        "Proud ✨"
    )

    val preloadedImages = listOf(
        "Kyoto Walk 🌸", 
        "Swiss Alps 🏔️", 
        "Hawaii Sunset 🌅", 
        "Martian Ride 🌌", 
        "Grand Canyon 🏜️"
    )

    val preloadedVideos = listOf(
        "Interval Climb 🚀", 
        "HIIT Sprinting 🚴", 
        "Warmup Cooldown 🧘", 
        "Scenic Forest Hikes 🌲"
    )

    val funEmojisList = listOf(
        "😀", "😂", "🥰", "😎", "🤔", "😰", "🥳", "😱", 
        "🥑", "🍕", "🍔", "🥗", "🍌", "🥪", "🍫", "☕", "🥤", 
        "🌌", "🚀", "👾", "🚴", "🧘", "💪", "🔥", "✨", "🎉"
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Quick post creation card
        ElevatedCard(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().testTag("newsfeed_composer_card")
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!displayWriteFrame) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Share your elliptical achievements!", fontSize = 13.sp, color = SoftGreyText)
                        Button(
                            onClick = { displayWriteFrame = true },
                            colors = ButtonDefaults.buttonColors(containerColor = GlowNeonPink),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Share Post", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = inlinePostText,
                        onValueChange = { inlinePostText = it },
                        modifier = Modifier.fillMaxWidth().testTag("composer_input"),
                        placeholder = { Text("What did you glide through today, champ?") },
                        maxLines = 3
                    )

                    // Emoji Bar Selection Helper
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("TAP EMOJIS TO INSERT:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GlowNeonTeal)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(funEmojisList) { emoji ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                        .clickable { inlinePostText += emoji }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(emoji, fontSize = 15.sp)
                                }
                            }
                        }
                    }

                    // Feeling status state chooser
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("MY CURRENT EMOTION / STATUS:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GlowNeonTeal)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(feelingsList) { feel ->
                                val isSelected = selectedFeeling == feel
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) GlowNeonTeal.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface)
                                        .border(
                                            1.dp,
                                            if (isSelected) GlowNeonTeal else MaterialTheme.colorScheme.outline,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            selectedFeeling = if (isSelected) "" else feel
                                        }
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = feel,
                                        fontSize = 11.sp,
                                        color = if (isSelected) GlowNeonTeal else Color.White,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Image attachment chooser
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("ATTACH PICTURE:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GlowNeonPink)
                            Box(modifier = Modifier.fillMaxWidth()) {
                                var expandedImgTab by remember { mutableStateOf(false) }
                                Button(
                                    onClick = { expandedImgTab = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        if (selectedImageName.isEmpty()) "Select Photo 🖼️" else selectedImageName,
                                        fontSize = 11.sp,
                                        color = if (selectedImageName.isEmpty()) SoftGreyText else GlowNeonPink
                                    )
                                }
                                DropdownMenu(
                                    expanded = expandedImgTab,
                                    onDismissRequest = { expandedImgTab = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("None") },
                                        onClick = { selectedImageName = ""; expandedImgTab = false }
                                    )
                                    preloadedImages.forEach { img ->
                                        DropdownMenuItem(
                                            text = { Text(img) },
                                            onClick = { selectedImageName = img; expandedImgTab = false }
                                        )
                                    }
                                }
                            }
                        }

                        // Video attachment chooser
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("ATTACH VIDEO:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GlowNeonPink)
                            Box(modifier = Modifier.fillMaxWidth()) {
                                var expandedVidTab by remember { mutableStateOf(false) }
                                Button(
                                    onClick = { expandedVidTab = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        if (selectedVideoName.isEmpty()) "Select Video 📹" else selectedVideoName,
                                        fontSize = 11.sp,
                                        color = if (selectedVideoName.isEmpty()) SoftGreyText else GlowNeonPink
                                    )
                                }
                                DropdownMenu(
                                    expanded = expandedVidTab,
                                    onDismissRequest = { expandedVidTab = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("None") },
                                        onClick = { selectedVideoName = ""; expandedVidTab = false }
                                    )
                                    preloadedVideos.forEach { vid ->
                                        DropdownMenuItem(
                                            text = { Text(vid) },
                                            onClick = { selectedVideoName = vid; expandedVidTab = false }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Share Link textfield
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("SHARE LINKS (YOUTUBE, OTHER APPS):", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GlowNeonTeal)
                        OutlinedTextField(
                            value = enteredExternalUrl,
                            onValueChange = { enteredExternalUrl = it },
                            placeholder = { Text("https://youtube.com/watch?v=...", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
                            leadingIcon = { Icon(Icons.Default.Link, contentDescription = "Link symbol", tint = GlowNeonTeal, modifier = Modifier.size(16.dp)) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                displayWriteFrame = false
                                inlinePostText = ""
                                selectedFeeling = ""
                                selectedImageName = ""
                                selectedVideoName = ""
                                enteredExternalUrl = ""
                            }
                        ) {
                            Text("Cancel", color = SoftGreyText)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (inlinePostText.isNotBlank()) {
                                    viewModel.writeCommunityPost(
                                        text = inlinePostText,
                                        inlineImageName = selectedImageName,
                                        videoName = selectedVideoName,
                                        feeling = selectedFeeling,
                                        url = enteredExternalUrl
                                    )
                                    // Reset states
                                    inlinePostText = ""
                                    selectedFeeling = ""
                                    selectedImageName = ""
                                    selectedVideoName = ""
                                    enteredExternalUrl = ""
                                    displayWriteFrame = false
                                    keyboardController?.hide()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GlowNeonTeal),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("submit_composer_button")
                        ) {
                            Text("Post Now", color = CosmicDarkBackground, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().weight(1f)
        ) {
            items(posts) { post ->
                NewsfeedPostCard(post, viewModel)
            }
        }
    }
}

@Composable
fun NewsfeedPostCard(post: SocialPost, viewModel: AuraViewModel) {
    var isCommentingActive by remember { mutableStateOf(false) }
    var userCommentInput by remember { mutableStateOf("") }
    
    // Parse comments json
    val commentList = remember(post.commentsJson) {
        val list = mutableListOf<Pair<String, String>>()
        try {
            val jsonArray = JSONArray(post.commentsJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(Pair(obj.getString("author"), obj.getString("content")))
            }
        } catch (e: Exception) {
            // handle error
        }
        list
    }

    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().testTag("post_card_${post.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Author row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(GlowNeonTeal.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        post.userAvatar,
                        color = GlowNeonTeal,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                }
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(post.userName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        if (post.attachedFeeling.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(GlowNeonPink.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = post.attachedFeeling,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GlowNeonPink
                                )
                            }
                        }
                    }
                    Text("Elliptical Enthusiast  •  Today", fontSize = 10.sp, color = SoftGreyText)
                }
            }

            // Post content
            Text(post.postText, fontSize = 13.sp, lineHeight = 19.sp)

            // Post Video mock
            if (post.postVideo.isNotEmpty()) {
                var isVideoPlaying by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                        .clickable { isVideoPlaying = !isVideoPlaying }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Black, Color(0xFF1E1E1E), Color(0xFFFF007F).copy(alpha = 0.15f))
                            )
                        )
                    }
                    if (isVideoPlaying) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Red))
                                Text("PLAYING LIVE VIDEO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GlowNeonPink)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(post.postVideo, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Aura streaming synced. Click to stop.", fontSize = 9.sp, color = SoftGreyText)
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(GlowNeonPink.copy(alpha = 0.2f))
                                    .border(1.dp, GlowNeonPink, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play attached video", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(post.postVideo.uppercase(Locale.US), fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("TAP TO STREAM WORKOUT CLIPS", fontSize = 8.sp, color = SoftGreyText)
                        }
                    }
                }
            }

            // Post Image mock
            if (post.postImage.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    // Render scenic wireframe or custom shape representation in newsfeed
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFFFF007F).copy(alpha = 0.2f), Color.Transparent),
                                center = Offset(size.width/2, size.height/2)
                            )
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Landscape, contentDescription = "Scenic Upload", tint = GlowNeonPink)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(post.postImage.replace("_", " ").uppercase(Locale.US), fontSize = 11.sp, color = SoftGreyText, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // External url link clickable inside post
            if (post.externalUrl.isNotEmpty()) {
                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(GlowNeonTeal.copy(alpha = 0.08f))
                        .border(1.dp, GlowNeonTeal.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .clickable {
                            try {
                                uriHandler.openUri(post.externalUrl)
                            } catch (e: Exception) {
                                // Ignore or show fallback
                            }
                        }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Launch, contentDescription = "External URL", tint = GlowNeonTeal, modifier = Modifier.size(14.dp))
                        Column {
                            Text("SHARED EXTERNAL LINK VIA URL", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = GlowNeonTeal)
                            Text(
                                post.externalUrl,
                                fontSize = 11.sp,
                                color = SoftGreyText,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                    Text("GO 🌐", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GlowNeonTeal)
                }
            }

            Divider(color = MaterialTheme.colorScheme.outline)

            // React counters Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Like button
                    IconButton(onClick = { viewModel.toggleLike(post) }, modifier = Modifier.size(34.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ThumbUp,
                                contentDescription = "Like icon",
                                tint = if (post.hasLiked) GlowNeonTeal else SoftGreyText,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${post.likes}", fontSize = 11.sp)
                        }
                    }

                    // Love button
                    IconButton(onClick = { viewModel.toggleLove(post) }, modifier = Modifier.size(34.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Love icon",
                                tint = if (post.hasLoved) GlowNeonPink else SoftGreyText.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${post.loves}", fontSize = 11.sp)
                        }
                    }
                }

                TextButton(onClick = { isCommentingActive = !isCommentingActive }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Comment, contentDescription = "Comment Icon", tint = GlowNeonTeal, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Comment (${commentList.size})", color = GlowNeonTeal, fontSize = 11.sp)
                    }
                }
            }

            // Expanded comments section
            if (isCommentingActive) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    commentList.forEach { (author, text) ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.background)
                                .padding(8.dp)
                        ) {
                            Column {
                                Text(author, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = GlowNeonTeal)
                                Text(text, fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground)
                            }
                        }
                    }

                    // Input bar to add comment
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = userCommentInput,
                            onValueChange = { userCommentInput = it },
                            modifier = Modifier.weight(1f).testTag("comment_input_${post.id}"),
                            placeholder = { Text("Write a comment...", fontSize = 11.sp) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
                            singleLine = true
                        )
                        IconButton(
                            onClick = {
                                viewModel.addPostComment(post, userCommentInput)
                                userCommentInput = ""
                            },
                            modifier = Modifier.size(38.dp).clip(CircleShape).background(GlowNeonTeal).testTag("post_comment_submit_${post.id}")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Add Comment", tint = CosmicDarkBackground, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}


// --- DISCUSSION FORUM: Live text + voice forum threads ---
@Composable
fun DiscussionRoomsScreen(viewModel: AuraViewModel) {
    val rooms = listOf("Meals & Nutrition Info", "Elliptical HIIT Crew", "Form Guidance", "Daily Motivation Quotes")
    val selectedRoom by viewModel.selectedForumRoom
    val messages by viewModel.forumMessages.collectAsState(emptyList())
    var textInput by remember { mutableStateOf("") }
    var isMicActive by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Horizontal Room selector list
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(rooms) { room ->
                val isSelected = selectedRoom == room
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) GlowNeonTeal else MaterialTheme.colorScheme.surface)
                        .border(1.dp, if (isSelected) GlowNeonTeal else MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                        .clickable { viewModel.selectedForumRoom.value = room }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("forum_room_$room")
                ) {
                    Text(
                        room,
                        color = if (isSelected) CosmicDarkBackground else SoftGreyText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Message Feed
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            if (messages.isEmpty()) {
                Text(
                    "Welcome! This forum room is currently quiet. Be the first to initiate our conversation!",
                    modifier = Modifier.align(Alignment.Center),
                    fontSize = 12.sp,
                    color = SoftGreyText,
                    textAlign = TextAlign.Center
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(messages) { msg ->
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(msg.userName, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = GlowNeonPink)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("•  10m ago", fontSize = 8.sp, color = SoftGreyText)
                            }
                            if (msg.isVoice) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(GlowNeonPink.copy(alpha = 0.12f))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Mic, contentDescription = "Voice post", tint = GlowNeonPink, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Voice Clip: " + msg.messageText, fontSize = 12.sp, color = GlowNeonPink)
                                }
                            } else {
                                Text(msg.messageText, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                    }
                }
            }
        }

        // Message controls (With toggle mic option)
        Row(
            modifier = Modifier.fillMaxWidth().testTag("forum_input_row"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = { 
                    isMicActive = !isMicActive 
                    if (isMicActive) {
                        viewModel.sendForumMessage("Pumping along at 85 cadence! Real effort guys!", isVoiced = true)
                        isMicActive = false
                    }
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isMicActive) Color.Red else MaterialTheme.colorScheme.surface)
                    .testTag("forum_mic_button")
            ) {
                Icon(Icons.Default.Mic, contentDescription = "Microphone", tint = if (isMicActive) Color.White else GlowNeonPink)
            }

            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { Text("Write publicly in forum...") },
                modifier = Modifier.weight(1f).testTag("forum_text_field"),
                shape = RoundedCornerShape(12.dp)
            )

            IconButton(
                onClick = {
                    viewModel.sendForumMessage(textInput)
                    textInput = ""
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(GlowNeonTeal)
                    .testTag("forum_send_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send forum message", tint = CosmicDarkBackground)
            }
        }
    }
}

// --- DIRECT MESSENGER: Private messenger DMs ---
@Composable
fun FriendsMessengerScreen(viewModel: AuraViewModel) {
    val friendsList by viewModel.friendsList.collectAsState()
    val selectedFriend by viewModel.selectedFriendChat
    val messages by viewModel.directMessages.collectAsState(emptyList())
    var dmInput by remember { mutableStateOf("") }
    var addFriendNameInput by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: Add Friend Console
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().testTag("add_friend_card")
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "🌌 CONNECT WITH RIVAL CELESTIAL GLIDERS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = GlowNeonTeal,
                    letterSpacing = 1.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = addFriendNameInput,
                        onValueChange = { addFriendNameInput = it },
                        modifier = Modifier.weight(1f).testTag("add_friend_input_text"),
                        placeholder = { Text("Enter spacesuit tag / crew name...", fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GlowNeonTeal,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    Button(
                        onClick = {
                            if (addFriendNameInput.isNotBlank()) {
                                viewModel.addFriend(addFriendNameInput.trim())
                                addFriendNameInput = ""
                            }
                            keyboardController?.hide()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GlowNeonTeal, contentColor = CosmicDarkBackground),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("add_friend_submit")
                    ) {
                        Text("Add Crew", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Section: Crew Members Status List
        Text("CREW LIST (REAL-TIME ORBITAL TELEMETRY)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GlowNeonTeal)
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(friendsList) { friend ->
                val isSelectedChat = friend.name == selectedFriend
                ElevatedCard(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (isSelectedChat) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectedFriendChat.value = friend.name }
                        .testTag("friend_card_${friend.name}")
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Green online status dot vs grey offline dots
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (friend.isOnline) Color(0xFF00FF66) else Color.Gray)
                                )
                                Text(
                                    friend.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                            }
                            // Direct messaging state tag
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSelectedChat) GlowNeonPink.copy(alpha = 0.2f) else MaterialTheme.colorScheme.background)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    if (isSelectedChat) "ACTIVE Private Chat" else "Click to Chat",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelectedChat) GlowNeonPink else SoftGreyText
                                )
                            }
                        }

                        // Workout status telemetry
                        if (friend.isOnline && friend.activeWorkout != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.DirectionsRun,
                                        contentDescription = "Active workout icon",
                                        tint = GlowNeonTeal,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        "ACTIVE ON GRIDS: \"${friend.activeWorkout}\"",
                                        fontSize = 11.sp,
                                        color = GlowNeonTeal,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    // Challenger Join
                                    Button(
                                        onClick = { viewModel.challengeFriend(friend.name) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = GlowNeonPink,
                                            contentColor = Color.White
                                        ),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.height(28.dp).testTag("challenge_btn_${friend.name}")
                                    ) {
                                        Text("⚔️ Challenger Join", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // Do own workout
                                    OutlinedButton(
                                        onClick = { viewModel.startWorkout("Lucerne Hills Climb") },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GlowNeonTeal),
                                        border = BorderStroke(1.dp, GlowNeonTeal.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.height(28.dp).testTag("own_workout_btn_${friend.name}")
                                    ) {
                                        Text("🚶 Do Own", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            Text(
                                "💤 Currently Offline. No biometric signals broadcasted.",
                                fontSize = 11.sp,
                                color = SoftGreyText
                            )
                        }
                    }
                }
            }
        }

        // Section: Direct Private Message Box for selected user
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("PRIVATE CHAT CONSOLE (SECURED CORRIDOR VS $selectedFriend)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GlowNeonTeal, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Audio Call Icon Button
                IconButton(
                    onClick = { viewModel.startCall(selectedFriend, isVideo = false) },
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(GlowNeonTeal.copy(alpha = 0.15f)).testTag("audio_call_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = "Audio Call",
                        tint = GlowNeonTeal,
                        modifier = Modifier.size(14.dp)
                    )
                }
                // Video Call Icon Button
                IconButton(
                    onClick = { viewModel.startCall(selectedFriend, isVideo = true) },
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(GlowNeonPink.copy(alpha = 0.15f)).testTag("video_call_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = "Video Call",
                        tint = GlowNeonPink,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            if (messages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No frequency history found. Send a private text signal below to open transmission!",
                        color = SoftGreyText,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(messages) { msg ->
                        val isMe = msg.isFromUser
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 12.dp,
                                            topEnd = 12.dp,
                                            bottomStart = if (isMe) 12.dp else 0.dp,
                                            bottomEnd = if (isMe) 0.dp else 12.dp
                                        )
                                    )
                                    .background(if (isMe) GlowNeonPink else MaterialTheme.colorScheme.background)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                if (msg.isVoice) {
                                    var isPlayingByUI by remember { mutableStateOf(false) }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        IconButton(
                                            onClick = { 
                                                isPlayingByUI = !isPlayingByUI 
                                                if (isPlayingByUI) {
                                                    viewModel.playCosmicChimeMelody()
                                                }
                                            },
                                            modifier = Modifier.size(24.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))
                                        ) {
                                            Icon(
                                                imageVector = if (isPlayingByUI) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = "Play voice memo",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Column {
                                            Text(msg.messageText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            // Cute interactive cosmic soundwaves representation
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                val stepsC = 12
                                                for (sIdx in 0 until stepsC) {
                                                    val heightDp = if (isPlayingByUI) {
                                                        (6..22).random().dp
                                                    } else {
                                                        (10 + (sIdx * 3) % 12).dp
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .width(2.dp)
                                                            .height(heightDp)
                                                            .clip(RoundedCornerShape(1.dp))
                                                            .background(Color.White.copy(alpha = 0.6f))
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else if (msg.isCallLog) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (msg.isCallVideo) Icons.Default.Videocam else Icons.Default.Phone,
                                            contentDescription = "Call Logo",
                                            tint = GlowNeonTeal,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(msg.messageText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = GlowNeonTeal)
                                    }
                                } else {
                                    Text(msg.messageText, fontSize = 12.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Message input bar with Recording mode
        val isRecording by viewModel.isRecordingVoiceMessage.collectAsState()
        val recordingSecs by viewModel.voiceMessageTimerSeconds.collectAsState()

        if (isRecording) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Red.copy(alpha = 0.15f))
                    .border(1.dp, Color.Red, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color.Red)
                    )
                    Text(
                        "Voice Memo: ${viewModel.formatVoiceDuration(recordingSecs)} / 3:00 (Limit)",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { viewModel.cancelVoiceRecording() },
                        modifier = Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)).testTag("voice_cancel_btn")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Cancel Recording", tint = Color.Red, modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = { viewModel.stopAndSendVoiceMessage() },
                        modifier = Modifier.size(32.dp).clip(CircleShape).background(Color.Green).testTag("voice_submit_btn")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Send Recording", tint = CosmicDarkBackground, modifier = Modifier.size(16.dp))
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().testTag("messenger_input_row"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Mic send voice memo button
                IconButton(
                    onClick = { viewModel.startVoiceRecording() },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(GlowNeonPink.copy(alpha = 0.12f))
                        .testTag("messenger_mic_button")
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Record Voice Reply", tint = GlowNeonPink)
                }

                OutlinedTextField(
                    value = dmInput,
                    onValueChange = { dmInput = it },
                    placeholder = { Text("Private message to $selectedFriend...") },
                    modifier = Modifier.weight(1f).testTag("messenger_text_field"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GlowNeonTeal,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                IconButton(
                    onClick = {
                        if (dmInput.isNotBlank()) {
                            viewModel.sendDirectMessage(dmInput)
                            dmInput = ""
                        }
                        keyboardController?.hide()
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(GlowNeonTeal)
                        .testTag("messenger_send_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send DM", tint = CosmicDarkBackground)
                }
            }
        }
    }
}


// --- 4. PROGRESS TAB SCREEN (Weight tracker custom polyline chart, photos transformation card) ---
@Composable
fun ProgressTrackerTabScreen(viewModel: AuraViewModel) {
    val scrollState = rememberScrollState()
    val weights by viewModel.weightRecords.collectAsState()
    val photos by viewModel.progressPhotos.collectAsState()
    
    var weightInput by remember { mutableStateOf("") }
    var muscleInput by remember { mutableStateOf("") }
    var photoNoteInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Stats Overview Glow Card
        ElevatedCard(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().testTag("progress_header_card")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val brush = Brush.verticalGradient(
                            colors = listOf(GlowNeonTeal.copy(alpha = 0.12f), Color.Transparent)
                        )
                        drawRect(brush)
                    }
                    .padding(20.dp)
            ) {
                Column {
                    Text("FITNESS EVOLUTION INDEX", color = GlowNeonTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Your Elliptical Progress", fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Every stride builds muscle and sheds density. Incline workouts automatically optimize lower leg biomechanics and calorie burning parameters.", color = SoftGreyText, fontSize = 13.sp)
                }
            }
        }

        // --- CUSTOM CANVAS WEIGHT LINE CHART GRAPH ---
        Text("WEIGHT & MUSCLE MASS CHART HISTORY", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GlowNeonTeal)
        
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().testTag("weight_chart_card")
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (weights.isEmpty()) {
                    Text("No weights logged yet. Input values below to visualize your glowing progress timeline!", color = SoftGreyText, fontSize = 12.sp)
                } else {
                    Text("Total Loss: -6.7 lbs  |  Muscle Mass gain: +1.8 lbs", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    
                    // Draw custom Compose Canvas polyline graph with area gradients
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .background(Color.Transparent)
                    ) {
                        val w = size.width
                        val h = size.height
                        
                        // draw background lines
                        for (grid in 1..4) {
                            val y = grid * h / 5
                            drawLine(color = DarkBorderColor, start = Offset(0f, y), end = Offset(w, y), strokeWidth = 1f)
                        }

                        // Plot weight nodes
                        val ptsSize = weights.size
                        val stepX = w / (ptsSize - 1).coerceAtLeast(1)
                        val maxWeight = 190f
                        val minWeight = 170f
                        val weightRange = maxWeight - minWeight

                        val polyline = Path()
                        val fillPath = Path()
                        
                        weights.forEachIndexed { i, record ->
                            val x = i * stepX
                            val normalizeY = (record.weightLb - minWeight) / weightRange
                            val y = h - (normalizeY * h * 0.7f + h * 0.15f)
                            
                            if (i == 0) {
                                polyline.moveTo(x, y)
                                fillPath.moveTo(x, h)
                                fillPath.lineTo(x, y)
                            } else {
                                polyline.lineTo(x, y)
                                fillPath.lineTo(x, y)
                            }
                            // draw circles on values
                            drawCircle(color = GlowNeonTeal, radius = 5.dp.toPx(), center = Offset(x, y))
                        }
                        fillPath.lineTo((ptsSize - 1) * stepX, h)
                        fillPath.close()

                        // Draw path strokes & gradients
                        drawPath(polyline, color = GlowNeonTeal, style = Stroke(width = 6f))
                        drawPath(
                            fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(GlowNeonTeal.copy(alpha = 0.25f), Color.Transparent)
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        weights.forEach { record ->
                            Text(record.date, fontSize = 10.sp, color = SoftGreyText)
                        }
                    }
                }
            }
        }

        // Weight Input Panel
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().testTag("weight_logger_card")
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Log Weight & Muscle Metrics", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = { weightInput = it },
                        placeholder = { Text("Weight (lbs)") },
                        modifier = Modifier.weight(1f).testTag("weight_input_field")
                    )

                    OutlinedTextField(
                        value = muscleInput,
                        onValueChange = { muscleInput = it },
                        placeholder = { Text("Muscle %") },
                        modifier = Modifier.weight(1f).testTag("muscle_input_field")
                    )
                }

                Button(
                    onClick = {
                        val w = weightInput.toFloatOrNull() ?: 0f
                        val m = muscleInput.toFloatOrNull() ?: 0f
                        if (w > 0f) {
                            viewModel.addNewWeight(w, m)
                            weightInput = ""
                            muscleInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GlowNeonTeal),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("submit_weight_button")
                ) {
                    Text("ADD METRIC POINT", color = CosmicDarkBackground, fontWeight = FontWeight.Bold)
                }
            }
        }

        // --- PROGRESS PHOTO TRANSFORMATION GALLERY ---
        Text("CINEMATIC TRANSFORMATION GALLERY", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GlowNeonTeal)
        
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().testTag("photos_gallery_card")
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Progress Photos", fontWeight = FontWeight.Bold)
                    Icon(Icons.Default.PhotoCamera, contentDescription = "Camera Icon", tint = GlowNeonPink)
                }

                if (photos.isEmpty()) {
                    Text("No visual records logged yet. Insert a simulated image capture to build your aesthetic journey gallery!", color = SoftGreyText, fontSize = 12.sp)
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(photos) { photo ->
                            Column(
                                modifier = Modifier
                                    .width(135.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.background)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(95.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.Black),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        drawRect(
                                            brush = Brush.linearGradient(
                                                colors = listOf(Color(0xFF0F0E17), Color(0xFF1E1C2A))
                                            )
                                        )
                                    }
                                    Icon(Icons.Default.Person, contentDescription = "Avatar mock", tint = GlowNeonTeal, modifier = Modifier.size(28.dp))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(photo.date, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(photo.notes, fontSize = 9.sp, color = SoftGreyText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }

                // Add simulated progress photo
                OutlinedTextField(
                    value = photoNoteInput,
                    onValueChange = { photoNoteInput = it },
                    placeholder = { Text("Enter photo description (e.g. Week 5, definition increases)") },
                    modifier = Modifier.fillMaxWidth().testTag("photo_description")
                )

                Button(
                    onClick = {
                        if (photoNoteInput.isNotEmpty()) {
                            viewModel.addMockProgressPhoto(photoNoteInput)
                            photoNoteInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GlowNeonPink),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("capture_photo_button")
                ) {
                    Text("CAPTURE SIMULATED PROGRESS PHOTO", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}


// --- 5. MILESTONE EMBLEMS & LEADERBOARD TAB (Gamified achievements dashboard) ---
@Composable
fun LeaderboardAndAchievementsTabScreen(viewModel: AuraViewModel) {
    var sectionId by remember { mutableIntStateOf(0) } // 0 = Badge accomplishments, 1 = Leaderboard compare

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = sectionId,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = GlowNeonTeal,
            modifier = Modifier.testTag("gamification_sub_tabs")
        ) {
            Tab(selected = sectionId == 0, onClick = { sectionId = 0 }, text = { Text("Milestones & Badges", fontWeight = FontWeight.Bold) })
            Tab(selected = sectionId == 1, onClick = { sectionId = 1 }, text = { Text("Friend Leaderboard", fontWeight = FontWeight.Bold) })
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .padding(16.dp)
        ) {
            if (sectionId == 0) {
                BadgesListAccomplishments(viewModel)
            } else {
                LeaderboardRankingScreen(viewModel)
            }
        }
    }
}

// Accomplishment badging list view
@Composable
fun BadgesListAccomplishments(viewModel: AuraViewModel) {
    val badges by viewModel.badges.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) {
        Text("UNLOCKED FITNESS BADGES & EMBLEMS", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GlowNeonTeal)
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(badges) { badge ->
                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (badge.isUnlocked) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("badge_card_${badge.title}")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val isStar = badge.iconName == "Grade" || badge.iconName == "Emoji:⭐"
                        if (badge.iconName.startsWith("Emoji:")) {
                            val emojiChar = badge.iconName.substringAfter("Emoji:")
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (badge.isUnlocked) {
                                            if (isStar) AccentAmber.copy(alpha = 0.2f) else GlowNeonTeal.copy(alpha = 0.2f)
                                        } else {
                                            Color.DarkGray.copy(alpha = 0.2f)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (badge.isUnlocked) emojiChar else "❓",
                                    fontSize = 28.sp
                                )
                            }
                        } else {
                            Icon(
                                imageVector = when (badge.iconName) {
                                    "Landscape" -> Icons.Default.Landscape
                                    "Scale" -> Icons.Default.TrendingDown
                                    "Explore" -> Icons.Default.Explore
                                    "Grade" -> Icons.Default.Grade
                                    "Star" -> Icons.Default.Grade
                                    else -> Icons.Default.EmojiEvents
                                },
                                contentDescription = "Badge Emblem",
                                tint = if (badge.isUnlocked) {
                                    if (isStar) AccentAmber else GlowNeonTeal
                                } else {
                                    SoftGreyText
                                },
                                modifier = Modifier.size(38.dp)
                            )
                        }
                        Text(
                            badge.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (badge.isUnlocked) {
                                if (isStar) AccentAmber else MaterialTheme.colorScheme.onSurface
                            } else {
                                SoftGreyText
                            },
                            textAlign = TextAlign.Center
                        )
                        Text(
                            badge.description,
                            fontSize = 10.sp,
                            color = SoftGreyText,
                            minLines = 2,
                            textAlign = TextAlign.Center
                        )
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (badge.isUnlocked) {
                                    if (isStar) AccentAmber.copy(alpha = 0.15f) else GlowNeonPink.copy(alpha = 0.15f)
                                } else {
                                    Color.DarkGray
                                })
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                if (badge.isUnlocked) "UNLOCKED" else "LOCKED",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (badge.isUnlocked) {
                                    if (isStar) AccentAmber else GlowNeonPink
                                } else {
                                    SoftGreyText
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// Leaderboard Screen Compare with online friends
@Composable
fun LeaderboardRankingScreen(viewModel: AuraViewModel) {
    val synced by viewModel.isLeaderboardValid.collectAsState()
    val steps by viewModel.userSteps.collectAsState()
    val completedChallenges by viewModel.numChallengesCompleted.collectAsState()
    
    // User points formula: steps * 1 (1 point per step) plus completed challenges (20 pts each challenge completed)
    val userScore = steps + (completedChallenges * 20)

    // Dynamic rankings (Emily is the current threshold target of 12,500 Cal/pts)
    val baseRankings = listOf(
        Pair("Emily (Sprint Queen)", 12500),
        Pair("Jake (Alpine Master)", 11350),
        Pair("Sarah Miller", 9200),
        Pair("David Diaz", 8400)
    )

    // Insert user's dynamic points and sort descending
    val dynamicRankings = remember(userScore) {
        val list = baseRankings.toMutableList()
        list.add(Pair("You (Glider Legend)", userScore))
        list.sortedByDescending { it.second }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp), 
        modifier = Modifier.fillMaxSize().testTag("leaderboard_screen")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "WEEKLY LEADERBOARD RANKS", 
                fontSize = 14.sp, 
                fontWeight = FontWeight.Bold, 
                color = GlowNeonTeal
            )
            
            // Sync status badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(30.dp))
                    .background(if (synced) GlowNeonTeal.copy(alpha = 0.15f) else Color.Red.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (synced) "SYNCED" else "OUT OF SYNC",
                    color = if (synced) GlowNeonTeal else Color.Red,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        if (!synced) {
            // Elegant error / call to action notice
            ElevatedCard(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = Color.Red.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth().border(1.dp, Color.Red.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp), 
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.SyncProblem, contentDescription = "Out of sync", tint = Color.Red, modifier = Modifier.size(34.dp))
                    Text(
                        "RANKS NOT IN SYNC", 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 14.sp, 
                        color = Color.Red
                    )
                    Text(
                        "Leaderboard parameters are valid only when watch, workout sessions, or medical data are synced. Broadcast your biometrics now to secure your rank!",
                        fontSize = 11.sp,
                        color = SoftGreyText,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = { viewModel.syncData() },
                        colors = ButtonDefaults.buttonColors(containerColor = GlowNeonTeal),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("leaderboard_sync_btn")
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync Now", tint = CosmicDarkBackground)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SYNC WATCH & HEALTH APPS", color = CosmicDarkBackground, fontWeight = FontWeight.Black, fontSize = 11.sp)
                    }
                }
            }
        } else {
            // Synced rankings screen!
            Text(
                "Rankings are verified using live synced step data + extra daily achievements. (+1 pt per step, +20 pts per challenge completion)",
                fontSize = 11.sp,
                color = SoftGreyText
            )

            dynamicRankings.forEachIndexed { index, (name, score) ->
                val isUser = name.contains("You")
                val rankNum = index + 1
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isUser) GlowNeonTeal.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp, 
                            if (isUser) GlowNeonTeal.copy(alpha = 0.4f) else DarkBorderColor, 
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "#$rankNum",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = if (rankNum <= 3) AccentAmber else SoftGreyText,
                                modifier = Modifier.width(28.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(if (isUser) GlowNeonTeal.copy(alpha = 0.2f) else GlowNeonPink.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    name[0].toString(), 
                                    color = if (isUser) GlowNeonTeal else GlowNeonPink, 
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column {
                                Text(
                                    name, 
                                    fontWeight = if (isUser) FontWeight.ExtraBold else FontWeight.Bold, 
                                    fontSize = 14.sp,
                                    color = if (isUser) GlowNeonTeal else Color.White
                                )
                                if (isUser && completedChallenges > 0) {
                                    Text(
                                        "Includes $completedChallenges Gold Star challenges!",
                                        fontSize = 10.sp,
                                        color = AccentAmber
                                    )
                                }
                            }
                        }

                        Text(
                            java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(score) + " PTS", 
                            fontWeight = FontWeight.Black, 
                            fontSize = 14.sp, 
                            color = if (isUser) GlowNeonTeal else Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Extra Sync Taps
            Button(
                onClick = { viewModel.syncData() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, GlowNeonTeal.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().testTag("sync_recal_btn")
            ) {
                Icon(Icons.Default.Sync, contentDescription = "Sync", tint = GlowNeonTeal)
                Spacer(modifier = Modifier.width(6.dp))
                Text("RE-SYNC LATEST WATCH PROGRESS", color = GlowNeonTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}


// --- 6. SETTINGS & HARDWARE CONFIG (Guides, Theme toggling & Key monitor) ---
@Composable
fun SettingsConfigTabScreen(viewModel: AuraViewModel) {
    val scrollState = rememberScrollState()
    val isApiKeyActive = GeminiClient.isApiKeyAvailable()
    val isDarkByModel by viewModel.darkThemeEnabled.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().testTag("settings_header_card")
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AURA PLATFORM HUB", fontWeight = FontWeight.Black, fontSize = 16.sp, color = GlowNeonTeal)
                Text("Device Config, Theme Settings and Gemini API Integrations", fontSize = 12.sp, color = SoftGreyText)
            }
        }

        // Accessibility & Themes Switch
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().testTag("theme_switch_card")
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Accessibility & Aesthetics", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DarkMode, contentDescription = "Dark mode")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Dark Mode Theme Status")
                    }

                    Switch(
                        checked = isDarkByModel,
                        onCheckedChange = { viewModel.darkThemeEnabled.value = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = GlowNeonPink),
                        modifier = Modifier.testTag("dark_mode_switch")
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(GlowNeonPink.copy(alpha = 0.08f))
                        .padding(10.dp)
                ) {
                    Text(
                        "Dark Mode delivers a high-contrast black/neon viewport suitable for low illumination gym environments and simplifies font readability.",
                        fontSize = 11.sp,
                        color = GlowNeonPink
                    )
                }
            }
        }

        // Hardware Watch and Camera Instructions Guideline
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().testTag("instructions_card")
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Bluetooth Samsung Galaxy Watch Linking Guide", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("1. Open Samsung Galaxy Store and download the Aura Core Watch Wearable application.", fontSize = 12.sp, color = SoftGreyText)
                Text("2. Turn on watch Bluetooth, wear study watch snugly on preferred wrist and trigger automatic pairing overlay.", fontSize = 12.sp, color = SoftGreyText)
                Text("3. Stride telemetry automatically transmits heart rate, stress levels and calories parameters continuously.", fontSize = 12.sp, color = SoftGreyText)
            }
        }

        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().testTag("keys_info_card")
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("API Secret Status Verification", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Gemini API Client Active")
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isApiKeyActive) Color(0xFF1E4620) else Color(0xFF5E2727))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            if (isApiKeyActive) "SECURE" else "SIMULATED",
                            color = if (isApiKeyActive) Color.Green else Color.Red,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text("Manage your keys securely using the Secrets panel inside the AI Studio configuration interface.", fontSize = 11.sp, color = SoftGreyText)
            }
        }
    }
}

// --- 6. USER PROFILE TAB SCREEN (Profile picture, bio, achievements, add friend, and private DMs) ---
@Composable
fun ProfileTabScreen(viewModel: AuraViewModel) {
    val scrollState = rememberScrollState()
    val bioText by viewModel.userBio.collectAsState()
    val accomplishments by viewModel.userAccomplishments.collectAsState()
    val friendsList by viewModel.friendsList.collectAsState()
    
    // Manage profile focus selection: "My Profile" vs discoverable crew members
    var profileViewFocus by remember { mutableStateOf("My Profile") } 
    var showEditBioDialog by remember { mutableStateOf(false) }
    var draftBio by remember { mutableStateOf(bioText) }

    // Additional mock discoverable crew members
    val discoverableCrew = listOf(
        Triple("Sarah (Sprint Queen)", "🧑‍🚀 Veteran orbital elliptical sprinter seeking high-intensity intervals all over the galaxy path! Live pace tracker.", "Completed 52 rides • Peak Cadence 122 RPM • Speed Demon Badge"),
        Triple("Marcus (Endurance Beast)", "🤖 Long path flat stroll and canyon hiker. Sustaining active cardio zones for 60+ minutes smoothly.", "Completed 88 miles • Endurance Master Badge • Heart healthy status"),
        Triple("Elena (Gliding Nova)", "✨ Stellar spacer. Love doing late night relaxed walks in Kyoto Bamboo groves with Aura trainer walking buddy chat active.", "Completed 18 walks • Sunrise Champion • Warm soul")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Tab selector inside Profile tab: My Profile vs Discover Crew
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(InsetCardDark)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (profileViewFocus == "My Profile") GlowNeonTeal.copy(alpha = 0.2f) else Color.Transparent)
                    .clickable { profileViewFocus = "My Profile" }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("My Profile", color = if (profileViewFocus == "My Profile") GlowNeonTeal else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (profileViewFocus != "My Profile") GlowNeonTeal.copy(alpha = 0.2f) else Color.Transparent)
                    .clickable { profileViewFocus = discoverableCrew[0].first }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Discover Crew", color = if (profileViewFocus != "My Profile") GlowNeonTeal else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        if (profileViewFocus == "My Profile") {
            // My Profile card view
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().testTag("my_profile_container")
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Profile image icon circle with deep space feeling background
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(GlowNeonPink, Color.Transparent)))
                            .border(2.dp, GlowNeonPink, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🧑‍🚀", fontSize = 54.sp)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Commander Glider (You)", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White)
                        Text("Online status: active in solar orbit", fontSize = 11.sp, color = GlowNeonTeal)
                    }

                    Divider(color = DarkBorderColor)

                    // Bio Header & Edit button
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("BIOSPHERIC BIO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SoftGreyText)
                            TextButton(onClick = {
                                draftBio = bioText
                                showEditBioDialog = true
                            }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Bio", modifier = Modifier.size(11.dp), tint = GlowNeonTeal)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Edit Bio", fontSize = 11.sp, color = GlowNeonTeal)
                                }
                            }
                        }
                        Text(
                            bioText,
                            fontSize = 13.sp,
                            color = Color.White,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            lineHeight = 18.sp
                        )
                    }

                    Divider(color = DarkBorderColor)

                    // Accomplishments List
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🏆 GLIDEPATH ACCOMPLISHMENTS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SoftGreyText)
                        
                        val accoms = accomplishments.split("•")
                        accoms.forEach { acc ->
                            if (acc.trim().isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black.copy(alpha = 0.2f))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("⭐", fontSize = 14.sp)
                                    Text(acc.trim(), fontSize = 12.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Discover other crew members list
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                discoverableCrew.forEach { (name, _, _) ->
                    val selected = profileViewFocus == name
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) GlowNeonPink.copy(alpha = 0.2f) else InsetCardDark)
                            .border(1.dp, if (selected) GlowNeonPink else DarkBorderColor, RoundedCornerShape(8.dp))
                            .clickable { profileViewFocus = name }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(name.split(" ")[0], color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            val curCrew = discoverableCrew.find { it.first == profileViewFocus } ?: discoverableCrew[0]
            val (cName, cBio, cAcc) = curCrew

            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().testTag("discover_profile_container")
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    
                    // Profile picture representation
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(GlowNeonTeal, Color.Transparent)))
                            .border(2.dp, GlowNeonTeal, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            when {
                                cName.contains("Sarah") -> "👩‍🚀"
                                cName.contains("Marcus") -> "🤖"
                                else -> "✨"
                            },
                            fontSize = 54.sp
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(cName, fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White)
                        
                        val isFriendAlready = friendsList.any { it.name.lowercase().contains(cName.split(" ")[0].lowercase()) }
                        Text(
                            if (isFriendAlready) "Online • Already in Friend List" else "Online • Broadcast Signal Available",
                            fontSize = 11.sp,
                            color = if (isFriendAlready) GlowNeonTeal else SoftGreyText
                        )
                    }

                    // Direct Message and Add Friend Buttons side by side (crucial user intent!)
                    val isFriendAlready = friendsList.any { it.name.lowercase().contains(cName.split(" ")[0].lowercase()) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (!isFriendAlready) {
                                    viewModel.addFriend(cName)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFriendAlready) Color.Gray else GlowNeonPink,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).testTag("profile_friend_add_btn")
                        ) {
                            Text(
                                if (isFriendAlready) "✅ Crew Added" else "➕ Add Friend",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                val standardMatchName = when {
                                    cName.contains("Sarah") -> "Sarah (Sprint Queen)"
                                    cName.contains("Marcus") -> "Marcus (Endurance Beast)"
                                    else -> "Elena (Gliding Nova)"
                                }
                                viewModel.selectedFriendChat.value = standardMatchName
                                viewModel.activeTabState.value = 2
                                viewModel.socialSubTabState.value = 2
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GlowNeonTeal,
                                contentColor = CosmicDarkBackground
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).testTag("profile_friend_dm_btn")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Comment, contentDescription = "DM", modifier = Modifier.size(14.dp))
                                Text("Direct Msg", fontSize = 12.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }

                    Divider(color = DarkBorderColor)

                    // Bio Section
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("GLIDER BIOSPHERE TAGLINE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SoftGreyText)
                        Text(
                            cBio,
                            fontSize = 13.sp,
                            color = Color.White,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            lineHeight = 18.sp
                        )
                    }

                    Divider(color = DarkBorderColor)

                    // Accomplishments list
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🏆 GLIDEPATH ACCOMPLISHMENTS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SoftGreyText)
                        val spl = cAcc.split("•")
                        spl.forEach { sc ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.2f))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("🚀", fontSize = 14.sp)
                                Text(sc.trim(), fontSize = 12.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditBioDialog) {
        AlertDialog(
            onDismissRequest = { showEditBioDialog = false },
            title = { Text("🚀 EDIT MISSION BIO", fontSize = 15.sp, color = GlowNeonTeal, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = draftBio,
                    onValueChange = { draftBio = it },
                    label = { Text("Your Space Biosphere Tagline") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GlowNeonTeal,
                        unfocusedBorderColor = DarkBorderColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.userBio.value = draftBio
                        showEditBioDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GlowNeonTeal, contentColor = CosmicDarkBackground)
                ) {
                    Text("SAVE BIO", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditBioDialog = false }) {
                    Text("CANCEL", color = SoftGreyText)
                }
            },
            containerColor = InsetCardDark
        )
    }
}
