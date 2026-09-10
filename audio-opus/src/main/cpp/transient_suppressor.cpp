#include "transient_suppressor.h"

#include <algorithm>
#include <cmath>

namespace ts3audio {

namespace {

constexpr int kSubBlockSize = 16;      // ~0.33 ms at 48kHz
constexpr float kHighPassAlpha = 0.92f;
constexpr float kMinEnergyThreshold = 15000.0f; // Minimal energy to consider an event
constexpr float kTransientRatioThreshold = 5.5f; // Energy surge ratio to classify as click
constexpr float kMinGain = 0.04f;      // Max attenuation ~ -28dB on click peak
constexpr int kHoldSubBlocks = 12;     // ~4 ms hold after click detection

inline int16_t Clamp16(float v) {
    if (v > 32767.0f) return 32767;
    if (v < -32768.0f) return -32768;
    return static_cast<int16_t>(v);
}

}  // namespace

TransientSuppressor::TransientSuppressor(int sample_rate)
    : sample_rate_(sample_rate),
      history_(kHistorySize, 0.0f) {
    Reset();
}

void TransientSuppressor::Reset() {
    prev_sample_ = 0.0f;
    fast_energy_ = 1000.0f;
    slow_energy_ = 1000.0f;
    current_gain_ = 1.0f;
    hold_counter_ = 0;
    std::fill(history_.begin(), history_.end(), 0.0f);
    history_pos_ = 0;
}

bool TransientSuppressor::IsVoiced(const int16_t* pcm, int num_samples) {
    // Check normalized autocorrelation over pitch period ranges (80Hz to 400Hz)
    // At 48kHz: lag 120 (400Hz) to lag 600 (80Hz)
    constexpr int kMinLag = 120;
    constexpr int kMaxLag = 500;
    constexpr int kCorrLen = 240; // 5ms window for cross-correlation

    if (num_samples < kCorrLen) return false;

    // Use latest history + current frame
    float energy0 = 0.0f;
    for (int i = 0; i < kCorrLen; ++i) {
        float s = static_cast<float>(pcm[i]);
        energy0 += s * s;
    }

    if (energy0 < 100000.0f) return false; // Too quiet to be voiced speech

    // Coarse pitch search with step of 4 for speed
    float max_norm_corr = 0.0f;
    for (int lag = kMinLag; lag <= kMaxLag; lag += 4) {
        float corr = 0.0f;
        float energyLag = 0.0f;

        for (int i = 0; i < kCorrLen; i += 2) {
            int hist_idx = (history_pos_ - lag + i + kHistorySize) % kHistorySize;
            float s_prev = history_[hist_idx];
            float s_curr = static_cast<float>(pcm[i]);

            corr += s_curr * s_prev;
            energyLag += s_prev * s_prev;
        }

        if (energyLag > 1000.0f) {
            float norm_corr = (corr * corr) / (energy0 * energyLag * 0.25f + 1e-6f);
            if (norm_corr > max_norm_corr) {
                max_norm_corr = norm_corr;
            }
        }
    }

    // A voiced sound typically has normalized correlation > 0.4
    return max_norm_corr > 0.40f;
}

float TransientSuppressor::ProcessInPlace(int16_t* pcm, int num_samples) {
    if (pcm == nullptr || num_samples <= 0) return 0.0f;

    bool voiced = IsVoiced(pcm, num_samples);
    float max_transient_intensity = 0.0f;

    int num_subblocks = num_samples / kSubBlockSize;

    for (int b = 0; b < num_subblocks; ++b) {
        int block_offset = b * kSubBlockSize;

        // 1. Calculate high-frequency pre-emphasized sub-block energy
        float sub_energy = 0.0f;
        for (int i = 0; i < kSubBlockSize; ++i) {
            float s = static_cast<float>(pcm[block_offset + i]);
            float hp = s - kHighPassAlpha * prev_sample_;
            prev_sample_ = s;
            sub_energy += hp * hp;

            // Store in circular history buffer for pitch tracking
            history_[history_pos_] = s;
            history_pos_ = (history_pos_ + 1) % kHistorySize;
        }

        // 2. Track moving envelopes
        fast_energy_ = 0.20f * sub_energy + 0.80f * fast_energy_;
        slow_energy_ = 0.008f * sub_energy + 0.992f * slow_energy_;

        // 3. Transient detector
        float ratio = fast_energy_ / (slow_energy_ + 100.0f);
        bool is_transient = (ratio > kTransientRatioThreshold) &&
                            (sub_energy > kMinEnergyThreshold) &&
                            !voiced;

        float target_gain = 1.0f;
        if (is_transient) {
            float intensity = std::min(1.0f, (ratio - kTransientRatioThreshold) / 10.0f);
            if (intensity > max_transient_intensity) {
                max_transient_intensity = intensity;
            }

            // Suppress impulsive spike
            target_gain = std::max(kMinGain, std::sqrt((slow_energy_ * 3.5f) / (sub_energy + 1.0f)));
            hold_counter_ = kHoldSubBlocks;
        } else if (hold_counter_ > 0) {
            hold_counter_--;
            target_gain = current_gain_;
        }

        // 4. Smooth gain transition
        float gain_step;
        if (target_gain < current_gain_) {
            // Fast attack on transient
            gain_step = (target_gain - current_gain_) / static_cast<float>(kSubBlockSize);
        } else {
            // Smooth release
            target_gain = 0.08f * 1.0f + 0.92f * current_gain_;
            gain_step = (target_gain - current_gain_) / static_cast<float>(kSubBlockSize);
        }

        // 5. Apply gain to samples in the sub-block
        for (int i = 0; i < kSubBlockSize; ++i) {
            current_gain_ += gain_step;
            current_gain_ = std::max(kMinGain, std::min(1.0f, current_gain_));
            float processed = static_cast<float>(pcm[block_offset + i]) * current_gain_;
            pcm[block_offset + i] = Clamp16(processed);
        }
    }

    return max_transient_intensity;
}

}  // namespace ts3audio
