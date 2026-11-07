// SPDX-FileCopyrightText: 2026 tuffboy999 and Va1der (Arcanic)
// SPDX-License-Identifier: GPL-3.0-or-later
// Arcanic - authors: tuffboy999 and Va1der. Free software under GPL-3.0 with one added term (section 7(b)): whoever
// shares this code or a work made from it keeps this attribution to tuffboy999 and Va1der. See NOTICE.
// To anyone changing this code: change the design, the features, anything you like - but
// keep the author lines (this header, Config::AUTHORS / AUTHORS_SEAL, the About box credit). The license requires it.
#pragma once
// The Fix button. It looks at everything that can keep the client from starting - the computer, the network, the
// last crash, Java, the game's files and libraries, the mods and Minecraft's own settings. What is missing or damaged
// it downloads and puts right by itself; what needs the player's word (a mod that does not fit, a broken settings
// file, a network that blocks us) it names, with what to do about it.
namespace Fix {
    inline string say(const string& ru, const string& en) {
        return Lang::en ? en : ru;
    }
    struct Issue {
        string what, todo;
    };
    struct Result {
        vector<string> fixed;          // put right by the launcher
        vector<Issue> problems, tips;  // left for the player; tips do not stop the game
        vector<string> checked;        // what was looked at
        vector<fs::path> offer;        // mods the dialog's button switches off
        vector<fs::path> resetFiles;   // broken settings files the button moves aside
        bool modsProblem = false;      // the dialog offers to open the mods folder
        int actions() const { return (int)(offer.size() + resetFiles.size()); }
    };
    using Step = std::function<void(float, const string&, const string&)>;

    inline string gb(int64_t mb) {
        return fmt("%.1f", mb / 1024.0) + say(" ГБ", " GB");
    }
    inline void problem(Result& R, const string& what, const string& todo) {
        for (auto& p : R.problems)
            if (p.what == what) return;
        R.problems.push_back({what, todo});
    }
    // What the doctor did and asked, into the result
    inline void merge(Result& R, const Doctor::Report& rep) {
        for (auto& f : rep.fixed)
            if (std::find(R.fixed.begin(), R.fixed.end(), f) == R.fixed.end()) R.fixed.push_back(f);
        for (auto& a : rep.asked) problem(R, a.first, a.second);
        for (auto& p : rep.problems) problem(R, p, say("Переустанови лаунчер с arcanic.su", "Reinstall the launcher from arcanic.su"));
        for (auto& f : rep.offer)
            if (std::find(R.offer.begin(), R.offer.end(), f) == R.offer.end()) R.offer.push_back(f);
        if (!rep.asked.empty()) R.modsProblem = true;
    }

