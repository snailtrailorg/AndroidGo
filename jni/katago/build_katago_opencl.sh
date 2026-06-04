#!/bin/bash
# Build libkatago_engine.so with OpenCL backend for Android
# Uses the CMakeLists.txt in jni/katago/
set -euo pipefail

NDK="${ANDROID_NDK_HOME:-${NDK:-}}"
[ -z "$NDK" ] && { echo "ERROR: set ANDROID_NDK_HOME" >&2; exit 1; }

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
BUILD_DIR="$PROJECT_ROOT/build/katago-opencl"
STRIP="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
OUTDIR="$PROJECT_ROOT/app/src/main/jniLibs/arm64-v8a"

echo "=== KataGo OpenCL Android Build ==="

rm -rf "$BUILD_DIR" && mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"

cmake \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-26 \
    -DCMAKE_BUILD_TYPE=Release \
    "$SCRIPT_DIR" 2>&1 | grep -v "Deprecation\|Call Stack\|CMake Warning" | tail -3

echo "Compiling..."
make -j$(nproc) 2>&1 | tail -3

echo ""
echo "=== Verify ==="
file "$OUTDIR/libkatago_engine.so" | head -1
nm -D "$OUTDIR/libkatago_engine.so" 2>/dev/null | grep katago_gtp_main && echo "katago_gtp_main: FOUND"
readelf -d "$OUTDIR/libkatago_engine.so" 2>/dev/null | grep NEEDED | head -5
$STRIP --strip-debug "$OUTDIR/libkatago_engine.so" 2>/dev/null || true
ls -lh "$OUTDIR/libkatago_engine.so"
echo "Build SUCCESS"
