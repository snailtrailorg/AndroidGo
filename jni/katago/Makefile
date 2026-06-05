NDK ?= $(ANDROID_NDK_HOME)
CMAKE := cmake
BUILD_BASE := $(CURDIR)/../../build/katago
OUTDIR := $(CURDIR)/../../app/src/main/jniLibs/arm64-v8a
STRIP := $(NDK)/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip

CMAKE_FLAGS := -DCMAKE_TOOLCHAIN_FILE=$(NDK)/build/cmake/android.toolchain.cmake \
               -DANDROID_ABI=arm64-v8a \
               -DANDROID_PLATFORM=android-26 \
               -DCMAKE_BUILD_TYPE=Release \
               -DCMAKE_SHARED_LINKER_FLAGS="-Wl,--export-dynamic -Wl,--allow-shlib-undefined -Wl,--unresolved-symbols=ignore-in-shared-libs"

.PHONY: all katago-cpu katago-gpu gnugo model clean verify

MODEL ?= $(shell grep '^MODEL' $(CURDIR)/model.conf 2>/dev/null | cut -d= -f2 | xargs)
MODEL_DIR = $(CURDIR)/models/$(MODEL)
ASSETS_DIR = $(CURDIR)/../../app/src/main/assets/engine

all: katago-cpu katago-gpu

model:
	@echo "=== Model: $(MODEL) ==="
	@test -d $(MODEL_DIR) || { echo "ERROR: model dir not found: $(MODEL_DIR)"; exit 1; }
	@cp $(MODEL_DIR)/*.txt.gz $(ASSETS_DIR)/katago_model.txt.gz
	@ls -lh $(ASSETS_DIR)/katago_model.txt.gz
	@echo "Model $(MODEL) → katago_model.txt.gz OK"

verify-model:
	@zcat $(ASSETS_DIR)/katago_model.txt.gz 2>/dev/null | head -1 || echo "NOT FOUND"

gnugo:
	@echo "=== GNU Go ==="
	@cd $(CURDIR)/../gnugo && bash build_gnugo.sh 2>&1 | tail -3
	@ls -lh $(OUTDIR)/libgnugo.so

katago-cpu: $(OUTDIR)/libkatago_cpu.so
katago-gpu: $(OUTDIR)/libkatago_gpu.so

$(OUTDIR)/libkatago_cpu.so:
	@echo "=== KataGo CPU (Eigen) ==="
	@mkdir -p $(BUILD_BASE)-cpu
	@test -f $(BUILD_BASE)-cpu/CMakeCache.txt || $(CMAKE) -S $(CURDIR) -B $(BUILD_BASE)-cpu $(CMAKE_FLAGS) -DKATAGO_BACKEND=EIGEN 2>&1 | grep -v "Deprecation\|cmake_minimum\|Call Stack" | tail -2
	@$(CMAKE) --build $(BUILD_BASE)-cpu -j$$(nproc) 2>&1 | tail -2
	@$(STRIP) --strip-debug $@ 2>/dev/null || true
	@ls -lh $@

$(OUTDIR)/libkatago_gpu.so:
	@echo "=== KataGo GPU (OpenCL) ==="
	@mkdir -p $(BUILD_BASE)-gpu
	@test -f $(BUILD_BASE)-gpu/CMakeCache.txt || $(CMAKE) -S $(CURDIR) -B $(BUILD_BASE)-gpu $(CMAKE_FLAGS) -DKATAGO_BACKEND=OPENCL 2>&1 | grep -v "Deprecation\|cmake_minimum\|Call Stack" | tail -2
	@$(CMAKE) --build $(BUILD_BASE)-gpu -j$$(nproc) 2>&1 | tail -2
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
	rm -rf $(BUILD_BASE)-cpu $(BUILD_BASE)-gpu
	rm -f $(OUTDIR)/libkatago_cpu.so $(OUTDIR)/libkatago_gpu.so