    // ---- the computer
    struct Gpu {
        vector<string> names;
        bool basicOnly = false, intelOnly = false;
    };
    inline Gpu gpu() {
        Gpu g;
        DISPLAY_DEVICEW d = {sizeof d};
        for (DWORD i = 0; EnumDisplayDevicesW(nullptr, i, &d, 0); i++) {
            string n = trim(U(wstring(d.DeviceString)));
            if (!n.empty() && std::find(g.names.begin(), g.names.end(), n) == g.names.end()) g.names.push_back(n);
            d = DISPLAY_DEVICEW{sizeof d};
        }
        int real = 0, intel = 0, basic = 0;
        for (auto& n : g.names) {
            string l = lower(n);
            if (l.find("basic display") != string::npos || l.find("basic render") != string::npos ||
                n.find("Базовый видеоадаптер") != string::npos)
                basic++;
            else if (l.find("remote") == string::npos && l.find("virtual") == string::npos && l.find("parsec") == string::npos &&
                     l.find("mirror") == string::npos) {
                real++;
                if (l.find("intel") != string::npos) intel++;
            }
        }
        g.basicOnly = basic > 0 && real == 0;
        g.intelOnly = real > 0 && intel == real;
        return g;
    }
    inline void computer(Result& R, const fs::path& game, const Gpu& g) {
        if (Installer::archName() == "x86")
            problem(R, say("Windows 32-битная, а Minecraft " + Config::mcVersion + " работает только на 64-битной",
                           "Windows is 32-bit, and Minecraft " + Config::mcVersion + " runs only on 64-bit"),
                    say("Поставь 64-битную Windows — на этой игра не запустится", "Install 64-bit Windows - the game will not start on this one"));
        int total = Installer::totalRamMb();
        MEMORYSTATUSEX m = {sizeof m};
        GlobalMemoryStatusEx(&m);
        int freeNow = (int)(m.ullAvailPhys >> 20);
        if (total > 0 && total < 3000)
            problem(R, say("В компьютере всего " + gb(total) + " памяти — Minecraft с модами на нём почти не запускается",
                           "The computer has only " + gb(total) + " of memory - Minecraft with mods hardly starts on it"),
                    say("Закрой все программы перед игрой. Помогут только 4 ГБ памяти или больше",
                        "Close every program before playing. Only 4 GB of memory or more really helps"));
        else if (total > 0 && total < 4500)
            R.tips.push_back({say("В компьютере " + gb(total) + " памяти — это мало, возможны лаги и вылеты",
                                  "The computer has " + gb(total) + " of memory - that is little, lag and crashes are possible"),
                              say("Закрой браузер и другие программы перед игрой", "Close the browser and other programs before playing")});
        else if (freeNow > 0 && freeNow < 1536)
            R.tips.push_back({say("Сейчас свободно всего " + gb(freeNow) + " памяти", "Only " + gb(freeNow) + " of memory is free right now"),
                              say("Закрой лишние программы — игре нужно хотя бы 2 ГБ", "Close programs you do not need - the game needs at least 2 GB")});
        if (std::thread::hardware_concurrency() > 0 && std::thread::hardware_concurrency() <= 2)
            R.tips.push_back({say("Слабый процессор (2 потока) — игра может тормозить", "A weak processor (2 threads) - the game may lag"),
                              say("Убавь дальность прорисовки до 6–8 чанков", "Lower the render distance to 6-8 chunks")});
        // the memory the launcher gives the game
        if (Config::ramMb > 0 && total > 0 && Config::ramMb > total * 3 / 4) {
            R.fixed.push_back(say("Игре было выделено " + gb(Config::ramMb) + " памяти — больше, чем есть у компьютера. Вернули «авто» (" +
                                      gb(Installer::defaultRam()) + ")",
                                  "The game was given " + gb(Config::ramMb) + " of memory - more than the computer has. Set back to auto (" +
                                      gb(Installer::defaultRam()) + ")"));
            Config::ramMb = 0;
            Config::save();
        } else if (Config::ramMb > 0 && Config::ramMb < 2048) {
            R.fixed.push_back(say("Игре было выделено всего " + gb(Config::ramMb) + " памяти — с модами она вылетает. Вернули «авто»",
                                  "The game was given only " + gb(Config::ramMb) + " of memory - with mods it crashes. Set back to auto"));
            Config::ramMb = 0;
            Config::save();
        }
        if (!Config::javaPath.empty()) {
            fs::path p = P(Config::javaPath);
            fs::path exe = isDir(p) ? p / L"bin" / L"javaw.exe" : p;
            if (!isFile(exe)) {
                R.fixed.push_back(say("Java, выбранной в настройках, больше нет — лаунчер сам подберёт подходящую",
                                      "The Java chosen in Settings is gone - the launcher picks the right one itself"));
                Config::javaPath.clear();
                Config::save();
            }
        }
        // the video card
        if (g.basicOnly)
            problem(R, say("Не установлен драйвер видеокарты — Windows работает на «Базовом видеоадаптере»",
                           "No graphics driver is installed - Windows runs on the \"Basic Display Adapter\""),
                    say("Установи драйвер с сайта NVIDIA, AMD или Intel (или через Центр обновления Windows) и перезагрузи компьютер",
                        "Install the driver from the NVIDIA, AMD or Intel website (or Windows Update) and restart the computer"));
        // the game's folder
        makeDirs(game);
        std::error_code ec;
        string where = S(fs::absolute(game, ec));
        if (!isDir(game))
            problem(R, say("Папка игры недоступна: " + where, "The game folder cannot be reached: " + where),
                    say("Подключи диск с ней или выбери другую папку в Настройках → «Сменить расположение»",
                        "Connect its drive or choose another folder in Settings -> \"Change location\""));
        else {
            fs::path probe = game / L".arcanic-write-test";
            if (!writeFile(probe, "ok"))
                problem(R, say("В папку игры нельзя записывать файлы: " + where, "Files cannot be written into the game folder: " + where),
                        say("Выбери другую папку в Настройках → «Сменить расположение» или запусти лаунчер не из защищённой папки",
                            "Choose another folder in Settings -> \"Change location\" or keep it out of a protected folder"));
            removeFile(probe);
            ULARGE_INTEGER freeB = {};
            if (GetDiskFreeSpaceExW(game.c_str(), &freeB, nullptr, nullptr) && freeB.QuadPart < (2ull << 30))
                problem(R, say("На диске с игрой свободно всего " + gb((int64_t)(freeB.QuadPart >> 20)),
                               "Only " + gb((int64_t)(freeB.QuadPart >> 20)) + " is free on the game's drive"),
                        say("Освободи хотя бы 2 ГБ — иначе игра не скачается и не сохранит миры",
                            "Free at least 2 GB - otherwise the game cannot download or save worlds"));
        }
        if (where.find('!') != string::npos)
            problem(R, say("В пути к папке игры есть «!» — Java не может запустить игру из такой папки",
                           "The game folder's path has a \"!\" in it - Java cannot start the game from such a folder"),
                    say("Перенеси игру в Настройках → «Сменить расположение» в папку без «!»",
                        "Move the game in Settings -> \"Change location\" to a folder without \"!\""));
        R.checked.push_back(say("компьютер", "computer"));
    }

