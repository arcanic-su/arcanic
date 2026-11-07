// SPDX-FileCopyrightText: 2026 tuffboy999 and Va1der (Arcanic)
// SPDX-License-Identifier: GPL-3.0-or-later
// Arcanic - authors: tuffboy999 and Va1der. Free software under GPL-3.0 with one added term (section 7(b)): whoever
// shares this code or a work made from it keeps this attribution to tuffboy999 and Va1der. See NOTICE.
// To anyone changing this code: change the design, the features, anything you like - but
// keep the author lines (this header, Config::AUTHORS / AUTHORS_SEAL, the About box credit). The license requires it.
// Arcanic launcher, native Windows build.
// base.h - the small things everything else stands on: text conversion, files, JSON, hashes, zip.
// Only Windows' own libraries are used, so the launcher is one .exe with nothing to install beside it.
#pragma once
#define NOMINMAX
#define WIN32_LEAN_AND_MEAN
#include <winsock2.h>
#include <ws2tcpip.h>
#include <windows.h>
#include <windowsx.h>
#include <shellapi.h>
#include <shlobj.h>
#include <shobjidl.h>
#include <winhttp.h>
#include <bcrypt.h>
#include <d2d1.h>
#include <d2d1helper.h>
#include <dwrite.h>
#include <dwrite_1.h>
#include <wincodec.h>

#include <algorithm>
#include <atomic>
#include <climits>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <filesystem>
#include <functional>
#include <map>
#include <memory>
#include <mutex>
#include <set>
#include <string>
#include <thread>
#include <unordered_map>
#include <vector>

namespace fs = std::filesystem;
using std::string;
using std::vector;
using std::wstring;

// ------------------------------------------------------------------------------------------------ text
inline wstring W(const string& s) {
    if (s.empty()) return L"";
    int n = MultiByteToWideChar(CP_UTF8, 0, s.data(), (int)s.size(), nullptr, 0);
    wstring w(n, 0);
    MultiByteToWideChar(CP_UTF8, 0, s.data(), (int)s.size(), w.data(), n);
    return w;
}
inline string U(const wstring& w) {
    if (w.empty()) return "";
    int n = WideCharToMultiByte(CP_UTF8, 0, w.data(), (int)w.size(), nullptr, 0, nullptr, nullptr);
    string s(n, 0);
    WideCharToMultiByte(CP_UTF8, 0, w.data(), (int)w.size(), s.data(), n, nullptr, nullptr);
    return s;
}
inline fs::path P(const string& utf8) {
    return fs::path(W(utf8));
}
inline string S(const fs::path& p) {
    return U(p.wstring());
}

inline string lower(string s) {
    for (auto& c : s) c = (char)tolower((unsigned char)c);
    return s;
}
// Upper-case for Latin and Cyrillic alike (button captions are drawn in capitals)
inline string upper(const string& s) {
    wstring w = W(s);
    if (!w.empty()) CharUpperBuffW(w.data(), (DWORD)w.size());
    return U(w);
}
inline string lowerAny(const string& s) {
    wstring w = W(s);
    if (!w.empty()) CharLowerBuffW(w.data(), (DWORD)w.size());
    return U(w);
}
inline bool starts(const string& s, const string& p) {
    return s.size() >= p.size() && !s.compare(0, p.size(), p);
}
inline bool ends(const string& s, const string& p) {
    return s.size() >= p.size() && !s.compare(s.size() - p.size(), p.size(), p);
}
inline string trim(const string& s) {
    size_t a = 0, b = s.size();
    while (a < b && isspace((unsigned char)s[a])) a++;
    while (b > a && isspace((unsigned char)s[b - 1])) b--;
    return s.substr(a, b - a);
}
// A plain file name that cannot step out of the folder it is joined to (names from the network go through this)
inline bool safeFileName(const string& n) {
    return !n.empty() && n.size() < 200 && n.find_first_of("/\\:*?\"<>|") == string::npos && n.find("..") == string::npos &&
           n.front() != ' ' && n.back() != ' ' && n.back() != '.';
}
inline string replaceAll(string s, const string& a, const string& b) {
    if (a.empty()) return s;
    for (size_t i = 0; (i = s.find(a, i)) != string::npos; i += b.size()) s.replace(i, a.size(), b);
    return s;
}
inline vector<string> split(const string& s, const string& seps) {
    vector<string> out;
    string cur;
    for (char c : s) {
        if (seps.find(c) != string::npos) {
            if (!cur.empty()) out.push_back(cur);
            cur.clear();
        } else
            cur += c;
    }
    if (!cur.empty()) out.push_back(cur);
    return out;
}
inline string fmt(const char* f, ...) {
    char buf[512];
    va_list a;
    va_start(a, f);
    vsnprintf(buf, sizeof buf, f, a);
    va_end(a);
    return buf;
}
inline string envVar(const wchar_t* name) {
    wchar_t buf[32768];
    DWORD n = GetEnvironmentVariableW(name, buf, 32768);
    return n ? U(wstring(buf, n)) : "";
}
inline bool hasEnv(const wchar_t* name) {
    return GetEnvironmentVariableW(name, nullptr, 0) > 0;
}

