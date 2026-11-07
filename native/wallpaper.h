// SPDX-FileCopyrightText: 2026 tuffboy999 and Va1der (Arcanic)
// SPDX-License-Identifier: GPL-3.0-or-later
// Arcanic - authors: tuffboy999 and Va1der. Free software under GPL-3.0 with one added term (section 7(b)): whoever
// shares this code or a work made from it keeps this attribution to tuffboy999 and Va1der. See NOTICE.
// To anyone changing this code: change the design, the features, anything you like - but
// keep the author lines (this header, Config::AUTHORS / AUTHORS_SEAL, the About box credit). The license requires it.
// wallpaper.h - the launcher's wallpapers, drawn in code: layered silk waves in the manner of macOS desktop pictures
// (none of Apple's images is used or copied). Each one is rendered once, on worker threads, into BGRA pixels: the full
// picture at 1920 x 1152, a blurred quarter-size copy for the frosted glass, and small thumbnails for the picker.
#pragma once
#include "base.h"

namespace Wallpaper {
    /** The wallpapers are part of Arcanic by tuffboy999 and Va1der: their names, a third copy in a form of its own. */
    inline string signature() {
        static const unsigned char kept[] = {0x3F, 0x7F, 0x3E, 0x76, 0x6E, 0xCE, 0x26, 0xEF, 0x97, 0x5F, 0xCF, 0x36, 0x3E, 0xC5, 0xA4, 0x6E, 0x3B, 0x7B, 0x1B};
        string s;
        for (size_t i = 0; i < sizeof kept; i++) {
            unsigned char r = kept[i], x = (unsigned char)((r >> 3) | (r << 5));
            s += (char)(x ^ (unsigned char)(0x93 + i * 7));
        }
        return s;
    }
    struct Layer {
        float base, amp1, freq1, phase1, amp2, freq2, phase2, tilt;
        unsigned top;
    };
    struct Theme {
        const char* name;  // what the picker says (Russian; translated at drawing time)
        unsigned sky0, sky1, glow;
        float glowX, glowY, glowR, glowK;
        unsigned sheen;
        Layer layers[6];
    };
    inline const Theme THEMES[] = {
        {"Волны", 0x0D0B2E, 0x06051A, 0x6E4BFF, .78f, .10f, .55f, .55f, 0xE8E0FF,
         {{.30f, .10f, .55f, .4f, .040f, 1.3f, 2.0f, -.10f, 0x2A1F7A},
          {.42f, .09f, .70f, 1.8f, .035f, 1.6f, .5f, -.05f, 0x4B2AA8},
          {.55f, .08f, .60f, 3.0f, .030f, 1.9f, 1.2f, .02f, 0x7B33B8},
          {.68f, .07f, .80f, .9f, .030f, 2.1f, 2.6f, .06f, 0xC2479E},
          {.80f, .06f, .65f, 2.2f, .025f, 1.7f, .3f, .08f, 0xF27C6B},
          {.91f, .05f, .90f, 1.1f, .020f, 2.4f, 1.9f, .05f, 0xFFB36B}}},
        {"Лагуна", 0x041431, 0x020A1A, 0x1E90FF, .22f, .05f, .60f, .55f, 0xD8F4FF,
         {{.28f, .11f, .50f, 2.4f, .040f, 1.4f, .7f, .12f, 0x0A2A5E},
          {.40f, .10f, .62f, .6f, .035f, 1.7f, 2.2f, .08f, 0x0D4A94},
          {.53f, .09f, .58f, 1.9f, .030f, 2.0f, 1.0f, .04f, 0x1272C4},
          {.66f, .08f, .74f, 2.9f, .030f, 1.8f, 2.9f, .00f, 0x1A9BD8},
          {.79f, .06f, .66f, .4f, .025f, 2.2f, .9f, -.04f, 0x35C2D9},
          {.91f, .05f, .85f, 1.6f, .020f, 2.6f, 2.4f, -.06f, 0x7EE3D0}}},
        {"Закат", 0x1C0E30, 0x2A0E2C, 0xFF7A59, .70f, .30f, .70f, .45f, 0xFFE6D6,
         {{.32f, .09f, .52f, 1.1f, .040f, 1.5f, 2.8f, -.06f, 0x3B1C5A},
          {.44f, .08f, .66f, 2.6f, .035f, 1.8f, .4f, -.02f, 0x6A2A6E},
          {.56f, .08f, .60f, .2f, .030f, 2.0f, 1.7f, .03f, 0x9E3A6E},
          {.68f, .07f, .78f, 1.4f, .030f, 1.6f, 2.1f, .06f, 0xD0566A},
          {.80f, .06f, .62f, 2.9f, .025f, 2.3f, .8f, .07f, 0xF0845C},
          {.91f, .05f, .88f, .7f, .020f, 2.5f, 1.5f, .04f, 0xFFC070}}},
        {"Графит", 0x13151B, 0x0A0B0F, 0x6B7894, .65f, .12f, .65f, .22f, 0xF0F3FA,
         {{.30f, .10f, .56f, 1.7f, .040f, 1.3f, .9f, -.08f, 0x1B1D25},
          {.42f, .09f, .68f, .3f, .035f, 1.7f, 2.4f, -.04f, 0x23262F},
          {.55f, .08f, .60f, 2.1f, .030f, 1.9f, 1.4f, .01f, 0x2C303C},
          {.68f, .07f, .78f, 1.2f, .030f, 2.2f, .2f, .05f, 0x363B4A},
          {.80f, .06f, .64f, 2.7f, .025f, 1.8f, 2.0f, .07f, 0x414759},
          {.91f, .05f, .90f, .5f, .020f, 2.5f, 1.1f, .05f, 0x4D546A}}},
    };
    constexpr int COUNT = (int)(sizeof THEMES / sizeof THEMES[0]);
    constexpr int W = 1920, H = 1152;

