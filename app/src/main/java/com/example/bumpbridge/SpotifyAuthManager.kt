package com.example.bumpbridge

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/**
 * Handles Spotify's Authorization Code + PKCE flow, which works with a free Spotify account
 * (no Premium required for login or search -- only Spotify's remote playback-control API
 * requires Premium, and this app deliberately avoids that API).
 *
 * Tokens are stored in plain SharedPreferences for simplicity. For anything beyond personal,
 * sideloaded use, switch this to androidx.security's EncryptedSharedPreferences.
 */
class SpotifyAuthManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("bumpbridge_auth", Context.MODE_PRIVATE)
    private val client = OkHttpClient()

    var codeVerifier: String? = null
        private set

    val accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)

    private val refreshToken: String?
        get() = prefs.getString(KEY_REFRESH_TOKEN, null)

    private var expiresAtMillis: Long
        get() = prefs.getLong(KEY_EXPIRES_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_EXPIRES_AT, value).apply()

    fun isLoggedIn(): Boolean = !accessToken.isNullOrEmpty()

    fun buildAuthUrl(): String {
        val verifier = PkceUtil.generateCodeVerifier()
        codeVerifier = verifier
        val challenge = PkceUtil.generateCodeChallenge(verifier)

        return Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("client_id", Config.SPOTIFY_CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", Config.REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("scope", "user-read-private")
            .build()
            .toString()
    }

    /** Exchanges an auth code for tokens. Blocking network call -- run off the main thread. */
    fun exchangeCodeForToken(code: String): Boolean {
        val verifier = codeVerifier ?: return false
        val body = FormBody.Builder()
            .add("client_id", Config.SPOTIFY_CLIENT_ID)
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", Config.REDIRECT_URI)
            .add("code_verifier", verifier)
            .build()
        return runTokenRequest(body)
    }

    /** Refreshes the access token using the stored refresh token. Blocking -- run off the main thread. */
    fun refreshAccessToken(): Boolean {
        val refresh = refreshToken ?: return false
        val body = FormBody.Builder()
            .add("client_id", Config.SPOTIFY_CLIENT_ID)
            .add("grant_type", "refresh_token")
            .add("refresh_token", refresh)
            .build()
        return runTokenRequest(body)
    }

    /** Returns a valid access token, refreshing first if the current one has expired. Blocking. */
    fun getValidAccessToken(): String? {
        val current = accessToken
        if (current != null && System.currentTimeMillis() < expiresAtMillis - 30_000) {
            return current
        }
        return if (refreshAccessToken()) accessToken else null
    }

    private fun runTokenRequest(body: FormBody): Boolean {
        val request = Request.Builder()
            .url("https://accounts.spotify.com/api/token")
            .post(body)
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: return false
                if (!response.isSuccessful) return false
                val json = JSONObject(responseBody)
                val editor = prefs.edit()
                editor.putString(KEY_ACCESS_TOKEN, json.getString("access_token"))
                if (json.has("refresh_token")) {
                    editor.putString(KEY_REFRESH_TOKEN, json.getString("refresh_token"))
                }
                val expiresIn = json.optLong("expires_in", 3600L)
                editor.putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + expiresIn * 1000)
                editor.apply()
                true
            }
        } catch (e: IOException) {
            false
        }
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
    }
}