// ------------------------------------------------------------------------------------------------ files
inline bool readFile(const fs::path& p, string& out) {
    HANDLE h =
        CreateFileW(p.c_str(), GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE, nullptr, OPEN_EXISTING, 0, nullptr);
    if (h == INVALID_HANDLE_VALUE) return false;
    LARGE_INTEGER sz;
    GetFileSizeEx(h, &sz);
    out.resize((size_t)sz.QuadPart);
    size_t got = 0;
    bool ok = true;
    while (got < out.size()) {
        DWORD n = 0;
        if (!ReadFile(h, out.data() + got, (DWORD)std::min<size_t>(out.size() - got, 1 << 24), &n, nullptr) || !n) {
            ok = false;
            break;
        }
        got += n;
    }
    CloseHandle(h);
    return ok;
}
inline bool writeFile(const fs::path& p, const void* data, size_t len) {
    std::error_code ec;
    fs::create_directories(p.parent_path(), ec);
    HANDLE h = CreateFileW(p.c_str(), GENERIC_WRITE, 0, nullptr, CREATE_ALWAYS, 0, nullptr);
    if (h == INVALID_HANDLE_VALUE) return false;
    size_t put = 0;
    bool ok = true;
    while (put < len) {
        DWORD n = 0;
        if (!WriteFile(h, (const char*)data + put, (DWORD)std::min<size_t>(len - put, 1 << 24), &n, nullptr)) {
            ok = false;
            break;
        }
        put += n;
    }
    CloseHandle(h);
    return ok;
}
inline bool writeFile(const fs::path& p, const string& s) {
    return writeFile(p, s.data(), s.size());
}
inline bool isFile(const fs::path& p) {
    std::error_code ec;
    return fs::is_regular_file(p, ec);
}
inline bool isDir(const fs::path& p) {
    std::error_code ec;
    return fs::is_directory(p, ec);
}
inline int64_t fileSize(const fs::path& p) {
    std::error_code ec;
    auto n = fs::file_size(p, ec);
    return ec ? -1 : (int64_t)n;
}
inline void makeDirs(const fs::path& p) {
    std::error_code ec;
    fs::create_directories(p, ec);
}
inline void removeFile(const fs::path& p) {
    std::error_code ec;
    fs::remove(p, ec);
}
inline fs::path homeDir() {
    string h = envVar(L"USERPROFILE");
    return P(h.empty() ? "C:\\" : h);
}
inline void openInExplorer(const fs::path& dir) {
    makeDirs(dir);
    ShellExecuteW(nullptr, L"open", dir.c_str(), nullptr, nullptr, SW_SHOWNORMAL);
}

// ------------------------------------------------------------------------------------------------ JSON
struct Json {
    enum Type { Null, Bool, Num, Str, Arr, Obj } type = Null;
    bool b = false;
    double n = 0;
    string s;
    vector<Json> a;
    vector<std::pair<string, Json>> o;

