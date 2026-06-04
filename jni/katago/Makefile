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

.PHONY: all cpu opencl clean verify

all: cpu opencl

cpu: $(OUTDIR)/libkatago_cpu.so

opencl: $(OUTDIR)/libkatago_gpu.so

$(OUTDIR)/libkatago_cpu.so:
	@echo "=== KataGo CPU (Eigen) ==="
	@rm -rf $(BUILD_BASE)-cpu && mkdir -p $(BUILD_BASE)-cpu
	@$(CMAKE) -S $(CURDIR) -B $(BUILD_BASE)-cpu $(CMAKE_FLAGS) -DKATAGO_BACKEND=EIGEN 2>&1 | grep -v "Deprecation\|cmake_minimum\|Call Stack" | tail -2
	@$(CMAKE) --build $(BUILD_BASE)-cpu -j$$(nproc) 2>&1 | tail -2
	@$(STRIP) --strip-debug $@ 2>/dev/null || true
	@ls -lh $@

$(OUTDIR)/libkatago_gpu.so:
	@echo "=== KataGo GPU (OpenCL) ==="
	@rm -rf $(BUILD_BASE)-opencl && mkdir -p $(BUILD_BASE)-opencl
	@$(CMAKE) -S $(CURDIR) -B $(BUILD_BASE)-opencl $(CMAKE_FLAGS) -DKATAGO_BACKEND=OPENCL 2>&1 | grep -v "Deprecation\|cmake_minimum\|Call Stack" | tail -2
	@$(CMAKE) --build $(BUILD_BASE)-opencl -j$$(nproc) 2>&1 | tail -2
	@$(STRIP) --strip-debug $@ 2>/dev/null || true
	@ls -lh $@

verify:
	@for f in libkatago_gpu.so libkatago_cpu.so; do \
		if [ -f $(OUTDIR)/$$f ]; then \
			ok=$$(nm -D $(OUTDIR)/$$f 2>/dev/null | grep -c katago_gtp_main || true); \
			printf "%-35s %s\n" "$$f" "$$([ $$ok -gt 0 ] && echo OK || echo MISSING)"; \
		else \
			printf "%-35s %s\n" "$$f" "NOT BUILT"; \
		fi; \
	done

clean:
	rm -rf $(BUILD_BASE)-opencl $(BUILD_BASE)-cpu
	rm -f $(OUTDIR)/libkatago_gpu.so $(OUTDIR)/libkatago_cpu.so