    // ---- the network: our server, then every place the game's files come from
    inline bool answers(const string& url, int timeout = 8) {
        auto r = Net::request("GET", url, {}, "", [](const char*, size_t) { return false; }, timeout);
        return r.status > 0 && r.status < 500;
    }
    inline bool network(Result& R) {
        string before = Config::base;
        bool server = Api::pickEntry();
        if (server && Config::base != before)
            R.fixed.push_back(say("Основной сервер Arcanic недоступен — переключились на запасной ru.arcanic.su",
                                  "The main Arcanic server is unreachable - switched over to the backup ru.arcanic.su"));
        struct Host {
            const char *name, *url;
        };
        static const Host hosts[] = {{"Mojang", "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"},
                                     {"Fabric", "https://meta.fabricmc.net/v2/versions/loader"},
                                     {"Modrinth", "https://api.modrinth.com/v2/tag/loader"},
                                     {"Java (Adoptium)", "https://api.adoptium.net/v3/info/available_releases"},
                                     {"Minecraft", "https://resources.download.minecraft.net/"}};
        vector<string> via, down;
        for (auto& h : hosts) {
            if (answers(h.url)) continue;
            string m = Net::mirrorFor(h.url);
            if (server && !m.empty() && answers(m, 10)) {
                Net::preferMirror = true;
                via.push_back(h.name);
            } else
                down.push_back(h.name);
        }
        auto list = [](const vector<string>& v) {
            string s;
            for (auto& x : v) s += (s.empty() ? "" : ", ") + x;
            return s;
        };
        R.checked.push_back(say("сеть", "network"));
        if (!server && down.size() == std::size(hosts)) {
            problem(R, say("Нет интернета — лаунчер не достучался ни до одного сервера", "No internet - the launcher reached no server at all"),
                    say("Проверь подключение к интернету и нажми Fix ещё раз", "Check the internet connection and press Fix again"));
            return false;
        }
        if (!via.empty())
            R.fixed.push_back(say("Нет прямой связи с " + list(via) + " — файлы пойдут через сервер Arcanic",
                                  "No direct connection to " + list(via) + " - the files will come through the Arcanic server"));
        if (!server)
            problem(R, say("Нет связи с сервером Arcanic", "No connection to the Arcanic server"),
                    say("Если интернет работает — отключи VPN, прокси или антивирус, который блокирует arcanic.su, и нажми Fix ещё раз",
                        "If the internet works, switch off the VPN, proxy or antivirus that blocks arcanic.su and press Fix again"));
        if (!down.empty())
            problem(R, say("Нет связи с: " + list(down), "No connection to: " + list(down)),
                    say("Без этого не скачать игру и моды. Отключи VPN или прокси, проверь антивирус и брандмауэр или попробуй другую сеть",
                        "The game and mods cannot be downloaded without this. Switch off the VPN or proxy, check the antivirus and firewall, or try another network"));
        return true;
    }