    static const Json& none() {
        static Json j;
        return j;
    }
    bool isNull() const { return type == Null; }
    const Json& operator[](const char* key) const {
        if (type == Obj)
            for (auto& kv : o)
                if (kv.first == key) return kv.second;
        return none();
    }
    const Json& operator[](size_t i) const { return type == Arr && i < a.size() ? a[i] : none(); }
    bool has(const char* key) const { return !(*this)[key].isNull(); }
    string str(const char* key, const string& def = "") const {
        const Json& j = (*this)[key];
        return j.type == Str ? j.s : def;
    }
    int64_t num(const char* key, int64_t def = 0) const {
        const Json& j = (*this)[key];
        return j.type == Num ? (int64_t)j.n : def;
    }
    bool flag(const char* key) const {
        const Json& j = (*this)[key];
        return j.type == Bool && j.b;
    }
    bool contains(const string& v) const {
        for (auto& x : a)
            if (x.type == Str && x.s == v) return true;
        return false;
    }
    Json& set(const string& key, Json v) {
        type = Obj;
        for (auto& kv : o)
            if (kv.first == key) {
                kv.second = std::move(v);
                return *this;
            }
        o.emplace_back(key, std::move(v));
        return *this;
    }
    static Json of(const string& v) {
        Json j;
        j.type = Str;
        j.s = v;
        return j;
    }
    static Json of(const char* v) { return of(string(v)); }
    static Json of(double v) {
        Json j;
        j.type = Num;
        j.n = v;
        return j;
    }
    static Json of(bool v) {
        Json j;
        j.type = Bool;
        j.b = v;
        return j;
    }
    static Json object() {
        Json j;
        j.type = Obj;
        return j;
    }
    static Json array() {
        Json j;
        j.type = Arr;
        return j;
    }

    static Json parse(const string& text) {
        size_t i = 0;
        Json j;
        try {
            j = value(text, i);
        } catch (...) {
            j = Json();
        }
        return j;
    }
    string dump() const {
        string out;
        write(out);
        return out;
    }

private:
    static void ws(const string& t, size_t& i) {
        while (i < t.size() && (t[i] == ' ' || t[i] == '\n' || t[i] == '\r' || t[i] == '\t')) i++;
    }
    static void utf8(string& out, unsigned cp) {
        if (cp < 0x80)
            out += (char)cp;
        else if (cp < 0x800) {
            out += (char)(0xC0 | cp >> 6);
            out += (char)(0x80 | (cp & 63));
        } else if (cp < 0x10000) {
            out += (char)(0xE0 | cp >> 12);
            out += (char)(0x80 | ((cp >> 6) & 63));
            out += (char)(0x80 | (cp & 63));
        } else {
            out += (char)(0xF0 | cp >> 18);
            out += (char)(0x80 | ((cp >> 12) & 63));
            out += (char)(0x80 | ((cp >> 6) & 63));
            out += (char)(0x80 | (cp & 63));
        }
    }
    static string text(const string& t, size_t& i) {
        string out;
        i++;
        while (i < t.size() && t[i] != '"') {
            if (t[i] != '\\') {
                out += t[i++];
                continue;
            }
            if (++i >= t.size()) break;
            char c = t[i++];
            switch (c) {
            case 'n':
                out += '\n';
                break;
            case 't':
                out += '\t';
                break;
            case 'r':
                out += '\r';
                break;
            case 'b':
                out += '\b';
                break;
            case 'f':
                out += '\f';
                break;
            case 'u': {
                unsigned cp = (unsigned)strtoul(t.substr(i, 4).c_str(), nullptr, 16);
                i += 4;
                if (cp >= 0xD800 && cp < 0xDC00 && i + 6 <= t.size() && t[i] == '\\' && t[i + 1] == 'u') {
                    unsigned lo = (unsigned)strtoul(t.substr(i + 2, 4).c_str(), nullptr, 16);
                    i += 6;
                    cp = 0x10000 + ((cp - 0xD800) << 10) + (lo - 0xDC00);
                }
                utf8(out, cp);
                break;
            }
            default:
                out += c;
            }
        }
        i++;
        return out;
    }
    static Json value(const string& t, size_t& i) {
        ws(t, i);
        if (i >= t.size()) throw 0;
        Json j;
        char c = t[i];
        if (c == '{') {
            j.type = Obj;
            i++;
            ws(t, i);
            if (i < t.size() && t[i] == '}') {
                i++;
                return j;
            }
            while (i < t.size()) {
                ws(t, i);
                if (t[i] != '"') throw 0;
                string k = text(t, i);
                ws(t, i);
                if (i >= t.size() || t[i] != ':') throw 0;
                i++;
                j.o.emplace_back(std::move(k), value(t, i));
                ws(t, i);
                if (i < t.size() && t[i] == ',') {
                    i++;
                    continue;
                }
                if (i < t.size() && t[i] == '}') {
                    i++;
                    return j;
                }
                throw 0;
            }
            throw 0;
        }
        if (c == '[') {
            j.type = Arr;
            i++;
            ws(t, i);
            if (i < t.size() && t[i] == ']') {
                i++;
                return j;
            }
            while (i < t.size()) {
                j.a.push_back(value(t, i));
                ws(t, i);
                if (i < t.size() && t[i] == ',') {
                    i++;
                    continue;
                }
                if (i < t.size() && t[i] == ']') {
                    i++;
                    return j;
                }
                throw 0;
            }
            throw 0;
        }
        if (c == '"') {
            j.type = Str;
            j.s = text(t, i);
            return j;
        }
        if (!t.compare(i, 4, "true")) {
            i += 4;
            j.type = Bool;
            j.b = true;
            return j;
        }
        if (!t.compare(i, 5, "false")) {
            i += 5;
            j.type = Bool;
            return j;
        }
        if (!t.compare(i, 4, "null")) {
            i += 4;
            return j;
        }
        size_t st = i;
        while (i < t.size() && (isdigit((unsigned char)t[i]) || strchr("+-.eE", t[i]))) i++;
        if (st == i) throw 0;
        j.type = Num;
        j.n = strtod(t.substr(st, i - st).c_str(), nullptr);
        return j;
    }
    static void quote(string& out, const string& s) {
        out += '"';
        for (unsigned char c : s) {
            if (c == '"')
                out += "\\\"";
            else if (c == '\\')
                out += "\\\\";
            else if (c == '\n')
                out += "\\n";
            else if (c == '\r')
                out += "\\r";
            else if (c == '\t')
                out += "\\t";
            else if (c < 0x20)
                out += fmt("\\u%04x", c);
            else
                out += (char)c;
        }
        out += '"';
    }
    void write(string& out) const {
        switch (type) {
        case Null:
            out += "null";
            break;
        case Bool:
            out += b ? "true" : "false";
            break;
        case Num:
            out += n == floor(n) && fabs(n) < 1e15 ? fmt("%lld", (long long)n) : fmt("%.17g", n);
            break;
        case Str:
            quote(out, s);
            break;
        case Arr:
            out += '[';
            for (size_t i = 0; i < a.size(); i++) {
                if (i) out += ',';
                a[i].write(out);
            }
            out += ']';
            break;
        case Obj:
            out += '{';
            for (size_t i = 0; i < o.size(); i++) {
                if (i) out += ',';
                quote(out, o[i].first);
                out += ':';
                o[i].second.write(out);
            }
            out += '}';
            break;
        }
    }
};

