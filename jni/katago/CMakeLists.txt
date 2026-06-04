cmake_minimum_required(VERSION 3.18)
project(katago_engine LANGUAGES CXX)

set(KATAGO_BACKEND "OPENCL" CACHE STRING "Neural net backend (OPENCL or EIGEN)")

# Derive output filename from backend
if(KATAGO_BACKEND STREQUAL "EIGEN")
  set(KATAGO_OUTPUT_NAME katago_cpu)
else()
  set(KATAGO_OUTPUT_NAME katago_gpu)
endif()

# Strip -Wl,--no-undefined (but NOT --no-undefined-version) added by NDK toolchain
string(REGEX REPLACE ",--no-undefined " " " CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS}")
string(REGEX REPLACE ",--fatal-warnings" "" CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS}")

set(CMAKE_CXX_STANDARD 17)

set(KATAGO_SRC ${CMAKE_CURRENT_SOURCE_DIR}/cpp)
set(OPENCL_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../opencl)
if(KATAGO_BACKEND STREQUAL "OPENCL")
  set(OPENCL_INCLUDES "${OPENCL_DIR}/include" "${OPENCL_DIR}/dispatch")
else()
  set(OPENCL_INCLUDES "")
endif()

# ── Sources — GLOB each directory once ──
file(GLOB_RECURSE SRC_CORE       "${KATAGO_SRC}/core/*.cpp")
file(GLOB_RECURSE SRC_GAME       "${KATAGO_SRC}/game/*.cpp")
file(GLOB_RECURSE SRC_SEARCH     "${KATAGO_SRC}/search/*.cpp")
file(GLOB_RECURSE SRC_DATAIO     "${KATAGO_SRC}/dataio/*.cpp")
file(GLOB_RECURSE SRC_BOOK       "${KATAGO_SRC}/book/*.cpp")
file(GLOB_RECURSE SRC_PROGRAM    "${KATAGO_SRC}/program/*.cpp")
file(GLOB_RECURSE SRC_COMMAND    "${KATAGO_SRC}/command/*.cpp")
file(GLOB_RECURSE SRC_TESTS      "${KATAGO_SRC}/tests/*.cpp")
file(GLOB_RECURSE SRC_NEURALNET  "${KATAGO_SRC}/neuralnet/*.cpp")
file(GLOB         SRC_MAIN       "${KATAGO_SRC}/main.cpp")

# Exclude non-OpenCL backends and test files
if(KATAGO_BACKEND STREQUAL "OPENCL")
  list(FILTER SRC_NEURALNET EXCLUDE REGEX "cuda|trt|metal|eigen|dummy|test")
elseif(KATAGO_BACKEND STREQUAL "EIGEN")
  list(FILTER SRC_NEURALNET EXCLUDE REGEX "cuda|trt|metal|opencl|dummy|test")
endif()
list(FILTER SRC_SEARCH    EXCLUDE REGEX "test")


# Custom sources
set(SRC_BRIDGE "${CMAKE_CURRENT_SOURCE_DIR}/katago_bridge.cpp")
if(KATAGO_BACKEND STREQUAL "OPENCL")
  set(SRC_DISPATCH "${OPENCL_DIR}/dispatch/opencl_dispatch.cpp")
else()
  set(SRC_DISPATCH "")
endif()

# ── Target ──
add_library(katago_engine SHARED
    ${SRC_CORE} ${SRC_GAME} ${SRC_SEARCH} ${SRC_DATAIO}
    ${SRC_BOOK} ${SRC_PROGRAM} ${SRC_NEURALNET} ${SRC_COMMAND}
    ${SRC_TESTS}
    ${SRC_MAIN} ${SRC_BRIDGE} ${SRC_DISPATCH}
)

# ── Includes ──
target_include_directories(katago_engine PRIVATE
    "${KATAGO_SRC}"
    "${KATAGO_SRC}/external"
    "${KATAGO_SRC}/external/tclap-1.2.5/include"
    "${KATAGO_SRC}/external/filesystem-1.5.8/include"
    "${KATAGO_SRC}/external/eigen3"
    ${OPENCL_INCLUDES}
)

# ── Defines ──
target_compile_definitions(katago_engine PRIVATE
    __ANDROID__
    NO_LIBZIP
    NO_GIT_REVISION
    BYTE_ORDER=1234
    LITTLE_ENDIAN=1234
    BIG_ENDIAN=4321
)
if(KATAGO_BACKEND STREQUAL "OPENCL")
  target_compile_definitions(katago_engine PRIVATE
    USE_OPENCL_BACKEND
    CL_TARGET_OPENCL_VERSION=120
    CL_USE_DEPRECATED_OPENCL_1_2_APIS)
elseif(KATAGO_BACKEND STREQUAL "EIGEN")
  target_compile_definitions(katago_engine PRIVATE USE_EIGEN_BACKEND)
endif()

# ── Link ──
target_link_libraries(katago_engine PRIVATE dl log z m)

# ── Export ──
target_link_options(katago_engine PRIVATE
    -Wl,--export-dynamic
    -Wl,--allow-shlib-undefined
    -Wl,--unresolved-symbols=ignore-in-shared-libs
    -Wl,-soname,lib${KATAGO_OUTPUT_NAME}.so
)

# ── Output to jniLibs ──
set_target_properties(katago_engine PROPERTIES
    OUTPUT_NAME "${KATAGO_OUTPUT_NAME}"
    LIBRARY_OUTPUT_DIRECTORY "${CMAKE_CURRENT_SOURCE_DIR}/../../app/src/main/jniLibs/arm64-v8a"
    SUFFIX ".so"
)
