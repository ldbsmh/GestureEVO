# GestureEVO

Android 手势与快捷操作工具，让常用操作触手可及。

- 手势轮盘与自定义快捷动作
- 应用、联系人及搜索引擎快捷搜索
- 剪贴板与屏幕内容处理、本地 OCR
- Pin 便签与提醒
- 本地与 WebDAV 备份

支持 Android 7.0 及以上的 ARM64 设备。按功能需要授予无障碍、悬浮窗等权限；部分系统操作需要 Root 或 Shizuku。

## 构建

配置 JDK 17、Python 3.10+ 和 Android SDK 后运行：

```bash
./gradlew :app:assembleDebug
```

Windows 使用 `gradlew.bat :app:assembleDebug`。所需 Android SDK 组件为 SDK Platform 36、Build Tools 36.0.0、NDK 27.0.12077973 和 CMake 3.22.1。设置 `JAVA_HOME` 和 `ANDROID_HOME`，或在本地 `local.properties` 中配置 `sdk.dir`。

仓库使用 Gradle Wrapper 8.13；首次构建需要联网下载 Gradle、依赖和 Abseil 源码。Python 需支持标准库 `lzma`，可通过 `-PpythonExecutable=/path/to/python` 指定解释器。调试 APK 输出到 `app/build/outputs/apk/debug/`，应用 ID 带 `.debug` 后缀，可与正式版共存。

## 本 fork 的改动：Z-Flow 小窗适配

fork 自 LumiaGG/GestureEVO（仅自用，不向上游提 PR）。在原有"模式 7（Sunshine / 米窗）"基础上，扩展为 Mi-Freeform 兼容广播的通用发送：

- 启动时探测已安装的兼容提供方（`com.sunshine.freeform` 阳光小窗、`io.relimus.zflow` Z-Flow），按 `<包名>.start_freeform` 动作逐一发送同源 extras（`packageName` / `activityName` / `userId`），协议细节保持与阳光小窗一致；
- 兼容提供方一个都没装时，回退到应用内建的原生小窗启动，手势不再"静默无反应"；
- 针对 Z-Flow 加固版：发送前先向 `content://io.relimus.zflow.request.auth`（`issueCapability`）领取与调用方 UID 绑定的能力 token 并附在广播上。需先在 Z-Flow 设置 → 其他 → 受信任外部调用者 中把 `io.github.lumiagg.gestureevo` 加入白名单；未加白名单或使用上游（不校验 token 的）Z-Flow 时自动降级为普通广播；
- 给 Z-Flow 发送的 `userId` 使用 Int（Z-Flow 以 `getIntExtra` 读取；阳光小窗路径维持 Long 不变）；
- `app/src/main/cpp/CMakeLists.txt` 支持通过环境变量 `ABSEIL_SOURCE_DIR` 指向本地已取回的 Abseil 源码（同一锁定 commit），便于无法访问 github.com 的离线构建。

使用：设置 → 小窗设置 → 小窗模式选择"模式 7"，配合已安装的阳光小窗或 Z-Flow 即可。

## 参与与许可

欢迎提交 Issue 或 Pull Request，详见[贡献指南](CONTRIBUTING.md)。安全问题请参阅[安全说明](SECURITY.md)。

主项目采用 [GPL-3.0-only](LICENSE)，第三方组件保留各自许可，见 [NOTICE](NOTICE)。

[隐私政策](PRIVACY_POLICY.md) · [用户协议](USER_AGREEMENT.md)