// ------------------------------------------------------------------------------------------------ hashes (Windows CNG)
inline string hex(const unsigned char* d, size_t n) {
    static const char* h = "0123456789abcdef";
    string s;
    for (size_t i = 0; i < n; i++) {
        s += h[d[i] >> 4];
        s += h[d[i] & 15];
    }
    return s;
}

// A running hash: SHA1, SHA256 or MD5
struct Hasher {
    BCRYPT_ALG_HANDLE alg = nullptr;
    BCRYPT_HASH_HANDLE hash = nullptr;
    DWORD len = 0;
    explicit Hasher(const wchar_t* name) {
        if (BCryptOpenAlgorithmProvider(&alg, name, nullptr, 0) < 0) {
            alg = nullptr;
            return;
        }
        DWORD got = 0;
        BCryptGetProperty(alg, BCRYPT_HASH_LENGTH, (PUCHAR)&len, sizeof len, &got, 0);
        if (BCryptCreateHash(alg, &hash, nullptr, 0, nullptr, 0, 0) < 0) hash = nullptr;
    }
    ~Hasher() {
        if (hash) BCryptDestroyHash(hash);
        if (alg) BCryptCloseAlgorithmProvider(alg, 0);
    }
    void add(const void* d, size_t n) {
        if (hash) BCryptHashData(hash, (PUCHAR)d, (ULONG)n, 0);
    }
    vector<unsigned char> done() {
        vector<unsigned char> out(len);
        if (hash) BCryptFinishHash(hash, out.data(), len, 0);
        return out;
    }
    string doneHex() {
        auto v = done();
        return hex(v.data(), v.size());
    }
};
inline string sha256Hex(const string& s) {
    Hasher h(BCRYPT_SHA256_ALGORITHM);
    h.add(s.data(), s.size());
    return h.doneHex();
}

