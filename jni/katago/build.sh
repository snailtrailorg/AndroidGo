#!/bin/bash
# Build KataGo engine for Android
# Usage: ./build.sh [opencl|cpu|all]
set -euo pipefail

NDK="${ANDROID_NDK_HOME:-${NDK:-}}"
[ -z "$NDK" ] && { echo "ERROR: set ANDROID_NDK_HOME" >&2; exit 1; }

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
OUTDIR="$PROJECT_ROOT/app/src/main/jniLibs/arm64-v8a"
STRIP="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
BACKEND="${1:-opencl}"

echo "=== KataGo Android Build ==="

build_one() {
    local cmake_backend="$1" label="$2" out_name="$3"
    local bd="$PROJECT_ROOT/build/katago-$label"

    echo ""
    echo "--- $label ($cmake_backend) ---"
    rm -rf "$bd" && mkdir -p "$bd"

    cmake -S "$SCRIPT_DIR" -B "$bd" \
        -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI=arm64-v8a \
        -DANDROID_PLATFORM=android-26 \
        -DCMAKE_BUILD_TYPE=Release \
        -DKATAGO_BACKEND="$cmake_backend" \
        -DCMAKE_SHARED_LINKER_FLAGS="-Wl,--export-dynamic -Wl,--allow-shlib-undefined -Wl,--unresolved-symbols=ignore-in-shared-libs" \
        2>&1 | grep -v "Deprecation\|Call Stack\|cmake_minimum" | tail -2

    cmake --build "$bd" -j$(nproc) 2>&1 | tail -2

    $STRIP --strip-debug "$OUTDIR/$out_name" 2>/dev/null || true
    ls -lh "$OUTDIR/$out_name"
}

case "$BACKEND" in
    opencl) build_one OPENCL opencl libkatago_engine.so ;;
    cpu)    build_one EIGEN  cpu    libkatago_engine_cpu.so ;;
    all)
        build_one EIGEN  cpu    libkatago_engine_cpu.so
        build_one OPENCL opencl libkatago_engine.so
        ;;
    *) echo "Usage: $0 [opencl|cpu|all]"; exit 1 ;;
esac

echo ""
echo "=== Verify ==="
for f in libkatago_engine.so libkatago_engine_cpu.so; do
    if [ -f "$OUTDIR/$f" ]; then
        ok=$(nm -D "$OUTDIR/$f" 2>/dev/null | grep -c katago_gtp_main || true)
        echo "  $f: $([ $ok -gt 0 ] && echo OK || echo MISSING)"
    fi
done
echo "Done."
