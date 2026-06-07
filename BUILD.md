# AndroidGo Build Guide

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Android SDK | 36 (Android 15) | APK compilation, platform tools |
| Android NDK | r27+ | C/C++ cross-compilation for arm64-v8a |
| CMake | ≥ 3.18 | Build system (gpg_client, gnugo, katago) |
| Gradle | 8.x (via wrapper) | Kotlin/Java + APK packaging |
| JDK | 17 | Gradle runtime |
| Make | any | Build orchestration |
| filesystem | ~5 GB free | Build artifacts + source checkout + models (169 MB) |
| host OS | Linux x86_64 | llvm-strip path and nproc are Linux-specific |

## Quick Start

```bash
export ANDROID_NDK_HOME=$HOME/Android/Sdk/ndk/27.0.12077973

# Full build (engines + model + APK)
make all      # build all three engines, auto-deploy default model if needed
make apk      # assemble debug APK
make install  # install to connected device
make run      # launch app on device

# One-shot: all + APK + install
make all apk install
```

## Build Overview

```
make all                              gradlew assembleDebug
───────                              ────────────────────
model-ready                          Kotlin/Compose → classes.dex
  ├─ model (auto-deploy)             gtp_client (CMake) → libgtp_client.so
  │                                   └─ jni_bridge.cpp + gtp_client.cpp
gnugo (CMake) → libgnugo.so
  └─ jni/gnugo/                      打包 → APK
     ├─ engine/  (static libs)          ├─ lib/arm64-v8a/*.so
     ├─ patterns/
     ├─ sgf/                            └─ assets/katago_model.txt.gz
     ├─ utils/
     └─ interface/ (shared lib)

katago-cpu (CMake) → libkatago_cpu.so
  └─ jni/katago/cpp/ + Eigen3

katago-gpu (CMake) → libkatago_gpu.so
  └─ jni/katago/cpp/ + OpenCL dispatch
```

## Build Targets

All targets run from project root. `ANDROID_NDK_HOME` is required for engine builds.

### Top-level Makefile

```
make all          Build engines + auto-deploy default model
make menuconfig   Interactive KataGo model selection
make model        Deploy selected model to assets/
make gnugo        Build GNU Go engine only
make katago-cpu   Build KataGo CPU (Eigen) engine only
make katago-gpu   Build KataGo GPU (OpenCL) engine only
make clean        Remove all build artifacts (engines + gradle)
make verify       Check all engine .so exports

make apk          Assemble debug APK (gradle wrapper)
make install      Build APK + install via adb
make run          Launch app on device (adb)
```

### Per-engine Makefiles

```bash
make -C jni/gnugo       # GNU Go only
make -C jni/katago      # KataGo CPU + GPU + model-ready
```

## Model Management

Three models are committed in `jni/katago/models/`. The active model is selected
by `jni/katago/model.conf`.

### Interactive selection

```bash
make menuconfig
```

```
  === KataGo Model Selection ===

    1. b10c128           14M   500..5000
  > 2. b15c192           46M   300..3000
    3. b20c256          109M   200..2000

  Select [1-3]:
```

This updates `model.conf`, deploys the model to `assets/engine/`, and regenerates
`ModelConfig.kt` with the correct VISIT_LEVELS.

### Manual

```bash
# Edit model.conf, then:
make model

# Or override without changing model.conf:
make model MODEL=b20c256
```

### How it works

```
model.conf        →  MODEL=b15c192
models/<name>/
  *.txt.gz         →  copied to assets/engine/katago_model.txt.gz (aapt2 decompresses)
  params.conf      →  line 1 → VISIT_LEVELS in ModelConfig.kt
                      line 2 → display name (informational)
```

## Difficulty → maxVisits

Difficulty (1–10) maps to `maxVisits` differently per model because larger models
take more computation per visit. Goal: similar thinking time at each level.

| Level | b10c128 | b15c192 | b20c256 |
|-------|---------|---------|---------|
| 1 | 500 | 300 | 200 |
| 2 | 650 | 380 | 250 |
| 3 | 800 | 500 | 330 |
| 4 | 1,000 | 625 | 430 |
| 5 | 1,500 | 825 | 550 |
| 6 | 2,000 | 1,050 | 700 |
| 7 | 2,800 | 1,350 | 925 |
| 8 | 3,500 | 1,750 | 1,150 |
| 9 | 4,200 | 2,300 | 1,500 |
| 10 | 5,000 | 3,000 | 2,000 |

GNU Go uses its own internal level system (`--level 1..10`).

## Engine Detail

### libgnugo.so — GNU Go 3.8

