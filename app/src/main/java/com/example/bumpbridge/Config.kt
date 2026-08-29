package com.example.bumpbridge

object Config {
    // TODO: fill in with your own values from https://developer.spotify.com/dashboard
    const val SPOTIFY_CLIENT_ID = "YOUR_SPOTIFY_CLIENT_ID"
    const val REDIRECT_URI = "bumpbridge://callback"

    // Verify this against the real Metrolist package name on your device:
    // Settings > Apps > Metrolist > Advanced, or `adb shell pm list packages | grep metrolist`.
    // This is the common package id for the open-source Metrolist YouTube Music client,
    // but confirm it before relying on it.
    const val METROLIST_PACKAGE = "com.metrolist.music"

    const val SPOTIFY_PACKAGE = "com.spotify.music"

    // After triggering playback on Spotify, the service pauses it again after this delay,
    // to shorten the audio overlap with Metrolist. Set to 0 to disable auto-pause.
    // See README "The audio overlap trade-off" before changing this.
    const val AUTO_PAUSE_AFTER_MS = 2500L
}