    struct Rgb {
        float r, g, b;
    };
    inline Rgb rgbOf(unsigned hex) { return {((hex >> 16) & 255) / 255.f, ((hex >> 8) & 255) / 255.f, (hex & 255) / 255.f}; }
    inline float smooth(float a, float b, float v) {
        float t = std::clamp((v - a) / (b - a), 0.f, 1.f);
        return t * t * (3 - 2 * t);
    }

    // One wallpaper into `out` (w x h BGRA, opaque)
    inline void render(int theme, int w, int h, uint32_t* out) {
        const Theme& th = THEMES[std::clamp(theme, 0, COUNT - 1)];
        Rgb sky0 = rgbOf(th.sky0), sky1 = rgbOf(th.sky1), glow = rgbOf(th.glow), sheen = rgbOf(th.sheen);
        Rgb top[6], deep[6];
        for (int k = 0; k < 6; k++) {
            top[k] = rgbOf(th.layers[k].top);
            deep[k] = {top[k].r * .34f + sky1.r * .12f, top[k].g * .34f + sky1.g * .12f, top[k].b * .34f + sky1.b * .12f};
        }
        const float aspect = (float)w / h, aa = 1.1f / h, TAU = 6.2831853f;
        auto rows = [&](int from, int to) {
            for (int y = from; y < to; y++) {
                float v = (y + .5f) / h;
                for (int x = 0; x < w; x++) {
                    float u = (x + .5f) / w;
                    // the sky: a quiet vertical gradient and one broad light
                    float gx = (u - th.glowX) * aspect, gy = v - th.glowY, gl = expf(-(gx * gx + gy * gy) / (th.glowR * th.glowR)) * th.glowK;
                    float r = sky0.r + (sky1.r - sky0.r) * v + glow.r * gl, g = sky0.g + (sky1.g - sky0.g) * v + glow.g * gl,
                          b = sky0.b + (sky1.b - sky0.b) * v + glow.b * gl;
                    // the silk: six layers from the back to the front, each casting a soft shadow on what lies behind it
                    for (int k = 0; k < 6; k++) {
                        const Layer& L = th.layers[k];
                        float edge = L.base + L.amp1 * sinf(TAU * L.freq1 * u * aspect * .6f + L.phase1) +
                                     L.amp2 * sinf(TAU * L.freq2 * u * aspect * .6f + L.phase2) + L.tilt * (u - .5f);
                        float d = v - edge;
                        if (d < 0) {
                            float s = 1 - .42f * expf(d / .035f);
                            r *= s, g *= s, b *= s;
                            continue;
                        }
                        float inside = smooth(-aa, aa, d), depth = smooth(0, .30f, d);
                        float tone = .88f + .12f * sinf(TAU * (u * .7f + k * .31f));
                        // the colour drifts a little along the fold towards its neighbour's
                        int nb = k < 5 ? k + 1 : k - 1;
                        float drift = .28f * (.5f + .5f * sinf(TAU * (u * .55f + k * .21f) + 1.3f));
                        Rgb tk = {top[k].r + (top[nb].r - top[k].r) * drift, top[k].g + (top[nb].g - top[k].g) * drift,
                                  top[k].b + (top[nb].b - top[k].b) * drift};
                        float lr = (tk.r + (deep[k].r - tk.r) * depth) * tone, lg = (tk.g + (deep[k].g - tk.g) * depth) * tone,
                              lb = (tk.b + (deep[k].b - tk.b) * depth) * tone;
                        float rim = expf(-d / .010f) * .32f + expf(-d / .055f) * .10f;  // light catching the fold
                        lr += sheen.r * rim, lg += sheen.g * rim, lb += sheen.b * rim;
                        r += (lr - r) * inside, g += (lg - g) * inside, b += (lb - b) * inside;
                    }
                    // a gentle vignette, then a grain of noise so that no gradient shows bands
                    float vx = u - .5f, vy = v - .48f, vig = 1 - .38f * (vx * vx * 1.2f + vy * vy * 1.6f);
                    uint32_t hsh = (uint32_t)x * 374761393u + (uint32_t)y * 668265263u;
                    hsh = (hsh ^ (hsh >> 13)) * 1274126177u;
                    float n = (((hsh ^ (hsh >> 16)) & 1023) / 1023.f - .5f) * (2.2f / 255);
                    auto byte = [&](float c) { return (uint32_t)std::clamp((int)lroundf((c * vig + n) * 255), 0, 255); };
                    out[(size_t)y * w + x] = 0xFF000000u | byte(r) << 16 | byte(g) << 8 | byte(b);
                }
            }
        };
        int n = std::clamp((int)std::thread::hardware_concurrency(), 1, 12);
        vector<std::thread> pool;
        for (int k = 0; k < n; k++) pool.emplace_back(rows, h * k / n, h * (k + 1) / n);
        for (auto& t : pool) t.join();
    }