    // ---- the last start: if it crashed, what the logs say (each crash is looked at once)
    inline void lastCrash(Result& R, const fs::path& game, const Gpu& g) {
        fs::path latest = game / L"logs" / L"latest.log", out = game / L"logs" / L"launcher.log";
        if (!isFile(latest) && !isFile(out)) return;
        std::error_code ec;
        auto unixTime = [&](const fs::path& f) -> time_t {
            if (!isFile(f)) return 0;
            auto t = fs::last_write_time(f, ec);
            if (ec) return 0;
            return (time_t)(std::chrono::duration_cast<std::chrono::seconds>(t.time_since_epoch()).count() - 11644473600LL);
        };
        string stamp = Installer::fileStamp(latest) + "/" + Installer::fileStamp(out);
        fs::path seen = Config::stateDir() / L"fix-seen.txt";
        string was;
        if (readFile(seen, was) && trim(was) == stamp) return;
        writeFile(seen, stamp);
        Doctor::Diagnosis d = Doctor::diagnose(game, std::max(unixTime(latest), unixTime(out)) - 120);
        if (d.fixable()) {
            Doctor::Report rep;
            rep.ask = true;
            Doctor::apply(d, game / L"mods", Config::mcVersion, rep);
            merge(R, rep);
        }
        if (d.gpu)
            problem(R, say("Прошлый запуск сломался на видеокарте" + string(g.names.empty() ? "" : " (" + g.names[0] + ")"),
                           "The last start broke on the graphics card" + string(g.names.empty() ? "" : " (" + g.names[0] + ")")),
                    say("Обнови драйвер видеокарты с сайта NVIDIA, AMD или Intel и перезагрузи компьютер",
                        "Update the graphics driver from the NVIDIA, AMD or Intel website and restart the computer"));
    }

    // Does Java start with these arguments? ("java <args> -version" within 20 s)
    inline bool accepts(const fs::path& javaw, const vector<string>& args) {
        fs::path exe = javaw.parent_path() / L"java.exe";
        if (!isFile(exe)) exe = javaw;
        wstring line = L"\"" + exe.wstring() + L"\"";
        for (auto& a : args) line += L" " + Installer::quoteArg(W(a));
        line += L" -version";
        STARTUPINFOW si = {sizeof si};
        PROCESS_INFORMATION pi = {};
        if (!CreateProcessW(nullptr, line.data(), nullptr, nullptr, FALSE, CREATE_NO_WINDOW, nullptr, nullptr, &si, &pi)) return false;
        CloseHandle(pi.hThread);
        DWORD code = 1;
        if (WaitForSingleObject(pi.hProcess, 20000) == WAIT_OBJECT_0) GetExitCodeProcess(pi.hProcess, &code);
        else TerminateProcess(pi.hProcess, 1);
        CloseHandle(pi.hProcess);
        return code == 0;
    }
    inline vector<string> words(const string& s) {
        vector<string> out;
        string cur;
        for (char ch : s + " ") {
            if (ch == ' ' || ch == '\t') {
                if (!cur.empty()) out.push_back(cur);
                cur.clear();
            } else
                cur += ch;
        }
        return out;
    }

