// SPDX-FileCopyrightText: 2026 tuffboy999 and Va1der (Arcanic)
// SPDX-License-Identifier: GPL-3.0-or-later
// Arcanic - authors: tuffboy999 and Va1der. Free software under GPL-3.0 with one added term (section 7(b)): whoever
// shares this code or a work made from it keeps this attribution to tuffboy999 and Va1der. See NOTICE.
// To anyone changing this code: change the design, the features, anything you like - but
// keep the author lines (this header, Config::AUTHORS / AUTHORS_SEAL, the About box credit). The license requires it.
#pragma once
// The launcher's sounds
#include <xaudio2.h>
#include <array>
#pragma comment(lib, "xaudio2.lib")

namespace UiAudio {
    enum Sound { STARTUP = 130, NAV = 131, CLICK = 132, LAUNCH = 133, FAIL = 134 };
    constexpr int FIRST = STARTUP, COUNT = 5;

    // ------------------------------------------------------------------------------------------ synthesis
    struct Wave {
        static constexpr int RATE = 44100;
        vector<float> s;
        explicit Wave(float seconds) : s((size_t)(seconds * RATE), 0.f) {}
        void tone(float at, float f0, float f1, float amp, float attack, float tau, float glide = .05f) {
            size_t i0 = (size_t)(at * RATE);
            double phase = 0;
            for (size_t i = i0; i < s.size(); i++) {
                float t = (i - i0) / (float)RATE;
                float env = t < attack ? t / attack : expf(-(t - attack) / tau);
                if (t > attack && env < 1e-4f) break;
                float f = f1 + (f0 - f1) * expf(-t / glide);
                phase += 6.283185307 * f / RATE;
                s[i] += amp * env * (float)sin(phase);
            }
        }
        // A struck bell: the inharmonic partials of a small chime, the high ones dying first
        void bell(float at, float f, float amp, float tau) {
            static const float ratio[] = {1, 2.0f, 2.76f, 4.07f, 5.4f}, gain[] = {1, .32f, .2f, .09f, .05f},
                               life[] = {1, .55f, .4f, .28f, .18f};
            for (int k = 0; k < 5; k++) tone(at, f * ratio[k], f * ratio[k], amp * gain[k], .0015f, tau * life[k], 1);
        }
        // Filtered noise: `lp0` -> `lp1` sweeps a one-pole low-pass (0..1), `hp` is a one-pole high-pass (0.9..0.999)
        void noise(float at, float dur, float amp, float lp0, float lp1, float hp, float tau, bool rise = false) {
            size_t i0 = (size_t)(at * RATE), n = (size_t)(dur * RATE);
            uint32_t x = 0x9E3779B9u ^ (uint32_t)(at * 977 + dur * 7919);
            float lp = 0, prev = 0, out = 0;
            for (size_t k = 0; k < n && i0 + k < s.size(); k++) {
                x ^= x << 13; x ^= x >> 17; x ^= x << 5;
                float white = (x / 4294967295.f) * 2 - 1, u = k / (float)n;
                lp += (lp0 + (lp1 - lp0) * u) * (white - lp);
                out = hp * (out + lp - prev);
                prev = lp;
                float t = k / (float)RATE;
                float env = rise ? u * u * (1 - powf(u, 40)) : std::min(1.f, t / .002f) * expf(-t / tau);
                s[i0 + k] += amp * env * out;
            }
        }
        // A small room: four damped combs and two all-passes (Schroeder)
        void reverb(float wet) {
            static const int combs[4] = {1557, 1617, 1491, 1422}, passes[2] = {556, 225};
            vector<float> out(s.size(), 0.f);
            for (int c : combs) {
                vector<float> buf(c, 0.f);
                float filt = 0;
                for (size_t i = 0, at = 0; i < s.size(); i++, at = (at + 1) % c) {
                    float y = buf[at];
                    filt = y * .75f + filt * .25f;
                    buf[at] = s[i] + filt * .78f;
                    out[i] += y * .25f;
                }
            }
            for (int p : passes) {
                vector<float> buf(p, 0.f);
                for (size_t i = 0, at = 0; i < s.size(); i++, at = (at + 1) % p) {
                    float b = buf[at], y = -.5f * out[i] + b;
                    buf[at] = out[i] + .5f * y;
                    out[i] = y;
                }
            }
            for (size_t i = 0; i < s.size(); i++) s[i] += out[i] * wet;
        }
        void fadeOut(float seconds) {
            size_t n = std::min(s.size(), (size_t)(seconds * RATE));
            for (size_t k = 0; k < n; k++) s[s.size() - 1 - k] *= k / (float)n;
        }
        // 16-bit PCM, scaled so the loudest sample reaches `peak`
        string pcm(float peak) const {
            float top = 1e-6f;
            for (float v : s) top = std::max(top, fabsf(v));
            string out(s.size() * 2, '\0');
            for (size_t i = 0; i < s.size(); i++) {
                int16_t v = (int16_t)lroundf(std::clamp(s[i] / top * peak, -1.f, 1.f) * 32767);
                memcpy(&out[i * 2], &v, 2);
            }
            return out;
        }
    };

