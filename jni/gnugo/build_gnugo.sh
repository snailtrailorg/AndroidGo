#!/bin/bash
# Build libgnugo_engine.so for Android
# Usage: ./build_gnugo.sh [--clean]
#
# Prerequisites:
#   - ANDROID_NDK_HOME or NDK environment variable pointing to NDK r27+
#   - GNU Go 3.8 tarball at /tmp/gnugo-3.8.tar.gz (or set GNUTO_TARBALL)
#   - host gcc, cmake, make

set -euo pipefail

NDK="${ANDROID_NDK_HOME:-${NDK:-}}"
if [ -z "$NDK" ]; then
    echo "ERROR: set ANDROID_NDK_HOME or NDK to your NDK path" >&2
    exit 1
fi

GNUTO_TARBALL="${GNUTO_TARBALL:-/tmp/gnugo-3.8.tar.gz}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_JNILIBS="$(cd "$SCRIPT_DIR/.." && pwd)/app/src/main/jniLibs/arm64-v8a"

WORKDIR="/tmp/gnugo-android-build"

# --- clean ---
if [ "${1:-}" = "--clean" ]; then
    rm -rf "$WORKDIR"
    echo "Cleaned $WORKDIR"
    exit 0
fi

echo "=== GNU Go Android Build ==="
echo "NDK: $NDK"
echo "Tarball: $GNUTO_TARBALL"
echo "Output: $PROJECT_JNILIBS/libgnugo_engine.so"
echo ""

# --- Step 1: extract + configure (generates pattern .c files) ---
if [ ! -d "$WORKDIR" ]; then
    echo "--- Step 1: extract + autotools configure ---"
    mkdir -p "$WORKDIR"
    tar xzf "$GNUTO_TARBALL" -C "$WORKDIR" --strip-components=1

    cd "$WORKDIR"
    ./configure --without-curses --without-readline --disable-socket-support \
        CFLAGS="-std=gnu89 -w -fcommon" 2>&1 | tail -3
    # Build host tools + generate patterns (runs mkpat, uncompress_fuseki, mkeyes)
    make -j$(nproc) 2>&1 | tail -5
    echo "Pattern files generated."
else
    echo "--- Step 1: using existing build dir ---"
fi

# --- Step 2: apply patches ---
echo ""
echo "--- Step 2: apply patches ---"
cd "$WORKDIR"

# Patch countlib assertion
if grep -q 'ASSERT1(IS_STONE(board\[str\]), str)' engine/board.c 2>/dev/null; then
    patch -p1 < "$SCRIPT_DIR/0001-countlib-assertion-fix.patch"
    echo "  Applied countlib fix"
else
    echo "  countlib fix already applied"
fi

# Patch GRID_OPT
if grep -q '^#define GRID_OPT 0$' patterns/patterns.h 2>/dev/null; then
    patch -p1 < "$SCRIPT_DIR/0002-grid-opt-config.patch"
    echo "  Applied GRID_OPT fix"
else
    echo "  GRID_OPT fix already applied"
fi

# Copy bridge
cp "$SCRIPT_DIR/gnugo_bridge.c" .
echo "  Bridge file copied"

# --- Step 3: cmake + make for Android ---
echo ""
echo "--- Step 3: NDK cross-compile ---"

BUILD_DIR="$WORKDIR/build_android"
rm -rf "$BUILD_DIR" && mkdir -p "$BUILD_DIR" && cd "$BUILD_DIR"

# Need to use our CMakeLists.txt, not the source one
cp "$SCRIPT_DIR/CMakeLists.txt" "$WORKDIR/CMakeLists.txt"

cmake -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 \
    -DCMAKE_BUILD_TYPE=Release "$WORKDIR" 2>&1 | tail -3

make -j$(nproc) 2>&1 | tail -5

# --- Step 4: strip + copy to project ---
echo ""
echo "--- Step 4: install ---"
"$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" --strip-debug libgnugo.so
mkdir -p "$PROJECT_JNILIBS"
cp libgnugo.so "$PROJECT_JNILIBS/libgnugo_engine.so"
ls -lh "$PROJECT_JNILIBS/libgnugo_engine.so"

# Verify symbol
if readelf --dyn-syms "$PROJECT_JNILIBS/libgnugo_engine.so" 2>/dev/null | grep -q gnugo_gtp_main; then
    echo "Build SUCCESS — gnugo_gtp_main found"
else
    echo "Build FAILED — gnugo_gtp_main NOT FOUND" >&2
    exit 1
fi
