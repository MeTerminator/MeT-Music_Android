# MeT-Music_Android

基于 Kotlin Multiplatform 和 Compose Multiplatform 的音乐播放器，当前支持 Android、Web 和桌面端（JVM）。

共享播放器界面使用 [AppleMusic-like Lyrics for Kotlin Multiplatform](https://github.com/MeTerminator/applemusic-like-lyrics_kotlin)
处理 AMLL/TTML 歌词解析和 Apple Music 风格的同步歌词显示。

## 项目结构

- [`androidApp`](./androidApp)：Android 应用入口。
- [`shared`](./shared)：共享的 Compose UI、播放状态、网络请求和歌词集成代码。
- [`desktopApp`](./desktopApp)：桌面端（JVM）应用。
- [`webApp`](./webApp)：Web 应用。
- [`../applemusic-like-lyrics_kotlin`](../applemusic-like-lyrics_kotlin)：歌词组件的本地源码仓库。

## TTML 歌词依赖

`shared` 使用依赖坐标：

```kotlin
implementation("top.met6:applemusic-like-lyrics:0.1.0")
```

本地开发时，[`settings.gradle.kts`](./settings.gradle.kts) 通过 Gradle Composite Build，
将上述坐标替换为同级目录下 `../applemusic-like-lyrics_kotlin` 仓库的 `:lyrics` 模块，
因此可以同时修改播放器和歌词组件源码。

两个仓库需要放在同一目录下：

```text
AAA-Projects/
├── MeT-Music_Android/
└── applemusic-like-lyrics_kotlin/
```

如果不需要本地源码联调，可以移除或关闭 `includeBuild`，改为从已配置的 Maven 仓库获取相同坐标。
歌词组件的 API 和独立构建方式请参考其[集成文档](https://github.com/MeTerminator/applemusic-like-lyrics_kotlin#从源码使用)。

## 共享代码目录

- [`commonMain`](./shared/src/commonMain/kotlin)：所有目标平台共用的代码。
- [`androidMain`](./shared/src/androidMain/kotlin)：Android 专用代码。
- [`jvmMain`](./shared/src/jvmMain/kotlin)：桌面端（JVM）专用代码。
- [`jsMain`](./shared/src/jsMain/kotlin)：JavaScript 专用代码。
- [`wasmJsMain`](./shared/src/wasmJsMain/kotlin)：WebAssembly 专用代码。

## 运行应用

也可以直接使用 IDE 中的运行配置。

```bash
# Android
./gradlew :androidApp:assembleDebug

# 桌面端：热重载
./gradlew :desktopApp:hotRun --auto

# 桌面端：普通运行
./gradlew :desktopApp:run

# WebAssembly
./gradlew :webApp:wasmJsBrowserDevelopmentRun

# JavaScript
./gradlew :webApp:jsBrowserDevelopmentRun
```

## 验证命令

```bash
# 构建 Android Debug 包
./gradlew :androidApp:assembleDebug

# 编译共享 Android 源集
./gradlew :shared:compileAndroidMain

# 验证歌词组件
../applemusic-like-lyrics_kotlin/gradlew :lyrics:jvmTest :lyrics:compileAndroidMain
```

## 相关项目

- [applemusic-like-lyrics_kotlin](https://github.com/MeTerminator/applemusic-like-lyrics_kotlin)：Kotlin Multiplatform 歌词组件。
- [applemusic-like-lyrics](https://github.com/amll-dev/applemusic-like-lyrics)：AMLL 上游参考项目。
- [Kotlin Multiplatform](https://www.jetbrains.com.cn/en-us/help/kotlin-multiplatform-dev/get-started.html)
- [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/)

## 双平台账号与音质

- 启动直接进入资料库，无需登录；可切换 QQ 音乐和网易云音乐资料库。
- 在设置中分别输入 QQ 号、网易云用户 ID，两个平台可同时保存账号，并单独切换或退出。
- 用户 ID 登录用于读取公开资料与歌单，不等同于平台会员授权；VIP 播放取决于服务器账号权益。
- QQ 音乐与网易云音乐分别保存音质设置，播放和预加载按歌曲所属平台选择音质。
- 网易云支持原生 YRC 逐字歌词、翻译和罗马音；播放队列、一起听和缓存保留平台标识，音频缓存按音质区分。
