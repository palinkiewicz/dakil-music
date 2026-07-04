<div align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp" alt="Music App Icon" width="128" />

  # Music

  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/palinkiewicz/music">
    <img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="60" />
  </a>
  <p align="center">
    <img src="https://img.shields.io/badge/Min%20SDK-29-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Min SDK" />
    <img src="https://img.shields.io/badge/Target%20SDK-36-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Target SDK" />
    <img src="https://img.shields.io/badge/Language-Kotlin%20100%25-7F52FF?style=for-the-badge" alt="Language" />
    <img src="https://img.shields.io/github/repo-size/palinkiewicz/dakil-music?style=for-the-badge&color=blue" alt="Repository Size" />
    <img src="https://img.shields.io/github/v/release/palinkiewicz/dakil-music?style=for-the-badge&color=orange" alt="Latest Release" />
  </p>
</div>

**Music** is a professional, enterprise-grade, and privacy-respecting Free and Open Source Software (FOSS) local audio player for Android. Built entirely from the ground up using **Jetpack Compose** and **AndroidX Media3**, it provides a lightning-fast, modern, and deterministic media experience without any remote tracking, advertisements, or telemetry.

The application follows strict Clean Architecture guidelines, utilizing manual dependency injection for zero-overhead startup times, robust performance, and absolute reliability.

## Key Features

### 🎵 Core Playback & Queue Management
* **Advanced Media3 Engine**: Powered by `MediaLibraryService` with full support for background playback, Bluetooth media controls, and system notification integrations.
* **Live Editable Queue**: Dynamic play queue supporting drag-to-reorder (via `sh.calvin.reorderable`) and customizable item removal modes (swipe gestures, context menu, or dedicated buttons).
* **System Playback Resumption**: State persistence via DataStore allows system UI, OEM media panels, and connected Bluetooth devices to seamlessly revive playback sessions even after process death.
* **External Audio Routing**: Features a dual-entry system for external files: an **Add-to-Queue** alias to append files directly to your live session, and an isolated **Quick Player** that runs in a transient standalone window without altering your library.

### 📊 Advanced Lyrics & Metadata Engineering
* **Comprehensive Lyrics Engine**: Native parsing and rendering of embedded plain-text and synchronized LRC files. Supports custom real-time time offsets.
* **Online Lyrics Integration**: Opt-in automatic fetching from `lrclib.net` matching tracks accurately by duration proximity.
* **Metadata Burning**: Hard-write modified lyrics and structural metadata directly back into your physical audio files with automated history propagation.
* **Tailored Cover Art & Album Rules**: Choose between global `MediaStore` artwork or individual track embedded pictures. Define granular per-album rules for artwork extraction and author matching that survive system ID reassignments.

### 🎛️ Studio-Grade Audio Customization
* **DynamicsProcessing Pipeline**: Leverages a modern platform-level dynamics processing architecture rather than legacy bundles, avoiding unintended session attenuation.
* **Parametric Equalizer & Bass Boost**: Fully configurable frequency bands paired with a low-shelf bass boost contribution, completely guarded by an explicit hardware-safe brickwall limiter.

### 📈 Local Analytics & Smart Organization
* **Opt-in Listening History**: Local-first, high-fidelity polling system that maps playback duration, excludes paused time, tracks loops, and checkpoints progress to disk to survive unexpected closures.
* **Deterministic Statistics**: Visualized listening trends, metrics, and weekday/hour bucketing utilizing native `java.time` operations to honor local timezones and custom first-day-of-week settings.
* **Data Portability**: Full support for importing, exporting, and merging listening records using standard CSV formats.

### 🚗 System Integrations & Backups
* **Android Auto**: Fully compatible with Android Auto browsing hierarchies (Albums, Artists, Genres, Playlists), including voice-command routing via system `MEDIA_PLAY_FROM_SEARCH` intents.
* **Glance Home-Screen Widget**: Clean, modern remote control widget utilizing Jetpack Glance, reflecting live playback states, track info, and cached cover art previews.
* **Enterprise Backup Engine**: Export independent slices of your application configuration (settings, playlists, rules, sort states) into structured CSV files packed securely within a single cross-device ZIP file.

## Architecture & Technology Stack

The project relies on a highly disciplined multi-layered structure with inward-pointing dependencies (`presentation` → `domain` ← `data`) ensuring strict isolation of business logic:

* **Domain Layer**: Pure, platform-free Kotlin code representing models, repository interfaces, and isolated single-operation use cases.
* **Data Layer**: Concrete implementations of domains. Media discovery is handled live through the Android `MediaStore` (no heavy local database caching for media), persistence utilizes **Room** (for listening records) and **9 isolated Preferences DataStores** to prevent I/O bottlenecks. Audio manipulation utilizes JAudiotagger for tag parsing.
* **Presentation Layer**: Built completely using **Jetpack Compose (Material Design 3)**, establishing pure state tracking via `StateFlow` under an MVVM structure.
* **Manual Dependency Injection**: Zero DI frameworks (no Hilt/Dagger/Koin overhead). Dependencies are explicitly wired inside a process-lifetime `AppContainer` for deterministic lifecycle control and accelerated application launch speeds.

## Getting Started & Development

### Requirements
* **JDK 17** or newer
* **Android SDK** (Target SDK 36, Minimum SDK 29)
* An active Android Emulator or physical device

### Building and Installation
Execute the standard Gradle tasks via the wrapper at the repository root:

```bash
# Clone the repository
git clone [https://github.com/palinkiewicz/music.git](https://github.com/palinkiewicz/music.git)
cd music

# Build the debug configuration APK
./gradlew assembleDebug

# Build and deploy directly onto a connected device or emulator
./gradlew installDebug
```

## Verification & Testing

Maintain code quality and stability through the following automated validation tasks:

```bash
# Run JVM unit tests (includes statistics, codecs, and parser verifications)
./gradlew test

# Run JVM unit tests exclusively for the debug variant
./gradlew testDebugUnitTest

# Execute comprehensive Android Lint checks (errors will break the build)
./gradlew lint

# Execute instrumented UI/integration tests (requires connected device)
./gradlew connectedAndroidTest
```

[!NOTE]
Dependencies are centrally managed via the Gradle Version Catalog (`gradle/libs.versions.toml`). Avoid declaring hardcoded versions inline within build scripts.

## Localization & Contributions

We aim for global accessibility. When contributing new features or UI controls, ensure that corresponding translation entries are supplied across all active resource configurations under `app/src/main/res/values-*/strings.xml`.

## License

This project is licensed under the Free and Open Source Software regulations. Feel free to inspect, fork, and enhance the application following standard open-source compliance guidelines.
