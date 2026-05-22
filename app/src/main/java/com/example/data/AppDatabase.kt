package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// --- Room Entities ---

@Entity(tableName = "workout_sessions")
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,
    val durationSeconds: Int,
    val calories: Float,
    val avgHeartRate: Float,
    val avgCadence: Float,
    val distanceKm: Float,
    val workoutType: String,
    val coachFeedback: String
)

@Entity(tableName = "social_posts")
data class SocialPost(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userName: String,
    val userAvatar: String,
    val postText: String,
    val postImage: String, // Base64 or local description/uri
    val timestamp: Long,
    val likes: Int,
    val loves: Int,
    val hasLiked: Boolean = false,
    val hasLoved: Boolean = false,
    val commentsJson: String = "[]", // JSON string representing a clean array of comments
    val postVideo: String = "",
    val attachedFeeling: String = "",
    val externalUrl: String = ""
)

@Entity(tableName = "forum_messages")
data class DiscussionMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val roomType: String,
    val userName: String,
    val messageText: String,
    val timestamp: Long,
    val isVoice: Boolean = false
)

@Entity(tableName = "direct_messages")
data class DirectMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val friendName: String,
    val messageText: String,
    val timestamp: Long,
    val isFromUser: Boolean,
    val isVoice: Boolean = false,
    val voiceDurationSec: Int = 0,
    val isCallLog: Boolean = false,
    val callDurationSec: Int = 0,
    val isCallVideo: Boolean = false
)

@Entity(tableName = "progress_weights")
data class WeightRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,
    val weightLb: Float,
    val muscleMassLb: Float
)

@Entity(tableName = "progress_photos")
data class UserProgressPhoto(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val photoUri: String,
    val date: String,
    val notes: String
)

@Entity(tableName = "badge_achievements")
data class BadgeAchievement(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val iconName: String,
    val unlockedAt: Long? = null,
    val isUnlocked: Boolean = false
)

@Entity(tableName = "companion_state")
data class CompanionRelationshipState(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userStrengths: String = "High determination",
    val userWeaknesses: String = "Skips morning sessions",
    val excusesLevel: Int = 5,
    val relationshipPoints: Int = 10,
    val coachStyle: String = "Comedic Encourager",
    val conversationMemorySummary: String = "Just starting our fitness journey!"
)

// --- Room DAOs ---

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workout_sessions ORDER BY date DESC")
    fun getAllSessions(): Flow<List<WorkoutSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: WorkoutSession)
}

@Dao
interface SocialDao {
    @Query("SELECT * FROM social_posts ORDER BY timestamp DESC")
    fun getAllPosts(): Flow<List<SocialPost>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPost(post: SocialPost)

    @Update
    suspend fun updatePost(post: SocialPost)
}

@Dao
interface ForumDao {
    @Query("SELECT * FROM forum_messages WHERE roomType = :roomType ORDER BY timestamp ASC")
    fun getMessagesByRoom(roomType: String): Flow<List<DiscussionMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(msg: DiscussionMessage)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM direct_messages WHERE friendName = :friendName ORDER BY timestamp ASC")
    fun getDirectMessages(friendName: String): Flow<List<DirectMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDirectMessage(msg: DirectMessage)
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress_weights ORDER BY date ASC")
    fun getWeightRecords(): Flow<List<WeightRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeight(record: WeightRecord)

    @Query("SELECT * FROM progress_photos ORDER BY date DESC")
    fun getPhotos(): Flow<List<UserProgressPhoto>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: UserProgressPhoto)
}

@Dao
interface AchievementDao {
    @Query("SELECT * FROM badge_achievements ORDER BY isUnlocked DESC, id ASC")
    fun getAllBadges(): Flow<List<BadgeAchievement>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBadge(badge: BadgeAchievement)

    @Update
    suspend fun updateBadge(badge: BadgeAchievement)
}

@Dao
interface CompanionDao {
    @Query("SELECT * FROM companion_state LIMIT 1")
    suspend fun getCompanionState(): CompanionRelationshipState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompanionState(state: CompanionRelationshipState)
}

// --- App Database Configuration ---

@Database(
    entities = [
        WorkoutSession::class,
        SocialPost::class,
        DiscussionMessage::class,
        DirectMessage::class,
        WeightRecord::class,
        UserProgressPhoto::class,
        BadgeAchievement::class,
        CompanionRelationshipState::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun socialDao(): SocialDao
    abstract fun forumDao(): ForumDao
    abstract fun chatDao(): ChatDao
    abstract fun progressDao(): ProgressDao
    abstract fun achievementDao(): AchievementDao
    abstract fun companionDao(): CompanionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aura_elliptical_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
