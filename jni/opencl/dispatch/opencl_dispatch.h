/*
 * OpenCL dispatch table for Android — dlopen/dlsym-based function loading.
 *
 * On Android, libOpenCL.so is a vendor library that cannot be directly
 * linked (DT_NEEDED).  Instead we resolve all OpenCL entry points at
 * runtime via dlopen/dlsym and route every call through this dispatch
 * table.
 *
 * Usage:
 *   1. Call ocl_init() once at startup (safe to call multiple times).
 *   2. Use OCL(clFunction, args...) for every OpenCL API call.
 */

#ifndef OPENCL_DISPATCH_H
#define OPENCL_DISPATCH_H

#include <CL/cl.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ---- Function pointer table (populated by ocl_init) ---- */

struct OpenCLDispatch {
    /* Platform */
    cl_int  (*GetPlatformIDs)(cl_uint, cl_platform_id*, cl_uint*);
    cl_int  (*GetPlatformInfo)(cl_platform_id, cl_platform_info, size_t, void*, size_t*);

    /* Device */
    cl_int  (*GetDeviceIDs)(cl_platform_id, cl_device_type, cl_uint, cl_device_id*, cl_uint*);
    cl_int  (*GetDeviceInfo)(cl_device_id, cl_device_info, size_t, void*, size_t*);

    /* Context */
    cl_context (*CreateContext)(const cl_context_properties*, cl_uint, const cl_device_id*,
                                void (*)(const char*, const void*, size_t, void*), void*, cl_int*);
    cl_int  (*ReleaseContext)(cl_context);

    /* Command queue */
    cl_command_queue (*CreateCommandQueue)(cl_context, cl_device_id, cl_command_queue_properties, cl_int*);
    cl_int  (*ReleaseCommandQueue)(cl_command_queue);

    /* Memory */
    cl_mem  (*CreateBuffer)(cl_context, cl_mem_flags, size_t, void*, cl_int*);
    cl_int  (*ReleaseMemObject)(cl_mem);

    /* Program */
    cl_program (*CreateProgramWithSource)(cl_context, cl_uint, const char**, const size_t*, cl_int*);
    cl_int  (*BuildProgram)(cl_program, cl_uint, const cl_device_id*, const char*,
                             void (*)(cl_program, void*), void*);
    cl_int  (*GetProgramBuildInfo)(cl_program, cl_device_id, cl_program_build_info,
                                    size_t, void*, size_t*);
    cl_int  (*ReleaseProgram)(cl_program);

    /* Kernel */
    cl_kernel (*CreateKernel)(cl_program, const char*, cl_int*);
    cl_int  (*SetKernelArg)(cl_kernel, cl_uint, size_t, const void*);
    cl_int  (*ReleaseKernel)(cl_kernel);

    /* Command execution */
    cl_int  (*EnqueueNDRangeKernel)(cl_command_queue, cl_kernel, cl_uint, const size_t*,
                                     const size_t*, const size_t*, cl_uint, const cl_event*, cl_event*);
    cl_int  (*EnqueueReadBuffer)(cl_command_queue, cl_mem, cl_bool, size_t, size_t,
                                  void*, cl_uint, const cl_event*, cl_event*);
    cl_int  (*EnqueueWriteBuffer)(cl_command_queue, cl_mem, cl_bool, size_t, size_t,
                                   const void*, cl_uint, const cl_event*, cl_event*);

    /* Synchronisation */
    cl_int  (*Finish)(cl_command_queue);
    cl_int  (*Flush)(cl_command_queue);
    cl_int  (*WaitForEvents)(cl_uint, const cl_event*);
    cl_int  (*ReleaseEvent)(cl_event);

    /* Profiling */
    cl_int  (*GetEventProfilingInfo)(cl_event, cl_profiling_info, size_t, void*, size_t*);
};

/* The global dispatch table — populated by ocl_init(). */
extern struct OpenCLDispatch ocl;

/* Initialise the dispatch table.  Tries each candidate library in order.
 * Returns 0 on success, -1 if no vendor OpenCL library was found.
 * Safe to call multiple times (subsequent calls are no-ops). */
int ocl_init(void);

#ifdef __cplusplus
}
#endif

/* Convenience macro for calling through the dispatch table.
 * Usage:  OCL(CreateContext, props, 1, &dev, NULL, NULL, &err) */
#define OCL(fn, ...)  (ocl.fn(__VA_ARGS__))

#endif /* OPENCL_DISPATCH_H */
