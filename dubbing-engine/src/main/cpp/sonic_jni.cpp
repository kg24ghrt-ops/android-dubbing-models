#include <jni.h>
#include <android/log.h>
#include "sonic.h"

#define LOG_TAG "SonicJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_yourapp_dubbing_engine_TextToSpeechEngine_nativeCreateSonic(
        JNIEnv *env, jobject thiz, jint sampleRate, jint numChannels) {
    sonicStream stream = sonicCreateStream(sampleRate, numChannels);
    return reinterpret_cast<jlong>(stream);
}

JNIEXPORT void JNICALL
Java_com_yourapp_dubbing_engine_TextToSpeechEngine_nativeSetPitch(
        JNIEnv *env, jobject thiz, jlong handle, jfloat pitch) {
    sonicSetPitch(reinterpret_cast<sonicStream>(handle), pitch);
}

JNIEXPORT void JNICALL
Java_com_yourapp_dubbing_engine_TextToSpeechEngine_nativeSetSpeed(
        JNIEnv *env, jobject thiz, jlong handle, jfloat speed) {
    sonicSetSpeed(reinterpret_cast<sonicStream>(handle), speed);
}

JNIEXPORT jint JNICALL
Java_com_yourapp_dubbing_engine_TextToSpeechEngine_nativeWriteSonic(
        JNIEnv *env, jobject thiz, jlong handle, jbyteArray input, jint numSamples, jbyteArray output) {
    jbyte *inBuf = env->GetByteArrayElements(input, nullptr);
    jbyte *outBuf = env->GetByteArrayElements(output, nullptr);
    
    int written = sonicWriteShortToStream(reinterpret_cast<sonicStream>(handle),
                                         reinterpret_cast<short*>(inBuf), numSamples);
    int available = sonicReadShortFromStream(reinterpret_cast<sonicStream>(handle),
                                            reinterpret_cast<short*>(outBuf),
                                            env->GetArrayLength(output) / sizeof(short));
    
    env->ReleaseByteArrayElements(input, inBuf, JNI_ABORT);
    env->ReleaseByteArrayElements(output, outBuf, 0);
    
    return available;
}

JNIEXPORT void JNICALL
Java_com_yourapp_dubbing_engine_TextToSpeechEngine_nativeFlushSonic(
        JNIEnv *env, jobject thiz, jlong handle) {
    sonicFlushStream(reinterpret_cast<sonicStream>(handle));
}

JNIEXPORT void JNICALL
Java_com_yourapp_dubbing_engine_TextToSpeechEngine_nativeDestroySonic(
        JNIEnv *env, jobject thiz, jlong handle) {
    sonicDestroyStream(reinterpret_cast<sonicStream>(handle));
}

}