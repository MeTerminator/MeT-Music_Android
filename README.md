# MeT Music Mobile

Kotlin Multiplatform music player targeting Android, Web, and Desktop (JVM).

The shared player UI uses the [AppleMusic-like Lyrics for Kotlin Multiplatform](https://github.com/MeTerminator/applemusic-like-lyrics_kotlin)
library for AMLL/TTML parsing and Apple Music-style synchronized lyrics.

## Repository layout

- [`androidApp`](./androidApp) — Android application entry point.
- [`shared`](./shared) — shared Compose Multiplatform UI, playback state, networking, and lyric integration.
- [`desktopApp`](./desktopApp) — Desktop (JVM) application.
- [`webApp`](./webApp) — Web application.
- [`../applemusic-like-lyrics_kotlin`](../applemusic-like-lyrics_kotlin) — sibling source checkout of the lyrics component.

## Lyrics dependency

`shared` declares the stable coordinate `top.met6:applemusic-like-lyrics:0.1.0`.
For local development, [`settings.gradle.kts`](./settings.gradle.kts) uses a Gradle composite build to substitute that coordinate with
the sibling `../applemusic-like-lyrics_kotlin` repository and its `:lyrics` module. This keeps the app and component linked while developing them together.

If the sibling repository is not checked out, remove or disable the `includeBuild` block and provide the same coordinate from a configured Maven repository.
See the component project's [integration documentation](https://github.com/MeTerminator/applemusic-like-lyrics_kotlin#从源码使用) for its API and standalone build commands.

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
    - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
    - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
      For example, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
      folder is the appropriate location.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and
options:

- Android app: `./gradlew :androidApp:assembleDebug`
- Desktop app:
    - Hot reload: `./gradlew :desktopApp:hotRun --auto`
    - Standard run: `./gradlew :desktopApp:run`
- Web app:
    - Wasm target (faster, modern browsers): `./gradlew :webApp:wasmJsBrowserDevelopmentRun`
    - JS target (slower, supports older browsers): `./gradlew :webApp:jsBrowserDevelopmentRun`

Before building locally, make sure both repositories are siblings:

```text
AAA-Projects/
├── MeT-Music_Android/
└── applemusic-like-lyrics_kotlin/
```

Useful verification commands:

- Android app: `./gradlew :androidApp:assembleDebug`
- Shared module: `./gradlew :shared:compileKotlinAndroid`
- Lyrics dependency: `../applemusic-like-lyrics_kotlin/gradlew :lyrics:jvmTest :lyrics:compileAndroidMain`

---

Learn more
about [Kotlin Multiplatform](https://www.jetbrains.com.cn/en-us/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/#compose-multiplatform),
[Kotlin/Wasm](https://kotl.in/wasm/)…

We would appreciate your feedback on Compose/Web and Kotlin/Wasm in the public Slack
channel [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
If you face any issues, please report them on [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP).
