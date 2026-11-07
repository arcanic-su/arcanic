// SPDX-FileCopyrightText: 2026 tuffboy999 and Va1der (Arcanic)
// SPDX-License-Identifier: GPL-3.0-or-later
// Arcanic - authors: tuffboy999 and Va1der. Free software under GPL-3.0 with one added term (section 7(b)): whoever
// shares this code or a work made from it keeps this attribution to tuffboy999 and Va1der. See NOTICE.
// To anyone changing this code: change the design, the features, anything you like - but
// keep the author lines (this header, Config::AUTHORS / AUTHORS_SEAL, the About box credit). The license requires it.
// gfx.h - drawing. Direct2D does the shapes on the graphics card, DirectWrite sets the text (Segoe UI, the system's
// own font), WIC decodes pictures. Everything is positioned in the 1200x750 layout
// and scaled once, by the window's transform, so it looks the same at any screen density.
#pragma once
#include "core.h"
#include <dwrite_3.h>

template <class T> static void release(T*& p) {
    if (p) {
        p->Release();
        p = nullptr;
    }
}

struct Col {
    float r = 0, g = 0, b = 0, a = 1;
};
static Col rgb(unsigned hex, float a = 1) {
    return {((hex >> 16) & 255) / 255.f, ((hex >> 8) & 255) / 255.f, (hex & 255) / 255.f, a};
}
static Col rgba(int r, int g, int b, int a) {
    return {r / 255.f, g / 255.f, b / 255.f, a / 255.f};
}
static float clamp01(float x) {
    return x < 0 ? 0 : x > 1 ? 1 : x;
}
// The colour with its opacity multiplied by k
static Col fade(Col c, float k) {
    c.a *= clamp01(k);
    return c;
}
// The colour with its opacity set to a
static Col alpha(Col c, float a) {
    c.a = clamp01(a);
    return c;
}
static Col mix(Col a, Col b, float t) {
    t = clamp01(t);
    return {a.r + (b.r - a.r) * t, a.g + (b.g - a.g) * t, a.b + (b.b - a.b) * t, a.a + (b.a - a.a) * t};
}

/** Who drew this interface (Arcanic, by tuffboy999 and Va1der): a second copy of the names, kept in its own form. */
inline string credit() {
    static const unsigned char kept[] = {0xA3, 0x96, 0x95, 0x62, 0x92, 0x87, 0x51, 0x57, 0x51, 0x6A, 0x6A, 0x6A, 0xAA, 0xA0, 0x93, 0x97, 0x97, 0xA6, 0xA5};
    string s;
    for (size_t i = sizeof kept; i-- > 0;) s += (char)((kept[i] + 256 - 0x31) & 0xFF);
    return s;
}

namespace Pal {
    // macOS, dark appearance: system blue for actions, label colours for text, glass over the wallpaper for surfaces
    static const Col ACCENT = rgb(0x409CFF), ACCENT2 = rgb(0x8EC1FF), ACCENT_HI = rgb(0x5AABFF), LILAC = rgb(0xC6E0FF);
    static const Col TEXT = rgb(0xF5F5F7), MUTED = rgb(0x9E9EA6), DIM = rgb(0x6E6E76), WHITE = rgb(0xFFFFFF);
    static const Col OK = rgb(0x30D158), ERR = rgb(0xFF453A), WARN = rgb(0xFF9F0A);
    static const Col BG = rgb(0x0C0C10), CARD = rgb(0x1C1C22), CARD2 = rgb(0x26262E), CARD3 = rgb(0x30303A),
                     WELL = rgb(0x16161C), ACTION = rgb(0x0A84FF);
    static const Col CHIP = rgba(255, 255, 255, 22), EDGE = rgba(255, 255, 255, 18), RIM = rgba(255, 255, 255, 28);
    static const Col SEPARATOR = rgba(255, 255, 255, 20), TINT = rgba(22, 22, 28, 150);  // a hairline; the glass tint
}  // namespace Pal

enum Wt { MEDIUM = 500, SEMI = 600, BOLD = 700, BLACK = 800 };

static string resourceBytes(int id) {
    HRSRC r = FindResourceW(nullptr, MAKEINTRESOURCEW(id), RT_RCDATA);
    if (!r) return "";
    HGLOBAL g = LoadResource(nullptr, r);
    return g ? string((const char*)LockResource(g), SizeofResource(nullptr, r)) : "";
}

struct Gfx {
    ID2D1Factory* d2d = nullptr;
    IDWriteFactory* dw = nullptr;
    IWICImagingFactory* wic = nullptr;
    ID2D1HwndRenderTarget* win = nullptr;
    ID2D1RenderTarget* rt = nullptr;
    ID2D1SolidColorBrush* brush = nullptr;
    ID2D1StrokeStyle* round = nullptr;
    ID2D1StrokeStyle* sharp = nullptr;  // mitred joins, flat ends: the outlines and the icons
    HWND hwnd = nullptr;
    float scale = 1;                    // layout units -> pixels
    bool raw = false;                   // set while drawing what the player typed: it is never translated
    bool accountFont = false;           // quieter system typography for the account picker
    bool headline = false;
    IDWriteFontCollection1* brandFonts = nullptr;
    IDWriteInMemoryFontFileLoader* brandLoader = nullptr;  // kept for as long as the fonts are used
    std::map<int, wstring> brandFamily;                     // weight -> the family name it goes by in the collection
    static constexpr float FIT = .94f;  // the layout was tuned with this: every size is drawn a touch smaller

    struct Layout {
        IDWriteTextLayout* l = nullptr;
        float width = 0, baseline = 0;
    };
    std::unordered_map<string, Layout> layouts;
    std::map<std::pair<int, int>, IDWriteTextFormat*> formats;
    std::unordered_map<string, ID2D1Bitmap*> bitmaps;  // device pictures, by key; dropped if the device is lost

