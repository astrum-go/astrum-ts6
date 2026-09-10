cmake_minimum_required(VERSION 3.22.1)
project(ts3opus_jni LANGUAGES C CXX)

find_package(opus REQUIRED CONFIG)

set(RNNOISE_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/third_party/rnnoise)

add_library(
    rnnoise
    STATIC
    ${RNNOISE_ROOT}/src/denoise.c
    ${RNNOISE_ROOT}/src/rnn.c
    ${RNNOISE_ROOT}/src/pitch.c
    ${RNNOISE_ROOT}/src/kiss_fft.c
    ${RNNOISE_ROOT}/src/celt_lpc.c
    ${RNNOISE_ROOT}/src/nnet.c
    ${RNNOISE_ROOT}/src/nnet_default.c
    ${RNNOISE_ROOT}/src/parse_lpcnet_weights.c
    ${RNNOISE_ROOT}/src/rnnoise_data.c
    ${RNNOISE_ROOT}/src/rnnoise_tables.c
)
target_include_directories(
    rnnoise
    PUBLIC ${RNNOISE_ROOT}/include
    PRIVATE ${RNNOISE_ROOT}/src
)
target_compile_features(rnnoise PRIVATE c_std_99)
target_compile_definitions(rnnoise PRIVATE RNNOISE_BUILD DISABLE_DEBUG_FLOAT=1)
target_compile_options(rnnoise PRIVATE -O3 -Wall -Wextra -Wno-unused-parameter)
set_target_properties(rnnoise PROPERTIES POSITION_INDEPENDENT_CODE ON)
target_link_libraries(rnnoise PUBLIC m)

add_library(
    ts3opus_jni
    SHARED
    opus_jni.cpp
    rnnoise_jni.cpp
    transient_suppressor.cpp
    transient_suppressor_jni.cpp
)

target_compile_features(ts3opus_jni PRIVATE cxx_std_17)
target_compile_options(ts3opus_jni PRIVATE -Wall -Wextra -Werror)
target_link_libraries(ts3opus_jni PRIVATE opus::libopus.a rnnoise)

