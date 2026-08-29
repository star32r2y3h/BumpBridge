package com.example.bumpbridge

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

object SpotifyApi {
    private val client = OkHttpClient()

    data class TrackResult(val uri: String, val name: String, val artist: String)

    /**
     * Searches Spotify's catalog for the best match. Blocking network call -- run off the
     * main thread. Returns null if nothing matched or the request failed.
     *
     * Note: Spotify's catalog and YouTube Music's catalog don't fully overlap -- remixes,
     * live versions, region-locked tracks, or YouTube-only uploads may not have a match.
     */
    fun searchTrack(accessToken: String, title: String, artist: String): TrackResult? {
        val query = URLEncoder.encode("track:$title artist:$artist", "UTF-8")
        val url = "https://api.spotify.com/v1/search?q=$query&type=track&limit=1"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: return null
                if (!response.isSuccessful) return null
                val json = JSONObject(bodyString)
                val items = json.getJSONObject("tracks").getJSONArray("items")
                if (items.length() == 0) return null
                val track = items.getJSONObject(0)
                val uri = track.getString("uri")
                val name = track.getString("name")
                val artistName = track.getJSONArray("artists").getJSONObject(0).getString("name")
                TrackResult(uri, name, artistName)
            }
        } catch (e: IOException) {
            null
        }
    }
}