    bool init(HWND h) {
        hwnd = h;
        if (FAILED(D2D1CreateFactory(D2D1_FACTORY_TYPE_SINGLE_THREADED, &d2d))) return false;
        if (FAILED(DWriteCreateFactory(DWRITE_FACTORY_TYPE_SHARED, __uuidof(IDWriteFactory), (IUnknown**)&dw)))
            return false;
        CoCreateInstance(CLSID_WICImagingFactory, nullptr, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&wic));
        D2D1_STROKE_STYLE_PROPERTIES sp = D2D1::StrokeStyleProperties(D2D1_CAP_STYLE_ROUND, D2D1_CAP_STYLE_ROUND,
                                                                      D2D1_CAP_STYLE_ROUND, D2D1_LINE_JOIN_ROUND);
        d2d->CreateStrokeStyle(sp, nullptr, 0, &round);
        D2D1_STROKE_STYLE_PROPERTIES mp = D2D1::StrokeStyleProperties(D2D1_CAP_STYLE_SQUARE, D2D1_CAP_STYLE_SQUARE,
                                                                      D2D1_CAP_STYLE_SQUARE, D2D1_LINE_JOIN_MITER_OR_BEVEL, 4);
        d2d->CreateStrokeStyle(mp, nullptr, 0, &sharp);
        loadBrandFonts();
        return true;
    }
    bool target() {
        if (win) return true;
        RECT rc;
        GetClientRect(hwnd, &rc);
        D2D1_PIXEL_FORMAT pf = D2D1::PixelFormat(DXGI_FORMAT_B8G8R8A8_UNORM, D2D1_ALPHA_MODE_PREMULTIPLIED);
        D2D1_HWND_RENDER_TARGET_PROPERTIES hp = D2D1::HwndRenderTargetProperties(hwnd, D2D1::SizeU(rc.right, rc.bottom),
                                                                                 D2D1_PRESENT_OPTIONS_RETAIN_CONTENTS);
        bool soft = Config::software || Config::softwareNow || lost >= 2;
        if (soft || FAILED(d2d->CreateHwndRenderTarget(
                        D2D1::RenderTargetProperties(D2D1_RENDER_TARGET_TYPE_DEFAULT, pf, 96, 96), hp, &win))) {
            win = nullptr;
            D2D1_RENDER_TARGET_USAGE usage = Config::softwareNow
                                                 ? D2D1_RENDER_TARGET_USAGE_GDI_COMPATIBLE
                                                 : D2D1_RENDER_TARGET_USAGE_NONE;
            if (FAILED(d2d->CreateHwndRenderTarget(
                    D2D1::RenderTargetProperties(D2D1_RENDER_TARGET_TYPE_SOFTWARE, pf, 96, 96, usage), hp, &win)))
                return false;
            soft = true;
        }
        log(soft ? "drawing in software" : "drawing on the graphics card");
        rt = win;
        rt->CreateSolidColorBrush(D2D1::ColorF(0, 0, 0, 1), &brush);
        // ClearType with a firmer contrast: light text on the dark panels stays sharp instead of going soft and thin
        rt->SetTextAntialiasMode(D2D1_TEXT_ANTIALIAS_MODE_CLEARTYPE);
        IDWriteRenderingParams *system = nullptr, *crisp = nullptr;
        if (SUCCEEDED(dw->CreateRenderingParams(&system))) {
            if (SUCCEEDED(dw->CreateCustomRenderingParams(system->GetGamma(), std::max(1.f, system->GetEnhancedContrast()),
                                                          system->GetClearTypeLevel(), system->GetPixelGeometry(),
                                                          DWRITE_RENDERING_MODE_NATURAL_SYMMETRIC, &crisp)))
                rt->SetTextRenderingParams(crisp);
            release(system);
            release(crisp);
        }
        return true;
    }
    void dropDevice() {
        for (auto& b : bitmaps)
            if (b.second) b.second->Release();
        bitmaps.clear();
        release(frostBrush);
        dropBrushes();
        release(brush);
        release(win);
        rt = nullptr;
    }
    void resize(int w, int h) {
        if (win) win->Resize(D2D1::SizeU(w, h));
    }
    void begin() {
        rt->BeginDraw();
        rt->SetTransform(D2D1::Matrix3x2F::Identity());
        rt->Clear(D2D1::ColorF(0x0C0C10, 1.f));
        rt->SetTransform(D2D1::Matrix3x2F::Scale(scale, scale));
    }
    int lost = 0;  // how many times the graphics device was lost
    // A line in ~/.arcanic/launcher.log: which way the window is drawn and anything that went wrong with it
    static void log(const string& line) { Log::info("gfx: " + line); }
    // --offscreen only: the finished frame goes into this .bmp file (for automated checks), then it is cleared
    fs::path snapTo;
    void snapshot() {
        ID2D1GdiInteropRenderTarget* gi = nullptr;
        HDC dc = nullptr;
        if (FAILED(rt->QueryInterface(__uuidof(ID2D1GdiInteropRenderTarget), (void**)&gi))) {
            snapTo.clear();
            return;
        }
        RECT rc;
        GetClientRect(hwnd, &rc);
        int w = rc.right, h = rc.bottom;
        HRESULT hr = gi->GetDC(D2D1_DC_INITIALIZE_MODE_COPY, &dc);
        if (FAILED(hr)) log(fmt("snapshot: GetDC refused 0x%08X", (unsigned)hr));
        if (SUCCEEDED(hr)) {
            BITMAPINFO bi = {};
            bi.bmiHeader = {sizeof(BITMAPINFOHEADER), w, -h, 1, 32, BI_RGB};
            void* bits = nullptr;
            HDC mem = CreateCompatibleDC(dc);
            HBITMAP hb = CreateDIBSection(dc, &bi, DIB_RGB_COLORS, &bits, nullptr, 0);
            HGDIOBJ old = SelectObject(mem, hb);
            BitBlt(mem, 0, 0, w, h, dc, 0, 0, SRCCOPY);
            SelectObject(mem, old);
            BITMAPFILEHEADER fh = {0x4D42, (DWORD)(sizeof fh + sizeof bi.bmiHeader + w * h * 4), 0, 0,
                                   (DWORD)(sizeof fh + sizeof bi.bmiHeader)};
            string out((const char*)&fh, sizeof fh);
            out.append((const char*)&bi.bmiHeader, sizeof bi.bmiHeader);
            out.append((const char*)bits, (size_t)w * h * 4);
            writeFile(snapTo, out);
            DeleteObject(hb);
            DeleteDC(mem);
            gi->ReleaseDC(nullptr);
        }
        gi->Release();
        snapTo.clear();
    }
    bool end() {
        if (!snapTo.empty()) snapshot();
        HRESULT hr = rt->EndDraw();
        if (hr == D2DERR_RECREATE_TARGET) {
            lost++;
            log(fmt("graphics device lost (%d)", lost));
            dropDevice();
            return false;
        }
        if (FAILED(hr) && !complained) {
            complained = true;
            log(fmt("drawing reported 0x%08X", (unsigned)hr));
        }
        return true;
    }
    bool complained = false;

    // ------------------------------------------------------------------------------------------ shapes
    ID2D1SolidColorBrush* use(Col c) {
        brush->SetColor(D2D1::ColorF(c.r, c.g, c.b, c.a));
        return brush;
    }
    void rect(float x, float y, float w, float h, Col c) {
        if (c.a > 0) rt->FillRectangle(D2D1::RectF(x, y, x + w, y + h), use(c));
    }
    // ---- the Arcanic shape: corners cut, not rounded
    struct ShapeKey {
        int x, y, w, h, r;
        bool operator==(const ShapeKey& o) const { return x == o.x && y == o.y && w == o.w && h == o.h && r == o.r; }
    };
    struct ShapeHash {
        size_t operator()(const ShapeKey& k) const {
            return ((size_t)k.x * 73856093) ^ ((size_t)k.y * 19349663) ^ ((size_t)k.w * 83492791) ^ ((size_t)k.h * 2971215073u) ^ (size_t)k.r;
        }
    };
    std::unordered_map<ShapeKey, ID2D1Geometry*, ShapeHash> shapes;
    bool rounded = true;
    void dropShapes() {
        for (auto& s : shapes) release(s.second);
        shapes.clear();
    }
    ID2D1Geometry* shape(float x, float y, float w, float h, float r) {
        if (w <= 0 || h <= 0) return nullptr;
        auto q = [](float v) { return (int)lroundf(v * 4); };
        ShapeKey key{q(x), q(y), q(w), q(h), q(r)};
        auto hit = shapes.find(key);
        if (hit != shapes.end()) return hit->second;
        if (shapes.size() > 6000) dropShapes();
        ID2D1Geometry* g = nullptr;
        float half = std::min(w, h) / 2;
        if (r >= half - .5f || rounded) {
            ID2D1RoundedRectangleGeometry* pill = nullptr;
            float rad = std::min(r, half);
            d2d->CreateRoundedRectangleGeometry(D2D1::RoundedRect(D2D1::RectF(x, y, x + w, y + h), rad, rad), &pill);
            g = pill;
        } else {
            float cut = std::min(r * .85f, half), bevel = std::min(cut * .32f, half);
            ID2D1PathGeometry* path = nullptr;
            ID2D1GeometrySink* sink = nullptr;
            if (SUCCEEDED(d2d->CreatePathGeometry(&path)) && SUCCEEDED(path->Open(&sink))) {
                D2D1_POINT_2F pts[] = {{x + w - bevel, y}, {x + w, y + bevel}, {x + w, y + h - cut}, {x + w - cut, y + h},
                                       {x + bevel, y + h},  {x, y + h - bevel}, {x, y + cut}};
                sink->BeginFigure(D2D1::Point2F(x + cut, y), D2D1_FIGURE_BEGIN_FILLED);
                sink->AddLines(pts, ARRAYSIZE(pts));
                sink->EndFigure(D2D1_FIGURE_END_CLOSED);
                sink->Close();
            }
            release(sink);
            g = path;
        }
        return shapes[key] = g;
    }
    void rr(float x, float y, float w, float h, float r, Col c) {
        if (c.a <= 0) return;
        if (r <= .01f) return rect(x, y, w, h, c);
        if (auto* g = shape(x, y, w, h, r)) rt->FillGeometry(g, use(c));
    }
    void rrLine(float x, float y, float w, float h, float r, Col c, float sw = 1) {
        if (c.a <= 0) return;
        if (auto* g = shape(x, y, w, h, r)) rt->DrawGeometry(g, use(c), sw, sharp);
    }
    // ---- glass: what lies behind a panel, blurred, then tinted - the macOS material
    ID2D1BitmapBrush* frostBrush = nullptr;
    float frostW = 0, frostH = 0;
    void setFrost(ID2D1Bitmap* b) {
        release(frostBrush);
        if (!b || !rt) return;
        D2D1_SIZE_U s = b->GetPixelSize();
        frostW = (float)s.width, frostH = (float)s.height;
        rt->CreateBitmapBrush(b, D2D1::BitmapBrushProperties(D2D1_EXTEND_MODE_CLAMP, D2D1_EXTEND_MODE_CLAMP, D2D1_BITMAP_INTERPOLATION_MODE_LINEAR),
                              &frostBrush);
    }
    // A glass panel over the wallpaper
    void glass(float x, float y, float w, float h, float r, Col tint, float a = 1, bool rim = true) {
        if (a <= .003f) return;
        if (frostBrush) {
            D2D1_MATRIX_3X2_F m;
            rt->GetTransform(&m);
            D2D1::Matrix3x2F inv = *D2D1::Matrix3x2F::ReinterpretBaseType(&m);
            if (inv.Invert()) {
                frostBrush->SetTransform(D2D1::Matrix3x2F::Scale(1000.f / frostW, 600.f / frostH) * D2D1::Matrix3x2F::Scale(scale, scale) * inv);
                frostBrush->SetOpacity(clamp01(a));
                fillShape(x, y, w, h, r, frostBrush);
            }
        }
        rr(x, y, w, h, r, fade(tint, a));
        if (rim) {
            rrGrad(x, y, w, std::min(h, 36.f), r, alpha(Pal::WHITE, .05f * a), alpha(Pal::WHITE, 0), 0, y, 0, y + std::min(h, 36.f));
            rrLine(x + .5f, y + .5f, w - 1, h - 1, std::max(0.f, r - .5f), alpha(Pal::WHITE, .10f * a), 1);
        }
    }
    // Any brush (a picture, a gradient) inside the Arcanic shape
    void fillShape(float x, float y, float w, float h, float r, ID2D1Brush* b) {
        if (auto* g = shape(x, y, w, h, r)) rt->FillGeometry(g, b);
    }
    void circle(float cx, float cy, float r, Col c) {
        if (c.a > 0) rt->FillEllipse(D2D1::Ellipse(D2D1::Point2F(cx, cy), r, r), use(c));
    }
    void ring(float cx, float cy, float r, Col c, float sw) {
        if (c.a > 0) rt->DrawEllipse(D2D1::Ellipse(D2D1::Point2F(cx, cy), r, r), use(c), sw, round);
    }
    void line(float x1, float y1, float x2, float y2, Col c, float sw) {
        rt->DrawLine(D2D1::Point2F(x1, y1), D2D1::Point2F(x2, y2), use(c), sw, round);
    }
    // A rounded rectangle filled with a gradient from c1 (at x1,y1) to c2 (at x2,y2)
    // gradient brushes are made once per pair of colours and reused every frame (a fade only changes their opacity)
    std::unordered_map<uint64_t, ID2D1LinearGradientBrush*> linearBrushes;
    std::unordered_map<uint32_t, ID2D1RadialGradientBrush*> radialBrushes;
    static uint32_t packed(Col c, float scale) {
        auto byte = [](float v) { return (uint32_t)lroundf(clamp01(v) * 255); };
        return byte(c.r) << 24 | byte(c.g) << 16 | byte(c.b) << 8 | byte(scale > 0 ? c.a / scale : 0);
    }
    static Col unpacked(uint32_t v) {
        return {(v >> 24 & 255) / 255.f, (v >> 16 & 255) / 255.f, (v >> 8 & 255) / 255.f, (v & 255) / 255.f};
    }
    void dropBrushes() {
        for (auto& b : linearBrushes) release(b.second);
        for (auto& b : radialBrushes) release(b.second);
        linearBrushes.clear();
        radialBrushes.clear();
        release(edgeMask);
        edgeMask = nullptr;
    }
    // A mask clear at y1 and solid from y2 down: what scrolls under the toolbar fades away there, as on a Mac
    ID2D1LinearGradientBrush* edgeMask = nullptr;
    ID2D1Brush* fadeIn(float y1, float y2) {
        if (!edgeMask) {
            D2D1_GRADIENT_STOP st[2] = {{0, D2D1::ColorF(0, 0, 0, 0)}, {1, D2D1::ColorF(0, 0, 0, 1)}};
            ID2D1GradientStopCollection* sc = nullptr;
            if (SUCCEEDED(rt->CreateGradientStopCollection(st, 2, &sc)))
                rt->CreateLinearGradientBrush(D2D1::LinearGradientBrushProperties(D2D1::Point2F(0, 0), D2D1::Point2F(0, 1)), sc, &edgeMask);
            release(sc);
        }
        if (edgeMask) {
            edgeMask->SetStartPoint(D2D1::Point2F(0, y1));
            edgeMask->SetEndPoint(D2D1::Point2F(0, y2));
        }
        return edgeMask;
    }
    void rrGrad(float x, float y, float w, float h, float r, Col c1, Col c2, float x1, float y1, float x2, float y2) {
        float m = std::max(c1.a, c2.a);
        if (m <= .001f) return;
        if (linearBrushes.size() > 600) dropBrushes();
        uint64_t key = (uint64_t)packed(c1, m) << 32 | packed(c2, m);
        ID2D1LinearGradientBrush*& gb = linearBrushes[key];
        if (!gb) {
            Col a = unpacked((uint32_t)(key >> 32)), b = unpacked((uint32_t)key);
            D2D1_GRADIENT_STOP st[2] = {{0, D2D1::ColorF(a.r, a.g, a.b, a.a)}, {1, D2D1::ColorF(b.r, b.g, b.b, b.a)}};
            ID2D1GradientStopCollection* sc = nullptr;
            if (SUCCEEDED(rt->CreateGradientStopCollection(st, 2, &sc)))
                rt->CreateLinearGradientBrush(D2D1::LinearGradientBrushProperties(D2D1::Point2F(0, 0), D2D1::Point2F(1, 0)), sc, &gb);
            release(sc);
        }
        if (!gb) return;
        gb->SetStartPoint(D2D1::Point2F(x1, y1));
        gb->SetEndPoint(D2D1::Point2F(x2, y2));
        gb->SetOpacity(m);
        fillShape(x, y, w, h, r, gb);
    }
    // A round light with a soft falloff
    void radial(float cx, float cy, float r, Col c) {
        if (c.a <= .001f || r <= 0) return;
        if (radialBrushes.size() > 200) dropBrushes();
        uint32_t key = packed(c, c.a);
        ID2D1RadialGradientBrush*& gb = radialBrushes[key];
        if (!gb) {
            D2D1_GRADIENT_STOP st[3] = {{0, D2D1::ColorF(c.r, c.g, c.b, 1)},
                                        {.45f, D2D1::ColorF(c.r, c.g, c.b, .32f)},
                                        {1, D2D1::ColorF(c.r, c.g, c.b, 0)}};
            ID2D1GradientStopCollection* sc = nullptr;
            if (SUCCEEDED(rt->CreateGradientStopCollection(st, 3, &sc)))
                rt->CreateRadialGradientBrush(D2D1::RadialGradientBrushProperties(D2D1::Point2F(0, 0), D2D1::Point2F(0, 0), 1, 1), sc, &gb);
            release(sc);
        }
        if (!gb) return;
        gb->SetCenter(D2D1::Point2F(cx, cy));
        gb->SetRadiusX(r);
        gb->SetRadiusY(r);
        gb->SetOpacity(c.a);
        rt->FillEllipse(D2D1::Ellipse(D2D1::Point2F(cx, cy), r, r), gb);
    }
    // A soft halo around a rounded rectangle: stacked translucent shapes that fade outwards
    void glow(float x, float y, float w, float h, float r, Col c, float spread) {
        const int n = 7;
        for (int i = n; i >= 1; i--) {
            float s = spread * i / n, k = powf(1 - i / (n + 1.f), 1.5f) * (2.2f / n);
            rr(x - s, y - s, w + s * 2, h + s * 2, r + s, fade(c, k));
        }
    }
    void clip(float x, float y, float w, float h) {
        rt->PushAxisAlignedClip(D2D1::RectF(x, y, x + w, y + h), D2D1_ANTIALIAS_MODE_ALIASED);
    }
    void unclip() { rt->PopAxisAlignedClip(); }

    // A path made of straight lines and curves, for the icons and the backdrop's cubes
    struct Path {
        ID2D1PathGeometry* g = nullptr;
        ID2D1GeometrySink* s = nullptr;
        bool open = false;
        explicit Path(ID2D1Factory* f) {
            if (SUCCEEDED(f->CreatePathGeometry(&g))) g->Open(&s);
        }
        ~Path() {
            release(s);
            release(g);
        }
        Path& M(float x, float y) {
            if (open) s->EndFigure(D2D1_FIGURE_END_OPEN);
            s->BeginFigure(D2D1::Point2F(x, y), D2D1_FIGURE_BEGIN_FILLED);
            open = true;
            return *this;
        }
        Path& L(float x, float y) {
            s->AddLine(D2D1::Point2F(x, y));
            return *this;
        }
        Path& C(float x1, float y1, float x2, float y2, float x, float y) {
            s->AddBezier(D2D1::BezierSegment(D2D1::Point2F(x1, y1), D2D1::Point2F(x2, y2), D2D1::Point2F(x, y)));
            return *this;
        }
        Path& Z() {
            if (open) {
                s->EndFigure(D2D1_FIGURE_END_CLOSED);
                open = false;
            }
            return *this;
        }
        ID2D1PathGeometry* done() {
            if (s) {
                if (open) s->EndFigure(D2D1_FIGURE_END_OPEN);
                s->Close();
                release(s);
            }
            return g;
        }
    };
    void stroke(Path& p, Col c, float sw) {
        if (auto g = p.done()) rt->DrawGeometry(g, use(c), sw, round);
    }
    void fill(Path& p, Col c) {
        if (auto g = p.done()) rt->FillGeometry(g, use(c));
    }

    // Line icons drawn on a 24-unit grid, centred on (cx, cy) and scaled to size
    void icon(const char* name, float cx, float cy, float size, Col c, float strokePx = 1.7f) {
        if (c.a <= 0) return;
        D2D1_MATRIX_3X2_F old;
        rt->GetTransform(&old);
        float k = size / 24.f, sw = strokePx / k;
        rt->SetTransform(D2D1::Matrix3x2F::Scale(k, k) * D2D1::Matrix3x2F::Translation(cx - size / 2, cy - size / 2) *
                         old);
        string n = name;
        Path p(d2d);
        auto ln = [&](float a, float b, float x, float y) { line(a, b, x, y, c, sw); };
        if (n == "x") {
            ln(6, 6, 18, 18);
            ln(18, 6, 6, 18);
        } else if (n == "min")
            ln(6, 12, 18, 12);
        else if (n == "search") {
            ring(10.5f, 10.5f, 6.2f, c, sw);
            ln(15.2f, 15.2f, 20, 20);
        } else if (n == "copy") {
            rt->DrawRoundedRectangle(D2D1::RoundedRect(D2D1::RectF(9, 9, 20, 20), 2.5f, 2.5f), use(c), sw);
            p.M(15, 9)
                .L(15, 6)
                .C(15, 4.8f, 14.2f, 4, 13, 4)
                .L(6, 4)
                .C(4.8f, 4, 4, 4.8f, 4, 6)
                .L(4, 13)
                .C(4, 14.2f, 4.8f, 15, 6, 15)
                .L(9, 15);
            stroke(p, c, sw);
        } else if (n == "refresh") {
            p.M(19.5f, 9).C(18.2f, 5.9f, 15.3f, 4, 12, 4).C(8.5f, 4, 5.6f, 6.2f, 4.5f, 9.4f);
            stroke(p, c, sw);
            Path q(d2d);
            q.M(4.5f, 15).C(5.8f, 18.1f, 8.7f, 20, 12, 20).C(15.5f, 20, 18.4f, 17.8f, 19.5f, 14.6f);
            stroke(q, c, sw);
            Path a(d2d);
            a.M(19.8f, 4.5f).L(19.8f, 9.2f).L(15.2f, 9.2f);
            stroke(a, c, sw);
            Path b(d2d);
            b.M(4.2f, 19.5f).L(4.2f, 14.8f).L(8.8f, 14.8f);
            stroke(b, c, sw);
        } else if (n == "trash") {
            ln(4, 6.5f, 20, 6.5f);
            p.M(9, 6.5f).L(9, 4.5f).L(15, 4.5f).L(15, 6.5f);
            stroke(p, c, sw);
            Path q(d2d);
            q.M(6, 6.5f)
                .L(7, 19)
                .C(7.1f, 19.8f, 7.7f, 20.5f, 8.5f, 20.5f)
                .L(15.5f, 20.5f)
                .C(16.3f, 20.5f, 16.9f, 19.8f, 17, 19)
                .L(18, 6.5f);
            stroke(q, c, sw);
            ln(10.2f, 10, 10.2f, 17);
            ln(13.8f, 10, 13.8f, 17);
        } else if (n == "filter") {
            p.M(4, 5).L(20, 5).L(14, 12.5f).L(14, 19).L(10, 17).L(10, 12.5f).Z();
            stroke(p, c, sw);
        } else if (n == "check") {
            p.M(5, 12.5f).L(10, 17.5f).L(19, 7);
            stroke(p, c, sw);
        } else if (n == "play") {
            p.M(8, 5).L(19, 12).L(8, 19).Z();
            fill(p, c);
        } else if (n == "folder") {
            // a folder, as macOS draws it: a flat body top with a low, flat tab on the left, centred on the grid
            p.M(3.5f, 16.5f)
                .L(3.5f, 7.5f)
                .C(3.5f, 6.7f, 4.1f, 6, 5, 6)
                .L(8.5f, 6)
                .C(9.1f, 6, 9.6f, 6.3f, 9.9f, 6.8f)
                .L(10.7f, 8)
                .L(19, 8)
                .C(19.9f, 8, 20.5f, 8.7f, 20.5f, 9.5f)
                .L(20.5f, 16.5f)
                .C(20.5f, 17.4f, 19.9f, 18, 19, 18)
                .L(5, 18)
                .C(4.1f, 18, 3.5f, 17.4f, 3.5f, 16.5f)
                .Z();
            stroke(p, c, sw);
        } else if (n == "logout") {
            p.M(10, 4).L(6, 4).C(4.5f, 4, 4, 4.8f, 4, 6).L(4, 18).C(4, 19.2f, 4.5f, 20, 6, 20).L(10, 20);
            stroke(p, c, sw);
            ln(10, 12, 20, 12);
            Path q(d2d);
            q.M(16, 8).L(20, 12).L(16, 16);
            stroke(q, c, sw);
        } else if (n == "shield") {
            p.M(12, 3).L(19, 6).L(19, 12).C(19, 16.5f, 16, 19.5f, 12, 21).C(8, 19.5f, 5, 16.5f, 5, 12).L(5, 6).Z();
            stroke(p, c, sw);
            Path q(d2d);
            q.M(9, 12).L(11.2f, 14.2f).L(15.2f, 9.8f);
            stroke(q, c, sw);
        } else if (n == "home") {
            p.M(4, 11).L(12, 4).L(20, 11);
            stroke(p, c, sw);
            Path q(d2d);
            q.M(6, 10).L(6, 20).L(18, 20).L(18, 10);
            stroke(q, c, sw);
            ln(10, 20, 10, 14);
            ln(14, 14, 14, 20);
            ln(10, 14, 14, 14);
        } else if (n == "box") {
            p.M(12, 3).L(20, 7.5f).L(20, 16.5f).L(12, 21).L(4, 16.5f).L(4, 7.5f).Z();
            stroke(p, c, sw);
            Path q(d2d);
            q.M(4, 7.5f).L(12, 12).L(20, 7.5f);
            stroke(q, c, sw);
            ln(12, 12, 12, 21);
        } else if (n == "layers") {
            p.M(12, 3).L(21, 8).L(12, 13).L(3, 8).Z();
            stroke(p, c, sw);
            Path q(d2d);
            q.M(3, 12.5f).L(12, 17.5f).L(21, 12.5f);
            stroke(q, c, sw);
            Path r(d2d);
            r.M(3, 16.5f).L(12, 21.5f).L(21, 16.5f);
            stroke(r, c, sw);
        } else if (n == "gear") {
            ring(12, 12, 3, c, sw);
            ring(12, 12, 6.2f, c, sw);
            for (int i = 0; i < 8; i++) {
                float an = i * 3.14159265f / 4;
                ln(12 + cosf(an) * 6.2f, 12 + sinf(an) * 6.2f, 12 + cosf(an) * 9, 12 + sinf(an) * 9);
            }
        } else if (n == "download") {
            ln(12, 4, 12, 15);
            p.M(7, 10.5f).L(12, 15.5f).L(17, 10.5f);
            stroke(p, c, sw);
            ln(5, 20, 19, 20);
        } else if (n == "lock") {
            rt->DrawRoundedRectangle(D2D1::RoundedRect(D2D1::RectF(5, 11, 19, 21), 2.5f, 2.5f), use(c), sw);
            p.M(8, 11).L(8, 8).C(8, 2, 16, 2, 16, 8).L(16, 11);
            stroke(p, c, sw);
        } else if (n == "alert") {
            p.M(12, 3.5f).L(21.5f, 20).L(2.5f, 20).Z();
            stroke(p, c, sw);
            ln(12, 10, 12, 14.5f);
            circle(12, 17.4f, 1, c);
        } else if (n == "user") {
            ring(12, 8, 4, c, sw);
            p.M(4.5f, 21).C(4.5f, 16.5f, 8, 14.5f, 12, 14.5f).C(16, 14.5f, 19.5f, 16.5f, 19.5f, 21);
            stroke(p, c, sw);
        } else if (n == "eye" || n == "eye-off") {
            p.M(2, 12)
                .C(5, 6.5f, 8.5f, 5, 12, 5)
                .C(15.5f, 5, 19, 6.5f, 22, 12)
                .C(19, 17.5f, 15.5f, 19, 12, 19)
                .C(8.5f, 19, 5, 17.5f, 2, 12)
                .Z();
            stroke(p, c, sw);
            ring(12, 12, 3, c, sw);
            if (n == "eye-off") ln(4, 20, 20, 4);
        }
        // solid ones from the style guide
        else if (n == "users") {
            circle(9, 8, 3.6f, c);
            p.M(2.5f, 20).C(2.5f, 15.2f, 5.4f, 13.2f, 9, 13.2f).C(12.6f, 13.2f, 15.5f, 15.2f, 15.5f, 20).Z();
            fill(p, c);
            circle(16.6f, 8.6f, 2.9f, c);
            Path q(d2d);
            q.M(14.6f, 13.6f)
                .C(15.2f, 13.4f, 15.9f, 13.3f, 16.6f, 13.3f)
                .C(19.4f, 13.3f, 21.5f, 15, 21.5f, 19)
                .L(17, 19)
                .C(17, 16.6f, 16.1f, 14.8f, 14.6f, 13.6f)
                .Z();
            fill(q, c);
        } else if (n == "grid") {
            for (int i = 0; i < 4; i++)
                rt->FillRoundedRectangle(D2D1::RoundedRect(D2D1::RectF(4 + (i % 2) * 9.f, 4 + (i / 2) * 9.f,
                                                                       11 + (i % 2) * 9.f, 11 + (i / 2) * 9.f),
                                                           2, 2),
                                         use(c));
        } else if (n == "plus") {
            ln(12, 5, 12, 19);
            ln(5, 12, 19, 12);
        } else if (n == "fwd") {
            ln(5, 12, 18, 12);
            p.M(13, 7).L(18, 12).L(13, 17);
            stroke(p, c, sw);
        } else if (n == "back") {
            ln(19, 12, 6, 12);
            p.M(11, 7).L(6, 12).L(11, 17);
            stroke(p, c, sw);
        } else if (n == "cloud") {
            p.M(7, 18)
                .C(4.2f, 18, 2.5f, 16, 2.5f, 13.6f)
                .C(2.5f, 11.4f, 4.1f, 9.7f, 6.2f, 9.5f)
                .C(7, 6.6f, 9.4f, 4.6f, 12.3f, 4.6f)
                .C(15.8f, 4.6f, 18.5f, 7.2f, 18.8f, 10.5f)
                .C(20.4f, 11, 21.5f, 12.5f, 21.5f, 14.3f)
                .C(21.5f, 16.3f, 19.9f, 18, 17.8f, 18);
            stroke(p, c, sw);
            ln(12, 10, 12, 19);
            Path q(d2d);
            q.M(9, 16.2f).L(12, 19.2f).L(15, 16.2f);
            stroke(q, c, sw);
        } else if (n == "discord") {  // the Discord mark; its eyes are cut in the sign-in panel's colour
            p.M(19.6f, 6.2f)
                .C(18.1f, 5.5f, 16.6f, 5, 15, 4.7f)
                .L(14.6f, 5.6f)
                .C(12.9f, 5.3f, 11.1f, 5.3f, 9.4f, 5.6f)
                .L(9, 4.7f)
                .C(7.4f, 5, 5.9f, 5.5f, 4.4f, 6.2f)
                .C(1.6f, 10.4f, .8f, 14.5f, 1.2f, 18.5f)
                .C(3, 19.8f, 4.8f, 20.6f, 6.6f, 21.2f)
                .L(7.7f, 19.5f)
                .C(7, 19.2f, 6.4f, 18.9f, 5.8f, 18.5f)
                .L(6.2f, 18.2f)
                .C(9.9f, 19.9f, 14.1f, 19.9f, 17.8f, 18.2f)
                .L(18.2f, 18.5f)
                .C(17.6f, 18.9f, 17, 19.2f, 16.3f, 19.5f)
                .L(17.4f, 21.2f)
                .C(19.2f, 20.6f, 21, 19.8f, 22.8f, 18.5f)
                .C(23.3f, 13.8f, 22, 9.8f, 19.6f, 6.2f)
                .Z();
            fill(p, c);
            Col eye = {0x1B / 255.f, 0x1C / 255.f, 0x20 / 255.f, c.a};
            rt->FillEllipse(D2D1::Ellipse(D2D1::Point2F(8.7f, 14), 1.7f, 1.9f), use(eye));
            rt->FillEllipse(D2D1::Ellipse(D2D1::Point2F(15.3f, 14), 1.7f, 1.9f), use(eye));
        } else if (n == "bell") {
            p.M(6, 16.5f).L(6, 11).C(6, 7.7f, 8.7f, 5, 12, 5).C(15.3f, 5, 18, 7.7f, 18, 11).L(18, 16.5f).L(19.5f, 18).L(4.5f, 18).Z();
            stroke(p, c, sw);
            Path q(d2d);
            q.M(10, 20.5f).C(10.5f, 21.3f, 11.2f, 21.7f, 12, 21.7f).C(12.8f, 21.7f, 13.5f, 21.3f, 14, 20.5f);
            stroke(q, c, sw);
        } else if (n == "sidebar") {
            rt->DrawRoundedRectangle(D2D1::RoundedRect(D2D1::RectF(3.5f, 5, 20.5f, 19), 2.5f, 2.5f), use(c), sw);
            ln(9.5f, 5, 9.5f, 19);
        } else if (n == "info") {
            ring(12, 12, 8.5f, c, sw);
            ln(12, 11, 12, 16.5f);
            circle(12, 7.8f, 1.1f, c);
        } else if (n == "user-plus") {
            ring(10, 8, 3.6f, c, sw);
            p.M(3.5f, 20).C(3.5f, 15.8f, 6.5f, 14, 10, 14).C(12, 14, 13.8f, 14.6f, 15, 15.6f);
            stroke(p, c, sw);
            ln(18.5f, 13, 18.5f, 19);
            ln(15.5f, 16, 21.5f, 16);
        } else if (n == "chevrons") {
            Path q(d2d);
            q.M(8, 9.5f).L(12, 5.5f).L(16, 9.5f);
            stroke(q, c, sw);
            p.M(8, 14.5f).L(12, 18.5f).L(16, 14.5f);
            stroke(p, c, sw);
        } else if (n == "store") {
            p.M(5, 8).L(19, 8).L(18, 20).L(6, 20).Z();
            stroke(p, c, sw);
            Path q(d2d);
            q.M(9, 10.5f).L(9, 7).C(9, 5, 10.3f, 4, 12, 4).C(13.7f, 4, 15, 5, 15, 7).L(15, 10.5f);
            stroke(q, c, sw);
        } else if (n == "library") {
            ln(5, 5, 5, 19);
            ln(9, 5, 9, 19);
            p.M(12.5f, 6).L(16, 5).L(20, 18.5f).L(16.5f, 19.5f).Z();
            stroke(p, c, sw);
        } else if (n == "more") {
            circle(6, 12, 1.5f, c);
            circle(12, 12, 1.5f, c);
            circle(18, 12, 1.5f, c);
        } else if (n == "clock") {  // a dial with two hands
            const float r = 8.5f, k = r * .5523f;
            p.M(12, 12 - r).C(12 + k, 12 - r, 12 + r, 12 - k, 12 + r, 12).C(12 + r, 12 + k, 12 + k, 12 + r, 12, 12 + r)
                .C(12 - k, 12 + r, 12 - r, 12 + k, 12 - r, 12).C(12 - r, 12 - k, 12 - k, 12 - r, 12, 12 - r).Z();
            stroke(p, c, sw);
            ln(12, 12, 12, 7.5f);
            ln(12, 12, 15, 13.5f);
        } else if (n == "infinity") {  // a figure of eight lying down
            p.M(12, 12).C(14.5f, 8.5f, 20.5f, 8.5f, 20.5f, 12).C(20.5f, 15.5f, 14.5f, 15.5f, 12, 12)
                .C(9.5f, 8.5f, 3.5f, 8.5f, 3.5f, 12).C(3.5f, 15.5f, 9.5f, 15.5f, 12, 12).Z();
            stroke(p, c, sw);
        } else if (n == "list") {  // three lines, a dot before each
            circle(5, 7, 1.4f, c);
            circle(5, 12, 1.4f, c);
            circle(5, 17, 1.4f, c);
            ln(9, 7, 20, 7);
            ln(9, 12, 20, 12);
            ln(9, 17, 20, 17);
        } else if (n == "chevron") {
            p.M(9, 6).L(15, 12).L(9, 18);
            stroke(p, c, sw);
        } else if (n == "gamepad") {
            p.M(7, 8).L(17, 8).C(19.5f, 8, 21, 10, 21, 13).C(21, 16, 20, 18, 18.5f, 18).C(17, 18, 16, 15.5f, 14.5f, 15.5f)
                .L(9.5f, 15.5f).C(8, 15.5f, 7, 18, 5.5f, 18).C(4, 18, 3, 16, 3, 13).C(3, 10, 4.5f, 8, 7, 8).Z();
            stroke(p, c, sw);
            ln(7.5f, 11, 7.5f, 13.5f);
            ln(6.25f, 12.25f, 8.75f, 12.25f);
            circle(15.5f, 11.5f, 1, c);
            circle(17.5f, 13.3f, 1, c);
        } else            ring(12, 12, 6, c, sw);
        rt->SetTransform(old);
    }

    // ------------------------------------------------------------------------------------------ text
    void loadBrandFonts() {
        IDWriteFactory5* f5 = nullptr;
        IDWriteInMemoryFontFileLoader*& memory = brandLoader;
        IDWriteFontSetBuilder1* builder = nullptr;
        IDWriteFontSet* set = nullptr;
        if (SUCCEEDED(dw->QueryInterface(__uuidof(IDWriteFactory5), (void**)&f5)) &&
            SUCCEEDED(f5->CreateInMemoryFontFileLoader(&memory)) && SUCCEEDED(f5->RegisterFontFileLoader(memory)) &&
            SUCCEEDED(f5->CreateFontSetBuilder(&builder))) {
            for (int id : {101, 102, 103}) {
                HRSRC r = FindResourceW(nullptr, MAKEINTRESOURCEW(id), RT_RCDATA);
                HGLOBAL g = r ? LoadResource(nullptr, r) : nullptr;
                IDWriteFontFile* file = nullptr;
                if (g && SUCCEEDED(memory->CreateInMemoryFontFileReference(f5, LockResource(g), SizeofResource(nullptr, r), nullptr, &file)))
                    builder->AddFontFile(file);
                release(file);
            }
            if (SUCCEEDED(builder->CreateFontSet(&set))) f5->CreateFontCollectionFromFontSet(set, &brandFonts);
        }
        // each weight may come as its own family ("Montserrat", "Montserrat SemiBold"): note which name holds which
        for (UINT32 i = 0; brandFonts && i < brandFonts->GetFontFamilyCount(); i++) {
            IDWriteFontFamily* family = nullptr;
            IDWriteLocalizedStrings* names = nullptr;
            IDWriteFont* font = nullptr;
            wchar_t name[128] = L"";
            if (SUCCEEDED(brandFonts->GetFontFamily(i, &family)) && SUCCEEDED(family->GetFamilyNames(&names)) &&
                SUCCEEDED(names->GetString(0, name, 128)) && SUCCEEDED(family->GetFont(0, &font)))
                brandFamily[(int)font->GetWeight()] = name;
            release(font);
            release(names);
            release(family);
        }
        if (brandFamily.empty()) release(brandFonts);
        release(set);
        release(builder);
        release(f5);
        log(brandFonts ? "text in Inter" : "text in Segoe UI (no in-memory fonts on this Windows)");
    }
    IDWriteTextFormat* format(int weight, float size) {
        bool brand = brandFonts != nullptr;  // Inter for every text in this design
        auto key = std::make_pair(weight + (accountFont ? 10000 : 0) + (brand ? 20000 : 0), (int)lroundf(size * 100));
        auto it = formats.find(key);
        if (it != formats.end()) return it->second;
        IDWriteTextFormat* f = nullptr;
        wstring family = L"Segoe UI";
        if (brand) {  // the family whose weight is nearest to the one asked for
            int best = 1 << 30;
            for (auto& [w, name] : brandFamily)
                if (abs(w - weight) < best) best = abs(w - weight), family = name;
        }
        dw->CreateTextFormat(family.c_str(), brand ? brandFonts : nullptr, (DWRITE_FONT_WEIGHT)weight,
                             DWRITE_FONT_STYLE_NORMAL, DWRITE_FONT_STRETCH_NORMAL, size * FIT * (brand ? .97f : 1.f), L"", &f);
        if (f) f->SetWordWrapping(DWRITE_WORD_WRAPPING_NO_WRAP);
        return formats[key] = f;
    }
    // The text in the current language, laid out once and kept
    const Layout& layout(const string& ru, int weight, float size, float tracking) {
        string s = raw ? ru : Lang::t(ru), key = s + "\x01" + std::to_string(weight) + "\x01" +
                                                 std::to_string((int)lroundf(size * 100)) + "\x01" +
                                                 std::to_string((int)lroundf(tracking * 100));
        key += accountFont ? "|account" : headline ? "|head" : "|default";
        auto it = layouts.find(key);
        if (it != layouts.end()) return it->second;
        if (layouts.size() > 2500) {
            for (auto& l : layouts)
                if (l.second.l) l.second.l->Release();
            layouts.clear();
        }
        Layout lay;
        wstring w = W(s);
        if (IDWriteTextFormat* f = format(weight, size))
            dw->CreateTextLayout(w.c_str(), (UINT32)w.size(), f, 4000, 200, &lay.l);
        if (lay.l) {
            if (tracking != 0) {
                IDWriteTextLayout1* l1 = nullptr;
                if (SUCCEEDED(lay.l->QueryInterface(__uuidof(IDWriteTextLayout1), (void**)&l1)) && l1) {
                    l1->SetCharacterSpacing(0, tracking, 0, DWRITE_TEXT_RANGE{0, (UINT32)w.size()});
                    l1->Release();
                }
            }
            DWRITE_TEXT_METRICS m;
            lay.l->GetMetrics(&m);
            lay.width = m.widthIncludingTrailingWhitespace;
            DWRITE_LINE_METRICS lm;
            UINT32 n = 0;
            if (SUCCEEDED(lay.l->GetLineMetrics(&lm, 1, &n)) && n) lay.baseline = lm.baseline;
        }
        return layouts[key] = lay;
    }
    float width(const string& s, int weight, float size, float tracking = 0) {
        return s.empty() ? 0 : layout(s, weight, size, tracking).width;
    }
    // Draws with the baseline at y
    void text(const string& s, float x, float y, int weight, float size, Col c, float tracking = 0) {
        if (s.empty() || c.a <= 0) return;
        const Layout& l = layout(s, weight, size, tracking);
        // on whole pixels: a glyph that starts between two pixels is smeared over both
        float px = roundf(x * scale) / scale, py = roundf((y - l.baseline) * scale) / scale;
        if (l.l) rt->DrawTextLayout(D2D1::Point2F(px, py), l.l, use(c), D2D1_DRAW_TEXT_OPTIONS_NONE);
    }
    void center(const string& s, float cx, float y, int weight, float size, Col c, float tracking = 0) {
        text(s, cx - width(s, weight, size, tracking) / 2, y, weight, size, c, tracking);
    }
    void right(const string& s, float rx, float y, int weight, float size, Col c) {
        text(s, rx - width(s, weight, size), y, weight, size, c);
    }
    // Baseline that visually centres a line of capital-height text on cy
    static float mid(float cy, float size) { return cy + size * FIT * .35f; }
    // A layout made for measuring only: never cached, the caller releases it
    IDWriteTextLayout* scratchLayout(const wstring& w, int weight, float size, float maxWidth) {
        IDWriteTextLayout* l = nullptr;
        if (IDWriteTextFormat* f = format(weight, size)) dw->CreateTextLayout(w.c_str(), (UINT32)w.size(), f, maxWidth, 2000, &l);
        return l;
    }
    // How many leading UTF-16 units of the layout fit in maxWidth (whole clusters only)
    static size_t fitting(IDWriteTextLayout* l, float maxWidth) {
        UINT32 n = 0;
        l->GetClusterMetrics(nullptr, 0, &n);
        vector<DWRITE_CLUSTER_METRICS> cm(n);
        if (!n || FAILED(l->GetClusterMetrics(cm.data(), n, &n))) return 0;
        float acc = 0;
        size_t chars = 0;
        for (auto& c : cm) {
            if (acc + c.width > maxWidth) break;
            acc += c.width;
            chars += c.length;
        }
        return chars;
    }
    std::unordered_map<string, string> fitCache;
    std::unordered_map<string, vector<string>> wrapCache;
    string sizeKey(int weight, float size, float maxWidth) const {
        return fmt("\x01%d\x01%d\x01%d\x01%d", weight, (int)lroundf(size * 100), (int)lroundf(maxWidth * 10), accountFont ? 1 : 0);
    }
    // Shortens a string with an ellipsis until it fits maxWidth (measured once per string, then remembered)
    string fit(const string& ru, int weight, float size, float maxWidth) {
        string s = Lang::t(ru);
        if (s.empty()) return s;
        string key = s + sizeKey(weight, size, maxWidth);
        auto hit = fitCache.find(key);
        if (hit != fitCache.end()) return hit->second;
        if (fitCache.size() > 4000) fitCache.clear();
        string out = s;
        wstring w = W(s);
        if (IDWriteTextLayout* l = scratchLayout(w, weight, size, 100000)) {
            DWRITE_TEXT_METRICS m;
            if (SUCCEEDED(l->GetMetrics(&m)) && m.widthIncludingTrailingWhitespace > maxWidth) {
                size_t chars = std::max<size_t>(1, fitting(l, maxWidth - width("…", weight, size)));
                wstring cut = w.substr(0, chars);
                while (cut.size() > 1 && iswspace(cut.back())) cut.pop_back();
                out = U(cut) + "…";
            }
            l->Release();
        }
        return fitCache[key] = out;
    }
    // Words wrapped into at most maxLines lines by DirectWrite itself; what does not fit ends with an ellipsis
    const vector<string>& wrap(const string& s, int weight, float size, float maxWidth, int maxLines) {
        string key = s + sizeKey(weight, size, maxWidth) + fmt("\x01%d", maxLines);
        auto hit = wrapCache.find(key);
        if (hit != wrapCache.end()) return hit->second;
        if (wrapCache.size() > 1500) wrapCache.clear();
        vector<string> lines;
        wstring w = W(s);
        for (auto& c : w)
            if (c == L'\r' || c == L'\n' || c == L'\t') c = L' ';
        if (IDWriteTextLayout* l = scratchLayout(w, weight, size, std::max(1.f, maxWidth))) {
            l->SetWordWrapping(DWRITE_WORD_WRAPPING_WRAP);
            UINT32 n = 0;
            l->GetLineMetrics(nullptr, 0, &n);
            vector<DWRITE_LINE_METRICS> lm(n);
            if (n && SUCCEEDED(l->GetLineMetrics(lm.data(), n, &n))) {
                size_t pos = 0;
                for (UINT32 i = 0; i < n && (int)lines.size() < maxLines; i++) {
                    bool last = (int)lines.size() == maxLines - 1 && i + 1 < n;
                    wstring part = last ? w.substr(pos) : w.substr(pos, lm[i].length);
                    pos += lm[i].length;
                    while (!part.empty() && iswspace(part.back())) part.pop_back();
                    while (!part.empty() && iswspace(part.front())) part.erase(part.begin());
                    if (part.empty()) continue;
                    lines.push_back(last ? fit(U(part), weight, size, maxWidth) : U(part));
                }
            }
            l->Release();
        }
        return wrapCache[key] = lines;
    }

    // ------------------------------------------------------------------------------------------ pictures
    // Decodes PNG / JPEG (and whatever else this Windows has codecs for), shrinking anything larger than maxSide
    IWICBitmapSource* decode(const string& bytes, int maxSide) {
        if (!wic || bytes.empty()) return nullptr;
        IWICStream* st = nullptr;
        IWICBitmapDecoder* dec = nullptr;
        IWICBitmapFrameDecode* fr = nullptr;
        IWICFormatConverter* conv = nullptr;
        IWICBitmapSource* out = nullptr;
        if (SUCCEEDED(wic->CreateStream(&st)) &&
            SUCCEEDED(st->InitializeFromMemory((BYTE*)bytes.data(), (DWORD)bytes.size())) &&
            SUCCEEDED(wic->CreateDecoderFromStream(st, nullptr, WICDecodeMetadataCacheOnLoad, &dec)) &&
            SUCCEEDED(dec->GetFrame(0, &fr))) {
            IWICBitmapSource* src = fr;
            src->AddRef();
            UINT w = 0, h = 0;
            fr->GetSize(&w, &h);
            if (maxSide > 0 && (int)std::max(w, h) > maxSide) {
                IWICBitmapScaler* sc = nullptr;
                float k = maxSide / (float)std::max(w, h);
                if (SUCCEEDED(wic->CreateBitmapScaler(&sc)) &&
                    SUCCEEDED(sc->Initialize(fr, std::max(1u, (UINT)(w * k)), std::max(1u, (UINT)(h * k)),
                                             WICBitmapInterpolationModeFant))) {
                    src->Release();
                    src = sc;
                } else
                    release(sc);
            }
            if (SUCCEEDED(wic->CreateFormatConverter(&conv)) &&
                SUCCEEDED(conv->Initialize(src, GUID_WICPixelFormat32bppPBGRA, WICBitmapDitherTypeNone, nullptr, 0,
                                           WICBitmapPaletteTypeMedianCut))) {
                // decode now, into memory: the bytes it reads from will not outlive this call
                IWICBitmap* mem = nullptr;
                if (SUCCEEDED(wic->CreateBitmapFromSource(conv, WICBitmapCacheOnLoad, &mem))) out = mem;
            }
            src->Release();
        }
        release(conv);
        release(fr);
        release(dec);
        release(st);
        return out;
    }
    ID2D1Bitmap* toDevice(IWICBitmapSource* src) {
        ID2D1Bitmap* b = nullptr;
        if (src && rt) rt->CreateBitmapFromWicBitmap(src, nullptr, &b);
        return b;
    }
    void face(ID2D1Bitmap* skin, float x, float y, float s, float r, float a) {
        if (!skin || a <= 0) return;
        D2D1_SIZE_F sz = skin->GetSize();
        float u = sz.width / 64, k = s / (8 * u);
        ID2D1BitmapBrush* bb = nullptr;
        if (FAILED(rt->CreateBitmapBrush(skin,
                                         D2D1::BitmapBrushProperties(D2D1_EXTEND_MODE_CLAMP, D2D1_EXTEND_MODE_CLAMP,
                                                                     D2D1_BITMAP_INTERPOLATION_MODE_NEAREST_NEIGHBOR),
                                         &bb)))
            return;
        bb->SetOpacity(clamp01(a));

        bb->SetTransform(D2D1::Matrix3x2F::Scale(k, k) * D2D1::Matrix3x2F::Translation(x - 8 * u * k, y - 8 * u * k));
        fillShape(x, y, s, s, r, bb);
        if (sz.height >= sz.width) {
            bb->SetTransform(D2D1::Matrix3x2F::Scale(k, k) *
                             D2D1::Matrix3x2F::Translation(x - 40 * u * k, y - 8 * u * k));
            fillShape(x, y, s, s, r, bb);
        }
        bb->Release();
    }
    // A picture cropped to fill a rounded rectangle (never stretched)
    void picture(ID2D1Bitmap* b, float x, float y, float w, float h, float r, float a) {
        if (!b || a <= 0) return;
        D2D1_SIZE_F sz = b->GetSize();
        float k = std::max(w / sz.width, h / sz.height);
        ID2D1BitmapBrush* bb = nullptr;
        bool crisp = sz.width <= 64 && k >= 2;  // small pixel art stays crisp when enlarged
        if (FAILED(rt->CreateBitmapBrush(
                b,
                D2D1::BitmapBrushProperties(D2D1_EXTEND_MODE_CLAMP, D2D1_EXTEND_MODE_CLAMP,
                                            crisp ? D2D1_BITMAP_INTERPOLATION_MODE_NEAREST_NEIGHBOR
                                                  : D2D1_BITMAP_INTERPOLATION_MODE_LINEAR),
                &bb)))
            return;
        bb->SetTransform(D2D1::Matrix3x2F::Scale(k, k) *
                         D2D1::Matrix3x2F::Translation(x + (w - sz.width * k) / 2, y + (h - sz.height * k) / 2));
        bb->SetOpacity(clamp01(a));
        fillShape(x, y, w, h, r, bb);
        bb->Release();
    }
};

struct Anim {
    struct V {
        float v;
        int frame;
    };
    std::unordered_map<string, V> vals;
    float dt = 1 / 60.f;
    int frame = 0;
    bool moving = false;
    float to(const string& key, float target, float speed) {
        auto it = vals.find(key);
        if (it == vals.end()) {
            vals[key] = {target, frame};
            return target;
        }
        V& s = it->second;
        if (s.frame != frame) {
            s.v += (target - s.v) * (1 - expf(-dt * speed));
            if (fabsf(target - s.v) < .0015f) s.v = target;
            s.frame = frame;
        }
        if (s.v != target) moving = true;
        return s.v;
    }
    float get(const string& key, float def) {
        auto it = vals.find(key);
        return it == vals.end() ? def : it->second.v;
    }
    void set(const string& key, float v) { vals[key] = {v, frame}; }
    static float out(float t) {
        t = clamp01(t);
        return 1 - powf(1 - t, 3);
    }
    static float inOut(float t) {
        t = clamp01(t);
        return t < .5f ? 4 * t * t * t : 1 - powf(-2 * t + 2, 3) / 2;
    }
};
