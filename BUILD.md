# AndroidGo Build Guide

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Android SDK | 36 (Android 15) | APK compilation, platform tools |
| Android NDK | r27+ | C/C++ cross-compilation for arm64-v8a |
| CMake | ≥ 3.18 | Build system (engine + JNI bridge) |
| Gradle | 8.x (via wrapper) | Kotlin/Java + APK packaging |
| JDK | 17 | Gradle runtime |
| Make | any | Top-level build orchestration |
| filesystem | ~5 GB free | Build artifacts + source checkout |
| host OS | Linux x86_64 | Makefile llvm-strip path and nproc are Linux-specific |

## Build Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    gradlew assembleDebug                    │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              Kotlin / Compose sources                 │  │
│  │  MainActivity  HistoryScreen  NewGameDialog  ...     │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌───────────────────────┐  ┌───────────────────────────┐  │
│  │   libgtp_client.so    │  │   Pre-built engine .so     │  │
│  │   (CMake → JNI桥接)    │  │   (jniLibs/arm64-v8a/)    │  │
│  │                       │  │                           │  │
│  │   gtp_client.cpp      │  │   libgnugo.so             │  │
│  │   jni_bridge.cpp      │  │   libkatago_cpu.so        │  │
│  │   gtp_client.h        │  │   libkatago_gpu.so        │  │
│  └───────────────────────┘  └───────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│               Engine .so Build (jni/katago/Makefile)        │
│                                                             │
│  make katago-cpu  ──→  CMake + NDK  ──→  libkatago_cpu.so  │
│  make katago-gpu  ──→  CMake + NDK  ──→  libkatago_gpu.so  │
│  make gnugo       ──→  build_gnugo.sh ──→  libgnugo.so     │
└─────────────────────────────────────────────────────────────┘
```

## Quick Start

```bash
# 1. Set environment
export ANDROID_HOME=$HOME/Android/Sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/27.0.12077973
export PATH=$ANDROID_HOME/platform-tools:$PATH

# 2. Build all native engines (one-time or when C++ changes)
make -C jni/katago all

# 3. Build and install APK
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Component Build Detail

### 1. libgnugo.so — GNU Go 3.8 Engine

| Aspect | Detail |
|--------|--------|
| Source | `jni/gnugo/` (full GNU Go 3.8 source + Android patches) |
| Bridge | `jni/gnugo/gnugo_bridge.c` — exports `gnugo_gtp_main` |
| Build | `make gnugo` → `jni/gnugo/build_gnugo.sh` |
| Output | `app/src/main/jniLibs/arm64-v8a/libgnugo.so` (~8.9 MB) |
| Backend | Native C (no neural net) |
| Notes | Pre-patched: countlib assertion fix, GRID_OPT fix, exit→pthread_exit |

**Build steps (build_gnugo.sh):**
1. Extract GNU Go 3.8 tarball (source is committed, skip if present)
2. `./configure --without-curses --without-readline --disable-socket-support`
3. `make` host tools → generate pattern `.c` files (mkpat, uncompress_fuseki, mkeyes)
4. Apply patches: `0001-countlib-assertion-fix.patch`, `0002-grid-opt-config.patch`
5. Copy `gnugo_bridge.c`
6. CMake + NDK cross-compile for arm64-v8a (API 26)
7. `llvm-strip --strip-debug`

### 2. libkatago_cpu.so — KataGo Eigen CPU

| Aspect | Detail |
|--------|--------|
| Source | `jni/katago/cpp/` (KataGo v1.16.5) |
| Bridge | `jni/katago/katago_bridge.cpp` — exports `katago_gtp_main` |
| Build | `make katago-cpu` → CMake with `KATAGO_BACKEND=EIGEN` |
| Output | `app/src/main/jniLibs/arm64-v8a/libkatago_cpu.so` (~7.2 MB) |
| Backend | Eigen3 (CPU matrix ops, no GPU) |
| Dependencies | Eigen3 headers (`jni/katago/cpp/external/eigen3/`, MIT) |

