# AndroidGo — top-level build orchestration.
#
#   make menuconfig    Interactive KataGo model selection
#   make engines       Build all three engines + deploy default model
#   make all           engines + apk (full build)
#   make apk           Assemble debug APK (gradle)
#   make install       Build APK + install via adb
#   make run           Launch app on device
#   make gnugo         Build GNU Go engine only
#   make katago-cpu    Build KataGo CPU (Eigen) engine only
#   make katago-gpu    Build KataGo GPU (OpenCL) engine only
#   make model         Deploy selected model to assets/
#   make bundle        Build release AAB (gradle bundleRelease)
#   make clean         Remove all build artifacts
#   make verify        Check all engine .so exports

NDK     ?= $(ANDROID_NDK_HOME)
GRADLEW := $(CURDIR)/gradlew
APK     := $(CURDIR)/app/build/outputs/apk/debug/app-debug.apk
AAB     := $(CURDIR)/app/build/outputs/bundle/release/app-release.aab
APPID   := org.snailtrail.androidgo
MAIN    := $(APPID)/.MainActivity

JNILIBS  := $(CURDIR)/app/src/main/jniLibs/arm64-v8a
ENGINE_SOS := $(JNILIBS)/libgnugo.so $(JNILIBS)/libkatago_cpu.so $(JNILIBS)/libkatago_gpu.so
CXX_SO     := $(JNILIBS)/libc++_shared.so
MODEL_FILE := $(CURDIR)/app/src/main/assets/engine/katago_model.txt.gz

.PHONY: all engines menuconfig model gnugo katago-cpu katago-gpu \
        clean verify test apk install run guard-assets bundle

# ── Top-level targets ─────────────────────────────────────────────

all: engines apk

engines:
	@$(MAKE) -C jni/katago model-ready
	@$(MAKE) -C jni/gnugo
	@$(MAKE) -C jni/katago katago-cpu katago-gpu
	@$(MAKE) runtime-libs

# Copy NDK C++ runtime (not committed — fetched on first build).
# arm64-v8a only, matches abiFilters in build.gradle.kts.
runtime-libs: $(CXX_SO)

$(CXX_SO):
	@mkdir -p $(JNILIBS)
	@cp $(NDK)/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so $@
	@echo "  libc++_shared.so → jniLibs"

menuconfig:
	@$(MAKE) -C jni/katago menuconfig

model:
	@$(MAKE) -C jni/katago model

apk: guard-assets
	@$(GRADLEW) assembleDebug

bundle: guard-assets
	@$(GRADLEW) bundleRelease

guard-assets:
	@missing=""; \
	for f in $(ENGINE_SOS) $(CXX_SO) $(MODEL_FILE); do \
	    [ -f $$f ] || missing="$$missing  $$f\n"; \
	done; \
	if [ -n "$$missing" ]; then \
	    echo "ERROR: Required files not found:"; \
	    printf "$$missing"; \
	    echo "Run 'make engines' first (model + libc++ auto-deploy if missing)."; \
	    exit 1; \
	fi

install: apk
	adb install -r $(APK)

run:
	@if ! adb shell pm list packages $(APPID) 2>/dev/null | grep -q $(APPID); then \
	    echo "ERROR: $(APPID) not installed. Run 'make install' first."; exit 1; \
	fi
	adb shell am start -n $(MAIN)

test:
	@$(GRADLEW) test

# ── Individual engines ────────────────────────────────────────────

# ── Individual engines ────────────────────────────────────────────

gnugo:
	@$(MAKE) -C jni/gnugo

katago-cpu katago-gpu:
	@$(MAKE) -C jni/katago $@

# ── Housekeeping ───────────────────────────────────────────────────

clean:
	@$(MAKE) -C jni/gnugo clean
	@$(MAKE) -C jni/katago clean
	@rm -f $(CXX_SO)
	@rm -rf $(CURDIR)/build
	@rm -rf $(CURDIR)/app/.cxx
	@$(GRADLEW) clean 2>&1 | tail -1
	@rm -rf $(CURDIR)/.gradle

verify:
	@$(MAKE) -C jni/gnugo verify 2>/dev/null || true
	@$(MAKE) -C jni/katago verify
	@printf "%-25s %-10s %s\n" "libgnugo.so" \
	    "$$(ls -lh $(CURDIR)/app/src/main/jniLibs/arm64-v8a/libgnugo.so 2>/dev/null | awk '{print $$5}')" \
	    "$$(nm -D $(CURDIR)/app/src/main/jniLibs/arm64-v8a/libgnugo.so 2>/dev/null | grep -q gtp_main && echo OK || echo MISSING)"