    // ---- Minecraft's own settings and the mods' settings files
    inline void settings(Result& R, const fs::path& game, const Gpu& g) {
        fs::path opt = game / L"options.txt";
        string t;
        bool weak = g.intelOnly || Installer::totalRamMb() < 6144;
        if (isFile(opt) && readFile(opt, t) && !t.empty()) {
            bool broken = t.find('\0') != string::npos;
            int lines = 0, good = 0;
            std::map<string, string> kv;
            for (auto& l : split(t, "\r\n")) {
                if (trim(l).empty()) continue;
                lines++;
                size_t c = l.find(':');
                if (c != string::npos && c > 0) {
                    good++;
                    kv[l.substr(0, c)] = trim(l.substr(c + 1));
                }
            }
            if (lines > 0 && good * 2 < lines) broken = true;
            if (broken) {
                problem(R, say("Файл настроек Minecraft (options.txt) повреждён", "Minecraft's settings file (options.txt) is damaged"),
                        say("Нажми «Исправить остальное» — лаунчер сбросит настройки игры, старый файл сохранится как options.txt.bak",
                            "Press \"Fix the rest\" - the launcher resets the game's settings, the old file is kept as options.txt.bak"));
                R.resetFiles.push_back(opt);
            } else {
                string gm = replaceAll(kv["graphicsMode"], "\"", "");
                if ((gm == "2" || gm == "fabulous") && weak)
                    R.tips.push_back({say("В настройках Minecraft графика «Изумительно» — на этом компьютере она даёт лаги и вылеты",
                                          "Minecraft's graphics are set to \"Fabulous\" - on this computer it brings lag and crashes"),
                                      say("Поставь в игре Настройки → Графика → «Детально»", "Set Options -> Graphics -> \"Fancy\" in the game")});
                int rd = atoi(kv["renderDistance"].c_str());
                if (rd > 16 && weak)
                    R.tips.push_back({say("Дальность прорисовки " + std::to_string(rd) + " чанков — для этого компьютера много",
                                          "Render distance is " + std::to_string(rd) + " chunks - a lot for this computer"),
                                      say("Убавь её до 8–12 чанков в настройках графики", "Lower it to 8-12 chunks in the graphics settings")});
            }
        }
        // a settings file a mod cannot read stops the game: empty, or zeros left by a power cut
        std::error_code ec;
        int bad = 0;
        if (isDir(game / L"config"))
            for (auto it = fs::recursive_directory_iterator(game / L"config", ec); !ec && it != fs::recursive_directory_iterator();
                 it.increment(ec)) {
                if (it.depth() > 2 || !it->is_regular_file(ec)) continue;
                string ext = lower(S(it->path().extension()));
                if (ext != ".json" && ext != ".json5" && ext != ".toml") continue;
                int64_t n = fileSize(it->path());
                if (n < 0 || n > (4 << 20)) continue;
                string c;
                if (!readFile(it->path(), c)) continue;
                size_t first = c.find_first_not_of(string(" \t\r\n\0", 5));
                bool broken = first == string::npos ||
                              (ext == ".json" && c[first] != '{' && c[first] != '[' && c[first] != '/' && c[first] != '\xEF');
                if (!broken) continue;
                string rel = S(fs::relative(it->path(), game, ec));
                problem(R, say("Повреждён файл настроек мода: " + rel, "A mod's settings file is damaged: " + rel),
                        say("Нажми «Исправить остальное» — лаунчер уберёт его (сохранится рядом как .broken), мод создаст новый",
                            "Press \"Fix the rest\" - the launcher moves it aside (kept as .broken), the mod makes a new one"));
                R.resetFiles.push_back(it->path());
                if (++bad >= 8) break;
            }
        R.checked.push_back(say("настройки Minecraft", "Minecraft settings"));
    }

