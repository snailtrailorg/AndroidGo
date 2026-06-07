cmake_minimum_required(VERSION 3.10)
project(GNUGo LANGUAGES C)

set(CMAKE_C_STANDARD 90)
add_compile_options(-std=gnu89 -fcommon -w -Wno-error=return-type -Wno-error=format-security)

# config.h is pre-generated and committed (was produced by autotools configure)
add_definitions(-DHAVE_CONFIG_H)
include_directories(${CMAKE_CURRENT_SOURCE_DIR})

add_subdirectory(utils)
add_subdirectory(sgf)
add_subdirectory(engine)
add_subdirectory(patterns)
add_subdirectory(interface)
