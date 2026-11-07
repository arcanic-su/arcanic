// SPDX-FileCopyrightText: 2026 tuffboy999 and Va1der (Arcanic)
// SPDX-License-Identifier: GPL-3.0-or-later
// Arcanic - authors: tuffboy999 and Va1der. Free software under GPL-3.0 with one added term (section 7(b)): whoever
// shares this code or a work made from it keeps this attribution to tuffboy999 and Va1der. See NOTICE.
// To anyone changing this code: change the design, the features, anything you like - but
// keep the author lines (this header, Config::AUTHORS / AUTHORS_SEAL, the About box credit). The license requires it.
#pragma once
#include <regex>
// The mods folder's doctor. Before the game starts it reads every mod the way Fabric will and fixes what would stop
// the game: a library nobody installed, a mod built for another Minecraft, a Forge mod, two copies of one mod, a broken
// file, two mods that refuse each other. After a failed start it reads the game's log and does the same for what only
// the game could find. Mods it switches off are only renamed to .disabled: the Library turns them back on.
namespace Doctor {
    inline string say(const char* ru, const char* en) { return Lang::en ? en : ru; }

    // ---- versions and version ranges, as fabric.mod.json writes them (">=0.16", "~1.21.4", "1.21.x", ["a", "b"])
    struct Ver {
        vector<long long> nums;  // -1: a wildcard part ("x")
        string pre;
        bool ok = false;
    };
    inline Ver parseVer(string s) {
        Ver v;
        s = trim(s);
        size_t plus = s.find('+');
        if (plus != string::npos) s = s.substr(0, plus);
        size_t dash = s.find('-');
        if (dash != string::npos) {
            v.pre = s.substr(dash + 1);
            s = s.substr(0, dash);
        }
        if (s.empty()) return v;
        for (auto& p : split(s, ".")) {
            if (p == "x" || p == "X" || p == "*") {
                v.nums.push_back(-1);
                continue;
            }
            if (p.find_first_not_of("0123456789") != string::npos || p.size() > 15) return Ver{};
            v.nums.push_back(atoll(p.c_str()));
        }
        v.ok = !v.nums.empty();
        return v;
    }
    inline int comparePre(const string& a, const string& b) {
        if (a == b) return 0;
        if (a.empty()) return 1;  // a release is newer than its pre-releases
        if (b.empty()) return -1;
        auto pa = split(a, "."), pb = split(b, ".");
        for (size_t i = 0; i < std::max(pa.size(), pb.size()); i++) {
            if (i >= pa.size()) return -1;
            if (i >= pb.size()) return 1;
            bool na = pa[i].find_first_not_of("0123456789") == string::npos, nb = pb[i].find_first_not_of("0123456789") == string::npos;
            if (na && nb) {
                long long x = atoll(pa[i].c_str()), y = atoll(pb[i].c_str());
                if (x != y) return x < y ? -1 : 1;
            } else if (pa[i] != pb[i])
                return na ? -1 : nb ? 1 : (pa[i] < pb[i] ? -1 : 1);
        }
        return 0;
    }
    inline int compare(const Ver& a, const Ver& b) {
        for (size_t i = 0; i < std::max(a.nums.size(), b.nums.size()); i++) {
            long long x = i < a.nums.size() ? a.nums[i] : 0, y = i < b.nums.size() ? b.nums[i] : 0;
            if (x != y) return x < y ? -1 : 1;
        }
        return comparePre(a.pre, b.pre);
    }
    // One term of a range; anything this cannot read counts as met, so an odd range never switches a mod off
    inline bool term(const Ver& v, string t) {
        t = trim(t);
        if (t.empty() || t == "*") return true;
        string op;
        for (const char* o : {">=", "<=", ">", "<", "=", "~", "^"})
            if (starts(t, o)) {
                op = o;
                break;
            }
        string rest = trim(t.substr(op.size()));
        while (!rest.empty() && rest.back() == '-') rest.pop_back();  // "1.21-": pre-releases included
        Ver r = parseVer(rest);
        if (!r.ok) return true;
        bool wild = std::find(r.nums.begin(), r.nums.end(), -1) != r.nums.end();
        if (wild) {  // "1.21.x": the parts before the x are the same
            for (size_t i = 0; i < r.nums.size() && r.nums[i] >= 0; i++)
                if ((i < v.nums.size() ? v.nums[i] : 0) != r.nums[i]) return false;
            return true;
        }
        int c = compare(v, r);
        if (op == ">=") return c >= 0;
        if (op == "<=") return c <= 0;
        if (op == ">") return c > 0;
        if (op == "<") return c < 0;
        if (op == "~" || op == "^") {
            if (c < 0) return false;
            Ver top = r;
            top.pre.clear();
            if (op == "~" && top.nums.size() >= 2) {
                top.nums.resize(2);
                top.nums[1]++;
            } else {
                top.nums.resize(1);
                top.nums[0]++;
            }
            return compare(v, top) < 0;
        }
        return c == 0;
    }
    inline bool fits(const string& version, const Json& range) {
        Ver v = parseVer(version);
        if (!v.ok) return true;
        if (range.type == Json::Arr) {
            if (range.a.empty()) return true;
            for (auto& r : range.a)
                if (r.type == Json::Str && fits(version, r)) return true;
            return false;
        }
        if (range.type != Json::Str) return true;
        for (auto& t : split(range.s, " "))
            if (!term(v, t)) return false;
        return true;
    }

