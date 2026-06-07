# AndroidGo — top-level build orchestration.
#
# Targets:
#   all          Build all engines + deploy default model
#   menuconfig   Interactive KataGo model selection
#   model        Deploy selected model to assets/
#   gnugo        Build GNU Go engine only
#   katago-cpu   Build KataGo CPU (Eigen) engine only
#   katago-gpu   Build KataGo GPU (OpenCL) engine only
#   clean        Remove all build artifacts
#   verify       Check all engine .so files
#   apk          Assemble debug APK (gradle)
#   install      Install debug APK to connected device (adb)
#   run          Start app on device

NDK     ?= $(ANDROID_NDK_HOME)
GRADLEW := $(CURDIR)/gradlew
APK     := $(CURDIR)/app/build/outputs/apk/debug/app-debug.apk
APPID   := org.snailtrail.androidgo
MAIN    := $(APPID)/.MainActivity

.PHONY: all menuconfig model gnugo katago-cpu katago-gpu \
        clean verify apk install run

all:
	@$(MAKE) -C jni/katago model-ready
	@$(MAKE) -C jni/gnugo
	@$(MAKE) -C jni/katago katago-cpu katago-gpu

menuconfig:
	@$(MAKE) -C jni/katago menuconfig

model:
	@$(MAKE) -C jni/katago model

gnugo:
	@$(MAKE) -C jni/gnugo

katago-cpu katago-gpu:
	@$(MAKE) -C jni/katago $@

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

apk:
	@$(GRADLEW) assembleDebug

install: apk
	adb install -r $(APK)

run:
	adb shell am start -n $(MAIN)
