cmake_minimum_required(VERSION 3.22.1)
project("androidgo_gtp")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(gtp_client SHARED
    gtp_client.cpp
    jni_bridge.cpp
)

target_include_directories(gtp_client PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

find_library(log-lib log)

target_link_libraries(gtp_client ${log-lib})
