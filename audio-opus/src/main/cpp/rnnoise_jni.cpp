#include <jni.h>
#include <rnnoise.h>

#include <algorithm>
#include <array>
#include <cmath>
#include <new>
#include <string>

namespace {

constexpr int kFrameSize = 480;

struct NativeRnNoiseContext {
    DenoiseState* denoiser;
    std::array<float, kFrameSize> input{};
    std::array<float, kFrameSize> output{};
};

void throw_java(JNIEnv* env, const char* class_name, const std::string& message) {
    jclass exception_class = env->FindClass(class_name);
    if (exception_class != nullptr) {
        env->ThrowNew(exception_class, message.c_str());
    }
}

NativeRnNoiseContext* context_from_handle(JNIEnv* env, jlong handle) {
    auto* context = reinterpret_cast<NativeRnNoiseContext*>(handle);
    if (context == nullptr || context->denoiser == nullptr) {
        throw_java(env, "java/lang/IllegalStateException", "RNNoise processor is closed");
    }
    return context;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_io_github_ts3mobile_audio_opus_NativeRnNoiseProcessor_nativeCreate(
        JNIEnv* env,
        jclass) {
    if (rnnoise_get_frame_size() != 480) {
        throw_java(env, "java/lang/IllegalStateException", "Unexpected RNNoise frame size");
        return 0;
    }
    auto* context = new (std::nothrow) NativeRnNoiseContext{rnnoise_create(nullptr)};
    if (context == nullptr) {
        throw_java(env, "java/lang/OutOfMemoryError", "Unable to create RNNoise processor");
        return 0;
    }
    if (context->denoiser == nullptr) {
        delete context;
        throw_java(env, "java/lang/OutOfMemoryError", "Unable to create RNNoise processor");
        return 0;
    }
    return reinterpret_cast<jlong>(context);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_io_github_ts3mobile_audio_opus_NativeRnNoiseProcessor_nativeProcessInPlace(
        JNIEnv* env,
        jclass,
        jlong handle,
        jshortArray pcm) {
    NativeRnNoiseContext* context = context_from_handle(env, handle);
    if (context == nullptr) return 0;
    if (pcm == nullptr) {
        throw_java(env, "java/lang/IllegalArgumentException", "PCM input is null");
        return 0;
    }

    const int frame_size = rnnoise_get_frame_size();
    const jsize sample_count = env->GetArrayLength(pcm);
    if (sample_count <= 0 || sample_count % frame_size != 0) {
        throw_java(env, "java/lang/IllegalArgumentException", "PCM must contain complete RNNoise frames");
        return 0;
    }

    jshort* samples = env->GetShortArrayElements(pcm, nullptr);
    if (samples == nullptr) return 0;

    float vad_total = 0;
    for (jsize offset = 0; offset < sample_count; offset += frame_size) {
        for (int index = 0; index < frame_size; ++index) {
            context->input[static_cast<size_t>(index)] = samples[offset + index];
        }
        vad_total += rnnoise_process_frame(
                context->denoiser,
                context->output.data(),
                context->input.data());
        for (int index = 0; index < frame_size; ++index) {
            const long rounded = std::lrint(context->output[static_cast<size_t>(index)]);
            samples[offset + index] = static_cast<jshort>(
                    std::clamp(rounded, -32768L, 32767L));
        }
    }

    env->ReleaseShortArrayElements(pcm, samples, 0);
    return vad_total / static_cast<float>(sample_count / frame_size);
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_ts3mobile_audio_opus_NativeRnNoiseProcessor_nativeDestroy(
        JNIEnv*,
        jclass,
        jlong handle) {
    auto* context = reinterpret_cast<NativeRnNoiseContext*>(handle);
    if (context == nullptr) return;
    rnnoise_destroy(context->denoiser);
    delete context;
}
