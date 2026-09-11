#include <jni.h>
#include <opus/opus.h>

#include <string>
#include <vector>

namespace {

void throw_java(JNIEnv* env, const char* class_name, const std::string& message) {
    jclass exception_class = env->FindClass(class_name);
    if (exception_class != nullptr) {
        env->ThrowNew(exception_class, message.c_str());
    }
}

OpusDecoder* decoder_from_handle(JNIEnv* env, jlong handle) {
    auto* decoder = reinterpret_cast<OpusDecoder*>(handle);
    if (decoder == nullptr) {
        throw_java(env, "java/lang/IllegalStateException", "Opus decoder is closed");
    }
    return decoder;
}

std::string opus_error(const char* operation, int code) {
    return std::string(operation) + ": " + opus_strerror(code);
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_br_app_astrum_ts6_audio_opus_NativeOpusDecoder_nativeCreate(
        JNIEnv* env,
        jclass,
        jint sample_rate,
        jint channels) {
    int error = OPUS_OK;
    OpusDecoder* decoder = opus_decoder_create(sample_rate, channels, &error);
    if (error != OPUS_OK || decoder == nullptr) {
        throw_java(env, "java/lang/IllegalStateException", opus_error("opus_decoder_create failed", error));
        return 0;
    }
    return reinterpret_cast<jlong>(decoder);
}

extern "C" JNIEXPORT jshortArray JNICALL
Java_br_app_astrum_ts6_audio_opus_NativeOpusDecoder_nativeDecode(
        JNIEnv* env,
        jclass,
        jlong handle,
        jbyteArray packet,
        jint frame_size) {
    OpusDecoder* decoder = decoder_from_handle(env, handle);
    if (decoder == nullptr) return nullptr;
    if (frame_size <= 0 || frame_size > 5760) {
        throw_java(env, "java/lang/IllegalArgumentException", "Invalid Opus frame size");
        return nullptr;
    }

    jbyte* packet_bytes = nullptr;
    jsize packet_size = 0;
    if (packet != nullptr) {
        packet_size = env->GetArrayLength(packet);
        if (packet_size > 0) {
            packet_bytes = env->GetByteArrayElements(packet, nullptr);
            if (packet_bytes == nullptr) return nullptr;
        }
    }

    std::vector<opus_int16> pcm(static_cast<size_t>(frame_size));
    const int decoded_samples = opus_decode(
            decoder,
            reinterpret_cast<const unsigned char*>(packet_bytes),
            packet_size,
            pcm.data(),
            frame_size,
            0);

    if (packet_bytes != nullptr) {
        env->ReleaseByteArrayElements(packet, packet_bytes, JNI_ABORT);
    }
    if (decoded_samples < 0) {
        throw_java(
                env,
                "java/lang/IllegalArgumentException",
                opus_error("opus_decode failed", decoded_samples));
        return nullptr;
    }

    jshortArray output = env->NewShortArray(decoded_samples);
    if (output == nullptr) return nullptr;
    env->SetShortArrayRegion(output, 0, decoded_samples, pcm.data());
    return output;
}

extern "C" JNIEXPORT void JNICALL
Java_br_app_astrum_ts6_audio_opus_NativeOpusDecoder_nativeReset(
        JNIEnv* env,
        jclass,
        jlong handle) {
    OpusDecoder* decoder = decoder_from_handle(env, handle);
    if (decoder == nullptr) return;
    const int error = opus_decoder_ctl(decoder, OPUS_RESET_STATE);
    if (error != OPUS_OK) {
        throw_java(env, "java/lang/IllegalStateException", opus_error("opus_decoder_ctl failed", error));
    }
}

extern "C" JNIEXPORT void JNICALL
Java_br_app_astrum_ts6_audio_opus_NativeOpusDecoder_nativeDestroy(
        JNIEnv*,
        jclass,
        jlong handle) {
    opus_decoder_destroy(reinterpret_cast<OpusDecoder*>(handle));
}

extern "C" JNIEXPORT jlong JNICALL
Java_br_app_astrum_ts6_audio_opus_NativeOpusEncoder_nativeCreate(
        JNIEnv* env,
        jclass,
        jint sample_rate,
        jint channels,
        jint bitrate) {
    int error = OPUS_OK;
    OpusEncoder* encoder = opus_encoder_create(sample_rate, channels, OPUS_APPLICATION_VOIP, &error);
    if (error != OPUS_OK || encoder == nullptr) {
        throw_java(env, "java/lang/IllegalStateException", opus_error("opus_encoder_create failed", error));
        return 0;
    }

    int control_error = OPUS_OK;
    const auto apply_control = [&control_error](int result) {
        if (control_error == OPUS_OK && result != OPUS_OK) control_error = result;
    };
    apply_control(opus_encoder_ctl(encoder, OPUS_SET_BITRATE(bitrate)));
    apply_control(opus_encoder_ctl(encoder, OPUS_SET_VBR(1)));
    apply_control(opus_encoder_ctl(encoder, OPUS_SET_VBR_CONSTRAINT(1)));
    apply_control(opus_encoder_ctl(encoder, OPUS_SET_COMPLEXITY(10)));
    apply_control(opus_encoder_ctl(encoder, OPUS_SET_SIGNAL(OPUS_SIGNAL_VOICE)));
    apply_control(opus_encoder_ctl(encoder, OPUS_SET_MAX_BANDWIDTH(OPUS_BANDWIDTH_FULLBAND)));
    apply_control(opus_encoder_ctl(encoder, OPUS_SET_LSB_DEPTH(16)));
    if (control_error != OPUS_OK) {
        opus_encoder_destroy(encoder);
        throw_java(env, "java/lang/IllegalStateException", opus_error("opus_encoder_ctl failed", control_error));
        return 0;
    }
    return reinterpret_cast<jlong>(encoder);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_br_app_astrum_ts6_audio_opus_NativeOpusEncoder_nativeEncode(
        JNIEnv* env,
        jclass,
        jlong handle,
        jshortArray pcm) {
    auto* encoder = reinterpret_cast<OpusEncoder*>(handle);
    if (encoder == nullptr) {
        throw_java(env, "java/lang/IllegalStateException", "Opus encoder is closed");
        return nullptr;
    }
    if (pcm == nullptr) {
        throw_java(env, "java/lang/IllegalArgumentException", "PCM input is null");
        return nullptr;
    }

    const jsize frame_size = env->GetArrayLength(pcm);
    jshort* pcm_samples = env->GetShortArrayElements(pcm, nullptr);
    if (pcm_samples == nullptr) return nullptr;

    std::vector<unsigned char> encoded(1275);
    const int encoded_bytes = opus_encode(
            encoder,
            reinterpret_cast<const opus_int16*>(pcm_samples),
            frame_size,
            encoded.data(),
            static_cast<opus_int32>(encoded.size()));
    env->ReleaseShortArrayElements(pcm, pcm_samples, JNI_ABORT);

    if (encoded_bytes < 0) {
        throw_java(
                env,
                "java/lang/IllegalArgumentException",
                opus_error("opus_encode failed", encoded_bytes));
        return nullptr;
    }

    jbyteArray output = env->NewByteArray(encoded_bytes);
    if (output == nullptr) return nullptr;
    env->SetByteArrayRegion(
            output,
            0,
            encoded_bytes,
            reinterpret_cast<const jbyte*>(encoded.data()));
    return output;
}

extern "C" JNIEXPORT void JNICALL
Java_br_app_astrum_ts6_audio_opus_NativeOpusEncoder_nativeDestroy(
        JNIEnv*,
        jclass,
        jlong handle) {
    opus_encoder_destroy(reinterpret_cast<OpusEncoder*>(handle));
}