inline bool base64(const string& in, string& out) {
    static int T[256];
    static bool init = false;
    if (!init) {
        for (int& x : T) x = -1;
        const char* a = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        for (int i = 0; i < 64; i++) T[(unsigned char)a[i]] = i;
        T['-'] = 62;
        T['_'] = 63;
        init = true;
    }
    out.clear();
    int val = 0, bits = -8;
    for (unsigned char c : in) {
        if (c == '=' || isspace(c)) continue;
        if (T[c] < 0) return false;
        val = (val << 6) | T[c];
        bits += 6;
        if (bits >= 0) {
            out += (char)((val >> bits) & 0xFF);
            bits -= 8;
        }
    }
    return true;
}

// ------------------------------------------------------------------------------------------------ inflate and zip
// Raw DEFLATE (RFC 1951)
struct Inflater {
    const unsigned char* in;
    size_t inLen, pos = 0;
    uint32_t bitBuf = 0;
    int bitCnt = 0;
    string& out;
    Inflater(const unsigned char* d, size_t n, string& o) : in(d), inLen(n), out(o) {}

    int bits(int need) {
        uint32_t v = bitBuf;
        while (bitCnt < need) {
            if (pos >= inLen) throw 1;
            v |= (uint32_t)in[pos++] << bitCnt;
            bitCnt += 8;
        }
        bitBuf = v >> need;
        bitCnt -= need;
        return (int)(v & ((1u << need) - 1));
    }
    struct Huff {
        short count[16], symbol[288];
    };
    static void build(Huff& h, const short* length, int n) {
        short offs[16];
        for (int i = 0; i < 16; i++) h.count[i] = 0;
        for (int i = 0; i < n; i++) h.count[length[i]]++;
        h.count[0] = 0;
        offs[1] = 0;
        for (int i = 1; i < 15; i++) offs[i + 1] = offs[i] + h.count[i];
        for (int i = 0; i < n; i++)
            if (length[i]) h.symbol[offs[length[i]]++] = (short)i;
    }
    int decode(const Huff& h) {
        int code = 0, first = 0, index = 0;
        for (int len = 1; len <= 15; len++) {
            code |= bits(1);
            int count = h.count[len];
            if (code - count < first) return h.symbol[index + (code - first)];
            index += count;
            first += count;
            first <<= 1;
            code <<= 1;
        }
        throw 2;
    }
    void codes(const Huff& lit, const Huff& dist) {
        static const short lens[29] = {3,  4,  5,  6,  7,  8,  9,  10, 11,  13,  15,  17,  19,  23, 27,
                                       31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258};
        static const short lext[29] = {0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2,
                                       2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0};
        static const short dists[30] = {1,    2,    3,    4,    5,    7,    9,    13,    17,    25,
                                        33,   49,   65,   97,   129,  193,  257,  385,   513,   769,
                                        1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577};
        static const short dext[30] = {0, 0, 0, 0, 1, 1, 2, 2,  3,  3,  4,  4,  5,  5,  6,
                                       6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13};
        for (;;) {
            int sym = decode(lit);
            if (sym < 256) {
                out += (char)sym;
                continue;
            }
            if (sym == 256) return;
            sym -= 257;
            if (sym >= 29) throw 3;
            int len = lens[sym] + bits(lext[sym]);
            sym = decode(dist);
            if (sym >= 30) throw 4;
            size_t d = (size_t)dists[sym] + bits(dext[sym]);
            if (d > out.size()) throw 5;
            size_t from = out.size() - d;
            for (int i = 0; i < len; i++) out += out[from + i];
        }
    }
    void run() {
        int last;
        do {
            last = bits(1);
            int type = bits(2);
            if (type == 0) {
                bitBuf = 0;
                bitCnt = 0;
                if (pos + 4 > inLen) throw 6;
                unsigned len = in[pos] | (in[pos + 1] << 8);
                pos += 4;
                if (pos + len > inLen) throw 7;
                out.append((const char*)in + pos, len);
                pos += len;
            } else if (type == 1) {
                static Huff lit, dist;
                static bool made = false;
                if (!made) {
                    short l[288];
                    int i = 0;
                    for (; i < 144; i++) l[i] = 8;
                    for (; i < 256; i++) l[i] = 9;
                    for (; i < 280; i++) l[i] = 7;
                    for (; i < 288; i++) l[i] = 8;
                    build(lit, l, 288);
                    for (i = 0; i < 30; i++) l[i] = 5;
                    build(dist, l, 30);
                    made = true;
                }
                codes(lit, dist);
            } else if (type == 2) {
                static const short order[19] = {16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15};
                short l[320] = {0};
                int nlen = bits(5) + 257, ndist = bits(5) + 1, ncode = bits(4) + 4;
                for (int i = 0; i < ncode; i++) l[order[i]] = (short)bits(3);
                Huff lenc;
                build(lenc, l, 19);
                short ll[320] = {0};
                for (int i = 0; i < nlen + ndist;) {
                    int sym = decode(lenc);
                    if (sym < 16) {
                        ll[i++] = (short)sym;
                        continue;
                    }
                    int prev = 0, rep;
                    if (sym == 16) {
                        if (!i) throw 8;
                        prev = ll[i - 1];
                        rep = 3 + bits(2);
                    } else if (sym == 17)
                        rep = 3 + bits(3);
                    else
                        rep = 11 + bits(7);
                    if (i + rep > nlen + ndist) throw 9;
                    while (rep--) ll[i++] = (short)prev;
                }
                Huff lit, dist;
                build(lit, ll, nlen);
                build(dist, ll + nlen, ndist);
                codes(lit, dist);
            } else
                throw 10;
        } while (!last);
    }
};
inline bool inflate(const unsigned char* d, size_t n, string& out, size_t expect = 0) {
    out.clear();
    if (expect) out.reserve(expect);
    try {
        Inflater(d, n, out).run();
        return true;
    } catch (...) {
        return false;
    }
}

