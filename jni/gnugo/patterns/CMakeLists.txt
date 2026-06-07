# Patterns static library.
# Host tools (mkpat, joseki, mkeyes, mkmcpat, compress/uncompress/extract_fuseki)
# were run at build time by autotools to generate the pattern .c/.db files that
# are already committed.  They are excluded here because each defines its own
# main() and is not needed at runtime.

file(GLOB pat_SRCS *.c)
list(FILTER pat_SRCS EXCLUDE REGEX
    "/(mkpat|joseki|mkeyes|mkmcpat|compress_fuseki|uncompress_fuseki|extract_fuseki)\\.c$")

add_library(patterns STATIC ${pat_SRCS})

target_include_directories(patterns PRIVATE
    "${GNUGo_SOURCE_DIR}/engine"
    "${GNUGo_SOURCE_DIR}/sgf"
    "${GNUGo_SOURCE_DIR}/utils"
)
