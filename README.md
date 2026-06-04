# AndroidGo

Android 围棋应用。Kotlin + Jetpack Compose (Material 3)，三引擎支持，中国规则数子法。

## 功能

- **三引擎**：GNU Go 3.8、KataGo v1.16.5 CPU (Eigen)、KataGo v1.16.5 GPU (OpenCL)
- **CPU/GPU 后端选择**：KataGo 支持 Eigen CPU 和 OpenCL GPU 两种后端，开局对话框切换
- **GTP 协议完整实现**：C++ JNI 桥接，dlopen + pthread 同进程运行，双引擎复用
- **中国规则数子法**：`(黑-白)/2 - 贴子 + 让子还子`，引擎死子判定
- **SGF 完整读写**：对局保存、历史列表、棋盘复盘、delta replay（增量进退）
- **让子 0-9 · 棋盘 9/13/19 · 难度 1-10 · 贴目可配**
- **中英双语**：跟随系统语言
- **Material 3 UI**：三页（对弈 · 历史 · 复盘），RoleChip 统一风格选择器

## 屏幕截图

<p align="center">
  <em>（待补充）</em>
</p>

## 构建

完整构建文档见 [BUILD.md](BUILD.md)。

### 快速开始

```bash
# 环境
export ANDROID_HOME=$HOME/Android/Sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/27.0.12077973

# 构建引擎 .so
make -C jni/katago all       # KataGo CPU + GPU
# 或单独构建：
make -C jni/katago katago-cpu
make -C jni/katago katago-gpu
make -C jni/katago gnugo      # GNU Go

# 构建 APK
./gradlew assembleDebug
```

### 构建产物

| 产物 | 大小 | 说明 |
|------|------|------|
| `app-debug.apk` | ~55 MB | Debug APK，含全部资源 |
| `libgnugo.so` | 8.9 MB | GNU Go 3.8 |
| `libkatago_cpu.so` | 7.2 MB | KataGo Eigen CPU |
| `libkatago_gpu.so` | 8.4 MB | KataGo OpenCL GPU |
| `libgtp_client.so` | ~50 KB | JNI 桥接层（Gradle 自动编译） |

### 构建要求

| 工具 | 版本 |
|------|------|
| Android SDK | 36 (API 36) |
| Android NDK | r27+ |
| CMake | ≥ 3.18 |
| Gradle | 8.x (wrapper) |
| JDK | 17 |
| Make | any |
| 宿主机 | Linux x86_64 |

## 技术栈

```
Kotlin / Compose (Material 3)
    │
    ▼
GtpEngine (JNI) ──→ libgtp_client.so
    │                    │
    │              dlopen + dlsym
    │                    │
    ├── libgnugo.so       (GNU Go 3.8)
    ├── libkatago_cpu.so  (KataGo v1.16.5 + Eigen3)
    └── libkatago_gpu.so  (KataGo v1.16.5 + OpenCL dispatch)
                              │
                        dlopen/dlsym (runtime)
                              │
                    vendor libOpenCL.so
                    (Qualcomm Adreno / ARM Mali / PowerVR)
```

- **语言**：Kotlin + C++17
- **UI**：Jetpack Compose (Material 3)
- **引擎通信**：GTP 协议 via POSIX pipe + pthread
- **JNI 桥接**：CMake（`app/src/main/cpp/CMakeLists.txt`）
- **引擎编译**：CMake + NDK 交叉编译（`jni/katago/CMakeLists.txt`）
- **最低 SDK**：26 (Android 8.0) · **目标 SDK**：36 (Android 15)
- **架构**：arm64-v8a only
- **引擎模型**：KataGo b10c128-s101M（37 MB）
- **许可证**：MIT

## GPU 加速

KataGo GPU 后端通过 **runtime dispatch table** 实现：

1. 编译时：不链接 `libOpenCL.so`（NDK 不含此库）
2. 运行时：`opencl_dispatch.cpp` → `dlopen("libOpenCL.so")` → `dlsym` 解析 25 个 OpenCL 1.2 函数
3. 所有 `clXxx()` 调用通过 `OCL(Function, args)` 宏重定向到函数指针

