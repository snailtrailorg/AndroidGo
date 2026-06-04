/*
 * OpenCL dispatch table initialisation for Android.
 *
 * Searches for a vendor OpenCL library at runtime and populates all
 * function pointers in the global `ocl` dispatch table.
 */

#include "opencl_dispatch.h"
#include <dlfcn.h>
#include <android/log.h>

#define OCL_LOG(...) __android_log_print(ANDROID_LOG_ERROR, "OpenCLDispatch", __VA_ARGS__)

/* Global dispatch table — zero-initialised. */
struct OpenCLDispatch ocl = {0};

/* Vendor libraries to try, in preference order. */
static const char* CANDIDATES[] = {
    "libOpenCL.so",         /* Qualcomm Adreno, most devices */
    "libGLES_mali.so",      /* ARM Mali (carries OpenCL symbols) */
    "libPVROCL.so",         /* Imagination PowerVR */
    "libOpenCL-pixel.so",   /* Google Pixel */
    NULL
};

static void* ocl_handle = NULL;
static int ocl_loaded = 0;

int ocl_init(void) {
    if (ocl_loaded) return 0;

    /* Try each candidate library */
    for (int i = 0; CANDIDATES[i]; i++) {
        ocl_handle = dlopen(CANDIDATES[i], RTLD_NOW | RTLD_LOCAL);
        if (ocl_handle) {
            OCL_LOG("loaded %s", CANDIDATES[i]);
            break;
        }
    }

    if (!ocl_handle) {
        OCL_LOG("no vendor OpenCL library found");
        return -1;
    }

    /* Resolve each entry point */
#define RESOLVE(fn) do { \
    ocl.fn = (decltype(ocl.fn))dlsym(ocl_handle, "cl" #fn); \
    if (!ocl.fn) OCL_LOG("WARNING: cl" #fn " not found"); \
} while(0)

    RESOLVE(GetPlatformIDs);
    RESOLVE(GetPlatformInfo);
    RESOLVE(GetDeviceIDs);
    RESOLVE(GetDeviceInfo);
    RESOLVE(CreateContext);
    RESOLVE(ReleaseContext);
    RESOLVE(CreateCommandQueue);
    RESOLVE(ReleaseCommandQueue);
    RESOLVE(CreateBuffer);
    RESOLVE(ReleaseMemObject);
    RESOLVE(CreateProgramWithSource);
    RESOLVE(BuildProgram);
    RESOLVE(GetProgramBuildInfo);
    RESOLVE(ReleaseProgram);
    RESOLVE(CreateKernel);
    RESOLVE(SetKernelArg);
    RESOLVE(ReleaseKernel);
    RESOLVE(EnqueueNDRangeKernel);
    RESOLVE(EnqueueReadBuffer);
    RESOLVE(EnqueueWriteBuffer);
    RESOLVE(Finish);
    RESOLVE(Flush);
    RESOLVE(WaitForEvents);
    RESOLVE(ReleaseEvent);
    RESOLVE(GetEventProfilingInfo);

#undef RESOLVE

    ocl_loaded = 1;
    OCL_LOG("dispatch table initialised — %d functions", 26);
    return 0;
}
