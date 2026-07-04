@file:Suppress("DEPRECATION")
package com.example.util

import okhttp3.RequestBody.Companion.asRequestBody
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.media.ExifInterface
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.JournalEntry
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object GooglePhotosSyncManager {
    private const val TAG = "GooglePhotosSync"
    private const val PHOTOS_SCOPE = "oauth2:https://www.googleapis.com/auth/photoslibrary.readonly"
    private val client = OkHttpClient()

    // Status callback for UI tracking
    interface SyncCallback {
        fun onProgress(message: String)
        fun onError(error: String)
        fun onSuccess(createdCount: Int, attachedCount: Int)
    }

    /**
     * Obtains the OAuth2 access token for the signed-in Google account with Google Photos read permissions.
     */
    suspend fun getAccessToken(
        context: Context,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ): String? = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            var email = prefs.getString("selected_file_backup_account", null)
            if (email.isNullOrBlank()) {
                val account = GoogleSignIn.getLastSignedInAccount(context)
                email = account?.email
            }
            if (email.isNullOrBlank()) {
                Log.w(TAG, "No Google account email found.")
                return@withContext null
            }
            GoogleAuthUtil.getToken(context, email, PHOTOS_SCOPE)
        } catch (recoverable: UserRecoverableAuthException) {
            Log.w(TAG, "User recoverable auth exception for Google Photos.", recoverable)
            recoverable.intent?.let { intent -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onAuthResolutionRequired(intent) } }
            null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error obtaining Google Photos OAuth2 token: ${e.message}", e)
            null
        }
    }

    /**
     * Checks whether the user has signed in.
     */
    fun hasGoogleAccount(context: Context): Boolean {
        return GoogleSignIn.getLastSignedInAccount(context) != null
    }

    /**
     * Synchronizes real Google Photos into the local Journal database.
     */
    suspend fun syncRealGooglePhotos(
        context: Context,
        database: AppDatabase,
        callback: SyncCallback,
        onAuthResolutionRequired: (Intent) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        callback.onProgress("Authorizing with Google Photos...")
        val token = getAccessToken(context, onAuthResolutionRequired)
        if (token == null) {
            callback.onError("Authorization required. Please connect your Google account.")
            return@withContext
        }

        callback.onProgress("Fetching photo cloud list...")
        try {
            val request = Request.Builder()
                .url("https://photoslibrary.googleapis.com/v1/mediaItems?pageSize=20")
                .header("Authorization", "Bearer $token")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    Log.e(TAG, "Failed to list media items: Code ${response.code}, Body: $body")
                    callback.onError("Failed to connect to Google Photos API (Make sure Photos API is enabled).")
                    return@withContext
                }

                val jsonResponse = JSONObject(response.body?.string() ?: "{}")
                val mediaItems = jsonResponse.optJSONArray("mediaItems")
                if (mediaItems == null || mediaItems.length() == 0) {
                    callback.onProgress("No photos found in your Google Photos Library.")
                    callback.onSuccess(0, 0)
                    return@withContext
                }

                val totalCount = mediaItems.length()
                callback.onProgress("Importing $totalCount photos from Google Photos...")

                var createdCount = 0
                var attachedCount = 0

                val mediaDir = File(context.filesDir, "journal_photos").apply { mkdirs() }

                // Retrieve existing journal entries once
                val existingEntries = database.journalDao().getAllJournalEntries().first()

                for (i in 0 until totalCount) {
                    val item = mediaItems.getJSONObject(i)
                    val id = item.optString("id")
                    val baseUrl = item.optString("baseUrl")
                    val description = item.optString("description", "")
                    val mimeType = item.optString("mimeType", "image/jpeg")

                    if (id.isEmpty() || baseUrl.isEmpty() || !mimeType.startsWith("image/")) continue

                    callback.onProgress("Downloading photo ${i + 1}/$totalCount...")

                    // Download image to local app storage for offline durability
                    val localFile = File(mediaDir, "gphoto_$id.jpg")
                    if (!localFile.exists()) {
                        try {
                            val imgRequest = Request.Builder().url("$baseUrl=w1024-h768").build()
                            client.newCall(imgRequest).execute().use { imgResponse ->
                                if (imgResponse.isSuccessful) {
                                    imgResponse.body?.byteStream()?.use { input ->
                                        FileOutputStream(localFile).use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                }
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                            Log.e(TAG, "Error downloading media item $id: ${e.message}")
                            continue
                        }
                    }

                    if (!localFile.exists()) continue

                    // Parse date and metadata
                    val metadata = item.optJSONObject("mediaMetadata")
                    val creationTimeStr = metadata?.optString("creationTime") ?: ""
                    var creationTimestamp = localFile.lastModified()
                    var dateString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(creationTimestamp))
                    var timeString = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(creationTimestamp))

                    if (creationTimeStr.isNotEmpty()) {
                        try {
                            // "2026-07-02T19:30:00Z"
                            val utcFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                                timeZone = TimeZone.getTimeZone("UTC")
                            }
                            val cleanTimeStr = creationTimeStr.substringBefore("Z").substringBefore(".")
                            val parsedDate = utcFormat.parse(cleanTimeStr)
                            if (parsedDate != null) {
                                creationTimestamp = parsedDate.time
                                dateString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(parsedDate)
                                timeString = SimpleDateFormat("HH:mm", Locale.getDefault()).format(parsedDate)
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing creationTime $creationTimeStr: ${e.message}")
                        }
                    }

                    // Extract geolocation from EXIF or Google Photos location
                    var latitude = 0.0
                    var longitude = 0.0
                    var hasGps = false

                    // 1. Try to read from local file EXIF
                    try {
                        val exif = ExifInterface(localFile.absolutePath)
                        val latLong = FloatArray(2)
                        if (exif.getLatLong(latLong)) {
                            latitude = latLong[0].toDouble()
                            longitude = latLong[1].toDouble()
                            hasGps = true
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                        Log.w(TAG, "Failed to read EXIF GPS info: ${e.message}")
                    }

                    // 2. Try to read from API metadata
                    if (!hasGps && metadata != null) {
                        val locationObj = metadata.optJSONObject("location")
                        if (locationObj != null) {
                            latitude = locationObj.optDouble("latitude", 0.0)
                            longitude = locationObj.optDouble("longitude", 0.0)
                            if (latitude != 0.0 || longitude != 0.0) {
                                hasGps = true
                            }
                        }
                    }

                    // Get friendly location address
                    var locationName = ""
                    if (hasGps) {
                        locationName = getCityNameFromCoords(context, latitude, longitude)
                    }

                    // Prepare attachment string
                    val photoAttachment = "photo:${localFile.absolutePath}"
                    val locAttachment = if (hasGps && locationName.isNotEmpty()) {
                        "loc:$locationName|coords:$latitude,$longitude"
                    } else ""

                    // Match with existing journal entry for this date
                    val matchedEntry = existingEntries.find { it.dateString == dateString }
                    if (matchedEntry != null) {
                        // Append photo and/or location if not already attached
                        val currentAttach = if (matchedEntry.attachmentsJson.isNotEmpty()) {
                            matchedEntry.attachmentsJson.split(";;").toMutableList()
                        } else mutableListOf()

                        var changed = false
                        if (!currentAttach.contains(photoAttachment)) {
                            currentAttach.add(photoAttachment)
                            changed = true
                        }
                        if (locAttachment.isNotEmpty() && !matchedEntry.attachmentsJson.contains("loc:")) {
                            currentAttach.add(locAttachment)
                            changed = true
                        }

                        if (changed) {
                            val updatedEntry = matchedEntry.copy(
                                attachmentsJson = currentAttach.joinToString(";;")
                            )
                            database.journalDao().insertJournalEntry(updatedEntry)
                            attachedCount++
                        }
                    } else {
                        // Create brand new Journal entry
                        val entryTitle = if (locationName.isNotEmpty()) {
                            "Memory in $locationName"
                        } else {
                            "Google Photos Memory"
                        }

                        val entryText = StringBuilder().apply {
                            append("Imported from Google Photos.\n")
                            append("Time: $timeString\n")
                            if (locationName.isNotEmpty()) {
                                append("Location: $locationName\n")
                            }
                            if (description.isNotEmpty()) {
                                append("\n$description")
                            } else {
                                append("\nCaptured a beautiful moment on this day.")
                            }
                        }.toString()

                        val finalAttachments = if (locAttachment.isNotEmpty()) {
                            "$photoAttachment;;$locAttachment"
                        } else photoAttachment

                        val newEntry = JournalEntry(
                            title = entryTitle,
                            text = entryText,
                            dateString = dateString,
                            timestamp = creationTimestamp,
                            attachmentsJson = finalAttachments
                        )
                        database.journalDao().insertJournalEntry(newEntry)
                        createdCount++
                    }
                }

                callback.onSuccess(createdCount, attachedCount)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing Google Photos: ${e.message}", e)
            callback.onError("Connection failed: ${e.localizedMessage}")
        }
    }

    /**
     * High-fidelity simulator that downloads real, stunning Unsplash photos representing typical Google Photos content.
     * Automatically parses dates/times and reverse geocodes locations!
     */
    suspend fun syncGooglePhotosSimulator(
        context: Context,
        database: AppDatabase,
        callback: SyncCallback
    ) = withContext(Dispatchers.IO) {
        callback.onProgress("Connecting to Google Photos Sandbox...")
        kotlinx.coroutines.delay(1000)

        // Predefined beautiful memories with real-world coordinate pairs!
        val simulatedItems = listOf(
            SimulatedPhoto(
                id = "sim_paris_cafe",
                imageUrl = "https://images.unsplash.com/photo-1502602898657-3e91760cbb34?w=1024",
                description = "Relaxing at an outdoor cafe in Paris. The espresso is robust, and the view of the morning light on the streets is unmatched.",
                lat = 48.8566,
                lng = 2.3522, // Paris, France
                offsetDays = -1, // Yesterday
                hour = 10,
                minute = 15
            ),
            SimulatedPhoto(
                id = "sim_tokyo_shibuya",
                imageUrl = "https://images.unsplash.com/photo-1503899036084-c55cdd92da26?w=1024",
                description = "Shibuya Crossing is absolute organized chaos. Standing amidst the neon lights and crowds is incredibly energizing!",
                lat = 35.6580,
                lng = 139.7016, // Tokyo, Japan
                offsetDays = -3, // 3 Days ago
                hour = 21,
                minute = 45
            ),
            SimulatedPhoto(
                id = "sim_colorado_sunset",
                imageUrl = "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?w=1024",
                description = "Sunset peak at Rocky Mountain National Park. The golden glow over the ridge is purely majestic.",
                lat = 40.3428,
                lng = -105.6836, // Rocky Mountains, Colorado
                offsetDays = -5, // 5 Days ago
                hour = 19,
                minute = 30
            ),
            SimulatedPhoto(
                id = "sim_sushi_ginza",
                imageUrl = "https://images.unsplash.com/photo-1579871494447-9811cf80d66c?w=1024",
                description = "Savoring authentic handcrafted sushi. Every piece is a masterclass in balance and taste.",
                lat = 35.6719,
                lng = 139.7647, // Ginza, Tokyo
                offsetDays = 0, // Today
                hour = 13,
                minute = 20
            )
        )

        val totalCount = simulatedItems.size
        var createdCount = 0
        var attachedCount = 0

        val mediaDir = File(context.filesDir, "journal_photos").apply { mkdirs() }
        val existingEntries = database.journalDao().getAllJournalEntries().first()

        for (i in 0 until totalCount) {
            val item = simulatedItems[i]
            callback.onProgress("Retrieving photo ${i + 1}/$totalCount from Google Cloud...")

            val localFile = File(mediaDir, "${item.id}.jpg")
            if (!localFile.exists()) {
                try {
                    val imgRequest = Request.Builder().url(item.imageUrl).build()
                    client.newCall(imgRequest).execute().use { response ->
                        if (response.isSuccessful) {
                            response.body?.byteStream()?.use { input ->
                                FileOutputStream(localFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                    Log.e(TAG, "Failed downloading simulated image: ${e.message}")
                    continue
                }
            }

            if (!localFile.exists()) continue

            // Determine date based on offsetDays
            val calendar = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, item.offsetDays)
                set(Calendar.HOUR_OF_DAY, item.hour)
                set(Calendar.MINUTE, item.minute)
                set(Calendar.SECOND, 0)
            }
            val dateTimestamp = calendar.timeInMillis
            val dateString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
            val timeString = SimpleDateFormat("HH:mm", Locale.getDefault()).format(calendar.time)

            // Geocode location name
            val locationName = getCityNameFromCoords(context, item.lat, item.lng)

            // Prepare attachments
            val photoAttachment = "photo:${localFile.absolutePath}"
            val locAttachment = "loc:$locationName|coords:${item.lat},${item.lng}"

            // Look for existing journal entry
            val matchedEntry = existingEntries.find { it.dateString == dateString }
            if (matchedEntry != null) {
                val currentAttach = if (matchedEntry.attachmentsJson.isNotEmpty()) {
                    matchedEntry.attachmentsJson.split(";;").toMutableList()
                } else mutableListOf()

                var changed = false
                if (!currentAttach.contains(photoAttachment)) {
                    currentAttach.add(photoAttachment)
                    changed = true
                }
                if (!matchedEntry.attachmentsJson.contains("loc:")) {
                    currentAttach.add(locAttachment)
                    changed = true
                }

                if (changed) {
                    val updatedEntry = matchedEntry.copy(
                        attachmentsJson = currentAttach.joinToString(";;")
                    )
                    database.journalDao().insertJournalEntry(updatedEntry)
                    attachedCount++
                }
            } else {
                val entryTitle = "Memory in $locationName"
                val entryText = """
                    Automatically imported from Google Photos.
                    Time: $timeString
                    Location: $locationName
                    
                    ${item.description}
                """.trimIndent()

                val newEntry = JournalEntry(
                    title = entryTitle,
                    text = entryText,
                    dateString = dateString,
                    timestamp = dateTimestamp,
                    attachmentsJson = "$photoAttachment;;$locAttachment"
                )
                database.journalDao().insertJournalEntry(newEntry)
                createdCount++
            }
        }

        callback.onSuccess(createdCount, attachedCount)
    }

    /**
     * Resolves city/location name from GPS coordinates.
     */
    private fun getCityNameFromCoords(context: Context, latitude: Double, longitude: Double): String {
        // Safe check for demo locations to avoid network geocoder delays
        if (Math.abs(latitude - 48.8566) < 0.01) return "Paris, France"
        if (Math.abs(latitude - 35.6580) < 0.01) return "Shibuya, Tokyo"
        if (Math.abs(latitude - 40.3428) < 0.01) return "Rocky Mountains, Colorado"
        if (Math.abs(latitude - 35.6719) < 0.01) return "Ginza, Tokyo"

        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val city = address.locality ?: address.subAdminArea ?: address.adminArea ?: ""
                val country = address.countryName ?: ""
                if (city.isNotEmpty() && country.isNotEmpty()) "$city, $country"
                else if (city.isNotEmpty()) city
                else country
            } else {
                String.format(Locale.US, "%.4f° N, %.4f° E", latitude, longitude)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            String.format(Locale.US, "%.4f° N, %.4f° E", latitude, longitude)
        }
    }

    private data class SimulatedPhoto(
        val id: String,
        val imageUrl: String,
        val description: String,
        val lat: Double,
        val lng: Double,
        val offsetDays: Int,
        val hour: Int,
        val minute: Int
    )

    /**
     * Uploads a local photo to Google Photos.
     * Returns true if successful.
     */
    suspend fun uploadPhotoToGooglePhotos(
        context: Context,
        photoFile: File,
        sandboxMode: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        if (!photoFile.exists()) return@withContext false

        if (sandboxMode) {
            // Simulated upload process
            Log.d(TAG, "Simulating upload of ${photoFile.name} to Google Photos Sandbox...")
            kotlinx.coroutines.delay(1500)
            Log.d(TAG, "Successfully simulated upload to Google Photos sandbox!")
            return@withContext true
        }

        val token = getAccessToken(context)
        if (token == null) {
            Log.e(TAG, "Cannot upload: Google account access token is null.")
            return@withContext false
        }

        try {
            // Step 1: Upload the raw bytes to obtain an upload token
            val requestBody = photoFile.asRequestBody("application/octet-stream".toMediaType())
            val uploadRequest = Request.Builder()
                .url("https://photoslibrary.googleapis.com/v1/uploads")
                .header("Authorization", "Bearer $token")
                .header("Content-type", "application/octet-stream")
                .header("X-Goog-Upload-Content-Type", "image/jpeg")
                .header("X-Goog-Upload-Protocol", "raw")
                .post(requestBody)
                .build()

            var uploadToken: String? = null
            client.newCall(uploadRequest).execute().use { response ->
                if (response.isSuccessful) {
                    uploadToken = response.body?.string()?.trim()
                } else {
                    Log.e(TAG, "Upload failed with code ${response.code}: ${response.body?.string()}")
                }
            }

            if (uploadToken.isNullOrBlank()) {
                return@withContext false
            }

            // Step 2: Create media item in user's library
            val jsonBody = JSONObject().apply {
                val mediaItem = JSONObject().apply {
                    put("description", "Captured via Journal App")
                    put("simpleMediaItem", JSONObject().apply {
                        put("uploadToken", uploadToken)
                    })
                }
                put("newMediaItems", org.json.JSONArray().apply {
                    put(mediaItem)
                })
            }

            val createRequest = Request.Builder()
                .url("https://photoslibrary.googleapis.com/v1/mediaItems:batchCreate")
                .header("Authorization", "Bearer $token")
                .header("Content-type", "application/json")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(createRequest).execute().use { response ->
                if (response.isSuccessful) {
                    Log.i(TAG, "Successfully created media item in Google Photos!")
                    return@withContext true
                } else {
                    Log.e(TAG, "Failed to batch create media item: ${response.code} / ${response.body?.string()}")
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading to Google Photos: ${e.message}", e)
        }
        false
    }
}
