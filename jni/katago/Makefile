# Build KataGo engines (CPU/GPU) + neural network model management.
#
# Prerequisites: ANDROID_NDK_HOME, cmake

NDK     ?= $(ANDROID_NDK_HOME)
CMAKE   := cmake
CPU_BUILD := $(CURDIR)/../../build/katago-cpu
GPU_BUILD := $(CURDIR)/../../build/katago-gpu
OUTDIR  := $(CURDIR)/../../app/src/main/jniLibs/arm64-v8a
# Requires Linux — llvm-strip path is host-specific
STRIP   := $(NDK)/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip

CMAKE_FLAGS := -DCMAKE_TOOLCHAIN_FILE=$(NDK)/build/cmake/android.toolchain.cmake \
               -DANDROID_ABI=arm64-v8a \
               -DANDROID_PLATFORM=android-26 \
               -DCMAKE_BUILD_TYPE=Release \
               -DCMAKE_SHARED_LINKER_FLAGS="-Wl,--export-dynamic -Wl,--allow-shlib-undefined -Wl,--unresolved-symbols=ignore-in-shared-libs"

.PHONY: all katago-cpu katago-gpu model menuconfig model-ready clean verify

# ── Model ──────────────────────────────────────────────────────────────

MODEL     ?= $(shell grep '^MODEL' $(CURDIR)/model.conf 2>/dev/null | cut -d= -f2 | xargs)
MODEL_DIR  = $(CURDIR)/models/$(MODEL)
ASSETS_DIR = $(CURDIR)/../../app/src/main/assets/engine
MODEL_KT   = $(CURDIR)/../../app/src/main/java/org/snailtrail/androidgo/engine/ModelConfig.kt

all: model-ready katago-cpu katago-gpu

model-ready:
	@if [ ! -f $(ASSETS_DIR)/katago_model.txt.gz ]; then \
	    echo "  Model not deployed — using default from model.conf"; \
	    $(MAKE) model; \
	fi

