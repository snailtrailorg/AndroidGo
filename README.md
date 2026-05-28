# AndroidGo

Android 围棋应用。Kotlin + Jetpack Compose，双引擎（GNU Go + KataGo），中国规则数子法。

## 功能

- 双引擎支持：GNU Go（fork+exec）和 KataGo v1.16.5（dlopen+in-process，Eigen CPU 后端）
- GTP 协议完整实现（C++ JNI 桥接）
- 中国规则（数子法）+ 引擎死子判定
- SGF 棋谱导入/导出 + 复盘
- 中英双语（跟随系统语言，缺省英文）
- Material 3 UI + Font Awesome 风格图标
- 让子、贴目、棋盘大小可配置，参数自动保存
- 悔棋（退 2 手）、恢复、过一手、点目、终局

## 构建

需要 Android Studio + NDK 27。

```bash
./gradlew assembleDebug
```

APK 约 32MB（debug，含 14MB b10 模型 + 两个 ~7MB .so）。

## 技术栈

- Kotlin + Jetpack Compose (Material 3)
- C++17 GTP 客户端（JNI via CMake）
- GNU Go 3.8（静态链接）+ KataGo v1.16.5+b10c128（交叉编译）
- 最低 SDK 24 · 目标 SDK 36 · arm64-v8a only

## 目录结构

```
app/src/main/
├── cpp/                    # GTP 客户端（C++ JNI）
│   ├── gtp_client.h/cpp    # GTP 协议引擎
│   ├── jni_bridge.cpp      # JNI 桥接
│   └── CMakeLists.txt
├── java/org/snailtrail/androidgo/
│   ├── MainActivity.kt     # 主界面 + 游戏控制
│   ├── HistoryScreen.kt    # 历史对局 + 复盘
│   ├── engine/
│   │   ├── GtpEngine.kt    # Kotlin GTP 封装
│   │   └── EngineManager.kt # 引擎生命周期
│   ├── game/
│   │   ├── GoGame.kt       # 游戏逻辑 + 数子
│   │   ├── SgfUtil.kt      # SGF 解析/生成
│   │   └── GoUtils.kt      # 共享常量和坐标转换
│   └── ui/
│       ├── NewGameDialog.kt # 新局设置
│       └── board/
│           └── GoBoard.kt  # Canvas 棋盘
├── jniLibs/arm64-v8a/      # 预编译引擎 .so
├── assets/engine/          # KataGo 配置和模型
└── res/
    ├── values/strings.xml  # 英文（缺省）
    ├── values-zh/strings.xml # 中文
    └── drawable/           # Font Awesome 图标
```

## License

MIT — 随便用，不限制。
