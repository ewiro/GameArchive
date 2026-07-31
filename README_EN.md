<div align="center">

<img src="screenshots/app_icon.png" width="128" alt="GameArchive icon">

</div>

## About

GameArchive started as a Steam library organizer. It can now connect to Bangumi, manage anime collections, and combine game playtime and anime progress into a personal activity history.

The project is designed for players and anime viewers who want to keep control of their data. Account settings, tags, notes, progress baselines, and activity history are primarily stored on the device. GameArchive does not provide its own account system or cloud database.

> [!NOTE]
> This is an unofficial, non-commercial personal open-source project. It is not affiliated with or endorsed by Valve, Steam, or Bangumi.

## Screenshots

<table>
  <tr>
    <td align="center"><img src="screenshots/login.jpg" width="240"><br>Sign in</td>
    <td align="center"><img src="screenshots/game.jpg" width="240"><br>Game library</td>
    <td align="center"><img src="screenshots/detail_game.jpg" width="240"><br>Game details</td>
  </tr>
  <tr>
    <td align="center"><img src="screenshots/specials.jpg" width="240"><br>Steam specials</td>
    <td align="center"><img src="screenshots/anime.jpg" width="240"><br>Anime collections</td>
    <td align="center"><img src="screenshots/detail_anime.jpg" width="240"><br>Anime details</td>
  </tr>
  <tr>
    <td align="center"><img src="screenshots/activity.jpg" width="240"><br>Activity history</td>
    <td align="center"><img src="screenshots/sequence.jpg" width="240"><br>Filters and sorting</td>
    <td align="center"><img src="screenshots/setting.jpg" width="240"><br>Settings</td>
  </tr>
</table>

## Features

### Steam library

- Loads player profiles, levels, and owned games, with merged libraries from multiple Steam accounts.
- Search, sort, filter, and group games by name, playtime, local status, or tags.
- Eight local statuses: Unplayed, Playing, Completed, Multiple Playthroughs, Long-term, Perfected, Shelved, and Abandoned.
- Custom names, tags, notes, avatars, avatar frames, and profile backgrounds.
- Automatically loads Steam profile decorations and animated backgrounds; manual overrides take priority.
- Game details include media, store descriptions, player reviews, local comments, play history, and personal achievements.

### Bangumi anime

- Signs in with Bangumi OAuth and loads all five anime collection categories.
- List and grid layouts with configurable rating display.
- Fuzzy search for Bangumi anime subjects.
- Edit collection status, main-episode progress, rating, tags, and comments, then sync changes to Bangumi.
- Subject information, staff, summaries, and watch history.
- Preserves local tag input order. Watch history entries can be edited and reflected in activity statistics.

### Activity history

- A yearly heatmap for daily game and anime activity.
- Steam tracking only records newly detected playtime for games marked as Playing.
- Bangumi tracking only records newly watched main episodes for anime marked as Watching; bonus episodes are excluded.
- The first sync creates a baseline and never counts existing totals as today's activity.
- Browse a single day or the complete cover history up to a selected date, then open the corresponding details.
- When the Anime page is disabled, only game activity is shown.

### Steam specials and customization

- Scrapes Steam specials while filtering common DLC, soundtrack, and season-pass entries.
- Sorts by review score, price, discount, or sales.
- Light, dark, and system themes, with English and Simplified Chinese interfaces.
- Configurable playtime capsules, status grouping, visible pages, and anime layout.
- Manual backup and restore for marks, tags, notes, custom names, activity statistics, and selected appearance settings.

## Download and setup

Download APKs from [Releases](https://github.com/ewiro/GameArchive/releases):

- `arm64-v8a`: recommended for most modern Android devices.
- `armeabi-v7a`: intended for older 32-bit devices.

The minimum supported version is Android 7.0 (API 24).

### Steam

First-time setup requires:

1. Your SteamID, the numeric ID associated with your profile.
2. A [Steam Web API Key](https://steamcommunity.com/dev/apikey).

The app never asks for your Steam password, Steam Guard code, or login cookies. Your game details must be public for the Steam API to return a complete library.

### Bangumi

Bangumi support is optional. In Settings, add an account and choose Bangumi. The app opens your browser to complete OAuth authorization.

## Build from source

### Requirements

- Android Studio
- JDK 17
- Android SDK 37

### Configuration

1. Clone the repository:

   ```bash
   git clone https://github.com/ewiro/GameArchive.git
   cd GameArchive
   ```
2. Copy the configuration template:

   ```text
   app/src/main/java/com/example/gamearchive/AppConfig.kt.example
   → app/src/main/java/com/example/gamearchive/AppConfig.kt
   ```
3. Configure the values you need:

   - `PROXY_URL`: the default public proxy or your own Worker URL.
   - `BANGUMI_CLIENT_ID` and `BANGUMI_CLIENT_SECRET`: create an app on the [Bangumi developer page](https://bgm.tv/dev/app).
   - `BANGUMI_REDIRECT_URI`: must match both the developer registration and the Manifest intent filter.

   `AppConfig.kt` is excluded by `.gitignore`. Never commit real credentials.
4. Build a Debug APK:

   ```bash
   # Windows
   .\gradlew.bat assembleDebug

   # macOS / Linux
   ./gradlew assembleDebug
   ```

### Self-hosted proxy

[`cloudflare_worker.js`](cloudflare_worker.js) in the repository root contains the proxy implementation. Deploy it to your own Cloudflare Worker and update `PROXY_URL` in `AppConfig.kt`.

## Technology

- Kotlin 2.4.0 and JVM 17
- Compose Multiplatform 1.11.1
- MIUI X (`miuix-ui` / `miuix-icons`)
- Activity-based navigation and MVVM
- Retrofit, OkHttp, and Gson
- Kotlin Coroutines
- Coil
- SharedPreferences for local storage
- Cloudflare Worker request forwarding

## Feedback and contributions

Use [Issues](https://github.com/ewiro/GameArchive/issues) for bug reports and feature requests. A useful bug report should include:

- GameArchive version, device model, and Android version
- Reproduction steps
- Actual and expected behavior
- Screenshots or logs with account credentials removed, when relevant

Keep Pull Requests focused on a single purpose. Do not commit Steam API Keys, Bangumi OAuth credentials, signing files, or local configuration.

## Acknowledgements

- [Steam Web API](https://steamcommunity.com/dev)
- [Bangumi API](https://github.com/bangumi/api)
- [MIUI X](https://github.com/compose-miuix-ui/miuix)
- [DSEG](https://www.keshikan.net/fonts-e.html)
