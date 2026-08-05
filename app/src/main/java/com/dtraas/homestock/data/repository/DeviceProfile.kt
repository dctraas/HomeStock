package com.dtraas.homestock.data.repository

import android.content.Context
import android.net.Uri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * An optional, per-device display name (e.g. "Mama", "Jip") and profile photo
 * used to attribute activity log entries to a person. Purely local — not an
 * account, not shared via Firestore beyond being stamped onto the entries
 * this device writes.
 */
class DeviceProfile(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _displayName = MutableStateFlow(prefs.getString(KEY_DISPLAY_NAME, null))
    val displayName: StateFlow<String?> = _displayName

    private val _photoPath = MutableStateFlow(
        prefs.getString(KEY_PHOTO_PATH, null)?.takeIf { File(it).exists() },
    )
    val photoPath: StateFlow<String?> = _photoPath

    fun setDisplayName(name: String?) {
        val normalized = name?.trim()?.takeIf { it.isNotEmpty() }
        prefs.edit().putString(KEY_DISPLAY_NAME, normalized).apply()
        _displayName.value = normalized
    }

    /** Copies the picked image into app-private storage, since Photo Picker URIs aren't durable across restarts. */
    suspend fun setPhotoFromUri(sourceUri: Uri) {
        withContext(Dispatchers.IO) {
            val destination = File(appContext.filesDir, PHOTO_FILE_NAME)
            appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
            prefs.edit().putString(KEY_PHOTO_PATH, destination.absolutePath).apply()
            _photoPath.value = destination.absolutePath
        }
    }

    suspend fun clearPhoto() {
        withContext(Dispatchers.IO) {
            _photoPath.value?.let { File(it).delete() }
            prefs.edit().remove(KEY_PHOTO_PATH).apply()
            _photoPath.value = null
        }
    }

    private companion object {
        const val PREFS_NAME = "device_profile"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_PHOTO_PATH = "photo_path"
        const val PHOTO_FILE_NAME = "profile_photo.jpg"
    }
}
