// SPDX-License-Identifier: GPL-3.0-only
#include "analyzer/audio_analysis.h"
#include "analyzer/resampler.h"
#include "analyzer/mel_spectrogram.h"
#include "analyzer/vocal_spectrogram.h"
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
  std::vector<float> mel_pcm(22050, 0);
  for (size_t i = 0; i < mel_pcm.size(); ++i) mel_pcm[i] = static_cast<float>(0.1 * std::sin(i * 2 * 3.141592653589793 * 440 / 22050));
  const auto mel = bitchord::smart::ComputeBeatSpectrogram(mel_pcm, 22050);
  if (mel.frames < 49 || mel.values.size() != mel.frames * 128) throw std::runtime_error("Beat model mel shape differs");
  for (float sample : mel.values) if (!std::isfinite(sample) || sample < 0) throw std::runtime_error("Invalid log-mel value");
  if (!bitchord::smart::ComputeBeatSpectrogram(mel_pcm, 16000).values.empty()) throw std::runtime_error("Wrong mel sample rate accepted");
  std::vector<float> vocal_pcm(44100, 0);
  for (size_t i = 0; i < vocal_pcm.size(); ++i) vocal_pcm[i] = static_cast<float>(0.1 * std::sin(i * 2 * 3.141592653589793 * 440 / 44100));
  const auto vocal = bitchord::smart::ComputeVocalSpectrogram({vocal_pcm, vocal_pcm}, 44100);
  if (vocal.frames < 42 || vocal.values.size() != vocal.frames * 2049 * 2) throw std::runtime_error("Vocal STFT shape differs");
  for (float sample : vocal.values) if (!std::isfinite(sample) || sample < 0) throw std::runtime_error("Invalid vocal STFT value");
  std::cout << "PASS: Original native mel and stereo STFT model front ends produced finite, correctly shaped spectrograms.\n";
  std::cout << "PASS: Original native DSP measured " << measured.bpm << " BPM, real content boundaries and energy; silence cannot authorize beat matching; native resampler retained duration.\n";
}
