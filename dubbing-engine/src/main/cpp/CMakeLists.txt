cmake_minimum_required(VERSION 3.22.1)
project("dubbingengine")

# Sonic library for audio pitch/speed change
add_library(sonic STATIC
        sonic/sonic.c)
target_include_directories(sonic PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/sonic)

# JNI wrapper for Sonic
add_library(sonic_jni SHARED sonic_jni.cpp)
target_link_libraries(sonic_jni sonic log)

# Pitch detector (YIN algorithm)
add_library(pitch_detector SHARED pitch_detector.cpp)
target_link_libraries(pitch_detector log)