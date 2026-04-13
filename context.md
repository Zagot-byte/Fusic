# Fusic — Project Context

## What Is This
Personal YouTube Music player. Bypasses YTM paywall for high
quality audio, background playback, offline downloads, and local
music. Single user only. Not for distribution.

---

## Repo Structure
```
fusic/
  docker-compose.yml    ← spins both services together
  context.md            ← this file
  .gitignore
  fusic-logo.svg        ← app icon source
  fusic-logo-192.png    ← Android icon
  fusic-logo-512.png    ← PWA icon

  proxy/                ← audio resolver + image proxy ONLY
    main.py             ← /resolve, /stream, /image, /health
    audio.py            ← yt-dlp extract CDN URL
    image.py            ← CORS image proxy
    auth.py             ← X-API-Key middleware
    requirements.txt
    Dockerfile
    .env.example
    .env                ← gitignored, API_KEY + COOKIES_FILE
    cookies.txt         ← gitignored, export from YTM browser

  api/                  ← all ytmusicapi music data ONLY
    main.py             ← search, home, artist, album, radio, lyrics
    ytmusic.py          ← all ytmusicapi calls in threadpool
    auth.py             ← X-API-Key middleware
    requirements.txt
    Dockerfile
    .env.example
    .env                ← gitignored, API_KEY only
    oauth.json          ← gitignored, ytmusicapi auth

  frontend/             ← web app (Vanilla JS, no framework)
    index.html
    css/main.css
    js/app.js

  android/              ← Capacitor WebView wrapper
    capacitor.config.ts
    package.json
    app/src/main/java/dev/fusic/
      MainActivity.java
      MediaPlayerService.java   ← foreground service (tiny-music-player fork)
      MediaPlayerPlugin.java    ← Capacitor JS bridge
      AudioPlayer.java
      Notifications.java
```

---

## Services

### proxy — port 8001
Only touches yt-dlp and httpx. Zero music data logic.
```
GET /health
GET /resolve/{videoId}   ← returns direct YT CDN URL (~6hr expiry)
GET /stream/{videoId}    ← fallback byte proxy if CDN CORS blocked
GET /image?url=          ← CORS proxy for YTM thumbnails
```

### api — port 8002
Only touches ytmusicapi. Zero audio bytes.
```
GET /health
GET /search?q=&filter=
GET /suggestions?q=
GET /home
GET /artist/{channelId}
GET /album/{browseId}
GET /radio/{videoId}     ← get_watch_playlist, YTM autoplay
GET /lyrics/{browseId}   ← timed lyrics if available
GET /charts?country=
GET /moods
```

---

## Auth
Single API key on both services.
Header: `X-API-Key: <key>`
Set in both `.env` files — must be the same value.
Dev key: `fusic_dev_key`
Production: generate random 32-char string.

---

## Audio Quality
With YTM premium cookies (cookies.txt):
  → format 141 (256kbps AAC M4A) — best quality
Without cookies:
  → bestaudio (~128kbps opus)

yt-dlp format string: `141/bestaudio[ext=m4a]/bestaudio`
extractor_args: `player_client=web_music`

---

## Client-side Architecture
Everything except audio resolve and music data is client-side.

### What never hits the server:
- Queue management
- Playback state (position, play/pause, volume)
- History
- Downloads (fetched directly from CDN URL)
- Local music playback

### localStorage keys:
```
fusic_volume          float
fusic_shuffle         bool
fusic_repeat          off|one|all
fusic_history         array, last 50 tracks
fusic_liked           array of videoIds
fusic_downloads       array of {videoId, path, metadata}
fusic_cache_urls      {videoId: {url, expires}}  ← CDN URL cache
fusic_cache_home      {data, timestamp}           ← TTL 1hr
fusic_cache_artists   {channelId: {data, timestamp}} ← TTL 6hr
fusic_cache_albums    {browseId: {data, timestamp}}  ← TTL 6hr
```

