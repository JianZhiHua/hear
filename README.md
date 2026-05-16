# 听见

听见是一款面向个人和熟人圈使用的 Android 音乐聚合播放器。应用使用 Kotlin、Jetpack Compose 和 Media3 构建，支持 QQ 音乐与网易云音乐的搜索、歌单同步、播放队列、歌词显示、本地歌单和后台播放。

## 功能概览

- 双平台接入：支持 QQ 音乐、网易云音乐的 Cookie 配置、歌单同步、歌单详情、搜索、歌词和播放链接解析。
- 播放体验：基于 Media3 ExoPlayer，支持播放/暂停、上一首、下一首、进度拖动、音量、顺序播放、单曲循环和随机播放。
- 后台播放：接入 MediaSessionService，支持通知栏、锁屏和耳机控制。
- 歌词体验：支持 LRC 时间轴解析、当前歌词高亮、自动滚动和自定义歌词样式。
- 本地歌单：支持本机缓存平台歌单，创建本地歌单，将搜索结果加入本地歌单，并从本地歌单中移除歌曲。
- 小清新 UI：底部导航结构，包含“我的音乐”“聚合搜索”“设置”，配合全局迷你播放器和沉浸式播放页。

## 项目结构

```text
app/src/main/java/com/qingyi/hear
├── domain       # 领域模型、歌词解析、队列逻辑
├── network      # JSON、HTTP、DNS 兜底解析
├── playback     # Media3 播放控制、后台服务、媒体项映射
├── providers    # QQ 音乐、网易云音乐平台实现
├── storage      # Cookie、播放队列、歌词设置、本地歌单缓存
└── ui           # Compose 界面与 ViewModel
```

## 环境要求

- Android Studio 或 IntelliJ IDEA
- JDK 17
- Android SDK 36
- Gradle Wrapper 已随项目提交，无需全局安装 Gradle

## 构建与验证

在项目根目录执行：

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

Debug APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 使用说明

1. 启动应用后进入“设置”页。
2. 分别填入 QQ 音乐和网易云音乐 Cookie，并点击“校验并保存”。
3. 点击“校验并同步所有歌单”，平台歌单会缓存到本机。
4. 在“我的音乐”页查看平台歌单，也可以创建本地歌单。
5. 在“聚合搜索”页搜索歌曲，点击播放，或加入本地歌单。
6. 点击底部迷你播放器可进入全屏播放页，查看封面、歌词和播放队列。

## 隐私与限制

- Cookie 仅保存在本机，不应写入日志、提交到仓库或上传到第三方服务。
- 应用不会绕过会员、版权、DRM 或账号权限限制。若平台没有返回可播放链接，界面会展示中文错误提示。
- 音乐平台真实播放 URL 通常带有短期签名，应用按需解析，不长期缓存真实播放 URL。

## IDEA 运行配置

仓库保留了 `.idea/runConfigurations` 中的共享运行配置：

- `Hear App`
- `Gradle - assembleDebug`
- `Gradle - lintDebug`
- `Gradle - testDebugUnitTest`

其他 IDE 本机状态、构建缓存和 `local.properties` 已通过 `.gitignore` 排除。

## 常见问题

### Cookie 失效怎么办？

进入“设置”页重新粘贴对应平台 Cookie，然后重新同步歌单。

### 歌曲无法播放怎么办？

可能是网络、DNS、版权、会员或账号权限限制。应用会尽量显示具体错误原因；如果两个平台都有同一首歌，可以尝试切换来源。

### 为什么要缓存歌单？

平台歌单同步依赖网络和 Cookie。本机缓存可以让应用在下次启动时先展示已有歌单，减少等待和接口失败带来的空白状态。
