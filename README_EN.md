<div align="center">

<a href="README.md">
  <img src="https://img.shields.io/badge/中文-简体-1976D2?logoColor=white" alt="中文" />
</a>
<a href="README_EN.md">
  <img src="https://img.shields.io/badge/English-EN-546E7A?logoColor=white" alt="English" />
</a>

</div>
<br>

# Game Archive 

> A modern Steam library management and special offers query tool built with Compose and MIUI X theme.

![Platform](https://img.shields.io/badge/Platform-Android-green.svg)
![Language](https://img.shields.io/badge/Language-Kotlin-orange.svg)
![License](https://img.shields.io/badge/License-MIT-blue.svg)


---

##  Screenshot Preview 

| Login & Setup | My Library  | Specials  | Immersive Details  | Settings | Sort & Filter |
|:---:|:---:|:---:|:---:|:---:|:---:|
| <img src="screenshots/login.jpg" width="200"/> | <img src="screenshots/library.jpg" width="200"/> | <img src="screenshots/specials.jpg" width="200"/> | <img src="screenshots/detail.jpg" width="200"/> |<img src="screenshots/setting.jpg" width="200"/> |<img src="screenshots/sequence.jpg" width="200"/> |



---

##  Core Features 

###  My Library
*   **Visuals**: Built on **MIUI X** theme, supporting dark/light/follow-system modes.
*   **Statistics**: Displays avatar, level, total game count, and total playtime at the top.
*   **Cards**: List card colors are dynamically graded based on playtime (from light to dark), intuitively showing your "dedication."
*   **Marks**: 8 status markers (Unplayed / Playing / Completed / Multi-completed / Long-term / Perfected / Shelved / Abandoned), each with distinct colors at a glance.

###  Specials Query
*   **Experience**: Built-in filtering automatically removes DLCs, soundtracks, season passes, etc., displaying only the **base games**.
*   **Data**: Uses concurrent scraping technology to load hundreds of discounted games at once.
*   **Sorting**: Supports multi-dimensional sorting by **Review Rating**, **Current Price**, **Discount**, and **Sales**.
*   **Reviews**: The list directly displays review ratings (e.g., "95% Positive") and distinguishes rating levels with different colors.

###  Immersive Details 
*   **Gallery**: Horizontal sliding 16:9 adaptive gallery, seamlessly splicing videos and screenshots for a borderless experience.
*   **Reviews**: Directly integrates the Steam player review section, supporting long reviews without truncation.
*   **Layout**: Uses JS/CSS injection technology to rearrange cluttered Steam description HTML, removing excess spacing for a beautiful text-image layout.
*   **Cover**: Automatically fixes irregular promotional images, prioritizing the display of standard Steam covers.

###  Customization
*   Supports custom profile backgrounds, avatars, and **avatar frames** (GIF supported).
*   Library grouping (recent / played / backlog) with multiple sort options.
*   Light / Dark / Follow-system themes, with status bar auto-adapting to theme.
*   **Custom Tags**: Add personalized tags to any game, with full CRUD support.


---

##  Privacy & Security

We understand the importance of Steam account security, therefore:

1.  **Local Storage**: All Steam IDs and Web API Keys are stored only in the local encrypted storage (`SharedPreferences`) of your phone.
2.  **Zero Collection**: This project has **no** backend servers and will not collect or upload any of your personal information.
3.  **Open Source & Transparent**: All code (including proxy server scripts) is completely open source, allowing you to audit it at any time.

---

##  Deployment & Usage

### 1. Download & Install
Go to the [Releases Page](https://github.com/ewiro/GameArchive/releases) to download the latest version of the APK.
*   Modern phones are recommended to download the `arm64` version.

### 2. Get Necessary Information
You need to enter your Steam information for the first time use:
*   **Steam ID**: Your 64-digit numeric ID (can be found in your profile link).
*   **Web API Key**: Go to the [Steam Developer Page](https://steamcommunity.com/dev/apikey) to apply for free (domain name can be filled in arbitrarily).

### 3. (Advanced) Self-hosted Proxy Service
To ensure stable access in mainland China, the App uses a built-in Cloudflare Worker proxy by default (address configured in the `PROXY_URL` constant of `AppConfig.kt`).
For absolute data security and faster speeds, **it is strongly recommended that you deploy your own Worker** — simply replace that one address in `AppConfig.kt` and rebuild.

 **[Click to View: Self-hosted Cloudflare Worker Tutorial](WORKER_SETUP.md)**

---

##  Tech Stack
This project is built using modern Android development standards:

*   **Language**: [Kotlin](https://kotlinlang.org/)
*   **Architecture**: MVVM (Compose + HorizontalPager + ViewModel + LiveData)
*   **Network**: [Retrofit](https://square.github.io/retrofit/) + [OkHttp](https://square.github.io/okhttp/) + [GSON](https://github.com/google/gson)
*   **Image Loading**: [Coil](https://coil-kt.github.io/coil/) (optimized for GIF animations and memory caching)
*   **UI Components**: Compose Multiplatform + MIUI X (miuix-ui 0.9.2)
*   **Asynchronous Processing**: Kotlin Coroutines (Async/Await concurrent requests)
*   **Backend/Proxy**: Cloudflare Worker (request forwarding & anti-scraping)
---

##  License

This project is open sourced under the MIT License - see the [LICENSE](LICENSE) file for details.
