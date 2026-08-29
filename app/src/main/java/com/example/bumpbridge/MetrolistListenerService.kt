package com.example.bumpbridge

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Watches Metrolist's media session for track changes, finds the matching track on Spotify,
 * and triggers real playback there so Spotify-based "now listening" features (like Bump)
 * pick it up. Requires the user to grant this app Notification Access, since Android only
 * exposes other apps' MediaSessions to apps with that permission.
 */
class MetrolistListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var metrolistController: MediaController? = null
    private var spotifyController: MediaController? = null
    private var lastSyncedKey: String? = null

    private lateinit var authManager: SpotifyAuthManager

    private val metrolistCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            metadata ?: return
            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return
            val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
            handleNewTrack(title, artist)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            // Only react while Metrolist is actually playing, not paused or stopped.
            if (state?.state == PlaybackState.STATE_PLAYING) {
                metrolistController?.metadata?.let {
                    val title = it.getString(MediaMetadata.METADATA_KEY_TITLE)
                    val artist = it.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                    if (title != null) handleNewTrack(title, artist)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        authManager = SpotifyAuthManager(applicationContext)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        refreshSessions()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Media apps re-post their notification on track change -- a reliable trigger to
        // re-check active sessions even if a MediaController callback is missed.
        if (sbn?.packageName == Config.METROLIST_PACKAGE) {
            refreshSessions()
        }
    }

    private fun refreshSessions() {
        val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        val component = ComponentName(this, MetrolistListenerService::class.java)
        val sessions = try {
            manager.getActiveSessions(component)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification listener permission not granted yet", e)
            return
        }

        sessions.find { it.packageName == Config.METROLIST_PACKAGE }?.let { controller ->
            if (metrolistController?.sessionToken != controller.sessionToken) {
                metrolistController?.unregisterCallback(metrolistCallback)
                metrolistController = controller
                controller.registerCallback(metrolistCallback)
                controller.metadata?.let {
                    val title = it.getString(MediaMetadata.METADATA_KEY_TITLE)
                    val artist = it.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                    if (title != null) handleNewTrack(title, artist)
                }
            }
        }

        spotifyController = sessions.find { it.packageName == Config.SPOTIFY_PACKAGE }
    }

    private fun handleNewTrack(title: String, artist: String) {
        val key = "$title|$artist"
        if (key == lastSyncedKey) return
        lastSyncedKey = key

        serviceScope.launch {
            val token = authManager.getValidAccessToken()
            if (token == null) {
                Log.w(TAG, "Not logged into Spotify yet -- open the app to connect your account")
                return@launch
            }
            val match = SpotifyApi.searchTrack(token, title, artist)
            if (match == null) {
                Log.i(TAG, "No Spotify match for '$title' by '$artist'")
                return@launch
            }
            PlaybackTrigger.play(applicationContext, match.uri)

            if (Config.AUTO_PAUSE_AFTER_MS > 0) {
                delay(Config.AUTO_PAUSE_AFTER_MS)
                spotifyController?.transportControls?.pause()
            }
        }
    }

    override fun onDestroy() {
        metrolistController?.unregisterCallback(metrolistCallback)
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "BumpBridge"
    }
}