| Aspect | Detail |
|--------|--------|
| Source | `jni/gnugo/` (full GNU Go 3.8, committed) |
| Bridge | `jni/gnugo/gnugo_bridge.c` — exports `gnugo_gtp_main` |
| Build | `make gnugo` → CMake ADD_SUBDIRECTORY |
| CMake | `jni/gnugo/CMakeLists.txt` + per-subdirectory |
| Output | `app/src/main/jniLibs/arm64-v8a/libgnugo.so` (~8.3 MB) |
| Link | `-Wl,-z,max-page-size=16384` (16 KB ELF alignment) |

**Build architecture:**
```
jni/gnugo/CMakeLists.txt     ← cmake_minimum_required + compile options + ADD_SUBDIRECTORY
  ├── engine/CMakeLists.txt   ← libengine.a + libboard.a (static)
  ├── patterns/CMakeLists.txt ← libpatterns.a (static, GLOB minus host tools)
  ├── sgf/CMakeLists.txt      ← libsgf.a (static)
  ├── utils/CMakeLists.txt    ← libutils.a (static)
  └── interface/CMakeLists.txt ← libgnugo.so (shared, links all static libs)
                                 main.c compiled with -Dmain=gnugo_main
                                 gnugo_bridge.c provides gnugo_gtp_main
```

**Key design decisions:**
- Static libraries isolate symbol conflicts (no `-Dmain=xxx` hacks needed).
- `-Wl,-z,muldefs` safety net for stray `main()` from utils/getopt*.c.
- `setvbuf(stdout, NULL, _IONBF, 0)` in bridge — pipe is not a TTY.
- `config.h` pre-generated and committed (no autotools needed).
- Patches applied directly to source (board.c, patterns.h).

### libkatago_cpu.so — KataGo Eigen CPU

| Aspect | Detail |
|--------|--------|
| Source | `jni/katago/cpp/` (KataGo v1.16.5) |
| Bridge | `jni/katago/katago_bridge.cpp` — exports `katago_gtp_main` |
| Build | `make katago-cpu` → CMake `-DKATAGO_BACKEND=EIGEN` |
| CMake | `jni/katago/CMakeLists.txt` (GLOB sources + CONFIGURE_DEPENDS) |
| Output | `app/src/main/jniLibs/arm64-v8a/libkatago_cpu.so` (~7.2 MB) |
| Backend | Eigen3 headers (`jni/katago/cpp/external/eigen3/`, MIT) |
| Link | `-Wl,-z,max-page-size=16384` |

### libkatago_gpu.so — KataGo OpenCL GPU

| Aspect | Detail |
|--------|--------|
| Source | `jni/katago/cpp/` (KataGo v1.16.5) |
| Bridge | `jni/katago/katago_bridge.cpp` — exports `katago_gtp_main` |
| Dispatch | `jni/opencl/dispatch/opencl_dispatch.cpp` — runtime dlopen/dlsym 25 OpenCL functions |
| Build | `make katago-gpu` → CMake `-DKATAGO_BACKEND=OPENCL` |
| Output | `app/src/main/jniLibs/arm64-v8a/libkatago_gpu.so` (~8.4 MB) |
| Backend | OpenCL 1.2 (GPU, via dispatch → vendor libOpenCL.so) |
| Headers | Khronos OpenCL 1.2 (`jni/opencl/include/CL/`, MIT) |
| Link | `-Wl,-z,max-page-size=16384`, `--allow-shlib-undefined` (OpenCL resolved at runtime) |

**AndroidManifest entries (Android 12+):**
```xml
<uses-native-library android:name="libOpenCL.so" android:required="false" />
<uses-native-library android:name="libOpenCL-pixel.so" android:required="false" />
<uses-native-library android:name="libPVROCL.so" android:required="false" />
```

### libgtp_client.so — JNI Bridge

| Aspect | Detail |
|--------|--------|
| Source | `app/src/main/cpp/gtp_client.cpp`, `jni_bridge.cpp` |
| Build | Gradle → CMake (integrated, no manual step) |
| CMake | `app/src/main/cpp/CMakeLists.txt` |
| Output | Built into APK as `lib/arm64-v8a/libgtp_client.so` |
| Link | `-Wl,-z,max-page-size=16384` |

## Directory Map

