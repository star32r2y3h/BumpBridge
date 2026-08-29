package com.example.bumpbridge

import android.content.Context
import android.content.Intent
import android.net.Uri

object PlaybackTrigger {
    /** Opens the given Spotify track URI in the Spotify app, starting playback there. */
    fun play(context: Context, spotifyUri: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(spotifyUri)).apply {
            setPackage(Config.SPOTIFY_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Spotify isn't installed, or the URI wasn't handled.
        }
    }
}