// Reads entries out of a .zip / .jar that is held in memory
struct Zip {
    struct Entry {
        string name;
        uint32_t method, csize, usize, offset;
    };
    string data;
    vector<Entry> entries;

    static uint32_t u32(const unsigned char* p) { return p[0] | (p[1] << 8) | (p[2] << 16) | ((uint32_t)p[3] << 24); }
    static uint32_t u16(const unsigned char* p) { return p[0] | (p[1] << 8); }
    bool open(const fs::path& file) {
        entries.clear();
        if (!readFile(file, data)) return false;
        return parse();
    }
    // An archive already in memory (a jar inside a jar)
    bool load(string bytes) {
        entries.clear();
        data = std::move(bytes);
        return parse();
    }
    bool parse() {
        if (data.size() < 22) return false;
        const unsigned char* d = (const unsigned char*)data.data();
        size_t i = data.size() - 22, stop = data.size() > 70000 ? data.size() - 70000 : 0;
        for (;; i--) {
            if (u32(d + i) == 0x06054b50) break;
            if (i == stop) return false;
        }
        uint32_t count = u16(d + i + 10), off = u32(d + i + 16);
        for (uint32_t k = 0; k < count && off + 46 <= data.size(); k++) {
            if (u32(d + off) != 0x02014b50) break;
            Entry e;
            e.method = u16(d + off + 10);
            e.csize = u32(d + off + 20);
            e.usize = u32(d + off + 24);
            uint32_t nl = u16(d + off + 28), xl = u16(d + off + 30), cl = u16(d + off + 32);
            if ((uint64_t)off + 46 + nl + xl + cl > data.size()) { entries.clear(); return false; }
            e.offset = u32(d + off + 42);
            e.name.assign(data.data() + off + 46, nl);
            entries.push_back(e);
            off += 46 + nl + xl + cl;
        }
        return true;
    }
    const Entry* find(const string& name) const {
        for (auto& e : entries)
            if (e.name == name) return &e;
        return nullptr;
    }
    bool read(const Entry& e, string& out) const {
        const unsigned char* d = (const unsigned char*)data.data();
        if ((size_t)e.offset + 30 > data.size() || u32(d + e.offset) != 0x04034b50) return false;
        size_t start = (size_t)e.offset + 30 + u16(d + e.offset + 26) + u16(d + e.offset + 28);
        if (start + e.csize > data.size()) return false;
        if (e.method == 0) {
            out.assign(data.data() + start, e.csize);
            return true;
        }
        if (e.method == 8) return inflate(d + start, e.csize, out, e.usize);
        return false;
    }
    bool read(const string& name, string& out) const {
        const Entry* e = find(name);
        return e && read(*e, out);
    }
};