    inline string synthesize(int sound) {
        switch (sound) {
        case CLICK: {  // a short, soft tick: a bright ping over a little body
            Wave w(.14f);
            w.tone(0, 2600, 1800, .5f, .0006f, .009f, .006f);
            w.tone(0, 240, 170, .35f, .001f, .016f, .01f);
            w.noise(0, .02f, .22f, .6f, .6f, .9f, .004f);
            w.reverb(.06f);
            return w.pcm(.16f);
        }
        case NAV: {  // a page turning: a breath of air with a rising, rounded tone in it
            Wave w(.42f);
            w.noise(0, .26f, .55f, .06f, .14f, .995f, .07f);
            w.tone(0, 500, 690, .3f, .005f, .06f, .045f);
            w.tone(0, 1000, 1380, .08f, .005f, .04f, .045f);
            w.reverb(.14f);
            return w.pcm(.12f);
        }
        case STARTUP: {
            const float hit = 2.6f;
            Wave w(4.7f);
            for (float f : {110.f, 164.81f, 220.f, 277.18f, 329.63f})
                for (float detune : {0.f, .21f})  // a slow, shallow shimmer rather than a beat that pumps
                    w.tone(0, f + detune, f + detune, detune > 0 ? .022f : .045f, hit - .05f, 1.1f, 1);
            w.noise(1.25f, hit - 1.25f, .3f, .01f, .32f, .99f, 1, true);
            w.tone(hit, 72, 44, .55f, .002f, .3f, .08f);
            w.noise(hit, .5f, .16f, .45f, .2f, .97f, .09f);
            w.bell(hit, 440.f, .30f, 1.5f);
            w.bell(hit, 554.37f, .22f, 1.4f);
            w.bell(hit, 659.25f, .22f, 1.3f);
            w.bell(hit, 880.f, .15f, 1.1f);
            float sparks[4][2] = {{.04f, 1760.f}, {.10f, 2217.46f}, {.17f, 2637.f}, {.26f, 3520.f}};
            for (int i = 0; i < 4; i++) w.bell(hit + sparks[i][0], sparks[i][1], .05f - i * .008f, .5f - i * .04f);
            w.reverb(.3f);
            w.fadeOut(.5f);
            return w.pcm(.3f);
        }
        case LAUNCH: {  // the game is up: three notes going up
            Wave w(1.8f);
            w.tone(0, 110, 92, .2f, .003f, .25f, .1f);
            w.bell(0, 440.f, .30f, .9f);
            w.bell(.075f, 659.25f, .26f, .9f);
            w.bell(.15f, 880.f, .22f, 1.f);
            w.bell(.15f, 1318.5f, .06f, .6f);
            w.reverb(.28f);
            w.fadeOut(.3f);
            return w.pcm(.24f);
        }
        case FAIL: {  // something went wrong: two low notes stepping down, quietly
            Wave w(.7f);
            w.tone(0, 330, 312, .4f, .004f, .12f, .05f);
            w.tone(.11f, 262, 247, .4f, .004f, .16f, .05f);
            w.reverb(.12f);
            w.fadeOut(.15f);
            return w.pcm(.16f);
        }
        }
        return "";
    }
    // --render-sounds <folder>: writes every sound as a .wav (to listen to and to check them)
    inline void render(const fs::path& dir) {
        const char* names[COUNT] = {"startup", "nav", "click", "launch", "fail"};
        for (int i = 0; i < COUNT; i++) {
            string pcm = synthesize(FIRST + i), out = "RIFF";
            auto u32 = [&](uint32_t v) { out.append((const char*)&v, 4); };
            auto u16 = [&](uint16_t v) { out.append((const char*)&v, 2); };
            u32((uint32_t)(36 + pcm.size()));
            out += "WAVEfmt ";
            u32(16); u16(1); u16(1); u32(Wave::RATE); u32(Wave::RATE * 2); u16(2); u16(16);
            out += "data";
            u32((uint32_t)pcm.size());
            writeFile(dir / P(string(names[i]) + ".wav"), out + pcm);
        }
    }