    // ---- one jar, read the way Fabric reads it
    struct Mod {
        fs::path file;
        string id, name, version;
        bool fabric = false, broken = false, foreign = false, ours = false;
        Json depends, breaks;
        std::map<string, string> provides;            // this mod and every mod inside it -> version
        vector<std::pair<string, Json>> needs;        // what it and the mods inside it depend on
        vector<Json> loaderNeeds;                     // their "fabricloader" ranges
    };
    inline const std::set<string>& builtIn() {
        static const std::set<string> ids = {"minecraft", "java", "fabricloader", "fabric-loader", "mixinextras"};
        return ids;
    }
    // Mods known to break the game together with ours or with Sodium, whatever their own files say: id -> why (ru, en)
    inline const std::map<string, std::pair<string, string>>& incompatible() {
        static const std::map<string, std::pair<string, string>> ids = {
            {"optifabric", {"(OptiFine) не работает с Sodium и Arcanic — вместо него уже стоит Sodium",
                            "(OptiFine) does not work with Sodium and Arcanic - Sodium is already in its place"}},
            {"optifine", {"(OptiFine) не работает с Sodium и Arcanic — вместо него уже стоит Sodium",
                          "(OptiFine) does not work with Sodium and Arcanic - Sodium is already in its place"}},
        };
        return ids;
    }
    inline void readJar(string bytes, Mod& m, int depth) {
        Zip z;
        if (!z.load(std::move(bytes))) {
            if (!depth) m.broken = true;
            return;
        }
        string meta;
        if (!z.read("fabric.mod.json", meta)) {
            if (!depth && (z.find("META-INF/mods.toml") || z.find("META-INF/neoforge.mods.toml") || z.find("mcmod.info") ||
                           z.find("quilt.mod.json")))
                m.foreign = true;
            return;
        }
        Json j;
        try {
            j = Json::parse(meta);
        } catch (...) {
            if (!depth) m.fabric = true;  // Fabric will read it; we just cannot say more about it
            return;
        }
        string id = j.str("id"), version = j.str("version");
        if (!depth) {
            m.fabric = true;
            m.id = id;
            m.version = version;
            m.name = j.str("name", id);
            m.depends = j["depends"];
            m.breaks = j["breaks"];
        }
        if (!id.empty()) m.provides[id] = version;
        for (auto& p : j["provides"].a)
            if (p.type == Json::Str) m.provides[p.s] = version;
        for (auto& kv : j["depends"].o) {
            if (kv.first == "fabricloader" || kv.first == "fabric-loader") m.loaderNeeds.push_back(kv.second);
            else m.needs.push_back(kv);
        }
        if (depth < 3)
            for (auto& inner : j["jars"].a) {
                string bytesIn;
                if (z.read(inner.str("file"), bytesIn)) readJar(std::move(bytesIn), m, depth + 1);
            }
    }
    inline std::set<string> ourFiles(const fs::path& mods) {
        std::set<string> out = {lower(Config::MOD_FILE)};
        string list;
        if (readFile(mods / L".arcanic-managed", list))
            for (auto& l : split(list, "\r\n")) out.insert(lower(trim(l)));
        return out;
    }
    inline vector<Mod> read(const fs::path& mods) {
        vector<Mod> out;
        auto ours = ourFiles(mods);
        std::error_code ec;
        for (auto& e : fs::directory_iterator(mods, ec)) {
            string n = S(e.path().filename());
            if (!ends(lower(n), ".jar") || !e.is_regular_file(ec)) continue;
            Mod m;
            m.file = e.path();
            m.ours = ours.count(lower(n)) > 0 || starts(lower(n), "arcanic-client");
            string bytes;
            if (!readFile(m.file, bytes)) continue;  // held by another program: Fabric will say so if it matters
            readJar(std::move(bytes), m, 0);
            if (m.name.empty()) m.name = Game::pretty(n);
            out.push_back(std::move(m));
        }
        return out;
    }