**兼容设备**：

| GPU | 库名 | 状态 |
|-----|------|------|
| Qualcomm Adreno | `libOpenCL.so` | ✅ Samsung S23 验证 |
| ARM Mali | `libGLES_mali.so` | 未测试 |
| PowerVR | `libPVROCL.so` | 未测试 |
| Google Pixel | `libOpenCL-pixel.so` | 未测试 |

设备无 OpenCL 或加载失败时，应用仍可通过 KataGo CPU (Eigen) 或 GNU Go 正常对弈。

## 目录结构

```
AndroidGo/
├── BUILD.md                    # 完整构建文档
├── README.md                   # 本文件
├── app/
│   ├── build.gradle.kts        # Gradle 配置
│   └── src/main/
│       ├── AndroidManifest.xml # uses-native-library for OpenCL
│       ├── cpp/                # GTP 客户端 (C++ JNI)
│       │   ├── gtp_client.cpp  # GNU Go / KataGo 共用
│       │   ├── gtp_client.h
│       │   ├── jni_bridge.cpp  # JNI 函数表
│       │   └── CMakeLists.txt  # Gradle 集成的 CMake
│       ├── java/org/snailtrail/androidgo/
│       │   ├── MainActivity.kt # 主界面 + 游戏循环
│       │   ├── MainScreen.kt   # BottomBar, ScoreCard, AboutDialog
│       │   ├── HistoryScreen.kt # 历史对局 + 复盘
│       │   ├── engine/
│       │   │   ├── EngineManager.kt # 引擎生命周期、难度映射、模型提取
│       │   │   └── GtpEngine.kt    # Kotlin JNI 封装
│       │   ├── game/
│       │   │   ├── GoGame.kt   # 规则引擎 + 中国数子法
│       │   │   ├── GoUtils.kt  # PrefKeys, 坐标转换
│       │   │   └── SgfUtil.kt  # SGF 读写 (atomic write)
│       │   └── ui/
│       │       ├── NewGameDialog.kt # 新局对话框 (RoleChip 风格)
│       │       ├── GameInfoBar.kt   # 对局信息栏 (玩家名/手数/状态)
│       │       ├── TitleBar.kt      # 菜单栏
│       │       └── board/GoBoard.kt # Canvas 棋盘 + 领地标记
│       ├── assets/engine/      # katago.cfg + katago_model.txt
│       ├── jniLibs/arm64-v8a/  # 预编译引擎 .so
│       │   ├── libgnugo.so
│       │   ├── libkatago_cpu.so
│       │   ├── libkatago_gpu.so
│       │   └── libc++_shared.so
│       └── res/                # 字符串资源 (values + values-zh)
├── jni/
│   ├── gnugo/                  # GNU Go 3.8 源码 + Android 补丁
│   │   ├── build_gnugo.sh
│   │   ├── CMakeLists.txt
│   │   ├── gnugo_bridge.c      # dlopen 入口 (gnugo_gtp_main)
│   │   └── engine/             # GNU Go 引擎源码
│   ├── katago/
│   │   ├── Makefile            # 引擎构建入口
│   │   ├── CMakeLists.txt      # CMake 配置 (双后端)
│   │   ├── katago_bridge.cpp   # dlopen 入口 (katago_gtp_main + exit/abort 拦截)
│   │   ├── katago.ver          # Linker version script
│   │   └── cpp/                # KataGo v1.16.5 源码
│   │       └── external/eigen3/ # Eigen3 3.4.0 headers (MIT)
│   └── opencl/
│       ├── dispatch/           # OpenCL runtime dispatch table
│       │   ├── opencl_dispatch.h   # OCL() 宏 + 函数指针结构体
│       │   └── opencl_dispatch.cpp # dlopen/dlsym 25 函数
│       └── include/CL/         # Khronos OpenCL 1.2 headers (MIT)
└── engine_tests/               # Python GTP 测试框架
```

## License

MIT
