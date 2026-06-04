#ifndef NEURALNET_OPENCLINCLUDES_H
#define NEURALNET_OPENCLINCLUDES_H

//Ensures a consistent opencl version everywhere we include opencl
#define CL_TARGET_OPENCL_VERSION 120
#define CL_USE_DEPRECATED_OPENCL_1_2_APIS

#ifdef __APPLE__
#include <OpenCL/opencl.h>
#else
#include <CL/cl.h>
#endif

// On Android, OpenCL calls are routed through a runtime dispatch table.
// Include the dispatch header so the OCL() macro is available everywhere.
#ifdef __ANDROID__
#include "opencl_dispatch.h"
#endif

#endif //NEURALNET_OPENCLINCLUDES_H
