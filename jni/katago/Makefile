NDK ?= $(ANDROID_NDK_HOME)
CMAKE := cmake
KATAGO_CPU_BUILD := $(CURDIR)/../../build/katago-cpu
KATAGO_GPU_BUILD := $(CURDIR)/../../build/katago-gpu
GNUGO_BUILD      := $(CURDIR)/../../build/gnugo
OUTDIR := $(CURDIR)/../../app/src/main/jniLibs/arm64-v8a
# Requires Linux — llvm-strip path is host-specific
STRIP := $(NDK)/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip

CMAKE_FLAGS := -DCMAKE_TOOLCHAIN_FILE=$(NDK)/build/cmake/android.toolchain.cmake \
               -DANDROID_ABI=arm64-v8a \
               -DANDROID_PLATFORM=android-26 \
               -DCMAKE_BUILD_TYPE=Release \
               -DCMAKE_SHARED_LINKER_FLAGS="-Wl,--export-dynamic -Wl,--allow-shlib-undefined -Wl,--unresolved-symbols=ignore-in-shared-libs"

.PHONY: all katago-cpu katago-gpu gnugo model menuconfig clean verify

MODEL ?= $(shell grep '^MODEL' $(CURDIR)/model.conf 2>/dev/null | cut -d= -f2 | xargs)
MODEL_DIR = $(CURDIR)/models/$(MODEL)
ASSETS_DIR = $(CURDIR)/../../app/src/main/assets/engine
MODEL_CONFIG_KT = $(CURDIR)/../../app/src/main/java/org/snailtrail/androidgo/engine/ModelConfig.kt

all: model-ready katago-cpu katago-gpu gnugo

model-ready:
	@if [ ! -f $(ASSETS_DIR)/katago_model.txt.gz ]; then \
	    echo "  Model not deployed — using default from model.conf"; \
	    $(MAKE) model; \
	fi

model:
	@echo "=== Model: $(MODEL) ==="
	@test -d $(MODEL_DIR) || { echo "ERROR: model dir not found: $(MODEL_DIR)"; exit 1; }
	@cp $(MODEL_DIR)/*.txt.gz $(ASSETS_DIR)/katago_model.txt.gz
	@echo "package org.snailtrail.androidgo.engine" > $(MODEL_CONFIG_KT)
	@echo "const val MODEL_NAME = \"$(MODEL)\"" >> $(MODEL_CONFIG_KT)
	@echo "val VISIT_LEVELS = intArrayOf($$(head -1 $(MODEL_DIR)/params.conf))" >> $(MODEL_CONFIG_KT)
	@ls -lh $(ASSETS_DIR)/katago_model.txt.gz
	@echo "Model $(MODEL) → katago_model.txt.gz + ModelConfig.kt OK"
	@cat $(MODEL_CONFIG_KT)

menuconfig:
	@echo ""
	@echo "  === KataGo Model Selection ==="
	@echo ""
	@n=1; \
	for d in $(CURDIR)/models/*/; do \
	    name=$$(basename $$d); \
	    size=$$(ls -lh $$d/*.txt.gz 2>/dev/null | awk '{print $$5}'); \
	    visits=$$(head -1 $$d/params.conf 2>/dev/null); \
	    visits_short=$$(echo $$visits | cut -d, -f1,10); \
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

gnugo: $(OUTDIR)/libgnugo.so

$(OUTDIR)/libgnugo.so:
	@echo "=== GNU Go 3.8 ==="
	@mkdir -p $(GNUGO_BUILD)
	@test -f $(GNUGO_BUILD)/CMakeCache.txt || \
	    $(CMAKE) -S $(CURDIR)/../gnugo -B $(GNUGO_BUILD) \
	    -DCMAKE_TOOLCHAIN_FILE=$(NDK)/build/cmake/android.toolchain.cmake \
	    -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 \
	    -DCMAKE_BUILD_TYPE=Release 2>&1 | grep -v "Deprecation\|cmake_minimum" | tail -2
	@$(CMAKE) --build $(GNUGO_BUILD) -j$$(nproc) 2>&1 | tail -2
	@mkdir -p $(OUTDIR)
	@cp $(GNUGO_BUILD)/interface/libgnugo.so $@
	@$(STRIP) --strip-debug $@ 2>/dev/null || true
	@ls -lh $@

katago-cpu: $(OUTDIR)/libkatago_cpu.so
katago-gpu: $(OUTDIR)/libkatago_gpu.so

$(OUTDIR)/libkatago_cpu.so:
	@echo "=== KataGo CPU (Eigen) ==="
	@mkdir -p $(KATAGO_CPU_BUILD)
	@test -f $(KATAGO_CPU_BUILD)/CMakeCache.txt || $(CMAKE) -S $(CURDIR) -B $(KATAGO_CPU_BUILD) $(CMAKE_FLAGS) -DKATAGO_BACKEND=EIGEN 2>&1 | grep -v "Deprecation\|cmake_minimum\|Call Stack" | tail -2
	@$(CMAKE) --build $(KATAGO_CPU_BUILD) -j$$(nproc) 2>&1 | tail -2
	@$(STRIP) --strip-debug $@ 2>/dev/null || true
	@ls -lh $@

$(OUTDIR)/libkatago_gpu.so:
	@echo "=== KataGo GPU (OpenCL) ==="
	@mkdir -p $(KATAGO_GPU_BUILD)
	@test -f $(KATAGO_GPU_BUILD)/CMakeCache.txt || $(CMAKE) -S $(CURDIR) -B $(KATAGO_GPU_BUILD) $(CMAKE_FLAGS) -DKATAGO_BACKEND=OPENCL 2>&1 | grep -v "Deprecation\|cmake_minimum\|Call Stack" | tail -2
	@$(CMAKE) --build $(KATAGO_GPU_BUILD) -j$$(nproc) 2>&1 | tail -2
	@$(STRIP) --strip-debug $@ 2>/dev/null || true
	@ls -lh $@

verify:
	@printf "%-25s %-10s %s\n" "ENGINE" "SIZE" "EXPORT"
	@printf "%-25s %-10s %s\n" "------" "----" "------"
	@for f in libgnugo.so libkatago_cpu.so libkatago_gpu.so; do \
		if [ -f $(OUTDIR)/$$f ]; then \
			sz=$$(ls -lh $(OUTDIR)/$$f | awk '{print $$5}'); \
			ok=$$(nm -D $(OUTDIR)/$$f 2>/dev/null | grep -c gtp_main || true); \
			printf "%-25s %-10s %s\n" "$$f" "$$sz" "$$([ $$ok -gt 0 ] && echo OK || echo MISSING)"; \
		else \
			printf "%-25s %-10s %s\n" "$$f" "-" "NOT BUILT"; \
		fi; \
	done

clean:
	rm -rf $(KATAGO_CPU_BUILD) $(KATAGO_GPU_BUILD) $(GNUGO_BUILD)
	rm -f $(OUTDIR)/libkatago_cpu.so $(OUTDIR)/libkatago_gpu.so $(OUTDIR)/libgnugo.so
