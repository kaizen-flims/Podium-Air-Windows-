// SPDX-License-Identifier: GPL-3.0-only
#include "analyzer/audio_analysis.h"
#include "analyzer/resampler.h"
#include <cmath>
#include <iostream>
#include <stdexcept>

int main() {
  constexpr double rate = 11025;
  std::vector<float> pcm(static_cast<size_t>(rate * 60), 0);
  // Two beats per second, with a pitched bed, leading and trailing silence.
  for (size_t i = static_cast<size_t>(rate * 2); i < pcm.size() - rate * 3; ++i) {
    const double t = i / rate;
    const double beat = std::fmod(t - 2, 0.5);
    pcm[i] = static_cast<float>(0.05 * std::sin(t * 2 * 3.141592653589793 * 220)
      + (beat < 0.055 ? 0.65 * std::exp(-beat * 70) * std::sin(beat * 2 * 3.141592653589793 * 90) : 0));
  }
  auto measured = bitchord::smart::AnalyzeAudio(pcm, rate, 60);
  if (!std::isfinite(measured.bpm) || std::abs(measured.bpm - 120) > 3 || measured.energy_curve.empty())
    throw std::runtime_error("Measured beat grid or energy curve failed: " + std::to_string(measured.bpm));
  if (measured.audible_start_time > 3 || measured.content_end_time < 56 || measured.content_end_time > 58)
    throw std::runtime_error("Leading/trailing silence was not measured correctly");
  std::vector<float> silence(static_cast<size_t>(rate * 5), 0);
  auto empty = bitchord::smart::AnalyzeAudio(silence, rate, 5);
  if (!std::isfinite(empty.bpm) || empty.beat_confidence >= 0.55)
    throw std::runtime_error("Silence incorrectly authorized beat matching");
  auto reduced = bitchord::smart::Resample(pcm, rate, rate / 2);
  if (reduced.size() != pcm.size() / 2) throw std::runtime_error("Native resampler duration drift");
  for (float sample : reduced) if (!std::isfinite(sample)) throw std::runtime_error("Nonfinite resampled PCM");
  std::cout << "PASS: Original native DSP measured " << measured.bpm << " BPM, real content boundaries and energy; silence cannot authorize beat matching; native resampler retained duration.\n";
}
