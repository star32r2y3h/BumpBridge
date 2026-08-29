package com.example.bumpbridge

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var authManager: SpotifyAuthManager
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        authManager = SpotifyAuthManager(applicationContext)
        statusText = findViewById(R.id.statusText)

        findViewById<Button>(R.id.connectSpotifyButton).setOnClickListener {
            val url = authManager.buildAuthUrl()
            CustomTabsIntent.Builder().build().launchUrl(this, Uri.parse(url))
        }

        findViewById<Button>(R.id.notificationAccessButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        handleIntent(intent)
        updateStatus()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "bumpbridge" && data.host == "callback") {
            val code = data.getQueryParameter("code")
            val error = data.getQueryParameter("error")
            when {
                error != null -> Toast.makeText(this, "Spotify login failed: $error", Toast.LENGTH_LONG).show()
                code != null -> lifecycleScope.launch {
                    val success = withContext(Dispatchers.IO) { authManager.exchangeCodeForToken(code) }
                    Toast.makeText(
                        this@MainActivity,
                        if (success) "Connected to Spotify" else "Login failed, try again",
                        Toast.LENGTH_LONG
                    ).show()
                    updateStatus()
                }
            }
        }
    }

    private fun updateStatus() {
        statusText.text = if (authManager.isLoggedIn()) {
            "Spotify: connected\n\nNow tap \"Grant Notification Access\" below, enable BumpBridge, " +
                "then open Metrolist and play something."
        } else {
            "Spotify: not connected"
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }
}