    // ---- what the doctor did and what it could not do
    struct Report {
        vector<string> fixed, problems;
        vector<Json> loaderNeeds;
        // Fix asks instead: a mod that is in the way is named with what to do, and switched off only on the player's word
        bool ask = false;
        vector<std::pair<string, string>> asked;  // what is wrong, what to do
        vector<fs::path> offer;                   // the mods the player can switch off with one button
        bool changed() const { return !fixed.empty(); }
    };
    // issue: what is wrong, without what was done about it; always: switched off even when asking (a second copy)
    inline bool switchOff(const Mod& m, Report& r, const string& issue, bool always = false, const string& done = "") {
        string why = !done.empty() ? done : issue + say(" — выключили", " - switched off");
        if (r.ask && !always) {
            for (auto& a : r.asked)
                if (a.first == issue) return false;
            string file = S(m.file.filename());
            if (m.ours)
                r.asked.push_back({issue, say("Переустанови лаунчер с arcanic.su", "Reinstall the launcher from arcanic.su")});
            else {
                r.asked.push_back({issue, Lang::en ? "Delete \"" + file + "\" from the mods folder or switch it off in the Library"
                                                   : "Удали «" + file + "» из папки mods или выключи его в Библиотеке"});
                r.offer.push_back(m.file);
            }
            return false;
        }
        if (m.ours) {
            r.problems.push_back(why);
            return false;
        }
        fs::path to = m.file;
        to += L".disabled";
        if (!MoveFileExW(m.file.c_str(), to.c_str(), MOVEFILE_REPLACE_EXISTING)) {
            r.problems.push_back(why);
            return false;
        }
        r.fixed.push_back(why);
        Log::warn("doctor: switched off " + S(m.file.filename()) + " - " + why);
        return true;
    }
    // The Modrinth project behind a mod id, when the two differ
    inline string alias(const string& id) {
        static const std::map<string, string> known = {
            {"fabric", "fabric-api"},           {"architectury", "architectury-api"},
            {"yet_another_config_lib_v3", "yacl"}, {"yet-another-config-lib", "yacl"},
            {"owo", "owo-lib"},                 {"cloth_config", "cloth-config"},
            {"cloth-config2", "cloth-config"},  {"puzzleslib", "puzzles-lib"},
            {"forgeconfigapiport", "forge-config-api-port"}, {"resourcefullib", "resourceful-lib"},
            {"cardinal-components", "cardinal-components-api"}, {"fabric_language_kotlin", "fabric-language-kotlin"},
            {"player_animation_library", "player-animation-library"}, {"midnightlib", "midnightlib"},
            {"libipn", "libipn"},               {"konkrete", "konkrete"}};
        auto it = known.find(id);
        if (it != known.end()) return it->second;
        if (starts(id, "fabric-") && id != "fabric-language-kotlin") return "fabric-api";  // a module of Fabric API
        return id;
    }
    // Finds the mod with this id on Modrinth (then CurseForge) and installs its build for this Minecraft
    inline bool installById(const string& id, const string& mc, const fs::path& mods, string& title) {
        vector<string> slugs = {alias(id)};
        if (slugs[0] != id) slugs.push_back(id);
        if (id.find('_') != string::npos) slugs.push_back(replaceAll(id, "_", "-"));
        for (auto& slug : slugs) {
            auto r = Net::request("GET", string(Modrinth::API) + "/project/" + Net::enc(slug), {}, "", nullptr, 10);
            if (r.status != 200) continue;
            try {
                Json p = Json::parse(r.body);
                if (p.str("project_type") != "mod" || !p["loaders"].contains("fabric") || !p["game_versions"].contains(mc)) continue;
                Modrinth::install(p.str("id"), mc, mods);
                title = p.str("title", slug);
                return true;
            } catch (...) {}
        }
        string want = Modrinth::plain(id);
        auto same = [&](const Modrinth::Hit& h) { return Modrinth::plain(h.slug) == want || Modrinth::plain(h.title) == want; };
        try {
            for (auto& h : Modrinth::search(id, mc, "mod", "", 0, nullptr))
                if (same(h)) {
                    Modrinth::install(h.id, mc, mods);
                    title = h.title;
                    return true;
                }
        } catch (...) {}
        try {
            for (auto& h : CurseForge::search(id, mc, "", 0, nullptr))
                if (same(h) && h.direct) {
                    CurseForge::install(h.id, mc, mods);
                    title = h.title;
                    return true;
                }
        } catch (...) {}
        return false;
    }
    // Newer builds (for this Minecraft) of these files, from Modrinth by their hashes; each found one replaces its file
    inline std::set<fs::path> replaceFromModrinth(const vector<const Mod*>& list, const string& mc, Report& r, bool forVersion) {
        std::set<fs::path> done;
        std::map<string, const Mod*> bySha;
        for (auto* m : list) {
            if (m->ours) continue;
            string sha = Installer::sha1File(m->file);
            if (!sha.empty()) bySha[sha] = m;
        }
        if (bySha.empty()) return done;
        vector<string> shas;
        for (auto& kv : bySha) shas.push_back(kv.first);
        std::map<string, Modrinth::Update> found;
        try {
            found = Modrinth::updates(shas, mc);
        } catch (...) {
            return done;
        }
        for (auto& kv : found) {
            auto it = bySha.find(kv.first);
            if (it == bySha.end()) continue;
            const Mod& m = *it->second;
            fs::path fresh = m.file.parent_path() / P(kv.second.filename);
            try {
                Net::file(kv.second.url, fresh, kv.second.sha1, nullptr);
            } catch (...) {
                continue;
            }
            if (fresh != m.file) removeFile(m.file);
            done.insert(m.file);
            r.fixed.push_back(forVersion ? (Lang::en ? "\"" + m.name + "\" updated for Minecraft " + mc
                                                     : "«" + m.name + "» обновлён под Minecraft " + mc)
                                         : (Lang::en ? "\"" + m.name + "\" updated to a version the other mods need"
                                                     : "«" + m.name + "» обновлён до версии, которая нужна другим модам"));
            Log::info("doctor: " + S(m.file.filename()) + " -> " + kv.second.filename);
        }
        return done;
    }

