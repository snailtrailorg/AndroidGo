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
#   make clean         Remove all build artifacts
#   make verify        Check all engine .so exports

NDK     ?= $(ANDROID_NDK_HOME)
GRADLEW := $(CURDIR)/gradlew
APK     := $(CURDIR)/app/build/outputs/apk/debug/app-debug.apk
APPID   := org.snailtrail.androidgo
MAIN    := $(APPID)/.MainActivity

ENGINE_SOS := $(CURDIR)/app/src/main/jniLibs/arm64-v8a/libgnugo.so \
              $(CURDIR)/app/src/main/jniLibs/arm64-v8a/libkatago_cpu.so \
              $(CURDIR)/app/src/main/jniLibs/arm64-v8a/libkatago_gpu.so

.PHONY: all engines menuconfig model gnugo katago-cpu katago-gpu \
        clean verify apk install run guard-engines

# ── Top-level targets ─────────────────────────────────────────────

all: engines apk

engines:
	@$(MAKE) -C jni/katago model-ready
	@$(MAKE) -C jni/gnugo
	@$(MAKE) -C jni/katago katago-cpu katago-gpu

menuconfig:
	@$(MAKE) -C jni/katago menuconfig

model:
	@$(MAKE) -C jni/katago model

apk: guard-engines
	@$(GRADLEW) assembleDebug

guard-engines:
	@missing=""; \
	for so in $(ENGINE_SOS); do \
	    [ -f $$so ] || missing="$$missing  $$so\n"; \
	done; \
	if [ -n "$$missing" ]; then \
	    echo "ERROR: Engine .so not found:"; \
	    printf "$$missing"; \
	    echo "Run 'make engines' first."; \
	    exit 1; \
	fi

install: apk
	adb install -r $(APK)

run:
	@if ! adb shell pm list packages $(APPID) 2>/dev/null | grep -q $(APPID); then \
	    echo "ERROR: $(APPID) not installed. Run 'make install' first."; exit 1; \
	fi
	adb shell am start -n $(MAIN)

# ── Individual engines ────────────────────────────────────────────

gnugo:
	@$(MAKE) -C jni/gnugo

katago-cpu katago-gpu:
	@$(MAKE) -C jni/katago $@

# ── Housekeeping ───────────────────────────────────────────────────

clean:
	@$(MAKE) -C jni/gnugo clean
	@$(MAKE) -C jni/katago clean
	@$(GRADLEW) clean 2>&1 | tail -1

verify:
	@$(MAKE) -C jni/gnugo verify 2>/dev/null || true
	@$(MAKE) -C jni/katago verify
	@printf "%-25s %-10s %s\n" "libgnugo.so" \
	    "$$(ls -lh $(CURDIR)/app/src/main/jniLibs/arm64-v8a/libgnugo.so 2>/dev/null | awk '{print $$5}')" \
	    "$$(nm -D $(CURDIR)/app/src/main/jniLibs/arm64-v8a/libgnugo.so 2>/dev/null | grep -q gtp_main && echo OK || echo MISSING)"

