#include <jni.h>
#include "transient_suppressor.h"

#include <new>
#include <string>

namespace {

void throw_java(JNIEnv* env, const char* class_name, const std::string& message) {
    jclass exception_class = env->FindClass(class_name);
    if (exception_class != nullptr) {
        env->ThrowNew(exception_class, message.c_str());
    }
}

ts3audio::TransientSuppressor* suppressor_from_handle(JNIEnv* env, jlong handle) {
    auto* suppressor = reinterpret_cast<ts3audio::TransientSuppressor*>(handle);
    if (suppressor == nullptr) {
        throw_java(env, "java/lang/IllegalStateException", "TransientSuppressor is closed");
    }
    return suppressor;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_br_app_astrum_ts6_audio_opus_NativeTransientSuppressor_nativeCreate(
        JNIEnv* env,
        jclass,
        jint sample_rate) {
    auto* suppressor = new (std::nothrow) ts3audio::TransientSuppressor(sample_rate);
    if (suppressor == nullptr) {
        throw_java(env, "java/lang/OutOfMemoryError", "Unable to create TransientSuppressor");
        return 0;
    }
    return reinterpret_cast<jlong>(suppressor);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_br_app_astrum_ts6_audio_opus_NativeTransientSuppressor_nativeProcessInPlace(
        JNIEnv* env,
        jclass,
        jlong handle,
        jshortArray pcm_array) {
    auto* suppressor = suppressor_from_handle(env, handle);
    if (suppressor == nullptr) return 0.0f;

    if (pcm_array == nullptr) return 0.0f;

    jsize sample_count = env->GetArrayLength(pcm_array);
    if (sample_count <= 0) return 0.0f;

    jshort* elements = env->GetShortArrayElements(pcm_array, nullptr);
    if (elements == nullptr) {
        throw_java(env, "java/lang/OutOfMemoryError", "Unable to get short array elements");
        return 0.0f;
    }

    float score = suppressor->ProcessInPlace(reinterpret_cast<int16_t*>(elements), sample_count);

    env->ReleaseShortArrayElements(pcm_array, elements, 0);
    return score;
}

extern "C" JNIEXPORT void JNICALL
Java_br_app_astrum_ts6_audio_opus_NativeTransientSuppressor_nativeReset(
        JNIEnv* env,
        jclass,
        jlong handle) {
    auto* suppressor = suppressor_from_handle(env, handle);
    if (suppressor != nullptr) {
        suppressor->Reset();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_br_app_astrum_ts6_audio_opus_NativeTransientSuppressor_nativeDestroy(
        JNIEnv*,
        jclass,
        jlong handle) {
    delete reinterpret_cast<ts3audio::TransientSuppressor*>(handle);
}
