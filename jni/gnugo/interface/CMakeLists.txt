# Shared library — dlopen entry point for the Android app.
# gnugo_bridge.c provides gnugo_gtp_main(); interface/main.c provides the
# real GNU Go startup logic with its main() renamed to gnugo_main.
#
# -z,muldefs handles stray main() definitions from static libraries
# (utils/getopt.c and utils/getopt1.c each have a test main()).

INCLUDE_DIRECTORIES(${GNUGo_SOURCE_DIR}/engine)
INCLUDE_DIRECTORIES(${GNUGo_SOURCE_DIR}/sgf)
INCLUDE_DIRECTORIES(${GNUGo_SOURCE_DIR}/utils)

SET(gnugo_SRCS
    main.c
    play_ascii.c
    play_gmp.c
    play_gtp.c
    play_solo.c
    play_test.c
    gmp.c
    gtp.c
    ${CMAKE_SOURCE_DIR}/gnugo_bridge.c
    )

ADD_LIBRARY(gnugo SHARED ${gnugo_SRCS})

SET_SOURCE_FILES_PROPERTIES(main.c PROPERTIES
    COMPILE_DEFINITIONS "main=gnugo_main")

TARGET_LINK_LIBRARIES(gnugo sgf engine sgf utils patterns m)

SET_TARGET_PROPERTIES(gnugo PROPERTIES
    LINK_FLAGS "-Wl,--export-dynamic -Wl,-soname,libgnugo.so -Wl,-z,max-page-size=16384 -Wl,-z,muldefs -Wl,--wrap=exit -Wl,--wrap=abort"
)
