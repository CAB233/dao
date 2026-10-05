cmake_minimum_required(VERSION 3.22.1)
project(DaoQr LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_VISIBILITY_INLINES_HIDDEN ON)
# Use the NDK linker for LTO with CMake 3.22's legacy Android toolchain detection.
set(CMAKE_C_LINK_OPTIONS_IPO "-fuse-ld=lld")
set(CMAKE_CXX_LINK_OPTIONS_IPO "-fuse-ld=lld")

if(NOT DAO_ZXING_VERSION OR NOT DAO_ZXING_SHA256)
    message(FATAL_ERROR "ZXing-C++ version and SHA-256 must be supplied by Gradle")
endif()

# Build only QR readers. Keep character decoding and all QR detection options.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(ZXING_READERS ON CACHE BOOL "" FORCE)
set(ZXING_WRITERS OFF CACHE STRING "" FORCE)
set(ZXING_C_API OFF CACHE BOOL "" FORCE)
set(ZXING_EXPERIMENTAL_API OFF CACHE BOOL "" FORCE)
set(ZXING_ENABLE_QRCODE ON CACHE BOOL "" FORCE)
foreach(format 1D AZTEC DATAMATRIX MAXICODE PDF417)
    set(ZXING_ENABLE_${format} OFF CACHE BOOL "" FORCE)
endforeach()

include(FetchContent)
FetchContent_Declare(zxing
    URL "https://codeload.github.com/zxing-cpp/zxing-cpp/tar.gz/refs/tags/v${DAO_ZXING_VERSION}"
    URL_HASH "SHA256=${DAO_ZXING_SHA256}"
    SOURCE_SUBDIR core
    TLS_VERIFY ON
)
FetchContent_MakeAvailable(zxing)

add_library(dao_qr SHARED QrReader.cpp)
target_link_libraries(dao_qr PRIVATE ZXing::ZXing)
foreach(target ZXing dao_qr)
    target_compile_options(${target} PRIVATE
        -ffunction-sections -fdata-sections
        "$<$<NOT:$<CONFIG:Debug>>:-Oz>"
    )
    set_target_properties(${target} PROPERTIES
        INTERPROCEDURAL_OPTIMIZATION_RELEASE ON
        INTERPROCEDURAL_OPTIMIZATION_RELWITHDEBINFO ON
        INTERPROCEDURAL_OPTIMIZATION_MINSIZEREL ON
    )
endforeach()
target_link_options(dao_qr PRIVATE
    -Wl,--gc-sections -Wl,--exclude-libs,ALL -Wl,-z,max-page-size=16384
)