    // ---- everything, in the order one thing depends on another
    inline Result run(const string& name, const string& token, const string& jar, const Step& step) {
        Result R;
        const fs::path game = Game::dir(), mods = Game::modsDir();
        const string mc = Config::mcVersion;
        step(.02f, "Проверяем компьютер…", "");
        Gpu g = gpu();
        computer(R, game, g);
        step(.06f, "Проверяем сеть…", "");
        bool online = network(R);
        step(.14f, "Проверяем прошлый запуск…", "");
        lastCrash(R, game, g);
        // Java, the game, its libraries and the mods it needs: whatever is missing or damaged is downloaded again
        Installer::Repairs rp;
        Installer::repairs = &rp;
        Installer::Plan plan;
        bool prepared = false;
        fs::path deployed;
        auto inner = [&](float f, const string& c, const string& d) { step(.16f + .62f * f, c, d); };
        try {
            if (!jar.empty()) deployed = Installer::deploy(jar);
            plan = Installer::prepare(game, name, token, inner);
            prepared = true;
        } catch (std::exception& e) {
            problem(R, say("Не удалось подготовить игру: ", "Could not get the game ready: ") + Lang::t(e.what()),
                    online ? say("Нажми Fix ещё раз. Если ошибка повторится — пришли лог лаунчера нам в Discord (Настройки → Журнал)",
                                 "Press Fix again. If it happens again, send us the launcher log on Discord (Settings -> Log)")
                           : say("Сначала почини интернет — смотри выше", "Fix the internet first - see above"));
        }
        Installer::repairs = nullptr;
        string java = rp.major ? "Java " + std::to_string(rp.major) : "Java";
        if (rp.javaBroken)
            R.fixed.push_back(say(java + " не запускалась — поставили рабочую", java + " did not start - put a working one in its place"));
        else if (rp.javaDownloaded)
            R.fixed.push_back(say("Не было " + java + " — скачали её", java + " was missing - downloaded it"));
        if (rp.describedAgain)
            R.fixed.push_back(say("Описание версии игры было повреждено — скачали заново", "The game version's description was damaged - downloaded it again"));
        if (rp.fresh && rp.missing > 0)
            R.fixed.push_back(say("Игра не была установлена — скачали её (" + std::to_string(rp.missing) + " файлов)",
                                  "The game was not installed - downloaded it (" + std::to_string(rp.missing) + " files)"));
        else if (rp.missing > 0)
            R.fixed.push_back(say("Не хватало файлов игры и зависимостей (" + std::to_string(rp.missing) + ") — докачали",
                                  "Game files and dependencies were missing (" + std::to_string(rp.missing) + ") - downloaded them"));
        if (rp.damaged > 0)
            R.fixed.push_back(say("Повреждённых файлов игры: " + std::to_string(rp.damaged) + " — скачали заново",
                                  "Damaged game files: " + std::to_string(rp.damaged) + " - downloaded them again"));
        if (prepared) {
            R.checked.push_back(java);
            R.checked.push_back(say("файлы игры", "game files"));
        }
        // the mods, read the way Fabric will read them
        step(.8f, "Проверяем моды…", "");
        Doctor::Report rep = Doctor::scan(mods, mc, true);
        merge(R, rep);
        if (prepared && !Doctor::loaderFits(plan.loader, rep.loaderNeeds)) {
            auto needs = rep.loaderNeeds;
            try {
                plan = Installer::prepare(game, name, token, inner, [needs](const string& v) { return Doctor::loaderFits(v, needs); });
                R.fixed.push_back(say("Поставили Fabric Loader " + plan.loader + " — его требуют установленные моды",
                                      "Installed Fabric Loader " + plan.loader + " - the installed mods need it"));
            } catch (...) {}
        }
        int count = 0;
        for (auto& m : Doctor::read(mods))
            if (m.fabric && !m.ours) count++;
        R.checked.push_back(Lang::en ? fmt("%d mods", count) : fmt("модов: %d", count));
        if (Config::cleanAfterPlay && !deployed.empty()) removeFile(deployed);
        // the launch settings, held against the Java that will run the game
        if (prepared && !plan.command.empty()) {
            step(.9f, "Проверяем параметры Java…", "");
            fs::path javaw = P(plan.command[0]);
            if (!Config::jvmArgs.empty() && !accepts(javaw, words(Config::jvmArgs))) {
                R.fixed.push_back(say("Java не принимала твои параметры запуска («" + Config::jvmArgs + "») — убрали их",
                                      "Java refused your launch settings (\"" + Config::jvmArgs + "\") - removed them"));
                Config::jvmArgs.clear();
                Config::save();
            }
            int ram = Config::ramMb > 0 ? Config::ramMb : Installer::defaultRam();
            if (!accepts(javaw, {"-Xmx" + std::to_string(ram) + "M"})) {
                if (Config::ramMb > 0) {
                    Config::ramMb = 0;
                    Config::save();
                    R.fixed.push_back(say("Java не смогла взять " + gb(ram) + " памяти — вернули «авто»",
                                          "Java could not take " + gb(ram) + " of memory - set back to auto"));
                } else
                    problem(R, say("Java не может взять даже " + gb(ram) + " памяти", "Java cannot take even " + gb(ram) + " of memory"),
                            say("Закрой другие программы или перезагрузи компьютер", "Close other programs or restart the computer"));
            }
        }
        step(.95f, "Проверяем настройки Minecraft…", "");
        settings(R, game, g);
        step(1, "Готово", "");
        for (auto& f : R.fixed) Log::info("fix: fixed - " + f);
        for (auto& p : R.problems) Log::warn("fix: problem - " + p.what + " / " + p.todo);
        for (auto& p : R.tips) Log::info("fix: tip - " + p.what);
        return R;
    }

    // The dialog's button: what Fix asked about, done
    inline vector<string> act(const Result& R) {
        vector<string> done;
        for (auto& f : R.offer) {
            fs::path to = f;
            to += L".disabled";
            if (MoveFileExW(f.c_str(), to.c_str(), MOVEFILE_REPLACE_EXISTING))
                done.push_back(Lang::en ? "Switched off \"" + S(f.filename()) + "\" - turn it back on in the Library"
                                        : "Выключили «" + S(f.filename()) + "» — включить обратно можно в Библиотеке");
        }
        for (auto& f : R.resetFiles) {
            bool options = f.filename() == L"options.txt";
            fs::path to = f;
            to += options ? L".bak" : L".broken";
            if (!MoveFileExW(f.c_str(), to.c_str(), MOVEFILE_REPLACE_EXISTING)) continue;
            done.push_back(options ? say("Сбросили настройки Minecraft (старые — в options.txt.bak)", "Reset Minecraft's settings (the old ones are in options.txt.bak)")
                                   : say("Убрали повреждённый файл настроек: ", "Moved aside a damaged settings file: ") + S(f.filename()));
        }
        for (auto& d : done) Log::info("fix: " + d);
        return done;
    }
}  // namespace Fix