    // ---- before the game: everything Fabric would refuse to start with
    inline Report scan(const fs::path& mods, const string& mc, bool ask = false) {
        Report r;
        r.ask = ask;
        std::set<string> tried;
        for (int pass = 0; pass < 6; pass++) {
            size_t before = r.fixed.size();
            vector<Mod> all = read(mods);
            vector<Mod*> live;
            // broken files and mods for other loaders
            for (auto& m : all) {
                if (m.broken) {
                    switchOff(m, r, Lang::en ? "\"" + m.name + "\" is damaged (not a whole jar)" : "«" + m.name + "» повреждён (файл не целый)");
                    continue;
                }
                if (m.foreign) {
                    switchOff(m, r, Lang::en ? "\"" + m.name + "\" is a Forge/NeoForge mod, it does not run on Fabric"
                                             : "«" + m.name + "» — мод для Forge/NeoForge, на Fabric он не работает");
                    continue;
                }
                if (m.fabric) {
                    auto bad = incompatible().find(m.id);
                    if (bad != incompatible().end() && !m.ours) {
                        switchOff(m, r, Lang::en ? "\"" + m.name + "\" " + bad->second.second : "«" + m.name + "» " + bad->second.first);
                        continue;
                    }
                    live.push_back(&m);
                }
            }
            // two copies of one mod: ours wins, then the newest
            std::map<string, vector<Mod*>> byId;
            for (auto* m : live)
                if (!m->id.empty()) byId[m->id].push_back(m);
            std::set<Mod*> gone;
            for (auto& kv : byId) {
                if (kv.second.size() < 2) continue;
                auto keep = *std::max_element(kv.second.begin(), kv.second.end(), [](Mod* a, Mod* b) {
                    if (a->ours != b->ours) return b->ours;
                    return compare(parseVer(a->version), parseVer(b->version)) < 0;
                });
                for (auto* m : kv.second) {
                    string said = Lang::en ? "Two copies of \"" + m->name + "\" - the extra one switched off, kept version " + keep->version
                                           : "Две копии «" + m->name + "» — лишнюю выключили, оставили версию " + keep->version;
                    if (m != keep && switchOff(*m, r, said, true, said)) gone.insert(m);
                }
            }
            live.erase(std::remove_if(live.begin(), live.end(), [&](Mod* m) { return gone.count(m) > 0; }), live.end());
            if (r.fixed.size() > before) continue;  // read the folder again before going further
            // built for another Minecraft: a build for this one from Modrinth, or off
            vector<const Mod*> otherVersion;
            for (auto* m : live)
                if (!m->ours && m->depends.has("minecraft") && !fits(mc, m->depends["minecraft"])) otherVersion.push_back(m);
            auto replaced = replaceFromModrinth(otherVersion, mc, r, true);
            for (auto* m : otherVersion)
                if (!replaced.count(m->file))
                    switchOff(*m, r, Lang::en ? "\"" + m->name + "\" is not for Minecraft " + mc + " and has no build for it"
                                              : "«" + m->name + "» не для Minecraft " + mc + ", а версии под неё нет");
            if (r.fixed.size() > before) continue;
            // what is there, and the libraries that are missing or too old
            std::map<string, std::pair<string, Mod*>> have;  // id -> version, the jar it is in
            for (auto* m : live)
                for (auto& p : m->provides)
                    if (!have.count(p.first) || compare(parseVer(have[p.first].first), parseVer(p.second)) < 0) have[p.first] = {p.second, m};
            if (have.count("fabric-api")) have["fabric"] = have["fabric-api"];
            std::map<string, string> missing;  // id -> the mod that needs it
            vector<const Mod*> tooOld;
            for (auto* m : live)
                for (auto& need : m->needs) {
                    const string& id = need.first;
                    if (builtIn().count(id)) continue;
                    auto h = have.find(id);
                    if (h == have.end()) {
                        if (!missing.count(id)) missing[id] = m->name;
                    } else if (!fits(h->second.first, need.second) && !h->second.second->ours && h->second.second != m)
                        tooOld.push_back(h->second.second);
                }
            for (auto& kv : missing) {
                string title;
                // each library is looked for once: if what was installed still does not count, its mods go off
                if (tried.insert(kv.first).second && installById(kv.first, mc, mods, title)) {
                    r.fixed.push_back(Lang::en ? "Installed \"" + title + "\" - \"" + kv.second + "\" needs it"
                                               : "Поставили «" + title + "» — его требует «" + kv.second + "»");
                    Log::info("doctor: installed " + kv.first + " for " + kv.second);
                    continue;
                }
                for (auto* m : live) {
                    bool needsIt = std::any_of(m->needs.begin(), m->needs.end(), [&](const auto& n) { return n.first == kv.first; });
                    if (needsIt)
                        switchOff(*m, r, Lang::en ? "\"" + m->name + "\" needs \"" + kv.first + "\", which is nowhere to be found"
                                                  : "«" + m->name + "» требует «" + kv.first + "», а его нигде нет");
                }
            }
            if (!tooOld.empty()) {
                std::sort(tooOld.begin(), tooOld.end());
                tooOld.erase(std::unique(tooOld.begin(), tooOld.end()), tooOld.end());
                replaceFromModrinth(tooOld, mc, r, false);
            }
            if (r.fixed.size() > before) continue;
            // two mods that refuse to run together: the one that says so goes
            for (auto* m : live)
                for (auto& b : m->breaks.o) {
                    auto h = have.find(b.first);
                    if (h == have.end() || h->second.second == m || !fits(h->second.first, b.second)) continue;
                    Mod* other = h->second.second;
                    Mod* off = m->ours ? other : m;
                    if (off->ours) continue;
                    string issue = Lang::en ? "\"" + m->name + "\" does not work together with \"" + other->name + "\""
                                            : "«" + m->name + "» не работает вместе с «" + other->name + "»";
                    if (switchOff(*off, r, issue, false,
                                  issue + (Lang::en ? " - switched off \"" + off->name + "\"" : " — выключили «" + off->name + "»")))
                        goto nextPass;
                }
            break;  // nothing more to fix
        nextPass:;
        }
        // what Fabric Loader itself must be, for the mods that stay
        for (auto& m : read(mods))
            if (m.fabric && !m.broken)
                for (auto& n : m.loaderNeeds) r.loaderNeeds.push_back(n);
        for (auto& f : r.fixed) Log::info("doctor: " + f);
        return r;
    }
    inline bool loaderFits(const string& loader, const vector<Json>& needs) {
        for (auto& n : needs)
            if (!fits(loader, n)) return false;
        return true;
    }

