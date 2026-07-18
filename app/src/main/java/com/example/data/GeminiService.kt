package com.example.data

import android.util.Log
import com.ditchloopy.BuildConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GeminiPart(val text: String)

@JsonClass(generateAdapter = true)
data class GeminiContent(val parts: List<GeminiPart>)

@JsonClass(generateAdapter = true)
data class GeminiRequest(val contents: List<GeminiContent>)

@JsonClass(generateAdapter = true)
data class GeminiResponse(val candidates: List<GeminiCandidate>?)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(val content: GeminiContent?)

object GeminiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val requestAdapter = moshi.adapter(GeminiRequest::class.java)
    private val responseAdapter = moshi.adapter(GeminiResponse::class.java)

    /**
     * Checks if the Gemini API Key is available and non-default.
     */
    fun isApiKeyAvailable(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotEmpty() && key != "MY_GEMINI_API_KEY" && !key.contains("PLACEHOLDER")
    }

    /**
     * Generates a personalized DitchLoopy Invitation using Gemini based on user mood, preferences, and local environment.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun generatePersonalizedInvitation(
        mood: String,
        indoorOutdoor: String,
        energyLevel: String,
        userContext: String
    ): Invitation? = withContext(Dispatchers.IO) {
        if (!isApiKeyAvailable()) {
            Log.w("GeminiService", "Gemini API key is not available. Using local curation.")
            return@withContext null
        }

        val prompt = """
            You are the "Mission Intelligence Engine" of the application DitchLoopy, designed to break daily autopilot loops.
            We need you to generate exactly ONE highly evocative, creative, and mindful experience (called an "Invitation") tailored to the user's current states:
            - Mood/Vibe: $mood
            - Preference: $indoorOutdoor
            - Energy Level: $energyLevel
            - Additional user thoughts: $userContext

            The experience should not feel like a chore or habit. It must focus on memory creation, presence, or exploration. Keep it calm, reassuring, and completely safe.

            Format the response strictly as a JSON object with the following fields, and do NOT wrap it in markdown code blocks:
            {
              "title": "A short, elegant, atmospheric title (e.g., 'The Sidewalk Symphony')",
              "description": "A single-paragraph, beautifully written, evocative description of what the user should do.",
              "category": "One of: 'Discover', 'Experience', 'Reflect', 'Grow'",
              "difficulty": "One of: 'Easy', 'Medium', 'Deep'",
              "duration": "A short duration (e.g., '10m', '30m', '1h')",
              "cost": "One of: 'Free', 'Low', 'Medium'"
            }
        """.trimIndent()

        val requestBodyObj = GeminiRequest(listOf(GeminiContent(listOf(GeminiPart(prompt)))))
        val requestJson = requestAdapter.toJson(requestBodyObj)

        val apiKey = BuildConfig.GEMINI_API_KEY
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(requestJson.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("GeminiService", "API call failed with code: ${response.code} - ${response.message}")
                    return@withContext null
                }
                val bodyStr = response.body?.string() ?: return@withContext null
                val geminiResp = responseAdapter.fromJson(bodyStr)
                val rawText = geminiResp?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: return@withContext null
                
                // Clean markdown backticks if returned
                val jsonString = rawText.trim()
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()

                // Parse the inner generated JSON
                val moshiInner = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                val mapAdapter = moshiInner.adapter(Map::class.java)
                val map = mapAdapter.fromJson(jsonString) as? Map<String, Any> ?: return@withContext null

                val title = (map["title"] as? String) ?: "An Unplanned Moment"
                val description = (map["description"] as? String) ?: "Step away from your screen for a brief period of quiet awareness."
                val category = (map["category"] as? String) ?: "Discover"
                val difficulty = (map["difficulty"] as? String) ?: "Easy"
                val duration = (map["duration"] as? String) ?: "15m"
                val cost = (map["cost"] as? String) ?: "Free"

                Invitation(
                    id = "custom_" + System.currentTimeMillis(),
                    title = title,
                    description = description,
                    category = category,
                    difficulty = difficulty,
                    duration = duration,
                    energyLevel = energyLevel,
                    indoorOutdoor = indoorOutdoor,
                    introvertScore = "Solo",
                    cost = cost,
                    isCompleted = false,
                    isSelectedForWeek = false,
                    dayOfWeek = 0,
                    isCustom = true
                )
            }
        } catch (e: Exception) {
            Log.e("GeminiService", "Error generating invitation: ${e.message}", e)
            null
        }
    }
}
