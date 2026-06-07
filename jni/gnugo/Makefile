# Build libgnugo.so from the committed GNU Go 3.8 source.
# Uses CMake (ADD_SUBDIRECTORY → static libs → shared library).
#
# Prerequisites: ANDROID_NDK_HOME, cmake

NDK     ?= $(ANDROID_NDK_HOME)
BUILD   := $(CURDIR)/../../build/gnugo
OUTDIR  := $(CURDIR)/../../app/src/main/jniLibs/arm64-v8a
STRIP   := $(NDK)/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip

.PHONY: all clean

all: $(OUTDIR)/libgnugo.so

$(OUTDIR)/libgnugo.so:
	@echo "=== GNU Go 3.8 ==="
	@mkdir -p $(BUILD)
	@test -f $(BUILD)/CMakeCache.txt || \
	    cmake -S $(CURDIR) -B $(BUILD) \
	    -DCMAKE_TOOLCHAIN_FILE=$(NDK)/build/cmake/android.toolchain.cmake \
	    -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 \
	    -DCMAKE_BUILD_TYPE=Release 2>&1 | grep -v "Deprecation\|cmake_minimum" | tail -2
	@cmake --build $(BUILD) -j$$(nproc) 2>&1 | tail -2
	@mkdir -p $(OUTDIR)
	@cp $(BUILD)/interface/libgnugo.so $@
	@$(STRIP) --strip-debug $@ 2>/dev/null || true
	@ls -lh $@

clean:
	rm -rf $(BUILD) $(OUTDIR)/libgnugo.so

verify:
	@printf "%-25s %-10s %s\n" "libgnugo.so" \
	    "$$(ls -lh $(OUTDIR)/libgnugo.so 2>/dev/null | awk '{print $$5}')" \
	    "$$(nm -D $(OUTDIR)/libgnugo.so 2>/dev/null | grep -q gtp_main && echo OK || echo MISSING)"
