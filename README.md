# BumpBridge

Detects what's playing in Metrolist and triggers the same track to actually play on your
Spotify account, so Spotify-linked "now listening" features (like Bump) pick it up — without
needing Spotify Premium.

## How it works

1. `MetrolistListenerService` (a `NotificationListenerService`) reads Metrolist's active media
   session to get the title/artist whenever a new track starts.
2. It searches Spotify's catalog for a matching track using Spotify's Web API (search works
   fine on a free account — only *remote playback control* requires Premium, which this app
   avoids entirely).
3. It opens the match with a `spotify:track:...` deep link, which starts real playback inside
   the Spotify app itself. Because it's genuinely playing through your own account, it shows up
   in Spotify-based listening-activity features normally.
4. Optionally (see below), it pauses Spotify again a couple of seconds later.

## Setup

1. **Create a Spotify app**: go to https://developer.spotify.com/dashboard, click "Create app".
   - Redirect URI: `bumpbridge://callback`
   - APIs used: Web API
2. Copy the **Client ID** into `Config.kt` (`SPOTIFY_CLIENT_ID`).
3. **Add yourself as a user**: Spotify apps in Development Mode only work for up to 25
   explicitly-added users. In the dashboard, under your app's settings → User Management, add
   the Spotify account email you'll log in with on the phone.
4. **Confirm Metrolist's package name** on your device (Settings → Apps → Metrolist →
   Advanced, or `adb shell pm list packages | grep metrolist`) and update
   `Config.METROLIST_PACKAGE` if it's different from `com.metrolist.music`.
5. Open the project in Android Studio, let Gradle sync, and run it on your device.
6. In the app: tap **Connect Spotify**, log in, then tap **Grant Notification Access** and
   enable BumpBridge in the system list.
7. Open Metrolist and play a song.

I wrote this by hand in an environment without Android SDK / Gradle access, so I couldn't
compile it myself — if Android Studio flags an error, tell me the message and I'll fix it.

## The audio-overlap trade-off (read this)

This is the one part of the request that has a real, unavoidable limitation: Android has no
public API to silence one specific app's audio while another plays, without root. So when
BumpBridge triggers Spotify, Spotify's audio genuinely starts, briefly overlapping with
Metrolist.

`Config.AUTO_PAUSE_AFTER_MS` (default 2500ms) makes the service pause Spotify again shortly
after triggering it, to shorten that overlap to about two seconds per track. The trade-off:
Spotify — not this app — decides how long something has to be "playing" before friend-activity
features treat it as your current track, and pausing quickly may or may not be enough for Bump
to register it. Set `AUTO_PAUSE_AFTER_MS = 0` to disable auto-pause if you'd rather let it play
fully and skip the guesswork.

## Other limitations

- **Catalog mismatches**: Spotify's catalog and YouTube Music's don't fully overlap. Remixes,
  live versions, region-locked tracks, or YouTube-only uploads may not have a Spotify match —
  `SpotifyApi.searchTrack` returns `null` in that case and nothing is triggered.
- **25-user cap**: fine for personal use; if you ever want to share this app, Spotify requires
  applying for extended quota mode.
- **Notification access is a sensitive permission**: fine for a sideloaded personal app, but
  Play Store review scrutinizes apps that request it.
- **Token storage**: tokens are stored in plain `SharedPreferences` for simplicity. If you plan
  to keep using this long-term, swap in `androidx.security.crypto.EncryptedSharedPreferences`.

## Project structure

```
app/src/main/java/com/example/bumpbridge/
  Config.kt                    -- client ID, package names, tunables
  PkceUtil.kt                  -- PKCE code verifier/challenge generation
  SpotifyAuthManager.kt        -- OAuth login, token storage/refresh
  SpotifyApi.kt                -- track search
  PlaybackTrigger.kt           -- launches the Spotify deep link
  MetrolistListenerService.kt  -- the notification listener that ties it together
  MainActivity.kt              -- login UI, notification-access shortcut
```