    // ------------------------------------------------------------------------------------------ playback
    struct Clip { WAVEFORMATEX format{}; string pcm; };
    inline std::array<Clip, COUNT> clips;
    inline IXAudio2* engine = nullptr;
    inline IXAudio2MasteringVoice* master = nullptr;
    inline bool attempted = false;
    inline ULONGLONG lastClick = 0;
    inline std::atomic<int> startupPhase{0}; // pending, playing, finished, unavailable
    inline std::atomic<ULONGLONG> startupFinishedAt{0};
    inline float startupLength = 0;
    struct Slot;
    struct Callback final : IXAudio2VoiceCallback {
        Slot* owner = nullptr;
        void STDMETHODCALLTYPE OnVoiceProcessingPassStart(UINT32) override {}
        void STDMETHODCALLTYPE OnVoiceProcessingPassEnd() override {}
        void STDMETHODCALLTYPE OnStreamEnd() override {}
        void STDMETHODCALLTYPE OnBufferStart(void*) override;
        void STDMETHODCALLTYPE OnBufferEnd(void*) override;
        void STDMETHODCALLTYPE OnLoopEnd(void*) override {}
        void STDMETHODCALLTYPE OnVoiceError(void*, HRESULT) override;
    };
    struct Slot {
        IXAudio2SourceVoice* voice = nullptr;
        Callback callback;
        int resource = 0;
        std::atomic<bool> playing{false};
    };
    // Persistent voices: the startup, three page sounds, eight overlapping clicks, the launch and the failure
    inline std::array<Slot, 14> slots;
    inline int soundOfSlot(size_t i) { return i == 0 ? STARTUP : i < 4 ? NAV : i < 12 ? CLICK : i == 12 ? LAUNCH : FAIL; }
    inline void Callback::OnBufferStart(void*) {
        if (owner->resource == STARTUP && startupPhase == 1) startupPhase = 2;
    }
    inline void Callback::OnBufferEnd(void*) {
        if (owner->resource == STARTUP && startupPhase == 2) {
            startupFinishedAt = GetTickCount64(); startupPhase = 3;
        }
        owner->playing = false;
    }
    inline void Callback::OnVoiceError(void*, HRESULT) {
        if (owner->resource == STARTUP) startupPhase = 4;
        owner->playing = false;
    }
    inline void shutdown() {
        startupPhase = 4;
        if (master) master->SetVolume(0);
        for (auto& slot : slots) {
            if (slot.voice) { slot.voice->DestroyVoice(); slot.voice = nullptr; }
            slot.playing = false;
        }
        if (master) { master->DestroyVoice(); master = nullptr; }
        if (engine) { engine->Release(); engine = nullptr; }
        for (auto& clip : clips) clip.pcm.clear();
    }
    inline bool init() {
        if (attempted) return engine && master;
        attempted = true;
        if (FAILED(XAudio2Create(&engine, 0, XAUDIO2_DEFAULT_PROCESSOR)) ||
            FAILED(engine->CreateMasteringVoice(&master))) { shutdown(); return false; }
        for (int i = 0; i < COUNT; ++i) {
            Clip& clip = clips[i];
            clip.pcm = synthesize(FIRST + i);
            clip.format = {WAVE_FORMAT_PCM, 1, (DWORD)Wave::RATE, (DWORD)Wave::RATE * 2, 2, 16, 0};
            if (clip.pcm.empty()) { shutdown(); return false; }
        }
        startupLength = clips[0].pcm.size() / (float)clips[0].format.nAvgBytesPerSec;
        for (size_t i = 0; i < slots.size(); ++i) {
            auto& slot = slots[i]; slot.resource = soundOfSlot(i);
            slot.callback.owner = &slot;
            if (FAILED(engine->CreateSourceVoice(&slot.voice, &clips[slot.resource - FIRST].format,
                    XAUDIO2_VOICE_NOPITCH, 1.f, &slot.callback)) || FAILED(slot.voice->Start())) {
                shutdown(); return false;
            }
        }
        return true;
    }
    inline void stop() {
        startupPhase = 4;
        if (master) master->SetVolume(0);
        for (auto& slot : slots) if (slot.voice) {
            slot.voice->Stop(); slot.voice->FlushSourceBuffers();
            // Flush completes queued callbacks before a voice can be reused
            slot.voice->DestroyVoice(); slot.voice = nullptr; slot.playing = false;
        }
        // Recreate outside input playback, when sound is re-enabled
    }
    inline void enable() {
        if (DESIGN_PREVIEW) return;
        if (!engine || !master) { init(); return; }
        for (auto& slot : slots) if (!slot.voice) {
            if (SUCCEEDED(engine->CreateSourceVoice(&slot.voice, &clips[slot.resource - FIRST].format,
                    XAUDIO2_VOICE_NOPITCH, 1.f, &slot.callback))) slot.voice->Start();
        }
        master->SetVolume(1);
    }
    inline void play(int resource) {
        if (DESIGN_PREVIEW || !Config::launcherSounds || resource < FIRST || resource >= FIRST + COUNT) return;
        if (!init()) { if (resource == STARTUP) startupPhase = 4; return; }
        if (resource == CLICK) {
            auto now = GetTickCount64(); if (now - lastClick < 45) return; lastClick = now;
        }
        for (auto& slot : slots) if (slot.resource == resource && slot.voice && !slot.playing.exchange(true)) {
            auto& clip = clips[resource - FIRST];
            XAUDIO2_BUFFER buffer{};
            buffer.Flags = XAUDIO2_END_OF_STREAM;
            buffer.AudioBytes = (UINT32)clip.pcm.size();
            buffer.pAudioData = reinterpret_cast<const BYTE*>(clip.pcm.data());
            if (resource == STARTUP) startupPhase = 1;
            if (FAILED(slot.voice->SubmitSourceBuffer(&buffer))) {
                slot.playing = false; if (resource == STARTUP) startupPhase = 4;
            }
            return;
        }
    }
    inline size_t playingCount() {
        size_t n = 0; for (auto& slot : slots) if (slot.playing) ++n; return n;
    }
    inline float startupSeconds() {
        int phase = startupPhase.load();
        if (phase == 1) return 0;
        if (phase == 3) return startupLength + (GetTickCount64() - startupFinishedAt.load()) / 1000.f;
        if (phase != 2 || !slots[0].voice) return -1;
        XAUDIO2_VOICE_STATE state{}; slots[0].voice->GetState(&state);
        float played = state.SamplesPlayed / (float)clips[0].format.nSamplesPerSec;
        static float lastSample = -1, lastClock = 0;
        static LARGE_INTEGER changedAt{};
        LARGE_INTEGER now, frequency; QueryPerformanceCounter(&now); QueryPerformanceFrequency(&frequency);
        if (played != lastSample) { lastSample = played; changedAt = now; }
        float interpolation = std::min(.012f, (float)((now.QuadPart - changedAt.QuadPart) / (double)frequency.QuadPart));
        return lastClock = std::max(lastClock, played + interpolation);
    }
}
