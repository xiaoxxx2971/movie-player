# 极光影院 (FreeMovieApp) - Android 源码工程

这是一个采用现代 Android 架构开发的免费高品质流媒体应用工程，包含精选开源公有领域电影片单，开箱即用。

---

## 🌟 技术架构与特性

1. **核心播放器**：基于 Google 官方推荐的 **AndroidX Media3 (ExoPlayer 1.2.1)**，原生支持 HLS (`.m3u8`)、DASH、MP4 自适应码率播放。
2. **UI 框架**：纯 **Jetpack Compose** 声明式 UI，采用暗黑影院（Dark Cinema）极简现代视觉设计。
3. **单 Activity 导航**：基于 Navigation Compose 实现 `HomeScreen`（首页推荐与分类检索） -> `DetailScreen`（影片详情） -> `PlayerScreen`（全屏横屏沉浸播放）。
4. **图片加载**：集成 **Coil 2.6.0** 异步图片缓存库，平滑加载海报与缩略图。
5. **合规内容源**：内置 Blender 基金会等开放版权/公共领域的高清测试流。

---

## 🚀 如何在 Android Studio 中打开并一键打包 APK

### 步骤 1：导入工程
1. 解压本项目的 `FreeMovieApp.zip` 文件到本地目录。
2. 打开 **Android Studio** (推荐 Hedgehog 2023.1.1 或更高版本)。
3. 选择 **File > Open...**，选中解压后的 `FreeMovieApp` 根目录。
4. 等待 Android Studio 自动完成 Gradle 依赖同步（Sync）。

### 步骤 2：编译生成 APK
1. 点击顶部菜单栏的 **Build > Build Bundle(s) / APK(s) > Build APK(s)**。
2. 编译完成后，右下角会弹出提示通知，点击 **locate** 即可在 `app/build/outputs/apk/debug/` 目录找到生成的 `app-debug.apk`。
3. 将该 APK 传输到 Android 手机上即可直接安装并观影！

---

## 🛠️ 如何配置与扩展自己的视频源

工程中影视数据位于 `app/src/main/java/com/example/freemovieapp/data/MovieRepository.kt`：
- 你可以直接修改或追加 `sampleMovies` 列表中的项目。
- 也可以通过 Retrofit / Ktor 替换为从你的远程云服务器（如自建的 Node.js/Go 接口或静态 JSON）动态加载片单：
```kotlin
data class Movie(
    val id: String,
    val title: String,
    val category: String,
    val year: String,
    val rating: Double,
    val duration: String,
    val description: String,
    val posterUrl: String,
    val backdropUrl: String,
    val videoUrl: String // 支持任意标准 HLS (.m3u8) 或 MP4 链接
)
```

---

## ☁️ 云端免安装自动编译 APK (GitHub Actions)

如果你不想在电脑上安装任何开发软件，直接使用 GitHub 免费云端打包：

1. 打开 [GitHub](https://github.com/) 并登录，点击右上角 **「+」 > New repository** 新建一个仓库（公开或私有均可）。
2. 在新仓库页面，点击 **uploading an existing file**，将本项目解压后的全部文件（包括 `.github` 文件夹）拖拽上传并 Commit。
3. 点击仓库顶部的 **Actions** 标签页，你会看到自动触发的 **「Build FreeMovieApp APK」** 流程。
4. 几分钟后编译完成（显示绿色的对勾），点击该工作流进入详情页。
5. 在最下方的 **Artifacts** 区域即可直接点击下载 **`FreeMovieApp-APK`**（内含可直接安装的 `.apk` 文件）。