```
AndroidGo/
├── Makefile                            ← Top-level build orchestration
├── BUILD.md
├── app/
│   ├── build.gradle.kts                ← compileSdk=36, minSdk=26, arm64-v8a only
│   └── src/main/
│       ├── AndroidManifest.xml         ← uses-native-library for OpenCL
│       ├── assets/engine/
│       │   ├── katago.cfg              ← KataGo GTP config
│       │   └── katago_model.txt.gz     ← Deployed model (generated, gitignored)
│       ├── cpp/
│       │   ├── CMakeLists.txt          ← Gradle CMake (libgtp_client.so)
│       │   ├── gtp_client.cpp          ← GTP client: pipe, timeout, interrupt
│       │   ├── gtp_client.h
│       │   └── jni_bridge.cpp          ← JNI → GtpClient (opaque long ptr)
│       ├── java/.../androidgo/
│       │   ├── MainActivity.kt         ← Compose main screen + game loop
│       │   ├── MainScreen.kt
│       │   ├── HistoryScreen.kt        ← SGF history + review
│       │   ├── engine/
│       │   │   ├── EngineManager.kt    ← Engine lifecycle, model extraction
│       │   │   ├── GtpEngine.kt        ← JNI native methods
│       │   │   └── ModelConfig.kt      ← MODEL_NAME + VISIT_LEVELS (generated)
│       │   ├── game/
│       │   │   ├── GoGame.kt           ← Rules, stone placement, scoring
│       │   │   ├── GoUtils.kt          ← PrefKeys, coordinate conversion
│       │   │   └── SgfUtil.kt          ← SGF read/write
│       │   └── ui/
│       │       ├── NewGameDialog.kt    ← Board, handicap, engine, backend
│       │       ├── GameInfoBar.kt
│       │       └── board/GoBoard.kt    ← Canvas rendering
│       ├── jniLibs/arm64-v8a/          ← Engine .so files (build output)
│       └── res/                        ← String resources (en + zh)
├── jni/
│   ├── gnugo/                          ← GNU Go 3.8 source + build
│   │   ├── Makefile                    ← cmake wrapper (33 lines)
│   │   ├── CMakeLists.txt              ← Top-level: ADD_SUBDIRECTORY
│   │   ├── gnugo_bridge.c              ← dlopen entry point: gnugo_gtp_main
│   │   ├── config.h                    ← Pre-generated (autotools configure)
│   │   ├── engine/                     ← 38 .c files
│   │   │   ├── CMakeLists.txt          ← libengine.a + libboard.a
│   │   │   └── board.c                 ← countlib assertion fix applied
│   │   ├── patterns/
│   │   │   ├── CMakeLists.txt          ← libpatterns.a (GLOB, exclude host tools)
│   │   │   └── patterns.h              ← GRID_OPT fix applied
│   │   ├── sgf/CMakeLists.txt          ← libsgf.a
│   │   ├── utils/CMakeLists.txt        ← libutils.a
│   │   └── interface/CMakeLists.txt    ← libgnugo.so (shared)
│   ├── katago/
│   │   ├── Makefile                    ← cmake wrapper + model management
│   │   ├── CMakeLists.txt              ← GLOB sources + CONFIGURE_DEPENDS
│   │   ├── katago_bridge.cpp           ← dlopen entry point: katago_gtp_main
│   │   ├── model.conf                  ← Active model selection
│   │   ├── models/                     ← Neural network models (committed)
│   │   │   ├── b10c128/   (14 MB)
│   │   │   ├── b15c192/   (46 MB)
│   │   │   └── b20c256/  (109 MB)
│   │   └── cpp/                        ← KataGo v1.16.5 full source
│   └── opencl/
│       ├── dispatch/
│       │   ├── opencl_dispatch.h       ← OpenCLDispatch struct + OCL() macro
│       │   └── opencl_dispatch.cpp     ← dlopen/dlsym 25 OpenCL 1.2 functions
│       └── include/CL/                 ← Khronos OpenCL 1.2 headers (MIT)
└── gradlew                             ← Gradle wrapper
```

## 16 KB Page Alignment

Android 15+ devices use 16 KB memory pages (was 4 KB). All `.so` files must have
ELF LOAD segment `p_align ≥ 16384 (0x4000)`.

| CMakeLists | Flag |
|------------|------|
| `app/src/main/cpp/CMakeLists.txt` | `-Wl,-z,max-page-size=16384` |
| `jni/gnugo/interface/CMakeLists.txt` | `-Wl,-z,max-page-size=16384` |
| `jni/katago/CMakeLists.txt` | `-Wl,-z,max-page-size=16384` |

Verify:
```bash
readelf -lW app/src/main/jniLibs/arm64-v8a/*.so | grep -E "File:|LOAD" | grep -A1 "\.so"
# All LOAD align= values should be 0x4000
```

## Environment Variables

| Variable | Required | Purpose |
|----------|----------|---------|
| `ANDROID_NDK_HOME` | For `make all` | NDK path (r27+) |
| `ANDROID_HOME` | For gradlew | Android SDK root |

## Common Workflows

### First build from clone

```bash
export ANDROID_NDK_HOME=$HOME/Android/Sdk/ndk/27.0.12077973
make menuconfig     # optional: choose model (default b15c192)
make all apk        # build engines + APK
make install run    # install and launch
```

### Switch model

```bash
make menuconfig     # select model interactively
make apk install    # rebuild APK with new model
```

### Code change rebuild

```bash
# Kotlin/Compose only
make apk install

# GNU Go source changed
make gnugo apk install

# KataGo source changed
make katago-cpu apk install

# Model changed
make model apk install
```

### Full rebuild from scratch

```bash
make clean
make all apk install
```