**CMakeLists.txt (`jni/katago/CMakeLists.txt`):**
- GLOB collects sources from: `core/`, `game/`, `search/`, `dataio/`, `book/`, `program/`, `neuralnet/`, `command/`, `tests/`
- Filter: exclude `cuda`, `trt`, `metal`, `opencl`, `dummy`, `test` patterns
- Bridge: `katago_bridge.cpp` (always included)
- Dispatch: NOT included
- Defines: `USE_EIGEN_BACKEND`, `__ANDROID__`, `NO_LIBZIP`, `NO_GIT_REVISION`, `BYTE_ORDER=1234`
- Includes: Eigen3 headers
- Link: `dl`, `log`, `z`, `m`
- Export: `katago_gtp_main` via `--export-dynamic`

### 3. libkatago_gpu.so — KataGo OpenCL GPU

| Aspect | Detail |
|--------|--------|
| Source | `jni/katago/cpp/` (KataGo v1.16.5) |
| Bridge | `jni/katago/katago_bridge.cpp` — exports `katago_gtp_main` |
| Dispatch | `jni/opencl/dispatch/opencl_dispatch.cpp` — runtime dlopen/dlsym |
| Build | `make katago-gpu` → CMake with `KATAGO_BACKEND=OPENCL` |
| Output | `app/src/main/jniLibs/arm64-v8a/libkatago_gpu.so` (~8.4 MB) |
| Backend | OpenCL 1.2 (GPU, via dispatch → vendor libOpenCL.so) |
| Dependencies | Khronos OpenCL headers (`jni/opencl/include/CL/`, MIT) |

**Same as CPU, plus:**
- Dispatch: `opencl_dispatch.cpp` (25 OpenCL 1.2 functions via dlsym)
- Defines: `USE_OPENCL_BACKEND`, `CL_TARGET_OPENCL_VERSION=120`, `CL_USE_DEPRECATED_OPENCL_1_2_APIS`
- Includes: OpenCL headers + dispatch headers
- NOT linked against `libOpenCL.so` (resolved at runtime)

**AndroidManifest entries (for GPU access, Android 12+):**
```xml
<uses-native-library android:name="libOpenCL.so" android:required="false" />
<uses-native-library android:name="libOpenCL-pixel.so" android:required="false" />
<uses-native-library android:name="libGLES_mali.so" android:required="false" />
<uses-native-library android:name="libPVROCL.so" android:required="false" />
```

### 4. libgtp_client.so — JNI Bridge

| Aspect | Detail |
|--------|--------|
| Source | `app/src/main/cpp/gtp_client.cpp`, `jni_bridge.cpp`, `gtp_client.h` |
| Build | Gradle → CMake (integrated, no manual step) |
| Output | Built into APK as `lib/arm64-v8a/libgtp_client.so` |
| Role | JNI wrapper: `dlopen` engine .so → `dlsym` gtp_main → pipe + pthread |

### 5. Android APK

| Aspect | Detail |
|--------|--------|
| Source | `app/src/main/java/`, `app/src/main/res/`, `app/src/main/assets/` |
| Build | `./gradlew assembleDebug` |
| Output | `app/build/outputs/apk/debug/app-debug.apk` |
| Dependencies | Jetpack Compose, Material3, Kotlin Coroutines, AndroidX |

## Directory Map