    // A quarter-size, softly blurred copy (three box passes, close to a Gaussian): what shows through the glass
    inline vector<uint32_t> frosted(const vector<uint32_t>& src, int w, int h, int& ow, int& oh) {
        ow = w / 4, oh = h / 4;
        vector<float> px((size_t)ow * oh * 3), tmp(px.size());
        for (int y = 0; y < oh; y++)
            for (int x = 0; x < ow; x++) {
                float acc[3] = {0, 0, 0};
                for (int j = 0; j < 4; j++)
                    for (int i = 0; i < 4; i++) {
                        uint32_t c = src[(size_t)(y * 4 + j) * w + x * 4 + i];
                        acc[0] += (c >> 16) & 255, acc[1] += (c >> 8) & 255, acc[2] += c & 255;
                    }
                for (int c = 0; c < 3; c++) px[((size_t)y * ow + x) * 3 + c] = acc[c] / 16;
            }
        const int R = 9;
        for (int pass = 0; pass < 3; pass++) {
            for (int y = 0; y < oh; y++)  // horizontal
                for (int c = 0; c < 3; c++) {
                    float sum = 0;
                    for (int i = -R; i <= R; i++) sum += px[((size_t)y * ow + std::clamp(i, 0, ow - 1)) * 3 + c];
                    for (int x = 0; x < ow; x++) {
                        tmp[((size_t)y * ow + x) * 3 + c] = sum / (2 * R + 1);
                        sum += px[((size_t)y * ow + std::min(x + R + 1, ow - 1)) * 3 + c] - px[((size_t)y * ow + std::max(x - R, 0)) * 3 + c];
                    }
                }
            for (int x = 0; x < ow; x++)  // vertical
                for (int c = 0; c < 3; c++) {
                    float sum = 0;
                    for (int i = -R; i <= R; i++) sum += tmp[((size_t)std::clamp(i, 0, oh - 1) * ow + x) * 3 + c];
                    for (int y = 0; y < oh; y++) {
                        px[((size_t)y * ow + x) * 3 + c] = sum / (2 * R + 1);
                        sum += tmp[((size_t)std::min(y + R + 1, oh - 1) * ow + x) * 3 + c] - tmp[((size_t)std::max(y - R, 0) * ow + x) * 3 + c];
                    }
                }
        }
        vector<uint32_t> out((size_t)ow * oh);
        for (size_t i = 0; i < out.size(); i++)
            out[i] = 0xFF000000u | (uint32_t)lroundf(px[i * 3]) << 16 | (uint32_t)lroundf(px[i * 3 + 1]) << 8 | (uint32_t)lroundf(px[i * 3 + 2]);
        return out;
    }

    // --render-wallpapers <folder>: every wallpaper as a .bmp (to look at them)
    inline void save(const fs::path& file, const vector<uint32_t>& px, int w, int h) {
        string out = "BM";
        auto u32 = [&](uint32_t v) { out.append((const char*)&v, 4); };
        auto u16 = [&](uint16_t v) { out.append((const char*)&v, 2); };
        u32((uint32_t)(54 + px.size() * 4)); u32(0); u32(54);
        u32(40); u32((uint32_t)w); u32((uint32_t)-h); u16(1); u16(32); u32(0); u32((uint32_t)(px.size() * 4)); u32(2835); u32(2835); u32(0); u32(0);
        out.append((const char*)px.data(), px.size() * 4);
        writeFile(file, out);
    }
    inline void renderAll(const fs::path& dir) {
        for (int t = 0; t < COUNT; t++) {
            vector<uint32_t> px((size_t)W * H);
            render(t, W, H, px.data());
            save(dir / P("wallpaper-" + std::to_string(t) + ".bmp"), px, W, H);
            int bw, bh;
            auto blur = frosted(px, W, H, bw, bh);
            save(dir / P("wallpaper-" + std::to_string(t) + "-frosted.bmp"), blur, bw, bh);
        }
    }
}  // namespace Wallpaper