model:
	@echo "=== Model: $(MODEL) ==="
	@test -d $(MODEL_DIR) || { echo "ERROR: model dir not found: $(MODEL_DIR)"; exit 1; }
	@cp $(MODEL_DIR)/*.txt.gz $(ASSETS_DIR)/katago_model.txt.gz
	@echo "package org.snailtrail.androidgo.engine" > $(MODEL_KT)
	@echo "const val MODEL_NAME = \"$(MODEL)\"" >> $(MODEL_KT)
	@echo "val VISIT_LEVELS = intArrayOf($$(head -1 $(MODEL_DIR)/params.conf))" >> $(MODEL_KT)
	@ls -lh $(ASSETS_DIR)/katago_model.txt.gz
	@echo "Model $(MODEL) → katago_model.txt.gz + ModelConfig.kt OK"
	@cat $(MODEL_KT)

menuconfig:
	@echo ""
	@echo "  === KataGo Model Selection ==="
	@echo ""
	@n=1; \
	for d in $(CURDIR)/models/*/; do \
	    name=$$(basename $$d); \
	    size=$$(ls -lh $$d/*.txt.gz 2>/dev/null | awk '{print $$5}'); \
	    visits=$$(head -1 $$d/params.conf 2>/dev/null); \
	    marker=" "; \
	    [ "$$name" = "$(MODEL)" ] && marker=">"; \
	    printf "  %s %s. %-14s %6s   %s..%s\n" \
	        "$$marker" "$$n" "$$name" "$$size" \
	        "$$(echo $$visits | cut -d, -f1)" \
	        "$$(echo $$visits | rev | cut -d, -f1 | rev)"; \
	    n=$$((n + 1)); \
	done; \
	echo ""; \
	printf "  Select [1-%d]: " $$((n - 1)); \
	read sel; \
	n=1; \
	for d in $(CURDIR)/models/*/; do \
	    if [ "$$n" -eq "$$sel" ]; then \
	        choice=$$(basename $$d); \
	        break; \
	    fi; \
	    n=$$((n + 1)); \
	done; \
	if [ -z "$${choice:-}" ]; then \
	    echo "  Invalid selection."; \
	    exit 1; \
	fi; \
	echo ""; \
	echo "  Model: $$choice"; \
	sed -i "s/^MODEL=.*/MODEL=$$choice/" $(CURDIR)/model.conf; \
	$(MAKE) model
	@echo ""

verify-model:
	@zcat $(ASSETS_DIR)/katago_model.txt.gz 2>/dev/null | head -1 || echo "NOT FOUND"

# If cmake files change, invalidate cache so it reconfigures.
KATAGO_CMAKE   := $(CURDIR)/CMakeLists.txt $(CURDIR)/katago_bridge.cpp
OPENCL_DISPATCH := $(CURDIR)/opencl/dispatch/opencl_dispatch.cpp \
                   $(CURDIR)/opencl/dispatch/opencl_dispatch.h

# ── Engines ────────────────────────────────────────────────────────────

katago-cpu: $(OUTDIR)/libkatago_cpu.so
katago-gpu: $(OUTDIR)/libkatago_gpu.so

$(OUTDIR)/libkatago_cpu.so: $(KATAGO_CMAKE)
	@if [ -f $(CPU_BUILD)/CMakeCache.txt ]; then \
	    newer=$$(find $(KATAGO_CMAKE) -newer $(CPU_BUILD)/CMakeCache.txt 2>/dev/null); \
	    [ -n "$$newer" ] && { echo "  cmake files changed, reconfiguring..."; rm -rf $(CPU_BUILD); }; \
	fi
	@echo "=== KataGo CPU (Eigen) ==="
	@mkdir -p $(CPU_BUILD)
	@test -f $(CPU_BUILD)/CMakeCache.txt || \
	    $(CMAKE) -S $(CURDIR) -B $(CPU_BUILD) $(CMAKE_FLAGS) \
	    -DKATAGO_BACKEND=EIGEN 2>&1 | grep -v "Deprecation\|cmake_minimum\|Call Stack" | tail -2
	@$(CMAKE) --build $(CPU_BUILD) -j$$(nproc) 2>&1 | tail -2
	@$(STRIP) --strip-debug $@ 2>/dev/null || true
	@ls -lh $@

$(OUTDIR)/libkatago_gpu.so: $(KATAGO_CMAKE) $(OPENCL_DISPATCH)
	@if [ -f $(GPU_BUILD)/CMakeCache.txt ]; then \
	    newer=$$(find $(KATAGO_CMAKE) $(OPENCL_DISPATCH) -newer $(GPU_BUILD)/CMakeCache.txt 2>/dev/null); \
	    [ -n "$$newer" ] && { echo "  cmake files changed, reconfiguring..."; rm -rf $(GPU_BUILD); }; \
	fi
	@echo "=== KataGo GPU (OpenCL) ==="
	@mkdir -p $(GPU_BUILD)
	@test -f $(GPU_BUILD)/CMakeCache.txt || \
	    $(CMAKE) -S $(CURDIR) -B $(GPU_BUILD) $(CMAKE_FLAGS) \
	    -DKATAGO_BACKEND=OPENCL 2>&1 | grep -v "Deprecation\|cmake_minimum\|Call Stack" | tail -2
	@$(CMAKE) --build $(GPU_BUILD) -j$$(nproc) 2>&1 | tail -2
	@$(STRIP) --strip-debug $@ 2>/dev/null || true
	@ls -lh $@

# ── Verify ─────────────────────────────────────────────────────────────

verify:
	@printf "%-25s %-10s %s\n" "ENGINE" "SIZE" "EXPORT"
	@printf "%-25s %-10s %s\n" "------" "----" "------"
	@for f in libkatago_cpu.so libkatago_gpu.so; do \
	    if [ -f $(OUTDIR)/$$f ]; then \
	        sz=$$(ls -lh $(OUTDIR)/$$f | awk '{print $$5}'); \
	        ok=$$(nm -D $(OUTDIR)/$$f 2>/dev/null | grep -c gtp_main || true); \
	        printf "%-25s %-10s %s\n" "$$f" "$$sz" "$$([ $$ok -gt 0 ] && echo OK || echo MISSING)"; \
	    else \
	        printf "%-25s %-10s %s\n" "$$f" "-" "NOT BUILT"; \
	    fi; \
	done

# ── Clean ──────────────────────────────────────────────────────────────

clean:
	rm -rf $(CPU_BUILD) $(GPU_BUILD)
	rm -f $(OUTDIR)/libkatago_cpu.so $(OUTDIR)/libkatago_gpu.so
	rm -f $(ASSETS_DIR)/katago_model.txt.gz