### CDN URL cache flow:
```
play(videoId)
  → check fusic_cache_urls[videoId]
  → if exists and expires > Date.now() → use cached URL
  → else → GET /resolve/{videoId} → cache result → use URL
  → set <audio>.src = url → play()
```

---

## Frontend Stack
- Vanilla JS, zero frameworks, zero npm, zero build step
- 3 files: index.html + css/main.css + js/app.js
- `<audio>` element for playback (not Web Audio API)
  reason: Web Audio API gets suspended on Android background
- dotart-ui for ASCII borders/decorations (final layer, TBD)

### Color palette:
```
--bg:          #0f0b0a   near black background
--surface:     #141010   card/panel surface
--surface-hi:  #1a1414   hover/active
--crimson:     #7a1a1a   primary accent
--crimson-hi:  #c94040   highlights/active
--text:        #ededec   primary text
--text-dim:    #6b6b6a   secondary text
--border:      #2a1818   subtle border
```

### Fonts:
- Space Grotesk — titles, headers
- IBM Plex Mono — artist names, timestamps, lyrics, nav

### Neumorphism (dark):
```css
/* raised element */
box-shadow: -4px -4px 8px #2a1818, 4px 4px 8px #080505;
/* pressed/active */
box-shadow: inset 2px 2px 5px #080505, inset -2px -2px 5px #2a1818;
```

### Layout:
Desktop (>768px): sidebar(240px) + main(flex) + player panel(320px)
Mobile (≤768px): fullscreen content + bottom mini-player bar(60px)
  tap mini-player → fullscreen overlay slides up from bottom

---

## Android — Capacitor Wrapper
Wraps frontend/ in a WebView. No rewrite.
appId: dev.fusic.app

### Native layer adds:
- MediaPlayerService (foreground service, forked from tiny-music-player)
  → keeps audio alive when screen off / app backgrounded
  → accepts HTTP CDN URLs via setDataSource(url)
- MediaSession → lock screen controls + notification player
- Capacitor bridge (MediaPlayerPlugin.java)
  → web JS calls: play(url, metadata), pause(), seek(), etc
  → events to JS: stateChange, progress, trackEnd, mediaAction

### JS usage:
```js
await Capacitor.Plugins.MediaPlayer.play({
  url: cdnUrl,
  title: track.title,
  artist: track.artist,
  thumbnailUrl: track.thumbnail
})
```

---

## Running Locally
```bash
# start both services
cd fusic
docker compose up --build -d

# verify
curl http://localhost:8001/health
curl http://localhost:8002/health

# test search
curl -H "X-API-Key: fusic_dev_key" \
  "http://localhost:8002/search?q=coldplay"

# test resolve
curl -H "X-API-Key: fusic_dev_key" \
  "http://localhost:8001/resolve/dQw4w9WgXcQ"

# open frontend
open frontend/index.html
```

---

## Production (Oracle A1)
```bash
# on server
git clone <repo> fusic
cd fusic
cp proxy/.env.example proxy/.env
cp api/.env.example api/.env
# edit both .env files with production API_KEY
# drop cookies.txt in proxy/
# drop oauth.json in api/
docker compose up -d

# update frontend API_BASE to server IP
# in js/app.js:
const PROXY_BASE = 'http://<oracle-ip>:8001'
const API_BASE   = 'http://<oracle-ip>:8002'
const API_KEY    = '<production-key>'
```

---

## TODO
- [ ] docker compose up --build runs clean
- [ ] test /resolve returns valid CDN URL
- [ ] frontend plays audio from CDN URL
- [ ] mobile fullscreen player overlay working
- [ ] Android SDK install complete
- [ ] APK build + sideload
- [ ] background playback test on device
- [ ] cookies.txt for 256kbps
- [ ] Oracle A1 deployment
- [ ] dotart-ui visual layer (final pass)