    // ---- after a failed start: what the game's log says
    struct Diagnosis {
        std::set<string> install, update, off;  // mod ids
        bool jvmArgs = false, memory = false, java = false, gpu = false;
        string suspect;                          // a mod the crash report points at
        bool fixable() const { return !install.empty() || !update.empty() || !off.empty() || jvmArgs || memory || java; }
    };
    inline string tail(const fs::path& f, size_t most = 600000) {
        string t;
        if (!readFile(f, t)) return "";
        return t.size() > most ? t.substr(t.size() - most) : t;
    }
    inline Diagnosis diagnose(const fs::path& game, time_t since) {
        Diagnosis d;
        string text = tail(game / L"logs" / L"launcher.log") + "\n" + tail(game / L"logs" / L"latest.log");
        std::error_code ec;
        fs::path newest;
        for (auto& e : fs::directory_iterator(game / L"crash-reports", ec)) {
            auto t = fs::last_write_time(e.path(), ec);
            auto secs = std::chrono::duration_cast<std::chrono::seconds>(t.time_since_epoch()).count();
            // file_time on Windows counts from 1601: to Unix seconds
            if (secs - 11644473600LL >= since && (newest.empty() || e.path() > newest)) newest = e.path();
        }
        if (!newest.empty()) text += "\n" + tail(newest, 200000);
        auto each = [&](const char* pattern, const std::function<void(const std::smatch&)>& f) {
            try {
                std::regex re(pattern, std::regex::icase);
                for (std::sregex_iterator it(text.begin(), text.end(), re), end; it != end; ++it) f(*it);
            } catch (...) {}
        };
        // Fabric's own list of what is wrong and how to fix it
        each(R"(of (?:mod '[^'\n]*' \()?([a-z0-9_.\-]+)\)?, which is missing)", [&](const std::smatch& m) { d.install.insert(lower(m[1].str())); });
        each(R"(- Install ([a-z0-9_.\-]+),)", [&](const std::smatch& m) { d.install.insert(lower(m[1].str())); });
        each(R"(of (?:mod '[^'\n]*' \()?([a-z0-9_.\-]+)\)?, but only the wrong version is present)", [&](const std::smatch& m) { d.update.insert(lower(m[1].str())); });
        each(R"(- Replace mod '[^'\n]*' \(([a-z0-9_.\-]+)\))", [&](const std::smatch& m) { d.update.insert(lower(m[1].str())); });
        each(R"(- Remove mod '[^'\n]*' \(([a-z0-9_.\-]+)\))", [&](const std::smatch& m) { d.off.insert(lower(m[1].str())); });
        each(R"(Mod '[^'\n]*' \(([a-z0-9_.\-]+)\)[^\n]* is incompatible with)", [&](const std::smatch& m) { d.off.insert(lower(m[1].str())); });
        // a mod's code that broke the game while it was loading
        each(R"(Mixin apply for mod ([a-z0-9_.\-]+) failed)", [&](const std::smatch& m) { d.off.insert(lower(m[1].str())); });
        each(R"(MixinApplyError[^\n]*from mod ([a-z0-9_.\-]+))", [&](const std::smatch& m) { d.off.insert(lower(m[1].str())); });
        each(R"(Could not execute entrypoint stage '[^']*' due to errors, provided by '([a-z0-9_.\-]+)')", [&](const std::smatch& m) { d.off.insert(lower(m[1].str())); });
        each(R"(Suspected Mods?:\s*\n?\s*[^\n(]*\(([a-z0-9_.\-]+)\))", [&](const std::smatch& m) {
            string id = lower(m[1].str());
            if (id != "minecraft" && id != "fabricloader" && id != "java") d.suspect = id;
        });
        // Java and its settings
        auto has = [&](const char* s) { return text.find(s) != string::npos; };
        if (has("Could not create the Java Virtual Machine") || has("Unrecognized VM option") || has("Invalid maximum heap size") ||
            has("Unrecognized option:") || has("Error occurred during initialization of VM"))
            d.jvmArgs = true;
        if (has("Could not reserve enough space") || has("java.lang.OutOfMemoryError") || has("Native memory allocation (malloc) failed"))
            d.memory = true;
        if (has("UnsupportedClassVersionError") || has("has been compiled by a more recent version of the Java Runtime") ||
            has("Error: could not open") || has("jvm.cfg"))
            d.java = true;
        if (has("WGL: The driver does not appear to support OpenGL") || has("Pixel format not accelerated") ||
            has("GLFW error 65542") || has("GLFW error 65543") || has("Failed to create the GLFW window") ||
            (has("EXCEPTION_ACCESS_VIOLATION") && (has("atio6axx") || has("ig9icd64") || has("nvoglv64"))))
            d.gpu = true;
        if (d.off.empty() && d.install.empty() && d.update.empty() && !d.suspect.empty()) d.off.insert(d.suspect);
        d.off.erase("minecraft");
        d.off.erase("fabricloader");
        return d;
    }
    // Does what the diagnosis says; true when something changed and a second start may work
    inline bool apply(const Diagnosis& d, const fs::path& mods, const string& mc, Report& r) {
        size_t before = r.fixed.size();
        vector<Mod> all = read(mods);
        auto holder = [&](const string& id) -> Mod* {
            for (auto& m : all)
                if (m.id == id) return &m;
            for (auto& m : all)
                if (m.provides.count(id)) return &m;
            return nullptr;
        };
        for (auto& id : d.install) {
            if (holder(id)) continue;
            string title;
            if (installById(id, mc, mods, title))
                r.fixed.push_back(Lang::en ? "Installed \"" + title + "\", which the game asked for" : "Поставили «" + title + "» — игра просила его");
            else
                r.problems.push_back(Lang::en ? "The game needs \"" + id + "\", but it was not found" : "Игре нужен «" + id + "», но его не нашли");
        }
        vector<const Mod*> old;
        for (auto& id : d.update)
            if (Mod* m = holder(id)) old.push_back(m);
        auto replaced = replaceFromModrinth(old, mc, r, false);
        for (auto* m : old)
            if (!replaced.count(m->file) && !d.install.count(m->id)) {
                Mod copy = *m;
                switchOff(copy, r, Lang::en ? "\"" + m->name + "\" is the wrong version and has no update"
                                            : "«" + m->name + "» не той версии, а обновления нет");
            }
        for (auto& id : d.off)
            if (Mod* m = holder(id)) {
                string issue = Lang::en ? "\"" + m->name + "\" broke the game's start" : "«" + m->name + "» ломал запуск игры";
                switchOff(*m, r, issue, false,
                          issue + say(" — выключили (включить обратно можно в Библиотеке)", " - switched off (turn it back on in the Library)"));
            }
        if (d.jvmArgs && (!Config::jvmArgs.empty() || Config::ramMb > 0)) {
            Config::jvmArgs.clear();
            Config::ramMb = 0;
            Config::save();
            r.fixed.push_back(say("Java не приняла параметры запуска — вернули стандартные", "Java refused the launch settings - set them back to the defaults"));
        }
        if (d.memory && Config::ramMb != 0 && Config::ramMb > Installer::defaultRam()) {
            Config::ramMb = 0;
            Config::save();
            r.fixed.push_back(say("Не хватило памяти — вернули объём памяти по умолчанию", "Not enough memory - memory set back to the default"));
        }
        if (d.java) {
            std::error_code ec;
            fs::remove_all(Installer::ownRuntime(21), ec);
            if (!Config::javaPath.empty()) {
                Config::javaPath.clear();
                Config::save();
            }
            r.fixed.push_back(say("Java оказалась не той — поставим правильную", "Java was the wrong one - the right one will be installed"));
        }
        return r.fixed.size() > before;
    }
    // What to tell the player when nothing could be fixed
    inline string advice(const Diagnosis& d) {
        if (d.gpu) return say("Видеокарта не смогла запустить игру. Обнови драйвер видеокарты (сайт NVIDIA, AMD или Intel) и попробуй снова.",
                              "The graphics card could not start the game. Update its driver (NVIDIA, AMD or Intel website) and try again.");
        if (!d.suspect.empty())
            return Lang::en ? "The crash report points at the mod \"" + d.suspect + "\". Try switching it off in the Library."
                            : "Отчёт об ошибке указывает на мод «" + d.suspect + "». Попробуй выключить его в Библиотеке.";
        return "";
    }
}  // namespace Doctor
