// SPDX-FileCopyrightText: 2026 tuffboy999 and Va1der (Arcanic)
// SPDX-License-Identifier: GPL-3.0-or-later
// Arcanic - authors: tuffboy999 and Va1der. Free software under GPL-3.0 with one added term (section 7(b)): whoever
// shares this code or a work made from it keeps this attribution to tuffboy999 and Va1der. See NOTICE.
// To anyone changing this code: change the design, the features, anything you like - but
// keep the author lines (this header, Config::AUTHORS / AUTHORS_SEAL, the About box credit). The license requires it.
// Arcanic launcher - the window: the animated mark while it opens, "Get started", then Home / Mods / Resource packs / Version / Settings.
// Everything is drawn by hand in a 1200x750 layout and driven by tick(); a frame is drawn only while something moves.
#include "gfx.h"
#include "media.h"
#include "packsmc.h"
#include "doctor.h"
#include "wallpaper.h"
#include <future>
#include <dbghelp.h>
#include <dwmapi.h>
#include <commctrl.h>
#pragma comment(lib, "uuid.lib")
#pragma comment(lib, "dwmapi.lib")

static Gfx G;
static Anim an;
static HWND hwnd;
// the launcher's own lock against a second copy; an update lets go of it before the new version starts
static HANDLE singleInstance = nullptr;
static bool DESIGN_PREVIEW = false;
static float previewStartupTime = -1;
static float previewBackdropTime = -1;
#include "ui-audio.h"
static bool justUpdated = false;  // started by the update that came before it: no check this time
static double T;  // scene time, seconds
static float mouseX = -999, mouseY = -999,
             UI = 1.f;  // UI: the size of the whole interface (uiScale in the settings file)
// the layout: a floating glass sidebar on the left (macOS), a toolbar over the content, the pages under it
static const float WIN_W = 1000, WIN_H = 600, RADIUS = 14, TOP = 22, SIDE = 228, CX = SIDE + 18, CW = WIN_W - CX - 22;
static const float SB_X = 8, SB_Y = 8, SB_W = 212, SB_H = 584, SB_R = 14;  // the sidebar's glass panel
static const float PAGE_TOP = 62, MODS_TOP = PAGE_TOP, ROW_H = 56, ROW_STEP = 62;
using namespace Pal;
static Col glassOf(Col solid, float opacity = .62f) {
    static const Col base = rgb(0x0A0F1A);
    auto ch = [&](float s, float b) { return clamp01(b + (s - b) / opacity); };
    return {ch(solid.r, base.r), ch(solid.g, base.g), ch(solid.b, base.b), solid.a * opacity};
}

// ---- work done on other threads hands its results back to the window through this queue
static std::mutex queueLock;
static vector<std::function<void()>> queued;
static void post(std::function<void()> f) {
    {
        std::lock_guard<std::mutex> l(queueLock);
        queued.push_back(std::move(f));
    }
    PostMessageW(hwnd, WM_APP, 0, 0);
}
static void wake(double seconds);
static void applyUiScale(float scale);
static void previewUiScale(float scale);
static void commitUiScale();
static bool advanceUiScale(double dt);
static float uiScaleTarget = 1;
static bool windowZoomed = false;            // green: the window fills the screen it is on
static bool windowRoundedByWindows = false;  // Windows 11 rounds the corners and casts the shadow itself
static int windowW = 0, windowH = 0;          // the size the window must keep (nothing may resize it)
static void toggleZoom();

// ================================================================================================ widgets
struct Widget {
    string id;
    float x = 0, y = 0, w = 0, h = 0;
    bool hovered = false, pressed = false, focused = false, enabled = true, visible = true;
    double rippleAt = -10;  // the last press, in the widget's own coordinates: drawn as a ripple
    float rippleX = 0, rippleY = 0;
    explicit Widget(string i) : id(std::move(i)) {}
    virtual ~Widget() {}
    void at(float X, float Y, float W_, float H) {
        x = X;
        y = Y;
        w = W_;
        h = H;
    }
    bool hit(float mx, float my) const { return visible && enabled && mx >= x && mx < x + w && my >= y && my < y + h; }
    float a(const char* k, float target, float speed) { return an.to(id + "." + k, target, speed); }
    virtual void paint(float alpha) {}
    virtual void down(float mx, float my, int clicks) {}
    virtual void drag(float mx, float my) {}
    virtual void up(float mx, float my, bool inside) {}
    virtual bool key(int vk, bool ctrl, bool shift) { return false; }
    virtual void typed(wchar_t c) {}
    virtual bool textual() const { return false; }
};

struct DialogPop {
    D2D1_MATRIX_3X2_F old;
    DialogPop(float k, float cx, float cy) {
        G.rt->GetTransform(&old);
        float t = clamp01(k), back = 1 + 2.2f * powf(t - 1, 3) + 1.2f * powf(t - 1, 2);  // easeOutBack
        float s = .94f + .06f * back;
        G.rt->SetTransform(D2D1::Matrix3x2F::Scale(s, s, D2D1::Point2F(cx, cy)) *
                           D2D1::Matrix3x2F::Translation(0, 12 * (1 - t)) * old);
    }
    ~DialogPop() { G.rt->SetTransform(old); }
};

// The press ripple: a soft light that swells from where the widget was pressed, kept inside its rounded shape
static void ripple(const Widget& w, float x, float y, float wd, float h, float r, float al, Col c = WHITE) {
    float age = (float)(T - w.rippleAt);
    const float life = .5f;
    if (age < 0 || age > life || al <= 0 || wd <= 0 || h <= 0) return;
    an.moving = true;
    float t = age / life, grow = 1 - powf(1 - std::min(1.f, t * 1.6f), 3);
    float dx = std::max(w.rippleX - x, x + wd - w.rippleX), dy = std::max(w.rippleY - y, y + h - w.rippleY);
    float reach = sqrtf(dx * dx + dy * dy) * 1.25f + 8, fadeOut = (1 - t) * (1 - t);
    ID2D1Geometry* shape = G.shape(x, y, wd, h, std::max(r, .5f));
    if (!shape) return;
    G.rt->PushLayer(D2D1::LayerParameters(D2D1::InfiniteRect(), shape), nullptr);
    G.rect(x, y, wd, h, fade(c, al * .045f * fadeOut));  // the whole shape lights for a moment
    G.radial(w.rippleX, w.rippleY, std::max(4.f, reach * grow), fade(c, al * .17f * fadeOut));
    G.rt->PopLayer();
}

struct Clickable : Widget {
    std::function<void()> click;
    string tag, icon;
    using Widget::Widget;
    void up(float, float, bool inside) override {
        if (inside && enabled && click) click();
    }
};

struct Btn : Widget {
    string text, icon;
    bool busy = false, ghost = false, boxy = false, running = false, jelly = false;
    bool primary = false;
    float jellyScale = 1, jellyVelocity = 0;
    float fontSize = 15;
    std::function<void()> click;
    Btn(string i, string t) : Widget(std::move(i)), text(std::move(t)) {}
    void up(float, float, bool inside) override {
        if (inside && enabled && !busy && click) click();
    }
    void paint(float al) override {
        float hv = a("h", hovered && enabled && !busy ? 1 : 0, 24), bz = a("b", busy ? 1 : 0, 18),
              dis = a("d", enabled ? 0 : 1, 12), r = h / 2;  // capsules, as the newest macOS draws its buttons
        struct RestoreTransform {
            D2D1_MATRIX_3X2_F old;
            RestoreTransform() { G.rt->GetTransform(&old); }
            ~RestoreTransform() { G.rt->SetTransform(old); }
        } restore;
        float scale;
        if (jelly) {  // Play: a damped spring, the same motion at any frame rate
            float target = pressed ? .97f : (hovered && enabled && !busy ? 1.012f : 1.f);
            float dt = std::min(an.dt, .05f), decay = 13.f, frequency = 18.f;
            float wd = sqrtf(frequency * frequency - decay * decay);
            float displacement = jellyScale - target, e = expf(-decay * dt);
            float c = cosf(wd * dt), sn = sinf(wd * dt), velocity = jellyVelocity;
            jellyScale = target + e * (displacement * c + (velocity + decay * displacement) / wd * sn);
            jellyVelocity = e * (velocity * c - (decay * velocity + frequency * frequency * displacement) / wd * sn);
            if (fabsf(jellyScale - target) > .0001f || fabsf(jellyVelocity) > .0001f) an.moving = true;
            else { jellyScale = target; jellyVelocity = 0; }
            scale = jellyScale;
        } else
            scale = 1 - .025f * a("pressMotion", pressed && enabled ? 1.f : 0.f, 36);
        G.rt->SetTransform(D2D1::Matrix3x2F::Scale(scale, scale, D2D1::Point2F(x + w / 2, y + h / 2)) * restore.old);
        if (running) {  // the game is on: a calm pill, a live green dot, white words
            G.rr(x, y, w, h, r, fade(rgba(255, 255, 255, 26), al));
            string label = Lang::t(text);
            float fs = std::max(11.f, fontSize - 1.f);
            float tw = G.width(label, SEMI, fs), sx = x + (w - (tw + 20)) / 2, cy = y + h / 2;
            float pulse = .5f + .5f * sinf((float)T * 3.2f);
            G.circle(sx + 5, cy, 4 + 4 * pulse, fade(alpha(OK, .22f * (1 - pulse)), al));
            G.circle(sx + 5, cy, 4, fade(OK, al));
            G.text(label, sx + 20, Gfx::mid(cy, fs), SEMI, fs, fade(WHITE, al));
            an.moving = true;
            return;
        }
        float press = a("press", pressed && enabled && !busy ? 1 : 0, 30);
        Col tc;
        if (primary) {  // the accent button: system blue, white words
            Col fill = mix(mix(ACTION, rgb(0x2A95FF), hv), rgb(0x0068D6), press);
            fill = mix(fill, rgb(0x3A3A40), std::max(dis * .85f, bz * .2f));
            G.rr(x, y, w, h, r, fade(fill, al));
            G.rrGrad(x, y, w, h, r, fade(alpha(WHITE, .14f), al * (1 - dis)), alpha(WHITE, 0), 0, y, 0, y + h * .6f);
            tc = fade(mix(WHITE, rgb(0x8E8E93), dis), al);
        } else {  // the plain button: a soft, see-through fill
            G.rr(x, y, w, h, r, fade(alpha(WHITE, .10f + .06f * hv - .04f * press - .05f * dis), al));
            G.rrLine(x + .5f, y + .5f, w - 1, h - 1, r - .5f, fade(alpha(WHITE, .06f), al));
            tc = fade(mix(TEXT, rgb(0x6E6E73), dis), al);
        }
        ripple(*this, x, y, w, h, r, al, WHITE);
        string label = Lang::t(text);
        if (label.empty() && !icon.empty() && bz < .01f) {  // an icon-only button: the icon in the exact middle
            G.icon(icon.c_str(), x + w / 2, y + h / 2, 14, tc, 1.7f);
            return;
        }
        float fs = std::max(11.5f, fontSize - (boxy ? 0 : 1.f));
        float tw = G.width(label, SEMI, fs), lead = bz > .01f ? 22 * bz : !icon.empty() ? 21 : 0, sx = x + (w - (tw + lead)) / 2;
        if (bz > .01f) {
            float cx = sx + 7, cy = y + h / 2, ang = (float)fmod(T * 5.2, 6.2831853);
            G.ring(cx, cy, 6.5f, fade(tc, bz * .25f), 1.8f);
            for (int i = 0; i < 10; i++) {
                float a0 = ang + i * .17f, a1 = a0 + .17f;
                G.line(cx + cosf(a0) * 6.5f, cy + sinf(a0) * 6.5f, cx + cosf(a1) * 6.5f, cy + sinf(a1) * 6.5f, fade(tc, bz), 1.8f);
            }
        } else if (!icon.empty())
            G.icon(icon.c_str(), sx + 7, y + h / 2, 14, tc, 1.7f);
        G.text(label, sx + lead, Gfx::mid(y + h / 2, fs), SEMI, fs, tc);
    }
};
struct IconBtn : Widget {
    string icon;
    bool danger = false;
    std::function<void()> click;
    IconBtn(string i, string ic) : Widget(std::move(i)), icon(std::move(ic)) {}
    void up(float, float, bool inside) override {
        if (inside && click) click();
    }
    bool plate = false;  // a toolbar button: a soft capsule behind the icon even at rest
    void paint(float al) override {
        float hv = a("h", hovered && enabled ? 1 : 0, 20), dis = enabled ? 1.f : .35f;
        float r = std::min(w, h) / 2;
        if (plate) G.rr(x, y, w, h, r, fade(alpha(WHITE, .09f + .06f * hv), al));
        else G.rr(x, y, w, h, r, fade(alpha(danger ? ERR : WHITE, danger ? .18f : .09f), al * hv));
        ripple(*this, x, y, w, h, r, al, danger ? ERR : WHITE);
        G.icon(icon.c_str(), x + w / 2, y + h / 2, std::min(w, h) * .52f, fade(mix(rgb(0xE5E5EA), danger ? ERR : WHITE, hv), al * dis), 1.7f);
    }
};

struct Toggle : Widget {
    bool on, row = false;
    float rippleR = 14;  // the row's corner (0 inside a grouped card)
    std::function<void(bool)> onChange;
    Toggle(string i, bool v) : Widget(std::move(i)), on(v) {}
    void up(float, float, bool inside) override {
        if (inside && enabled) {
            on = !on;
            if (onChange) onChange(on);
        }
    }
    void paint(float al) override {
        // the macOS switch: system blue when on, a dim track when off, a white knob that stretches while held
        float v = a("v", on ? 1 : 0, 18), hv = a("h", hovered ? 1 : 0, 16), dis = enabled ? 1 : .45f;
        float sw = row ? 38 : w, sh = row ? 22 : h, sx = row ? x + w - 22 - sw : x, sy = row ? y + (h - sh) / 2 : y;
        if (row) ripple(*this, x, y, w, h, rippleR, al * dis, WHITE);
        G.rr(sx, sy, sw, sh, sh / 2, fade(mix(alpha(WHITE, .16f + .04f * hv), ACTION, v), al * dis));
        float press = a("p", pressed && enabled ? 1 : 0, 22), ks = sh - 4, kw = ks + 6 * press, kx = sx + 2 + (sw - 4 - kw) * v;
        G.rr(kx, sy + 3, kw, ks, ks / 2, fade(rgba(0, 0, 0, 60), al * dis));
        G.rr(kx, sy + 2, kw, ks, ks / 2, fade(WHITE, al * dis));
    }
};

struct Check : Widget {
    string label;
    bool on;
    Check(string i, string l, bool v) : Widget(std::move(i)), label(std::move(l)), on(v) {}
    void up(float, float, bool inside) override {
        if (inside) on = !on;
    }
    void paint(float al) override {
        float v = a("v", on ? 1 : 0, 18), hv = a("h", hovered ? 1 : 0, 14), bs = 18, by = y + (h - bs) / 2;
        G.rr(x, by, bs, bs, 6, fade(mix(WELL, ACCENT, v), al));
        G.rrLine(x + .5f, by + .5f, bs - 1, bs - 1, 5.5f, fade(mix(mix(CARD3, rgb(0x45455C), hv), ACCENT, v), al));
        if (v > .02f) G.icon("check", x + bs / 2, by + bs / 2, 13, fade(WHITE, al * v), 2.1f);
        G.text(label, x + bs + 9, Gfx::mid(y + h / 2, 13), MEDIUM, 13, fade(mix(MUTED, TEXT, hv), al));
    }
};

struct Slider : Widget {
    float value;
    int steps = 0;
    bool dragging = false, screenAnchored = false;
    float screenTrackX = 0, screenTrackWidth = 1;
    std::function<void(float)> onChange;
    std::function<void(float)> onCommit;
    std::function<string(float)> label;
    Slider(string i, float v) : Widget(std::move(i)), value(v) {}
    float snap(float v) const {
        v = clamp01(v);
        return steps > 0 ? roundf(v * steps) / steps : v;
    }
    void set(float mx) {
        float position = (mx - x - 10) / (w - 20);
        if (screenAnchored && dragging && !DESIGN_PREVIEW) {
            POINT pointer{}; GetCursorPos(&pointer);
            position = (pointer.x - screenTrackX) / screenTrackWidth;
        }
        float v = snap(position);
        if (v != value) {
            value = v;
            if (onChange) onChange(v);
        }
    }
    void down(float mx, float, int) override {
        if (screenAnchored) {
            POINT origin{}; ClientToScreen(hwnd, &origin);
            screenTrackX = origin.x + (x + 10) * G.scale;
            screenTrackWidth = std::max(1.f, (w - 20) * G.scale);
        }
        dragging = true;
        set(mx);
    }
    void drag(float mx, float) override {
        if (dragging) set(mx);
    }
    void up(float, float, bool) override { bool commit = dragging; dragging = false; if (commit && onCommit) onCommit(value); }
    void paint(float al) override {
        // the macOS slider: a thin track, filled in system blue up to a white knob with a soft shadow
        float hv = a("h", hovered || dragging ? 1 : 0, 16), dg = a("d", dragging ? 1 : 0, 18),
              v = a("v", value, dragging ? 40 : 22);
        float cy = y + h / 2, th = 4, tx = x + 10, tw = w - 20, kx = tx + tw * v;
        G.rr(tx, cy - th / 2, tw, th, th / 2, fade(alpha(WHITE, .18f), al));
        G.rr(tx, cy - th / 2, std::max(th, tw * v), th, th / 2, fade(ACTION, al));
        float r = 9 + 1.2f * dg;
        G.circle(kx, cy + 1, r + .5f, fade(rgba(0, 0, 0, 70), al));
        G.circle(kx, cy, r, fade(mix(rgb(0xF2F2F7), WHITE, hv), al));
        if (label && hv > .02f) {  // the value, in a bubble that rises over the knob
            string s = label(value);
            float bw = G.width(s, SEMI, 11.5f) + 18, bx = std::max(x, std::min(x + w - bw, kx - bw / 2)),
                  by = cy - r - 30 + 5 * (1 - hv);
            G.rr(bx, by, bw, 23, 11.5f, fade(rgb(0x3A3A3C), al * hv));
            G.center(s, bx + bw / 2, Gfx::mid(by + 11.5f, 11.5f), SEMI, 11.5f, fade(WHITE, al * hv));
        }
    }
};

struct Field : Widget {
    string label, hint, icon;
    wstring text;
    bool password = false, show = false, error = false, dragging = false, digits = false;  // digits: only 0-9 go in
    int caret = 0, anchor = 0, max = 160;
    float scroll = 0, fontSize = 15;
    double shakeAt = -10, focusAt = -10;
    std::function<void()> onEnter, onChange;
    Field(string i, string l) : Widget(std::move(i)), label(std::move(l)) {}
    bool textual() const override { return true; }
    void set(const string& s) {
        text = W(s);
        caret = anchor = (int)text.size();
        scroll = 0;
    }
    string value() const { return U(text); }
    void shake() { shakeAt = T; }
    bool hasSel() const { return caret != anchor; }
    wstring shownW() const { return password && !show ? wstring(text.size(), L'\x2022') : text; }
    float px(int i) {
        wstring s = shownW().substr(0, std::max(0, std::min(i, (int)text.size())));
        if (s.empty()) return 0;
        G.raw = true;
        float v = G.width(U(s), MEDIUM, fontSize);
        G.raw = false;
        return v;
    }
    float ox() const { return icon.empty() ? 0 : (h < 34 ? 18.f : 30.f); }
    float viewW() const { return w - 32 - ox() - (password ? 30 : 0); }
    void edit(const wstring& typed) {
        wstring s = typed;
        if (digits) s.erase(std::remove_if(s.begin(), s.end(), [](wchar_t ch) { return ch < L'0' || ch > L'9'; }), s.end());
        int a0 = std::min(caret, anchor), b0 = std::max(caret, anchor);
        wstring ns = text.substr(0, a0) + s + text.substr(b0);
        if ((int)ns.size() > max) return;
        text = ns;
        caret = anchor = a0 + (int)s.size();
        if (onChange) onChange();
    }
    int word(int from, int dir) {
        int i = from;
        if (dir < 0) {
            while (i > 0 && text[i - 1] == L' ') i--;
            while (i > 0 && text[i - 1] != L' ') i--;
        } else {
            while (i < (int)text.size() && text[i] == L' ') i++;
            while (i < (int)text.size() && text[i] != L' ') i++;
        }
        return i;
    }
    void move(int to, bool extend) {
        caret = std::max(0, std::min((int)text.size(), to));
        if (!extend) anchor = caret;
    }
    bool key(int vk, bool ctrl, bool shift) override {
        switch (vk) {
        case 'A':
            if (ctrl) {
                anchor = 0;
                caret = (int)text.size();
                return true;
            }
            break;
        case 'C':
        case 'X':
            if (ctrl) {
                if (hasSel() && !(password && !show)) {
                    wstring s = text.substr(std::min(caret, anchor), abs(caret - anchor));
                    if (OpenClipboard(hwnd)) {
                        EmptyClipboard();
                        if (HGLOBAL g = GlobalAlloc(GMEM_MOVEABLE, (s.size() + 1) * sizeof(wchar_t))) {
                            memcpy(GlobalLock(g), s.c_str(), (s.size() + 1) * sizeof(wchar_t));
                            GlobalUnlock(g);
                            SetClipboardData(CF_UNICODETEXT, g);
                        }
                        CloseClipboard();
                    }
                    if (vk == 'X') edit(L"");
                }
                return true;
            }
            break;
        case 'V':
            if (ctrl) {
                if (OpenClipboard(hwnd)) {
                    if (HANDLE h = GetClipboardData(CF_UNICODETEXT)) {
                        if (const wchar_t* p = (const wchar_t*)GlobalLock(h)) {
                            wstring s = p;
                            GlobalUnlock(h);
                            for (auto& c : s)
                                if (c == L'\r' || c == L'\n' || c == L'\t') c = L' ';
                            edit(s);
                        }
                    }
                    CloseClipboard();
                }
                return true;
            }
            break;
        case VK_BACK:
            if (hasSel())
                edit(L"");
            else if (caret > 0) {
                anchor = ctrl ? word(caret, -1) : caret - 1;
                edit(L"");
            }
            return true;
        case VK_DELETE:
            if (hasSel())
                edit(L"");
            else if (caret < (int)text.size()) {
                int to = ctrl ? word(caret, 1) : caret + 1;
                anchor = caret;
                caret = to;
                edit(L"");
            }
            return true;
        case VK_LEFT:
            if (hasSel() && !shift)
                move(std::min(caret, anchor), false);
            else
                move(ctrl ? word(caret, -1) : caret - 1, shift);
            return true;
        case VK_RIGHT:
            if (hasSel() && !shift)
                move(std::max(caret, anchor), false);
            else
                move(ctrl ? word(caret, 1) : caret + 1, shift);
            return true;
        case VK_HOME:
            move(0, shift);
            return true;
        case VK_END:
            move((int)text.size(), shift);
            return true;
        case VK_RETURN:
            if (onEnter) onEnter();
            return true;
        }
        return false;
    }
    void typed(wchar_t c) override {
        if (c >= 32 && c != 127) edit(wstring(1, c));
    }
    int indexAt(float mx) {
        float rel = mx - (x + 16 + ox()) + scroll;
        int best = 0;
        float bd = 1e9f;
        for (int i = 0; i <= (int)text.size(); i++) {
            float d = fabsf(px(i) - rel);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }
    void down(float mx, float, int clicks) override {
        if (password && mx > x + w - 40) {
            show = !show;
            return;
        }
        if (clicks >= 2) {
            anchor = 0;
            caret = (int)text.size();
            return;
        }
        move(indexAt(mx), false);
        dragging = true;
    }
    void drag(float mx, float) override {
        if (dragging) move(indexAt(mx), true);
    }
    void up(float, float, bool) override { dragging = false; }
    void paint(float al) override {
        float f = a("f", focused ? 1 : 0, 16), fl = a("l", focused || !text.empty() ? 1 : 0, 18),
              er = a("e", error ? 1 : 0, 14), hv = a("h", hovered ? 1 : 0, 14);
        double st = T - shakeAt;
        float X = x + (st < .6 ? (float)(sin(st * 44) * 7 * exp(-st * 7)) : 0), o = ox();

        float fr = std::min(8.f, h / 2);
        if (f > .01f || er > .01f) G.rr(X - 3, y - 3, w + 6, h + 6, fr + 3, fade(mix(alpha(ACTION, .45f), alpha(ERR, .5f), er), al * std::max(f, er)));
        G.rr(X, y, w, h, fr, fade(mix(rgba(255, 255, 255, 18), rgba(255, 255, 255, 26), hv), al));
        G.rr(X, y, w, h, fr, fade(rgba(0, 0, 0, 40), al * f));
        G.rrLine(X + .5f, y + .5f, w - 1, h - 1, fr - .5f, fade(mix(alpha(WHITE, .10f), alpha(ERR, .7f), er), al), 1);
        if (!icon.empty())
            G.icon(icon.c_str(), X + (h < 34 ? 18 : 25), y + h / 2, h < 34 ? 14 : 17,
                   fade(mix(rgb(0x98989D), ERR, er * .8f), al), 1.7f);
        // the label rests in the middle and floats up to a small caption once there is something to type
        bool compact = h < 46;
        float ls = compact ? fontSize - .5f : 14.5f + (11 - 14.5f) * fl;
        float lb = compact ? Gfx::mid(h / 2, ls) : (h / 2 + 5.2f) + (18.5f - (h / 2 + 5.2f)) * fl;
        if (!compact || text.empty())
        G.text(label, X + 16 + o, y + lb, MEDIUM, ls,
               fade(mix(rgb(0x8E8E93), ERR, er * .8f), al));
        float vw = viewW(), base = compact ? Gfx::mid(y + h / 2, fontSize) : y + h - 13.5f, cx = px(caret);
        G.clip(X + 14 + o, y + (compact ? 3 : 22), vw + 4, h - (compact ? 6 : 22));
        if (cx - scroll > vw) scroll = cx - vw;
        if (cx - scroll < 0) scroll = cx;
        if (px((int)text.size()) - scroll < vw && scroll > 0) scroll = std::max(0.f, px((int)text.size()) - vw);
        if (hasSel()) {
            float sa = px(std::min(caret, anchor)) - scroll, sb = px(std::max(caret, anchor)) - scroll;
            G.rr(X + 16 + o + sa, base - fontSize, sb - sa, fontSize * 1.4f, 3, fade(alpha(ACTION, .45f), al));
        }
        if (!compact && text.empty() && focused && !hint.empty()) G.text(hint, X + 16 + o, base, MEDIUM, fontSize, fade(DIM, al * f));
        if (!text.empty()) {
            G.raw = true;
            G.text(U(shownW()), X + 16 + o - scroll, base, MEDIUM, fontSize, fade(TEXT, al * fl));
            G.raw = false;
        }
        if (focused && (T - focusAt < .6 || fmod(T * 1.7, 1) < .58))
            G.rect(X + 16 + o + cx - scroll, base - fontSize * .95f, 1.5f, fontSize * 1.2f, fade(ACTION, al));
        G.unclip();
        if (password)
            G.icon(show ? "eye" : "eye-off", X + w - 22, y + h / 2, 17, fade(mix(DIM, TEXT, f * .5f), al), 1.5f);
    }
};

// ================================================================================================ the scene
enum Stage { LOGIN, WORKING, READY };
enum Page { HOME, MODS, PACKS, SETTINGS, ACCOUNTS };

struct Scene {
    Stage cur = LOGIN;
    Page page = HOME;
    double pageAt = -10;
    std::map<int, float> scroll;
    vector<Widget*> chrome;
    vector<Clickable*> nav;
    IconBtn minBtn{"min", "min"}, closeBtn{"close", "x"};
    // the admins' corner of the profile: Premium for an account, by its login
    Field grantLogin{"grantLogin", "Логин игрока"};
    Btn grantBtn{"grantBtn", "Выдать Premium"}, premListBtn{"premListBtn", "Список"};
    string role, grantNote, grantName;
    Col grantColor = MUTED;
    bool grantBusy = false;
    // "Grant Premium" gives it for the days typed to its right; "Lifetime" past them gives it for ever
    Field grantDays{"grantDays", "Дней"};
    Btn lifetimeBtn{"lifetimeBtn", "Навсегда"};
    // the list of the accounts with Premium; a click on one shows what can be done with it
    bool premOpen = false, premBusy = false;
    string premError;
    vector<Api::PremiumAccount> premRows;
    vector<std::unique_ptr<Clickable>> premRowW;
    string premSel;  // the login of the row that is open
    // on the open row: the days to give, then Give days | Lifetime | Take away
    Field premDays{"premDays", "Дней"};
    Btn premAct[3] = {Btn{"premAct0", "Выдать дни"}, Btn{"premAct1", "Навсегда"}, Btn{"premAct2", "Забрать"}};
    void premGiveDays() {
        string d = trim(premDays.value());
        int days = d.empty() || d.size() > 4 ? 0 : atoi(d.c_str());
        if (days < 1 || days > 3650) {
            premDays.error = true;
            premDays.shake();
            grantNote = "Введи дни: от 1 до 3650";
            grantName.clear();
            grantColor = ERR;
            setFocus(&premDays);
            return;
        }
        // a Premium with an end gets the days added to it; a lifetime one becomes these days from today
        bool lifetime = false;
        for (auto& r : premRows)
            if (r.login == premSel) lifetime = r.until == 0;
        premiumAction(premSel, days, false, !lifetime);
    }
    static constexpr float PREM_ROW = 44;
    float premListH() const {  // the height the open list takes in the account's card
        if (!premOpen) return 0;
        return 10 + std::max<size_t>(1, premRows.size()) * PREM_ROW + 6;
    }
    float grantRowsH() const { return 56 + (grantNote.empty() ? 0.f : 20.f); }  // the row, and the note under it when there is one
    bool isAdmin() const { return lower(trim(role)) == "admin"; }
    static string termText(int64_t until) {
        if (!until) return Lang::t("навсегда");
        time_t t = (time_t)until;
        tm local{};
        localtime_s(&local, &t);
        char buf[32];
        strftime(buf, sizeof buf, "%d.%m.%Y", &local);
        return Lang::t("до") + " " + buf;
    }
    void loadPremium() {
        if (premBusy) return;
        premBusy = true;
        premError.clear();
        string token = session.token;
        std::thread([this, token] {
            vector<Api::PremiumAccount> rows;
            string fail;
            try {
                rows = Api::premiumAccounts(token);
            } catch (std::exception& e) {
                fail = e.what();
            }
            post([this, rows, fail] {
                premBusy = false;
                premError = fail;
                if (fail.empty()) premRows = rows;
                bool still = false;
                for (auto& r : premRows) still |= r.login == premSel;
                if (!still) premSel.clear();
                wake(.6);
            });
        }).detach();
    }
    // days (added to the term when extend), for ever with 0, or take away the Premium of an account from the list
    void premiumAction(const string& login, int days, bool take, bool extend = true) {
        if (grantBusy || login.empty()) return;
        grantBusy = true;
        grantNote.clear();
        grantName.clear();
        string token = session.token;
        std::thread([this, login, days, take, token, extend] {
            string name, fail;
            try {
                name = Api::setPlan(token, login, !take, days, extend);
            } catch (std::exception& e) {
                fail = e.what();
            }
            post([this, name, fail, take, days] {
                grantBusy = false;
                if (!fail.empty()) {
                    grantNote = fail;
                    grantColor = ERR;
                } else {
                    grantNote = take ? "Premium снят" : days ? "Premium продлён" : "Premium навсегда";
                    grantName = days && !take ? name + "  ·  +" + std::to_string(days) + " " + Lang::t("дн.") : name;
                    grantColor = OK;
                    premDays.set("");
                    if (take) premSel.clear();
                }
                loadPremium();
                wake(.6);
            });
        }).detach();
    }
    // Premium for the login typed: lifetime, or for the days typed beside "Grant Premium"
    void grant(bool lifetime) {
        string who = trim(grantLogin.value());
        if (grantBusy) return;
        auto refuse = [this](Field& f, const char* note) {
            f.error = true;
            f.shake();
            grantNote = note;
            grantName.clear();
            grantColor = ERR;
            setFocus(&f);
        };
        if (who.empty()) return refuse(grantLogin, "Введи логин");
        int days = 0;
        if (!lifetime) {
            string d = trim(grantDays.value());
            days = d.empty() || d.size() > 4 ? 0 : atoi(d.c_str());
            if (days < 1 || days > 3650) return refuse(grantDays, "Введи дни: от 1 до 3650");
        }
        grantBusy = true;
        grantNote.clear();
        grantName.clear();
        string token = session.token;
        std::thread([this, who, token, days] {
            string name, fail;
            try {
                name = Api::setPlan(token, who, true, days);
            } catch (std::exception& e) {
                fail = e.what();
            }
            post([this, name, fail, days] {
                grantBusy = false;
                if (!fail.empty()) {
                    grantNote = fail;
                    grantName.clear();
                    grantColor = ERR;
                    grantLogin.error = true;
                } else {
                    grantNote = "Premium выдан";
                    grantName = name + "  ·  " + (days ? std::to_string(days) + " " + Lang::t("дн.") : Lang::t("Навсегда"));
                    grantColor = OK;
                    grantLogin.set("");
                    grantDays.set("");
                    if (premOpen) loadPremium();
                }
                wake(.6);
            });
        }).detach();
    }
    Clickable langBtn{"lang"};
    Field searchField{"search", "Поиск мода"};
    bool modsRemote = true, packsRemote = true, showCategories = false;
    Clickable categoryToggle{"categoryToggle"};
    string discordUrl =
        "https://discord.gg/djyhbYYDyf";
    bool arcBusy = false;
    std::atomic<bool> arcCancel{false};
    bool signedIn() const { return !session.token.empty(); }
    Btn trustBtn{"trust", "Trust mode"};  // the same game as visuals only: nothing that acts for the player, no Lua
    Btn loginBtn{"login", "Начать"}, discordBtn{"discordBtn", "Наш Discord"}, playBtn{"play", "Играть"},
        autoRamBtn{"autoRam", "Авто"}, gameDirBtn{"gameDir", "Папка игры"}, modsDirBtn{"modsDir", "Папка модов"},
        moveBtn{"move", "Сменить расположение"}, defaultBtn{"default", "По умолчанию"},
        updateBtn{"update", "Проверить обновления"},
        openModsBtn{"openMods", "Открыть папку"}, openPacksBtn{"openPacks", "Открыть папку"};
    Slider memory{"memory", 0};
    Toggle cleanToggle{"cleanAfterPlay", true};
    Field jvmField{"jvmArgs", "-XX:+UseG1GC"};
    double saveAt = 0;
    Clickable filters[3] = {Clickable{"filter0"}, Clickable{"filter1"}, Clickable{"filter2"}};
    int modFilter = 0;
    std::map<string, std::unique_ptr<Toggle>> rowToggles;
    std::map<string, std::unique_ptr<Btn>> installBtns;
    Widget *pressedW = nullptr, *focusW = nullptr;

    // ---- session
    Session session;
    string clientJar;
    HANDLE gameProc = nullptr;
    bool exitAfterGame = false;
    string userName, version, plan, errMsg, lastErr, note, caption, prevCaption, prepDetail, filesNote, updateNote;
    Col noteColor = MUTED, updateColor = MUTED;
    int step = 0;
    double stepAt = 0, captionAt = -10, speedAt = 0, dlSpeed = 0;
    int64_t dlDone = 0, dlTotal = 0, speedFrom = 0;
    float shown = 0, prepFrac = 0;
    bool preparing = false, playBusy = false;

    // ---- the game's files and the Modrinth catalogue
    vector<Game::Mod> mods;
    vector<Game::Pack> packs;
    std::mutex iconLock;
    std::map<string, string> iconBytes;  // key -> raw image bytes, read on worker threads
    double scannedAt = -10;
    std::atomic<bool> scanning{false};
    vector<Modrinth::Hit> catalog;
    string catalogFor;
    bool catalogLoaded = false, catalogFailed = false;
    std::atomic<bool> catalogBusy{false};
    double typedAt = 0;
    std::map<string, vector<Modrinth::Hit>> searchCache;
    std::atomic<unsigned> modRequest{0}, packRequest{0};
    std::atomic<int> activeSearches{0};
    string modInFlight, packInFlight;
    float catalogY = 0;
    float contentHeight = 0;
    std::map<string, string> installState;  // project id -> busy / done / none / error
    // ---- the catalogue's categories, the resource packs from Modrinth, and the preview of a project
    Field packSearch{"packSearch", "Поиск ресурспака"};
    vector<std::unique_ptr<Clickable>> modCats, packCats;
    int modCat = 0, packCat = 0;
    std::map<string, std::unique_ptr<Clickable>> viewBtns;  // a catalogue row: a click opens its preview
    vector<Modrinth::Hit> packCatalog;
    string packCatalogFor, packError, modError;
    Btn sourcesBtn{"sources","Источники"}, importPackBtn{"importPack","Добавить ZIP"};
    bool sourcesOpen = false;
    int sourceFocus = 0;
    Clickable sourceOptions[3] = {Clickable{"source.pmc"},Clickable{"source.mr"},Clickable{"source.all"}};
    bool packLoaded = false, packFailed = false;
    std::atomic<bool> packBusy{false};
    double packTypedAt = 0;
    float packCatalogY = 0;
    std::map<string, string> packInstalled;  // project id -> its file in resourcepacks/
    // ---- updates of installed mods
    std::map<string, Modrinth::Update> modUpdates;  // file name in mods/ -> its newer build
    std::map<string, string> hashCache;              // "file|size|time" -> SHA-1 (a jar is hashed once)
    string updatesFor;                               // the folder + version the list above was made for
    double updatesAskedAt = -100;
    bool updatesBusy = false, updatingAll = false;
    Btn updateAllBtn{"updateAll", "Обновить все"};
    string modsSignature() const {
        string s = Config::mcVersion + "#";
        for (auto& m : mods) s += S(m.file.filename()) + "|" + std::to_string(m.size) + ";";
        return s;
    }
    void checkModUpdates() {
        if (updatesBusy || updatingAll || T - updatesAskedAt < 3) return;
        string sig = modsSignature();
        if (sig == updatesFor) return;
        updatesBusy = true;
        updatesAskedAt = T;
        vector<Game::Mod> list = mods;
        string version = Config::mcVersion;
        auto cache = hashCache;
        std::thread([this, list, version, sig, cache] {
            auto hashes = cache;
            std::set<string> managed;
            string text;
            if (readFile(Game::modsDir() / L".arcanic-managed", text))
                for (auto& l : split(text, "\r\n")) managed.insert(lower(trim(l)));
            std::map<string, string> byHash;  // sha1 -> file name
            for (auto& m : list) {
                string fn = S(m.file.filename()), plain = lower(replaceAll(fn, ".disabled", ""));
                if (m.required || managed.count(plain)) continue;
                std::error_code ec;
                auto when = fs::last_write_time(m.file, ec).time_since_epoch().count();
                string key = fn + "|" + std::to_string(m.size) + "|" + std::to_string((long long)when);
                if (!hashes.count(key)) hashes[key] = Installer::sha1File(m.file);
                if (!hashes[key].empty()) byHash[lower(hashes[key])] = fn;
            }
            vector<string> sums;
            for (auto& kv : byHash) sums.push_back(kv.first);
            std::map<string, Modrinth::Update> found;
            bool ok = true;
            try {
                for (auto& kv : Modrinth::updates(sums, version)) found[byHash[kv.first]] = kv.second;
            } catch (const std::exception& e) {
                ok = false;
                Log::warn(string("mod updates: ") + e.what());
            }
            post([this, found, ok, sig, hashes] {
                updatesBusy = false;
                hashCache = hashes;
                if (!ok) return;
                updatesFor = sig;
                modUpdates = found;
                dirty = true;
            });
        }).detach();
    }
    void updateAllMods() {
        if (updatingAll || modUpdates.empty()) return;
        if (gameProc || playBusy) {
            setNote("Закрой игру, чтобы обновить моды", WARN);
            return;
        }
        updatingAll = true;
        updateAllBtn.busy = true;
        auto todo = modUpdates;
        fs::path dir = Game::modsDir();
        std::thread([this, todo, dir] {
            int done = 0, failed = 0;
            for (auto& kv : todo) {
                fs::path old = dir / P(kv.first);
                bool off = ends(lower(kv.first), ".disabled");
                fs::path fresh = dir / P(kv.second.filename + (off ? ".disabled" : ""));
                try {
                    Net::file(kv.second.url, fresh, kv.second.sha1, nullptr);
                    if (fresh != old) removeFile(old);
                    done++;
                    Log::info("mod updated: " + kv.first + " -> " + S(fresh.filename()));
                } catch (const std::exception& e) {
                    failed++;
                    Log::warn("mod update failed: " + kv.first + ": " + e.what());
                }
            }
            post([this, done, failed] {
                updatingAll = false;
                updateAllBtn.busy = false;
                modUpdates.clear();
                updatesFor.clear();
                scannedAt = -10;
                if (!failed)
                    setNote(plural(done, "мод обновлён", "мода обновлено", "модов обновлено", "mod updated", "mods updated"), OK);
                else
                    setNote(fmt(Lang::en ? "Updated %d, failed %d - the log has the reasons" : "Обновлено %d, не вышло %d — причины в логе", done, failed), WARN);
                UiAudio::play(failed ? UiAudio::FAIL : UiAudio::CLICK);
                dirty = true;
            });
        }).detach();
    }
    // ---- paging: Modrinth answers 30 at a time; the next 30 come when the list is scrolled near its end
    int modMrFetched = 0, modMrTotal = 0, packMrFetched = 0, packMrTotal = 0, modCfFetched = 0, modCfTotal = 0;
    bool modMoreBusy = false, packMoreBusy = false;
    std::map<string, std::pair<int, int>> searchMeta;  // search key -> {Modrinth hits fetched, how many there are}
    std::map<string, std::pair<int, int>> cfMeta;      // the same for CurseForge (mods only)
    bool moreToCome(bool packs) const {
        if (packs) return (Config::packSources & 2) && packMrFetched < packMrTotal;
        return ((Config::modSources & 1) && modMrFetched < modMrTotal) || ((Config::modSources & 2) && modCfFetched < modCfTotal);
    }
    // One page of mods from the chosen sites, asked at the same time and taken in turn. A CurseForge mod that Modrinth
    // already gave (by name) is left out, so the same mod is not listed twice.
    struct ModPage {
        vector<Modrinth::Hit> hits;
        int mrGot = 0, mrTotal = -1, cfGot = 0, cfTotal = -1;
        string error;
    };
    static ModPage modPage(const string& query, const string& version, const string& category, int mask, int mrOffset, int cfOffset,
                           const vector<Modrinth::Hit>& already = {}) {
        ModPage out;
        std::future<vector<Modrinth::Hit>> mr, cf;
        int mrTotal = 0, cfTotal = 0;
        if (mask & 1) mr = std::async(std::launch::async, [&] { return Modrinth::search(query, version, "mod", category, mrOffset, &mrTotal); });
        if (mask & 2) cf = std::async(std::launch::async, [&] { return CurseForge::search(query, version, category, cfOffset, &cfTotal); });
        vector<Modrinth::Hit> fromMr, fromCf;
        if (mr.valid()) try { fromMr = mr.get(); out.mrGot = (int)fromMr.size(); out.mrTotal = mrTotal; } catch (const std::exception& e) { out.error = e.what(); }
        if (cf.valid()) try { fromCf = cf.get(); out.cfGot = (int)fromCf.size(); out.cfTotal = cfTotal; } catch (const std::exception& e) { if (out.error.empty()) out.error = e.what(); }
        std::set<string> names;
        for (const vector<Modrinth::Hit>* list : {&already, (const vector<Modrinth::Hit>*)&fromMr})
            for (auto& h : *list) if (h.source == "modrinth") { names.insert(Modrinth::plain(h.title)); names.insert(Modrinth::plain(h.slug)); }
        for (size_t i = 0; i < std::max(fromMr.size(), fromCf.size()); i++) {
            if (i < fromMr.size()) out.hits.push_back(fromMr[i]);
            if (i < fromCf.size() && !names.count(Modrinth::plain(fromCf[i].title)) && !names.count(Modrinth::plain(fromCf[i].slug)))
                out.hits.push_back(fromCf[i]);
        }
        return out;
    }
    bool previewOpen = false;
    Modrinth::Hit preview;
    int previewAt = 0;
    std::set<string> galBusy;
    IconBtn pvClose{"pvClose", "x"}, pvPrev{"pvPrev", "back"}, pvNext{"pvNext", "fwd"};
    Btn pvInstall{"pvInstall", "Установить"};
    static constexpr float PV_W = 760, PV_H = 566;
    float pvX() const { return SIDE + (WIN_W - SIDE - PV_W) / 2; }
    float pvY() const { return (WIN_H - PV_H) / 2 + 8; }
    // ---- the account: who the player is in the game
    Clickable msAdd{"msAdd"};
    string accErr;
    vector<Microsoft::Account> microsoftAccounts;
    bool microsoftBusy = false;
    std::atomic<bool> microsoftCancel{false};
    static bool licensedKey(const string& key) { return starts(key, "ms:"); }
    string selectedAccount() const {
        return Config::microsoftAccount.empty() ? offlineNick() : "ms:" + Config::microsoftAccount;
    }
    string accountName(const string& key) const {
        if (licensedKey(key)) {
            for (const auto& a : microsoftAccounts)
                if ("ms:" + a.uuid == key) return a.name;
            return "Microsoft";
        }
        return key;
    }
    void loadMicrosoft() {
        try {
            microsoftAccounts = Microsoft::list();
        } catch (const std::exception& e) {
            accErr = e.what();
        }
    }
    void signInMicrosoft() {
        if (microsoftBusy) {
            microsoftCancel = true;
            return;
        }
        if (playBusy) return;
        microsoftBusy = true;
        microsoftCancel = false;
        accErr.clear();
        setNote(Microsoft::message("Открываем вход Microsoft…", "Opening Microsoft sign-in…"), MUTED);
        std::thread([this] {
            try {
                auto a = Microsoft::login(microsoftCancel,
                                          [this](const string& text) { post([this, text] { setNote(text, MUTED); }); });
                post([this, a] {
                    microsoftBusy = false;
                    loadMicrosoft();
                    Config::microsoftAccount = a.uuid;
                    Config::save();
                    markAdded("ms:" + a.uuid);
                    accAdding = false;
                    accView = "ms:" + a.uuid;
                    accountDone();
                    setNote(Microsoft::message("Аккаунт Microsoft добавлен: ", "Microsoft account added: ") + a.name,
                            MUTED);
                });
            } catch (const std::exception& e) {
                string text = e.what();
                post([this, text] {
                    microsoftBusy = false;
                    accErr = text;
                    setNote(text, ERR);
                    MessageBoxW(hwnd, W(text).c_str(), L"Microsoft / Arcanic", MB_OK | MB_ICONINFORMATION);
                });
            }
        }).detach();
        wake(1);
    }
    Field accNick{"accNick", "Введите никнейм"};
    IconBtn accAdd{"accAdd", "plus"};
    // the Accounts page: a big card for the account in view, then a tile per account (the toolbar's plus adds one)
    bool accAdding = false;
    string accView, accNote;
    double accNoteAt = -10;
    float pageDirection = 1;
    Toggle soundsToggle{"launcherSounds", true}, startupToggle{"startupAnimation", true};
    Slider menuScale{"menuScale", .5f};
    Btn resetScale{"resetScale", "Сбросить"};
    Clickable langSetting{"langSetting"};
    IconBtn heroCopy{"heroCopy", "copy"},
        heroRefresh{"heroRefresh", "refresh"}, heroDelete{"heroDelete", "trash"};
    Btn heroSelect{"heroSelect", "Выбрать"}, accBack{"accBack", "Назад"};
    static constexpr float HERO_H = 236, CHIP_H = 64, ADD_CARD_H = 452;
    // The accounts the player saved: nicks first, then the Microsoft ones (the default nick is not one of them)
    vector<string> savedAccounts() const {
        vector<string> out;
        for (auto& a : split(Config::accounts, ",")) {
            if (trim(a).empty()) continue;
            string n = Installer::offlineName(trim(a));
            if (std::find(out.begin(), out.end(), n) == out.end() && out.size() < 30) out.push_back(n);
        }
        for (const auto& a : microsoftAccounts) out.push_back("ms:" + a.uuid);
        return out;
    }
    bool addMode() const { return accAdding || savedAccounts().empty(); }
    string viewKey() const {
        auto l = savedAccounts();
        if (std::find(l.begin(), l.end(), accView) != l.end()) return accView;
        string cur = selectedAccount();
        if (std::find(l.begin(), l.end(), cur) != l.end()) return cur;
        return l.empty() ? "" : l[0];
    }
    string addedOn(const string& key) const {
        for (auto& e : split(Config::accountDates, ";")) {
            size_t eq = e.find('=');
            if (eq != string::npos && e.substr(0, eq) == key) return e.substr(eq + 1);
        }
        return "";
    }
    void markAdded(const string& key) {
        if (!addedOn(key).empty()) return;
        SYSTEMTIME t;
        GetLocalTime(&t);
        Config::accountDates +=
            (Config::accountDates.empty() ? "" : ";") + key + "=" + fmt("%02d.%02d.%04d", t.wDay, t.wMonth, t.wYear);
        Config::save();
    }
    void accSay(const string& s) {
        accNote = s;
        accNoteAt = T;
        wake(2.5);
    }
    static void copyText(const string& s) {
        wstring w = W(s);
        if (!OpenClipboard(hwnd)) return;
        EmptyClipboard();
        if (HGLOBAL g = GlobalAlloc(GMEM_MOVEABLE, (w.size() + 1) * sizeof(wchar_t))) {
            memcpy(GlobalLock(g), w.c_str(), (w.size() + 1) * sizeof(wchar_t));
            GlobalUnlock(g);
            SetClipboardData(CF_UNICODETEXT, g);
        }
        CloseClipboard();
    }
    /** The skin asked again: from Mojang for a nick, after a fresh Microsoft sign-in for a licensed account. */
    void refreshAccount(const string& key) {
        string name = accountName(key), k = "head:" + lower(name);
        {
            std::lock_guard<std::mutex> l(iconLock);
            iconBytes.erase(k);
            headBusy.erase(k);
        }
        auto it = G.bitmaps.find(k);
        if (it != G.bitmaps.end()) {
            if (it->second) it->second->Release();
            G.bitmaps.erase(it);
        }
        removeFile(Config::stateDir() / L"cache" / L"heads" / P(lower(name) + ".png"));
        accSay("Обновляем…");
        if (!licensedKey(key)) {
            post([this] { accSay("Скин обновлён"); });
            return;
        }
        string uuid = key.substr(3);
        std::thread([this, uuid] {
            string err;
            try {
                Microsoft::refresh(uuid);
            } catch (const std::exception& e) {
                err = e.what();
            }
            post([this, err] {
                loadMicrosoft();
                accSay(err.empty() ? "Скин обновлён" : err);
            });
        }).detach();
    }
    vector<std::unique_ptr<Clickable>> accRows;
    float accCardsY = 0;
    vector<std::pair<string, string>> serverVersions;  // id, a short note ("рекомендуем", ...)
    vector<std::pair<string, string>> versionList() const {
        auto out = serverVersions;
        bool has = false;
        for (auto& v : out)
            if (v.first == Config::mcVersion) has = true;
        if (!has) out.insert(out.begin(), {Config::mcVersion, ""});
        return out;
    }
    void pickVersion(const string& id) {
        wake(1);
        if (id != Config::mcVersion && !playBusy && !gameProc) {
            if (!Game::switchMods(id)) {
                setNote("Не удалось сменить моды версии — закрой программы, которые держат файлы в папке mods", WARN);
                return;
            }
            Config::mcVersion = id;
            scannedAt = -10;
            Config::save();
            catalogLoaded = packLoaded = false;
            auto tag = G.bitmaps.find("tex:item/name_tag");
            if (tag != G.bitmaps.end()) {
                release(tag->second);
                G.bitmaps.erase(tag);
            }
            setNote("Версия игры: Fabric " + id, MUTED);
        }
    }
    // ---- Play goes through stages, the way Lunar does: the client, the game, Java, the files, the start
    int playStage = 0;
    static int stageOf(const string& caption) {
        string c = lower(caption);
        if (c.find("обновлен") != string::npos && c.find("клиент") != string::npos) return 0;
        if (c.find("версию игры") != string::npos) return 1;
        if (c.find("java") != string::npos) return 2;
        if (c.find("целостност") != string::npos || c.find("докачиваем") != string::npos ||
            c.find("загружаем игру") != string::npos)
            return 3;
        if (c.find("microsoft") != string::npos || c.find("запускаем") != string::npos) return 4;
        return -1;
    }
    bool modal() const { return previewOpen || errOpen; }
    // ---- the alert, as macOS shows one
    bool errOpen = false;
    string errTitle, errText;
    fs::path errPath;
    double errCopiedAt = -10;
    Btn errCopy{"errCopy", "Скопировать путь"}, errFolder{"errFolder", "Открыть папку"}, errClose{"errClose", "Закрыть"};
    static constexpr float ERR_W = 440, ERR_H = 300;
    float errX() const { return (WIN_W - ERR_W) / 2; }
    float errY() const { return (WIN_H - ERR_H) / 2; }
    void showError(const string& title, const string& text, const fs::path& path) {
        Log::error(title + ": " + text + " (log: " + S(path) + ")");
        errTitle = title;
        errText = text;
        errPath = path;
        errOpen = true;
        errCopiedAt = -10;
        previewOpen = sourcesOpen = false;
        setFocus(nullptr);
        pressedW = nullptr;
        wake(1);
    }
    void errorDialog() {
        float k = an.to("err.dlg", errOpen ? 1.f : 0.f, 14);
        if (k < .005f) return;
        G.rect(0, 0, WIN_W, WIN_H, rgba(0, 0, 0, (int)lroundf(90 * k)));
        float x0 = errX(), y0 = errY(), cx = x0 + ERR_W / 2;
        DialogPop pop(k, cx, y0 + ERR_H / 2);
        G.glow(x0, y0 + 12, ERR_W, ERR_H, 18, alpha(rgb(0), .45f * k), 30);
        G.glass(x0, y0, ERR_W, ERR_H, 18, rgba(38, 38, 44, 215), k);
        appIcon(cx - 27, y0 + 22, 54, k);
        G.circle(cx + 22, y0 + 70, 11, fade(rgb(0x2A2A30), k));  // the alert badge on the icon
        G.circle(cx + 22, y0 + 70, 9, fade(WARN, k));
        G.rect(cx + 21, y0 + 64.5f, 2, 6.5f, fade(WHITE, k));
        G.rect(cx + 21, y0 + 72.5f, 2, 2, fade(WHITE, k));
        G.headline = true;
        G.center(G.fit(errTitle, SEMI, 15, ERR_W - 48), cx, y0 + 104, SEMI, 15, fade(TEXT, k));
        G.headline = false;
        int n = 0;
        for (auto& l : wrap(Lang::t(errText), MEDIUM, 12.5f, ERR_W - 56, 3))
            G.center(l, cx, y0 + 128 + 18 * n++, MEDIUM, 12.5f, fade(rgb(0xC7C7CC), k));
        float py = y0 + ERR_H - 20 - 30 - 16 - 32;
        G.center("Лог ошибки — пришли этот файл нам в Discord", cx, py - 10, MEDIUM, 11.5f, fade(MUTED, k));
        G.rr(x0 + 20, py, ERR_W - 40, 32, 8, fade(rgba(0, 0, 0, 70), k));
        G.rrLine(x0 + 20.5f, py + .5f, ERR_W - 41, 31, 7.5f, fade(alpha(WHITE, .06f), k));
        G.icon("folder", x0 + 36, py + 16, 13, fade(MUTED, k), 1.6f);
        G.raw = true;
        G.text(G.fit(S(errPath), MEDIUM, 12, ERR_W - 84), x0 + 52, Gfx::mid(py + 16, 12), MEDIUM, 12, fade(TEXT, k));
        G.raw = false;
        errCopy.text = T - errCopiedAt < 1.8 ? "Скопировано" : "Скопировать путь";
        for (Btn* b : {&errFolder, &errClose, &errCopy}) b->paint(k);
    }    
    vector<string> accounts() const {
        vector<string> out;
        for (auto& a : split(Config::accounts, ",")) {
            if (trim(a).empty()) continue;
            string n = Installer::offlineName(trim(a));
            if (std::find(out.begin(), out.end(), n) == out.end() && out.size() < 5) out.push_back(n);
        }
        string cur = offlineNick();
        if (std::find(out.begin(), out.end(), cur) == out.end()) out.insert(out.begin(), cur);
        for (const auto& a : microsoftAccounts) out.push_back("ms:" + a.uuid);
        return out;
    }
    std::set<string> headBusy;
    // The account's skin as a picture (asked for in the background the first time; null until it is here)
    ID2D1Bitmap* skinOf(const string& name) {
        string k = "head:" + lower(name);
        bool known;
        {
            std::lock_guard<std::mutex> l(iconLock);
            known = iconBytes.count(k) > 0;
            if (!known && !headBusy.count(k)) {
                headBusy.insert(k);
                std::thread([this, name, k] {
                    string bytes;
                    try {
                        bytes = Skins::skin(name);
                    } catch (...) {}
                    {
                        std::lock_guard<std::mutex> l2(iconLock);
                        if (!bytes.empty()) iconBytes[k] = bytes;
                    }
                    post([this] { dirty = true; });
                }).detach();
            }
        }
        ID2D1Bitmap* skin = known ? picture(k) : nullptr;
        return skin && skin->GetSize().width >= 64 && skin->GetSize().height >= 32 ? skin : nullptr;
    }
    // The whole player from the front, the outer layer over the inner one, pixels kept sharp: 16 x 32 skin pixels
    void body(const string& name, float x, float y, float h, float a) {
        ID2D1Bitmap* sk = skinOf(name);
        if (!sk) return;
        D2D1_SIZE_F sz = sk->GetSize();
        float u = sz.width / 64, q = h / 32;
        bool tall = sz.height >= sz.width;
        auto part = [&](float sx, float sy, float sw, float sh, float dx, float dy, float grow) {
            D2D1_RECT_F src = D2D1::RectF(sx * u, sy * u, (sx + sw) * u, (sy + sh) * u);
            D2D1_RECT_F dst = D2D1::RectF(x + (dx - grow) * q, y + (dy - grow) * q, x + (dx + sw + grow) * q,
                                          y + (dy + sh + grow) * q);
            G.rt->DrawBitmap(sk, dst, clamp01(a), D2D1_BITMAP_INTERPOLATION_MODE_NEAREST_NEIGHBOR, src);
        };
        G.rt->FillEllipse(D2D1::Ellipse(D2D1::Point2F(x + 8 * q, y + 32 * q), 7 * q, 1.2f * q),
                          G.use(fade(rgba(0, 0, 0, 90), a)));
        // the inner layer: legs, arms, body, head (the left limbs of an old 64 x 32 skin are its right ones)
        part(4, 20, 4, 12, 4, 20, 0);
        part(tall ? 20 : 4, tall ? 52 : 20, 4, 12, 8, 20, 0);
        part(44, 20, 4, 12, 0, 8, 0);
        part(tall ? 36 : 44, tall ? 52 : 20, 4, 12, 12, 8, 0);
        part(20, 20, 8, 12, 4, 8, 0);
        part(8, 8, 8, 8, 4, 0, 0);
        // the outer layer, a hair bigger, as the game draws it
        if (tall) {
            part(4, 36, 4, 12, 4, 20, .25f);
            part(4, 52, 4, 12, 8, 20, .25f);
            part(44, 36, 4, 12, 0, 8, .25f);
            part(52, 52, 4, 12, 12, 8, .25f);
            part(20, 36, 8, 12, 4, 8, .25f);
        }
        part(40, 8, 8, 8, 4, 0, .5f);
    }
    void head(const string& name, float x, float y, float s, float a) {
        string k = "head:" + lower(name);
        bool known;
        {
            std::lock_guard<std::mutex> l(iconLock);
            known = iconBytes.count(k) > 0;
            if (!known && !headBusy.count(k)) {
                headBusy.insert(k);
                std::thread([this, name, k] {
                    string bytes;
                    try {
                        bytes = Skins::skin(name);
                    } catch (...) {}
                    {
                        std::lock_guard<std::mutex> l2(iconLock);
                        if (!bytes.empty()) iconBytes[k] = bytes;
                    }
                    post([this] {
                        dirty = true;
                    });
                }).detach();
            }
        }
        ID2D1Bitmap* skin = known ? picture(k) : nullptr;
        if (skin && skin->GetSize().width >= 64 && skin->GetSize().height >= 32)
            G.face(skin, x, y, s, s * .26f, a);
        else
            letterTile(name, x, y, s, a);
    }
    void saveAccounts(const vector<string>& list) {
        string s;
        for (auto& a : list)
            if (!licensedKey(a)) s += (s.empty() ? "" : ",") + a;
        Config::accounts = s;
        Config::save();
    }
    void openAccounts() {
        if (!loggedIn()) return;
        accNick.set("");
        accNick.error = false;
        accErr.clear();
        accAdding = false;
        loadMicrosoft();
        openPage(ACCOUNTS);
    }
    void accountDone() {
        accNick.set("");
        accNick.error = false;
        setFocus(nullptr);
        wake(1);
    }
    void pickAccount(const string& name) {
        auto list = savedAccounts();
        if (microsoftBusy || playBusy) return;
        if (licensedKey(name))
            Config::microsoftAccount = name.substr(3);
        else {
            Config::microsoftAccount.clear();
            Config::nick = name;
        }
        saveAccounts(list);
        if (gameProc)
            setNote("Новый ник подействует при следующем запуске игры", MUTED);
        else
            setNote("В игру заходишь как " + accountName(name), MUTED);
        accountDone();
    }
    void removeAccount(const string& name) {
        if (microsoftBusy || playBusy) return;
        if (licensedKey(name)) {
            bool wasCurrent = Config::microsoftAccount == name.substr(3);
            try {
                Microsoft::remove(name.substr(3));
                if (wasCurrent) {
                    Config::microsoftAccount.clear();
                    Config::save();
                }
                loadMicrosoft();
            } catch (const std::exception& e) {
                accErr = e.what();
            }
            if (wasCurrent) {
                auto rest = savedAccounts();
                if (!rest.empty()) pickAccount(rest[0]);
            }
            if (accView == name) accView.clear();
            accSay("Аккаунт удалён");
            wake(.6);
            return;
        }
        bool wasCurrent = name == selectedAccount();
        auto list = savedAccounts();
        list.erase(std::remove(list.begin(), list.end(), name), list.end());
        saveAccounts(list);
        if (wasCurrent) {
            auto rest = savedAccounts();
            if (!rest.empty())
                pickAccount(rest[0]);
            else {
                Config::nick.clear();
                Config::save();
            }
        }
        if (accView == name) accView.clear();
        accSay("Аккаунт удалён");
        wake(.6);
    }
    void addAccount() {
        string v = trim(accNick.value()), clean;
        for (unsigned char c : v)
            if ((isalnum(c) || c == '_') && c < 128) clean += (char)c;
        if (clean.size() < 3 || clean.size() > 16 || clean != v) {
            accErr = "Ник: от 3 до 16 латинских букв, цифр или _";
            accNick.error = true;
            accNick.shake();
            setFocus(&accNick);
            wake(.8);
            return;
        }
        auto list = savedAccounts();
        list.erase(std::remove_if(list.begin(), list.end(), [](const string& key) { return licensedKey(key); }),
                   list.end());
        if (std::find(list.begin(), list.end(), clean) == list.end()) {
            if (list.size() >= 30) list.erase(list.begin());
            list.push_back(clean);
        }
        saveAccounts(list);
        markAdded(clean);
        accAdding = false;
        accView = clean;
        accNick.set("");
        pickAccount(clean);
    }
    ID2D1Bitmap* cardArt(int variant, float w, float h) {
        string key = fmt("cardart:%d:%d:%d:%d", variant, (int)w, (int)h, (int)lroundf(G.scale * 100));
        auto found = G.bitmaps.find(key);
        if (found != G.bitmaps.end()) return found->second;
        static const unsigned tints[] = {0xA6A8DD, 0x7FB2E8, 0xC59BE0, 0x86CFC0, 0xE6B98A, 0x8EA7F0, 0xB79CF2};
        Col tint = rgb(tints[variant % 7]);
        ID2D1BitmapRenderTarget* brt = nullptr;
        ID2D1Bitmap* result = nullptr;
        if (FAILED(G.rt->CreateCompatibleRenderTarget(D2D1::SizeF(w * G.scale, h * G.scale), &brt))) return nullptr;
        ID2D1RenderTarget* keep = G.rt;
        G.rt = brt;
        brt->BeginDraw();
        brt->SetTransform(D2D1::Matrix3x2F::Scale(G.scale, G.scale));
        uint32_t seed = 2166136261u ^ (uint32_t)(variant * 16777619u);
        auto rnd = [&] { seed ^= seed << 13; seed ^= seed >> 17; seed ^= seed << 5; return (seed & 0xFFFFFF) / 16777215.f; };
        G.rect(0, 0, w, h, rgb(0x0D0E17));
        G.rrGrad(0, 0, w, h, 0, mix(rgb(0x0D0E17), tint, .16f), rgb(0x0B0C13), 0, 0, 0, h);
        G.radial(w * (.55f + .3f * rnd()), h * .25f, h * 1.25f, alpha(tint, .30f));
        G.radial(w * .12f, h * 1.05f, h * .95f, alpha(rgb(0x7D67CB), .24f));
        for (int i = 0; i < 70; i++) {  // stars, fewer towards the ridge
            float sx = rnd() * w, sy = powf(rnd(), 1.6f) * h * .7f, s = .6f + rnd() * 1.1f;
            G.rect(sx, sy, s, s, alpha(mix(WHITE, tint, rnd() * .5f), .15f + .55f * rnd() * (1 - sy / h)));
        }
        // two rows of isometric blocks: the back row darker and higher, the front one closer and brighter
        float cube = std::max(10.f, h * .15f), dx = cube * .866f;
        for (int row = 0; row < 2; row++) {
            float base = h + cube * (row ? .55f : -.25f), shade = row ? 1.f : .55f, phase = variant * 1.7f + row * 3.1f;
            for (float cx = -dx; cx < w + dx * 2; cx += dx * 2) {
                int stack = 1 + (int)lroundf(1.6f + 1.4f * sinf(cx / w * 5.2f + phase) + 1.1f * sinf(cx / w * 11.3f + phase * 2.3f));
                for (int s = 0; s < std::max(1, stack); s++) {
                    float cy = base - s * cube;
                    bool lit = s == stack - 1 && rnd() > .82f;
                    Col top = mix(rgb(0x2A2B42), tint, lit ? .85f : .35f * shade), left = mix(rgb(0x171826), tint, .14f * shade),
                        right = rgb(0x0E0F18);
                    Gfx::Path t(G.d2d), l(G.d2d), r(G.d2d);
                    t.M(cx, cy - cube).L(cx + dx, cy - cube / 2).L(cx, cy).L(cx - dx, cy - cube / 2).Z();
                    l.M(cx - dx, cy - cube / 2).L(cx, cy).L(cx, cy + cube).L(cx - dx, cy + cube / 2).Z();
                    r.M(cx + dx, cy - cube / 2).L(cx, cy).L(cx, cy + cube).L(cx + dx, cy + cube / 2).Z();
                    G.fill(t, alpha(top, .55f + .45f * shade));
                    G.fill(l, alpha(left, .9f));
                    G.fill(r, alpha(right, .95f));
                    if (lit) G.radial(cx, cy - cube / 2, cube * 2.4f, alpha(tint, .35f));
                }
            }
        }
        G.rrGrad(0, h * .55f, w, h * .45f, 0, rgba(9, 9, 14, 0), rgba(9, 9, 14, 150), 0, h * .55f, 0, h);
        brt->EndDraw();
        G.rt = keep;
        brt->GetBitmap(&result);
        brt->Release();
        return G.bitmaps[key] = result;
    }
    static constexpr int ART_LICENSED = 5, ART_LOCAL = 6;
    // A card's picture inside the Arcanic shape, with a veil and (sink) a fade into the card below
    // Under the pointer the picture comes alive: the camera eases in and drifts slowly over the towers
    static D2D1::Matrix3x2F artMotion(float x, float y, float w, float h, float hv) {
        if (hv <= .001f) return D2D1::Matrix3x2F::Identity();
        an.moving = true;
        float zoom = 1 + .07f * hv, dx = sinf((float)T * .8f) * 6 * hv, dy = cosf((float)T * .6f) * 3 * hv;
        return D2D1::Matrix3x2F::Scale(zoom, zoom, D2D1::Point2F(x + w / 2, y + h * .6f)) * D2D1::Matrix3x2F::Translation(dx, dy);
    }
    void artTile(int variant, float x, float y, float w, float h, float, float, float, Col veil, float a, bool sink = true,
                 float radius = 12, float hover = 0) {
        ID2D1Bitmap* b = cardArt(variant, w, h);
        ID2D1BitmapBrush* bb = nullptr;
        if (b && SUCCEEDED(G.rt->CreateBitmapBrush(b, D2D1::BitmapBrushProperties(D2D1_EXTEND_MODE_CLAMP, D2D1_EXTEND_MODE_CLAMP,
                                                                                    D2D1_BITMAP_INTERPOLATION_MODE_LINEAR),
                                                    &bb))) {
            bb->SetTransform(D2D1::Matrix3x2F::Scale(1 / G.scale, 1 / G.scale) * D2D1::Matrix3x2F::Translation(x, y) * artMotion(x, y, w, h, hover));
            bb->SetOpacity(clamp01(a));
            G.fillShape(x, y, w, h, radius, bb);
            bb->Release();
        } else
            G.rr(x, y, w, h, radius, fade(CARD3, a));
        G.rr(x, y, w, h, radius, fade(veil, a));
        if (hover > .001f) G.rr(x, y, w, h, radius, fade(alpha(WHITE, .05f * hover), a));
        if (sink)
            G.rrGrad(x, y, w, h, radius, rgba(22, 22, 29, 0), fade(rgba(22, 22, 29, 235), a), 0, y + h * .42f, 0, y + h);
        G.rrLine(x + .5f, y + .5f, w - 1, h - 1, radius - .5f, fade(alpha(WHITE, .06f), a));
    }    static void msLogo(float x, float y, float s, float a) {
        Col c[] = {rgb(0xF25022), rgb(0x7FBA00), rgb(0x00A4EF), rgb(0xFFB900)};
        float q = (s - 2) / 2;
        for (int j = 0; j < 4; j++) G.rect(x + (j % 2) * (q + 2), y + (j / 2) * (q + 2), q, q, fade(c[j], a));
    }
    // "Аккаунты": the account in view as a large glass card (its whole skin at the right), then a tile per account
    void accountsPage() {
        if (addMode()) {
            addCards();
            return;
        }
        string key = viewKey(), name = accountName(key), cur = selectedAccount();
        bool premium = licensedKey(key);
        float x = CX, y = PAGE_TOP, w = CW, h = HERO_H;
        block(1, [&](float a) {
            G.glass(x, y, w, h, 14, TINT, a);
            string kind = premium ? "Microsoft" : "Локальный";  // the kind of account, as a small capsule
            float kw = G.width(Lang::t(kind), SEMI, 11.5f) + 36;
            G.rr(x + 22, y + 22, kw, 24, 12, fade(alpha(WHITE, .10f), a));
            if (premium)
                msLogo(x + 31, y + 28, 12, a);
            else
                G.icon("user", x + 37, y + 34, 12, fade(rgb(0xD1D1D6), a), 1.6f);
            G.text(kind, x + 48, Gfx::mid(y + 34, 11.5f), SEMI, 11.5f, fade(TEXT, a));
            G.accountFont = true;
            G.raw = true;
            G.text(G.fit(name, SEMI, 34, w - 380), x + 22, y + 104, SEMI, 34, fade(WHITE, a));
            G.raw = false;
            G.accountFont = false;
            string when = addedOn(key);
            G.text(when.empty() ? "Добавлен ранее" : "Добавлен: " + when, x + 24, y + 130, MEDIUM, 12.5f, fade(MUTED, a));
            heroSelect.paint(a);
            for (IconBtn* b : {&heroCopy, &heroRefresh, &heroDelete}) b->paint(a);
            float nk = clamp01((float)(2.2 - (T - accNoteAt)));
            if (nk > 0 && !accNote.empty())
                G.text(accNote, heroDelete.x + heroDelete.w + 14, Gfx::mid(heroDelete.y + heroDelete.h / 2, 12.5f), MEDIUM, 12.5f,
                       fade(ACCENT, a * nk));
            G.clip(x, y, w, h);  // the whole skin on the right, cut off by the card like a portrait
            float bh = h * 1.2f;
            body(name, x + w - 90 - bh / 2, y + 18, bh, a);
            G.unclip();
        });
        auto list = savedAccounts();
        if (!accRows.empty()) block(2, [&](float a) { groupHead(accRows[0]->y, "Все аккаунты", a); });
        for (size_t i = 0; i < list.size() && i < accRows.size(); i++) {
            Clickable& r = *accRows[i];
            string k = list[i], nm = accountName(k);
            bool viewed = k == key, inGame = k == cur, ms = licensedKey(k);
            block(3 + (int)std::min<size_t>(i, 8), [&](float a) {
                float hv = an.to(r.id + ".h", r.hovered ? 1 : 0, 16), vw = an.to(r.id + ".v", viewed ? 1 : 0, 14);
                G.glass(r.x, r.y, r.w, r.h, 12, mix(TINT, rgba(46, 46, 54, 165), hv), a);
                if (vw > .01f) G.rrLine(r.x + 1, r.y + 1, r.w - 2, r.h - 2, 11, fade(alpha(ACTION, vw), a), 2);  // the one in view
                ripple(r, r.x, r.y, r.w, r.h, 12, a, WHITE);
                G.rr(r.x + 12, r.y + 12, 40, 40, 9, fade(rgba(255, 255, 255, 30), a));
                head(nm, r.x + 12, r.y + 12, 40, a);
                G.accountFont = true;
                G.raw = true;
                G.text(G.fit(nm, SEMI, 14, r.w - 82), r.x + 62, r.y + 30, SEMI, 14, fade(WHITE, a));
                G.raw = false;
                G.accountFont = false;
                if (ms) msLogo(r.x + 62, r.y + 38, 10, a);
                G.text(inGame ? "Для игры" : ms ? "Microsoft" : "Локальный", r.x + (ms ? 77 : 62), r.y + 47, MEDIUM, 11.5f,
                       fade(inGame ? OK : MUTED, a));
                if (inGame) G.circle(r.x + r.w - 16, r.y + 16, 4, fade(OK, a));
            });
        }
    }
    // "Добавить аккаунт": two glass cards over the whole page - Microsoft, or a nickname
    void addCards() {
        float cw = (CW - 18) / 2, cy = accCardsY, ih = 230;
        block(1, [&](float a) {
            float x = CX;
            G.glass(x, cy, cw, ADD_CARD_H, 14, TINT, a);
            artTile(ART_LICENSED, x + 14, cy + 14, cw - 28, ih, 0, 0, 0, rgba(0, 0, 0, 0), a, true, 10);
            G.text("Лицензия", x + 20, cy + 14 + ih + 40, SEMI, 20, fade(WHITE, a));
            int ln = 0;
            for (auto& line : wrap(Lang::t("Доступ к лицензионным серверам через авторизацию Microsoft"), MEDIUM, 13, cw - 40, 2))
                G.text(line, x + 20, cy + 14 + ih + 64 + 19 * ln++, MEDIUM, 13, fade(MUTED, a));
            float hv = an.to("msAdd.h", msAdd.hovered ? 1 : 0, 16), bz = an.to("msAdd.b", microsoftBusy ? 1 : 0, 12),
                  r = msAdd.h / 2;
            G.rr(msAdd.x, msAdd.y, msAdd.w, msAdd.h, r, fade(alpha(WHITE, .10f + .06f * hv), a * (msAdd.enabled ? 1 : .5f)));
            G.rrLine(msAdd.x + .5f, msAdd.y + .5f, msAdd.w - 1, msAdd.h - 1, r - .5f, fade(alpha(WHITE, .06f), a));
            ripple(msAdd, msAdd.x, msAdd.y, msAdd.w, msAdd.h, r, a);
            string label = microsoftBusy ? "Отменить вход" : "Войти через Microsoft";
            float tw = G.width(Lang::t(label), SEMI, 13.5f), lx = msAdd.x + (msAdd.w - (tw + 28)) / 2, my = msAdd.y + msAdd.h / 2;
            if (bz > .01f) {
                float ang = (float)fmod(T * 5.2, 6.2831853);
                G.ring(lx + 8, my, 7, fade(alpha(TEXT, .25f), a * bz), 2);
                for (int i = 0; i < 10; i++) {
                    float a0 = ang + i * .17f, a1 = a0 + .17f;
                    G.line(lx + 8 + cosf(a0) * 7, my + sinf(a0) * 7, lx + 8 + cosf(a1) * 7, my + sinf(a1) * 7, fade(TEXT, a * bz), 2);
                }
                an.moving = true;
            } else
                msLogo(lx, my - 8, 16, a);
            G.text(label, lx + 28, Gfx::mid(my, 13.5f), SEMI, 13.5f, fade(TEXT, a));
        });
        block(2, [&](float a) {
            float x = CX + cw + 18;
            G.glass(x, cy, cw, ADD_CARD_H, 14, TINT, a);
            artTile(ART_LOCAL, x + 14, cy + 14, cw - 28, ih, 0, 0, 0, rgba(0, 0, 0, 0), a, true, 10);
            G.text("Локальный профиль", x + 20, cy + 14 + ih + 40, SEMI, 20, fade(WHITE, a));
            int ln = 0;
            for (auto& line : wrap(Lang::t("Доступ к нелицензионным серверам по никнейму"), MEDIUM, 13, cw - 40, 2))
                G.text(line, x + 20, cy + 14 + ih + 64 + 19 * ln++, MEDIUM, 13, fade(MUTED, a));
            accNick.paint(a);
            float ah = an.to("accAdd.h", accAdd.hovered ? 1 : 0, 16), ap = an.to("accAdd.p", accAdd.pressed ? 1 : 0, 30);
            G.rr(accAdd.x, accAdd.y, accAdd.w, accAdd.h, accAdd.h / 2, fade(mix(mix(ACTION, rgb(0x2A95FF), ah), rgb(0x0068D6), ap), a));
            ripple(accAdd, accAdd.x, accAdd.y, accAdd.w, accAdd.h, accAdd.h / 2, a);
            G.icon("plus", accAdd.x + accAdd.w / 2, accAdd.y + accAdd.h / 2, 16, fade(WHITE, a), 2.1f);
        });
        block(2, [&](float a) {
            float ek = an.to("acc.err", accErr.empty() ? 0 : 1, 12);
            G.text(G.fit(accErr.empty() ? "Ник: 3–16 символов — латинские буквы, цифры и _" : accErr, MEDIUM, 11.5f, cw - 40), CX + cw + 18 + 20,
                   accNick.y - 12, MEDIUM, 11.5f, fade(mix(MUTED, rgb(0xFF8A80), ek), a));
        });
    }
    // ---- page history: the arrows at the left of the toolbar
    vector<Page> backStack, fwdStack;
    bool historyMove = false;
    IconBtn navBack{"navBack", "back"}, navFwd{"navFwd", "fwd"}, greenBtn{"green", "plus"};
    // ---- the sidebar: the search field, the pages, the versions, Settings and the game account at the foot
    Field sbSearch{"sbSearch", "Поиск"};
    vector<std::unique_ptr<Clickable>> favRows;  // a row per version: picks it; the round button on the chosen one plays
    Clickable accountRow{"accountRow"};
    // ---- the toolbar: Mods | Resource packs, shared by the Catalogue and the Library
    Clickable kindTab[2] = {Clickable{"kindTab0"}, Clickable{"kindTab1"}};
    IconBtn addAccountBtn{"addAccount", "plus"};
    // ---- the home page
    Clickable homeAll[2] = {Clickable{"homeAll0"}, Clickable{"homeAll1"}};
    std::unique_ptr<Clickable> homeCards[8];
    static constexpr float HERO_Y = PAGE_TOP, HERO_HT = 226, CARD_HT = 148;
    float homeX() const { return CX; }
    float homeW() const { return CW; }
    string popularKey(bool packs) {
        return packs ? "packs|" + std::to_string(Config::packSources) + "|" + Config::mcVersion + "||" : "mods|" + std::to_string(Config::modSources) + "|" + Config::mcVersion + "||";
    }
    std::map<string, double> popularAskedAt;
    bool popularReady(bool packs) { return searchCache.count(popularKey(packs)) > 0; }
    vector<Modrinth::Hit> homeList(bool packs) {
        vector<Modrinth::Hit> out;
        auto it = searchCache.find(popularKey(packs));
        if (it == searchCache.end()) return out;
        for (auto& h : it->second)
            if (h.kind == (packs ? "resourcepack" : "mod") && providerAllowed(packs, h.source) && out.size() < 4) out.push_back(h);
        return out;
    }
    vector<Modrinth::Hit> homeMods() { return homeList(false); }
    vector<Modrinth::Hit> homePacks() { return homeList(true); }
    void loadPopular(bool packs) {
        string key = popularKey(packs);
        if (searchCache.count(key) || (popularAskedAt.count(key) && T - popularAskedAt[key] < 30)) return;
        popularAskedAt[key] = T;
        string version = Config::mcVersion;
        int mask = packs ? Config::packSources : Config::modSources;
        std::thread([this, packs, key, version, mask] {
            if (!packs) {
                ModPage pg = modPage("", version, "", mask, 0, 0);
                bool ok = pg.mrTotal >= 0 || pg.cfTotal >= 0;
                post([this, key, pg, ok] {
                    if (!ok || searchCache.count(key)) return;
                    if (searchCache.size() >= 32) searchCache.erase(searchCache.begin());
                    searchCache[key] = pg.hits;
                    searchMeta[key] = {pg.mrGot, std::max(0, pg.mrTotal)};
                    cfMeta[key] = {pg.cfGot, std::max(0, pg.cfTotal)};
                    dirty = true;
                });
                if (ok) loadIcons(pg.hits, [] { return true; });
                return;
            }
            vector<Modrinth::Hit> hits, mr, pmc;
            int total = 0;
            bool ok = false;
            if (mask & 2)
                try { mr = Modrinth::search("", version, packs ? "resourcepack" : "mod", "", 0, &total); ok = true; } catch (...) {}
            if (packs && (mask & 1))
                try { pmc = PacksMC::search("", ""); ok = true; } catch (...) {}
            for (size_t i = 0; i < std::max(mr.size(), pmc.size()); i++) {
                if (i < mr.size()) hits.push_back(mr[i]);
                if (i < pmc.size()) hits.push_back(pmc[i]);
            }
            int fetched = (int)mr.size();
            post([this, key, hits, ok, fetched, total] {
                if (!ok || searchCache.count(key)) return;
                if (searchCache.size() >= 32) searchCache.erase(searchCache.begin());
                searchCache[key] = hits;
                searchMeta[key] = {fetched, total};
                dirty = true;
            });
            if (ok) loadIcons(hits, [] { return true; });
        }).detach();
    }
    // The sidebar's search drives every list: the Catalogue searches online with it, the Library filters with it
    void searchTyped() {
        string q = sbSearch.value();
        searchField.set(q);
        packSearch.set(q);
        typedAt = packTypedAt = T;
        scroll[MODS] = scroll[PACKS] = 0;
        if (page != MODS && page != PACKS && !trim(q).empty()) {
            modsRemote = packsRemote = true;
            openPage(MODS);
        }
        wake(.8);
    }
    // ---- notifications: what setNote says comes as a macOS banner in the top right corner, then goes
    string toastText;
    Col toastColor = MUTED;
    double toastAt = -100, toastFor = 4.5;
    Clickable toastHit{"toast"};
    static constexpr float TOAST_W = 330, TOAST_H = 64;
    float toastK() {
        double age = T - toastAt;
        float in = clamp01((float)(age / .28)), out = clamp01((float)((toastAt + toastFor - T) / .35));
        if (toastHit.hovered && age > .28 && age < toastFor) toastAt = T - .3;
        return Anim::out(in) * out;
    }
    // ---- wallpapers: made on a worker thread, then turned into device pictures (see wallpaper.h)
    std::mutex wpLock;
    vector<uint32_t> wpPixels, wpFrost;
    int wpFrostW = 0, wpFrostH = 0, wpTheme = -1;
    std::atomic<int> wpBusy{0};
    vector<uint32_t> wpThumbs[Wallpaper::COUNT];
    static constexpr int THUMB_W = 336, THUMB_H = 200;  // rendered at 2x so the wallpaper picker stays crisp on hiDPI screens
    std::atomic<bool> thumbsReady{false};
    Clickable wpThumb[Wallpaper::COUNT] = {Clickable{"wp0"}, Clickable{"wp1"}, Clickable{"wp2"}, Clickable{"wp3"}};
    double wpChangedAt = -10;
    void makeWallpaper(int theme) {
        theme = std::clamp(theme, 0, Wallpaper::COUNT - 1);
        ++wpBusy;
        auto work = [this, theme] {
            vector<uint32_t> px((size_t)Wallpaper::W * Wallpaper::H);
            Wallpaper::render(theme, Wallpaper::W, Wallpaper::H, px.data());
            int fw, fh;
            auto frost = Wallpaper::frosted(px, Wallpaper::W, Wallpaper::H, fw, fh);
            {
                std::lock_guard<std::mutex> l(wpLock);
                wpPixels = std::move(px);
                wpFrost = std::move(frost);
                wpFrostW = fw, wpFrostH = fh, wpTheme = theme;
            }
            if (!thumbsReady) {
                for (int i = 0; i < Wallpaper::COUNT; i++) {
                    vector<uint32_t> th((size_t)THUMB_W * THUMB_H);
                    Wallpaper::render(i, THUMB_W, THUMB_H, th.data());
                    std::lock_guard<std::mutex> l(wpLock);
                    wpThumbs[i] = std::move(th);
                }
                thumbsReady = true;
            }
            --wpBusy;
            post([this] {
                dropBackground(true);
                wpChangedAt = T;
                dirty = true;
            });
        };
        if (DESIGN_PREVIEW) work();
        else std::thread(work).detach();
    }
    // The wallpaper, the dimmed content area and the sidebar's glass, composed once into one window-sized picture
    ID2D1Bitmap* background(bool login) {
        string key = login ? "bg:login" : "bg:cache";
        auto found = G.bitmaps.find(key);
        if (found != G.bitmaps.end()) return found->second;
        ID2D1Bitmap* wall = nullptr;
        {
            std::lock_guard<std::mutex> l(wpLock);
            if (wpPixels.empty()) return nullptr;
            auto props = D2D1::BitmapProperties(D2D1::PixelFormat(DXGI_FORMAT_B8G8R8A8_UNORM, D2D1_ALPHA_MODE_IGNORE));
            G.rt->CreateBitmap(D2D1::SizeU(Wallpaper::W, Wallpaper::H), wpPixels.data(), Wallpaper::W * 4, props, &wall);
            if (!G.bitmaps.count("bg:frost")) {
                ID2D1Bitmap* frost = nullptr;
                G.rt->CreateBitmap(D2D1::SizeU(wpFrostW, wpFrostH), wpFrost.data(), wpFrostW * 4, props, &frost);
                G.bitmaps["bg:frost"] = frost;
                G.setFrost(frost);
            }
        }
        if (!wall) return nullptr;
        ID2D1BitmapRenderTarget* brt = nullptr;
        ID2D1Bitmap* result = nullptr;
        if (SUCCEEDED(G.rt->CreateCompatibleRenderTarget(D2D1::SizeF(WIN_W * G.scale, WIN_H * G.scale), &brt))) {
            ID2D1RenderTarget* keep = G.rt;
            G.rt = brt;
            brt->BeginDraw();
            brt->SetTransform(D2D1::Matrix3x2F::Scale(G.scale, G.scale));
            brt->DrawBitmap(wall, D2D1::RectF(0, 0, WIN_W, WIN_H), 1, D2D1_BITMAP_INTERPOLATION_MODE_LINEAR);
            if (login)
                G.rect(0, 0, WIN_W, WIN_H, rgba(0, 0, 0, 40));
            else {
                G.rect(SIDE - 4, 0, WIN_W - SIDE + 4, WIN_H, rgba(0, 0, 0, 92));  // the content reads on a calmer picture
                G.rrGrad(SIDE, 0, WIN_W - SIDE, 70, 0, rgba(0, 0, 0, 70), rgba(0, 0, 0, 0), 0, 0, 0, 70);  // the toolbar
                G.glow(SB_X, SB_Y + 4, SB_W, SB_H, SB_R, alpha(rgb(0), .28f), 14);  // the sidebar floats a little
                G.glass(SB_X, SB_Y, SB_W, SB_H, SB_R, rgba(26, 26, 32, 120), 1);
            }
            brt->EndDraw();
            G.rt = keep;
            brt->GetBitmap(&result);
            brt->Release();
        }
        wall->Release();
        if (result) G.bitmaps[key] = result;
        return result;
    }
    void dropBackground(bool all) {
        for (const char* k : {"bg:cache", "bg:login", "bg:frost"}) {
            if (!all && !strcmp(k, "bg:frost")) continue;
            auto it = G.bitmaps.find(k);
            if (it == G.bitmaps.end()) continue;
            if (!strcmp(k, "bg:frost")) G.setFrost(nullptr);
            if (it->second) {
                if (!strcmp(k, "bg:cache")) {  // kept for a moment: the new wallpaper fades in over it
                    auto old = G.bitmaps.find("bg:old");
                    if (old != G.bitmaps.end() && old->second) old->second->Release();
                    G.bitmaps["bg:old"] = all ? it->second : nullptr;
                    if (!all) it->second->Release();
                } else
                    it->second->Release();
            }
            G.bitmaps.erase(k);
        }
    }
    ID2D1Bitmap* thumbBitmap(int i) {
        string key = "wpthumb:" + std::to_string(i);
        auto found = G.bitmaps.find(key);
        if (found != G.bitmaps.end()) return found->second;
        if (!thumbsReady) return nullptr;
        ID2D1Bitmap* b = nullptr;
        std::lock_guard<std::mutex> l(wpLock);
        if (wpThumbs[i].empty()) return nullptr;
        G.rt->CreateBitmap(D2D1::SizeU(THUMB_W, THUMB_H), wpThumbs[i].data(), THUMB_W * 4,
                           D2D1::BitmapProperties(D2D1::PixelFormat(DXGI_FORMAT_B8G8R8A8_UNORM, D2D1_ALPHA_MODE_IGNORE)), &b);
        return G.bitmaps[key] = b;
    }
    // ---- Settings: the Arcanic account and "О программе"
    Btn arcBtn{"arcBtn", "Войти"}, discordLink{"discordLink", "Discord"}, siteLink{"siteLink", "Сайт"}, logBtn{"logBtn", "Журнал"};
    // ---- when to draw
    bool dirty = true;
    double hotUntil = 1, paintedAt = 0, ambientAt = 0;
    int caretPhase = -1;
    int maxRam = 16384, ramSteps = 30;
    // ---- the logo animation
    double splashFor = 3.1, workAt = 0;  // the mark draws itself over the whole window while the launcher opens
    static constexpr double SPLASH_FADE = .65;

    Scene() {
        if (!DESIGN_PREVIEW) loadMicrosoft();
        msAdd.click = [this] { signInMicrosoft(); };
        maxRam = std::max(4096, std::min(32768, (Installer::totalRamMb() + 256) / 1024 * 1024));
        ramSteps = (maxRam - 1024) / 512;
        closeBtn.at(12, 12, 20, 20);
        minBtn.at(32, 12, 20, 20);
        closeBtn.danger = true;
        minBtn.click = [] { ShowWindow(hwnd, SW_MINIMIZE); };
        closeBtn.click = [this] { close(); };
        langBtn.at(WIN_W - 16 - 96, 14, 96, 30);
        langBtn.click = [this] {
            Lang::en = mouseX < langBtn.x + langBtn.w / 2;
            Config::lang = Lang::en ? "en" : "ru";
            Config::save();
        };
        grantLogin.max = 16;
        grantLogin.icon = "user";
        grantLogin.onEnter = [this] { setFocus(&grantDays); };  // Enter goes on to the days
        grantLogin.onChange = [this] { grantLogin.error = false; };
        grantDays.max = 4;
        grantDays.digits = true;
        grantDays.onEnter = [this] { grant(false); };
        grantDays.onChange = [this] { grantDays.error = false; };
        grantBtn.boxy = grantBtn.primary = true;
        grantBtn.fontSize = 14;
        grantBtn.click = [this] { grant(false); };
        lifetimeBtn.boxy = true;
        lifetimeBtn.fontSize = 14;
        lifetimeBtn.icon = "infinity";
        lifetimeBtn.click = [this] { grant(true); };
        premListBtn.boxy = premListBtn.ghost = true;
        premListBtn.fontSize = 14;
        premListBtn.icon = "list";
        premListBtn.click = [this] {
            premOpen = !premOpen;
            if (premOpen) loadPremium();
            wake(.6);
        };
        premDays.max = 4;
        premDays.digits = true;
        premDays.onEnter = [this] { premGiveDays(); };
        premDays.onChange = [this] { premDays.error = false; };
        for (int i = 0; i < 3; i++) {
            premAct[i].boxy = true;
            premAct[i].fontSize = 12.5f;
        }
        premAct[1].icon = "infinity";
        premAct[0].click = [this] { premGiveDays(); };
        premAct[1].click = [this] { premiumAction(premSel, 0, false); };
        premAct[2].click = [this] { premiumAction(premSel, 0, true); };
        chrome = {&minBtn, &closeBtn, &greenBtn, &langBtn, &navBack, &navFwd, &accountRow, &sbSearch, &toastHit};
        for (int i = 0; i < 4; i++) {
            favRows.push_back(std::make_unique<Clickable>("fav" + std::to_string(i)));
            favRows.back()->visible = favRows.back()->enabled = false;
            chrome.push_back(favRows.back().get());
        }
        greenBtn.click = [this] {
            eggAt = T;
            eggBite = false;
            wake(EGG_LEN + .3);
        };
        navBack.click = [this] {
            if (backStack.empty()) return;
            fwdStack.push_back(page);
            Page p = backStack.back();
            backStack.pop_back();
            historyMove = true;
            openPage(p);
            historyMove = false;
        };
        navFwd.click = [this] {
            if (fwdStack.empty()) return;
            backStack.push_back(page);
            Page p = fwdStack.back();
            fwdStack.pop_back();
            historyMove = true;
            openPage(p);
            historyMove = false;
        };
        accountRow.click = [this] { openAccounts(); };
        kindTab[0].tag = "Моды";
        kindTab[1].tag = "Ресурспаки";
        kindTab[0].click = [this] { modsRemote = packsRemote; openPage(MODS); };
        kindTab[1].click = [this] { packsRemote = modsRemote; openPage(PACKS); };
        addAccountBtn.plate = true;
        addAccountBtn.click = [this] {
            accAdding = true;
            accErr.clear();
            accNick.set("");
            setFocus(nullptr);
            wake(1);
        };
        homeAll[0].click = [this] {  // "See all": the whole popular list, not an old search
            modsRemote = true;
            searchField.set("");
            sbSearch.set("");
            modCat = 0;
            openPage(MODS);
        };
        homeAll[1].click = [this] {
            packsRemote = true;
            packSearch.set("");
            sbSearch.set("");
            packCat = 0;
            openPage(PACKS);
        };
        for (int i = 0; i < 8; i++) homeCards[i] = std::make_unique<Clickable>("homeCard" + std::to_string(i));
        sbSearch.max = 60;
        sbSearch.icon = "search";
        sbSearch.fontSize = 13;
        sbSearch.onChange = [this] { searchTyped(); };
        sbSearch.onEnter = [this] { searchTyped(); };
        toastHit.click = [this] { toastAt = T - toastFor; wake(.5); };
        arcBtn.fontSize = discordLink.fontSize = siteLink.fontSize = logBtn.fontSize = 13;
        arcBtn.boxy = discordLink.boxy = siteLink.boxy = logBtn.boxy = true;
        arcBtn.click = [this] {
            if (signedIn()) logout();
            else signInArcanic();
        };
        discordLink.icon = "discord";
        discordLink.click = [this] { ShellExecuteW(nullptr, L"open", W(discordUrl).c_str(), nullptr, nullptr, SW_SHOWNORMAL); };
        siteLink.click = [] { ShellExecuteW(nullptr, L"open", L"https://arcanic.su", nullptr, nullptr, SW_SHOWNORMAL); };
        logBtn.icon = "folder";
        logBtn.click = [] {
            wstring args = L"/select,\"" + Log::file().wstring() + L"\"";
            ShellExecuteW(nullptr, L"open", L"explorer.exe", args.c_str(), nullptr, SW_SHOWNORMAL);
        };
        for (int i = 0; i < Wallpaper::COUNT; i++)
            wpThumb[i].click = [this, i] {
                if (Config::wallpaper == i) return;
                Config::wallpaper = i;
                Config::save();
                makeWallpaper(i);
            };
        // the sidebar: Home, Catalogue (what can be installed), Library (what is installed); Settings at the foot
        const char* items[4][2] = {{"play", "Играть"}, {"store", "Каталог"}, {"library", "Библиотека"}, {"gear", "Настройки"}};
        for (int i = 0; i < 4; i++) {
            auto* c = new Clickable("nav" + std::to_string(i));
            c->icon = items[i][0];
            c->tag = items[i][1];
            c->click = [this, i] {
                if (i == 0) openPage(HOME);
                else if (i == 1 || i == 2) {
                    bool remote = i == 1;
                    Page target = page == PACKS ? PACKS : MODS;
                    if (page == target && (target == MODS ? modsRemote : packsRemote) != remote) pageAt = T;
                    modsRemote = packsRemote = remote;
                    scroll[MODS] = scroll[PACKS] = 0;
                    openPage(target);
                } else openPage(SETTINGS);
                wake(.5);
            };
            nav.push_back(c);
        }
        loginBtn.boxy = true;
        loginBtn.primary = true;
        loginBtn.fontSize = 17;
        loginBtn.click = [this] { submit(); };  // a wide bar with a small radius, like the reference
        playBtn.icon = "play";
        playBtn.fontSize = 16;
        playBtn.click = [this] { play(); };
        trustBtn.icon = "shield";
        trustBtn.click = [this] { play(true); };
        auto ghost = [](Btn& b, const char* icon, std::function<void()> click) {
            b.ghost = true;
            b.fontSize = 15.5f;
            b.icon = icon;
            b.click = std::move(click);
        };
        sourcesBtn.boxy = true; sourcesBtn.fontSize = 12.5f; sourcesBtn.icon = "cloud"; sourcesBtn.click = [this] { sourceMenu(); };
        for(int i=0;i<3;++i) sourceOptions[i].click=[this,i] {
            sourcesOpen=false; if(page==PACKS) selectPackSource(i+1); else if(page==MODS) selectModSource(i+1); wake(.3);
        };
        importPackBtn.boxy = true; importPackBtn.fontSize = 11.5f; importPackBtn.click = [this] { importPackZip(); };
        categoryToggle.click = [this] { showCategories = !showCategories; scroll[page] = 0; wake(.3); };
        searchField.max = 40;
        searchField.hint = "Название — ищем на Modrinth и CurseForge";
        searchField.onChange = [this] {
            scroll[MODS] = 0;
            typedAt = T;
            sbSearch.set(searchField.value());
            wake(.8);
        };
        packSearch.max = 40;
        packSearch.hint = "PacksMC";
        packSearch.onChange = [this] {
            scroll[PACKS] = 0;
            packTypedAt = T;
            sbSearch.set(packSearch.value());
            wake(.8);
        };
        int ci = 0;
        for (auto& c : Modrinth::MOD_CATEGORIES) {
            int i = ci++;
            auto b = std::make_unique<Clickable>("mcat" + std::to_string(i));
            b->tag = c.name;
            b->click = [this, i] {
                modCat = i;
                wake(.8);
            };
            modCats.push_back(std::move(b));
        }
        ci = 0;
        for (auto& c : PacksMC::CATEGORIES) {
            int i = ci++;
            auto b = std::make_unique<Clickable>("pcat" + std::to_string(i));
            b->tag = c.name;
            b->click = [this, i] {
                packCat = i;
                wake(.8);
            };
            packCats.push_back(std::move(b));
        }
        pvClose.click = [this] { closePreview(); };
        pvPrev.click = [this] { stepPreview(-1); };
        pvNext.click = [this] { stepPreview(1); };
        pvInstall.fontSize = 14;
        playBtn.jelly = playBtn.primary = true;
        accNick.max = 16;
        accNick.hint = "Steve_123";
        accNick.icon = "user";
        accNick.onEnter = [this] { addAccount(); };
        accNick.onChange = [this] {
            accErr.clear();
            accNick.error = false;
        };
        accAdd.click = [this] { addAccount(); };
        accBack.ghost = true;
        accBack.boxy = true;
        accBack.fontSize = 13;
        accBack.click = [this] {
            accAdding = false;
            accErr.clear();
            setFocus(nullptr);
            wake(1);
        };
        heroSelect.ghost = true;
        heroSelect.boxy = true;
        heroSelect.fontSize = 13.5f;
        heroSelect.click = [this] {
            string k = viewKey();
            if (!k.empty()) pickAccount(k);
        };
        heroCopy.click = [this] {
            string k = viewKey();
            if (!k.empty()) {
                copyText(accountName(k));
                accSay("Ник скопирован");
            }
        };
        heroRefresh.click = [this] {
            string k = viewKey();
            if (!k.empty()) refreshAccount(k);
        };
        heroDelete.danger = true;
        heroDelete.click = [this] {
            string k = viewKey();
            if (!k.empty()) removeAccount(k);
        };
        const char* names[3] = {"Все", "Включённые", "Выключенные"};
        for (int i = 0; i < 3; i++) {
            filters[i].tag = names[i];
            filters[i].click = [this, i] {
                modFilter = i;
                scroll[MODS] = 0;
            };
        }
        memory.value = ramToV(Config::ramMb > 0 ? Config::ramMb : Installer::defaultRam());
        memory.steps = ramSteps;
        memory.label = [this](float v) { return ramText(vToRam(v)); };
        memory.onChange = [this](float v) {
            Config::ramMb = vToRam(v);
            Config::save();
        };
        ghost(autoRamBtn, "", [this] {
            Config::ramMb = 0;
            Config::save();
            memory.value = memory.snap(ramToV(Installer::defaultRam()));
        });
        menuScale.steps = 0;
        menuScale.screenAnchored = true;
        menuScale.value = clamp01((Config::uiScale - .7f) / .6f);
        menuScale.label = [](float v) { return fmt("%.0f%%", (70 + 60 * v)); };
        menuScale.onChange = [](float v) { previewUiScale(.7f + .6f * v); };
        menuScale.onCommit = [](float) { commitUiScale(); };
        ghost(resetScale, "", [this] { menuScale.value = .5f; previewUiScale(1.f); commitUiScale(); });
        errCopy.primary = errCopy.boxy = true;
        errFolder.boxy = errFolder.ghost = errClose.boxy = errClose.ghost = true;
        errCopy.fontSize = errFolder.fontSize = errClose.fontSize = 12.5f;
        errCopy.click = [this] {
            copyText(S(errPath));
            errCopiedAt = T;
            wake(2);
        };
        errFolder.click = [this] {  // Explorer opens with the file selected
            wstring args = L"/select,\"" + errPath.wstring() + L"\"";
            if (!isFile(errPath)) args = L"\"" + errPath.parent_path().wstring() + L"\"";
            ShellExecuteW(nullptr, L"open", L"explorer.exe", args.c_str(), nullptr, SW_SHOWNORMAL);
        };
        errClose.click = [this] {
            errOpen = false;
            wake(1);
        };
        langSetting.click = [this] {  // the half that was pressed
            Lang::en = langSetting.rippleX < langSetting.x + langSetting.w / 2;
            Config::lang = Lang::en ? "en" : "ru";
            Config::save();
            wake(.6);
        };
        startupToggle.on = Config::startupAnimation;
        startupToggle.onChange = [](bool on) { Config::startupAnimation = on; Config::save(); };
        soundsToggle.on = Config::launcherSounds;
        soundsToggle.onChange = [](bool on) {
            Config::launcherSounds = on; Config::save();
            if (!on) UiAudio::stop(); else UiAudio::enable();
        };
        cleanToggle.on = Config::cleanAfterPlay;
        cleanToggle.onChange = [](bool on) { Config::cleanAfterPlay = on; Config::save(); };
        jvmField.max = 200;
        jvmField.fontSize = 13;
        jvmField.set(Config::jvmArgs);
        jvmField.onChange = [this] {
            Config::jvmArgs = trim(jvmField.value());
            saveAt = T + .8;
        };
        jvmField.onEnter = [this] {
            Config::jvmArgs = trim(jvmField.value());
            Config::save();
            saveAt = 0;
            setFocus(nullptr);
        };
        ghost(gameDirBtn, "", [] { openInExplorer(Game::dir()); });
        ghost(modsDirBtn, "", [] { openInExplorer(Game::dir() / L"mods"); });
        ghost(moveBtn, "", [this] { chooseLocation(); });
        ghost(defaultBtn, "", [this] {
            Config::gameDir.clear();
            Config::save();
            filesNote = "Используется папка по умолчанию";
        });
        ghost(updateBtn, "", [this] { checkUpdates(); });
        ghost(openModsBtn, "folder", [] { openInExplorer(Game::dir() / L"mods"); });
        updateAllBtn.click = [this] { updateAllMods(); };
        ghost(openPacksBtn, "folder", [] { openInExplorer(Game::dir() / L"resourcepacks"); });
        splashFor = Config::startupAnimation && !justUpdated ? 4.2 : 0;
        discordBtn.ghost = true;
        discordBtn.boxy = true;
        discordBtn.fontSize = 14;
        discordBtn.icon = "discord";
        discordBtn.click = [this] {
            if (!discordUrl.empty())
                ShellExecuteW(nullptr, L"open", W(discordUrl).c_str(), nullptr, nullptr, SW_SHOWNORMAL);
        };
        makeWallpaper(Config::wallpaper);
        if (DESIGN_PREVIEW) return;
        std::thread([this] {
            Net::Response r = Net::request("GET", Config::base + "/api/v1/launcher", {}, "", nullptr, 8);
            Json info = r.status == 200 ? Json::parse(r.body) : Json();
            string url = "https://discord.gg/djyhbYYDyf", app = info.str("discordApp");
            vector<std::pair<string, string>> vers;
            for (auto& v : info["versions"].a) {
                if (v.type == Json::Str && !trim(v.s).empty())
                    vers.push_back({trim(v.s), ""});
                else if (v.type == Json::Obj && !v.str("id").empty())
                    vers.push_back({v.str("id"), v.str("note")});
            }
            if (!vers.empty())
                post([this, vers] {
                    serverVersions = vers;
                    wake(.6);
                });
            if (starts(url, "https://"))
                post([this, url] {
                    discordUrl = url;
                    Discord::invite = url;
                    wake(.8);
                });
            if (!app.empty()) post([app] { Discord::appId = app; });
        }).detach();
    }

    bool loggedIn() const { return cur == READY; }
    float ramToV(int mb) const { return clamp01((mb - 1024.f) / (maxRam - 1024)); }
    int vToRam(float v) const { return 1024 + (int)lroundf(clamp01(v) * ramSteps) * 512; }
    static string ramText(int mb) {
        return (mb % 1024 == 0 ? std::to_string(mb / 1024) : fmt("%.1f", mb / 1024.f)) + " ГБ";
    }
    string offlineNick() const { return Installer::offlineName(Config::nick.empty() ? userName : Config::nick); }
    string nick() const { return accountName(selectedAccount()); }

    // ============================================================================================ state
    void setFocus(Widget* w) {
        if (focusW) focusW->focused = false;
        focusW = w;
        if (w) {
            w->focused = true;
            if (auto* f = dynamic_cast<Field*>(w)) f->focusAt = T;
        }
    }
    void go(Stage s) {
        if (s == cur) return;
        sourcesOpen = false;
        wake(1);
        cur = s;
        pressedW = nullptr;
        if (s == LOGIN) {
            page = HOME;
            setFocus(nullptr);
        }
        pageAt = T;
    }
    void openPage(Page p) {
        if (!loggedIn() || page == p) return;
        if (!historyMove) {
            backStack.push_back(page);
            if (backStack.size() > 30) backStack.erase(backStack.begin());
            fwdStack.clear();
        }
        sourcesOpen = false;
        wake(1);
        previewOpen = false;
        pageDirection = (int)p > (int)page ? 1.f : -1.f;
        page = p;
        UiAudio::play(131);
        showCategories = false;
        pageAt = T;
        scannedAt = -10;
        setFocus(nullptr);
    }
    void setCaption(const string& c) {
        prevCaption = caption;
        caption = c;
        captionAt = T;
        wake(.6);
    }
    void setNote(const string& s, Col c) {
        note = s;
        noteColor = c;
        wake(.2);
        // anything worth saying comes as a notification; quiet statuses (DIM) only change the line under the version
        if (c.r != DIM.r || c.g != DIM.g || c.b != DIM.b) {
            toastText = s;
            toastColor = c;
            toastFor = c.r == ERR.r && c.g == ERR.g ? 7 : 4.5;
            toastAt = T;
        }
    }
    void showLogin(const string& error) {
        preparing = false;
        playBusy = false;
        go(LOGIN);
        loginBtn.busy = false;
        if (!error.empty()) {
            errMsg = error;
            lastErr = error;
            showError("Лаунчер не смог подключиться", error, Log::file());
        }
        wake(.8);
    }
    void showWorking() {
        workAt = T;
        preparing = false;
        step = 0;
        stepAt = T;
        dlDone = dlTotal = 0;
        dlSpeed = 0;
        speedAt = 0;
        shown = 0;
        an.set("prog", 0);
        setCaption("Проверяем доступ…");
        go(WORKING);
    }
    void setStep(int s) {
        if (s == step) return;
        step = s;
        stepAt = T;
        setCaption(s == 1 ? "Загружаем клиент…" : s == 2 ? "Проверяем файл…" : "Готово");
    }
    void setDownload(int64_t done, int64_t total) {
        dlDone = done;
        dlTotal = total;
        if (speedAt == 0) {
            speedAt = T;
            speedFrom = done;
        } else if (T - speedAt >= .35) {
            double inst = (done - speedFrom) / (T - speedAt);
            dlSpeed = dlSpeed == 0 ? inst : dlSpeed * .6 + inst * .4;
            speedAt = T;
            speedFrom = done;
        }
    }
    void showReady() {
        userName = session.user.empty() ? "Player" : session.user;
        version = session.version;
        if (starts(version, "v")) version = version.substr(1);
        plan = session.plan;
        role = session.role;
        if (Config::session != session.token || Config::lastUser != session.user) {
            Config::session = session.token;
            Config::lastUser = session.user;
            Config::save();
        }  // signed in until "Выйти"
        setStep(3);
        go(READY);
        page = HOME;
        playBtn.busy = false;
        playBtn.text = "Играть";
        if (!Config::onboarded) {
            Config::onboarded = true;
            Config::save();
        }  // from now on the launcher signs in by itself
        setNote(version.empty() ? "Готово к запуску" : "Клиент Arcanic v" + version, DIM);
        wake(2);
        takeGameSession();
    }
    // "Начать": in, on the player's own click (the launcher never goes in by itself)
    void submit() {
        if (loginBtn.busy || cur == WORKING || splash()) return;
        errMsg.clear();
        setFocus(nullptr);
        resume();
    }
    float startupClock() const {
        if (!DESIGN_PREVIEW && Config::launcherSounds) {
            float audio = UiAudio::startupSeconds();
            if (audio >= 0) return audio;
        }
        return (float)T;
    }
    bool splash() const { return Config::startupAnimation && startupClock() < splashFor; }
    // Out of the Arcanic account: the launcher stays open, the game from now on runs without the sync
    void logout() {
        if (gameProc || playBusy || microsoftBusy || arcBusy || !signedIn()) return;
        string token = session.token;
        std::thread([token] { Api::logout(token); }).detach();  // the token stops working on the server too
        session.token.clear();
        session.user.clear();
        session.plan.clear();
        session.role.clear();
        Config::session.clear();
        Config::lastUser.clear();
        Config::save();
        userName = "Player";
        plan.clear();
        role.clear();
        setNote("Ты вышел из аккаунта Arcanic", MUTED);
        wake(.8);
    }
    // Signs in to Arcanic on the site: the browser opens, the launcher waits for it to come back with a code
    void signInArcanic() {
        if (arcBusy) {
            arcCancel = true;
            return;
        }
        if (playBusy || gameProc || signedIn()) return;
        arcBusy = true;
        arcCancel = false;
        setNote("Открываем сайт для входа…", MUTED);
        std::thread([this] {
            try {
                Session s = Api::siteLogin(
                    arcCancel, [this](const string& text) { post([this, text] { setNote(text, MUTED); }); });
                post([this, s] {
                    arcBusy = false;
                    session.token = s.token;
                    session.user = s.user;
                    session.plan = s.plan;
                    session.role = s.role;
                    if (!s.version.empty()) {
                        session.version = s.version;
                        version = s.version;
                        if (starts(version, "v")) version = version.substr(1);
                    }
                    userName = s.user;
                    plan = s.plan;
                    role = s.role;
                    Config::session = s.token;
                    Config::lastUser = s.user;
                    Config::save();
                    setNote("Вход в Arcanic выполнен", OK);
                    if (!Config::softwareNow) {
                        ShowWindow(hwnd, SW_RESTORE);
                        SetForegroundWindow(hwnd);
                    }
                    wake(1);
                });
            } catch (const std::exception& e) {
                string text = e.what();
                bool cancelled = arcCancel;
                post([this, text, cancelled] {
                    arcBusy = false;
                    setNote(text, cancelled ? MUTED : ERR);
                    wake(.6);
                });
            }
        }).detach();
        wake(1);
    }
    void takeGameSession() {
        fs::path file = Config::stateDir() / L"game-session.txt";
        string text;
        if (arcBusy || !readFile(file, text)) return;
        removeFile(file);
        auto lines = split(text, "\r\n");
        for (auto& l : lines) l = trim(l);
        if (lines.size() < 2 || lines[1].empty()) return;
        string token = Config::unseal(lines[1]);
        if (token.empty()) return;
        if (lines[0] == "out") {
            if (session.token != token) return;
            session.token.clear();
            session.user.clear();
            session.plan.clear();
            session.role.clear();
            Config::session.clear();
            Config::lastUser.clear();
            Config::save();
            userName = "Player";
            plan.clear();
            role.clear();
            setNote("Ты вышел из аккаунта Arcanic", MUTED);
            wake(.8);
            return;
        }
        if (lines[0] != "in" || token == session.token) return;
        std::thread([this, token] {
            try {
                Session s = Api::me(token);
                post([this, s] {
                    session.token = s.token;
                    session.user = s.user;
                    session.plan = s.plan;
                    session.role = s.role;
                    userName = s.user;
                    plan = s.plan;
                    role = s.role;
                    Config::session = s.token;
                    Config::lastUser = s.user;
                    Config::save();
                    setNote("Вход в Arcanic выполнен", OK);
                    wake(1);
                });
            } catch (std::exception&) {}  // not good any more, or no connection: nothing changes
        }).detach();
    }
    void close() {
        microsoftCancel = true;
        arcCancel = true;
        if (playBusy) {
            exitAfterGame = true;
            ShowWindow(hwnd, SW_HIDE);
            return;
        }
        if (gameProc) {
            exitAfterGame = true;
            ShowWindow(hwnd, SW_HIDE);
            return;
        }
        Log::info("closed by the player");
        PostQuitMessage(0);
    }

    // Into the launcher
    void resume() {
        showWorking();
        string token = Config::session;
        std::thread([this, token] {
            bool reached = Api::pickEntry();
            if (!reached) Log::warn("the server does not answer: starting from the kept client");
            if (reached && !justUpdated &&
                Updater::fetch([this] { post([this] { setCaption("Обновляем лаунчер…"); }); },
                               [this](int64_t d, int64_t t) { post([this, d, t] { setDownload(d, t); }); })) {
                post([this] { setCaption("Запускаем новую версию…"); });
                Sleep(500);
                post([this] { restartUpdated(); });
                return;
            }
            Session s;
            bool offline = false, expired = false;
            ULONGLONG t0 = GetTickCount64();
            if (!token.empty()) {
                try {
                    if (!reached) throw std::runtime_error("no connection");
                    s = Api::me(token);
                } catch (Api::Refused&) {
                    expired = true;
                } catch (std::exception&) {
                    offline = true;
                    s.token = token;
                    s.user = Config::lastUser;
                }
            }
            while (GetTickCount64() - t0 < 650) Sleep(20);  // let the first step be seen
            try {
                enter(s, offline || !reached);
                if (expired) post([this] { setNote("Вход в Arcanic устарел — войди заново", WARN); });
                else if (justUpdated) post([this] { setNote("Лаунчер обновлён", OK); });
            } catch (std::exception& e) {
                string m = e.what();
                post([this, m] {
                    session = Session();
                    showLogin(m);
                });
            }
        }).detach();
    } 
    // The new version is in this one's place: start it and go
    void restartUpdated() {
        if (singleInstance) {
            ReleaseMutex(singleInstance);
            CloseHandle(singleInstance);
            singleInstance = nullptr;
        }
        wstring cmd = GetCommandLineW();
        cmd += L" --updated";
        STARTUPINFOW si = {sizeof si};
        PROCESS_INFORMATION pi = {};
        wstring exe = Updater::self().wstring();
        if (CreateProcessW(exe.c_str(), cmd.data(), nullptr, nullptr, FALSE, 0, nullptr, nullptr, &si, &pi)) {
            CloseHandle(pi.hThread);
            CloseHandle(pi.hProcess);
        }
        PostQuitMessage(0);
    }
    void enter(Session s, bool offline) {
        post([this] { setStep(1); });
        string jar;
        if (offline) {
            jar = Api::cachedClient();
            if (jar.empty()) throw std::runtime_error("Нет связи с сервером");
        } else {
            try {
                jar =
                    Api::fetchClientCached([this](int64_t d, int64_t t) { post([this, d, t] { setDownload(d, t); }); });
            } catch (std::exception&) {
                jar = Api::cachedClient();
                if (jar.empty()) throw;
            }
        }
        if (s.version.empty() && !offline) s.version = Api::clientVersion();
        post([this] { setStep(2); });
        Sleep(500);
        post([this, s, jar, offline] {
            session = s;
            clientJar = jar;
            showReady();
            if (offline) setNote("Сервер недоступен — играем без синхронизации", WARN);
        });
    }
    // ---- Play: make sure the game is on disk, put the client into mods/, start it, wait, clean up
    void play(bool trust = false) {
        if (clientJar.empty() || gameProc || playBusy || microsoftBusy) return;
        Installer::trust = trust;
        if (trust) Log::info("play: trust mode (visuals only)");
        preparing = true;
        playBusy = true;
        prepFrac = 0;
        prepDetail.clear();
        an.set("prep", 0);
        playStage = 0;
        setCaption("Проверяем обновления клиента…");
        playBtn.busy = true;
        playBtn.text = "Запуск…";
        string name = nick(), licensed = Config::microsoftAccount;
        if (!licensed.empty() && std::none_of(microsoftAccounts.begin(), microsoftAccounts.end(), [&](const auto& a) { return a.uuid == licensed; })) {
            Log::warn("the chosen Microsoft account is gone; playing offline as " + name);
            Config::microsoftAccount.clear();
            Config::save();
            licensed.clear();
        }
        Session s0 = session;
        string jar0 = clientJar;
        auto report = [this](float f, const string& c, const string& d) {
            static string lastCaption;
            if (c != lastCaption) Log::info("play: " + (lastCaption = c));
            post([this, f, c, d] {
                prepFrac = f;
                prepDetail = d;
                int st = stageOf(c);
                if (st > playStage) playStage = st;
                if (c != caption) setCaption(c);
                wake(.2);
            });
        };
        std::thread([this, name, licensed, s0, jar0, report] {
            fs::path deployed;
            string fail;
            DWORD code = 0;
            ULONGLONG started = 0;
            string jar = jar0, token = s0.token;
            Doctor::Report fixes;     // everything the doctor changed on the way
            Doctor::Diagnosis diag;   // what the last failed start was about
            bool retried = false;
            try {
                // ---- 1. the client: "has it changed?" - the server answers from its ETag; a new one is downloaded and checked
                try {
                    Session s = s0;
                    bool changed = false;
                    auto onBytes = [this](int64_t d, int64_t t) {
                        if (t > 0 && d < t)
                            post([this, d, t] {
                                prepDetail = Installer::mb(d) + " / " + Installer::mb(t);
                                wake(.2);
                            });
                    };
                    string fresh = Api::fetchClientCached(onBytes, &changed);
                    if (changed) {
                        jar = fresh;
                        if (s0.token.empty())
                            s.version = Api::clientVersion();
                        else
                            try {
                                s = Api::me(s0.token);
                            } catch (std::exception&) {}
                    }
                    post([this, s, jar, changed] {
                        session = s;
                        clientJar = jar;
                        if (changed) {
                            version = s.version;
                            if (starts(version, "v")) version = version.substr(1);
                        }
                        prepDetail = changed ? "Клиент обновлён" + (version.empty() ? string() : " до v" + version)
                                             : "Последняя версия";
                        wake(.3);
                    });
                } catch (std::exception& e) {
                    if (string(e.what()) == "Клиент скачался с ошибкой") throw;
                    post([this] {
                        prepDetail = "Сервер не ответил — запускаем текущую версию";
                        wake(.3);
                    });  // offline: play what we have
                }
                for (int attempt = 0; attempt < 2; attempt++) {
                    Installer::Plan plan = Installer::prepare(Game::dir(), name, token, report);
                    deployed = Installer::deploy(jar);
                    // ---- the mods, read the way Fabric will read them: what would stop the game is fixed now
                    report(1, "Проверяем моды…", "");
                    Doctor::Report rep = Doctor::scan(Game::modsDir(), Config::mcVersion);
                    if (!Doctor::loaderFits(plan.loader, rep.loaderNeeds)) {
                        auto needs = rep.loaderNeeds;
                        plan = Installer::prepare(Game::dir(), name, token, report,
                                                  [needs](const string& v) { return Doctor::loaderFits(v, needs); });
                        Log::info("play: Fabric Loader " + plan.loader + " for the installed mods");
                    }
                    fixes.fixed.insert(fixes.fixed.end(), rep.fixed.begin(), rep.fixed.end());
                    fixes.problems.insert(fixes.problems.end(), rep.problems.begin(), rep.problems.end());
                    if (!licensed.empty()) {
                        report(1, Microsoft::message("Обновляем вход Microsoft…", "Refreshing Microsoft sign-in…"), "");
                        auto a = Microsoft::refresh(licensed);
                        Installer::license(plan, a);
                        post([this] { loadMicrosoft(); });
                    }
                    report(1, "Запускаем игру…", "");
                    HANDLE p = Installer::start(plan);
                    if (!p) throw std::runtime_error("Не удалось: javaw.exe");
                    started = GetTickCount64();
                    time_t launchedAt = time(nullptr);
                    vector<string> fixedNow = rep.fixed;
                    post([this, p, fixedNow] {
                        gameProc = p;
                        preparing = false;
                        playBusy = true;
                        playBtn.busy = false;
                        playBtn.enabled = false;
                        playBtn.running = true;
                        playBtn.text = "Игра запущена";
                        UiAudio::play(UiAudio::LAUNCH);
                        if (!fixedNow.empty()) setNote(fixSummary(fixedNow), OK);
                        else setNote("Закрой игру, чтобы вернуться сюда", MUTED);
                        wake(.6);
                    });
                    reportPresence(p, !licensed.empty() ? licensed : Installer::offlineUuid(name));
                    Discord::whilePlaying(p, Config::mcVersion);
                    GetExitCodeProcess(p, &code);
                    CloseHandle(p);
                    bool quickExit = GetTickCount64() - started < 45000;
                    if (code == 0 || !quickExit) break;
                    // ---- the game did not get going: read why, fix it, and start once more by ourselves
                    diag = Doctor::diagnose(Game::dir(), launchedAt - 5);
                    if (attempt == 1) break;
                    Doctor::Report after;
                    if (!Doctor::apply(diag, Game::modsDir(), Config::mcVersion, after)) break;
                    fixes.fixed.insert(fixes.fixed.end(), after.fixed.begin(), after.fixed.end());
                    retried = true;
                    Log::info(fmt("play: the game closed with code %lu; fixed %d things, starting again", code, (int)after.fixed.size()));
                    if (Config::cleanAfterPlay && !deployed.empty()) removeFile(deployed);
                    vector<string> said = after.fixed;
                    post([this, said] {
                        gameProc = nullptr;
                        preparing = true;
                        playBtn.running = false;
                        playBtn.busy = true;
                        playBtn.text = "Запуск…";
                        setCaption("Исправляем ошибку и запускаем снова…");
                        setNote(fixSummary(said), WARN);
                        wake(.4);
                    });
                    code = 0;
                }
            } catch (std::exception& e) {
                fail = e.what();
            } catch (...) {
                fail = "Неизвестная ошибка";
            }
            if (Config::cleanAfterPlay && !deployed.empty()) removeFile(deployed);
            bool quick = started && GetTickCount64() - started < 45000;
            if (!fail.empty()) Log::error("play failed: " + fail);
            else if (started) Log::info(fmt("game closed, exit code %lu after %llu s", code, (GetTickCount64() - started) / 1000));
            string advice = Doctor::advice(diag);
            vector<string> fixedAll = fixes.fixed;
            post([this, fail, code, quick, advice, fixedAll, retried] {
                gameProc = nullptr;
                preparing = false;
                playBusy = false;
                playBtn.busy = false;
                playBtn.enabled = true;
                playBtn.running = false;
                playBtn.text = "Играть";
                if (!fail.empty() || (code != 0 && quick)) UiAudio::play(UiAudio::FAIL);
                if (!fail.empty()) {
                    setNote(fail, ERR);
                    showError("Не удалось запустить игру", fail, Log::file());
                } else if (code != 0 && quick) {
                    setNote("Игра закрылась с ошибкой", ERR);
                    fs::path gameLog = Game::dir() / L"logs" / L"latest.log";
                    if (!isFile(gameLog)) gameLog = Game::dir() / L"logs" / L"launcher.log";
                    string text = Lang::en ? "Exit code " + std::to_string((int)code) + "." : "Код выхода " + std::to_string((int)code) + ".";
                    if (!advice.empty()) text += "\n\n" + advice;
                    if (retried)
                        text += Lang::en ? "\n\nThe launcher fixed this and tried again, but the game closed again:\n• " + join(fixedAll, "\n• ")
                                         : "\n\nЛаунчер уже исправил это и попробовал снова, но игра опять закрылась:\n• " + join(fixedAll, "\n• ");
                    else if (advice.empty())
                        text += Lang::en ? " Details are in the game log." : " Подробности — в логе игры.";
                    showError("Игра закрылась с ошибкой", text, gameLog);
                } else if (!fixedAll.empty())
                    setNote(fixSummary(fixedAll), OK);
                else
                    setNote("Игра закрыта", DIM);
                takeGameSession();
                wake(.6);
                if (exitAfterGame) PostQuitMessage(0);
            });
        }).detach();
    }
    // One line for the note: the first fix, and how many more there were (all of them are in the launcher log)
    static string fixSummary(const vector<string>& fixed) {
        if (fixed.empty()) return "";
        string s = fixed.front();
        if (fixed.size() > 1) s += Lang::en ? fmt("  (+%d more)", (int)fixed.size() - 1) : fmt("  (+ещё %d)", (int)fixed.size() - 1);
        return s;
    }
    static string join(const vector<string>& list, const string& sep) {
        string out;
        for (auto& s : list) out += (out.empty() ? "" : sep) + s;
        return out;
    }
    void chooseLocation() {
        IFileOpenDialog* dlg = nullptr;
        if (FAILED(CoCreateInstance(CLSID_FileOpenDialog, nullptr, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&dlg)))) return;
        DWORD opt = 0;
        dlg->GetOptions(&opt);
        dlg->SetOptions(opt | FOS_PICKFOLDERS | FOS_FORCEFILESYSTEM);
        dlg->SetTitle(W(Lang::t("Новая папка для игры")).c_str());
        if (SUCCEEDED(dlg->Show(hwnd))) {
            IShellItem* item = nullptr;
            PWSTR path = nullptr;
            if (SUCCEEDED(dlg->GetResult(&item)) && SUCCEEDED(item->GetDisplayName(SIGDN_FILESYSPATH, &path))) {
                fs::path target = path;
                CoTaskMemFree(path);
                filesNote = "Переносим файлы…";
                std::thread([this, target] {
                    string err = Game::moveTo(target);
                    post([this, err] {
                        filesNote = err.empty() ? "Игра теперь в этой папке" : err;
                        scannedAt = -10;
                    });
                }).detach();
            }
            release(item);
        }
        dlg->Release();
    }
    int online = -1;
    double onlineAt = -100;
    bool continuedAfterUpdate = false;
    static void reportPresence(HANDLE game, string uuid) {
        uuid.erase(std::remove(uuid.begin(), uuid.end(), '-'), uuid.end());
        HANDLE own = nullptr;
        if (uuid.size() != 32 || !DuplicateHandle(GetCurrentProcess(), game, GetCurrentProcess(), &own, SYNCHRONIZE, FALSE, 0)) return;
        std::thread([own, uuid] {
            string body = "{\"uuid\":\"" + lower(uuid) + "\"}";
            do Net::request("POST", Config::base + "/api/v1/presence", {"Content-Type: application/json"}, body, nullptr, 8);
            while (WaitForSingleObject(own, 60000) == WAIT_TIMEOUT);
            CloseHandle(own);
        }).detach();
    }
    std::atomic<bool> onlineBusy{false};
    void pollOnline() {
        if (onlineBusy || T - onlineAt < 30) return;
        onlineAt = T;
        onlineBusy = true;
        std::thread([this] {
            Net::Response r = Net::request("GET", Config::base + "/api/v1/online", {}, "", nullptr, 8);
            int n = r.status == 200 ? (int)Json::parse(r.body).num("online", -1) : -1;
            post([this, n] {
                onlineBusy = false;
                if (n >= 0 && n != online) {
                    online = n;
                    dirty = true;
                }
            });
        }).detach();
    }
    bool updateReady = false, updating = false;
    // "Проверить обновления"; once a newer launcher is found the same button downloads it and restarts into it
    void checkUpdates() {
        if (updating) return;
        if (updateReady) {
            installUpdate();
            return;
        }
        updateNote = "Проверяем…";
        updateColor = MUTED;
        if (DESIGN_PREVIEW) return;
        updateBtn.busy = true;
        std::thread([this] {
            Net::Response r = Net::request("GET", Config::base + "/api/v1/launcher", {}, "", nullptr, 10);
            string latest = r.status == 200 ? Json::parse(r.body).str("version") : "";
            bool ok = r.status == 200;
            post([this, ok, latest] {
                updateBtn.busy = false;
                if (!ok) {
                    updateNote = "Сервер обновлений недоступен";
                    updateColor = ERR;
                } else if (latest.empty() || !Updater::newer(latest, Config::VERSION)) {
                    updateNote = "Установлена последняя версия";
                    updateColor = OK;
                } else {
                    updateNote = "Доступна версия " + latest;
                    updateColor = ACCENT2;
                    updateReady = true;
                    updateBtn.text = "Обновить лаунчер";
                }
                wake(.6);
            });
        }).detach();
    }
    void installUpdate() {
        if (playBusy || gameProc) {
            updateNote = "Закрой игру, чтобы вернуться сюда";
            updateColor = WARN;
            return;
        }
        updating = true;
        updateBtn.busy = true;
        updateNote = "Скачиваем обновление…";
        updateColor = MUTED;
        std::thread([this] {
            bool ok = Updater::fetch(nullptr, [this](int64_t d, int64_t t) {
                post([this, d, t] {
                    updateNote = Installer::mb(d) + " / " + Installer::mb(t);
                    wake(.3);
                });
            });
            post([this, ok] {
                if (ok) {
                    updateNote = "Запускаем новую версию…";
                    restartUpdated();
                    return;
                }
                updating = false;
                updateBtn.busy = false;
                updateNote = "Не удалось обновить лаунчер";
                updateColor = ERR;
                wake(.6);
            });
        }).detach();
    }

    // ============================================================================================ files and catalogue
    static string key(const fs::path& file) {
        string s = S(file);
        return ends(s, ".disabled") ? s.substr(0, s.size() - 9) : s;
    }
    // What a folder's list looks like, in one string: a rescan that finds the same files draws nothing
    template <class L> static string listSig(const L& list) {
        string s;
        for (auto& x : list) s += S(x.file) + (x.on ? "+|" : "-|");
        return s;
    }
    void scan() {
        scanning = true;
        std::thread([this] {
            auto m = Game::mods();
            auto p = Game::packs();
            auto load = [this](const fs::path& f) {
                string k = key(f);
                {
                    std::lock_guard<std::mutex> l(iconLock);
                    if (iconBytes.count(k)) return;
                }
                string b = Game::icon(f);
                std::lock_guard<std::mutex> l(iconLock);
                iconBytes[k] = b;
            };
            for (auto& x : m) load(x.file);
            for (auto& x : p) load(x.file);
            auto fromModrinth = Modrinth::packFiles(Game::dir() / L"resourcepacks");
            post([this, m, p, fromModrinth] {
                bool same = listSig(m) == listSig(mods) && listSig(p) == listSig(packs) && fromModrinth == packInstalled;
                mods = m;
                packs = p;
                packInstalled = fromModrinth;
                scanning = false;
                if (!same) dirty = true;
            });
        }).detach();
    }
    void setMod(const Game::Mod& m, bool on) {
        fs::path now = Game::setMod(m, on);
        for (auto& x : mods)
            if (x.file == m.file) {
                x.file = now;
                x.on = on;
                break;
            }
        scannedAt = T - 2.5;
    }
    void setPack(const Game::Pack& p, bool on) {
        Game::setPack(p, on);
        for (auto& x : packs)
            if (x.file == p.file) {
                x.on = on;
                break;
            }
        scannedAt = T - 2.5;
    }
    vector<Game::Mod> shownMods() {
        string q = lowerAny(trim(searchField.value()));
        vector<Game::Mod> out;
        for (auto& m : mods) {
            if ((modFilter == 1 && !m.on) || (modFilter == 2 && m.on)) continue;
            if (!q.empty() && lowerAny(m.name).find(q) == string::npos &&
                lowerAny(S(m.file.filename())).find(q) == string::npos)
                continue;
            out.push_back(m);
        }
        return out;
    }
    vector<Modrinth::Hit> shownCatalog() {
        vector<Modrinth::Hit> out;
        if (modFilter != 0) return out;
        vector<string> files;
        for (auto& m : mods) files.push_back(S(m.file.filename()));
        for (auto& h : catalog)
            if (h.kind == "mod" && providerAllowed(false,h.source) && (installState.count(h.id) || !Modrinth::installed(h.slug, files))) out.push_back(h);
        return out;
    }
    string modKey() { return "mods|" + std::to_string(Config::modSources) + "|" + Config::mcVersion + "|" + Modrinth::MOD_CATEGORIES[modCat].id + "|" + trim(searchField.value()); }
    string packKey() { return "packs|" + std::to_string(Config::packSources) + "|" + Config::mcVersion + "|" + PacksMC::CATEGORIES[packCat].id + "|" + trim(packSearch.value()); }
    void loadIcons(const vector<Modrinth::Hit>& hits, const std::function<bool()>& current) {
        for (auto& h : hits) {
            if (!current()) return;
            string k = "mr:" + h.id;
            {
                std::lock_guard<std::mutex> l(iconLock);
                if (iconBytes.count(k)) continue;
            }
            string bytes;
            if (!h.iconUrl.empty()) {
                Net::Response r = Net::request("GET", h.iconUrl, {}, "", nullptr, 5);
                if (r.status == 200) bytes = r.body;
            }
            {
                std::lock_guard<std::mutex> l(iconLock);
                iconBytes[k] = bytes;
            }
            post([this] { dirty = true; });
        }
    }
    // What Modrinth is asked for a resource pack search: the words, and the category in Modrinth's own terms
    static std::pair<string, string> packQuery(const string& query, const string& category) {
        string facet = category == "pvp" ? "combat" : category == "faithful" ? "vanilla-like" : category == "fps" ? "utility" : category == "realistic" ? "realistic" : "";
        string text = query;
        if (!category.empty() && facet.empty()) text = trim(query + " " + category);
        return {text, facet};
    }
    // The next 30 from Modrinth, added at the end of the list (asked when the list is scrolled near its end)
    void loadMore(bool packs) {
        bool& busy = packs ? packMoreBusy : modMoreBusy;
        if (busy || !moreToCome(packs) || (packs ? packBusy.load() : catalogBusy.load())) return;
        string key = packs ? packKey() : modKey();
        if (key != (packs ? packCatalogFor : catalogFor)) return;
        busy = true;
        unsigned request = packs ? packRequest.load() : modRequest.load();
        int offset = packs ? packMrFetched : modMrFetched;
        string query = trim(packs ? packSearch.value() : searchField.value());
        string category = packs ? PacksMC::CATEGORIES[packCat].id : Modrinth::MOD_CATEGORIES[modCat].id;
        string version = Config::mcVersion;
        if (!packs) {
            int mask = Config::modSources, cfOffset = modCfFetched;
            if (modMrFetched >= modMrTotal) mask &= ~1;
            if (modCfFetched >= modCfTotal) mask &= ~2;
            vector<Modrinth::Hit> already = catalog;
            std::thread([this, request, key, offset, cfOffset, query, category, version, mask, already] {
                ModPage pg = modPage(query, version, category, mask, offset, cfOffset, already);
                post([this, request, key, pg] {
                    modMoreBusy = false;
                    if (request != modRequest.load() || key != modKey()) return;
                    // a site that failed or ran dry asks no more
                    if (pg.mrTotal < 0) modMrTotal = modMrFetched; else { modMrFetched += pg.mrGot; modMrTotal = pg.mrGot ? pg.mrTotal : modMrFetched; }
                    if (pg.cfTotal < 0) modCfTotal = modCfFetched; else { modCfFetched += pg.cfGot; modCfTotal = pg.cfGot ? pg.cfTotal : modCfFetched; }
                    std::set<string> have;
                    for (auto& h : catalog) have.insert(h.id);
                    vector<Modrinth::Hit> fresh;
                    for (auto& h : pg.hits)
                        if (!have.count(h.id) && h.kind == "mod") fresh.push_back(h);
                    catalog.insert(catalog.end(), fresh.begin(), fresh.end());
                    searchCache[key] = catalog;
                    searchMeta[key] = {modMrFetched, modMrTotal};
                    cfMeta[key] = {modCfFetched, modCfTotal};
                    dirty = true;
                    std::thread([this, fresh, request] { loadIcons(fresh, [this, request] { return request == modRequest.load(); }); }).detach();
                });
            }).detach();
            return;
        }
        std::thread([this, packs, request, key, offset, query, category, version] {
            vector<Modrinth::Hit> next;
            int total = -1;
            try {
                if (packs) {
                    auto [text, facet] = packQuery(query, category);
                    next = Modrinth::search(text, version, "resourcepack", facet, offset, &total);
                }
            } catch (...) {}
            post([this, packs, request, key, next, total] {
                (packs ? packMoreBusy : modMoreBusy) = false;
                if (request != (packs ? packRequest.load() : modRequest.load()) || key != (packs ? packKey() : modKey())) return;
                if (total < 0) {
                    (packs ? packMrTotal : modMrTotal) = packs ? packMrFetched : modMrFetched;
                    return;
                }
                auto& list = packs ? packCatalog : catalog;
                std::set<string> have;
                for (auto& h : list) have.insert(h.id);
                vector<Modrinth::Hit> fresh;
                for (auto& h : next)
                    if (!have.count(h.id) && h.kind == (packs ? "resourcepack" : "mod")) fresh.push_back(h);
                list.insert(list.end(), fresh.begin(), fresh.end());
                int& fetched = packs ? packMrFetched : modMrFetched;
                fetched += (int)next.size();
                (packs ? packMrTotal : modMrTotal) = next.empty() ? fetched : total;
                searchCache[key] = list;
                searchMeta[key] = {fetched, packs ? packMrTotal : modMrTotal};
                dirty = true;
                std::thread([this, fresh, packs, request] {
                    loadIcons(fresh, [this, packs, request] { return request == (packs ? packRequest.load() : modRequest.load()); });
                }).detach();
            });
        }).detach();
    }
    void completeSearch(bool packs, unsigned request, const string& key, vector<Modrinth::Hit> hits, bool failed, int mrFetched = 0,
                        int mrTotal = 0, int cfFetched = 0, int cfTotal = 0) {
        hits.erase(std::remove_if(hits.begin(),hits.end(),[this,packs](const auto& h) {
            return h.kind != (packs ? "resourcepack" : "mod") || !providerAllowed(packs,h.source);
        }),hits.end());
                if (request != (packs ? packRequest.load() : modRequest.load())) return;
                (packs ? packBusy : catalogBusy) = false;
                // A late response must never replace a newer typed query or another game version
                if (key != (packs ? packKey() : modKey())) return;
                if (!failed) {
                    if (searchCache.size() >= 32) searchCache.erase(searchCache.begin());
                    searchCache[key] = hits;
                    searchMeta[key] = {mrFetched, mrTotal};
                    cfMeta[key] = {cfFetched, cfTotal};
                }
                if (packs) { packCatalog = hits; packCatalogFor = key; packLoaded = true; packFailed = failed; packMrFetched = mrFetched; packMrTotal = mrTotal; }
                else { catalog = hits; catalogFor = key; catalogLoaded = true; catalogFailed = failed; modMrFetched = mrFetched; modMrTotal = mrTotal; modCfFetched = cfFetched; modCfTotal = cfTotal; }
                dirty = true;
    }
    bool providerAllowed(bool packs, const string& source) {
        if (packs) return (source == "packsmc" && (Config::packSources & 1)) || (source == "modrinth" && (Config::packSources & 2));
        return (source == "modrinth" && (Config::modSources & 1)) || (source == "curseforge" && (Config::modSources & 2));
    }
    void sourceMenu() {
        sourcesOpen = !sourcesOpen; sourceFocus = (page==PACKS ? Config::packSources : Config::modSources) - 1;
        setFocus(nullptr); wake(.35);
    }
    int sourceCount() const {return 3;}
    void placeSources() {
        float offset=-5*(1-an.get("sources.reveal",0));
        for(int i=0;i<sourceCount();++i) sourceOptions[i].at(sourcesBtn.x+8,sourcesBtn.y+46+offset+i*34,182,32);
    }
    bool inSources(float x,float y) const {
        float offset=-5*(1-an.get("sources.reveal",0));
        return x>=sourcesBtn.x && x<sourcesBtn.x+198 && y>=sourcesBtn.y+38+offset && y<sourcesBtn.y+54+offset+sourceCount()*34;
    }
    void paintSources() {
        float a=an.to("sources.reveal",sourcesOpen?1.f:0.f,26);
        if(a<.005f || (page!=MODS && page!=PACKS))return;
        placeSources(); float x=sourcesBtn.x,y=sourcesBtn.y+38-5*(1-a),h=16+sourceCount()*34;
        // it unfolds from the button: grows from 92 % at its top corner while the rows slide in one after another
        float pop=1-powf(1-a,3), grow=.92f+.08f*pop;
        D2D1_MATRIX_3X2_F saved; G.rt->GetTransform(&saved);
        G.rt->SetTransform(D2D1::Matrix3x2F::Scale(grow,grow,D2D1::Point2F(x+40,y))*saved);
        G.glow(x,y+6,198,h,12,alpha(rgb(0),.35f*a),16);
        G.glass(x,y,198,h,10,rgba(40,40,46,205),a);  // a macOS menu: glass, the row under the pointer in system blue
        for(int i=0;i<sourceCount();++i) {
            auto& row=sourceOptions[i]; bool selected=(page==PACKS?Config::packSources:Config::modSources)==i+1;
            float ra=clamp01((a-i*.14f)/(1-.14f*(sourceCount()-1)));
            ra=1-(1-ra)*(1-ra);
            D2D1_MATRIX_3X2_F rowSaved; G.rt->GetTransform(&rowSaved);
            G.rt->SetTransform(D2D1::Matrix3x2F::Translation(-8*(1-ra),0)*rowSaved);
            float a=ra*pop;
            float hv=an.to(row.id+".hover",row.hovered || sourceFocus==i?1.f:0.f,24);
            float sel=an.to(row.id+".sel",selected?1.f:0.f,18);
            G.rr(row.x,row.y,row.w,row.h,6,fade(ACTION,a*hv));
            string label=i==2?Microsoft::message("Все сайты","All sources"):page==MODS?(i==0?"Modrinth":"CurseForge"):i==0?"PacksMC":"Modrinth";
            if(i<2) {
                int resource=page==MODS?(i==0?140:142):(i==1?140:141);
                string key="provider:"+std::to_string(resource);
                auto found=G.bitmaps.find(key);
                if(found==G.bitmaps.end()) {
                    auto* decoded=G.decode(resourceBytes(resource),96);
                    auto* bitmap=G.toDevice(decoded); release(decoded);
                    G.bitmaps[key]=bitmap;
                }
                if(auto* bitmap=G.bitmaps[key]) G.picture(bitmap,row.x+28,row.y+7,18,18,5,a);
            } else G.icon("layers",row.x+37,row.y+16,15,fade(mix(ACCENT,WHITE,hv),a),1.6f);
            G.text(label,row.x+54,Gfx::mid(row.y+16,12.5f),MEDIUM,12.5f,fade(mix(TEXT,WHITE,hv),a));
            if(sel>.01f) G.icon("check",row.x+15,row.y+16,11,fade(WHITE,a*sel),2);  // the tick at the left, as in a Mac menu
            G.rt->SetTransform(rowSaved);
        }
        G.rt->SetTransform(saved);
    }
    void selectModSource(int source) {
        Config::modSources = std::clamp(source,1,3); Config::save();
        ++modRequest; catalogBusy = false; catalogLoaded = false; catalog.clear(); modError.clear();
        modInFlight.clear(); scroll[MODS] = 0; scannedAt = -10; wake(.5);
    }
    void selectPackSource(int source) {
        Config::packSources = std::clamp(source,1,3); Config::save();
        ++packRequest; packBusy = false; packLoaded = false; packCatalog.clear(); packError.clear();
        packInFlight.clear(); scroll[PACKS] = 0; scannedAt = -10; wake(.5);
    }
    // Files dropped onto the window: .zip resource packs go to resourcepacks/, .jar mods to mods/
    void dropFiles(const vector<fs::path>& files) {
        if (files.empty()) return;
        std::thread([this, files] {
            int packsIn = 0, modsIn = 0, refused = 0;
            for (auto& f : files) {
                string ext = lower(S(f.extension()));
                std::error_code ec;
                if (ext == ".zip" && PacksMC::importZip(f, Game::dir() / L"resourcepacks"))
                    packsIn++;
                else if (ext == ".jar" && fileSize(f) > 0 && fs::copy_file(f, Game::modsDir() / f.filename(), fs::copy_options::overwrite_existing, ec))
                    modsIn++;
                else
                    refused++;
            }
            Log::info(fmt("dropped files: %d packs, %d mods, %d refused", packsIn, modsIn, refused));
            post([this, packsIn, modsIn, refused] {
                scannedAt = -10;
                if (packsIn) { packsRemote = false; openPage(PACKS); }
                else if (modsIn) { modsRemote = false; openPage(MODS); }
                string said = (packsIn ? plural(packsIn, "пак добавлен", "пака добавлено", "паков добавлено", "pack added", "packs added") : "") +
                              (packsIn && modsIn ? "  ·  " : "") +
                              (modsIn ? plural(modsIn, "мод добавлен", "мода добавлено", "модов добавлено", "mod added", "mods added") : "");
                if (said.empty()) said = Lang::t("Перетащи .zip ресурспака или .jar мода");
                G.raw = false;
                setNote(said, packsIn || modsIn ? OK : WARN);
                UiAudio::play(packsIn || modsIn ? UiAudio::CLICK : UiAudio::FAIL);
            });
        }).detach();
    }
    void importPackZip() {
        IFileOpenDialog* dlg = nullptr;
        if (FAILED(CoCreateInstance(CLSID_FileOpenDialog,nullptr,CLSCTX_INPROC_SERVER,IID_PPV_ARGS(&dlg)))) return;
        COMDLG_FILTERSPEC filter[] = {{L"Minecraft resource pack (*.zip)", L"*.zip"}};
        dlg->SetFileTypes(1,filter); dlg->SetOptions(FOS_FORCEFILESYSTEM | FOS_FILEMUSTEXIST);
        dlg->SetTitle(W(Microsoft::message("Добавить ресурспак", "Add resource pack")).c_str());
        if (SUCCEEDED(dlg->Show(hwnd))) {
            IShellItem* item=nullptr; PWSTR path=nullptr;
            if (SUCCEEDED(dlg->GetResult(&item)) && SUCCEEDED(item->GetDisplayName(SIGDN_FILESYSPATH,&path))) {
                fs::path file=path; CoTaskMemFree(path);
                std::thread([this,file] {
                    bool copied=PacksMC::importZip(file,Game::dir()/L"resourcepacks");
                    post([this,copied] {
                        scannedAt=-10;
                        setNote(copied ? Microsoft::message("Ресурспак добавлен", "Resource pack added") : Microsoft::message("Не удалось добавить ZIP: проверь, что внутри есть pack.mcmeta", "Could not add ZIP: check that it contains pack.mcmeta"), copied?DIM:ERR);
                    });
                }).detach();
            }
            release(item);
        }
        dlg->Release();
    }
    void loadCatalog() { loadSearch(false); }
    void loadPacks() { loadSearch(true); }
    void loadSearch(bool packs) {
        string key = packs ? packKey() : modKey();
        auto& serial = packs ? packRequest : modRequest;
        auto& busy = packs ? packBusy : catalogBusy;
        auto cached = searchCache.find(key);
        if (cached != searchCache.end()) {
            ++serial; busy = false;
            auto meta = searchMeta.count(key) ? searchMeta[key] : std::make_pair(0, 0);
            if (packs) { packCatalog = cached->second; packCatalogFor = key; packLoaded = true; packFailed = false; packMrFetched = meta.first; packMrTotal = meta.second; }
            else {
                auto cf = cfMeta.count(key) ? cfMeta[key] : std::make_pair(0, 0);
                catalog = cached->second; catalogFor = key; catalogLoaded = true; catalogFailed = false;
                modMrFetched = meta.first; modMrTotal = meta.second; modCfFetched = cf.first; modCfTotal = cf.second;
            }
            dirty = true; return;
        }
        if (activeSearches >= 3) return;
        unsigned request = ++serial;
        busy = true; ++activeSearches;
        (packs ? packInFlight : modInFlight) = key;
        string query = trim(packs ? packSearch.value() : searchField.value());
        string category = packs ? PacksMC::CATEGORIES[packCat].id : Modrinth::MOD_CATEGORIES[modCat].id;
        string version = Config::mcVersion;
        int mask = packs ? Config::packSources : Config::modSources;
        (packs ? packError : modError).clear();
        std::thread([this, packs, request, key, query, category, version, mask] {
            vector<Modrinth::Hit> hits; bool failed = true;
            int mrTotal = 0;
            auto append = [&](const vector<Modrinth::Hit>& next) { hits.insert(hits.end(),next.begin(),next.end()); failed = false; };
            auto fetch = [&](const std::function<vector<Modrinth::Hit>()>& work) {
                try { append(work()); }
                catch (const std::exception& error) {
                    post([this,packs,request,message=string(error.what())] {
                        if (request==(packs?packRequest.load():modRequest.load())) (packs?packError:modError)=message;
                    });
                }
            };
            if (!packs) {
                ModPage pg = modPage(query, version, category, mask, 0, 0);
                bool none = pg.mrTotal < 0 && pg.cfTotal < 0;
                if (!pg.error.empty())
                    post([this, request, message = pg.error] { if (request == modRequest.load()) modError = message; });
                --activeSearches;
                post([this, request, key, pg, none] {
                    completeSearch(false, request, key, pg.hits, none, pg.mrGot, std::max(0, pg.mrTotal), pg.cfGot, std::max(0, pg.cfTotal));
                });
                if (!none) loadIcons(pg.hits, [this, request] { return request == modRequest.load(); });
                return;
            }
            if (packs) {
                std::future<vector<Modrinth::Hit>> pmc, mr;
                if(mask&1) pmc=std::async(std::launch::async,[&] {return PacksMC::search(query,category);});
                if(mask&2) mr=std::async(std::launch::async,[&] {
                    auto [text, facet] = packQuery(query, category);
                    return Modrinth::search(text, version, "resourcepack", facet, 0, &mrTotal);
                });
                vector<Modrinth::Hit> fromPmc, fromMr;
                if(pmc.valid()) { auto before = hits.size(); fetch([&] {return pmc.get();}); fromPmc.assign(hits.begin() + before, hits.end()); hits.resize(before); }
                if(mr.valid()) { auto before = hits.size(); fetch([&] {return mr.get();}); fromMr.assign(hits.begin() + before, hits.end()); hits.resize(before); }
                for (size_t i = 0; i < std::max(fromPmc.size(), fromMr.size()); i++) {  // the two sites in turn, one row each
                    if (i < fromMr.size()) hits.push_back(fromMr[i]);
                    if (i < fromPmc.size()) hits.push_back(fromPmc[i]);
                }
            }
            int mrFetched = 0;
            for (auto& h : hits) mrFetched += h.source == "modrinth" ? 1 : 0;
            --activeSearches;
            post([this, packs, request, key, hits, failed, mrFetched, mrTotal] { completeSearch(packs,request,key,hits,failed,mrFetched,mrTotal); });
            // Search is already ready for the next query; icons arrive independently
            if (!failed) loadIcons(hits, [this, packs, request] {
                return request == (packs ? packRequest.load() : modRequest.load());
            });
        }).detach();
    }
    vector<Game::Pack> shownPacks() {
        string q = lowerAny(trim(packSearch.value()));
        vector<Game::Pack> out;
        for (auto& p : packs)
            if (q.empty() || lowerAny(p.name).find(q) != string::npos ||
                lowerAny(S(p.file.filename())).find(q) != string::npos)
                out.push_back(p);
        return out;
    }
    vector<Modrinth::Hit> shownPackCatalog() {
        vector<Modrinth::Hit> out;
        for (auto& h : packCatalog)
            if (h.kind == "resourcepack" && providerAllowed(true,h.source) && (installState.count(h.id) || !packInstalled.count(h.id))) out.push_back(h);
        return out;
    }
    // PacksMC: the download has to happen on packsmc.com (their rule)
    std::map<string, unsigned> watchSerial;
    std::set<fs::path> claimedDownloads;
    void watchDownload(const Modrinth::Hit& h) {
        unsigned serial;
        {
            std::lock_guard<std::mutex> l(iconLock);
            serial = ++watchSerial[h.id];
        }
        installState[h.id] = "wait";
        setNote(Microsoft::message("Скачай пак на странице PacksMC — лаунчер сам установит его", "Download the pack on its PacksMC page - the launcher installs it by itself"), DIM);
        string id = h.id, title = h.title;
        fs::path dl = PacksMC::downloadsDir(), packsDir = Game::dir() / L"resourcepacks";
        std::thread([this, id, title, serial, dl, packsDir] {
            auto stamp = [](const fs::path& f) { std::error_code ec; auto t = fs::last_write_time(f, ec); return ec ? 0LL : (long long)t.time_since_epoch().count(); };
            std::map<fs::path, long long> seen;  // what was there before the click, and what turned out not to be a pack
            std::map<fs::path, uintmax_t> sizes;
            std::error_code ec;
            for (auto& e : fs::directory_iterator(dl, ec)) seen[e.path()] = stamp(e.path());
            for (int tick = 0; tick < 400; tick++) {  // 10 minutes, every 1.5 s
                Sleep(1500);
                bool current = false;
                {
                    std::lock_guard<std::mutex> l(iconLock);
                    current = watchSerial.count(id) && watchSerial[id] == serial;
                }
                if (!current) return;
                for (auto& e : fs::directory_iterator(dl, ec)) {
                    const fs::path& f = e.path();
                    if (lower(S(f.extension())) != ".zip") continue;
                    auto s = stamp(f);
                    auto was = seen.find(f);
                    if (was != seen.end() && was->second == s) continue;
                    uintmax_t size = fileSize(f);
                    if (!size || sizes[f] != size) { sizes[f] = size; continue; }
                    {
                        std::lock_guard<std::mutex> l(iconLock);
                        if (claimedDownloads.count(f)) { seen[f] = s; continue; }
                        claimedDownloads.insert(f);
                    }
                    fs::path in = PacksMC::importZipTo(f, packsDir);
                    if (in.empty()) { seen[f] = s; continue; }  // a .zip, but not a resource pack
                    post([this, id, title, in] {
                        auto known = Modrinth::packFiles(in.parent_path());
                        known[id] = S(in.filename());
                        string list;
                        for (auto& kv : known) list += kv.first + "\t" + kv.second + "\n";
                        writeFile(in.parent_path() / L".arcanic-packs", list);
                        Game::setPack(Game::Pack{Game::pretty(S(in.filename())), in, fileSize(in), false}, true);
                        installState[id] = "done";
                        scannedAt = -10;
                        setNote(Lang::en ? "Resource pack \"" + title + "\" installed and switched on" : "Ресурспак «" + title + "» установлен и включён", OK);
                        UiAudio::play(UiAudio::CLICK);
                        dirty = true;
                    });
                    return;
                }
            }
            post([this, id, serial] {
                if (watchSerial[id] != serial || installState[id] != "wait") return;
                installState.erase(id);
                setNote(Microsoft::message("Не дождались файла — нажми «Скачать» ещё раз", "No file arrived - press Download again"), WARN);
                dirty = true;
            });
        }).detach();
    }
    void install(const Modrinth::Hit& h) {
        if (h.source == "packsmc") {
            if (!PacksMC::page(h.webpage)) return;
            ShellExecuteW(hwnd, L"open", W(h.webpage).c_str(), nullptr, nullptr, SW_SHOWNORMAL);
            watchDownload(h);
            return;
        }
        if (h.source == "curseforge" && !h.direct) {  // the author allows downloads only on CurseForge itself
            if (CurseForge::page(h.webpage)) ShellExecuteW(hwnd, L"open", W(h.webpage).c_str(), nullptr, nullptr, SW_SHOWNORMAL);
            return;
        }
        if (h.source != "modrinth" && h.source != "curseforge") return;
        installState[h.id] = "busy";
        wake(.4);
        string id = h.id;
        bool pack = h.kind == "resourcepack", cf = h.source == "curseforge";
        string webpage = h.webpage;
        std::thread([this, id, pack, cf, webpage] {
            string result;
            try {
                if (cf)
                    CurseForge::install(id, Config::mcVersion, Game::modsDir());
                else if (pack)
                    Modrinth::installPack(id, Config::mcVersion, Game::dir() / L"resourcepacks");
                else
                    Modrinth::install(id, Config::mcVersion, Game::modsDir());
                result = "done";
            } catch (Modrinth::NoVersion&) {
                result = "none";
            } catch (CurseForge::OnlyOnSite&) {
                result = "site";
                if (CurseForge::page(webpage)) ShellExecuteW(nullptr, L"open", W(webpage).c_str(), nullptr, nullptr, SW_SHOWNORMAL);
            } catch (...) {
                result = "error";
            }
            post([this, id, result] {
                installState[id] = result;
                scannedAt = -10;
                dirty = true;
            });
        }).detach();
    }
    void setInstallBtn(Btn& b, const Modrinth::Hit& h) {
        if (h.source == "packsmc") {
            string st = installState.count(h.id) ? installState[h.id] : "";
            b.busy = false;
            b.enabled = PacksMC::page(h.webpage) && st != "done";
            b.text = st == "done" ? "Установлен" : st == "wait" ? "Ждём файл…" : "Скачать";
            Modrinth::Hit copy = h; b.click = [this,copy] { install(copy); }; return;
        }
        if (h.source == "curseforge" && !h.direct) {
            b.busy = false;
            b.enabled = CurseForge::page(h.webpage);
            b.text = "На сайте";
            Modrinth::Hit copy = h; b.click = [this, copy] { install(copy); }; return;
        }
        string st = installState.count(h.id) ? installState[h.id] : "";
        b.busy = st == "busy";
        b.enabled = st != "done" && st != "none" && st != "site";
        b.text = st == "busy"    ? "Скачиваем"
                 : st == "done"  ? "Установлен"
                 : st == "none"  ? "Нет версии"
                 : st == "site"  ? "На сайте"
                 : st == "error" ? "Повторить"
                                 : "Установить";
        Modrinth::Hit copy = h;
        b.click = [this, copy] { install(copy); };
    }
    // ---- the preview: a project's screenshots, one at a time, with its description and the Install button
    void openPreview(const Modrinth::Hit& h) {
        for (auto it = G.bitmaps.begin(); it != G.bitmaps.end();) {
            if (starts(it->first, "gal:")) {
                if (it->second) it->second->Release();
                it = G.bitmaps.erase(it);
            } else
                ++it;
        }  // the last project's pictures go
        {
            std::lock_guard<std::mutex> l(iconLock);
            for (auto it = iconBytes.begin(); it != iconBytes.end();) {
                if (starts(it->first, "gal:"))
                    it = iconBytes.erase(it);
                else
                    ++it;
            }
        }
        preview = h;
        previewAt = 0;
        previewOpen = true;
        setFocus(nullptr);
        pressedW = nullptr;
        loadShot(0);
        loadShot(1);
        wake(1);
    }
    void closePreview() {
        previewOpen = false;
        pressedW = nullptr;
        wake(1);
    }
    void stepPreview(int d) {
        int n = (int)preview.gallery.size();
        if (n < 2) return;
        previewAt = (previewAt + d + n) % n;
        loadShot(previewAt);
        loadShot((previewAt + 1) % n);
        wake(.6);
    }
    void loadShot(int i) {
        if (i < 0 || i >= (int)preview.gallery.size()) return;
        string url = preview.gallery[i], k = "gal:" + url;
        {
            std::lock_guard<std::mutex> l(iconLock);
            if (iconBytes.count(k) || galBusy.count(k)) return;
            galBusy.insert(k);
        }
        std::thread([this, url, k] {
            string bytes;
            try {
                Net::Response r = Net::request("GET", url, {}, "", nullptr, 30);
                if (r.status == 200) bytes = r.body;
            } catch (...) {}
            {
                std::lock_guard<std::mutex> l(iconLock);
                iconBytes[k] = bytes;
                galBusy.erase(k);
            }
            post([this] { dirty = true; });
        }).detach();
    }
    // The screenshot as a device picture; loading = still on its way, null without loading = it could not be shown
    ID2D1Bitmap* shot(const string& url, bool& loading) {
        string k = "gal:" + url;
        loading = false;
        auto it = G.bitmaps.find(k);
        if (it != G.bitmaps.end()) return it->second;
        string bytes;
        {
            std::lock_guard<std::mutex> l(iconLock);
            auto b = iconBytes.find(k);
            if (b == iconBytes.end()) {
                loading = true;
                return nullptr;
            }
            bytes = b->second;
        }
        IWICBitmapSource* src = G.decode(bytes, 1440);
        ID2D1Bitmap* bmp = G.toDevice(src);
        release(src);
        return G.bitmaps[k] = bmp;
    }
    bool shotLoading() {
        if (!previewOpen || preview.gallery.empty()) return false;
        std::lock_guard<std::mutex> l(iconLock);
        return !iconBytes.count("gal:" + preview.gallery[previewAt]);
    }
    // Words wrapped into at most maxLines lines; what does not fit ends with an ellipsis
    vector<string> wrap(const string& s, int weight, float size, float maxW, int maxLines) {
        return G.wrap(s, weight, size, maxW, maxLines);
    }
    void previewDialog() {
        float k = an.to("preview", previewOpen ? 1 : 0, 14);
        if (k < .005f || preview.id.empty()) return;
        G.rect(0, 0, WIN_W, WIN_H, rgba(0, 0, 0, (int)lroundf(110 * k)));
        float x0 = pvX(), y0 = pvY();
        DialogPop pop(k, x0 + PV_W / 2, y0 + PV_H / 2);
        G.glow(x0, y0 + 12, PV_W, PV_H, 18, alpha(rgb(0), .45f * k), 30);
        G.glass(x0, y0, PV_W, PV_H, 18, rgba(34, 34, 40, 215), k);
        float ix = x0 + 20, iy = y0 + 20, iw = PV_W - 40, ih = 372;
        int n = (int)preview.gallery.size();
        G.rr(ix, iy, iw, ih, 12, fade(rgba(0, 0, 0, 80), k));
        if (n == 0) {  // no screenshots: the project's own icon, large
            if (ID2D1Bitmap* bmp = picture("mr:" + preview.id))
                G.picture(bmp, ix + iw / 2 - 60, iy + ih / 2 - 80, 120, 120, 26, k);
            else
                letterTile(preview.title, ix + iw / 2 - 60, iy + ih / 2 - 80, 120, k);
            G.center("Скриншотов нет", ix + iw / 2, iy + ih / 2 + 74, MEDIUM, 13, fade(DIM, k));
        } else {
            bool loading;
            ID2D1Bitmap* b = shot(preview.gallery[std::min(previewAt, n - 1)], loading);
            if (b) {
                D2D1_SIZE_F s = b->GetSize();
                float sc = std::min(iw / s.width, ih / s.height), w = s.width * sc, h = s.height * sc;
                G.picture(b, ix + (iw - w) / 2, iy + (ih - h) / 2, w, h, 8, k);
            } else if (loading) {
                float cx = ix + iw / 2, cy = iy + ih / 2 - 12, ang = (float)fmod(T * 5.2, 6.2831853);
                G.ring(cx, cy, 13, fade(alpha(WHITE, .12f), k), 2.5f);
                for (int i = 0; i < 10; i++) {
                    float a0 = ang + i * .17f, a1 = a0 + .17f;
                    G.line(cx + cosf(a0) * 13, cy + sinf(a0) * 13, cx + cosf(a1) * 13, cy + sinf(a1) * 13,
                           fade(rgb(0xD1D1D6), k), 2.5f);
                }
                an.moving = true;
                G.center("Загружаем картинку…", cx, cy + 40, MEDIUM, 12.5f, fade(MUTED, k));
            } else
                G.center("Картинка не загрузилась", ix + iw / 2, iy + ih / 2, MEDIUM, 13, fade(DIM, k));
            if (n > 1) {
                G.circle(pvPrev.x + pvPrev.w / 2, pvPrev.y + pvPrev.h / 2, 21, rgba(30, 30, 36, (int)lroundf(190 * k)));
                pvPrev.paint(k);
                G.circle(pvNext.x + pvNext.w / 2, pvNext.y + pvNext.h / 2, 21, rgba(30, 30, 36, (int)lroundf(190 * k)));
                pvNext.paint(k);
                string at = std::to_string(previewAt + 1) + " / " + std::to_string(n);
                float pw = G.width(at, SEMI, 12) + 22;
                G.rr(ix + iw / 2 - pw / 2, iy + ih - 36, pw, 24, 12, rgba(30, 30, 36, (int)lroundf(190 * k)));
                G.center(at, ix + iw / 2, Gfx::mid(iy + ih - 24, 12), SEMI, 12, fade(TEXT, k));
            }
        }
        G.circle(pvClose.x + pvClose.w / 2, pvClose.y + pvClose.h / 2, 16, rgba(30, 30, 36, (int)lroundf(190 * k)));
        pvClose.paint(k);
        float ty = iy + ih + 18, tw = pvInstall.x - 20 - (x0 + 86);
        if (ID2D1Bitmap* bmp = picture("mr:" + preview.id))
            G.picture(bmp, x0 + 20, ty, 52, 52, 12, k);
        else
            letterTile(preview.title, x0 + 20, ty, 52, k);
        G.text(G.fit(preview.title, SEMI, 18, tw), x0 + 86, ty + 23, SEMI, 18, fade(TEXT, k));
        G.text(G.fit((preview.author.empty() ? "" : "Автор: " + preview.author + "  ·  ") + "↓ " +
                         count(preview.downloads),
                     MEDIUM, 12.5f, tw),
               x0 + 86, ty + 44, MEDIUM, 12.5f, fade(MUTED, k));
        pvInstall.paint(k);
        float ly = ty + 80;
        for (auto& line : wrap(preview.description, MEDIUM, 13, PV_W - 40, 3)) {
            G.text(line, x0 + 20, ly, MEDIUM, 13, fade(MUTED, k));
            ly += 19;
        }
    }
    static string count(int64_t n) {
        if (n >= 1000000) return fmt("%.1f млн", n / 1e6);
        if (n >= 1000) return std::to_string((int)lround(n / 1e3)) + " тыс.";
        return std::to_string(n);
    }
    // The device picture for a key (a mod's icon, a pack's cover), decoded on first use; null if there is none
    ID2D1Bitmap* picture(const string& k) {
        auto it = G.bitmaps.find(k);
        if (it != G.bitmaps.end()) return it->second;
        string bytes;
        {
            std::lock_guard<std::mutex> l(iconLock);
            auto b = iconBytes.find(k);
            if (b == iconBytes.end()) return nullptr;
            bytes = b->second;
        }
        IWICBitmapSource* src = G.decode(bytes, 384);  // enough resolution for the large preview icon on a hiDPI screen
        ID2D1Bitmap* bmp = G.toDevice(src);
        release(src);
        return G.bitmaps[k] = bmp;
    }

    // ============================================================================================ time
    bool live() const { return T - paintedAt < .5; }
    float scrollY() { return an.get("scroll:" + std::to_string(page), 0); }
    // Widgets that do not scroll: the toolbar's, and everything in a sheet
    bool fixed(Widget* w) {
        if (modal()) return true;
        for (Widget* f : std::initializer_list<Widget*>{&kindTab[0], &kindTab[1], &categoryToggle, &sourcesBtn, &openModsBtn, &openPacksBtn,
                                                        &importPackBtn, &addAccountBtn, &accBack, &searchField, &packSearch, &updateAllBtn})
            if (w == f) return true;
        return false;
    }
    float listTop() const { return PAGE_TOP; }    bool visibleAt(Widget* w) { return fixed(w) || mouseY >= listTop() - 8; }
    bool hover(Widget* w, bool over) {
        bool pr = pressedW == w && over, ch = w->hovered != over || w->pressed != pr;
        w->hovered = over;
        w->pressed = pr;
        return ch;
    }

    vector<Widget*> pageWidgets() {
        vector<Widget*> out;
        if (errOpen) {
            out = {&errCopy, &errFolder, &errClose};
            return out;
        }
        if (!loggedIn()) {
            if (cur == LOGIN && !splash()) out = {&loginBtn, &discordBtn};
            return out;
        }
        if (previewOpen) {
            out = {&pvClose, &pvInstall};
            if (preview.gallery.size() > 1) {
                out.push_back(&pvPrev);
                out.push_back(&pvNext);
            }
            return out;
        }
        switch (page) {
        case HOME:
            out = {&playBtn, &trustBtn};
            break;
        case ACCOUNTS:
            if (addMode()) {
                out = {&msAdd, &accNick, &accAdd};
                if (accBack.visible) out.push_back(&accBack);
            } else {
                out = {&heroSelect, &heroCopy, &heroRefresh, &heroDelete, &addAccountBtn};
                for (auto& r : accRows) out.push_back(r.get());
            }
            break;
        case MODS:
            out = {&kindTab[0], &kindTab[1]};
            if (searchField.visible) out.push_back(&searchField);
            if (modsRemote) {
                out.push_back(&categoryToggle);
                out.push_back(&sourcesBtn);
                if (showCategories) for (auto& c : modCats) out.push_back(c.get());
                for (auto& h : shownCatalog()) {
                    if (installBtns.count(h.id)) out.push_back(installBtns[h.id].get());
                    if (viewBtns.count(h.id)) out.push_back(viewBtns[h.id].get());
                }
            } else {
                out.push_back(&openModsBtn);
                if (updateAllBtn.visible) out.push_back(&updateAllBtn);
                for (auto& f : filters) out.push_back(&f);
                for (auto& m : shownMods()) { auto it = rowToggles.find("mod:" + key(m.file)); if (it != rowToggles.end()) out.push_back(it->second.get()); }
            }
            break;
        case PACKS:
            out = {&kindTab[0], &kindTab[1]};
            if (packSearch.visible) out.push_back(&packSearch);
            if (packsRemote) {
                out.push_back(&categoryToggle);
                out.push_back(&sourcesBtn);
                if (showCategories) for (auto& c : packCats) out.push_back(c.get());
                for (auto& h : shownPackCatalog()) {
                    if (installBtns.count(h.id)) out.push_back(installBtns[h.id].get());
                    if (viewBtns.count(h.id)) out.push_back(viewBtns[h.id].get());
                }
            } else {
                out.push_back(&importPackBtn);
                out.push_back(&openPacksBtn);
                for (auto& pack : shownPacks()) { auto it = rowToggles.find("pack:" + key(pack.file)); if (it != rowToggles.end()) out.push_back(it->second.get()); }
            }
            break;
        case SETTINGS:
            out = {&arcBtn, &menuScale, &resetScale, &langSetting, &soundsToggle, &startupToggle, &memory, &autoRamBtn, &cleanToggle, &jvmField,
                   &gameDirBtn, &modsDirBtn, &moveBtn, &defaultBtn, &updateBtn, &discordLink, &siteLink, &logBtn};
            for (auto& th : wpThumb) out.push_back(&th);
            if (isAdmin() && signedIn()) {
                out.push_back(&grantLogin);
                out.push_back(&grantBtn);
                out.push_back(&grantDays);
                out.push_back(&lifetimeBtn);
                out.push_back(&premListBtn);
                for (auto& b : premAct)  // before the rows: a button on the open row takes the click
                    if (b.visible) out.push_back(&b);
                if (premDays.visible) out.push_back(&premDays);
                for (auto& r : premRowW)
                    if (r->visible) out.push_back(r.get());
            }
            break;
        }
        return out;
    }    Toggle* rowToggle(const string& k, bool on, float y) {
        auto& t = rowToggles[k];
        if (!t) {
            t = std::make_unique<Toggle>(k, on);
            t->row = true;
        } else if (pressedW != t.get())
            t->on = on;
        t->at(CX, y, CW, ROW_H);  // the whole row is the switch
        return t.get();
    }
    // A row of category chips starting at y; returns where the next thing goes
    float placeCats(vector<std::unique_ptr<Clickable>>& cats, float y) {
        float x = CX;
        for (auto& c : cats) {
            float width = G.width(c->tag, MEDIUM, 12.5f) + 26;
            if (x > CX && x + width > CX + CW) { x = CX; y += 36; }
            c->at(x, y, width, 28); x += width + 7;
        }
        return y + 39;
    }
    // The catalogue's rows from y down: an Install button and a clickable row (the preview) per project
    float placeCatalog(const vector<Modrinth::Hit>& hits, float y, std::set<string>& ids) {
        float width = (CW - 10) / 2;
        for (size_t i = 0; i < hits.size(); i++) {
            const auto& h = hits[i]; ids.insert(h.id);
            float x = CX + (i % 2) * (width + 10), ry = y + (i / 2) * 90;
            auto& b = installBtns[h.id];
            if (!b) { b = std::make_unique<Btn>("inst:" + h.id, "Установить"); b->fontSize = 11.5f; b->boxy = true; }
            setInstallBtn(*b, h); b->at(x + width - 103, ry + 25, 91, 30);
            auto& v = viewBtns[h.id]; if (!v) v = std::make_unique<Clickable>("view:" + h.id);
            Modrinth::Hit copy = h; v->click = [this, copy] { openPreview(copy); };
            v->at(x, ry, width - 111, 80);
        }
        for (auto it = installBtns.begin(); it != installBtns.end();) {
            if (!ids.count(it->first)) { if (pressedW == it->second.get()) pressedW = nullptr; it = installBtns.erase(it); } else ++it;
        }
        for (auto it = viewBtns.begin(); it != viewBtns.end();) {
            if (!ids.count(it->first)) { if (pressedW == it->second.get()) pressedW = nullptr; it = viewBtns.erase(it); } else ++it;
        }
        return y + ((hits.size() + 1) / 2) * 90;
    }
    // ---- the toolbar: the arrows on the left, the page's own controls on the right
    static constexpr float TB_Y = 17, TB_H = 28;
    // Mods | Resource packs as a segmented control ending at `right`; returns where it starts
    float placeKind(float right) {
        float w0 = G.width(kindTab[0].tag, SEMI, 12.5f) + 28, w1 = G.width(kindTab[1].tag, SEMI, 12.5f) + 28, x = right - (w0 + w1 + 4);
        kindTab[0].at(x + 2, TB_Y + 2, w0, TB_H - 4);
        kindTab[1].at(x + 2 + w0, TB_Y + 2, w1, TB_H - 4);
        return x;
    }
    // ---- Settings: grouped like System Settings - a small heading over each group of rows
    struct SettingsGeom {
        float account, accountH, look, lookH, sound, soundH, game, gameH, files, filesH, about, aboutH, end;
    };
    static constexpr float SG_HEAD = 26, SG_GAP = 18, SG_ROW = 48, SG_WALL = 154;
    SettingsGeom settingsGeom() const {
        SettingsGeom g;
        float y = PAGE_TOP + SG_HEAD;
        g.account = y, g.accountH = 64 + (isAdmin() && signedIn() ? grantRowsH() + premListH() : 0), y +=g.accountH + SG_GAP + SG_HEAD;
        g.look = y, g.lookH = SG_WALL + 2 * SG_ROW, y += g.lookH + SG_GAP + SG_HEAD;
        g.sound = y, g.soundH = 2 * SG_ROW, y += g.soundH + SG_GAP + SG_HEAD;
        g.game = y, g.gameH = 4 * SG_ROW + 8, y += g.gameH + SG_GAP + SG_HEAD;
        g.files = y, g.filesH = 98, y += g.filesH + SG_GAP + SG_HEAD;
        g.about = y, g.aboutH = 112, y += g.aboutH;
        g.end = y;
        return g;
    }
    // Positions the widgets for this frame
    void place() {
        for (auto* c : nav) c->enabled = loggedIn();
        langBtn.visible = langBtn.enabled = !loggedIn();  // inside, the language lives in Settings
        playBtn.visible = playBtn.enabled = loggedIn() && page == HOME;
        trustBtn.visible = loggedIn() && page == HOME && !playBtn.running && !playBtn.busy;
        trustBtn.enabled = trustBtn.visible && !playBusy;
        // ---- the sidebar (window coordinates: it never scrolls)
        closeBtn.at(18, 18, 20, 20);
        minBtn.at(38, 18, 20, 20);
        greenBtn.at(58, 18, 20, 20);
        sbSearch.visible = sbSearch.enabled = accountRow.visible = accountRow.enabled = loggedIn();
        sbSearch.at(18, 48, SB_W - 20, 28);
        for (int i = 0; i < 3; i++) nav[i]->at(14, 90 + i * 30.f, SB_W - 12, 28);
        nav[3]->at(14, SB_Y + SB_H - 92, SB_W - 12, 28);
        accountRow.at(14, SB_Y + SB_H - 56, SB_W - 12, 44);
        {
            auto vl = versionList();
            for (size_t i = 0; i < favRows.size(); i++) {
                Clickable& r = *favRows[i];
                r.visible = r.enabled = i < vl.size() && loggedIn();
                r.at(14, 200 + i * 40.f, SB_W - 12, 38);
                string id = i < vl.size() ? vl[i].first : "";
                r.click = [this, id, &r] {  // the round button at the right of the chosen version starts the game
                    if (id == Config::mcVersion && r.rippleX > r.x + r.w - 36) play();
                    else pickVersion(id);
                };
            }
        }
        // ---- the toolbar
        navBack.at(CX, TB_Y, 34, TB_H);
        navFwd.at(CX + 34, TB_Y, 34, TB_H);
        navBack.enabled = !backStack.empty();
        navFwd.enabled = !fwdStack.empty();
        navBack.visible = navFwd.visible = loggedIn();
        // ---- the banner of a notification
        toastHit.at(WIN_W - 16 - TOAST_W, 14, TOAST_W, TOAST_H);
        toastHit.visible = toastHit.enabled = toastK() > .05f;
        contentHeight = 0;
        if (!loggedIn()) {  // the login card in the middle of the window
            const float cw = 300;
            loginBtn.at((WIN_W - cw) / 2 + 30, 342, cw - 60, 34);
            loginBtn.fontSize = 13.5f;
            discordBtn.at((WIN_W - cw) / 2 + 30, 384, cw - 60, 34);
            discordBtn.fontSize = 13;
            langBtn.at(WIN_W - 16 - 96, 16, 96, 28);
            return;
        }
        {  // the alert's buttons
            float by = errY() + ERR_H - 20 - 30, bw = (ERR_W - 40 - 16) / 3;
            errFolder.at(errX() + 20, by, bw, 30);
            errClose.at(errX() + 20 + bw + 8, by, bw, 30);
            errCopy.at(errX() + 20 + 2 * (bw + 8), by, bw, 30);
        }
        {  // the preview's own buttons (they also fade out with it)
            float x0 = pvX(), y0p = pvY(), iy = y0p + 20, ih = 372;
            pvClose.at(x0 + PV_W - 20 - 40, iy + 10, 30, 30);
            pvPrev.at(x0 + 20 + 12, iy + ih / 2 - 18, 36, 36);
            pvNext.at(x0 + PV_W - 20 - 12 - 36, iy + ih / 2 - 18, 36, 36);
            pvInstall.at(x0 + PV_W - 20 - 160, iy + ih + 22, 160, 34);
            if (!preview.id.empty()) setInstallBtn(pvInstall, preview);
        }
        float right = CX + CW;
        switch (page) {
        case HOME: {
            float x = homeX(), w = homeW();
            float pw = playBtn.running ? G.width(Lang::t(playBtn.text), SEMI, 13.5f) + 52 : 128;
            playBtn.at(x + 22, HERO_Y + HERO_HT - 22 - 36, pw, 36);
            playBtn.fontSize = 14;
            // Trust mode sits right of Play while the game can be started
            trustBtn.fontSize = 14;
            trustBtn.at(playBtn.x + playBtn.w + 10, playBtn.y, G.width(Lang::t(trustBtn.text), SEMI, 13.f) + 52, 36);
            for (auto& all : homeAll) all.visible = all.enabled = false;  // the home page is the banner alone now
            for (auto& card : homeCards) card->visible = card->enabled = false;
            contentHeight = HERO_Y + HERO_HT + 24 - listTop();
            break;
        }
        case MODS:
        case PACKS: {
            bool packsPage = page == PACKS, remote = packsPage ? packsRemote : modsRemote;
            float x = placeKind(right) - 10, leftmost = x;
            if (remote) {
                categoryToggle.at(x - 28, TB_Y, 28, TB_H);
                x -= 28 + 8;
                sourcesBtn.boxy = true;
                sourcesBtn.fontSize = 12.5f;
                float sw = G.width(Lang::t(sourcesBtn.text), SEMI, 12.5f) + 44;
                sourcesBtn.at(x - sw, TB_Y, sw, TB_H);
                leftmost = sourcesBtn.x;
            } else if (packsPage) {
                openPacksBtn.text = "";
                openPacksBtn.icon = "folder";
                openPacksBtn.at(x - 28, TB_Y, 28, TB_H);
                importPackBtn.boxy = true;
                importPackBtn.fontSize = 12.5f;
                importPackBtn.icon = "plus";
                float iw = G.width(Lang::t(importPackBtn.text), SEMI, 12.5f) + 44;
                importPackBtn.at(x - 28 - 8 - iw, TB_Y, iw, TB_H);
                leftmost = importPackBtn.x;
            } else {
                openModsBtn.text = "";
                openModsBtn.icon = "folder";
                openModsBtn.at(x - 28, TB_Y, 28, TB_H);
                float fx = x - 28 - 10;
                for (int i = 2; i >= 0; i--) {  // All | Enabled | Disabled, a second segmented control
                    float fw = G.width(filters[i].tag, SEMI, 12) + 22;
                    fx -= fw;
                    filters[i].at(fx, TB_Y + 2, fw, TB_H - 4);
                }
                leftmost = filters[0].x - 2;
                updateAllBtn.visible = updateAllBtn.enabled = !modUpdates.empty() || updatingAll;
                if (updateAllBtn.visible) {
                    updateAllBtn.primary = true;
                    updateAllBtn.boxy = true;
                    updateAllBtn.fontSize = 12.5f;
                    updateAllBtn.icon = "refresh";
                    // as long as it fits beside the page's title: "Update all (3)", then "Update (3)", then the icon and "3"
                    float titleEnd = navFwd.x + navFwd.w + 14 + G.width(Lang::t("Библиотека"), BOLD, 15) + 8, room = leftmost - 8 - titleEnd;
                    int n = (int)modUpdates.size();
                    string labels[3] = {updatingAll ? "Обновляем…" : fmt(Lang::en ? "Update all (%d)" : "Обновить все (%d)", n),
                                        updatingAll ? "…" : fmt(Lang::en ? "Update (%d)" : "Обновить (%d)", n), updatingAll ? "" : std::to_string(n)};
                    float uw = 0;
                    for (auto& l : labels) {
                        updateAllBtn.text = l;
                        uw = G.width(Lang::t(l), SEMI, 12.5f) + (l.empty() ? 30 : l == labels[0] ? 44 : 38);
                        if (uw <= room) break;
                    }
                    updateAllBtn.at(leftmost - 8 - uw, TB_Y, uw, TB_H);
                    leftmost = updateAllBtn.x;
                }
            }
            {
                Field& f = packsPage ? packSearch : searchField;
                float fw = std::min(220.f, leftmost - 10 - (CX + 250));
                f.visible = f.enabled = fw >= 120;
                f.fontSize = 12.5f;
                f.icon = "search";
                f.label = !remote ? "Поиск в библиотеке" : packsPage ? "Поиск ресурспаков" : "Поиск модов";
                f.at(leftmost - 10 - std::max(fw, 120.f), TB_Y, std::max(fw, 120.f), TB_H);
            }
            std::set<string> ids;
            float y = MODS_TOP;
            if (remote) {
                if (showCategories) y = placeCats(packsPage ? packCats : modCats, y);
                (packsPage ? packCatalogY : catalogY) = y;
                y = placeCatalog(packsPage ? shownPackCatalog() : shownCatalog(), y, ids);
                if (ids.empty()) y += 150;
                else if (moreToCome(packsPage)) y += 48;
            } else if (packsPage) {
                for (auto& pack : shownPacks()) {
                    string k = "pack:" + key(pack.file);
                    Game::Pack copy = pack;
                    rowToggle(k, pack.on, y)->onChange = [this, copy](bool on) { setPack(copy, on); };
                    y += ROW_STEP;
                }
                if (shownPacks().empty()) y += 150;
            } else {
                for (auto& mo : shownMods()) {
                    string k = "mod:" + key(mo.file);
                    auto* toggle = rowToggle(k, mo.on, y);
                    toggle->enabled = !mo.required;
                    Game::Mod copy = mo;
                    toggle->onChange = [this, copy](bool on) { setMod(copy, on); };
                    y += ROW_STEP;
                }
                if (shownMods().empty()) y += 150;
            }
            contentHeight = y - MODS_TOP;
            break;
        }
        case ACCOUNTS:
            if (addMode()) {
                accBack.visible = !savedAccounts().empty();
                accBack.boxy = true;
                accBack.fontSize = 12.5f;
                accBack.at(right - 96, TB_Y, 96, TB_H);
                accCardsY = PAGE_TOP;
                float cw = (CW - 18) / 2, by = accCardsY + ADD_CARD_H - 18 - 40;
                msAdd.at(CX + 18, by, cw - 36, 40);
                msAdd.enabled = !playBusy;
                accNick.at(CX + cw + 18 + 18, by, cw - 36 - 48, 40);
                accNick.fontSize = 13.5f;
                accAdd.at(CX + cw + 18 + cw - 18 - 38, by + 1, 38, 38);
                contentHeight = ADD_CARD_H + 44;
            } else {
                accBack.visible = false;
                addAccountBtn.at(right - 28, TB_Y, 28, TB_H);
                string k = viewKey();
                bool on = k == selectedAccount();
                float y = PAGE_TOP, by = y + HERO_H - 22 - 32;
                heroSelect.at(CX + 22, by, 116, 32);
                heroSelect.primary = !on;
                heroSelect.text = on ? "Выбрано" : "Выбрать";
                heroSelect.enabled = !on && !microsoftBusy && !playBusy;
                heroCopy.at(CX + 22 + 126, by, 32, 32);
                heroRefresh.at(CX + 22 + 166, by, 32, 32);
                heroDelete.at(CX + 22 + 206, by, 32, 32);
                for (IconBtn* b : {&heroCopy, &heroRefresh, &heroDelete}) b->plate = true;
                heroDelete.enabled = heroRefresh.enabled = !microsoftBusy && !playBusy;
                auto list = savedAccounts();
                while (accRows.size() < list.size()) accRows.push_back(std::make_unique<Clickable>("accRow" + std::to_string(accRows.size())));
                while (accRows.size() > list.size()) {
                    if (pressedW == accRows.back().get()) pressedW = nullptr;
                    accRows.pop_back();
                }
                int cols = 4;
                float gw = (CW - (cols - 1) * 12) / cols, gy = y + HERO_H + 18 + SG_HEAD;
                for (size_t i = 0; i < list.size(); i++) {
                    string key2 = list[i];
                    accRows[i]->at(CX + (i % cols) * (gw + 12), gy + (i / cols) * (CHIP_H + 12), gw, CHIP_H);
                    accRows[i]->click = [this, key2] {
                        accView = key2;
                        wake(.6);
                    };
                }
                contentHeight = gy + ((list.size() + cols - 1) / cols) * (CHIP_H + 12) + 8 - PAGE_TOP;
            }
            break;
        case SETTINGS: {
            auto g = settingsGeom();
            float inner = CX + 16, ir = CX + CW - 16;
            arcBtn.text = signedIn() ? "Выйти" : arcBusy ? "Отменить вход" : "Войти";
            arcBtn.primary = !signedIn();
            arcBtn.enabled = !(signedIn() && (gameProc || playBusy));
            float aw = G.width(Lang::t(arcBtn.text), SEMI, 13) + 40;
            arcBtn.at(ir - aw, g.account + 16, aw, 32);
            if (isAdmin() && signedIn()) {
                float ay = g.account + 64 + 10;
                // login | Grant Premium | days | Lifetime | List
                grantLogin.at(inner, ay, 220, 36);
                grantLogin.fontSize = 13.5f;
                float x = inner + 230;
                grantBtn.boxy = lifetimeBtn.boxy = premListBtn.boxy = true;
                grantBtn.fontSize = lifetimeBtn.fontSize = premListBtn.fontSize = 12.5f;
                float gw = G.width(Lang::t(grantBtn.text), SEMI, 12.5f) + 44;
                grantBtn.at(x, ay + 2, gw, 32);
                x += gw + 8;
                grantDays.at(x, ay, 92, 36);
                grantDays.fontSize = 13.5f;
                x += 92 + 8;
                float lw = G.width(Lang::t(lifetimeBtn.text), SEMI, 12.5f) + 50;
                lifetimeBtn.at(x, ay + 2, lw, 32);
                x += lw + 8;
                premListBtn.at(x, ay + 2, G.width(Lang::t(premListBtn.text), SEMI, 12.5f) + 50, 32);
                grantBtn.busy = grantBusy;
                lifetimeBtn.enabled = !grantBusy;
                // the list: a row per account; the open one carries its buttons
                float ly = g.account + 64 + grantRowsH() + 10;
                while (premRowW.size() < premRows.size()) premRowW.push_back(std::make_unique<Clickable>("premRow" + std::to_string(premRowW.size())));
                for (size_t i = 0; i < premRowW.size(); i++) {
                    auto& r = *premRowW[i];
                    bool has = premOpen && i < premRows.size();
                    r.visible = r.enabled = has;
                    r.at(inner - 6, ly + i * PREM_ROW, CW - 20, PREM_ROW - 4);
                    if (has) {
                        string login = premRows[i].login;
                        r.click = [this, login] {
                            premSel = premSel == login ? string() : login;
                            premDays.set("");
                            premDays.error = false;
                            wake(.4);
                        };
                    }
                }
                int open = -1;
                for (size_t i = 0; i < premRows.size(); i++)
                    if (premOpen && premRows[i].login == premSel) open = (int)i;
                float bx = ir - 4, by = ly + open * PREM_ROW + (PREM_ROW - 4 - 30) / 2;
                for (int i = 2; i >= 0; i--) {
                    Btn& b = premAct[i];
                    b.primary = i == 0;
                    float bw = G.width(Lang::t(b.text), SEMI, 12.5f) + (b.icon.empty() ? 30 : 50);
                    bx -= bw;
                    b.visible = open >= 0;
                    b.enabled = open >= 0 && !grantBusy;
                    if (open >= 0) b.at(bx, by, bw, 30);
                    bx -= 8;
                }
                premDays.visible = premDays.enabled = open >= 0;  // the days, left of "Give days"
                premDays.fontSize = 13;
                if (open >= 0) premDays.at(bx - 80, by, 80, 30);
            }
            {  // the wallpapers: four thumbnails
                float tw = (CW - 32 - 3 * 14) / 4, th = tw * THUMB_H / THUMB_W;
                for (int i = 0; i < Wallpaper::COUNT; i++) wpThumb[i].at(inner + i * (tw + 14), g.look + 38, tw, th + 22);
            }
            float row = g.look + SG_WALL;
            menuScale.at(CX + 150, row + (SG_ROW - 28) / 2, CW - 150 - 16 - 96 - 56, 28);
            resetScale.boxy = true;
            resetScale.fontSize = 12.5f;
            resetScale.at(ir - 96, row + (SG_ROW - 28) / 2, 96, 28);
            langSetting.at(ir - 112, row + SG_ROW + (SG_ROW - 28) / 2, 112, 28);
            Toggle* rows[2] = {&soundsToggle, &startupToggle};
            for (int i = 0; i < 2; i++) {
                rows[i]->row = true;
                rows[i]->rippleR = 0;
                rows[i]->at(CX, g.sound + SG_ROW * i, CW - 6, SG_ROW);
            }
            for (Btn* b : {&autoRamBtn, &gameDirBtn, &modsDirBtn, &moveBtn, &defaultBtn, &updateBtn}) {
                b->fontSize = 12.5f;
                b->boxy = true;
            }
            autoRamBtn.at(ir - 72, g.game + (SG_ROW - 28) / 2, 72, 28);
            autoRamBtn.enabled = Config::ramMb > 0;
            memory.at(CX + 8, g.game + SG_ROW + 2, CW - 16, 28);
            cleanToggle.row = true;
            cleanToggle.rippleR = 0;
            cleanToggle.at(CX, g.game + 2 * SG_ROW + 8, CW - 6, SG_ROW);
            jvmField.at(CX + 230, g.game + 3 * SG_ROW + 8 + (SG_ROW - 32) / 2, CW - 230 - 12, 32);
            float bx = inner, by = g.files + 50;
            defaultBtn.visible = defaultBtn.enabled = !Config::gameDir.empty();
            for (Btn* b : {&gameDirBtn, &modsDirBtn, &moveBtn, &defaultBtn}) {
                if (!b->visible) continue;
                float bw = G.width(Lang::t(b->text), SEMI, 12.5f) + 36;
                b->at(bx, by, bw, 30);
                bx += bw + 8;
            }
            float uw = G.width(Lang::t(updateBtn.text), SEMI, 12.5f) + 40;
            updateBtn.at(ir - uw, g.about + 16, uw, 30);
            bx = inner;
            for (Btn* b : {&discordLink, &siteLink, &logBtn}) {
                float bw = G.width(Lang::t(b->text), SEMI, 13) + (b->icon.empty() ? 36 : 56);
                b->at(bx, g.about + 66, bw, 30);
                bx += bw + 8;
            }
            contentHeight = g.end - PAGE_TOP + 20;
            break;
        }
        }
        float mx = std::max(0.f, contentHeight - (WIN_H - listTop() - 16));
        scroll[page] = std::max(0.f, std::min(mx, scroll[page]));
    }    bool inPage(float x, float y) const { return loggedIn() ? x > SIDE && y > TOP : y > 48; }
    Widget* pick(float x, float y) {
        if(sourcesOpen) {
            placeSources();
            for(int i=0;i<sourceCount();++i) if(sourceOptions[i].hit(x,y))return &sourceOptions[i];
            if(sourcesBtn.hit(unpageX(x),unpageY(y)))return &sourcesBtn;
            if(inSources(x,y))return nullptr;
        }
        if (Config::startupAnimation && splashFor > 0 && startupClock() < splashFor + SPLASH_FADE) {
            if (minBtn.hit(x, y)) return &minBtn;
            if (closeBtn.hit(x, y)) return &closeBtn;
            return nullptr;
        }
        if (modal() && (loggedIn() || errOpen)) {  // the dialog takes the input; only the window buttons stay live
            if (minBtn.hit(x, y)) return &minBtn;
            if (closeBtn.hit(x, y)) return &closeBtn;
            for (auto* w : pageWidgets())
                if (w->hit(x, y)) return w;
            return nullptr;
        }
        for (auto* w : chrome)
            if (w->hit(x, y)) return w;
        for (auto* w : nav)
            if (w->hit(x, y)) return w;
        if (inPage(x, y)) {
            x = unpageX(x); y = unpageY(y);
            float sy = scrollY();
            for (auto* w : pageWidgets())
                if ((fixed(w) || page == HOME || y >= listTop() - 8) && w->hit(x, y + (fixed(w) ? 0 : sy))) return w;
        }
        return nullptr;
    }
    bool isChrome(Widget* w) {
        for(auto& option:sourceOptions)if(w==&option)return true;
        for (auto* c : chrome)
            if (c == w) return true;
        for (auto* c : nav)
            if (c == w) return true;
        return false;
    }
    float localX(Widget* w, float x) { return isChrome(w) || modal() || !loggedIn() ? x : unpageX(x); }
    float localY(Widget* w, float y) {
        if (!isChrome(w) && !modal() && loggedIn()) y = unpageY(y);
        return isChrome(w) || fixed(w) ? y : y + scrollY();
    }

    // One step of time; returns true if a frame was drawn
    bool tick(double dt) {
        dt = std::max(dt, 0.0);
        T += std::min(dt, .25);
        dt = std::min(dt, .05);
        an.dt = (float)dt;
        an.frame++;
        if (advanceUiScale(dt)) dirty = true;
        if (saveAt > 0 && T >= saveAt) {
            saveAt = 0;
            Config::save();
        }
        // the folders are looked at again every few seconds, only while a page shows their contents
        bool filesShown = loggedIn() && (page == MODS || page == PACKS || page == HOME);
        if (!DESIGN_PREVIEW && filesShown && T - scannedAt > 4 && !scanning) {
            scannedAt = T;
            scan();
        }
        if (loggedIn() && page == HOME && !DESIGN_PREVIEW) pollOnline();
        if (!DESIGN_PREVIEW && page == MODS && !modsRemote && loggedIn() && !scanning) checkModUpdates();
        if (justUpdated && !continuedAfterUpdate && cur == LOGIN && !splash() && !DESIGN_PREVIEW) {
            continuedAfterUpdate = true;
            Log::info("updated: going on without a second click");
            submit();
        }
        if (!DESIGN_PREVIEW && page == HOME && loggedIn()) {
            loadPopular(false);
            loadPopular(true);
        }
        if (!DESIGN_PREVIEW && modsRemote && page == MODS && loggedIn() && (!catalogBusy || modInFlight != modKey())) {
            string q = trim(searchField.value());
            if ((!catalogLoaded || modKey() != catalogFor) && (q.empty() || T - typedAt > .20)) loadCatalog();
        }
        if (!DESIGN_PREVIEW && packsRemote && page == PACKS && loggedIn() && (!packBusy || packInFlight != packKey())) {
            string q = trim(packSearch.value());
            if ((!packLoaded || packKey() != packCatalogFor) && (q.empty() || T - packTypedAt > .20)) loadPacks();
        }
        if (!DESIGN_PREVIEW && loggedIn() && (page == MODS || page == PACKS) && (page == MODS ? modsRemote : packsRemote)) {
            bool packsP = page == PACKS;
            float mx = contentHeight - (WIN_H - listTop() - 16);
            if (moreToCome(packsP) && mx - scroll[page] < 520) loadMore(packsP);
        }
        place();
        bool changed = false;
        if(sourcesOpen) {
            placeSources();
            for(int i=0;i<sourceCount();++i) changed|=hover(&sourceOptions[i],sourceOptions[i].hit(mouseX,mouseY));
        }
        for (auto* w : chrome) changed |= hover(w, w->hit(mouseX, mouseY));
        for (auto* w : nav) changed |= hover(w, w->hit(mouseX, mouseY));
        float sy = scrollY();
        bool in = inPage(mouseX, mouseY);
        for (auto* w : pageWidgets())
            changed |= hover(w, in && visibleAt(w) && w->hit(localX(w, mouseX), localY(w, mouseY)));
        double age = T - stepAt;
        float target = step == 0   ? (float)(.02 + .06 * (1 - exp(-age * 1.4)))
                       : step == 1 ? (dlTotal > 0 ? .08f + .84f * std::min(1.f, dlDone / (float)dlTotal)
                                                  : (float)(.08 + .5 * (1 - exp(-age * .25))))
                       : step == 2 ? (float)(.92 + .07 * (1 - exp(-age * 2.5)))
                                   : 1;
        shown = an.to("prog", target, step == 1 ? 8.f : 4.f);
        an.to("prep", prepFrac, 8);
        an.to("scroll:" + std::to_string(page), scroll[page], 18);
        bool busyInstall = false;
        for (auto& s : installState)
            if (s.second == "busy") busyInstall = true;
        bool spinning = cur == WORKING || preparing || loginBtn.busy ||
                        ((page == MODS || page == PACKS) && (catalogBusy || packBusy || busyInstall)) || shotLoading();
        bool intro = Config::startupAnimation && startupClock() < splashFor + SPLASH_FADE + .1;
        int caret = -1;
        if (auto* f = dynamic_cast<Field*>(focusW)) caret = (T - f->focusAt < .6 || fmod(T * 1.7, 1) < .58) ? 1 : 0;
        bool blink = caret != caretPhase;
        caretPhase = caret;
        // A frame is drawn only when something moves: a hover, a transition, a spinner, the banner sliding in or out
        double toastAge = T - toastAt;
        bool toastMoving = toastAge < .32 || (toastAge > toastFor - .4 && toastAge < toastFor + .05);
        bool liveDot = playBtn.running && page == HOME && T - ambientAt >= 1 / 15.0;
        bool animating = dirty || changed || spinning || intro || an.moving || toastMoving || T < hotUntil;
        if (!(animating || blink || liveDot)) return false;
        if (animating) paintedAt = T;
        ambientAt = T;
        dirty = false;
        an.moving = false;
        if (paint()) return true;
        dirty = true;  // the frame was lost with the device: draw it again
        return false;
    }

    // ============================================================================================ painting
    bool paintingPage = false;
    float previewPageTime = -1;
    static constexpr float PAGE_REVEAL_SECONDS = .30f;
    float pageProgress() const {
        float age = DESIGN_PREVIEW && previewPageTime >= 0 ? previewPageTime : (float)(T - pageAt);
        return clamp01(age / PAGE_REVEAL_SECONDS);
    }
    float pageShift() const { return 0; }
    float pageDepthScale() const {
        if (!loggedIn()) return 1;
        float rest = 1 - pageProgress();
        return 1 - .03f * rest * rest * rest;
    }
    float pageX(float x) const { return (x - (SIDE + WIN_W) / 2) * pageDepthScale() + (SIDE + WIN_W) / 2; }
    float pageY(float y) const { return (y - WIN_H / 2) * pageDepthScale() + WIN_H / 2; }
    float unpageX(float x) const { return (x - (SIDE + WIN_W) / 2) / pageDepthScale() + (SIDE + WIN_W) / 2; }
    float unpageY(float y) const { return (y - WIN_H / 2) / pageDepthScale() + WIN_H / 2; }
    float pageOpacity() const {
        float t = pageProgress();
        float rest = 1 - t;
        return 1 - rest * rest * rest * rest;
    }
    float enter(int i) { if (paintingPage) return 1; return Anim::out((float)((T - pageAt - std::min(i, 4) * .018) / .22)); }
    // Runs body shifted and faded the way block number i of the page arrives
    template <class F> void block(int i, F body) {
        float e = enter(i);
        if (e <= 0) return;
        D2D1_MATRIX_3X2_F old;
        G.rt->GetTransform(&old);
        G.rt->SetTransform(D2D1::Matrix3x2F::Translation(roundf((1 - e) * 14 * pageDirection), 0) * old);
        body(e);
        G.rt->SetTransform(old);
    }
    static void card(float x, float y, float w, float h, float a) {
        G.rr(x, y, w, h, 14, glassOf(fade(CARD, a)));
        G.rrLine(x + .5f, y + .5f, w - 1, h - 1, 13.5f, fade(alpha(WHITE, .045f), a));  // the glass edge
    }
    static void iconTile(const char* icon, float x, float y, float s, float a) {
        G.rr(x, y, s, s, 10, fade(CARD3, a));
        G.icon(icon, x + s / 2, y + s / 2, s * .5f, fade(ACCENT2, a), 1.7f);
    }
    static void chip(float x, float y, float w, float h, float a) { G.rr(x, y, w, h, h / 2, fade(CHIP, a)); }
    static void badge(const string& text, float right, float cy, bool accent, float a) {
        float bw = G.width(text, SEMI, 11.5f) + 20, bx = right - bw;
        G.rr(bx, cy - 12, bw, 24, 12, fade(accent ? alpha(ACCENT, .2f) : alpha(WHITE, .06f), a));
        G.center(text, bx + bw / 2, Gfx::mid(cy, 11.5f), SEMI, 11.5f, fade(accent ? ACCENT2 : MUTED, a));
    }
    // "5 модов" / "5 mods": a count with the word in the right form for the language
    static string plural(int n, const char* one, const char* few, const char* many, const char* en1, const char* enN) {
        if (Lang::en) return std::to_string(n) + " " + (n == 1 ? en1 : enN);
        int d = n % 10, h = n % 100;
        return std::to_string(n) + " " + (d == 1 && h != 11 ? one : d >= 2 && d <= 4 && (h < 12 || h > 14) ? few : many);
    }
    string pageMeta() {
        auto on = [](const auto& list) { int n = 0; for (auto& x : list) n += x.on ? 1 : 0; return n; };
        string dot = "  ·  ";
        switch (page) {
        case MODS:
            if (modsRemote)
                return string(Config::modSources == 1 ? "Modrinth" : Config::modSources == 2 ? "CurseForge" : "Modrinth + CurseForge") + dot +
                       "Fabric " + Config::mcVersion;
            return mods.empty() ? Lang::t("Модов пока нет")
                                : plural((int)mods.size(), "мод", "мода", "модов", "mod", "mods") + dot +
                                      plural(on(mods), "включён", "включено", "включено", "enabled", "enabled") +
                                      (modUpdates.empty() ? string() : dot + plural((int)modUpdates.size(), "обновление", "обновления", "обновлений", "update", "updates"));
        case PACKS:
            if (packsRemote)
                return string(Config::packSources == 1 ? "PacksMC" : Config::packSources == 2 ? "Modrinth" : "PacksMC + Modrinth") + dot +
                       "Minecraft " + Config::mcVersion;
            return packs.empty() ? Lang::t("Ресурспаков нет")
                                 : plural((int)packs.size(), "пак", "пака", "паков", "pack", "packs") + dot +
                                       plural(on(packs), "включён", "включено", "включено", "enabled", "enabled");
        case ACCOUNTS:
            return addMode() ? "" : plural((int)savedAccounts().size(), "аккаунт", "аккаунта", "аккаунтов", "account", "accounts");
        default: return "";
        }
    }
    void bar(float x, float y, float w, float f, float a) {
        G.rr(x, y, w, 6, 3, fade(rgba(255, 255, 255, 30), a));
        G.rrGrad(x, y, std::max(6.f, w * clamp01(f)), 6, 3, fade(ACCENT_HI, a), fade(LILAC, a), x, 0, x + w, 0);
    }
    static float frac(float v) { return v - floorf(v); }

    static float smooth(float a, float b, float v) {
        float t = clamp01((v - a) / (b - a));
        return t * t * (3 - 2 * t);
    }
    void langSwitch() { segmented(langBtn, 1); }  // the same EN | RU control as in Settings
    string detail() {
        if (step == 0) return "Вход на сервер";
        if (step >= 2) return dlTotal > 0 ? Installer::mb(dlTotal) + " получено" : "Клиент получен";
        if (dlTotal <= 0) return "Получаем данные…";
        string s = Installer::mb(dlDone) + " / " + Installer::mb(dlTotal);
        if (dlSpeed > 1000) s += " · " + fmt("%.1f МБ/с", dlSpeed / 1048576.0);
        return s;
    }

    // The window's lights, top left: red closes, yellow hides, green fills the screen (and back), as on a Mac
    void trafficLights() {
        float hc = an.to("tl.close", closeBtn.hovered ? 1.f : 0.f, 20), hm = an.to("tl.min", minBtn.hovered ? 1.f : 0.f, 20),
              hg = an.to("tl.green", greenBtn.hovered ? 1.f : 0.f, 20);
        bool pointerNear = mouseX >= 12 && mouseX < 84 && mouseY >= 12 && mouseY < 44;
        float cy = closeBtn.y + 10;
        struct Light { Widget& w; unsigned base, lit, dark; float hv; };
        Light lights[3] = {{closeBtn, 0xFF5F57, 0xFF7B74, 0xBF4842, hc}, {minBtn, 0xFEBC2E, 0xFFCD5A, 0xBF8D22, hm}, {greenBtn, 0x28C840, 0x46D95B, 0x1E9630, hg}};
        for (auto& l : lights) {
            float cx = l.w.x + 10;
            G.circle(cx, cy, 6, mix(mix(rgb(l.base), rgb(l.lit), l.hv), rgb(l.dark), l.w.pressed ? .6f : 0.f));
            G.ring(cx, cy, 6, rgba(0, 0, 0, 46), .6f);
        }
        if (pointerNear) {  // the glyphs show when the pointer comes near the group
            G.icon("x", closeBtn.x + 10, cy, 9, rgba(80, 10, 10, 210), 2.3f);
            G.icon("min", minBtn.x + 10, cy, 9, rgba(90, 60, 0, 210), 2.3f);
            float gx = greenBtn.x + 10;  // a small sparkle: something is hidden behind this one
            Gfx::Path star(G.d2d);
            star.M(gx, cy - 3.6f).L(gx + 1, cy - 1).L(gx + 3.6f, cy).L(gx + 1, cy + 1).L(gx, cy + 3.6f).L(gx - 1, cy + 1).L(gx - 3.6f, cy).L(gx - 1, cy - 1).Z();
            G.fill(star, rgba(0, 60, 10, 220));
        }
    }
    // ---- the easter egg behind the green light
    static constexpr float EGG_LEN = 3.6f;
    double eggAt = -100;
    bool eggBite = false;
    bool eggOn() const { return T - eggAt < EGG_LEN; }
    void easterEgg() {
        float t = (float)(T - eggAt);
        if (t < 0 || t >= EGG_LEN) return;
        an.moving = true;
        auto ease = [](float v) { float x = clamp01(v), r = 1 - x; return 1 - r * r * r; };
        float k = ease(t / .25f) * (1 - ease((t - (EGG_LEN - .45f)) / .45f));
        G.rect(0, 0, WIN_W, WIN_H, rgba(0, 0, 0, (int)lroundf(150 * k)));
        static const char* SPRITE[12] = {
            "......s.....", ".....s.ll...", "..rrrsrrll..", ".rhrrrrrrrr.", "rhhrrrrrrrrd", "rhrrrrrrrrrd",
            "rrrrrrrrrrrd", "rrrrrrrrrrdd", ".rrrrrrrrdd.", ".rrrrrrrrdd.", "..rrrrrrdd..", "...rr..rd..."};
        const float P = 10, cx = WIN_W / 2, rest = WIN_H / 2 - 92;
        // the fall (gravity), then two bounces that die away, and a squash on each landing
        float y, squash = 1;
        if (t < .5f) y = -160 + (rest + 160) * (t / .5f) * (t / .5f);
        else {
            float b = t - .5f;
            y = rest - fabsf(sinf(b * 8.5f)) * 34 * expf(-b * 5.f);
            squash = 1 + .14f * expf(-b * 8) * cosf(b * 17);
        }
        float gold = ease((t - 1.3f) / .55f);
        bool bitten = t >= 1.0f;
        if (bitten && !eggBite) {
            eggBite = true;
            UiAudio::play(UiAudio::CLICK);
        }
        auto tint = [&](char c) {
            Col red = c == 'h' ? rgb(0xFF8A80) : c == 'd' ? rgb(0xA31919) : c == 's' ? rgb(0x6D4C41) : c == 'l' ? rgb(0x43A047) : rgb(0xE53935);
            Col gld = c == 'h' ? rgb(0xFFF6C2) : c == 'd' ? rgb(0xC48A12) : c == 's' ? rgb(0x8D6E63) : c == 'l' ? rgb(0x8BC34A) : rgb(0xF7C948);
            return mix(red, gld, gold);
        };
        float w = 12 * P, h = 12 * P * (2 - squash), x0 = cx - w * squash / 2, top = y + 12 * P - h, cw = P * squash, ch = h / 12;
        G.glow(x0, rest + 12 * P - 6, w * squash, 14, 7, alpha(rgb(0), .35f * k), 10);  // its shadow on the "floor"
        float band = (t - 1.55f) * 26;  // the enchanted shimmer runs across, corner to corner
        for (int r = 0; r < 12; r++)
            for (int c = 0; c < 12; c++) {
                char px = SPRITE[r][c];
                if (px == '.') continue;
                float bx = c - 12.f, by = r - 5.f;
                if (bitten && bx * bx + by * by < 9.6f) continue;  // the bite: a clean half circle out of the right side
                Col col = tint(px);
                if (gold > .01f) {
                    float d = fabsf((c + r) - band);
                    if (d < 2.5f) col = mix(col, rgb(0xFFFFFF), .55f * (1 - d / 2.5f) * gold);
                    col = mix(col, rgb(0xC9A2FF), .12f * gold * (.5f + .5f * sinf(t * 6 + c * .7f + r * .4f)));
                }
                G.rect(x0 + c * cw, top + r * ch, cw + .5f, ch + .5f, fade(col, k));
            }
        if (bitten && t < 2.1f) {  // the crumbs of the bite
            float age = t - 1.0f;
            for (int i = 0; i < 9; i++) {
                float ang = -1.2f + i * .32f, sp = 70 + 30 * (i % 3);
                float px = cx + 4.5f * P + cosf(ang) * sp * age, py = rest + 4 * P + sinf(ang) * sp * age + 160 * age * age;
                G.rect(px, py, 5, 5, fade(alpha(tint(i % 2 ? 'r' : 'h'), 1 - age / 1.1f), k));
            }
        }
        if (gold > .01f)  // sparkles round the golden apple
            for (int i = 0; i < 5; i++) {
                float ph = t * 3 + i * 1.3f, s = (.5f + .5f * sinf(ph)) * 7 * gold;
                float sx = cx + cosf(i * 1.26f + 0.4f) * 92, sy = rest + 6 * P + sinf(i * 1.26f + .4f) * 76;
                G.line(sx - s, sy, sx + s, sy, fade(alpha(rgb(0xFFF3B0), .9f), k), 1.6f);
                G.line(sx, sy - s, sx, sy + s, fade(alpha(rgb(0xFFF3B0), .9f), k), 1.6f);
            }
        float words = ease((t - 1.75f) / .45f) * k;
        if (words > .01f) {
            G.headline = true;
            G.center(Lang::t("Золотое яблоко найдено!"), cx, rest + 12 * P + 50, BOLD, 21, fade(WHITE, words));
            G.headline = false;
            G.center(Lang::t("Arcanic × macOS — спасибо, что играешь с нами"), cx, rest + 12 * P + 74, MEDIUM, 13, fade(rgb(0xD1D1D6), words));
        }
    }
    // The app's icon: a rounded square in system blue fading to violet, the A of the wordmark in white
    void appIcon(float x, float y, float s, float a) {
        G.glow(x, y + s * .06f, s, s, s * .23f, alpha(rgb(0), .30f * a), s * .12f);
        G.rrGrad(x, y, s, s, s * .23f, fade(rgb(0x3E8BFF), a), fade(rgb(0x7B4DFF), a), x, y, x + s, y + s);
        G.rrGrad(x, y, s, s * .5f, s * .23f, fade(alpha(WHITE, .22f), a), alpha(WHITE, 0), 0, y, 0, y + s * .5f);
        D2D1_MATRIX_3X2_F old;
        G.rt->GetTransform(&old);
        float k = s / 44;  // the A of the startup wordmark, on a 20 x 28 grid
        G.rt->SetTransform(D2D1::Matrix3x2F::Scale(k, k) * D2D1::Matrix3x2F::Translation(x + 12 * k, y + 8 * k) * old);
        Gfx::Path A(G.d2d);
        A.M(0, 28).L(0, 8).L(8, 0).L(20, 0).L(20, 28).M(0, 15).L(20, 15);
        if (auto* g = A.done()) G.rt->DrawGeometry(g, G.use(fade(WHITE, a)), 3.2f, G.sharp);
        G.rt->SetTransform(old);
    }
    // The login screen: the wallpaper, and one glass card in the middle (as the newest macOS greets you)
    void loginScreen() {
        float a = enter(0);
        if (ID2D1Bitmap* bg = background(true)) G.rt->DrawBitmap(bg, D2D1::RectF(0, 0, WIN_W, WIN_H), 1, D2D1_BITMAP_INTERPOLATION_MODE_NEAREST_NEIGHBOR);
        else G.rrGrad(0, 0, WIN_W, WIN_H, 0, rgb(0x15123A), rgb(0x07061A), 0, 0, 0, WIN_H);
        const float cw = 300, ch = 290, cx0 = (WIN_W - cw) / 2, cy0 = 156;
        G.glow(cx0, cy0 + 10, cw, ch, 22, alpha(rgb(0), .35f * a), 26);
        G.glass(cx0, cy0, cw, ch, 22, rgba(26, 26, 32, 130), a);
        float cx = WIN_W / 2;
        appIcon(cx - 32, cy0 + 26, 64, a);
        G.headline = true;
        G.center("Arcanic", cx, cy0 + 126, BOLD, 22, fade(WHITE, a));
        G.headline = false;
        int ln = 0;
        for (auto& l : wrap(Lang::t("Клиент, моды, ресурспаки и косметика Arcanic в одном месте."), MEDIUM, 12.5f, cw - 56, 2))
            G.center(l, cx, cy0 + 150 + 18 * ln++, MEDIUM, 12.5f, fade(rgb(0xC7C7CC), a));
        if (cur == WORKING) {
            float cn = clamp01((float)((T - captionAt) / .25)), y0 = loginBtn.y + 12;
            if (cn < 1 && !prevCaption.empty()) G.center(prevCaption, cx, y0 - 5 * cn, SEMI, 13.5f, fade(TEXT, a * (1 - cn)));
            G.center(caption, cx, y0 + 5 * (1 - cn), SEMI, 13.5f, fade(TEXT, a * cn));
            bar(loginBtn.x, y0 + 18, loginBtn.w, shown, a);
            G.text(detail(), loginBtn.x, y0 + 42, MEDIUM, 11.5f, fade(MUTED, a));
            G.right(std::to_string((int)lroundf(shown * 100)) + "%", loginBtn.x + loginBtn.w, y0 + 42, SEMI, 11.5f, fade(TEXT, a));
        } else {
            loginBtn.text = "Продолжить";
            discordBtn.text = "Discord";
            discordBtn.icon = "discord";
            loginBtn.paint(a);
            discordBtn.paint(a);
            float ek = an.to("err", !errMsg.empty() ? 1 : 0, 11);
            if (ek > .01f)
                G.center(G.fit(!errMsg.empty() ? errMsg : lastErr, MEDIUM, 12, cw - 30), cx, cy0 + ch + 26, MEDIUM, 12, fade(rgb(0xFF8A80), a * ek));
        }
        G.raw = true;
        G.text(string("v") + Config::VERSION + "  ·  " + (Lang::en ? "by " : "авторы: ") + replaceAll(credit(), " & ", Lang::en ? " & " : " и "), 22,
               WIN_H - 20, MEDIUM, 11, fade(rgba(255, 255, 255, 140), a));
        G.raw = false;
        G.right("arcanic.su", WIN_W - 22, WIN_H - 20, MEDIUM, 11, fade(rgba(255, 255, 255, 140), a));
    }
    // The sidebar (its glass is part of the background): lights, search, pages, versions, Settings, the game account
    void rail() {
        trafficLights();
        sbSearch.paint(1);
        auto rowBg = [&](Widget& w, bool active, const string& key) {
            float h = an.to(key + ".h", w.hovered ? 1.f : 0.f, 24), lit = an.to(key + ".a", active ? 1.f : 0.f, 18);
            if (lit > .01f || h > .01f) G.rr(w.x, w.y, w.w, w.h, 7, alpha(WHITE, .05f * h + .11f * lit));
            ripple(w, w.x, w.y, w.w, w.h, 7, 1, WHITE);
        };
        for (size_t i = 0; i < nav.size(); i++) {
            auto* item = nav[i];
            bool remotePage = (page == MODS && modsRemote) || (page == PACKS && packsRemote);
            bool active = (i == 0 && page == HOME) || (i == 1 && (page == MODS || page == PACKS) && remotePage) ||
                          (i == 2 && (page == MODS || page == PACKS) && !remotePage) || (i == 3 && page == SETTINGS);
            rowBg(*item, active, "nav" + std::to_string(i));
            G.icon(item->icon.c_str(), item->x + 16, item->y + item->h / 2, 15, ACCENT, 1.8f);  // macOS tints sidebar icons
            G.text(item->tag, item->x + 34, Gfx::mid(item->y + item->h / 2, 13), MEDIUM, 13, TEXT);
        }
        G.text("Версии", 24, 192, SEMI, 11, rgba(255, 255, 255, 110));
        auto vl = versionList();
        for (size_t i = 0; i < favRows.size(); i++) {
            Clickable& r = *favRows[i];
            if (!r.visible || i >= vl.size()) continue;
            string id = vl[i].first;
            bool on = id == Config::mcVersion;
            rowBg(r, on, r.id);
            float hv = an.to(r.id + ".h", r.hovered ? 1.f : 0.f, 24);
            artTile((int)i, r.x + 6, r.y + 5, 28, 28, 0, 0, 0, rgba(0, 0, 0, 0), 1, false, 7);
            versionIcon(r.x + 11, r.y + 10, 18, 1);
            G.raw = true;
            G.text("Fabric " + id, r.x + 42, r.y + 17, SEMI, 12.5f, on ? TEXT : MUTED);
            G.raw = false;
            string sub = !on ? "Выбрать" : gameProc ? "Игра запущена" : preparing ? caption : "Готово к запуску";
            G.text(G.fit(sub, MEDIUM, 11, r.w - 84), r.x + 42, r.y + 31, MEDIUM, 11, gameProc && on ? OK : MUTED);
            if (on && !gameProc && !playBusy) {  // quick launch: the round play button on the chosen version
                float px = r.x + r.w - 20, py = r.y + r.h / 2, k = .55f + .45f * hv;
                G.circle(px, py, 11, alpha(ACTION, k));
                G.icon("play", px + 1, py, 10, WHITE);
            }
            if (on && preparing) bar(r.x + 42, r.y + r.h - 3, r.w - 54, an.get("prep", 0), 1);
        }
        G.rect(SB_X + 14, SB_Y + SB_H - 100, SB_W - 28, 1, SEPARATOR);
        {  // the game account at the foot: its face, its name and its kind
            Clickable& r = accountRow;
            rowBg(r, page == ACCOUNTS, "acct");
            string key2 = selectedAccount(), nm = accountName(key2);
            bool known = !savedAccounts().empty() && !(licensedKey(key2) && nm == "Microsoft");
            if (known) {
                head(nm, r.x + 8, r.y + 7, 30, 1);
                G.raw = true;
                G.text(G.fit(nm, SEMI, 13, r.w - 70), r.x + 46, r.y + 20, SEMI, 13, TEXT);
                G.raw = false;
                G.text(licensedKey(key2) ? "Microsoft" : "Локальный профиль", r.x + 46, r.y + 35, MEDIUM, 11, MUTED);
            } else {  // nobody yet: the row asks for an account
                G.circle(r.x + 23, r.y + 22, 15, alpha(WHITE, .10f));
                G.icon("plus", r.x + 23, r.y + 22, 13, rgb(0xD1D1D6), 1.8f);
                G.text("Добавить аккаунт", r.x + 46, r.y + 20, SEMI, 13, TEXT);
                G.text("Microsoft или ник", r.x + 46, r.y + 35, MEDIUM, 11, MUTED);
            }
            G.icon("chevrons", r.x + r.w - 14, r.y + r.h / 2, 12, rgba(255, 255, 255, 120), 1.6f);
        }
    }
    // Two or three words as a macOS segmented control; `active` is the lit one
    void segmentedRow(Clickable* items, int n, int active, float a) {
        float x0 = items[0].x - 2, x1 = items[n - 1].x + items[n - 1].w + 2, y = items[0].y - 2, h = items[0].h + 4;
        G.rr(x0, y, x1 - x0, h, h / 2, fade(alpha(WHITE, .09f), a));
        for (int i = 0; i < n; i++) {
            Clickable& c = items[i];
            float hv = an.to(c.id + ".h", c.hovered ? 1.f : 0.f, 20), on = an.to(c.id + ".on", i == active ? 1.f : 0.f, 20);
            if (on > .01f) {
                G.rr(c.x, c.y + 1, c.w, c.h, c.h / 2, fade(rgba(0, 0, 0, 50), a * on));
                G.rr(c.x, c.y, c.w, c.h, c.h / 2, fade(alpha(WHITE, .22f), a * on));
            }
            G.center(c.tag, c.x + c.w / 2, Gfx::mid(c.y + c.h / 2, 12.5f), SEMI, 12.5f, fade(mix(mix(rgb(0xAEAEB2), TEXT, hv), WHITE, on), a));
        }
    }
    // The toolbar: the arrows, the page's name and a line of facts, then the page's own controls on the right
    void toolbar() {
        float a = 1;
        {  // back and forward, one capsule
            G.rr(navBack.x, navBack.y, navBack.w + navFwd.w, navBack.h, navBack.h / 2, alpha(WHITE, .08f));
            for (IconBtn* b : {&navBack, &navFwd}) {
                float hv = an.to(b->id + ".h", b->hovered && b->enabled ? 1.f : 0.f, 20);
                if (hv > .01f) G.rr(b->x, b->y, b->w, b->h, b->h / 2, alpha(WHITE, .08f * hv));
                ripple(*b, b->x, b->y, b->w, b->h, b->h / 2, 1, WHITE);
                G.icon(b == &navBack ? "back" : "fwd", b->x + b->w / 2, b->y + b->h / 2, 14, b->enabled ? mix(rgb(0xD1D1D6), WHITE, hv) : rgba(255, 255, 255, 60), 1.8f);
            }
            G.rect(navBack.x + navBack.w, navBack.y + 7, 1, navBack.h - 14, SEPARATOR);
        }
        string title = page == HOME ? "Играть" : page == SETTINGS ? "Настройки" : page == ACCOUNTS ? (addMode() ? "Добавить аккаунт" : "Аккаунты")
                       : ((page == MODS ? modsRemote : packsRemote) ? "Каталог" : "Библиотека");
        float tx = navFwd.x + navFwd.w + 14;
        G.headline = true;
        G.text(title, tx, TB_Y + 19, BOLD, 15, fade(WHITE, a));
        G.headline = false;
        string meta = pageMeta();
        if (!meta.empty()) {
            float tw = G.width(Lang::t(title), BOLD, 15), mx = tx + tw + 10, limit = WIN_W - 22;
            for (Widget* w : pageWidgets())  // the page's own controls in the toolbar: the facts stop short of them
                if (w->visible && fixed(w) && w->y < PAGE_TOP && w->x > mx) limit = std::min(limit, w->x);
            float room = std::min(260.f, limit - 14 - mx);
            if (room > 40) {
                G.raw = true;
                G.text(G.fit(meta, MEDIUM, 12, room), mx, TB_Y + 19, MEDIUM, 12, fade(MUTED, a));
                G.raw = false;
            }
        }
        if (page == MODS || page == PACKS) {
            Field& search = page == PACKS ? packSearch : searchField;
            if (search.visible) search.paint(a);
            segmentedRow(kindTab, 2, page == MODS ? 0 : 1, a);
            bool remote = page == MODS ? modsRemote : packsRemote;
            if (remote) {
                float on = an.to("cat.on", showCategories ? 1.f : 0.f, 18), hv = an.to("cat.h", categoryToggle.hovered ? 1.f : 0.f, 20);
                G.rr(categoryToggle.x, categoryToggle.y, categoryToggle.w, categoryToggle.h, categoryToggle.h / 2,
                     mix(alpha(WHITE, .08f + .06f * hv), alpha(ACTION, .85f), on));
                ripple(categoryToggle, categoryToggle.x, categoryToggle.y, categoryToggle.w, categoryToggle.h, categoryToggle.h / 2, 1, WHITE);
                G.icon("filter", categoryToggle.x + 14, categoryToggle.y + 14, 13, WHITE, 1.7f);
                sourcesBtn.paint(a);
            } else if (page == PACKS) {
                importPackBtn.paint(a);
                openPacksBtn.paint(a);
            } else {
                segmentedRow(filters, 3, modFilter, a);
                openModsBtn.paint(a);
                if (updateAllBtn.visible) updateAllBtn.paint(a);
            }
        } else if (page == ACCOUNTS) {
            if (addMode()) {
                if (accBack.visible) accBack.paint(a);
            } else
                addAccountBtn.paint(a);
        }
    }
    // The home page: the banner of the client with Play and Trust mode
    void homePage() {
        float x = homeX(), w = homeW(), y = HERO_Y, h = HERO_HT;
        block(1, [&](float a) {
            G.glow(x, y + 8, w, h, 16, alpha(rgb(0), .30f * a), 18);
            float mx = unpageX(mouseX), my = unpageY(mouseY) + scrollY();
            float heroHv = an.to("hero.h", mx >= x && mx < x + w && my >= y && my < y + h && !modal() ? 1.f : 0.f, 6);
            if (ID2D1Bitmap* artB = cardArt(7, w, h)) {
                ID2D1BitmapBrush* bb = nullptr;
                if (SUCCEEDED(G.rt->CreateBitmapBrush(artB, &bb))) {
                    bb->SetTransform(D2D1::Matrix3x2F::Scale(1 / G.scale, 1 / G.scale) * D2D1::Matrix3x2F::Translation(x, y) * artMotion(x, y, w, h, heroHv * .6f));
                    bb->SetOpacity(a);
                    G.fillShape(x, y, w, h, 16, bb);
                    bb->Release();
                }
            }
            G.rrGrad(x, y, w, h, 16, fade(rgba(10, 10, 16, 225), a), rgba(10, 10, 16, 0), x, 0, x + w * .72f, 0);
            G.rrLine(x + .5f, y + .5f, w - 1, h - 1, 15.5f, fade(alpha(WHITE, .14f), a));
            if (online >= 0) {  // players in the game right now
                string n = std::to_string(online) + " " + Lang::t("в игре");
                G.raw = true;
                float pw = G.width(n, SEMI, 12) + 34, px = x + w - 18 - pw;
                G.glass(px, y + 16, pw, 26, 13, rgba(20, 20, 26, 120), a, false);
                G.circle(px + 14, y + 29, 3.5f, fade(OK, a));
                G.text(n, px + 24, Gfx::mid(y + 29, 12), SEMI, 12, fade(WHITE, a));
                G.raw = false;
            }
            if (ID2D1Bitmap* word = startupWord())
                G.rt->DrawBitmap(word, D2D1::RectF(x + 14, y + 26, x + 14 + 240, y + 26 + 240 * 58 / 220.f), a, D2D1_BITMAP_INTERPOLATION_MODE_LINEAR);
            string badgeText = "Fabric " + Config::mcVersion + "  ·  " + Lang::t("рекомендуем");
            G.raw = true;
            float bw = G.width(badgeText, SEMI, 11) + 20;
            G.rr(x + 22, y + 96, bw, 22, 11, fade(alpha(ACTION, .30f), a));
            G.text(badgeText, x + 32, Gfx::mid(y + 107, 11), SEMI, 11, fade(ACCENT2, a));
            G.raw = false;
            if (preparing) {
                G.text(G.fit(caption, SEMI, 13.5f, w * .55f), x + 22, y + 142, SEMI, 13.5f, fade(WHITE, a));
                bar(x + 22, y + 152, std::min(320.f, w * .5f), an.get("prep", 0), a);
                G.text(G.fit(prepDetail, MEDIUM, 11.5f, w * .55f), x + 22, y + 174, MEDIUM, 11.5f, fade(MUTED, a));
            } else
                for (auto& l : wrap(Lang::t("Плащи, эмоции и эффекты, которые видят другие игроки Arcanic."), MEDIUM, 13, w * .6f, 1))
                    G.text(l, x + 22, y + 148, MEDIUM, 13, fade(rgb(0xD1D1D6), a));
            playBtn.paint(a);
            if (trustBtn.visible) trustBtn.paint(a);
            float after = trustBtn.visible ? trustBtn.x + trustBtn.w : playBtn.x + playBtn.w;
            if (!playBtn.running)
                G.text(version.empty() ? "Arcanic" : "Клиент v" + version, after + 16, Gfx::mid(playBtn.y + playBtn.h / 2, 12.5f),
                       MEDIUM, 12.5f, fade(rgb(0xAEAEB2), a));
        });
    }
    // A notification as macOS shows one: a glass banner in the top right corner that slides in and goes by itself
    void toast() {
        float k = toastK();
        if (k < .01f || toastText.empty()) return;
        float x = toastHit.x + (1 - k) * 40, y = toastHit.y, w = TOAST_W, h = TOAST_H;
        G.glow(x, y + 6, w, h, 16, alpha(rgb(0), .35f * k), 18);
        G.glass(x, y, w, h, 16, rgba(30, 30, 36, 160), k);
        appIcon(x + 12, y + 14, 36, k);
        G.text("Arcanic", x + 58, y + 26, SEMI, 12.5f, fade(WHITE, k));
        G.right(Lang::t("сейчас"), x + w - 14, y + 26, MEDIUM, 11, fade(MUTED, k));
        int ln = 0;
        for (auto& l : wrap(Lang::t(toastText), MEDIUM, 12, w - 76, 2))
            G.text(l, x + 58, y + 43 + 15 * ln++, MEDIUM, 12, fade(toastColor.r == ERR.r && toastColor.g == ERR.g ? rgb(0xFF8A80) : rgb(0xD1D1D6), k));
        if (k < 1 || T - toastAt < toastFor) an.moving = true;
    }

    // The game's own item picture (the name tag), pixels kept sharp; a drawn stand-in until the game is installed
    ID2D1Bitmap* itemTexture() {
        auto it = G.bitmaps.find("tex:item/name_tag");
        if (it != G.bitmaps.end()) return it->second;
        string bytes = Skins::gameTexture("item/name_tag");
        if (bytes.empty()) return nullptr;
        IWICBitmapSource* src = G.decode(bytes, 0);
        ID2D1Bitmap* b = G.toDevice(src);
        release(src);
        return G.bitmaps["tex:item/name_tag"] = b;
    }
    // The version's icon: the name tag item, as in the game's inventory
    void versionIcon(float x, float y, float s, float a) {
        if (ID2D1Bitmap* b = itemTexture()) {
            G.picture(b, x, y, s, s, 0, a);
            return;
        }
        // drawn: a beige tag lying diagonally, a dark string loop at its top left
        static const char* px[12] = {
            "............", ".oo.........", "o..o........", "o..ooo......", ".oo.ppo.....", "...opppo....",
            "...oppppo...", "....oppppo..", ".....oppspo.", "......opsso.", ".......osso.", "........oo..",
        };
        float q = s / 12;
        for (int j = 0; j < 12; j++)
            for (int i = 0; i < 12; i++) {
                char c = px[j][i];
                if (c == '.') continue;
                Col col = c == 'o' ? rgb(0x4A3A28) : c == 'p' ? rgb(0xE7D7B4) : rgb(0xBFA77E);
                G.rect(x + i * q, y + j * q, q + .3f, q + .3f, fade(col, a));
            }
    }
    // A file's own picture as a rounded square, or an icon tile if it has none
    void thumb(const string& k, const char* fallback, float x, float y, float s, float a) {
        if (ID2D1Bitmap* b = picture(k)) {
            G.rr(x, y, s, s, std::min(11.f, s / 4), fade(WHITE, a));
            G.picture(b, x, y, s, s, std::min(11.f, s / 4), a);
        }  // icons with see-through corners sit on a white tile
        else
            iconTile(fallback, x, y, s, a);
    }
    void row(float a, float y, const string& k, const char* icon, const string& name, const string& sub, Toggle* tg,
             const string& badgeText) {
        float hv = an.to(tg->id + ".rh", tg->hovered ? 1 : 0, 14), on = an.to(tg->id + ".ro", tg->on ? 1 : 0, 12);
        G.glass(CX, y, CW, ROW_H, 12, mix(TINT, rgba(44, 44, 52, 150), hv), a);
        thumb(k, icon, CX + 10, y + 8, 40, a * (.45f + .55f * on));
        G.text(G.fit(name, SEMI, 13.5f, CW - 300), CX + 62, y + 25, SEMI, 13.5f, fade(mix(MUTED, TEXT, on), a));
        G.text(G.fit(sub, MEDIUM, 11.5f, CW - 300), CX + 62, y + 42, MEDIUM, 11.5f, fade(MUTED, a));
        if (!badgeText.empty()) badge(badgeText, CX + CW - 22 - 42 - 14, y + ROW_H / 2, true, a);
        tg->paint(a);
    }
    // An empty list: a quiet mark, what is (not) here and what to do about it, centred where the list would be
    void empty(float a, float top, const char* icon, const string& title, const string& sub) {
        float cx = CX + CW / 2;
        G.circle(cx, top + 40, 26, fade(alpha(WHITE, .04f), a));
        G.ring(cx, top + 40, 26, fade(alpha(WHITE, .06f), a), 1);
        G.icon(icon, cx, top + 40, 22, fade(ACCENT2, a), 1.7f);
        G.center(title, cx, top + 94, SEMI, 15, fade(TEXT, a));
        G.center(G.fit(sub, MEDIUM, 12.5f, CW - 80), cx, top + 116, MEDIUM, 12.5f, fade(MUTED, a));
    }
    // A coloured tile with the first letter, for a mod whose icon cannot be shown
    static void letterTile(const string& name, float x, float y, float s, float a) {
        unsigned hsh = 0;
        for (unsigned char c : lower(name)) hsh = hsh * 31 + c;
        // a muted tone of the palette picked by the name, so each tile keeps its colour from start to start
        static const unsigned tones[6] = {0x3A3552, 0x2F3B54, 0x3E3446, 0x2C4246, 0x45383C, 0x34404F};
        Col c1 = rgb(tones[hsh % 6]), c2 = mix(c1, rgb(0x16171E), .45f);
        G.rrGrad(x, y, s, s, s * .28f, fade(c1, a), fade(c2, a), x, y, x + s, y + s);
        wstring w = W(name);
        if (w.empty()) w = L"?";
        G.center(upper(U(w.substr(0, 1))), x + s / 2, Gfx::mid(y + s / 2, s * .44f), BOLD, s * .44f, fade(WHITE, a));
    }
    void modsPage() {
        if (modsRemote) {
            float a = enter(1);
            if (showCategories) chips(modCats, modCat, a);
            auto hits = shownCatalog();
            if (hits.empty()) empty(a, catalogY, "box", catalogBusy ? "Ищем…" : catalogFailed ? "Modrinth недоступен" : "Ничего не найдено", "Попробуй другое название или сними фильтр");
            for (auto& h : hits) catalogRow(h, a);
            moreLine(false, hits.size(), catalogY, a);
            return;
        }

        float y = MODS_TOP;
        int i = 0;
        for (auto& m : shownMods()) {
            auto it = rowToggles.find("mod:" + key(m.file));
            if (it != rowToggles.end()) {
                Toggle* tg = it->second.get();
                float ry = y;
                block(2 + std::min(i, 8), [&](float a) {
                    row(a, ry, key(m.file), "box", m.name,
                        replaceAll(S(m.file.filename()), ".disabled", "") + " · " + Game::sizeText(m.size), tg,
                        m.required ? "нужен клиенту" : modUpdates.count(S(m.file.filename())) ? "есть обновление" : "");
                });
            }
            y += ROW_STEP;
            i++;
        }
        if (shownMods().empty()) empty(enter(1), MODS_TOP + 30, "box", mods.empty() ? "Модов пока нет" : "Ничего не найдено", mods.empty() ? "Добавь моды из Modrinth или открой папку игры" : "Попробуй другое название или сними фильтр");
    }
    void chips(vector<std::unique_ptr<Clickable>>& cats, int selected, float a) {
        for (size_t i = 0; i < cats.size(); i++) {
            Clickable& c = *cats[i];
            float sel = an.to(c.id + ".s", (int)i == selected ? 1 : 0, 16),
                  hv = an.to(c.id + ".h", c.hovered ? 1 : 0, 16);
            G.rr(c.x, c.y, c.w, c.h, c.h / 2, fade(mix(alpha(WHITE, .08f + .05f * hv), ACTION, sel), a));
            ripple(c, c.x, c.y, c.w, c.h, c.h / 2, a, WHITE);
            G.center(c.tag, c.x + c.w / 2, Gfx::mid(c.y + c.h / 2, 12.5f), MEDIUM, 12.5f, fade(mix(mix(rgb(0xD1D1D6), WHITE, hv), WHITE, sel), a));
        }
    }
    // Under a list with more to come: a small spinner and "loading more" (the next page is asked by tick())
    void moreLine(bool packs, size_t count, float top, float a) {
        if (!count || !moreToCome(packs)) return;
        float y = top + ((count + 1) / 2) * 90.f + 22, cx = CX + CW / 2;
        bool busy = packs ? packMoreBusy : modMoreBusy;
        string label = Lang::t(busy ? "Загружаем ещё…" : "Прокрути ниже — загрузим ещё");
        float tw = G.width(label, MEDIUM, 12), sx = cx - (tw + 22) / 2;
        if (busy) {
            float ang = (float)fmod(T * 5.2, 6.2831853);
            G.ring(sx + 7, y, 6.5f, fade(alpha(WHITE, .18f), a), 1.8f);
            for (int i = 0; i < 10; i++) {
                float a0 = ang + i * .17f, a1 = a0 + .17f;
                G.line(sx + 7 + cosf(a0) * 6.5f, y + sinf(a0) * 6.5f, sx + 7 + cosf(a1) * 6.5f, y + sinf(a1) * 6.5f, fade(rgb(0xD1D1D6), a), 1.8f);
            }
            an.moving = true;
        }
        G.text(label, sx + (busy ? 22 : 11), Gfx::mid(y, 12), MEDIUM, 12, fade(MUTED, a));
    }
    void catalogRow(const Modrinth::Hit& h, float a) {
        auto it = installBtns.find(h.id); auto vt = viewBtns.find(h.id);
        if (it == installBtns.end() || vt == viewBtns.end()) return;
        auto* b = it->second.get(); auto* v = vt->second.get();
        float x = v->x, y = v->y, width = (CW - 10) / 2;
        float sy = scrollY();
        if (y + 80 < sy + listTop() - 40 || y > sy + WIN_H + 40) return;
        float hv = an.to("grid:" + h.id, v->hovered ? 1 : 0, 16);
        G.glass(x, y, width, 80, 14, mix(TINT, rgba(44, 44, 52, 150), hv), a);
        ripple(*v, x, y, width, 80, 14, a, WHITE);
        if (auto* bmp = picture("mr:" + h.id)) G.picture(bmp, x + 12, y + 20, 40, 40, 10, a);
        else letterTile(h.title, x + 12, y + 20, 40, a);
        G.raw = true;
        G.text(G.fit(h.title, SEMI, 13, width - 169), x + 60, y + 26, SEMI, 13, fade(WHITE, a));
        G.raw = false;
        auto lines = wrap(h.description, MEDIUM, 11, width - 169, 2);
        for (size_t i = 0; i < lines.size(); i++) G.text(lines[i], x + 60, y + 44 + i * 14, MEDIUM, 11, fade(MUTED, a));
        b->paint(a);
    }
    void packsPage() {
        if (packsRemote) {
            float a = enter(1);
            if (showCategories) chips(packCats, packCat, a);
            auto hits = shownPackCatalog();
            if (hits.empty()) empty(a, packCatalogY, "layers", packBusy ? "Ищем…" : packFailed ? "Каталог недоступен" : "Ничего не найдено",
                packFailed && !packError.empty() ? G.fit(packError,MEDIUM,11.5f,CW-78) : "Попробуй другое название или сними фильтр");
            for (auto& h : hits) catalogRow(h, a);
            moreLine(true, hits.size(), packCatalogY, a);
            return;
        }

        auto list = shownPacks();
        bool typed = !trim(packSearch.value()).empty();
        float y = MODS_TOP;
        int i = 0;
        for (auto& p : list) {
            auto it = rowToggles.find("pack:" + key(p.file));
            if (it != rowToggles.end()) {
                Toggle* tg = it->second.get();
                float ry = y;
                block(1 + std::min(i, 8), [&](float a) {
                    row(a, ry, key(p.file), "layers", p.name, S(p.file.filename()) + " · " + Game::sizeText(p.size), tg,
                        "");
                });
            }
            y += ROW_STEP;
            i++;
        }
        if (list.empty()) empty(enter(1), MODS_TOP + 30, "layers", typed ? "Ничего не найдено" : "Ресурспаков нет", typed ? "Попробуй другое название или сними фильтр" : "Добавь ZIP или скачай пак из каталога");
    }
    // ---- Settings, as System Settings lays them out: a heading over each group, rows split by hairlines
    void groupHead(float groupTop, const string& title, float a) {
        G.text(title, CX + 4, groupTop - 9, SEMI, 12, fade(rgba(255, 255, 255, 140), a));
    }
    void group(float top, float h, float a) { G.glass(CX, top, CW, h, 12, TINT, a); }
    void hairline(float y, float a, float inset = 16) { G.rect(CX + inset, y, CW - inset - 16, 1, fade(SEPARATOR, a)); }
    void rowLabel(float top, float h, const string& label, float a, const string& sub = "") {
        if (sub.empty()) G.text(label, CX + 16, Gfx::mid(top + h / 2, 13), MEDIUM, 13, fade(TEXT, a));
        else {
            G.text(label, CX + 16, top + h / 2 - 2, MEDIUM, 13, fade(TEXT, a));
            G.raw = true;
            G.text(G.fit(sub, MEDIUM, 11.5f, CW - 320), CX + 16, top + h / 2 + 14, MEDIUM, 11.5f, fade(MUTED, a));
            G.raw = false;
        }
    }
    // EN | RU as a segmented control; the lit half slides to the language in use
    void segmented(Clickable& c, float a) {
        float half = c.w / 2, k = an.to(c.id + ".k", Lang::en ? 0.f : 1.f, 18), hv = an.to(c.id + ".h", c.hovered ? 1.f : 0.f, 16);
        G.rr(c.x, c.y, c.w, c.h, c.h / 2, fade(alpha(WHITE, .09f + .03f * hv), a));
        G.rr(c.x + 2 + (half - 2) * k, c.y + 3, half - 2, c.h - 4, (c.h - 4) / 2, fade(rgba(0, 0, 0, 50), a));
        G.rr(c.x + 2 + (half - 2) * k, c.y + 2, half - 2, c.h - 4, (c.h - 4) / 2, fade(alpha(WHITE, .22f), a));
        G.center("EN", c.x + half / 2 + 1, Gfx::mid(c.y + c.h / 2, 12), SEMI, 12, fade(mix(WHITE, rgb(0xAEAEB2), k), a));
        G.center("RU", c.x + half + half / 2 - 1, Gfx::mid(c.y + c.h / 2, 12), SEMI, 12, fade(mix(rgb(0xAEAEB2), WHITE, k), a));
    }
    void settingsPage() {
        auto g = settingsGeom();
        block(1, [&](float a) {  // the Arcanic account
            groupHead(g.account, "Аккаунт Arcanic", a);
            group(g.account, g.accountH, a);
            float y = g.account;
            if (signedIn()) letterTile(userName, CX + 16, y + 12, 40, a);
            else {
                G.circle(CX + 36, y + 32, 20, fade(alpha(WHITE, .10f), a));
                G.icon("user", CX + 36, y + 32, 20, fade(rgb(0xD1D1D6), a), 1.7f);
            }
            string t = lower(trim(plan));
            G.raw = signedIn();
            G.text(signedIn() ? userName : Lang::t("Вход не выполнен"), CX + 68, y + 29, SEMI, 14, fade(WHITE, a));
            G.raw = false;
            G.text(signedIn() ? (t == "premium" ? "Premium" : t == "beta" ? "Beta" : "Free") + string("  ·  ") + Lang::t("синхронизация включена")
                              : Lang::t("Войди, чтобы другие игроки видели твою косметику"),
                   CX + 68, y + 47, MEDIUM, 11.5f, fade(signedIn() && t == "premium" ? rgb(0xE8C36A) : MUTED, a));
            arcBtn.paint(a);
            if (isAdmin() && signedIn()) {
                hairline(y + 64, a);
                grantLogin.paint(a);
                grantBtn.paint(a);
                premListBtn.paint(a);
                {  // the open list lights its button
                    float on = an.to("premList.on", premOpen ? 1.f : 0.f, 18);
                    if (on > .01f) G.rrLine(premListBtn.x + .5f, premListBtn.y + .5f, premListBtn.w - 1, premListBtn.h - 1, premListBtn.h / 2 - .5f, fade(alpha(ACCENT, .7f), a * on), 1.2f);
                }
                grantDays.paint(a);
                lifetimeBtn.paint(a);
                if (!grantNote.empty()) {  // under the row: what the last action did
                    G.raw = true;
                    G.text(G.fit(Lang::t(grantNote) + (grantName.empty() ? string() : ": " + grantName), MEDIUM, 12, CW - 32),
                           CX + 16, Gfx::mid(y + 64 + 56 + 6, 12), MEDIUM, 12, fade(grantColor, a));
                    G.raw = false;
                }
                if (premOpen) {
                    float top = y + 64 + grantRowsH(), ly = top + 10;
                    hairline(top, a);
                    if (premRows.empty()) {
                        string msg = premBusy ? "Загружаем…" : !premError.empty() ? premError : "Ни у кого нет Premium";
                        G.text(msg, CX + 16, Gfx::mid(ly + (PREM_ROW - 4) / 2, 12.5f), MEDIUM, 12.5f, fade(premError.empty() ? MUTED : rgb(0xFF8A80), a));
                    }
                    for (size_t i = 0; i < premRows.size() && i < premRowW.size(); i++) {
                        auto& w = *premRowW[i];
                        auto& p = premRows[i];
                        bool open = p.login == premSel;
                        float hv = an.to(w.id + ".h", w.hovered ? 1.f : 0.f, 24), lit = an.to(w.id + ".o", open ? 1.f : 0.f, 18);
                        if (hv > .01f || lit > .01f) G.rr(w.x, w.y, w.w, w.h, 9, fade(alpha(WHITE, .04f * hv + .07f * lit), a));
                        ripple(w, w.x, w.y, w.w, w.h, 9, a, WHITE);
                        letterTile(p.name, w.x + 8, w.y + 6, 28, a);
                        G.raw = true;
                        G.text(G.fit(p.name, SEMI, 13, 240), w.x + 46, w.y + 18, SEMI, 13, fade(WHITE, a));
                        G.raw = false;
                        bool soon = p.until && p.until - (int64_t)time(nullptr) < 3 * 86400;  // ends within three days
                        G.text(termText(p.until), w.x + 46, w.y + 33, MEDIUM, 11.5f, fade(soon ? rgb(0xFFB25B) : p.until ? MUTED : rgb(0xE8C36A), a));
                        if (!open)
                            G.icon("chevron", w.x + w.w - 16, w.y + w.h / 2, 12, fade(rgba(255, 255, 255, 110), a * (.5f + .5f * hv)), 1.6f);
                    }
                    for (auto& b : premAct)
                        if (b.visible) b.paint(a);
                    if (premDays.visible) premDays.paint(a);
                }
            }
        });
        block(2, [&](float a) {  // the look: the wallpaper, the size, the language
            groupHead(g.look, "Внешний вид", a);
            group(g.look, g.lookH, a);
            G.text("Обои", CX + 16, g.look + 26, MEDIUM, 13, fade(TEXT, a));
            for (int i = 0; i < Wallpaper::COUNT; i++) {
                Clickable& c = wpThumb[i];
                float th = c.h - 22, hv = an.to(c.id + ".h", c.hovered ? 1.f : 0.f, 18), sel = an.to(c.id + ".s", Config::wallpaper == i ? 1.f : 0.f, 16);
                if (sel > .01f) G.rr(c.x - 3, c.y - 3, c.w + 6, th + 6, 11, fade(ACTION, a * sel));
                if (ID2D1Bitmap* b = thumbBitmap(i)) G.picture(b, c.x, c.y, c.w, th, 8, a);
                else G.rr(c.x, c.y, c.w, th, 8, fade(alpha(WHITE, .06f), a));
                G.rrLine(c.x + .5f, c.y + .5f, c.w - 1, th - 1, 7.5f, fade(alpha(WHITE, .12f + .15f * hv), a));
                G.center(Wallpaper::THEMES[i].name, c.x + c.w / 2, c.y + th + 16, MEDIUM, 12, fade(mix(rgb(0xAEAEB2), WHITE, std::max(sel, hv)), a));
            }
            float row = g.look + SG_WALL;
            hairline(row, a);
            rowLabel(row, SG_ROW, "Масштаб", a);
            menuScale.paint(a);
            G.raw = true;
            G.text(fmt("%.0f%%", 70 + 60 * menuScale.value), menuScale.x + menuScale.w + 10, Gfx::mid(row + SG_ROW / 2, 12.5f), SEMI, 12.5f, fade(MUTED, a));
            G.raw = false;
            resetScale.paint(a);
            hairline(row + SG_ROW, a);
            rowLabel(row + SG_ROW, SG_ROW, "Язык", a);
            segmented(langSetting, a);
        });
        block(3, [&](float a) {  // sound and motion
            groupHead(g.sound, "Звук и анимация", a);
            group(g.sound, g.soundH, a);
            rowLabel(g.sound, SG_ROW, "Звуки", a);
            soundsToggle.paint(a);
            hairline(g.sound + SG_ROW, a);
            rowLabel(g.sound + SG_ROW, SG_ROW, "Заставка при запуске", a);
            startupToggle.paint(a);
        });
        block(4, [&](float a) {  // the game
            groupHead(g.game, "Игра", a);
            group(g.game, g.gameH, a);
            G.text("Оперативная память", CX + 16, Gfx::mid(g.game + SG_ROW / 2, 13), MEDIUM, 13, fade(TEXT, a));
            G.text(Config::ramMb <= 0 ? ramText(Installer::defaultRam()) + " · " + Lang::t("авто") : ramText(Config::ramMb),
                   CX + 16 + G.width(Lang::t("Оперативная память"), MEDIUM, 13) + 10, Gfx::mid(g.game + SG_ROW / 2, 13), SEMI, 13, fade(ACCENT, a));
            autoRamBtn.paint(a);
            memory.paint(a);
            G.text("1 ГБ", memory.x + 10, memory.y + memory.h + 12, MEDIUM, 11, fade(MUTED, a));
            G.right(ramText(maxRam), memory.x + memory.w - 10, memory.y + memory.h + 12, MEDIUM, 11, fade(MUTED, a));
            float r2 = g.game + 2 * SG_ROW + 8;
            hairline(r2, a);
            rowLabel(r2, SG_ROW, "Убирать клиент из mods после игры", a, "Папка mods остаётся чистой для других сборок");
            cleanToggle.paint(a);
            hairline(r2 + SG_ROW, a);
            rowLabel(r2 + SG_ROW, SG_ROW, "Аргументы JVM", a, "Для опытных игроков");
            jvmField.paint(a);
        });
        block(5, [&](float a) {  // the files
            groupHead(g.files, "Файлы", a);
            group(g.files, g.filesH, a);
            G.text("Папка игры", CX + 16, g.files + 24, MEDIUM, 13, fade(TEXT, a));
            G.raw = true;
            G.text(G.fit(S(Game::dir()), MEDIUM, 12, CW - 160), CX + 108, g.files + 24, MEDIUM, 12, fade(MUTED, a));
            G.raw = false;
            for (Btn* b : {&gameDirBtn, &modsDirBtn, &moveBtn, &defaultBtn})
                if (b->visible) b->paint(a);
            Btn& last = defaultBtn.visible ? defaultBtn : moveBtn;
            if (!filesNote.empty())
                G.text(G.fit(filesNote, MEDIUM, 12, CX + CW - 16 - (last.x + last.w + 12)), last.x + last.w + 12, Gfx::mid(last.y + 15, 12),
                       MEDIUM, 12, fade(ACCENT, a));
        });
        block(6, [&](float a) {  // about
            groupHead(g.about, "О программе", a);
            group(g.about, g.aboutH, a);
            appIcon(CX + 16, g.about + 12, 34, a);
            G.headline = true;
            G.text("Arcanic", CX + 60, g.about + 28, BOLD, 14, fade(WHITE, a));
            G.headline = false;
            G.raw = true;
            G.text(string("v") + Config::VERSION, CX + 60 + G.width("Arcanic", BOLD, 14) + 8, g.about + 28, MEDIUM, 12, fade(MUTED, a));
            G.raw = false;
            G.text(updateNote.empty() ? "Обновления ставятся сами при запуске" : updateNote, CX + 60, g.about + 45, MEDIUM, 11.5f,
                   fade(updateNote.empty() ? MUTED : updateColor, a));
            updateBtn.paint(a);
            hairline(g.about + 56, a);
            for (Btn* b : {&discordLink, &siteLink, &logBtn}) b->paint(a);
            G.raw = true;
            G.right((Lang::en ? "Authors - " : "Авторы — ") + replaceAll(Config::authors(), " & ", Lang::en ? " & " : " и "), CX + CW - 16,
                    Gfx::mid(g.about + 81, 12.5f), MEDIUM, 12.5f, fade(MUTED, a));
            G.raw = false;
        });
    }    bool sbDrag = false;
    double lastWheelAt = -10;
    float sbGrab = 0;
    // The scrollbar of the page: where its track is and how tall its thumb; false when the page does not scroll
    bool sbGeom(float& top, float& track, float& th, float& mx) {
        top = listTop();
        mx = std::max(0.f, contentHeight - (WIN_H - top - 16));
        if (mx <= 0 || page == HOME || !loggedIn()) return false;
        track = WIN_H - top - 16;
        th = std::max(40.f, track * track / (track + mx));
        return true;
    }
    bool sbOver() {
        float top, track, th, mx;
        float bx = WIN_W - 9;
        return sbGeom(top, track, th, mx) && mouseX >= bx - 17 && mouseX < bx + 7 && mouseY >= top - 4 &&
               mouseY <= top + track + 4;
    }
    void sbTo(float y) {
        float top, track, th, mx;
        if (!sbGeom(top, track, th, mx)) return;
        scroll[page] = clamp01((y - sbGrab - top) / std::max(1.f, track - th)) * mx;
        an.set("scroll:" + std::to_string(page), scroll[page]);  // the thumb follows the hand at once, no easing
    }
    template <class F> void scrolled(F body) {
        float top = listTop(), sy = scrollY();
        G.clip(SIDE, top - 8, WIN_W - SIDE, WIN_H - top + 8);
        // scrolled: the list fades out under the toolbar instead of being cut there (the macOS scroll edge)
        ID2D1Brush* edge = sy > .5f ? G.fadeIn(top - 8, top + 14) : nullptr;
        if (edge)
            G.rt->PushLayer(D2D1::LayerParameters(D2D1::RectF(SIDE, top - 8, WIN_W, WIN_H), nullptr, D2D1_ANTIALIAS_MODE_PER_PRIMITIVE,
                                                  D2D1::IdentityMatrix(), 1, edge),
                            nullptr);
        D2D1_MATRIX_3X2_F old;
        G.rt->GetTransform(&old);
        G.rt->SetTransform(D2D1::Matrix3x2F::Translation(0, -sy) * old);
        body();
        G.rt->SetTransform(old);
        if (edge) G.rt->PopLayer();
        G.unclip();
        float mx = std::max(0.f, contentHeight - (WIN_H - top - 16));
        if (mx > 0) {
            float track = WIN_H - top - 16, th = std::max(40.f, track * track / (track + mx));
            bool scrolling = fabsf(an.get("scroll:" + std::to_string(page), 0) - scroll[page]) > .5f || T - lastWheelAt < .9;
            float hv = an.to("sb.h", sbDrag || sbOver() ? 1.f : 0.f, 16), show = an.to("sb.show", scrolling || sbDrag || sbOver() ? 1.f : 0.f, 9);
            if (show > .01f) {
                float w = 6 + 3 * hv, bx = WIN_W - 9;
                if (hv > .01f) G.rr(bx - w / 2 - 1, top, w + 2, track, (w + 2) / 2, rgba(255, 255, 255, (int)lroundf(14 * hv * show)));
                G.rr(bx - w / 2, top + (track - th) * (sy / mx), w, th, w / 2, alpha(WHITE, (.32f + .2f * hv) * show));
            }
        }
    }
    static constexpr float STARTUP_WORD_WIDTH = 220;
    void drawStartupWordmark(const string& title) {
        // Every glyph occupies the same 20 x 28 grid with a 12-unit gap
        for (size_t i = 0; i < title.size(); ++i) {
            float x = 4 + i * 32.f, y = 14;
            Gfx::Path glyph(G.d2d);
            switch (title[i]) {
            case 'A': glyph.M(x,y+28).L(x,y+8).L(x+8,y).L(x+20,y).L(x+20,y+28).M(x,y+15).L(x+20,y+15); break;
            case 'R': glyph.M(x,y+28).L(x,y).L(x+20,y).L(x+20,y+14).L(x,y+14).M(x+10,y+14).L(x+20,y+28); break;
            case 'C': glyph.M(x+20,y).L(x,y).L(x,y+28).L(x+20,y+28); break;
            case 'N': glyph.M(x,y+28).L(x,y).L(x+20,y+28).L(x+20,y); break;
            case 'I': glyph.M(x,y).L(x+20,y).M(x+10,y).L(x+10,y+28).M(x,y+28).L(x+20,y+28); break;
            case 'W': glyph.M(x,y).L(x,y+28).L(x+10,y+18).L(x+20,y+28).L(x+20,y); break;
            case 'K': glyph.M(x,y).L(x,y+28).M(x+20,y).L(x,y+14).L(x+20,y+28); break;
            case 'E': glyph.M(x+20,y).L(x,y).L(x,y+28).L(x+20,y+28).M(x,y+14).L(x+16,y+14); break;
            case 'G': glyph.M(x+20,y).L(x,y).L(x,y+28).L(x+20,y+28).L(x+20,y+14).L(x+10,y+14); break;
            }
            if (auto* geometry = glyph.done()) {
                D2D1_RECT_F bounds{};
                D2D1_MATRIX_3X2_F saved; G.rt->GetTransform(&saved);
                if (SUCCEEDED(geometry->GetWidenedBounds(2.1f,nullptr,nullptr,D2D1_DEFAULT_FLATTENING_TOLERANCE,&bounds))) {
                    auto fit = D2D1::Matrix3x2F::Translation(-bounds.left,-bounds.top)
                             * D2D1::Matrix3x2F::Scale(20/(bounds.right-bounds.left),28/(bounds.bottom-bounds.top))
                             * D2D1::Matrix3x2F::Translation(x,y);
                    G.rt->SetTransform(fit * saved);
                }
                G.rt->DrawGeometry(geometry,G.use(rgba(172,169,228,48)),4);
                G.rt->DrawGeometry(geometry,G.use(rgb(0xDDDDF8)),2.1f);
                G.rt->DrawGeometry(geometry,G.use(rgba(244,246,255,140)),.65f);
                G.rt->SetTransform(saved);
            }
        }
    }
    ID2D1Bitmap* startupText(const string& title, const string& key) {
        auto found = G.bitmaps.find(key);
        if (found != G.bitmaps.end()) return found->second;
        float width = (float)title.size()*32 - 12 + 8;
        ID2D1BitmapRenderTarget* target = nullptr; ID2D1Bitmap* result = nullptr;
        D2D1_SIZE_F canvas = D2D1::SizeF(width * 2, 116);
        D2D1_PIXEL_FORMAT format = D2D1::PixelFormat(DXGI_FORMAT_B8G8R8A8_UNORM, D2D1_ALPHA_MODE_PREMULTIPLIED);
        if (SUCCEEDED(G.rt->CreateCompatibleRenderTarget(&canvas, nullptr, &format, D2D1_COMPATIBLE_RENDER_TARGET_OPTIONS_NONE, &target))) {
            auto* keep = G.rt; G.rt = target;
            target->BeginDraw(); target->Clear(D2D1::ColorF(0,0));
            target->SetTransform(D2D1::Matrix3x2F::Scale(2,2));
            auto antialias = target->GetTextAntialiasMode();
            target->SetTextAntialiasMode(D2D1_TEXT_ANTIALIAS_MODE_GRAYSCALE);
            drawStartupWordmark(title);
            target->SetTextAntialiasMode(antialias);
            if (SUCCEEDED(target->EndDraw())) target->GetBitmap(&result);
            G.rt = keep; release(target);
        }
        if (result) G.bitmaps[key] = result;
        return result;
    }
    ID2D1Bitmap* startupWord() { return startupText("ARCANIC", "startup-word"); }
    // A point on the outline of a rounded square, t in [0, 1) clockwise from the middle of its top edge
    static D2D1_POINT_2F squirclePoint(float x, float y, float s, float r, float t) {
        const float pi = 3.1415926f, side = s - 2 * r, arc = pi * r / 2, per = 4 * side + 4 * arc;
        float d = (t - floorf(t)) * per;
        auto at = [&](float px, float py) { return D2D1::Point2F(px, py); };
        float run = side / 2;  // the right half of the top edge first
        if (d < run) return at(x + s / 2 + d, y);
        d -= run;
        const float cxs[4] = {x + s - r, x + s - r, x + r, x + r}, cys[4] = {y + r, y + s - r, y + s - r, y + r};
        for (int k = 0; k < 4; k++) {
            if (d < arc) {
                float ang = -pi / 2 + k * pi / 2 + d / r;
                return at(cxs[k] + r * cosf(ang), cys[k] + r * sinf(ang));
            }
            d -= arc;
            float len = k == 3 ? run : side;
            if (d < len || k == 3) {
                switch (k) {
                case 0: return at(x + s, y + r + d);
                case 1: return at(x + s - r - d, y + s);
                case 2: return at(x, y + s - r - d);
                default: return at(x + r + d, y);
                }
            }
            d -= len;
        }
        return at(x + s / 2, y);
    }
    void startupScene(float seconds, float duration) {
        auto ease = [](float v) { float t = clamp01(v), r = 1 - t; return 1 - r * r * r; };
        auto inOut = [](float v) { float t = clamp01(v); return t < .5f ? 4 * t * t * t : 1 - powf(-2 * t + 2, 3) / 2; };
        auto spring = [](float v) { if (v <= 0) return 0.f; return 1 - expf(-6.5f * v) * cosf(11.f * v); };  // a little past 1, then back
        float t = seconds, out = ease((t - duration) / (float)SPLASH_FADE), stay = 1 - out;
        an.moving = true;
        const float cx = WIN_W / 2, S = 104;
        // ---- the glass over the wallpaper: dark for a moment at the very start, frosted, cleared at the end
        float dawn = ease(t / .7f);
        G.glass(0, 0, WIN_W, WIN_H, 0, rgba(16, 16, 22, (int)lroundf(120 + 60 * (1 - dawn))), stay);
        if (dawn < 1) G.rect(0, 0, WIN_W, WIN_H, rgba(6, 6, 10, (int)lroundf(255 * (1 - dawn) * stay)));
        G.radial(cx, 250, 260, alpha(rgb(0x6E7BFF), .10f * dawn * stay));
        // ---- the icon: where it is (centre of the window, then on its way into the login card)
        float land = inOut((t - duration) / (float)SPLASH_FADE);
        bool toCard = !loggedIn();
        float size = S, ix = cx - S / 2, iy = 198;
        if (toCard && land > 0) {
            size = S + (64 - S) * land;
            ix = cx - size / 2;
            iy = 198 + (182 - 198) * land;
        }
        float pop = spring((t - .3f) / .9f), show = clamp01((t - .3f) / .2f) * (toCard ? 1.f : stay);
        float sc = .55f + .45f * pop;
        D2D1_MATRIX_3X2_F saved;
        G.rt->GetTransform(&saved);
        G.rt->SetTransform(D2D1::Matrix3x2F::Scale(sc, sc, D2D1::Point2F(ix + size / 2, iy + size / 2)) * saved);
        float r = size * .23f;
        const float beamFrom = .55f, beamTo = 2.0f;
        float beam = inOut((t - beamFrom) / (beamTo - beamFrom)), closed = t >= beamTo ? 1.f : 0.f;
        float flood = ease((t - 1.5f) / .8f);  // the colour coming in
        G.glow(ix, iy + size * .07f, size, size, r, alpha(rgb(0), .32f * show), size * .14f);
        G.glass(ix, iy, size, size, r, rgba(40, 40, 52, 150), show * (1 - flood));
        G.rrGrad(ix, iy, size, size, r, fade(rgb(0x3E8BFF), show * flood), fade(rgb(0x7B4DFF), show * flood), ix, iy, ix + size, iy + size);
        G.rrGrad(ix, iy, size, size * .5f, r, fade(alpha(WHITE, .22f), show * flood), alpha(WHITE, 0), 0, iy, 0, iy + size * .5f);
        G.rrLine(ix + .5f, iy + .5f, size - 1, size - 1, r - .5f, alpha(WHITE, (.10f + .25f * closed * (1 - flood * .6f)) * show), 1);
        // the beam: one lap round the outline with a fading tail, colours shifting blue - violet - cyan
        if (beam > 0 && t < beamTo + .05f) {
            const int N = 90;
            float tail = .32f * std::min(1.f, beam * 3);
            for (int i = N; i >= 1; --i) {
                float a0 = beam - tail * i / N, a1 = beam - tail * (i - 1) / N;
                if (a1 < 0) continue;
                a0 = std::max(0.f, a0);
                auto p0 = squirclePoint(ix, iy, size, r, a0), p1 = squirclePoint(ix, iy, size, r, a1);
                float k = 1 - (float)i / N;
                Col c = mix(mix(rgb(0x7B8CFF), rgb(0xB48CFF), a1), rgb(0x8FE3FF), k * k);
                G.line(p0.x, p0.y, p1.x, p1.y, alpha(c, .05f * k * show), 10 * k + 1);
                G.line(p0.x, p0.y, p1.x, p1.y, alpha(c, .65f * k * k * show), 2.2f * k + .4f);
            }
            auto head = squirclePoint(ix, iy, size, r, beam);
            G.radial(head.x, head.y, 26, alpha(rgb(0x9FC4FF), .45f * show));
            G.circle(head.x, head.y, 2.6f, alpha(WHITE, show));
        }
        float flash = t >= beamTo ? expf(-(t - beamTo) * 5) : 0;  // the outline closes
        if (flash > .01f) {
            G.rrLine(ix - 2, iy - 2, size + 4, size + 4, r + 2, alpha(rgb(0xCFE0FF), .8f * flash * show), 2);
            G.radial(ix + size / 2, iy + size / 2, size * 1.3f, alpha(rgb(0x9E8BFF), .22f * flash * show));
        }
        // the A, stroke by stroke (the same 20 x 28 glyph as the wordmark)
        float trace = inOut((t - 1.8f) / .75f);
        if (trace >= 1) appIcon(ix, iy, size, show);  // drawn: from here on it is the very icon of the login card
        else if (trace > 0) {
            float k = size / 44, gx = ix + 12 * k, gy = iy + 8 * k;
            const float pts[5][2] = {{0, 28}, {0, 8}, {8, 0}, {20, 0}, {20, 28}};
            const float lens[5] = {20, 11.31f, 12, 28, 20};  // the four strokes of the outline, then the bar
            float total = 91.31f, left = trace * total;
            Col ink = fade(WHITE, show);
            for (int s = 0; s < 4 && left > 0; s++) {
                float f = std::min(1.f, left / lens[s]);
                float x0 = pts[s][0], y0 = pts[s][1], x1 = pts[s + 1][0], y1 = pts[s + 1][1];
                G.line(gx + x0 * k, gy + y0 * k, gx + (x0 + (x1 - x0) * f) * k, gy + (y0 + (y1 - y0) * f) * k, ink, 3.2f * k);
                left -= lens[s];
            }
            if (left > 0) {
                float f = std::min(1.f, left / lens[4]);
                G.line(gx, gy + 15 * k, gx + 20 * f * k, gy + 15 * k, ink, 3.2f * k);
            }
        }
        G.rt->SetTransform(saved);
        // ---- the name, letter by letter, rising into place; a line under it; the progress bar
        float words = stay * stay;
        if (words > .01f) {
            const string name = "Arcanic";
            float total = G.width(name, BOLD, 30), x = cx - total / 2;
            G.headline = true;
            for (size_t i = 0; i < name.size(); i++) {
                float k = ease((t - 2.2f - i * .06f) / .45f);
                string ch = name.substr(i, 1);
                float wPrefix = G.width(name.substr(0, i), BOLD, 30);
                G.raw = true;
                G.text(ch, x + wPrefix, 346 + 10 * (1 - k), BOLD, 30, alpha(WHITE, k * words));
                G.raw = false;
            }
            G.headline = false;
            float sub = ease((t - 2.75f) / .5f);
            G.center("Моды, ресурспаки и косметика — в одном окне", cx, 374, MEDIUM, 13, alpha(rgb(0xD1D1D6), sub * words));
            float bw = 180, by = 408, fill = inOut((t - .6f) / std::max(.1f, duration - .7f));
            float barIn = ease((t - .6f) / .4f) * words;
            G.rr(cx - bw / 2, by, bw, 4, 2, alpha(WHITE, .16f * barIn));
            G.rr(cx - bw / 2, by, std::max(4.f, bw * fill), 4, 2, alpha(WHITE, .92f * barIn));
        }
    }
    bool paint() {
        if (!G.target()) return false;
        G.begin();
        G.rt->Clear(D2D1::ColorF(BG.r, BG.g, BG.b));
        if (!loggedIn()) {
            loginScreen();
            langSwitch();
            trafficLights();
        } else {
            // the wallpaper, the calmer content area and the sidebar's glass: one picture, drawn pixel for pixel
            if (ID2D1Bitmap* bg = background(false))
                G.rt->DrawBitmap(bg, D2D1::RectF(0, 0, WIN_W, WIN_H), 1, D2D1_BITMAP_INTERPOLATION_MODE_NEAREST_NEIGHBOR);
            else
                G.rrGrad(0, 0, WIN_W, WIN_H, 0, rgb(0x15123A), rgb(0x07061A), 0, 0, 0, WIN_H);
            auto old = G.bitmaps.find("bg:old");  // a new wallpaper fades in over the last one
            if (old != G.bitmaps.end()) {
                float k = clamp01((float)((T - wpChangedAt) / .55));
                if (k < 1 && old->second) {
                    G.rt->DrawBitmap(old->second, D2D1::RectF(0, 0, WIN_W, WIN_H), 1 - Anim::inOut(k), D2D1_BITMAP_INTERPOLATION_MODE_NEAREST_NEIGHBOR);
                    an.moving = true;
                } else {
                    if (old->second) old->second->Release();
                    G.bitmaps.erase(old);
                }
            }
            G.clip(SIDE, 0, WIN_W - SIDE, WIN_H);
            D2D1_MATRIX_3X2_F pageTransform;
            G.rt->GetTransform(&pageTransform);
            G.rt->SetTextAntialiasMode(D2D1_TEXT_ANTIALIAS_MODE_GRAYSCALE);
            bool reveal = pageProgress() < 1;
            if (reveal) {  // a page arrives: it fades in and settles from a hair smaller
                an.moving = true;
                auto layer = D2D1::LayerParameters(D2D1::RectF(SIDE, 0, WIN_W, WIN_H), nullptr, D2D1_ANTIALIAS_MODE_PER_PRIMITIVE,
                                                   D2D1::Matrix3x2F::Identity(), pageOpacity());
                G.rt->PushLayer(layer, nullptr);
                float depth = pageDepthScale();
                G.rt->SetTransform(D2D1::Matrix3x2F::Scale(depth, depth, D2D1::Point2F((SIDE + WIN_W) / 2, WIN_H / 2)) * pageTransform);
            }
            paintingPage = true;
            switch (page) {
            case HOME: scrolled([&] { homePage(); }); break;
            case MODS: scrolled([&] { modsPage(); }); break;
            case PACKS: scrolled([&] { packsPage(); }); break;
            case ACCOUNTS: scrolled([&] { accountsPage(); }); break;
            case SETTINGS: scrolled([&] { settingsPage(); }); break;
            }
            paintingPage = false;
            G.rt->SetTransform(pageTransform);
            if (reveal) G.rt->PopLayer();
            G.unclip();
            toolbar();
            G.rt->SetTextAntialiasMode(D2D1_TEXT_ANTIALIAS_MODE_CLEARTYPE);
            rail();
                previewDialog();
            paintSources();
            toast();
        }
        float startupTime = DESIGN_PREVIEW && previewStartupTime >= 0 ? previewStartupTime : startupClock();
        float duration = DESIGN_PREVIEW && previewStartupTime >= 0 ? 4.2f : (float)splashFor;
        if (Config::startupAnimation && duration > 0 && startupTime < duration + SPLASH_FADE) {
            startupScene(startupTime, duration);
            trafficLights();
        }
        easterEgg();
        errorDialog();
        if (!windowRoundedByWindows) G.rrLine(.5f, .5f, WIN_W - 1, WIN_H - 1, RADIUS, rgba(255, 255, 255, 30), 1);
        return G.end();
    }
    // A click or Esc during the intro: it fades out right away instead of making the player wait for it
    bool skipIntro() {
        if (!Config::startupAnimation || splashFor <= 0) return false;
        float now = startupClock();
        if (now >= splashFor) return false;
        splashFor = std::max(.01, (double)now);
        wake(1);
        return true;
    }
    void mouseDown(int clicks) {
        // the window's lights always work, whatever is open: red closes the launcher, yellow hides it (as on a Mac)
        bool lights = minBtn.hit(mouseX, mouseY) || closeBtn.hit(mouseX, mouseY);
        if (lights) {
            Widget* w = minBtn.hit(mouseX, mouseY) ? (Widget*)&minBtn : (Widget*)&closeBtn;
            pressedW = w;
            w->pressed = true;
            wake(.3);
            return;
        }
        if (skipIntro()) return;
        if (eggOn()) {
            if (T - eggAt < EGG_LEN - .45f) eggAt = T - (EGG_LEN - .45f);
            wake(.6);
            return;
        }
        if(sourcesOpen && !inSources(mouseX,mouseY) && !sourcesBtn.hit(unpageX(mouseX),unpageY(mouseY))) {
            sourcesOpen=false; wake(.3); return;
        }
        if (previewOpen && loggedIn() &&
            !(mouseX >= pvX() && mouseX < pvX() + PV_W && mouseY >= pvY() && mouseY < pvY() + PV_H) &&
            !minBtn.hit(mouseX, mouseY) && !closeBtn.hit(mouseX, mouseY)) {
            closePreview();
            return;
        }                            // a click beside the preview closes it
        if (!modal() && sbOver()) {  // grab the thumb, or click the track to jump there
            float top, track, th, mx;
            sbGeom(top, track, th, mx);
            float ty = top + (track - th) * (scroll[page] / mx);
            sbGrab = mouseY >= ty && mouseY <= ty + th ? mouseY - ty : th / 2;
            sbDrag = true;
            sbTo(mouseY);
            wake(.3);
            return;
        }
        Widget* w = pick(mouseX, mouseY);
        if (w) {
            pressedW = w;
            w->pressed = true;
            setFocus(w->textual() ? w : nullptr);
            float lx = localX(w, mouseX), ly = localY(w, mouseY);
            if (!w->textual()) {  // where the press landed: the ripple grows from here
                w->rippleAt = T;
                w->rippleX = lx;
                w->rippleY = ly;
            }
            w->down(lx, ly, clicks);
        } else
            setFocus(nullptr);
        wake(.3);
    }
    void mouseMove() {
        if(sourcesOpen) for(int i=0;i<sourceCount();++i) if(sourceOptions[i].hit(mouseX,mouseY))sourceFocus=i;
        if (sbDrag) {
            sbTo(mouseY);
            wake(.3);
            return;
        }
        if (pressedW) {
            pressedW->drag(localX(pressedW, mouseX), localY(pressedW, mouseY));
        }
        wake(.3);
    }
    void mouseUp() {
        if (sbDrag) {
            sbDrag = false;
            wake(.3);
            return;
        }
        if (pressedW) {
            Widget* w = pressedW;
            float ly = localY(w, mouseY);
            w->pressed = false;
            pressedW = nullptr;
            bool inside = w->hit(localX(w, mouseX), ly);
            bool navigation = std::find(nav.begin(), nav.end(), w) != nav.end();
            if (inside && w->enabled && !w->textual() && !navigation && w != &soundsToggle) UiAudio::play(132);
            w->up(localX(w, mouseX), ly, inside);
        }
        wake(.3);
    }
    void wheel(float notches) {
        lastWheelAt = T;
        if (errOpen) return;
        if(sourcesOpen) {sourcesOpen=false;wake(.3);return;}
        if (previewOpen) {
            stepPreview(notches > 0 ? -1 : 1);
            return;
        }
        if (mouseX < SIDE || !loggedIn()) return;
        scroll[page] -= notches * 76;
        wake(.3);
    }
    void keyDown(int vk, bool ctrl, bool shift) {
        wake(.3);
        if (eggOn() && vk == VK_ESCAPE) {
            if (T - eggAt < EGG_LEN - .45f) eggAt = T - (EGG_LEN - .45f);
            return;
        }
        if (errOpen) {
            if (vk == VK_ESCAPE || vk == VK_RETURN) errOpen = false;
            else if (ctrl && vk == 'C') errCopy.click();
            return;
        }
        if (ctrl && loggedIn() && !modal() && vk >= '1' && vk <= '4') {  // Ctrl+1..4: the sidebar's pages, top to bottom
            nav[vk - '1']->click();
            return;
        }
        if (ctrl && (vk == 'F' || vk == 'K') && loggedIn() && !modal()) {  // Ctrl+F / Ctrl+K: the search
            setFocus(page == MODS ? &searchField : page == PACKS ? &packSearch : &sbSearch);
            return;
        }
        if (ctrl && vk == VK_OEM_COMMA && loggedIn() && !modal()) {  // Ctrl+, : Settings, as Cmd+, on a Mac
            openPage(SETTINGS);
            return;
        }
        if(sourcesOpen) {
            if(vk==VK_ESCAPE) sourcesOpen=false;
            else if(vk==VK_DOWN)sourceFocus=(sourceFocus+1)%sourceCount();
            else if(vk==VK_UP)sourceFocus=(sourceFocus+sourceCount()-1)%sourceCount();
            else if(vk==VK_RETURN || vk==VK_SPACE)sourceOptions[sourceFocus].click();
            return;
        }
        if (Config::startupAnimation && splashFor > 0 && startupClock() < splashFor + SPLASH_FADE) {
            if (vk == VK_ESCAPE || vk == VK_RETURN || vk == VK_SPACE) skipIntro();
            return;
        }
        if (previewOpen) {
            if (vk == VK_ESCAPE)
                closePreview();
            else if (vk == VK_LEFT)
                stepPreview(-1);
            else if (vk == VK_RIGHT)
                stepPreview(1);
            return;
        }
        if (focusW && focusW->key(vk, ctrl, shift)) return;
        if (vk == VK_RETURN) {
            if (cur == LOGIN)
                submit();
            else if (loggedIn() && !playBusy)
                play();
        } else if (vk == VK_ESCAPE)
            setFocus(nullptr);
    }
    void typed(wchar_t c) {
        if (errOpen) return;
        if (previewOpen) return;
        if (!focusW && loggedIn() && page == ACCOUNTS && addMode() && c >= 32 && c != 127)
            setFocus(&accNick);  // typing goes into the nickname
        if (!focusW && loggedIn() && (page == MODS || page == PACKS) && c >= 32 && c != 127)
            setFocus(page == PACKS ? &packSearch : &searchField);  // typing in the Catalogue or the Library goes into its search
        if (focusW) focusW->typed(c);
        wake(.3);
    }
};

static Scene* scene;
static void wake(double seconds) {
    if (scene) scene->hotUntil = std::max(scene->hotUntil, T + seconds);
}

// ================================================================================================ the window
static float dpiScale(HWND h) {
    if (HMODULE u = GetModuleHandleW(L"user32.dll")) {
        auto f = (UINT(WINAPI*)(HWND))GetProcAddress(u, "GetDpiForWindow");
        if (f && h) {
            UINT d = f(h);
            if (d) return d / 96.f;
        }
    }
    HDC dc = GetDC(nullptr);
    float s = GetDeviceCaps(dc, LOGPIXELSX) / 96.f;
    ReleaseDC(nullptr, dc);
    return s;
}
// Sizes the window for the current screen density, keeps its centre, and cuts the rounded corners
static void fitWindow(bool center) {
    float k = dpiScale(hwnd) * UI * .86f; // compact density; all hit areas share this transform
    {
        HMONITOR mon = MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST);
        MONITORINFO mi = {sizeof mi};
        if (GetMonitorInfoW(mon, &mi)) {
            float fit = std::min((mi.rcWork.right - mi.rcWork.left) * (windowZoomed ? .985f : .94f) / WIN_W,
                                 (mi.rcWork.bottom - mi.rcWork.top) * (windowZoomed ? .985f : .94f) / WIN_H);
            if (k > fit || windowZoomed) k = fit;
        }
    }
    G.scale = k;
    int w = (int)lroundf(WIN_W * k), h = (int)lroundf(WIN_H * k);
    RECT rc;
    GetWindowRect(hwnd, &rc);
    int x = rc.left + ((rc.right - rc.left) - w) / 2, y = rc.top + ((rc.bottom - rc.top) - h) / 2;
    if (center) {
        HMONITOR mon = MonitorFromWindow(hwnd, MONITOR_DEFAULTTOPRIMARY);
        MONITORINFO mi = {sizeof mi};
        GetMonitorInfoW(mon, &mi);
        x = mi.rcWork.left + ((mi.rcWork.right - mi.rcWork.left) - w) / 2;
        y = mi.rcWork.top + ((mi.rcWork.bottom - mi.rcWork.top) - h) / 2;
        // where the player left it last time, if that place is still on a screen (a monitor may be gone)
        POINT saved{Config::winX + w / 2, Config::winY + 20};
        if (!DESIGN_PREVIEW && Config::winX != INT_MIN && MonitorFromPoint(saved, MONITOR_DEFAULTTONULL)) {
            x = Config::winX;
            y = Config::winY;
        }
    }
    if (!DESIGN_PREVIEW) {
        MONITORINFO monitor = {sizeof monitor};
        if (GetMonitorInfoW(MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST), &monitor)) {
            x = std::clamp(x, (int)monitor.rcWork.left, (int)monitor.rcWork.right - w);
            y = std::clamp(y, (int)monitor.rcWork.top, (int)monitor.rcWork.bottom - h);
        }
    }
    bool resized = rc.right - rc.left != w || rc.bottom - rc.top != h;
    windowW = w, windowH = h;
    if (resized || rc.left != x || rc.top != y)
        SetWindowPos(hwnd, nullptr, x, y, w, h, SWP_NOZORDER | SWP_NOACTIVATE);
    if (resized) {
        if (!windowRoundedByWindows) {  // Windows 10: the corners are cut by a region (no shadow there)
            int r = (int)lroundf(RADIUS * 2 * k);
            SetWindowRgn(hwnd, CreateRoundRectRgn(0, 0, w + 1, h + 1, r, r), TRUE);
        }
        G.resize(w, h);
    }
    for (const char* key : {"bg:cache", "bg:login"}) {  // the background was composed for the old size
        auto it = G.bitmaps.find(key);
        if (it == G.bitmaps.end()) continue;
        if (it->second) it->second->Release();
        G.bitmaps.erase(it);
    }
}
static void frameWindow() {
    MARGINS m = {0, 0, 1, 0};
    DwmExtendFrameIntoClientArea(hwnd, &m);
    DWORD round = 2;  // DWMWA_WINDOW_CORNER_PREFERENCE (33) = DWMWCP_ROUND
    windowRoundedByWindows = SUCCEEDED(DwmSetWindowAttribute(hwnd, 33, &round, sizeof round));
    BOOL dark = TRUE;  // DWMWA_USE_IMMERSIVE_DARK_MODE (20): a dark border on Windows 11
    DwmSetWindowAttribute(hwnd, 20, &dark, sizeof dark);
    SetWindowPos(hwnd, nullptr, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE | SWP_FRAMECHANGED);
    Log::info(windowRoundedByWindows ? "window: Windows rounds it and casts its shadow" : "window: corners cut by a region");
}
static void toggleZoom() {
    windowZoomed = !windowZoomed;
    fitWindow(true);
    if (!windowZoomed && Config::winX != INT_MIN) {  // back where it was
        SetWindowPos(hwnd, nullptr, Config::winX, Config::winY, 0, 0, SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE);
    }
    wake(1);
}
static void previewUiScale(float scale) {
    uiScaleTarget = std::clamp(scale, .7f, 1.3f);
    wake(.5);
}
static void commitUiScale() {
    Config::uiScale = uiScaleTarget;
    Config::save();
}
static bool advanceUiScale(double dt) {
    if (fabsf(UI - uiScaleTarget) < .00001f) return false;
    UI += (uiScaleTarget - UI) * (1 - expf((float)-dt * 24));
    if (fabsf(UI - uiScaleTarget) < .0002f) UI = uiScaleTarget;
    fitWindow(false);
    if (!DESIGN_PREVIEW) {
        POINT pointer{}; GetCursorPos(&pointer); ScreenToClient(hwnd, &pointer);
        mouseX = pointer.x / G.scale; mouseY = pointer.y / G.scale;
    }
    return true;
}
static void applyUiScale(float scale) {
    previewUiScale(scale); UI = uiScaleTarget;
    commitUiScale(); fitWindow(false); wake(.5);
}
static void setMouse(LPARAM lp) {
    mouseX = GET_X_LPARAM(lp) / G.scale;
    mouseY = GET_Y_LPARAM(lp) / G.scale;
}

static LRESULT CALLBACK wndProc(HWND h, UINT msg, WPARAM wp, LPARAM lp) {
    static bool tracking = false;
    static DWORD lastClick = 0;
    static int clicks = 0;
    switch (msg) {
    case WM_APP: {
        vector<std::function<void()>> run;
        {
            std::lock_guard<std::mutex> l(queueLock);
            run.swap(queued);
        }
        for (auto& f : run) f();
        if (scene) scene->dirty = true;
        return 0;
    }
    case WM_ERASEBKGND:
        return 1;
    case WM_PAINT:
        if (scene) {
            scene->place();
            scene->paint();
        }
        ValidateRect(h, nullptr);
        return 0;
    case WM_PRINTCLIENT:
        if (scene) {
            scene->place();
            scene->paint();
        }
        return 0;
    case WM_DPICHANGED:
        fitWindow(false);
        if (scene) scene->dirty = true;
        return 0;
    case WM_NCHITTEST: {  // the empty strip at the top moves the window
        POINT p = {GET_X_LPARAM(lp), GET_Y_LPARAM(lp)};
        ScreenToClient(h, &p);
        float x = p.x / G.scale, y = p.y / G.scale;
        // the window moves by its toolbar and by the top of the sidebar, as a Mac window does
        bool bar = scene && (scene->loggedIn() ? (y < 54 && (x > SIDE || y < 42)) : y < 52);
        if (bar && !scene->pick(x, y)) return HTCAPTION;
        return HTCLIENT;
    }
    case WM_NCLBUTTONDBLCLK:  // a double click on the toolbar zooms, as on a Mac
        if (wp == HTCAPTION) toggleZoom();
        return 0;
    case WM_NCCALCSIZE:  // no frame at all: the whole window is ours to draw
        if (wp) return 0;
        break;
    case WM_NCACTIVATE:
        return DefWindowProcW(h, msg, wp, -1);
    case WM_GETMINMAXINFO:
        if (windowW > 0) {
            auto* mm = (MINMAXINFO*)lp;
            mm->ptMinTrackSize = mm->ptMaxTrackSize = mm->ptMaxSize = {windowW, windowH};
        }
        return 0;
    case WM_MOUSEMOVE:
        setMouse(lp);
        if (!tracking) {
            TRACKMOUSEEVENT t = {sizeof t, TME_LEAVE, h, 0};
            TrackMouseEvent(&t);
            tracking = true;
        }
        if (scene) {
            scene->mouseMove();
            Widget* w = scene->pick(mouseX, mouseY);
            SetCursor(LoadCursorW(nullptr, w && w->textual() ? IDC_IBEAM : w ? IDC_HAND : IDC_ARROW));
        }
        return 0;
    case WM_MOUSELEAVE:
        tracking = false;
        if (scene && !scene->pressedW) {
            mouseX = mouseY = -999;
            wake(.3);
        }
        return 0;
    case WM_LBUTTONDOWN:
    case WM_LBUTTONDBLCLK: {
        setMouse(lp);
        SetCapture(h);
        SetFocus(h);
        DWORD now = GetTickCount();
        clicks = now - lastClick < GetDoubleClickTime() ? clicks + 1 : 1;
        lastClick = now;
        if (scene) scene->mouseDown(clicks);
        return 0;
    }
    case WM_LBUTTONUP:
        setMouse(lp);
        ReleaseCapture();
        if (scene) scene->mouseUp();
        return 0;
    case WM_RBUTTONUP:
        return 0;
    case WM_MOUSEWHEEL:
        if (scene) scene->wheel(GET_WHEEL_DELTA_WPARAM(wp) / (float)WHEEL_DELTA);
        return 0;
    case WM_SETCURSOR:
        if (LOWORD(lp) == HTCLIENT) return 1;
        break;
    case WM_KEYDOWN:
        if (scene) scene->keyDown((int)wp, GetKeyState(VK_CONTROL) < 0, GetKeyState(VK_SHIFT) < 0);
        return 0;
    case WM_CHAR:
        if (scene && GetKeyState(VK_CONTROL) >= 0) scene->typed((wchar_t)wp);
        return 0;
    case WM_ACTIVATE:
        if (scene && !DESIGN_PREVIEW && LOWORD(wp) != WA_INACTIVE && scene->loggedIn()) scene->takeGameSession();
        wake(.5);
        break;
    case WM_SIZE:
        if (wp == SIZE_RESTORED && scene) {
            scene->dirty = true;
            wake(.5);
        }
        return 0;
    case WM_DISPLAYCHANGE:
    case WM_POWERBROADCAST:
        if (scene) {
            scene->dirty = true;
            wake(1);
        }
        break;
    case WM_CLOSE:
        if (scene)
            scene->close();
        else
            PostQuitMessage(0);
        return 0;
    case WM_DROPFILES: {
        HDROP drop = (HDROP)wp;
        vector<fs::path> files;
        UINT n = DragQueryFileW(drop, 0xFFFFFFFF, nullptr, 0);
        for (UINT i = 0; i < n; i++) {
            wchar_t path[MAX_PATH * 4];
            if (DragQueryFileW(drop, i, path, ARRAYSIZE(path))) files.push_back(path);
        }
        DragFinish(drop);
        if (scene && scene->loggedIn()) scene->dropFiles(files);
        return 0;
    }
    case WM_EXITSIZEMOVE: {  // the window is remembered where the player left it
        RECT rc;
        if (!DESIGN_PREVIEW && GetWindowRect(h, &rc)) {
            Config::winX = rc.left;
            Config::winY = rc.top;
            Config::save();
        }
        return 0;
    }
    case WM_DESTROY:
        PostQuitMessage(0);
        return 0;
    }
    return DefWindowProcW(h, msg, wp, lp);
}

// ================================================================================================ when it crashes anyway
// A crash leaves a short report and a minidump in ~/.arcanic/logs, copies the folder's path to the clipboard and tells the
// player where it is, so they can send us the files instead of a screenshot of a Windows error.
// The path into the clipboard as text (works with no window of ours alive)
static void clipboardPath(const wstring& path) {
    if (!OpenClipboard(nullptr)) return;
    EmptyClipboard();
    if (HGLOBAL g = GlobalAlloc(GMEM_MOVEABLE, (path.size() + 1) * sizeof(wchar_t))) {
        memcpy(GlobalLock(g), path.c_str(), (path.size() + 1) * sizeof(wchar_t));
        GlobalUnlock(g);
        SetClipboardData(CF_UNICODETEXT, g);
    }
    CloseClipboard();
}
static void revealFile(const wstring& path) {  // Explorer opens with the file selected
    ShellExecuteW(nullptr, L"open", L"explorer.exe", (L"/select,\"" + path + L"\"").c_str(), nullptr, SW_SHOWNORMAL);
}
static void logDialog(const wstring& title, const wstring& text, const fs::path& file) {
    wstring path = file.wstring();
    clipboardPath(path);
    bool en = Lang::en;
    wstring copyLabel = en ? L"Copy path" : L"Скопировать путь", openLabel = en ? L"Open log file" : L"Открыть файл лога",
            closeLabel = en ? L"Close" : L"Закрыть";
    wstring content = text + (en ? L"\n\nThe log file (send it to us on Discord):\n" : L"\n\nФайл с логом (пришли его нам в Discord):\n") +
                      L"<a href=\"open\">" + path + L"</a>";
    wstring footer = en ? L"The path is already copied: paste it into Explorer or send the file." : L"Путь уже скопирован: вставь его в Проводник или просто пришли файл.";
    typedef HRESULT(WINAPI * TaskDialogFn)(const TASKDIALOGCONFIG*, int*, int*, BOOL*);
    HMODULE cc = LoadLibraryW(L"comctl32.dll");
    auto show = cc ? (TaskDialogFn)GetProcAddress(cc, "TaskDialogIndirect") : nullptr;
    if (show) {
        struct Ctx { wstring path, copied; } ctx{path, en ? L"Copied: " + path : L"Скопировано: " + path};
        TASKDIALOG_BUTTON buttons[] = {{101, copyLabel.c_str()}, {102, openLabel.c_str()}, {IDCLOSE, closeLabel.c_str()}};
        TASKDIALOGCONFIG c = {sizeof c};
        c.dwFlags = TDF_ENABLE_HYPERLINKS | TDF_ALLOW_DIALOG_CANCELLATION | TDF_SIZE_TO_CONTENT;
        c.pszWindowTitle = L"Arcanic";
        c.pszMainIcon = TD_ERROR_ICON;
        c.pszMainInstruction = title.c_str();
        c.pszContent = content.c_str();
        c.pszFooter = footer.c_str();
        c.pszFooterIcon = TD_INFORMATION_ICON;
        c.pButtons = buttons;
        c.cButtons = 3;
        c.nDefaultButton = 101;
        c.lpCallbackData = (LONG_PTR)&ctx;
        c.pfCallback = [](HWND h, UINT msg, WPARAM wp, LPARAM lp, LONG_PTR ref) -> HRESULT {
            auto* x = (Ctx*)ref;
            if (msg == TDN_CREATED) SetWindowPos(h, HWND_TOPMOST, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE);
            if (msg == TDN_HYPERLINK_CLICKED) revealFile(x->path);
            if (msg == TDN_BUTTON_CLICKED && wp == 101) {
                clipboardPath(x->path);
                SendMessageW(h, TDM_SET_ELEMENT_TEXT, TDE_FOOTER, (LPARAM)x->copied.c_str());
                return S_FALSE;
            }
            if (msg == TDN_BUTTON_CLICKED && wp == 102) {
                revealFile(x->path);
                return S_FALSE;
            }
            return S_OK;
        };
        if (SUCCEEDED(show(&c, nullptr, nullptr, nullptr))) return;
    }
    wstring plain = text + (en ? L"\n\nThe log file:\n" : L"\n\nФайл с логом:\n") + path +
                    (en ? L"\n\nThe path is copied. Open the log file now?" : L"\n\nПуть уже скопирован. Открыть файл лога?");
    if (MessageBoxW(nullptr, plain.c_str(), L"Arcanic", MB_ICONERROR | MB_YESNO | MB_TOPMOST) == IDYES) revealFile(path);
}
static bool thrownInfo(const EXCEPTION_RECORD* r, char* type, size_t typeSize, char* what, size_t whatSize) {
    if (!r || r->ExceptionCode != 0xE06D7363 || r->NumberParameters < 4) return false;
    __try {
        uintptr_t base = r->ExceptionInformation[3];
        const int* throwInfo = (const int*)r->ExceptionInformation[2];  // attributes, unwind, forward compat, catchable types
        const int* types = (const int*)(base + throwInfo[3]);
        for (int i = 0; i < types[0] && i < 16; i++) {
            const int* ct = (const int*)(base + types[1 + i]);  // properties, type, this displacement, ...
            const char* name = (const char*)(base + ct[1]) + 2 * sizeof(void*);
            if (i == 0) strncpy_s(type, typeSize, name, _TRUNCATE);
            if (!strcmp(name, ".?AVexception@std@@"))
                strncpy_s(what, whatSize, ((const std::exception*)((const char*)r->ExceptionInformation[1] + ct[2]))->what(), _TRUNCATE);
        }
        return true;
    } __except (EXCEPTION_EXECUTE_HANDLER) {
        return false;
    }
}
// Where it happened: the call stack as module+offset, unwound from the exception's own context
static void stackOf(const CONTEXT* from, char* out, size_t size) {
    out[0] = 0;
#if defined(_M_X64)
    __try {
        CONTEXT c = *from;
        for (int i = 0; i < 28 && c.Rip; i++) {
            HMODULE m = nullptr;
            char name[MAX_PATH] = "?";
            if (GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS | GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT, (LPCSTR)c.Rip, &m)) {
                GetModuleFileNameA(m, name, MAX_PATH);
                if (const char* slash = strrchr(name, '\\')) memmove(name, slash + 1, strlen(slash));
            }
            size_t used = strlen(out);
            _snprintf_s(out + used, size - used, _TRUNCATE, "  %s+0x%llX\r\n", name, (unsigned long long)(c.Rip - (DWORD64)m));
            DWORD64 imageBase = 0;
            PRUNTIME_FUNCTION fn = RtlLookupFunctionEntry(c.Rip, &imageBase, nullptr);
            if (!fn) {  // a leaf function: the return address is on top of the stack
                c.Rip = *(DWORD64*)c.Rsp;
                c.Rsp += 8;
            } else {
                PVOID data = nullptr;
                DWORD64 frame = 0;
                RtlVirtualUnwind(UNW_FLAG_NHANDLER, imageBase, c.Rip, fn, &c, &data, &frame, nullptr);
            }
        }
    } __except (EXCEPTION_EXECUTE_HANDLER) {}
#endif
}
static LONG WINAPI crashed(EXCEPTION_POINTERS* info) {
    static std::atomic<bool> once{false};
    if (once.exchange(true)) return EXCEPTION_EXECUTE_HANDLER;
    SYSTEMTIME t;
    GetLocalTime(&t);
    makeDirs(Log::folder());
    string stamp = fmt("%04d%02d%02d-%02d%02d%02d", t.wYear, t.wMonth, t.wDay, t.wHour, t.wMinute, t.wSecond);
    fs::path report = Log::folder() / P("crash-" + stamp + ".txt"), dump = Log::folder() / P("crash-" + stamp + ".dmp");
    DWORD code = info ? info->ExceptionRecord->ExceptionCode : 0;
    void* at = info ? info->ExceptionRecord->ExceptionAddress : nullptr;
    HMODULE module = nullptr;
    wchar_t moduleName[MAX_PATH] = L"?";
    if (at && GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS | GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT, (LPCWSTR)at, &module))
        GetModuleFileNameW(module, moduleName, MAX_PATH);
    string text = string("Arcanic ") + Config::VERSION + " (by " + Config::authors() + ") crashed\r\n" + "authors: " + Wallpaper::signature() + "\r\n" + Log::systemLine() + "\r\n" +
                  (info ? fmt("exception 0x%08lX at ", code) + S(fs::path(moduleName).filename()) +
                              fmt("+0x%llX", (unsigned long long)((uintptr_t)at - (uintptr_t)module))
                        : string("std::terminate (an exception nobody caught)")) +
                  "\r\nlog: " + S(Log::file()) + "\r\n";
    if (info) {
        char type[256] = "", what[512] = "", stack[4096] = "";
        if (thrownInfo(info->ExceptionRecord, type, sizeof type, what, sizeof what))
            text += string("C++ exception ") + type + (what[0] ? string(": ") + what : string()) + "\r\n";
        stackOf(info->ContextRecord, stack, sizeof stack);
        if (stack[0]) text += string("stack:\r\n") + stack;
    }
    writeFile(report, text);
    Log::error("CRASH " + text);
    if (HMODULE help = LoadLibraryW(L"dbghelp.dll")) {
        typedef BOOL(WINAPI * WriteDump)(HANDLE, DWORD, HANDLE, MINIDUMP_TYPE, PMINIDUMP_EXCEPTION_INFORMATION,
                                         PMINIDUMP_USER_STREAM_INFORMATION, PMINIDUMP_CALLBACK_INFORMATION);
        auto write = (WriteDump)GetProcAddress(help, "MiniDumpWriteDump");
        HANDLE h = CreateFileW(dump.c_str(), GENERIC_WRITE, 0, nullptr, CREATE_ALWAYS, 0, nullptr);
        if (write && h != INVALID_HANDLE_VALUE) {
            MINIDUMP_EXCEPTION_INFORMATION e{GetCurrentThreadId(), info, FALSE};
            write(GetCurrentProcess(), GetCurrentProcessId(), h, MiniDumpNormal, info ? &e : nullptr, nullptr, nullptr);
        }
        if (h != INVALID_HANDLE_VALUE) CloseHandle(h);
    }
    logDialog(Lang::en ? L"Arcanic closed because of an error" : L"Arcanic закрылся из-за ошибки",
              Lang::en ? L"Something went wrong and the launcher had to stop. The report beside the log tells us exactly what happened."
                       : L"Что-то пошло не так, и лаунчеру пришлось закрыться. Отчёт рядом с логом покажет нам, что именно случилось.",
              report);
    return EXCEPTION_EXECUTE_HANDLER;
}
static __declspec(noinline) void crashTest() { throw std::runtime_error("crash test (--crash-test)"); }
static void terminated() {
    crashed(nullptr);
    _exit(3);
}

int WINAPI wWinMain(HINSTANCE inst, HINSTANCE, PWSTR, int) {
    int authArgc = 0;
    LPWSTR* authArgv = CommandLineToArgvW(GetCommandLineW(), &authArgc);
    int authResult = Microsoft::helper(authArgc, authArgv);
    if (authResult < 0) authResult = Media::helper(authArgc, authArgv);
    if (authResult < 0 && authArgc == 3 && !wcscmp(authArgv[1], L"--render-wallpapers")) {
        Wallpaper::renderAll(authArgv[2]);
        authResult = 0;
    }
    if (authResult < 0 && authArgc == 3 && !wcscmp(authArgv[1], L"--render-sounds")) {
        UiAudio::render(authArgv[2]);
        authResult = 0;
    }
    LocalFree(authArgv);
    if (authResult >= 0) return authResult;
    // one launcher at a time: a second start just brings the first to the front
    justUpdated = wcsstr(GetCommandLineW(), L"--updated") != nullptr;
    if (justUpdated) Sleep(400);
    HANDLE once = singleInstance = CreateMutexW(nullptr, TRUE, L"ArcanicLauncherSingleInstance");
    bool selfTest = wcsstr(GetCommandLineW(), L"-test") != nullptr;
    if (GetLastError() == ERROR_ALREADY_EXISTS && !wcsstr(GetCommandLineW(), L"--offscreen") && !justUpdated && !selfTest) {
        if (HWND other = FindWindowW(L"ArcanicLauncher", nullptr)) {
            ShowWindow(other, SW_RESTORE);
            SetForegroundWindow(other);
        }
        return 0;
    }
    DESIGN_PREVIEW = wcsstr(GetCommandLineW(), L"--design-preview") != nullptr;
    if (!DESIGN_PREVIEW) Updater::tidy();
    CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);
    Config::load();
    Lang::en = lower(Config::lang) != "ru";
    UI = uiScaleTarget = Config::uiScale;
    SetUnhandledExceptionFilter(crashed);
    std::set_terminate(terminated);
    if (wcsstr(GetCommandLineW(), L"--crash-test")) crashTest();
    if (wcsstr(GetCommandLineW(), L"--credits-test")) {  // the three kept copies of the authors' names must all read the same
        writeFile(Config::stateDir() / L"credits-test.txt", Config::authors() + "\n" + credit() + "\n" + Wallpaper::signature() + "\n");
        return 0;
    }
    if (wcsstr(GetCommandLineW(), L"--mods-switch-test")) {  // the per-version mod sets, in the folder ARCANIC_MC_DIR points at
        if (!hasEnv(L"ARCANIC_MC_DIR")) {  // never on the player's own game folder: the test moves and overwrites files
            writeFile(Config::stateDir() / L"mods-switch-test.txt", "REFUSED: set ARCANIC_MC_DIR to an empty test folder\n");
            return 1;
        }
        string out;
        fs::path mods = Game::modsDir(), store = Game::dir() / L"mods-versions";
        auto has = [](const fs::path& d, const char* n) { return isFile(d / P(n)); };
        auto check = [&](bool ok, const string& what) { out += (ok ? "PASS " : "FAIL ") + what + "\n"; };
        writeFile(mods / L"a.jar", "a"); writeFile(mods / L"b.jar.disabled", "b"); writeFile(mods / L".arcanic-managed", "a.jar\n");
        writeFile(mods / P(Config::MOD_FILE), "client");
        check(Game::switchMods("1.21.11") && Game::modsOwner() == "1.21.11", "first start marks the set");
        check(Game::switchMods("1.21.4"), "switch to 1.21.4");
        check(!has(mods, "a.jar") && !has(mods, "b.jar.disabled") && !has(mods, ".arcanic-managed") && has(mods, Config::MOD_FILE), "mods/ emptied, client kept");
        check(has(store / L"1.21.11", "a.jar") && has(store / L"1.21.11", "b.jar.disabled") && has(store / L"1.21.11", ".arcanic-managed"), "1.21.11 set put away whole");
        writeFile(mods / L"c.jar", "c");
        check(Game::switchMods("1.21.11"), "switch back to 1.21.11");
        check(has(mods, "a.jar") && has(mods, "b.jar.disabled") && !has(mods, "c.jar") && has(store / L"1.21.4", "c.jar"), "sets swapped both ways");
        string a;
        check(readFile(mods / L"a.jar", a) && a == "a", "contents intact");
        HANDLE lock = CreateFileW((mods / L"a.jar").c_str(), GENERIC_READ, FILE_SHARE_READ, nullptr, OPEN_EXISTING, 0, nullptr);
        check(!Game::switchMods("1.21.4"), "a held file stops the switch");
        CloseHandle(lock);
        check(Game::modsOwner() == "1.21.11" && has(mods, "a.jar") && has(mods, "b.jar.disabled") && !has(mods, "c.jar") && has(store / L"1.21.4", "c.jar"), "after a refused switch nothing moved");
        check(Game::switchMods("1.21.4") && has(mods, "c.jar") && Game::modsOwner() == "1.21.4", "switch works once the file is free");
        writeFile(Config::stateDir() / L"mods-switch-test.txt", out);
        return 0;
    }
    int testArgc = 0;
    LPWSTR* testArgv = CommandLineToArgvW(GetCommandLineW(), &testArgc);
    fs::path doctorRoot = testArgc == 3 && !wcscmp(testArgv[1], L"--doctor-test") ? fs::path(testArgv[2]) : fs::path();
    LocalFree(testArgv);
    if (!doctorRoot.empty()) {  // --doctor-test <folder with mods/ and logs/>
        fs::path root = doctorRoot;
        string out;
        auto check = [&](bool ok, const string& what) { out += string(ok ? "PASS " : "FAIL ") + what + "\n"; writeFile(root / L"doctor-test.txt", out); };
        check(Doctor::fits("1.21.11", Json::of("~1.21.11")), "~1.21.11 takes 1.21.11");
        check(Doctor::fits("1.21.11", Json::of("~1.21.4")) && !Doctor::fits("1.21.11", Json::of("~1.20.4")), "~ keeps the minor version, as Fabric does");
        check(Doctor::fits("1.21.11", Json::of(">=1.21.2 <1.22")), "a range with two ends");
        check(Doctor::fits("1.21.11", Json::of("1.21.x")), "1.21.x");
        check(!Doctor::fits("1.21.11", Json::of("1.20.1")), "an exact other version");
        check(Doctor::fits("0.17.2", Json::of(">=0.16.10")), "loader >=0.16.10");
        check(!Doctor::fits("0.16.9", Json::of(">=0.17.0")), "old loader refused");
        check(Doctor::fits("1.21.11", Json::parse("[\"1.20.1\", \"1.21.11\"]")), "a list takes any of its versions");
        check(Doctor::fits("1.21.11-rc.1", Json::of(">=1.21.10-")), "a pre-release with a trailing dash");
        Lang::en = false;
        auto rep = Doctor::scan(root / L"mods", "1.21.11");
        for (auto& f : rep.fixed) out += "  fixed: " + f + "\n";
        for (auto& f : rep.problems) out += "  problem: " + f + "\n";
        auto exists = [&](const wchar_t* n) { return isFile(root / L"mods" / n); };
        check(exists(L"forge-mod.jar.disabled"), "the Forge mod is off");
        check(exists(L"broken.jar.disabled"), "the broken jar is off");
        check(exists(L"dupe-1.0.jar.disabled") && exists(L"dupe-2.0.jar"), "the older copy is off, the newer stays");
        check(exists(L"old-version.jar.disabled"), "the mod for 1.20.1 is off");
        check(exists(L"needs-nothing-real.jar.disabled"), "the mod with an unknown library is off");
        check(exists(L"needs-cloth.jar"), "the mod that needs Cloth Config stays");
        bool cloth = false;
        std::error_code ec;
        for (auto& e : fs::directory_iterator(root / L"mods", ec)) cloth |= starts(lower(S(e.path().filename())), "cloth-config");
        check(cloth, "Cloth Config was installed");
        check(exists(L"breaker.jar.disabled") && exists(L"victim.jar"), "the mod that breaks another is off");
        check(!Doctor::loaderFits("0.17.2", rep.loaderNeeds) && Doctor::loaderFits("0.99.0", rep.loaderNeeds), "the loader range is passed on");
        auto again = Doctor::scan(root / L"mods", "1.21.11");
        check(again.fixed.empty(), "a second scan finds nothing to fix");
        auto d = Doctor::diagnose(root, 0);
        check(d.install.count("fabric-api") > 0, "the log: a missing Fabric API");
        check(d.off.count("victim") > 0, "the log: the mod whose code broke the start");
        check(d.jvmArgs, "the log: bad JVM options");
        writeFile(root / L"doctor-test.txt", out);
        return 0;
    }
    if (wcsstr(GetCommandLineW(), L"--catalog-test")) {
        string out;
        bool reached = Api::pickEntry();
        out += "server: " + Config::base + (reached ? "" : " (not reached)") + "\n";
        try {
            int total = 0, got = 0;
            for (int pg = 0; pg < 3; pg++) {
                auto hits = Modrinth::search("", Config::mcVersion, "mod", "", got, &total);
                out += fmt("mods page %d: %d hits (offset %d of %d): ", pg + 1, (int)hits.size(), got, total) + (hits.empty() ? "" : hits.front().title + " .. " + hits.back().title) + "\n";
                got += (int)hits.size();
            }
            auto found = Modrinth::search("sodium", Config::mcVersion, "mod", "", 0, &total);
            out += fmt("search sodium: %d of %d, first ", (int)found.size(), total) + (found.empty() ? "-" : found.front().title) + "\n";
            auto packs = Modrinth::search("", Config::mcVersion, "resourcepack", "", 0, &total);
            out += fmt("modrinth packs: %d of %d\n", (int)packs.size(), total);
        } catch (const std::exception& e) { out += string("modrinth error: ") + e.what() + "\n"; }
        try {
            auto pmc = PacksMC::search("", "");
            out += fmt("packsmc: %d packs, first ", (int)pmc.size()) + (pmc.empty() ? "-" : pmc.front().title) + "\n";
            auto pvp = PacksMC::search("bedwars", "");
            out += fmt("packsmc bedwars: %d packs\n", (int)pvp.size());
        } catch (const std::exception& e) { out += string("packsmc error: ") + e.what() + "\n"; }
        try {
            int total = 0, got = 0;
            for (int pg = 0; pg < 2; pg++) {
                auto hits = CurseForge::search("", Config::mcVersion, "", got, &total);
                out += fmt("curseforge page %d: %d hits (offset %d of %d): ", pg + 1, (int)hits.size(), got, total) + (hits.empty() ? "" : hits.front().title + " .. " + hits.back().title) + "\n";
                got += (int)hits.size();
            }
            auto opt = CurseForge::search("", Config::mcVersion, "optimization", 0, &total);
            out += fmt("curseforge optimization: %d of %d\n", (int)opt.size(), total);
            auto both = Scene::modPage("sodium", Config::mcVersion, "", 3, 0, 0);
            int fromCf = 0;
            for (auto& h : both.hits) fromCf += h.source == "curseforge";
            out += fmt("both sites, sodium: %d hits (%d CurseForge), totals %d + %d", (int)both.hits.size(), fromCf, both.mrTotal, both.cfTotal) +
                   (both.error.empty() ? "" : " error " + both.error) + "\n";
            std::set<string> seen;
            for (auto& h : both.hits) {
                string k = Modrinth::plain(h.title);
                if (!seen.insert(k).second) out += "DUPLICATE: " + h.title + "\n";
            }
            fs::path dir = Config::stateDir() / L"cf-install-test";
            std::error_code ec; fs::remove_all(dir, ec);
            CurseForge::install("cf:238222", Config::mcVersion, dir);  // JEI, with a required library
            for (auto& e : fs::directory_iterator(dir, ec)) out += "installed: " + S(e.path().filename()) + fmt(" (%d KB)\n", (int)(fileSize(e.path()) / 1024));
            CurseForge::install("cf:238222", Config::mcVersion, dir);  // again: nothing doubles
            int count = 0;
            for (auto& e : fs::directory_iterator(dir, ec)) count += e.is_regular_file() ? 1 : 0;
            out += fmt("files after a second install: %d\n", count);
            fs::remove_all(dir, ec);
            for (int pg = 0; pg < 4; pg++)
                for (auto& h : CurseForge::search("", Config::mcVersion, "", pg * 30, nullptr))
                    if (!h.direct) { out += "site-only mod: " + h.title + " -> " + h.webpage + "\n"; pg = 4; break; }
        } catch (const std::exception& e) { out += string("curseforge error: ") + e.what() + "\n"; }
        writeFile(Config::stateDir() / L"catalog-test.txt", out);
        return 0;
    }
    Log::info(string("start Arcanic ") + Config::VERSION + " by " + Config::authors() + (DESIGN_PREVIEW ? " (design preview)" : "") + " | " + Log::systemLine());
    Log::info("state: " + S(Config::stateDir()) + " | game: " + S(Game::dir()) + " | server: " + Config::base);
    if (!DESIGN_PREVIEW) Game::switchMods(Config::mcVersion);
    int argc = 0;
    LPWSTR* argv = CommandLineToArgvW(GetCommandLineW(), &argc);
    bool offscreen =
        false;
    for (int i = 1; i < argc; i++) {
        if (!wcscmp(argv[i], L"--base") && i + 1 < argc) Config::base = U(argv[i + 1]);
        if (!wcscmp(argv[i], L"--offscreen")) {
            offscreen = true;
            Config::softwareNow = true;
        }
    }
    {
        string& b = Config::base;
        while (!b.empty() && b.back() == '/') b.pop_back();
        bool local = b.find("://localhost") != string::npos || b.find("://127.0.0.1") != string::npos || b.find("://[::1]") != string::npos;
        if (starts(b, "http://") && !local) {
            Log::warn("server: plaintext base lifted to https");
            b = "https://" + b.substr(7);
        }
    }

    WNDCLASSEXW wc = {sizeof wc};
    wc.lpfnWndProc = wndProc;
    wc.hInstance = inst;
    wc.lpszClassName = L"ArcanicLauncher";
    wc.hIcon = wc.hIconSm = LoadIconW(inst, MAKEINTRESOURCEW(1));
    wc.hCursor = LoadCursorW(nullptr, IDC_ARROW);
    wc.style = CS_DBLCLKS;
    RegisterClassExW(&wc);
    hwnd = CreateWindowExW(WS_EX_APPWINDOW, L"ArcanicLauncher", L"Arcanic", WS_POPUP | WS_THICKFRAME | WS_CAPTION | WS_MINIMIZEBOX | WS_SYSMENU, 100,
                           100, 1020, 638, nullptr, nullptr, inst, nullptr);
    if (!hwnd || !G.init(hwnd)) {
        Log::error(fmt("the window could not start (%lu)", GetLastError()));
        logDialog(Lang::en ? L"Arcanic could not open its window" : L"Arcanic не смог открыть окно",
                  Lang::en ? L"Windows did not let the launcher draw its window (often an old or broken graphics driver)."
                           : L"Windows не дал лаунчеру нарисовать окно (часто из-за старого или сломанного драйвера видеокарты).",
                  Log::file());
        return 1;
    }
    DragAcceptFiles(hwnd, TRUE);  // resource packs (.zip) and mods (.jar) can be dropped onto the window
    frameWindow();
    if (!offscreen && Config::launcherSounds) UiAudio::init();
    fitWindow(true);
    scene = new Scene();
    if (DESIGN_PREVIEW) {
        scene->cur = READY; scene->version = "1.6.7"; scene->userName = "Player"; scene->plan = "Free";
        scene->online = 24; scene->splashFor = 0;
        scene->setNote(Microsoft::message("Готово к запуску · Java 21 + Fabric", "Ready to launch · Java 21 + Fabric"), MUTED);
        const char* titles[] = {"Fabric API", "Sodium", "Iris Shaders", "Mod Menu", "Entity Culling", "Lithium", "FerriteCore", "ImmediatelyFast"};
        const char* details[] = {"Essential hooks and compatibility for Fabric mods.", "A modern rendering engine for a smoother Minecraft.", "Bring your favourite shader packs into the game.", "Browse and configure the mods you have installed.", "Skip rendering objects hidden behind solid blocks.", "Game logic optimizations with familiar behaviour.", "Lower memory use for bigger adventures.", "Improve immediate rendering performance."};
        for (int i = 0; i < 8; i++) {
            Modrinth::Hit h; h.id = "preview" + std::to_string(i); h.title = titles[i]; h.description = details[i]; h.downloads = (i + 1) * 1234567;
            if (i % 2) { h.source = "curseforge"; h.webpage = "https://www.curseforge.com/minecraft/mc-mods/preview"; h.direct = i != 3; }
            scene->catalog.push_back(h);
            string bytes; if (readFile(Config::stateDir() / P("preview-icon" + std::to_string(i) + ".png"), bytes)) scene->iconBytes["mr:" + h.id] = bytes;
        }
        const char* packs[] = {"Fresh Animations", "Faithful 32x", "Complementary", "Bare Bones", "Stay True", "Better Leaves"};
        for (int i = 0; i < 6; i++) { Modrinth::Hit h; h.id = "packpreview" + std::to_string(i); h.title = packs[i]; h.description = "A fresh look for the world you know and love."; h.kind = "resourcepack"; h.source = "packsmc"; h.webpage = "https://packsmc.com/pack/preview"; h.downloads = (i + 1) * 342567; scene->packCatalog.push_back(h); }
        scene->catalogLoaded = scene->packLoaded = true;
        scene->catalogFor = scene->modKey(); scene->packCatalogFor = scene->packKey();
        scene->searchCache[scene->popularKey(false)] = scene->catalog; scene->searchCache[scene->popularKey(true)] = scene->packCatalog;
    }
    if (!DESIGN_PREVIEW && Log::windowsMajor() < 10)
        scene->showError("Старая версия Windows",
                         "Minecraft 1.21 и Java 21 работают на Windows 10 и 11. Лаунчер откроется, но игра может не запуститься.",
                         Log::file());
    if (offscreen) {
        SetWindowPos(hwnd, nullptr, -6000, -6000, 0, 0, SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE);
        ShowWindow(hwnd, SW_SHOWNOACTIVATE);
    } else {
        ShowWindow(hwnd, SW_SHOW);
        UpdateWindow(hwnd);
        UiAudio::play(130);
    }

    LARGE_INTEGER freq, last;
    QueryPerformanceFrequency(&freq);
    QueryPerformanceCounter(&last);
    for (bool running = true; running;) {
        MSG msg;
        while (PeekMessageW(&msg, nullptr, 0, 0, PM_REMOVE)) {
            if (msg.message == WM_QUIT) {
                running = false;
                break;
            }
            TranslateMessage(&msg);
            DispatchMessageW(&msg);
        }
        if (!running) break;
        LARGE_INTEGER now;
        QueryPerformanceCounter(&now);
        double dt = (now.QuadPart - last.QuadPart) / (double)freq.QuadPart;
        last = now;
        if (offscreen && DESIGN_PREVIEW) {
            string command; auto req = Config::stateDir() / L"preview.request";
            if (readFile(req, command)) {
                removeFile(req); command = trim(command);
                if (starts(command, "startup:")) { previewStartupTime = (float)atof(command.substr(8).c_str()); scene->dirty = true; }
                else previewStartupTime = -1;
                if (!starts(command, "page-time:")) scene->previewPageTime = -1;
                if (starts(command, "page-time:")) {
                    scene->previewPageTime = (float)atof(command.substr(10).c_str()); scene->dirty = true;
                    writeFile(Config::stateDir() / L"page-motion.txt", std::to_string(scene->pageOpacity()) + " / " + std::to_string(scene->pageShift()));
                }
                else if (starts(command, "startup:")) { wake(.1); }
                else if (starts(command, "scale:")) {
                    float scale = (float)atof(command.substr(6).c_str());
                    scene->menuScale.value = clamp01((scale - .7f) / .6f); applyUiScale(scale);
                    scene->dirty = true;
                }
                else if (command == "check-scale") {
                    string errors;
                    scene->cur = READY; scene->openPage(SETTINGS); scene->previewPageTime = .8f; scene->place();
                    float saved = Config::uiScale;
                    for (float scale : {.70f, .85f, 1.f, 1.30f}) {
                        float before = UI, v = (scale - .7f) / .6f;
                        auto& slider = scene->menuScale;
                        slider.down(slider.x + 10 + (slider.w - 20) * v, slider.y + slider.h / 2, 1);
                        for (int frame = 0; frame < 30; ++frame) advanceUiScale(1.0/120);
                        if (fabsf(before - scale) > .01f && fabsf(UI - before) < .01f) errors += "No live scale preview while dragging\n";
                        if (!slider.dragging) errors += "Resize ended the drag\n";
                        slider.up(0, 0, true);
                        for (int frame = 0; frame < 90; ++frame) advanceUiScale(1.0/120);
                        if (fabsf(UI - scale) > .001f) errors += "Slider did not settle at scale\n";
                        Config::uiScale = 1.1f; Config::load();
                        if (fabsf(Config::uiScale - scale) > .001f) errors += "Scale did not persist\n";
                        RECT window{}; GetWindowRect(hwnd, &window);
                        if (abs((window.right - window.left) - (int)lroundf(WIN_W * G.scale)) > 1) errors += "Window scale mismatch\n";
                        POINT point{(LONG)lroundf(slider.x * G.scale + 20 * G.scale), (LONG)lroundf((slider.y + slider.h / 2) * G.scale)};
                        setMouse(MAKELPARAM(point.x, point.y));
                        if (scene->pick(mouseX, mouseY) != &slider) errors += "Scaled slider hit target missed\n";
                    }
                    scene->resetScale.click();
                    for (int frame = 0; frame < 120; ++frame) advanceUiScale(1.0/120);
                    if (fabsf(UI - 1) > .001f || fabsf(scene->menuScale.value - .5f) > .001f) errors += "Scale reset failed\n";
                    applyUiScale(saved); scene->menuScale.value = clamp01((saved - .7f) / .6f);
                    scene->previewPageTime = -1;
                    writeFile(Config::stateDir() / L"scale.check", errors.empty() ? "PASS" : errors);
                }
                else if (command == "check-admin") {  // the Premium controls: only an admin has them, and they refuse bad input
                    string errors;
                    auto has = [&](Widget* w) { for (auto* x : scene->pageWidgets()) if (x == w) return true; return false; };
                    scene->cur = READY; scene->openPage(SETTINGS);
                    string savedToken = scene->session.token, savedRole = scene->role;
                    scene->session.token = "preview"; scene->role = ""; scene->place();
                    for (Widget* w : {(Widget*)&scene->grantLogin, (Widget*)&scene->grantBtn, (Widget*)&scene->grantDays, (Widget*)&scene->lifetimeBtn, (Widget*)&scene->premListBtn})
                        if (has(w)) errors += "A player sees the admin controls\n";
                    if (scene->settingsGeom().accountH != 64) errors += "A player's card has the admin room\n";
                    scene->session.token.clear(); scene->role = "admin"; scene->place();
                    if (has(&scene->grantBtn)) errors += "Signed out, the admin controls show\n";
                    scene->session.token = "preview"; scene->role = "admin"; scene->place();
                    for (Widget* w : {(Widget*)&scene->grantLogin, (Widget*)&scene->grantBtn, (Widget*)&scene->grantDays, (Widget*)&scene->lifetimeBtn, (Widget*)&scene->premListBtn})
                        if (!has(w)) errors += "An admin misses a control\n";
                    scene->grantDays.set(""); scene->grantDays.edit(L"1a2-3");
                    if (scene->grantDays.value() != "123") errors += "Days take more than digits: " + scene->grantDays.value() + "\n";
                    scene->grantLogin.set(""); scene->grantNote.clear(); scene->grantBtn.click();
                    if (scene->grantBusy || scene->grantNote != "Введи логин") errors += "Grant without a login went on\n";
                    scene->grantLogin.set("someone"); scene->grantDays.set("0"); scene->grantNote.clear(); scene->grantBtn.click();
                    if (scene->grantBusy || scene->grantNote.find("3650") == string::npos) errors += "Grant with 0 days went on\n";
                    scene->grantDays.set("9999"); scene->grantNote.clear(); scene->grantBtn.click();
                    if (scene->grantBusy || scene->grantNote.find("3650") == string::npos) errors += "Grant with 9999 days went on\n";
                    scene->grantLogin.set(""); scene->grantDays.set(""); scene->grantNote.clear(); scene->grantLogin.error = scene->grantDays.error = false;
                    scene->session.token = savedToken; scene->role = savedRole;
                    writeFile(Config::stateDir() / L"admin.check", errors.empty() ? "PASS" : errors);
                }
                else if (command == "check-motion") {
                    string errors;
                    for (float phase : {0.f, .10f, .28f, .48f, .62f, .80f}) {
                        scene->previewPageTime = phase; scene->place();
                        float opacity = scene->pageOpacity(), offset = scene->pageShift();
                        if (opacity < 0 || opacity > 1 || fabsf(offset) > 24.001f) errors += "Invalid reveal range\n";
                        if (phase >= Scene::PAGE_REVEAL_SECONDS && (opacity != 1 || scene->pageDepthScale() != 1)) errors += "Reveal did not settle\n";
                        for (const auto& entry : scene->installBtns) {
                            auto* b = entry.second.get(); float sy = scene->scrollY();
                            if (b->visible && b->enabled && b->y - sy > scene->listTop() && b->y + b->h - sy < WIN_H &&
                                scene->pick(scene->pageX(b->x + b->w / 2), scene->pageY(b->y + b->h / 2 - sy)) != b)
                                errors += "Moving install target missed\n";
                        }
                    }
                    scene->previewPageTime = -1;
                    writeFile(Config::stateDir() / L"motion.check", errors.empty() ? "PASS" : errors);
                }
                else if (starts(command, "backdrop-time:")) {
                    previewBackdropTime = (float)atof(command.substr(14).c_str()); scene->dirty = true;
                }
                else if (command == "hover-start" || command == "hover-discord") {
                    auto& button = command == "hover-start" ? scene->loginBtn : scene->discordBtn;
                    mouseX = button.x + button.w / 2; mouseY = button.y + button.h / 2; scene->mouseMove();
                }
                else if (command == "mod-sources-menu") {
                    scene->cur=READY; scene->openPage(MODS); scene->modsRemote=true; scene->place();
                    scene->sourcesOpen=false; scene->sourceMenu();
                }
                else if (command == "sources-menu") {
                    scene->cur=READY; scene->openPage(PACKS); scene->packsRemote=true; scene->place();
                    Lang::en=true; scene->sourcesOpen=false; scene->sourceMenu();
                }
                else if (command == "check-source-popup") {
                    auto savedPage=scene->page; int savedSource=Config::packSources;
                    auto savedPacks=scene->packCatalog; bool savedLang=Lang::en; string errors;
                    scene->cur=READY; scene->openPage(PACKS); scene->packsRemote=true; scene->previewPageTime=1; scene->place();
                    scene->sourcesOpen=false; scene->sourceMenu(); scene->placeSources();
                    auto& option=scene->sourceOptions[1]; mouseX=option.x+20;mouseY=option.y+16;
                    if(scene->pick(mouseX,mouseY)!=&option)errors+="Popup row not clickable\n";
                    scene->mouseDown(1);scene->mouseUp();
                    if(scene->sourcesOpen || Config::packSources!=2) errors+="Mouse selection failed\n";
                    scene->sourceMenu();scene->keyDown(VK_DOWN,false,false);scene->keyDown(VK_RETURN,false,false);
                    if(scene->sourcesOpen || Config::packSources!=3)errors+="Keyboard selection failed\n";
                    scene->sourceMenu();scene->keyDown(VK_ESCAPE,false,false);
                    if(scene->sourcesOpen)errors+="Escape did not dismiss\n";
                    scene->sourceMenu();mouseX=20;mouseY=400;scene->mouseDown(1);
                    if(scene->sourcesOpen || scene->pressedW)errors+="Outside click was not consumed\n";
                    Lang::en=true;
                    if(Lang::t("Источники")!="Sources" || Lang::t("Добавить ZIP")!="Add ZIP") errors+="English label missing\n";
                    Lang::en=false;if(Lang::t("Источники")!="Источники")errors+="Russian label missing\n";
                    scene->sourcesOpen=false;scene->selectPackSource(savedSource);scene->packCatalog=savedPacks;
                    scene->openPage(savedPage);scene->previewPageTime=-1;Lang::en=savedLang;mouseX=mouseY=-999;
                    writeFile(Config::stateDir()/L"source-popup.check",errors.empty()?"PASS":errors);
                }
                else if (command == "check-sources") {
                    int saved=Config::packSources; auto savedPacks=scene->packCatalog; string errors;
                    for(int mask:{1,2,3}) {
                        scene->selectPackSource(mask); Config::packSources=0; Config::load();
                        if(Config::packSources!=mask) errors+="Source selection did not persist\n";
                        if(scene->providerAllowed(true,"packsmc")!=bool(mask&1) || scene->providerAllowed(true,"modrinth")!=bool(mask&2)) errors+="Wrong provider filter\n";
                        if(scene->providerAllowed(false,"packsmc")) errors+="Unavailable provider in mods\n";
                    }
                    scene->selectPackSource(saved); scene->packCatalog=savedPacks;
                    writeFile(Config::stateDir()/L"sources.check", errors.empty()?"PASS":errors);
                }
                else if (command == "check-catalog") {
                    auto mods = scene->catalog, packs = scene->packCatalog;
                    auto cache = scene->searchCache; string modFor = scene->catalogFor, packFor = scene->packCatalogFor;
                    unsigned mr = scene->modRequest, pr = scene->packRequest;
                    bool mb = scene->catalogBusy, pb = scene->packBusy;
                    Modrinth::Hit mod; mod.id = "fixture-mod"; mod.kind = "mod";
                    Modrinth::Hit pack; pack.id = "fixture-pack"; pack.kind = "resourcepack"; pack.source = "packsmc";
                    string errors;
                    scene->modRequest = scene->packRequest = 100;
                    scene->catalogFor = "sentinel";
                    scene->completeSearch(false,99,scene->modKey(),{mod},false);
                    if (scene->catalogFor != "sentinel") errors += "Stale request was published\n";
                    scene->completeSearch(false,100,"old-query",{mod},false);
                    if (scene->catalogFor != "sentinel") errors += "Old query was published\n";
                    scene->completeSearch(false,100,scene->modKey(),{mod,pack},false);
                    scene->completeSearch(true,100,scene->packKey(),{mod,pack},false);
                    if (scene->catalog.size()!=1 || scene->catalog[0].kind!="mod") errors += "Mixed mod results\n";
                    if (scene->packCatalog.size()!=1 || scene->packCatalog[0].kind!="resourcepack") errors += "Mixed resourcepack results\n";
                    if (scene->modKey()==scene->packKey()) errors += "Shared type cache key\n";
                    scene->catalog=mods;scene->packCatalog=packs;scene->searchCache=cache;
                    scene->catalogFor=modFor;scene->packCatalogFor=packFor;
                    scene->modRequest=mr;scene->packRequest=pr;scene->catalogBusy=mb;scene->packBusy=pb;
                    writeFile(Config::stateDir()/L"catalog.check",errors.empty()?"PASS":errors);
                }
                else if (starts(command, "scroll:")) {  // the page scrolled to a point (for pictures of its lower part)
                    float to = (float)atof(command.substr(7).c_str());
                    scene->scroll[scene->page] = to; scene->place(); to = scene->scroll[scene->page];
                    an.set("scroll:" + std::to_string(scene->page), to); scene->dirty = true;
                }
                else if (command == "query-preview") {
                    scene->cur = READY; scene->openPage(PACKS); scene->packSearch.set("Vanilla"); scene->setFocus(&scene->packSearch);
                }
                else if (command == "perf-packs") {  // frame cost of the full resource-pack catalogue (all sources)
                    auto savedPacks = scene->packCatalog; int savedSource = Config::packSources;
                    scene->packCatalog.clear();
                    for (int i = 0; i < 48; i++) {
                        Modrinth::Hit h; h.id = "perf" + std::to_string(i); h.title = "Пак номер " + std::to_string(i);
                        h.description = "автор" + std::to_string(i) + " · 16x · Красивый ресурспак для PvP с плавными анимациями, "
                                        "чистыми текстурами и улучшенной производительностью номер " + std::to_string(i * 7) + ". Подходит для Bedwars, SkyWars и Duels. Небо, оружие, броня, частицы и интерфейс переделаны вручную, а все предметы сохраняют ванильную узнаваемость, чтобы играть было удобно и привычно даже на слабых компьютерах без потери FPS.";
                        h.kind = "resourcepack"; h.source = i % 2 ? "packsmc" : "modrinth"; h.webpage = "https://packsmc.com/pack/x";
                        scene->packCatalog.push_back(h);
                    }
                    Config::packSources = 3;
                    scene->cur = READY; scene->openPage(PACKS); scene->packsRemote = true; scene->packLoaded = true;
                    scene->packCatalogFor = scene->packKey(); scene->previewPageTime = 1;
                    string report;
                    for (int pass = 0; pass < 2; pass++) {
                        LARGE_INTEGER f0, a0, b0; QueryPerformanceFrequency(&f0);
                        for (int k = 0; k < 10; k++) { scene->place(); scene->paint(); T += 1 / 60.0; an.frame++; }
                        QueryPerformanceCounter(&a0);
                        for (int k = 0; k < 60; k++) { scene->place(); scene->paint(); T += 1 / 60.0; an.frame++; }
                        QueryPerformanceCounter(&b0);
                        report += fmt("pass %d  %.2f ms/frame  layouts=%d\n", pass + 1,
                                      (b0.QuadPart - a0.QuadPart) * 1000.0 / f0.QuadPart / 60, (int)G.layouts.size());
                    }
                    Config::packSources = savedSource; scene->packCatalog = savedPacks; scene->previewPageTime = -1;
                    writeFile(Config::stateDir() / L"perf.txt", report);
                }
                else if (command == "search") { scene->cur = READY; scene->sbSearch.set("sodium"); scene->searchTyped(); scene->setFocus(&scene->sbSearch); }
                else if (command == "check-lights") {  // yellow must minimise, then red must quit (with an alert open)
                    scene->cur = READY; scene->showError("Проверка", "Окно поверх страницы", Log::file()); scene->place();
                    auto press = [&](Widget& w) { mouseX = w.x + w.w / 2; mouseY = w.y + w.h / 2; scene->mouseDown(1); scene->mouseUp(); };
                    press(scene->minBtn);
                    string r = IsIconic(hwnd) ? "min:PASS" : "min:FAIL";
                    ShowWindow(hwnd, SW_SHOWNOACTIVATE);
                    writeFile(Config::stateDir() / L"lights.check", r);
                    press(scene->closeBtn);
                }
                else if (starts(command, "egg:")) {  // a frame of the easter egg at a moment (for pictures)
                    float at = (float)atof(command.substr(4).c_str());
                    scene->eggAt = T - at; scene->eggBite = at >= 1; scene->dirty = true;
                }
                else if (command == "mods-scan") { scene->scan(); }
                else if (command == "updates-check") { scene->updatesFor.clear(); scene->updatesAskedAt = -100; scene->checkModUpdates(); }
                else if (command == "updates-apply") { scene->updateAllMods(); }
                else if (command == "updates-state") {
                    string s = fmt("mods %d, updates %d, busy %d/%d\n", (int)scene->mods.size(), (int)scene->modUpdates.size(), scene->updatesBusy ? 1 : 0, scene->updatingAll ? 1 : 0);
                    for (auto& kv : scene->modUpdates) s += kv.first + " -> " + kv.second.filename + " (" + kv.second.version + ")\n";
                    std::error_code ec;
                    for (auto& e : fs::directory_iterator(Game::modsDir(), ec)) s += "  file: " + S(e.path().filename()) + "\n";
                    writeFile(Config::stateDir() / L"updates-state.txt", s);
                }
                else if (command == "watch-test") {  // the PacksMC download watch, without the browser
                    Modrinth::Hit h; h.id = "packsmc:watch-test"; h.title = "Watch test"; h.source = "packsmc"; h.kind = "resourcepack";
                    scene->watchDownload(h);
                }
                else if (command == "watch-state") {
                    writeFile(Config::stateDir() / L"watch-state.txt", scene->installState.count("packsmc:watch-test") ? scene->installState["packsmc:watch-test"] : string("none"));
                }
                else if (command == "error-card") { scene->showError("Не удалось запустить игру", "Не удалось: HTTP 503", Log::file()); }
                else if (command == "quit") { PostQuitMessage(0); }
                else if (command == "localmods") { scene->cur = READY; scene->openPage(MODS); scene->nav[2]->click(); }
                else if (command == "localpacks") { scene->cur = READY; scene->openPage(PACKS); scene->nav[2]->click(); }
                else if (command == "categories") { scene->categoryToggle.click(); }
                else if (command == "account-list") {
                    Config::accounts = "Player,Alex";
                    Microsoft::Account example; example.name = "LicensedPlayer"; example.uuid = "preview-licensed";
                    scene->microsoftAccounts = {example}; scene->accAdding = false;
                    scene->cur = READY; scene->openPage(ACCOUNTS); scene->place();
                }
                else if (command == "nav-swipe") { scene->openPage(SETTINGS); }
                else if (command == "play-state") {
                    writeFile(Config::stateDir() / L"play-state.txt", std::to_string(scene->playBtn.jellyScale) + " / " + (scene->playBtn.hovered ? "hover" : "idle"));
                }
                else if (command == "nav-position") {
                    writeFile(Config::stateDir() / L"nav-position.txt", std::to_string(an.get("nav.text." + std::to_string(scene->page), -1)) + " / target 1");
                }
                else if (command == "hover-player") { mouseX = scene->playBtn.x + scene->playBtn.w / 2; mouseY = scene->playBtn.y + scene->playBtn.h / 2; scene->mouseMove(); }
                else if (command == "hover-footer") { mouseX = scene->accountRow.x + scene->accountRow.w / 2; mouseY = scene->accountRow.y + scene->accountRow.h / 2; scene->mouseMove(); }
                else if (command == "hover-install") {
                    scene->place();
                    if (!scene->catalog.empty()) {
                        auto it = scene->installBtns.find(scene->catalog.front().id);
                        if (it != scene->installBtns.end()) { auto* b = it->second.get(); mouseX = b->x + b->w / 2; mouseY = b->y + b->h / 2 - scene->scrollY(); scene->mouseMove(); }
                    }
                }
                else if (command == "unhover") { mouseX = mouseY = -999; scene->mouseMove(); }
                else if (command == "check") {
                    scene->place();
                    string errors;
                    
                    
                    if (scene->page == SETTINGS) {
                        bool previous = Config::launcherSounds;
                        scene->soundsToggle.onChange(false); Config::launcherSounds = true; Config::load();
                        if (Config::launcherSounds) errors += "Sound mute did not persist\n";
                        bool previousAnimation = Config::startupAnimation;
                        scene->startupToggle.onChange(false); Config::startupAnimation = true; Config::load();
                        if (Config::startupAnimation) errors += "Startup animation setting did not persist\n";
                        scene->startupToggle.onChange(previousAnimation);
                        scene->soundsToggle.onChange(previous);
                    }
                    for (Widget* button : std::initializer_list<Widget*>{&scene->playBtn, &scene->accountRow, &scene->sbSearch, &scene->closeBtn, &scene->minBtn, &scene->greenBtn, &scene->navBack, &scene->navFwd})
                        if (button->visible && button->enabled && scene->pick(button->x + button->w / 2, button->y + button->h / 2) != button) errors += "Hit target: " + button->id + "\n";
                    for (auto& row : scene->favRows)
                        if (row->visible && row->enabled && scene->pick(row->x + 30, row->y + row->h / 2) != row.get()) errors += "Version row hit target: " + row->id + "\n";
                    for (auto* button : scene->nav) if (button->visible && button->enabled && scene->pick(button->x + button->w / 2, button->y + button->h / 2) != button) errors += "Navigation hit target: " + button->id + "\n";
                    for (const auto& entry : scene->installBtns) {
                        if ((scene->page != MODS && scene->page != PACKS) || (scene->page == MODS && !scene->modsRemote) || (scene->page == PACKS && !scene->packsRemote)) break;
                        auto* button = entry.second.get();
                        float sy = scene->scrollY();
                        if (button->y - sy > scene->listTop() && button->y + button->h - sy < WIN_H) {
                            mouseX = scene->pageX(button->x + button->w / 2); mouseY = scene->pageY(button->y + button->h / 2 - sy);
                            if (scene->pick(mouseX, mouseY) != button) errors += "Install hit target: " + entry.first + "\n";
                        }
                    }
                    mouseX = mouseY = -999;
                    BYTE opacity = 0; DWORD flags = 0; COLORREF color = 0;
                    if (GetWindowLongPtrW(hwnd, GWL_EXSTYLE) & WS_EX_LAYERED) errors += "Window is still transparent\n";
                    writeFile(Config::stateDir() / L"preview.check", errors.empty() ? "PASS" : errors);
                }
                else if (command == "login") { scene->cur = LOGIN; scene->pageAt = T; scene->dirty = true; }
                else if (command == "admin-premium") {  // the admins' Premium corner with a sample list, one row open
                    scene->cur = READY; scene->session.token = "preview"; scene->role = "admin"; scene->userName = "Admin"; scene->plan = "beta";
                    time_t now = time(nullptr);
                    scene->premRows = {{"alex", "Alex", "admin", 0}, {"kira_x", "kira_x", "admin", (int64_t)now + 26 * 86400},
                                       {"steve", "Steve", "admin", (int64_t)now + 2 * 86400}};
                    scene->premOpen = true; scene->premSel = "kira_x"; scene->premDays.set("14");
                    scene->grantLogin.set(""); scene->grantDays.set(""); scene->grantNote.clear();
                    scene->openPage(SETTINGS);
                }
                else if (command == "admin-grant") {  // a login and days typed, the last grant's note under the row
                    scene->cur = READY; scene->session.token = "preview"; scene->role = "admin"; scene->userName = "Admin"; scene->plan = "beta";
                    scene->premOpen = false; scene->grantLogin.set("kira_x"); scene->grantDays.set("45");
                    scene->grantNote = "Premium выдан"; scene->grantName = "Alex  ·  " + Lang::t("Навсегда"); scene->grantColor = OK;
                    scene->openPage(SETTINGS);
                }
                else if (command == "ru") { Lang::en = false; scene->dirty = true; }
                else if (command == "en") { Lang::en = true; scene->dirty = true; }
                else { scene->cur = READY; scene->modsRemote = scene->packsRemote = true; Page page = command == "mods" ? MODS : command == "packs" ? PACKS : command == "settings" ? SETTINGS : command == "accounts" ? ACCOUNTS : HOME; scene->openPage(page); }
                wake(.5);
            }
        }
        if (offscreen) {  // automated checks: a file asks for a picture of the next frame
            fs::path req = Config::stateDir() / L"snap.request";
            string to;
            if (readFile(req, to) && !trim(to).empty()) {
                removeFile(req);
                G.snapTo = P(trim(to));
                scene->dirty = true;
            }
        }
        bool drew = !IsIconic(hwnd) && IsWindowVisible(hwnd) && scene->tick(dt);
        if (!drew)
            MsgWaitForMultipleObjects(0, nullptr, FALSE, scene->live() ? 4 : 100, QS_ALLINPUT);
        else {
            LARGE_INTEGER after;
            QueryPerformanceCounter(&after);
            if ((after.QuadPart - now.QuadPart) * 1000 / freq.QuadPart < 1) Sleep(0);
        }  // a driver that does not wait for the refresh must not make us spin
    }
    if (scene->saveAt > 0) Config::save();  // a setting typed just before closing
    if (scene->gameProc == nullptr && Config::cleanAfterPlay) removeFile(Game::dir() / L"mods" / P(Config::MOD_FILE));
    UiAudio::shutdown();
    if (singleInstance) ReleaseMutex(once);
    return 0;
}