```
AndroidGo/
├── BUILD.md                          ← This file
├── app/
│   ├── build.gradle.kts              ← Gradle config (minSdk=26, targetSdk=36, arm64-only)
│   └── src/main/
│       ├── AndroidManifest.xml       ← uses-native-library for OpenCL
│       ├── cpp/                      ← JNI bridge: gtp_client.cpp + jni_bridge.cpp
│       │   └── CMakeLists.txt        ← Gradle-invoked CMake (libgtp_client.so)
│       ├── java/.../androidgo/
│       │   ├── MainActivity.kt       ← Compose main screen + game loop
│       │   ├── MainScreen.kt         ← BottomBar, ScoreCard, AboutDialog
│       │   ├── HistoryScreen.kt      ← SGF history + review
│       │   ├── engine/
│       │   │   ├── EngineManager.kt  ← Engine lifecycle, .so loading, difficulty→maxVisits
│       │   │   └── GtpEngine.kt      ← JNI native methods
│       │   ├── game/
│       │   │   ├── GoGame.kt         ← Rules, stone placement, territory scoring
│       │   │   ├── GoUtils.kt        ← PrefKeys, coordinate conversion
│       │   │   └── SgfUtil.kt        ← SGF read/write
│       │   └── ui/
│       │       ├── NewGameDialog.kt  ← Board, handicap, engine, backend, difficulty
│       │       ├── GameInfoBar.kt    ← Player names, move count, turn indicator
│       │       ├── TitleBar.kt       ← Menu bar
│       │       └── board/            ← GoBoard canvas rendering
│       ├── assets/engine/            ← katago.cfg, katago_model.txt (37 MB)
│       ├── jniLibs/arm64-v8a/        ← Pre-built engine .so files
│       │   ├── libgnugo.so
│       │   ├── libkatago_cpu.so
│       │   ├── libkatago_gpu.so
│       │   └── libc++_shared.so      ← NDK C++ runtime
│       └── res/                      ← String resources (en + zh)
├── jni/
│   ├── gnugo/                        ← GNU Go 3.8 full source + Android patches
│   │   ├── build_gnugo.sh            ← Build script
│   │   ├── CMakeLists.txt            ← CMake config (ADD_EXECUTABLE → shared lib)
│   │   ├── gnugo_bridge.c            ← dlopen entry point: gnugo_gtp_main
│   │   ├── 0001-countlib-assertion-fix.patch
│   │   └── 0002-grid-opt-config.patch
│   ├── katago/
│   │   ├── Makefile                  ← Top-level build: make katago-cpu / katago-gpu / gnugo / all
│   │   ├── CMakeLists.txt            ← CMake config (KATAGO_BACKEND → defines + sources)
│   │   ├── katago_bridge.cpp         ← dlopen entry point: katago_gtp_main + __wrap_exit/abort
│   │   ├── katago.ver                ← Linker version script (force-export katago_gtp_main)
│   │   └── cpp/                      ← KataGo v1.16.5 full source
│   │       ├── external/eigen3/      ← Eigen3 3.4.0 headers (MIT, header-only)
│   │       ├── neuralnet/openclbackend.cpp  ← OpenCL backend (OCL() macro redirect)
│   │       ├── neuralnet/openclhelpers.cpp  ← Device query fallback (Qualcomm fix)
│   │       ├── neuralnet/openclincludes.h   ← Conditional OpenCL include
│   │       ├── neuralnet/openclkernels.cpp   ← Embedded OpenCL kernel strings
│   │       └── neuralnet/opencltuner.cpp     ← Auto tuning + cache
│   └── opencl/
│       ├── dispatch/
│       │   ├── opencl_dispatch.h      ← OpenCLDispatch struct + OCL() macro
│       │   └── opencl_dispatch.cpp    ← dlopen/dlsym 25 OpenCL 1.2 functions
│       └── include/CL/               ← Khronos OpenCL 1.2 headers (MIT)
├── engine_tests/                     ← GTP test harness (Python)
└── gnugo_patches/                    ← (historical, now at jni/gnugo/)
```

## Build Targets

```
make -C jni/katago katago-cpu    # KataGo Eigen CPU → libkatago_cpu.so
make -C jni/katago katago-gpu    # KataGo OpenCL GPU → libkatago_gpu.so
make -C jni/katago gnugo         # GNU Go → libgnugo.so
make -C jni/katago all           # katago-cpu + katago-gpu
make -C jni/katago clean         # Remove build artifacts
make -C jni/katago verify        # Check all .so exports
./gradlew assembleDebug          # Build APK (includes libgtp_client.so)
./gradlew installDebug           # Build + install (requires adb device)
```

## Configuration

### Engine Parameters (via `-override-config` CLI)

| Parameter | Default | Notes |
|-----------|---------|-------|
| `maxVisits` | 500–5000 | Mapped from difficulty level 1–10 |
| `maxTime` | 100s | Soft cap, engine returns best move at timeout |
| `numSearchThreads` | 1 | MCTS parallel threads |
| `nnCacheSizePowerOfTwo` | 14 | Neural net cache (2^14 entries) |

### KataGo Model

- File: `app/src/main/assets/engine/katago_model.txt` (37 MB)
- Model: b10c128-s101M (v1.16.5)
- Extracted at runtime to `context.filesDir/engine/model/`

## Environment Variables

| Variable | Required | Default | Purpose |
|----------|----------|---------|---------|
| `ANDROID_HOME` | For Gradle | `$HOME/Android/Sdk` | Android SDK root |
| `ANDROID_NDK_HOME` | For Makefile | — | NDK path (r27+) |
| `JAVA_HOME` | For Gradle | system default | JDK 17 |
