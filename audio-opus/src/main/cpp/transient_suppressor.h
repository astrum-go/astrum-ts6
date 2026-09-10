#ifndef TS3_TRANSIENT_SUPPRESSOR_H_
#define TS3_TRANSIENT_SUPPRESSOR_H_

#include <cstdint>
#include <vector>

namespace ts3audio {

class TransientSuppressor {
public:
    explicit TransientSuppressor(int sample_rate = 48000);
    ~TransientSuppressor() = default;

    /**
     * Processes audio in-place.
     * @param pcm Interleaved/mono 16-bit PCM buffer.
     * @param num_samples Number of samples to process (e.g. 480 for 10ms at 48kHz).
     * @return Transient intensity score between 0.0 (no transient) and 1.0 (heavy transient suppressed).
     */
    float ProcessInPlace(int16_t* pcm, int num_samples);

    void Reset();

    int sample_rate() const noexcept { return sample_rate_; }

private:
    [[maybe_unused]] int sample_rate_;
    float prev_sample_ = 0.0f;
    float fast_energy_ = 100.0f;
    float slow_energy_ = 100.0f;
    float current_gain_ = 1.0f;
    int hold_counter_ = 0;

    // Ring buffer for periodicity/voicing correlation check
    static constexpr int kHistorySize = 960; // 20ms of history for pitch search
    std::vector<float> history_;
    int history_pos_ = 0;

    bool IsVoiced(const int16_t* pcm, int num_samples);
};

}  // namespace ts3audio

#endif  // TS3_TRANSIENT_SUPPRESSOR_H_
