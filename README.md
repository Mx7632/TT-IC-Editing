# TTEdit - Android 图片编辑器

TTEdit 是一个基于现代 Android 技术栈（Kotlin + Jetpack Compose）开发的轻量级图片编辑应用。它提供了一套直观的工具，用于裁剪、旋转、调整图像参数、应用滤镜以及添加个性化文字。

## ✨ 主要功能

*   **基础编辑**
    *   **裁剪 (Crop)**: 支持自由裁剪和预设比例裁剪。
    *   **旋转 (Rotate)**: 支持 90 度旋转。
    *   **翻转 (Flip)**: 支持水平翻转和垂直翻转。

*   **色彩调整**
    *   **亮度 (Brightness)**: 调整图片明暗度。
    *   **对比度 (Contrast)**: 调整图片对比度。

*   **滤镜效果 (Filters)**
    *   内置多种精美滤镜：原图、黑白、复古 (Sepia)、暖色、冷色、反色、胶片 (Polaroid)。
    *   基于 `ColorMatrix` 实现的高性能实时滤镜渲染。

*   **文字叠加 (Text Overlay)**
    *   添加自定义文字图层。
    *   **手势操作**: 支持单指拖动移动，双指捏合缩放字体大小，双指旋转文字角度。
    *   **样式调整**: 修改文字颜色、字体（衬线、无衬线、手写体等）。
    *   **自动吸附**: 移动时支持吸附效果（基于测量尺寸的精确中心点）。

*   **保存与分享**
    *   将编辑后的图片保存到本地相册。

## 🛠 技术栈

*   **语言**: Kotlin
*   **UI 框架**: [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material3)
*   **架构模式**: MVVM (Model-View-ViewModel)
*   **图片加载**: [Coil](https://coil-kt.github.io/coil/)
*   **异步处理**: Kotlin Coroutines & Flow
*   **最低支持版本 (Min SDK)**: API 21 (Android 5.0)
*   **目标版本 (Target SDK)**: API 36

## 🚀 快速开始

1.  **克隆项目**
    ```bash
    git clone <repository-url>
    ```

2.  **打开项目**
    使用 Android Studio 打开项目根目录。

3.  **同步依赖**
    等待 Gradle Sync 完成，下载所需依赖库。

4.  **运行应用**
    连接 Android 设备或启动模拟器，点击 "Run" (绿色三角形按钮)。

## 📂 项目结构

```
com.yourname.photoeditor
├── ui
│   └── theme          // Compose 主题配置
├── EditScreen.kt      // 编辑页面 UI (Compose)
├── EditViewModel.kt   // 编辑页面业务逻辑 (StateFlow, Coroutines)
├── ImageProcessor.kt  // 图像处理核心算法 (Bitmap, Canvas, ColorMatrix)
├── MainActivity.kt    // 应用入口
├── MainScreen.kt      // 主界面 (图片选择)
└── PermissionUtils.kt // 权限请求工具
```

## 📝 开发日志

- **2025-12-07**:
    - 修复自由裁剪功能的稳定性问题。
    - 优化文字图层手势交互：支持双指缩放字体大小和旋转（0-360°），增加操作流畅性。
    - 修复文字样式调节时的实时渲染问题。
    - 新增图片水平翻转和垂直翻转功能。
    - 完善滤镜系统，基于 `ColorMatrix` 实现多种效果。

## 🤝 贡献

欢迎提交 Issue 和 Pull Request 来改进 TTEdit！

---
Created with ❤️ by TTEdit Team
