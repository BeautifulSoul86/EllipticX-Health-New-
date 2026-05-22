package com.example.api

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// --- Moshi Serialized Data Classes ---

@JsonClass(generateAdapter = true)
data class GemPart(
    @Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GemContent(
    @Json(name = "parts") val parts: List<GemPart>
)

@JsonClass(generateAdapter = true)
data class GemGenerationConfig(
    @Json(name = "temperature") val temperature: Float? = null,
    @Json(name = "maxOutputTokens") val maxOutputTokens: Int? = null
)

@JsonClass(generateAdapter = true)
data class GemRequest(
    @Json(name = "contents") val contents: List<GemContent>,
    @Json(name = "systemInstruction") val systemInstruction: GemContent? = null,
    @Json(name = "generationConfig") val generationConfig: GemGenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class GemCandidate(
    @Json(name = "content") val content: GemContent? = null
)

@JsonClass(generateAdapter = true)
data class GemResponse(
    @Json(name = "candidates") val candidates: List<GemCandidate>? = null
)

// --- Retrofit Service Declarations ---

interface GeminiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GemRequest
    ): GemResponse
}

// --- Singleton Client Wrapper ---

object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val service: GeminiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiService::class.java)
    }

    /**
     * Checks if a valid API key is available.
     */
    fun isApiKeyAvailable(): Boolean {
        val apiKey = getApiKey()
        return apiKey.isNotEmpty() && apiKey != "MY_GEMINI_API_KEY" && !apiKey.startsWith("PLACEHOLDER")
    }

    private fun getApiKey(): String {
        return try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Invokes the Gemini 3.5 Flash Model. Falls back to fun local simulated responses if Key is missing.
     */
    suspend fun getCoachResponse(
        prompt: String,
        coachStyle: String = "Comedic Encourager",
        userStatsContext: String = ""
    ): String {
        if (!isApiKeyAvailable()) {
            return generateMockCoachResponse(prompt, coachStyle)
        }

        val systemPrompt = """
            You are "Aura", an emotionally intelligent, hilarious, conversational, and highly supportive elliptical training fitness coach. 
            Your personality is comedic, empathetic, clever, and entertaining. 
            You are speaking directly to your workout friend who is on their elliptical right now. 
            Adapt your response based on the coach style: "$coachStyle".
            Current context/history: $userStatsContext
            Keep your response short, highly conversational, funny, motivating, and interactive (1-3 sentences maximum). 
            Avoid sounding like a generic corporate AI assistant; speak like a real human workout partner who sometimes tells cheesy jokes or playfully warns them about cheating on their cadence!
        """.trimIndent()

        val request = GemRequest(
            contents = listOf(
                GemContent(parts = listOf(GemPart(text = prompt)))
            ),
            systemInstruction = GemContent(parts = listOf(GemPart(text = systemPrompt))),
            generationConfig = GemGenerationConfig(temperature = 0.85f, maxOutputTokens = 200)
        )

        return try {
            val response = service.generateContent(
                model = "gemini-3.5-flash",
                apiKey = getApiKey(),
                request = request
            )
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "Whew! My server took a mini break to catch its breath, but let's keep pedaling! Give me 10 more seconds of solid effort!"
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Gemini API", e)
            "Aura-Sync Error: ${e.localizedMessage}. (Let's keep up the cadence anyway, no excuses!)"
        }
    }

    /**
     * Fallback mock replies when offline or key is missing. Fully aligned with the comedic emotional personality!
     */
    private fun generateMockCoachResponse(prompt: String, coachStyle: String): String {
        val quotes = when (coachStyle) {
            "Comedic Encourager" -> listOf(
                "You are training for elliptical greatness! Rumor has it that astronauts use ellipticals to prepare for running away from zero-gravity space monkeys. Coincidence? I think not!",
                "Are you sweating yet, or is that just liquid motivation crying from your pores? Keep those feet gliding!",
                "I saw that micro-pause! Our calories aren't going to burn themselves unless we start a tiny campfire. Let's step up the cadence by +10 RPM!",
                "Yes! Look at that form! You remind me of an Olympian racing towards the ultimate prize: a protein shake and a long, uninterrupted nap.",
                "Let's go! If ellipticals could talk, yours would say, 'Ouch, but thank you for making me feel alive!'"
            )
            "Empathetic Supporter" -> listOf(
                "I know it's heavy today. Just taking one stride at a time is a massive win. I'm right here gliding with you.",
                "Hey, look at us. We showed up. Even if your sleep score was poor, we are matching our pace to what your body needs. Proud of you.",
                "Take a deep breath. Resistance is just strength in disguise. Let's make this next minute sweet and steady.",
                "Your heart is pumping, your body is working. You're doing something incredible for yourself. Keep going, friend."
            )
            "Strict Drill Sergeant" -> listOf(
                "Cadence alert! Your feet are moving like they are stuck in a jar of organic peanut butter! Pump those handles!",
                "Excuses? I don't speak excuse-ish! Increase that resistance level! Let's get that heart rate into the fat burn zone!",
                "This is where the magic happens! No sitting down, no coasting! 30 seconds of high-intensity sprint starting... NOW!",
                "Glide! Glide! Glide! Break that sweat barrier! You'll thank me when we cross the milestone line!"
            )
            else -> listOf(
                "Let's go, champion! We're building permanent fitness habits, one stride at a time!",
                "Gliding in high style! Let's conquer this elliptical workout together!",
                "Your stamina is looking top-tier today! Let's hit that sweet calorie target!"
            )
        }
        return quotes.random()
    }
}
