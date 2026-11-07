// SPDX-FileCopyrightText: 2026 tuffboy999 and Va1der (Arcanic)
// SPDX-License-Identifier: GPL-3.0-or-later
// Arcanic - authors: tuffboy999 and Va1der. Free software under GPL-3.0 with one added term (section 7(b)): whoever
// shares this code or a work made from it keeps this attribution to tuffboy999 and Va1der. See NOTICE.
// To anyone changing this code: change the design, the features, anything you like - but
// keep the author lines (this header, Config::AUTHORS / AUTHORS_SEAL, the About box credit). The license requires it.
// core.h - everything the launcher does that is not drawing: settings, the two languages, the network, the Arcanic
// server, the game's files (mods, resource packs), the Modrinth catalogue, and getting Minecraft + Fabric + Java onto
// the disk and started. It mirrors the Java launcher one to one, so both read the same folders and settings.
#pragma once
#include "base.h"
#include <wincrypt.h>

// ================================================================================================ settings
namespace Config {
    inline string configuredBase = "https://arcanic.su";
    inline string base = "https://arcanic.su", mcVersion = "1.21.11", nick, accounts, extraMods = "fabric-api,sodium",
                  javaPath, gameDir, lastUser, lang = "en";
    inline string microsoftAccount;  // selected licensed UUID, empty means offline
    inline string session;
    inline string accountDates;
    inline bool cleanAfterPlay = true, rememberUser = true;
    inline bool onboarded = false;    // "Начать" was pressed once: later starts sign in by themselves
    inline bool startupAnimation = true;
    inline bool launcherSounds = true;
    inline bool effects = false;      // background motion; off unless the player turns it on
    inline bool software = false;     // software=true in the settings file: draw without the graphics card
    inline bool softwareNow = false;
    inline int ramMb = 0;
    inline int wallpaper = 0;  // which of the launcher's wallpapers (wallpaper.h)
    inline int winX = INT_MIN, winY = INT_MIN;  // where the window was left (INT_MIN: centred)
    inline string jvmArgs;  // extra JVM arguments the player added in "Параметры"
    inline float uiScale = 1.f;
    inline int modSources = 3, packSources = 3;  // mods: 1 Modrinth, 2 CurseForge; packs: 1 PacksMC, 2 Modrinth; 3 both (the default)
    inline const char* MOD_FILE = "arcanic-client.jar";
    inline const char* VERSION = "1.4.22";  // this launcher (the same number as in res.rc)
    // the authors of Arcanic: tuffboy999 and Va1der. GPL-3.0 section 7(b) (see NOTICE and LICENSE.txt): anyone who
    // shares this launcher, changed or not, keeps this attribution - in the About box and in the program's notices.
    inline const char* AUTHORS = "tuffboy999 & Va1der";
    inline const unsigned char AUTHORS_SEAL[] = {0x2E, 0x2F, 0x3C, 0x3C, 0x38, 0x35, 0x23, 0x63, 0x63, 0x63, 0x7A, 0x7C, 0x7A, 0x0C, 0x3B, 0x6B, 0x3E, 0x3F, 0x28};
    inline string authors() {
        string s;
        for (unsigned char ch : AUTHORS_SEAL) s += (char)(ch ^ 0x5A);
        return s;
    }

    inline fs::path stateDir() {
        return homeDir() / L".arcanic";
    }
    inline fs::path file() {
        return stateDir() / L"launcher.properties";
    }

    inline string unescape(const string& v) {
        string out;
        for (size_t i = 0; i < v.size(); i++) {
            if (v[i] != '\\' || i + 1 >= v.size()) {
                out += v[i];
                continue;
            }
            char c = v[++i];
            if (c == 'u' && i + 4 < v.size()) {
                wchar_t w = (wchar_t)strtoul(v.substr(i + 1, 4).c_str(), nullptr, 16);
                out += U(wstring(1, w));
                i += 4;
            } else if (c == 'n')
                out += '\n';
            else if (c == 't')
                out += '\t';
            else
                out += c;
        }
        return out;
    }
    inline string escape(const string& v) {
        string out;
        for (wchar_t c : W(v)) {
            if (c == L'\\' || c == L':' || c == L'=' || c == L'#' || c == L'!') {
                out += '\\';
                out += (char)c;
            } else if (c < 0x20 || c > 0x7E)
                out += fmt("\\u%04X", (unsigned)c);
            else
                out += (char)c;
        }
        return out;
    }
    void save();
    // the Arcanic session is kept encrypted for this Windows user (DPAPI); an older plain one is read as it is
    inline string seal(const string& plain) {
        if (plain.empty()) return "";
        DATA_BLOB in{(DWORD)plain.size(), (BYTE*)plain.data()}, out{};
        if (!CryptProtectData(&in, L"Arcanic session", nullptr, nullptr, nullptr, CRYPTPROTECT_UI_FORBIDDEN, &out))
            return "";
        string sealed = "dpapi:" + hex(out.pbData, out.cbData);
        LocalFree(out.pbData);
        return sealed;
    }
    inline string unseal(const string& text) {
        if (!starts(text, "dpapi:")) return text;
        string bytes;
        for (size_t i = 6; i + 1 < text.size(); i += 2) bytes += (char)strtol(text.substr(i, 2).c_str(), nullptr, 16);
        DATA_BLOB in{(DWORD)bytes.size(), (BYTE*)bytes.data()}, out{};
        if (!CryptUnprotectData(&in, nullptr, nullptr, nullptr, nullptr, CRYPTPROTECT_UI_FORBIDDEN, &out)) return "";
        string plain((char*)out.pbData, out.cbData);
        SecureZeroMemory(out.pbData, out.cbData);
        LocalFree(out.pbData);
        return plain;
    }
    inline void load() {
        string text;
        if (!readFile(file(), text)) {
            save();
            return;
        }
        std::map<string, string> p;
        for (auto& line : split(text, "\r\n")) {
            string l = trim(line);
            if (l.empty() || l[0] == '#' || l[0] == '!') continue;
            size_t eq = string::npos;
            for (size_t i = 0; i < l.size(); i++) {
                if (l[i] == '\\') {
                    i++;
                    continue;
                }
                if (l[i] == '=' || l[i] == ':') {
                    eq = i;
                    break;
                }
            }
            if (eq == string::npos) continue;
            p[trim(unescape(l.substr(0, eq)))] = unescape(trim(l.substr(eq + 1)));
        }
        auto get = [&](const char* k, const string& def) {
            auto it = p.find(k);
            return it == p.end() ? def : it->second;
        };
        base = configuredBase = trim(get("base", base));
        mcVersion = trim(get("mcVersion", mcVersion));
        nick = trim(get("nick", nick));
        accounts = trim(get("accounts", accounts));
        extraMods = trim(get("extraMods", extraMods));
        javaPath = trim(get("javaPath", javaPath));
        gameDir = trim(get("gameDir", gameDir));
        microsoftAccount = get("microsoftAccount", "");
        accountDates = get("accountDates", "");
        session = unseal(trim(get("session", "")));
        lastUser = get("lastUser", lastUser);
        lang = trim(get("lang", lang));
        cleanAfterPlay = get("cleanAfterPlay", "true") == "true";
        onboarded = get("onboarded", "false") == "true";
        rememberUser = get("rememberUser", "true") == "true";
        startupAnimation = get("startupAnimation", "true") == "true";
        modSources = std::clamp(atoi(get("modSources", "3").c_str()), 1, 3);
        if (get("modSourcesV", "") != "2") modSources = 3;  // CurseForge is new: everyone starts with both sites
        packSources = std::clamp(atoi(get("packSources", "3").c_str()), 1, 3);
        if (get("packSourcesV", "") != "2") packSources = 3;
        launcherSounds = get("launcherSounds", "true") == "true";
        effects = get("effects", "false") == "true";
        software = get("software", "false") == "true";
        ramMb = atoi(get("ramMb", "0").c_str());
        wallpaper = std::clamp(atoi(get("wallpaper", "0").c_str()), 0, 11);  // wallpaper.h has 12
        if (p.count("winX") && p.count("winY")) {
            winX = atoi(p["winX"].c_str());
            winY = atoi(p["winY"].c_str());
        }
        jvmArgs = trim(get("jvmArgs", ""));
        float s = (float)atof(get("uiScale", "1").c_str());
        if (s >= .7f && s <= 1.3f) uiScale = s;
        while (ends(base, "/")) base.pop_back();
    }
    inline void save() {
        string t = "#Arcanic launcher settings\n";
        auto put = [&](const char* k, const string& v) { t += string(k) + "=" + escape(v) + "\n"; };
        put("base", configuredBase);
        put("cleanAfterPlay", cleanAfterPlay ? "true" : "false");
        put("mcVersion", mcVersion);
        put("nick", nick);
        put("accounts", accounts);
        put("ramMb", std::to_string(ramMb));
        put("wallpaper", std::to_string(wallpaper));
        if (winX != INT_MIN) {
            put("winX", std::to_string(winX));
            put("winY", std::to_string(winY));
        }
        put("jvmArgs", jvmArgs);
        put("extraMods", extraMods);
        put("javaPath", javaPath);
        put("gameDir", gameDir);
        put("uiScale", fmt("%.3f", uiScale));
        put("startupAnimation", startupAnimation ? "true" : "false");
        put("modSources",std::to_string(modSources));
        put("packSources",std::to_string(packSources));
        put("packSourcesV", "2");
        put("modSourcesV", "2");
        put("launcherSounds", launcherSounds ? "true" : "false");
        put("effects", effects ? "true" : "false");
        put("software", software ? "true" : "false");
        put("lang", lang);
        put("session", seal(session));
        put("microsoftAccount", microsoftAccount);
        put("accountDates", accountDates);
        put("onboarded", onboarded ? "true" : "false");
        put("rememberUser", rememberUser ? "true" : "false");
        put("lastUser", rememberUser ? lastUser : "");
        // written beside and swapped in whole: a crash mid-write never leaves a cut-off file (and a lost sign-in)
        static std::mutex lock;
        std::lock_guard<std::mutex> hold(lock);
        fs::path tmp = file();
        tmp += L".tmp";
        if (!writeFile(tmp, t) || !MoveFileExW(tmp.c_str(), file().c_str(), MOVEFILE_REPLACE_EXISTING | MOVEFILE_WRITE_THROUGH)) {
            removeFile(tmp);
            writeFile(file(), t);
        }
    }
}  // namespace Config

// ================================================================================================ the log
// ~/.arcanic/logs/launcher.log: what the launcher did and what went wrong, with times
namespace Log {
    inline fs::path folder() { return Config::stateDir() / L"logs"; }
    inline fs::path file() { return folder() / L"launcher.log"; }
    inline std::mutex lock;
    inline void write(const char* level, const string& line) {
        std::lock_guard<std::mutex> hold(lock);
        std::error_code ec;
        fs::create_directories(folder(), ec);
        if (fileSize(file()) > (1 << 20)) {
            fs::path old = folder() / L"launcher.old.log";
            MoveFileExW(file().c_str(), old.c_str(), MOVEFILE_REPLACE_EXISTING);
        }
        SYSTEMTIME t;
        GetLocalTime(&t);
        string text = fmt("%04d-%02d-%02d %02d:%02d:%02d.%03d  %-5s ", t.wYear, t.wMonth, t.wDay, t.wHour, t.wMinute, t.wSecond,
                          t.wMilliseconds, level) + line + "\r\n";
        HANDLE h = CreateFileW(file().c_str(), FILE_APPEND_DATA, FILE_SHARE_READ | FILE_SHARE_WRITE, nullptr, OPEN_ALWAYS,
                               FILE_ATTRIBUTE_NORMAL, nullptr);
        if (h == INVALID_HANDLE_VALUE) return;
        DWORD put = 0;
        WriteFile(h, text.data(), (DWORD)text.size(), &put, nullptr);
        CloseHandle(h);
    }
    inline void info(const string& line) { write("INFO", line); }
    inline void warn(const string& line) { write("WARN", line); }
    inline void error(const string& line) { write("ERROR", line); }
    // An address as it may be written down: scheme, host and path, no query (it can carry keys)
    inline string safeUrl(const string& url) {
        size_t q = url.find_first_of("?#");
        return q == string::npos ? url : url.substr(0, q) + "?…";
    }
    // Windows, its build and the machine, for the first lines of the log
    inline string systemLine() {
        typedef LONG(WINAPI * RtlGetVersionFn)(OSVERSIONINFOW*);
        OSVERSIONINFOW v = {sizeof v};
        if (auto f = (RtlGetVersionFn)GetProcAddress(GetModuleHandleW(L"ntdll.dll"), "RtlGetVersion")) f(&v);
        SYSTEM_INFO si;
        GetNativeSystemInfo(&si);
        MEMORYSTATUSEX m = {sizeof m};
        GlobalMemoryStatusEx(&m);
        return fmt("Windows %lu.%lu build %lu, %s, %d cores, %llu MB RAM", v.dwMajorVersion, v.dwMinorVersion, v.dwBuildNumber,
                   si.wProcessorArchitecture == PROCESSOR_ARCHITECTURE_ARM64 ? "ARM64" : si.wProcessorArchitecture == PROCESSOR_ARCHITECTURE_AMD64 ? "x64" : "x86",
                   (int)si.dwNumberOfProcessors, (unsigned long long)(m.ullTotalPhys >> 20));
    }
    inline DWORD windowsMajor() {
        typedef LONG(WINAPI * RtlGetVersionFn)(OSVERSIONINFOW*);
        OSVERSIONINFOW v = {sizeof v};
        if (auto f = (RtlGetVersionFn)GetProcAddress(GetModuleHandleW(L"ntdll.dll"), "RtlGetVersion")) f(&v);
        return v.dwMajorVersion;
    }
}  // namespace Log

// ================================================================================================ languages
namespace Lang {
    inline std::atomic<bool> en{true};
    inline const char* WHOLE[] = {
        "есть обновление", "update",
        "Проверяем моды…", "Checking mods…",
        "Исправляем ошибку и запускаем снова…", "Fixing the error and starting again…",
        "Обновляем…", "Updating…",
        "Закрой игру, чтобы обновить моды", "Close the game to update mods",
        "Не удалось сменить моды версии — закрой программы, которые держат файлы в папке mods", "Could not switch this version's mods - close programs that keep files in the mods folder open",
        "Золотое яблоко найдено!", "Golden apple found!",
        "Arcanic × macOS — спасибо, что играешь с нами", "Arcanic × macOS - thanks for playing with us",
        "Ждём файл…", "Waiting…",
        "Скачать", "Download",
        "Поиск в библиотеке", "Search the library",
        "Поиск ресурспаков", "Search resource packs",
        "Поиск модов", "Search mods",
        "Загружаем ещё…", "Loading more…",
        "Прокрути ниже — загрузим ещё", "Scroll down to load more",
        "Анимации", "Animated",
        "PacksMC: слишком много запросов, попробуй через минуту", "PacksMC: too many requests, try again in a minute",
        "PacksMC сейчас недоступен, попробуй позже", "PacksMC is unavailable right now, try later",        "Моды, ресурспаки и косметика — в одном окне", "Mods, resource packs and cosmetics in one window",
        "Лог ошибки — пришли этот файл нам в Discord", "Error log — send this file to us on Discord",
        "Для игры", "In use",
        "Войти", "Sign in",
        "Выйти", "Sign out",
        "Сайт", "Website",
        "Журнал", "Log",
        "сейчас", "now",
        "Вход не выполнен", "Not signed in",
        "синхронизация включена", "sync is on",
        "Войди, чтобы другие игроки видели твою косметику", "Sign in so other players can see your cosmetics",
        "Внешний вид", "Appearance",
        "Обои", "Wallpaper",
        "Волны", "Waves",
        "Лагуна", "Lagoon",
        "Закат", "Sunset",
        "Графит", "Graphite",
        "Аврора", "Aurora",
        "Сирень", "Lilac",
        "Дюны", "Dunes",
        "Полночь", "Midnight",
        "Изумруд", "Emerald",
        "Роза", "Rose",
        "Сумерки", "Dusk",
        "Лёд", "Ice",
        "Звук и анимация", "Sound & motion",
        "авто", "auto",
        "Папка mods остаётся чистой для других сборок", "Keeps the mods folder clean for other setups",
        "Для опытных игроков", "For experienced players",
        "О программе", "About",
        "Microsoft или ник", "Microsoft or a nickname",        "Источники", "Sources",
        "Добавь моды из Modrinth или открой папку игры", "Add mods from Modrinth or open the game folder",
        "Каталог недоступен", "The catalogue is unavailable",
        "Память, файлы игры и лаунчер", "Memory, game files and the launcher",
        "Обновить лаунчер", "Update the launcher",
        "Скачиваем обновление…", "Downloading the update…",
        "Не удалось обновить лаунчер", "Could not update the launcher",
        "Дымка на фоне меню", "Mist behind the menu",
        "Продолжить", "Continue",
        "Главное", "General",
        "Поиск", "Search",
        "Каталог", "Catalogue",
        "Библиотека", "Library",
        "Сервисы", "Services",
        "Мои версии", "My versions",
        "Смотреть все", "See all",
        "Популярные моды", "Popular mods",
        "рекомендуем", "recommended",
        "Плащи, эмоции и эффекты, которые видят другие игроки Arcanic.", "Capes, emotes and effects that other Arcanic players see.",
        "Мод", "Mod",
        "Ресурспак", "Resource pack",
        "в игре", "playing",
        "Все аккаунты", "All accounts",
        "Уведомления", "Notifications",
        "Новых уведомлений нет", "No new notifications",
        "Я ищу…", "I'm looking for…",
        "Недавние", "Recent",
        "Здесь появятся твои запросы", "Your searches will show up here",
        "Искать снова", "Search again",
        "Поиск модов и ресурспаков…", "Search mods and resource packs…",
        "С возвращением", "Welcome back",
        "Добро пожаловать", "Welcome",
        "Клиент, моды, ресурспаки и косметика Arcanic в одном месте.", "The Arcanic client, mods, resource packs and cosmetics in one place.",
        "Загружаем…", "Loading…",
        "Поиск по нику", "Find by nick",
        "Скопировать путь", "Copy path",
        "Скопировано", "Copied",
        "Пришли этот файл нам в Discord — так мы быстрее найдём причину", "Send us this file on Discord: it helps us find the cause faster",
        "Не удалось запустить игру", "The game could not start",
        "Игра закрылась с ошибкой", "The game closed with an error",
        "Лаунчер не смог подключиться", "The launcher could not connect",
        "Старая версия Windows", "An old version of Windows",
        "Minecraft 1.21 и Java 21 работают на Windows 10 и 11. Лаунчер откроется, но игра может не запуститься.", "Minecraft 1.21 and Java 21 run on Windows 10 and 11. The launcher opens, but the game may not start.",
        "Неизвестная ошибка", "Unknown error",
        "Перетащи .zip ресурспака или .jar мода", "Drop a resource pack .zip or a mod .jar",
        "Готово к запуску", "Ready to play",
        "Обновления ставятся сами при запуске", "Updates install themselves at start-up",
        "Косметика и синхронизация", "Cosmetics and sync",
        "Сбросить", "Reset",
        "Интерфейс", "Interface",
        "Масштаб", "Scale",
        "Язык", "Language",
        "Звуки", "Sounds",
        "Заставка при запуске", "Startup animation",
        "Оперативная память", "Memory",
        "Перенос — в пустую папку, файлы переедут сами", "Pick an empty folder and the files move there",
        "Открыть каталог", "Browse the catalogue",
        "Добавь ZIP или скачай пак из каталога", "Add a ZIP or get a pack from the catalogue",
        "Добавить ZIP", "Add ZIP",
        "Начать",
        "Get started",
        "Наш Discord",
        "Join our Discord",
        "Базовая версия клиента.",
        "The basic version of the client.",
        "Ранний доступ: новые функции приходят к тебе первыми.",
        "Early access: new features reach you first.",
        "Полная версия клиента: всё открыто.",
        "The full version of the client: everything is open.",
        "Твой аккаунт Arcanic и версия клиента",
        "Your Arcanic account and client version",
        "Версии игроков",
        "Players' plans",
        "Premium по логину аккаунта Arcanic",
        "Premium by the Arcanic account's login",
        "Логин игрока",
        "Player's login",
        "Выдать Premium",
        "Grant Premium",
        "Снять",
        "Revoke",
        "Premium выдан",
        "Premium granted",
        "Premium снят",
        "Premium revoked",
        "Список", "List",
        "Навсегда", "Lifetime",
        "Дней", "Days",
        "дн.", "days",
        "Введи дни: от 1 до 3650", "Enter the days: 1 to 3650",
        "Выдать дни", "Give days",
        "Забрать", "Take away",
        "навсегда", "lifetime",
        "до", "until",
        "Ни у кого нет Premium", "Nobody has Premium",
        "Premium продлён", "Premium extended",
        "Premium навсегда", "Lifetime Premium",
        "Такого аккаунта нет",
        "No such account",
        "Нет прав",
        "Not allowed",
        "Версию админа менять нельзя",
        "An admin's plan cannot be changed",
        "Введи логин",
        "Type a login",
        "Синхронизация и косметика",
        "Sync and cosmetics",
        "Аккаунт Arcanic — бесплатно",
        "An Arcanic account is free",
        "Войди в аккаунт Arcanic или создай его — другие игроки с Arcanic увидят твои плащи, крылья и эмоции, а ты "
        "увидишь их.",
        "Sign in to your Arcanic account or make one: other Arcanic players will see your capes, wings and emotes, and "
        "you will see theirs.",
        "Войти или создать аккаунт",
        "Sign in or sign up",
        "Войди в аккаунт Arcanic",
        "Sign in to your Arcanic account",
        "чтобы видеть кастомную косметику",
        "to see custom cosmetics",
        "Войти в Arcanic",
        "Sign in to Arcanic",
        "Профиль Arcanic",
        "Arcanic profile",
        "Отменить вход",
        "Cancel the sign-in",
        "Аккаунт Arcanic",
        "Arcanic account",
        "Открываем сайт для входа…",
        "Opening the sign-in page…",
        "Заверши вход на сайте. Повторное нажатие отменит вход.",
        "Finish signing in on the site. Click again to cancel.",
        "Вход в Arcanic выполнен",
        "Signed in to Arcanic",
        "Вход отменён",
        "Sign-in cancelled",
        "Время входа истекло. Попробуй ещё раз",
        "The sign-in timed out. Try again",
        "Не удалось открыть браузер",
        "Could not open the browser",
        "Не удалось начать вход",
        "Could not start the sign-in",
        "Сайт вернул неверный ответ",
        "The site returned a wrong answer",
        "Ты вышел из аккаунта Arcanic",
        "You signed out of Arcanic",
        "Войди в аккаунт заново",
        "Sign in again",
        "Вход в Arcanic устарел — войди заново",
        "The Arcanic sign-in has expired — sign in again",
        "Косметика и синхронизация привязаны к этому аккаунту.",
        "Cosmetics and sync belong to this account.",
        "Сервер недоступен — играем без синхронизации",
        "The server is unreachable — playing without sync",
        "Добавлен: ",
        "Added: ",
        "Добавлен ранее",
        "Added earlier",
        "Выбрано",
        "Selected",
        "Выбрать",
        "Select",
        "Поиск по никнейму",
        "Search by nickname",
        "Microsoft",
        "Microsoft",
        "Локальный",
        "Local",
        "Назад",
        "Back",
        "Никого не нашли",
        "Nobody found",
        "Ник скопирован",
        "Nickname copied",
        "Обновляем…",
        "Refreshing…",
        "Скин обновлён",
        "Skin refreshed",
        "Аккаунт удалён",
        "Account removed",
        "Локальные",
        "Local ones",
        "Выберите способ авторизации: войдите через Microsoft или используйте локальный профиль",
        "Choose how to sign in: with Microsoft or with a local profile",
        "Доступ к лицензионным серверам через авторизацию Microsoft",
        "Access to licensed servers through Microsoft sign-in",
        "Доступ к нелицензионным серверам по никнейму",
        "Access to non-licensed servers by nickname",
        "Введите никнейм",
        "Enter a nickname",
        "Версии",
        "Versions",
        "Выбери, на какой версии играть",
        "Pick the version you play on",
        "Выбрана",
        "Selected",
        "Параметры запуска",
        "Launch options",
        "Аккаунты",
        "Accounts",
        "Под каким аккаунтом ты заходишь в игру",
        "The account you enter the game with",
        "Твои профили",
        "Your profiles",
        "Лицензия Microsoft",
        "Microsoft licence",
        "Локальный профиль",
        "Local profile",
        "В игре",
        "In use",
        "Выбрать",
        "Select",
        "Добавить аккаунт",
        "Add an account",
        "Выбери способ входа: аккаунт Microsoft или локальный профиль по нику",
        "Choose how to sign in: a Microsoft account or a local profile by nickname",
        "Доступ к лицензионным серверам через",
        "Access to licensed servers through",
        "авторизацию Microsoft",
        "Microsoft sign-in",
        "Без лицензии",
        "No licence",
        "Доступ к нелицензионным серверам",
        "Access to non-licensed servers",
        "по никнейму",
        "by nickname",
        "Отменить вход",
        "Cancel sign-in",
        "Ник: 3–16 символов — латинские буквы, цифры и _",
        "Nickname: 3–16 characters — Latin letters, digits and _",
        "Версия игры",
        "Game version",
        "Клиент",
        "Client",
        "Игра",
        "Game",
        "Запуск",
        "Start",
        "Проверяем обновления клиента…",
        "Checking for client updates…",
        "Проверяем версию игры…",
        "Checking the game version…",
        "Проверяем Java…",
        "Checking Java…",
        "Проверяем целостность файлов…",
        "Checking file integrity…",
        "Докачиваем файлы…",
        "Fetching missing files…",
        "Последняя версия",
        "Latest version",
        "Клиент обновлён",
        "Client updated",
        "Сервер не ответил — запускаем текущую версию",
        "The server did not answer — starting the current version",
        "Заходи в наш Discord — новости, помощь и общение",
        "Join our Discord — news, help and good company",
        "Визуальный клиент для Minecraft",
        "A visual client for Minecraft",
        "Сервер отклонил вход",
        "The server refused the sign-in",
        "Защищённое соединение",
        "Secure connection",
        "Тестовый сервер без шифрования",
        "Test server, no encryption",
        "Проверяем доступ…",
        "Checking access…",
        "Загружаем клиент…",
        "Downloading the client…",
        "Проверяем файл…",
        "Checking the file…",
        "Готово",
        "Done",
        "Вход на сервер",
        "Signing in",
        "Получаем данные…",
        "Receiving data…",
        "Клиент получен",
        "Client received",
        "Сервер не выдал сессию",
        "The server did not start a session",
        "Сессия отклонена сервером",
        "The server rejected the session",
        "Нет связи с сервером",
        "No connection to the server",
        "Клиент скачался с ошибкой",
        "The client did not download correctly",
        "Обновляем лаунчер…",
        "Updating the launcher…",
        "Запускаем новую версию…",
        "Starting the new version…",
        "Лаунчер обновлён",
        "The launcher was updated",
        "Описание Minecraft скачалось не целиком — нажми «Играть» ещё раз",
        "The description of Minecraft did not arrive whole — press Play again",
        "Описание Fabric скачалось не целиком — нажми «Играть» ещё раз",
        "The description of Fabric did not arrive whole — press Play again",
        "Ошибка загрузки",
        "Download error",
        "Версия:",
        "Version:",
        "Клиент готов",
        "Client ready",
        "Игра запущена",
        "Game is running",
        "Главная",
        "Home",
        "Моды",
        "Mods",
        "Ресурспаки",
        "Resource packs",
        "Версия",
        "Version",
        "Настройки",
        "Settings",
        "Папка игры",
        "Game folder",
        "Выйти из аккаунта",
        "Sign out",
        "Профиль",
        "Profile",
        "Твой доступ и версия клиента",
        "Your access and client version",
        "Сейчас играют:",
        "Playing now:",
        "Beta-доступ",
        "Beta access",
        "Игрок",
        "Player",
        "Параметры Arcanic",
        "Arcanic settings",
        "Оперативная память:",
        "Memory:",
        "Эффекты и анимации в лаунчере",
        "Launcher effects and animations",
        "Убирать клиент из mods после игры",
        "Remove the client from mods after playing",
        "Дополнительные аргументы JVM:",
        "Extra JVM arguments:",
        "Аргументы JVM",
        "JVM arguments",
        "Отмена",
        "Cancel",
        "Применить",
        "Apply",
        "Параметры сохранены",
        "Settings saved",
        "Эффекты, плавные анимации и свой стиль — всё уже",
        "Effects, smooth animations and your own style — it is all",
        "собрано, осталось нажать «Играть».",
        "set up, all that is left is to press Play.",
        "Играть",
        "Play",
        "Запуск…",
        "Starting…",
        "Настроить",
        "Settings",
        "Список модов",
        "Mod list",
        "Наш сайт",
        "Our site",
        "Клиент встанет в mods на время игры",
        "The client goes into mods while you play",
        "Клиент останется в mods после игры",
        "The client stays in mods after the game",
        "Закрой игру, чтобы вернуться сюда",
        "Close the game to come back here",
        "Игра закрыта",
        "Game closed",
        "Проверяем файлы…",
        "Checking files…",
        "Загружаем игру…",
        "Downloading the game…",
        "Загружаем Java…",
        "Downloading Java…",
        "Распаковываем Java…",
        "Unpacking Java…",
        "Запускаем игру…",
        "Starting the game…",
        "Что запускается вместе с клиентом",
        "What starts together with the client",
        "Открыть папку",
        "Open folder",
        "Поиск мода",
        "Search mods",
        "Название — ищем на Modrinth и CurseForge",
        "A name — searched on Modrinth and CurseForge",
        "На сайте",
        "On the site",
        "Все",
        "All",
        "Включённые",
        "Enabled",
        "Выключенные",
        "Disabled",
        "нужен клиенту",
        "required",
        "Модов пока нет",
        "No mods yet",
        "Они появятся после первого запуска игры — или поставь их из каталога ниже",
        "They appear after the first start of the game — or install some from the catalogue below",
        "Ничего не найдено",
        "Nothing found",
        "Попробуй другое название или сними фильтр",
        "Try another name or clear the filter",
        "Рекомендуем установить",
        "Recommended",
        "Найдено на Modrinth",
        "Found on Modrinth",
        "Ищем…",
        "Searching…",
        "Modrinth недоступен",
        "Modrinth is unavailable",
        "Всё рекомендованное уже стоит",
        "Everything recommended is installed",
        "Установить",
        "Install",
        "Скачиваем",
        "Downloading",
        "Установлен",
        "Installed",
        "Нет версии",
        "No build",
        "Повторить",
        "Retry",
        "Включай и выключай паки без захода в игру",
        "Switch packs on and off without opening the game",
        "Поиск ресурспака",
        "Search resource packs",
        "Название — ищем на Modrinth",
        "A name — searched on Modrinth",
        "Каталог модов",
        "Mod catalogue",
        "Ресурспаки с Modrinth",
        "Resource packs from Modrinth",
        "Оптимизация",
        "Performance",
        "Утилиты",
        "Utility",
        "Декор",
        "Decoration",
        "Приключения",
        "Adventure",
        "Снаряжение",
        "Equipment",
        "Библиотеки",
        "Libraries",
        "Ванильные",
        "Vanilla-like",
        "Реализм",
        "Realistic",
        "Простые",
        "Simple",
        "Тематические",
        "Themed",
        "Твики",
        "Tweaks",
        "Скриншотов нет",
        "No screenshots",
        "Загружаем картинку…",
        "Loading the picture…",
        "Картинка не загрузилась",
        "The picture did not load",
        "Закрыть",
        "Close",
        "Автор: ",
        "By ",
        "Ничего не нашлось",
        "Nothing found",
        "Включи его в списке выше",
        "Switch it on in the list above",
        "Нажми на строку, чтобы посмотреть скриншоты",
        "Click a row to see screenshots",
        "Кто играет?",
        "Who is playing?",
        "Новый ник",
        "New nickname",
        "Аккаунт Microsoft",
        "Microsoft account",
        "Лицензия",
        "Licensed",
        "скоро",
        "soon",
        "Аккаунт",
        "Account",
        "Офлайн-аккаунт",
        "Offline account",
        "Под каким именем ты заходишь в игру",
        "The name you play under",
        "Ник",
        "Nickname",
        "Сохранить",
        "Save",
        "3–16 символов: латинские буквы, цифры и _",
        "3–16 characters: Latin letters, digits and _",
        "Ник: от 3 до 16 латинских букв, цифр или _",
        "Nickname: 3 to 16 Latin letters, digits or _",
        "Аккаунт Microsoft (лицензия)",
        "Microsoft account (licensed)",
        "Войти через Microsoft",
        "Sign in with Microsoft",
        "Скоро: вход появится, когда Mojang одобрит наше приложение",
        "Coming soon: sign-in arrives once Mojang approves our app",
        "Новый ник подействует при следующем запуске игры",
        "The new nickname applies the next time the game starts",
        "Ресурспаков нет",
        "No resource packs",
        "Положи .zip в папку ресурспаков — он появится здесь",
        "Put a .zip into the resource packs folder — it will show up here",
        "Ранний доступ: новые функции приходят к тебе первыми.",
        "Early access: new features reach you first.",
        "Базовая версия клиента.",
        "The basic version of the client.",
        "Память, эффекты, файлы игры и лаунчер",
        "Memory, effects, game files and the launcher",
        "Память",
        "Memory",
        "Сколько памяти отдать игре. Оставь «авто», если игре хватает.",
        "How much memory the game gets. Leave it on auto if the game runs fine.",
        "Авто",
        "Auto",
        "Эффекты и анимации",
        "Effects and animations",
        "Частицы, свечение и движение фона за курсором",
        "Particles, glow and a background that follows the cursor",
        "Файлы",
        "Files",
        "Папка модов",
        "Mods folder",
        "Расположение игры. Выбери пустую папку на другом диске — файлы переедут туда.",
        "Where the game lives. Pick an empty folder on another drive — the files move there.",
        "Сменить расположение",
        "Change location",
        "По умолчанию",
        "Default",
        "Новая папка для игры",
        "A new folder for the game",
        "Переносим файлы…",
        "Moving files…",
        "Игра теперь в этой папке",
        "The game now lives in this folder",
        "Используется папка по умолчанию",
        "The default folder is in use",
        "Папка должна быть пустой",
        "The folder must be empty",
        "Лаунчер",
        "Launcher",
        "Проверь, нет ли новой версии лаунчера.",
        "See whether there is a newer launcher.",
        "Проверить обновления",
        "Check for updates",
        "Проверяем…",
        "Checking…",
        "Установлена последняя версия",
        "You have the latest version",
        "Сервер обновлений недоступен",
        "The update server is unavailable",
        "Java не найдена и не скачалась",
        "Java was not found and could not be downloaded",
    };
    inline const char* PARTS[] = {
        " до v", " to v",
        "Игра закрылась с ошибкой (код ",
        "The game exited with an error (code ",
        "). Лог: ",
        "). Log: ",
        " · повреждено: ",
        " · damaged: ",
        "Версия игры: ",
        "Game version: ",
        "В игру заходишь как ",
        "You enter the game as ",
        "Клиент v",
        "Client v",
        "Клиент Arcanic v",
        "Arcanic client v",
        "Клиент обновлён до v",
        "Client updated to v",
        " · рекомендуем",
        " · recommended",
        "Визуальный клиент для Minecraft ",
        "A visual client for Minecraft ",
        " на Fabric.",
        " on Fabric.",
        "Загрузка не удалась: ",
        "Download failed: ",
        "Не удалось перенести: ",
        "Could not move the game: ",
        "Игроков в игре: ",
        "Players in the game: ",
        "Код выхода ",
        "Exit code ",
        ". Подробности — в логе игры.",
        ". The details are in the game's log.",
        "Не удалось: ",
        "Failed: ",
        "Сервер ответил ",
        "The server replied ",
        "Ошибка: ",
        "Error: ",
        "Доступна версия ",
        "Version available: ",
        "Для Fabric ",
        "Nothing for Fabric ",
        " ничего нет",
        "",
        " · авто",
        " · auto",
        " получено",
        " received",
        " из ",
        " of ",
        "Автор: ",
        "By ",
        " млн",
        "M",
        " тыс.",
        "K",
        " МБ/с",
        " MB/s",
        " МБ",
        " MB",
        " КБ",
        " KB",
        " ГБ",
        " GB",
        "папка",
        "folder",
    };
    inline string t(const string& ru) {
        if (!en || ru.empty()) return ru;
        static std::unordered_map<string, string> table, built;
        static std::mutex m;
        std::lock_guard<std::mutex> lock(m);
        if (table.empty())
            for (size_t i = 0; i + 1 < std::size(WHOLE); i += 2) table[WHOLE[i]] = WHOLE[i + 1];
        auto it = table.find(ru);
        if (it != table.end()) return it->second;
        bool cyr = false;
        for (size_t i = 0; i + 1 < ru.size(); i++)
            if ((unsigned char)ru[i] == 0xD0 || (unsigned char)ru[i] == 0xD1) {
                cyr = true;
                break;
            }
        if (!cyr) return ru;
        auto b = built.find(ru);
        if (b != built.end()) return b->second;
        string e = ru;
        for (size_t i = 0; i + 1 < std::size(PARTS); i += 2) e = replaceAll(e, PARTS[i], PARTS[i + 1]);
        if (built.size() > 600) built.clear();
        return built[ru] = e;
    }
}  // namespace Lang

// ================================================================================================ network
namespace Net {
    struct Response {
        int status = 0;
        string body;
        int64_t length = -1;
        string error, etag;
    };
    using Sink = std::function<bool(const char*, size_t)>;  // return false to stop

    inline HINTERNET openSession() {
        wstring agent = L"ArcanicLauncher/" + W(Config::VERSION) + L" (by " + W(Config::authors()) + L")";
        HINTERNET ses = WinHttpOpen(agent.c_str(), 4 /* WINHTTP_ACCESS_TYPE_AUTOMATIC_PROXY */, WINHTTP_NO_PROXY_NAME,
                                    WINHTTP_NO_PROXY_BYPASS, 0);
        if (!ses)
            ses = WinHttpOpen(agent.c_str(), WINHTTP_ACCESS_TYPE_DEFAULT_PROXY, WINHTTP_NO_PROXY_NAME, WINHTTP_NO_PROXY_BYPASS, 0);
        if (!ses) {
            Log::error(fmt("net: WinHttpOpen failed (%lu)", GetLastError()));
            return nullptr;
        }
        DWORD tls = 0x00000800 | 0x00002000;
        if (!WinHttpSetOption(ses, WINHTTP_OPTION_SECURE_PROTOCOLS, &tls, sizeof tls)) {
            tls = 0x00000800 | 0x00000200;
            WinHttpSetOption(ses, WINHTTP_OPTION_SECURE_PROTOCOLS, &tls, sizeof tls);
        }
        DWORD conns = 16;
        WinHttpSetOption(ses, WINHTTP_OPTION_MAX_CONNS_PER_SERVER, &conns, sizeof conns);
        return ses;
    }
    // The session every ordinary request shares (made once, thread-safe, lives as long as the launcher)
    inline HINTERNET sharedSession() {
        static HINTERNET ses = openSession();
        return ses;
    }

    // One request
    inline Response request(const string& method, const string& url, const vector<string>& headers = {},
                            const string& body = "", Sink sink = nullptr, int timeoutSec = 40, bool redirects = true) {
        Response r;
        wstring wurl = W(url);
        URL_COMPONENTS uc = {sizeof uc};
        wchar_t host[256], path[4096];
        uc.lpszHostName = host;
        uc.dwHostNameLength = 256;
        uc.lpszUrlPath = path;
        uc.dwUrlPathLength = 4096;
        if (!WinHttpCrackUrl(wurl.c_str(), 0, 0, &uc)) {
            r.error = "bad url";
            return r;
        }
        bool own = !redirects;
        HINTERNET ses = own ? openSession() : sharedSession();
        if (!ses) {
            r.error = "no session";
            return r;
        }
        HINTERNET con = WinHttpConnect(ses, host, uc.nPort, 0), req = nullptr;
        if (con)
            req = WinHttpOpenRequest(con, W(method).c_str(), path, nullptr, WINHTTP_NO_REFERER,
                                     WINHTTP_DEFAULT_ACCEPT_TYPES,
                                     uc.nScheme == INTERNET_SCHEME_HTTPS ? WINHTTP_FLAG_SECURE : 0);
        if (req) {
            WinHttpSetTimeouts(req, 15000, 15000, timeoutSec * 1000, timeoutSec * 1000);
            if (!redirects) {
                DWORD policy = WINHTTP_OPTION_REDIRECT_POLICY_NEVER;
                WinHttpSetOption(req, WINHTTP_OPTION_REDIRECT_POLICY, &policy, sizeof policy);
            }
            wstring hs;
            for (auto& h : headers) hs += W(h) + L"\r\n";
            if (WinHttpSendRequest(req, hs.empty() ? WINHTTP_NO_ADDITIONAL_HEADERS : hs.c_str(), (DWORD)-1,
                                   body.empty() ? WINHTTP_NO_REQUEST_DATA : (LPVOID)body.data(), (DWORD)body.size(),
                                   (DWORD)body.size(), 0) &&
                WinHttpReceiveResponse(req, nullptr)) {
                DWORD code = 0, sz = sizeof code;
                WinHttpQueryHeaders(req, WINHTTP_QUERY_STATUS_CODE | WINHTTP_QUERY_FLAG_NUMBER,
                                    WINHTTP_HEADER_NAME_BY_INDEX, &code, &sz, WINHTTP_NO_HEADER_INDEX);
                r.status = (int)code;
                wchar_t len[32];
                sz = sizeof len;
                if (WinHttpQueryHeaders(req, WINHTTP_QUERY_CONTENT_LENGTH, WINHTTP_HEADER_NAME_BY_INDEX, len, &sz,
                                        WINHTTP_NO_HEADER_INDEX))
                    r.length = _wtoi64(len);
                wchar_t tag[512];
                sz = sizeof tag;
                if (WinHttpQueryHeaders(req, WINHTTP_QUERY_ETAG, WINHTTP_HEADER_NAME_BY_INDEX, tag, &sz,
                                        WINHTTP_NO_HEADER_INDEX))
                    r.etag = U(wstring(tag, sz / sizeof(wchar_t)));
                vector<char> buf(1 << 16);
                for (;;) {
                    DWORD got = 0;
                    if (!WinHttpReadData(req, buf.data(), (DWORD)buf.size(), &got)) {
                        r.error = "read failed";
                        break;
                    }
                    if (!got) break;
                    if (sink && r.status == 200) {
                        if (!sink(buf.data(), got)) {
                            r.error = "stopped";
                            break;
                        }
                    } else
                        r.body.append(buf.data(), got);
                }
            } else
                r.error = fmt("no connection (%lu)", GetLastError());
        } else
            r.error = fmt("no connection (%lu)", GetLastError());
        if (!r.status || (r.status >= 400 && r.status != 404 && r.status != 401))
            Log::warn("net: " + method + " " + Log::safeUrl(url) + " -> " + (r.status ? std::to_string(r.status) : r.error));
        if (req) WinHttpCloseHandle(req);
        if (con) WinHttpCloseHandle(con);
        if (own) WinHttpCloseHandle(ses);
        return r;
    }
    inline string enc(const string& s);
    inline std::atomic<bool> preferMirror{false};
    // Our server as a caching proxy for a public game file; empty for hosts we do not mirror
    inline string mirrorFor(const string& url) {
        static const char* hosts[] = {"://piston-meta.mojang.com/",
                                      "://piston-data.mojang.com/",
                                      "://launchermeta.mojang.com/",
                                      "://launcher.mojang.com/",
                                      "://resources.download.minecraft.net/",
                                      "://libraries.minecraft.net/",
                                      "://meta.fabricmc.net/",
                                      "://maven.fabricmc.net/",
                                      "://api.adoptium.net/",
                                      "://github.com/",
                                      "://objects.githubusercontent.com/",
                                      "://api.modrinth.com/",
                                      "://cdn.modrinth.com/",
                                      "://edge.forgecdn.net/",
                                      "://mediafilez.forgecdn.net/",
                                      "://media.forgecdn.net/"};
        for (auto h : hosts)
            if (url.find(h) != string::npos) return Config::base + "/api/v1/mirror?url=" + enc(url);
        return "";
    }
    // The order to try an original CDN url and our mirror in, newest failure first
    inline vector<string> candidates(const string& url) {
        string m = mirrorFor(url);
        if (m.empty()) return {url};
        return preferMirror ? vector<string>{m, url} : vector<string>{url, m};
    }
    // Did the whole answer arrive?
    inline bool whole(const Response& r) {
        if (!r.error.empty()) return false;
        if (r.length > 0 && (int64_t)r.body.size() != r.length) return false;
        size_t a = r.body.find_first_not_of(" \t\r\n"), b = r.body.find_last_not_of(" \t\r\n");
        if (a == string::npos) return false;
        if (r.body[a] == '{') return r.body[b] == '}';
        if (r.body[a] == '[') return r.body[b] == ']';
        return true;
    }
    // GET returning text; tries the original then our mirror; throws a readable message when all fail
    inline string text(const string& url, int timeoutSec = 40) {
        string err;
        auto cand = candidates(url);
        for (size_t c = 0; c < cand.size(); c++) {
            Response r = request("GET", cand[c], {}, "", nullptr, timeoutSec);
            if (r.status == 200 && whole(r)) {
                if (c > 0) preferMirror = true;
                return r.body;
            }
            err = r.status && r.status != 200 ? "HTTP " + std::to_string(r.status) : "Нет связи с сервером";
        }
        throw std::runtime_error(err);
    }
    // One attempt from one url; returns the error, empty on success
    inline string fileOnce(const string& url, const fs::path& dest, const string& sha1,
                           const std::function<void(int64_t)>& onBytes) {
        int64_t counted = 0;
        string err;
        makeDirs(dest.parent_path());
        fs::path part = dest;
        part += L".part";
        HANDLE h = CreateFileW(part.c_str(), GENERIC_WRITE, 0, nullptr, CREATE_ALWAYS, 0, nullptr);
        if (h == INVALID_HANDLE_VALUE)
            err = "cannot write " + S(part);
        else {
            Hasher sha(BCRYPT_SHA1_ALGORITHM);
            Response r = request(
                "GET", url, {}, "",
                [&](const char* d, size_t n) {
                    DWORD put = 0;
                    if (!WriteFile(h, d, (DWORD)n, &put, nullptr)) return false;
                    sha.add(d, n);
                    counted += (int64_t)n;
                    if (onBytes) onBytes((int64_t)n);
                    return true;
                },
                45);
            CloseHandle(h);
            if (r.status != 200)
                err = r.status ? "HTTP " + std::to_string(r.status) : "Нет связи с сервером";
            else if (!r.error.empty())
                err = r.error;
            else if (!sha1.empty() && lower(sha.doneHex()) != lower(sha1))
                err = "checksum mismatch";
            else if (!MoveFileExW(part.c_str(), dest.c_str(), MOVEFILE_REPLACE_EXISTING))
                err = "cannot move " + S(dest);
        }
        if (!err.empty()) {
            removeFile(part);
            if (onBytes) onBytes(-counted);
        }
        return err;
    }
    // Downloads to dest, checking the SHA-1 when known
    inline void file(const string& url, const fs::path& dest, const string& sha1,
                     const std::function<void(int64_t)>& onBytes) {
        auto cand = candidates(url);
        string err;
        for (size_t c = 0; c < cand.size(); c++) {
            for (int attempt = 1; attempt <= 2; attempt++) {
                err = fileOnce(cand[c], dest, sha1, onBytes);
                if (err.empty()) {
                    if (c > 0) preferMirror = true;
                    return;
                }  // the mirror saved us: everyone else tries it first now
                Sleep(500 * attempt);
            }
        }
        throw std::runtime_error(err);
    }
    inline string enc(const string& s) {
        string out;
        for (unsigned char c : s) {
            if (isalnum(c) || c == '-' || c == '_' || c == '.' || c == '~')
                out += (char)c;
            else
                out += fmt("%%%02X", c);
        }
        return out;
    }
}  // namespace Net

#include "microsoft.h"

// ================================================================================================ the Arcanic server
struct Session {
    string token, version, user, plan, role;
};

namespace Api {
    // The server said no (a wrong password, a token it does not know) - as opposed to "it could not be reached"
    struct Refused : std::runtime_error {
        using std::runtime_error::runtime_error;
    };

    inline Session answer(const Net::Response& r, const string& token) {
        Json o = Json::parse(r.body);
        Session s;
        s.token = token.empty() ? o.str("token") : token;
        s.version = o.str("version");
        s.user = trim(o.str("name"));
        s.plan = o.str("plan");
        s.role = o.str("role");
        if (s.token.empty() || s.user.empty()) throw std::runtime_error("Сервер не выдал сессию");
        return s;
    }
    // Is the saved token still good?
    inline const string BACKUP = "https://ru.arcanic.su";

    // Over to the backup entry, if we are on the main address and the backup answers
    inline bool useBackup() {
        if (!starts(Config::base, "https://arcanic.su")) return false;
        if (Net::request("GET", BACKUP + "/api/v1/launcher", {}, "", nullptr, 6).status != 200) return false;
        Config::base = BACKUP;
        Net::preferMirror = true;
        return true;
    }
    // The main address first; ru.arcanic.su only when it does not answer
    inline bool pickEntry() {
        if (starts(Config::base, BACKUP)) Net::preferMirror = true;
        if (!starts(Config::base, "https://arcanic.su")) return true;
        if (Net::request("GET", Config::base + "/api/v1/launcher", {}, "", nullptr, 6).status == 200) return true;
        return useBackup();
    }
    inline Session me(const string& token);
}  // namespace Api

// ================================================================================================ updating itself
namespace Updater {
    // Is version a newer than b?
    inline bool newer(const string& a, const string& b) {
        auto parts = [](const string& v) {
            vector<int> out;
            int n = 0;
            bool any = false;
            for (char c : v) {
                if (isdigit((unsigned char)c)) n = n * 10 + (c - '0'), any = true;
                else if (c == '.') out.push_back(n), n = 0, any = false;
                else break;
            }
            if (any || !out.empty()) out.push_back(n);
            return out;
        };
        vector<int> x = parts(a), y = parts(b);
        for (size_t i = 0; i < std::max(x.size(), y.size()); i++) {
            int p = i < x.size() ? x[i] : 0, q = i < y.size() ? y[i] : 0;
            if (p != q) return p > q;
        }
        return false;
    }
    inline fs::path self() {
        wchar_t p[4096];
        DWORD n = GetModuleFileNameW(nullptr, p, 4096);
        return fs::path(wstring(p, n));
    }
    inline fs::path beside(const wchar_t* tail) {
        fs::path p = self();
        p += tail;
        return p;
    }
    // What is left of the version before the last update; it could not go while it was still running
    inline void tidy() {
        std::error_code ec;
        fs::remove(beside(L".old"), ec);
        fs::remove(beside(L".new"), ec);
    }
    // When the server has a newer launcher: downloads it beside this one, checks it and puts it in this one's place
    inline bool fetch(const std::function<void()>& found, const std::function<void(int64_t, int64_t)>& progress) {
        Net::Response r = Net::request("GET", Config::base + "/api/v1/launcher", {}, "", nullptr, 8);
        if (r.status != 200) return false;
        string latest, want;
        int64_t size = 0;
        try {
            latest = Json::parse(r.body).str("version");
        } catch (...) {
            return false;
        }
        if (latest.empty() || !newer(latest, Config::VERSION)) return false;
        Net::Response f = Net::request("GET", Config::base + "/api/v1/launcher/files.json", {}, "", nullptr, 8);
        if (f.status != 200 || !Net::whole(f)) return false;
        try {
            Json j = Json::parse(f.body);
            if (j.str("version") != latest) return false;
            want = lower(j["files"]["exe"].str("sha256"));
            size = (int64_t)j["files"]["exe"].num("size", 0);
        } catch (...) {
            return false;
        }
        if (want.size() != 64 || size <= 0) return false;
        if (found) found();
        fs::path me = self(), next = beside(L".new"), old = beside(L".old");
        HANDLE h = CreateFileW(next.c_str(), GENERIC_WRITE, 0, nullptr, CREATE_ALWAYS, 0, nullptr);
        if (h == INVALID_HANDLE_VALUE) return false;
        Hasher sha(BCRYPT_SHA256_ALGORITHM);
        int64_t got = 0;
        Net::Response d = Net::request(
            "GET", Config::base + "/api/v1/launcher/Arcanic.exe", {}, "",
            [&](const char* p, size_t n) {
                DWORD put = 0;
                if (!WriteFile(h, p, (DWORD)n, &put, nullptr)) return false;
                sha.add(p, n);
                got += (int64_t)n;
                if (progress) progress(got, size);
                return true;
            },
            45);
        CloseHandle(h);
        if (d.status != 200 || !d.error.empty() || got != size || lower(sha.doneHex()) != want) {
            removeFile(next);
            return false;
        }
        std::error_code ec;
        fs::remove(old, ec);
        if (!MoveFileExW(me.c_str(), old.c_str(), MOVEFILE_REPLACE_EXISTING)) {
            removeFile(next);
            return false;
        }
        if (!MoveFileExW(next.c_str(), me.c_str(), MOVEFILE_REPLACE_EXISTING)) {
            MoveFileExW(old.c_str(), me.c_str(), MOVEFILE_REPLACE_EXISTING);
            removeFile(next);
            return false;
        }
        return true;
    }
}  // namespace Updater

namespace Api {
    inline Session me(const string& token) {
        Net::Response r =
            Net::request("GET", Config::base + "/api/v1/me", {"Authorization: Bearer " + token}, "", nullptr, 12);
        if (r.status == 200) return answer(r, token);
        if (r.status == 401 || r.status == 403) throw Refused("Войди в аккаунт заново");
        throw std::runtime_error(r.status ? "Сервер ответил " + std::to_string(r.status) : "Нет связи с сервером");
    }
    // An admin gives an account Premium or takes it back. days 0 is for ever; extend adds the days to a Premium still on
    inline string setPlan(const string& token, const string& login, bool premium, int days = 0, bool extend = false) {
        Json body = Json::object();
        body.set("login", Json::of(login));
        body.set("plan", Json::of(string(premium ? "premium" : "free")));
        if (premium) {
            body.set("days", Json::of((double)days));
            body.set("extend", Json::of(extend));
        }
        Net::Response r = Net::request("POST", Config::base + "/api/v1/admin/plan",
                                       {"Content-Type: application/json", "Authorization: Bearer " + token},
                                       body.dump(), nullptr, 12);
        if (!r.status) throw std::runtime_error("Нет связи с сервером");
        if (r.status == 200) return trim(Json::parse(r.body).str("name"));
        if (r.status == 404) throw Refused("Такого аккаунта нет");
        if (r.status == 401 || r.status == 403) throw Refused("Нет прав");
        if (r.status == 400) throw Refused("Версию админа менять нельзя");
        throw std::runtime_error("Сервер ответил " + std::to_string(r.status));
    }
    // For the admins: the accounts with Premium now, and when each one's Premium ends (0 for ever)
    struct PremiumAccount {
        string login, name, by;
        int64_t until = 0;
    };
    inline vector<PremiumAccount> premiumAccounts(const string& token) {
        Net::Response r = Net::request("GET", Config::base + "/api/v1/admin/premium", {"Authorization: Bearer " + token}, "", nullptr, 12);
        if (!r.status) throw std::runtime_error("Нет связи с сервером");
        if (r.status == 401 || r.status == 403) throw Refused("Нет прав");
        if (r.status != 200) throw std::runtime_error("Сервер ответил " + std::to_string(r.status));
        vector<PremiumAccount> out;
        Json j = Json::parse(r.body);
        for (auto& x : j["list"].a) out.push_back({x.str("login"), x.str("name"), x.str("by"), x.num("until", 0)});
        if (j["list"].isNull())  // a server from before terms: the names only
            for (auto& n : j["accounts"].a)
                if (n.type == Json::Str) out.push_back({n.s, n.s, "", 0});
        return out;
    }
    inline void logout(const string& token) {
        if (!token.empty())
            Net::request("POST", Config::base + "/api/v1/logout", {"Authorization: Bearer " + token}, "", nullptr, 8);
    }

    // The version of the client the server hands out now (asked for a player who is not signed in)
    inline string clientVersion() {
        Net::Response r = Net::request("GET", Config::base + "/api/v1/launcher", {}, "", nullptr, 8);
        try {
            return r.status == 200 ? Json::parse(r.body).str("client") : string();
        } catch (...) {
            return string();
        }
    }
    // Signs in through the site
    inline Session siteLogin(std::atomic<bool>& cancel, const std::function<void(const string&)>& progress) {
        Microsoft::Winsock ws;
        Microsoft::Socket listener;
        listener.s = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
        sockaddr_in address{};
        address.sin_family = AF_INET;
        address.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
        address.sin_port = 0;
        BOOL exclusive = TRUE;
        setsockopt(listener.s, SOL_SOCKET, SO_EXCLUSIVEADDRUSE, (char*)&exclusive, sizeof exclusive);
        if (listener.s == INVALID_SOCKET || bind(listener.s, (sockaddr*)&address, sizeof address) ||
            listen(listener.s, 4))
            throw std::runtime_error("Не удалось начать вход");
        int len = sizeof address;
        getsockname(listener.s, (sockaddr*)&address, &len);
        string state = Microsoft::randomHex();
        string url = Config::base + "/login/?port=" + std::to_string(ntohs(address.sin_port)) + "&state=" + state;
        if (Config::softwareNow)
            writeFile(Config::stateDir() / L"login-url.txt",
                      url);
        else if ((INT_PTR)ShellExecuteW(nullptr, L"open", W(url).c_str(), nullptr, nullptr, SW_SHOWNORMAL) <= 32)
            throw std::runtime_error("Не удалось открыть браузер");
        progress("Заверши вход на сайте. Повторное нажатие отменит вход.");
        ULONGLONG deadline = GetTickCount64() + 10 * 60 * 1000;
        string code;
        while (!cancel && GetTickCount64() < deadline && code.empty()) {
            fd_set read;
            FD_ZERO(&read);
            FD_SET(listener.s, &read);
            timeval timeout{0, 200000};
            if (select(0, &read, nullptr, nullptr, &timeout) <= 0) continue;
            Microsoft::Socket client;
            client.s = accept(listener.s, nullptr, nullptr);
            if (client.s == INVALID_SOCKET) continue;
            DWORD ms = 1500;
            setsockopt(client.s, SOL_SOCKET, SO_RCVTIMEO, (char*)&ms, sizeof ms);
            setsockopt(client.s, SOL_SOCKET, SO_SNDTIMEO, (char*)&ms, sizeof ms);
            string request;
            char buf[2048];
            ULONGLONG readUntil = GetTickCount64() + 3000;
            while (!cancel && GetTickCount64() < readUntil && request.size() < 16384 &&
                   request.find("\r\n\r\n") == string::npos) {
                int n = recv(client.s, buf, sizeof buf, 0);
                if (n <= 0) break;
                request.append(buf, n);
            }
            size_t end = starts(request, "GET ") ? request.find(' ', 4) : string::npos;
            string target = end == string::npos ? string() : request.substr(4, end - 4), response;
            if (!starts(target, "/?"))
                response =
                    "HTTP/1.1 404 Not Found\r\nConnection: close\r\nContent-Length: 0\r\n\r\n";
            else {
                try {
                    code = Microsoft::callbackCode(target, state);
                } catch (const std::exception&) {
                    code.clear();
                }
                response = "HTTP/1.1 302 Found\r\nLocation: " + Config::base + "/login/?" +
                           (code.empty() ? "failed=1" : "done=1") +
                           "\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nConnection: "
                           "close\r\nContent-Length: 0\r\n\r\n";
            }
            send(client.s, response.data(), (int)response.size(), 0);
        }
        if (cancel) throw std::runtime_error("Вход отменён");
        if (code.empty()) throw std::runtime_error("Время входа истекло. Попробуй ещё раз");
        Json body = Json::object();
        body.set("code", Json::of(code));
        Net::Response r = Net::request("POST", Config::base + "/api/v1/exchange", {"Content-Type: application/json"},
                                       body.dump(), nullptr, 15);
        if (!r.status) throw std::runtime_error("Нет связи с сервером");
        if (r.status != 200) throw std::runtime_error("Сайт вернул неверный ответ");
        return answer(r, "");
    }

    inline fs::path clientCache() {
        return Config::stateDir() / L"cache" / L"client.jar";
    }
    // The client kept from the last download, or empty
    inline string cachedClient() {
        string jar;
        return readFile(clientCache(), jar) && starts(jar, "PK") ? jar : string();
    }
    inline string fetchClientCached(const std::function<void(int64_t, int64_t)>& progress, bool* changed = nullptr) {
        fs::path dir = Config::stateDir() / L"cache", jarFile = clientCache(), tagFile = dir / L"client.etag";
        removeFile(dir / L"client.blob");
        string cached = cachedClient(), tag;
        bool have = !cached.empty() && readFile(tagFile, tag) && !trim(tag).empty();
        vector<string> headers;
        if (have) headers.push_back("If-None-Match: " + trim(tag));
        string jar;
        int64_t total = -1;
        auto get = [&] {
            jar.clear();
            return Net::request(
                "GET", Config::base + "/api/v1/client", headers, "",
                [&](const char* d, size_t n) {
                    jar.append(d, n);
                    if (progress) progress((int64_t)jar.size(), total);
                    return true;
                },
                25);
        };
        Net::Response r = get();
        if ((!r.status || !r.error.empty()) && useBackup()) r = get();
        if (have && r.status == 304) {
            if (changed) *changed = false;
            if (progress) progress((int64_t)cached.size(), (int64_t)cached.size());
            return cached;
        }
        if (r.status != 200)
            throw std::runtime_error(r.status ? "Загрузка не удалась: " + std::to_string(r.status)
                                              : "Нет связи с сервером");
        string sum;
        for (char c : r.etag)
            if (isxdigit((unsigned char)c)) sum += (char)tolower((unsigned char)c);
        if (!starts(jar, "PK") || (sum.size() == 64 && sha256Hex(jar) != sum))
            throw std::runtime_error("Клиент скачался с ошибкой");
        if (progress) progress((int64_t)jar.size(), (int64_t)jar.size());
        if (changed) *changed = !have || jar != cached;
        makeDirs(dir);
        if (!r.etag.empty() && writeFile(jarFile, jar))
            writeFile(tagFile, r.etag);
        else {
            removeFile(tagFile);
        }
        return jar;
    }
    // Mods the server hands out for the game; empty if it has none or cannot be reached
    inline Json serverMods() {
        Net::Response r = Net::request("GET", Config::base + "/api/v1/mods", {}, "", nullptr, 10);
        Json j = r.status == 200 ? Json::parse(r.body) : Json();
        return j.type == Json::Arr ? j : Json::array();
    }
}  // namespace Api

// ================================================================================================ the game's folder
namespace Game {
    inline fs::path dir() {
        if (hasEnv(L"ARCANIC_MC_DIR")) return P(envVar(L"ARCANIC_MC_DIR"));
        if (!Config::gameDir.empty()) return P(Config::gameDir);
        return Config::stateDir() / L"game";
    }
    inline fs::path modsDir() {
        fs::path m = dir() / L"mods";
        makeDirs(m);
        return m;
    }
    // ---- each game version keeps its own mods
    inline fs::path modStore(const string& version) {
        string v;
        for (char ch : version) v += (isalnum((unsigned char)ch) || ch == '.' || ch == '-' || ch == '_') ? ch : '_';
        return dir() / L"mods-versions" / P(v.empty() ? "unknown" : v);
    }
    inline string modsOwner() {
        string v;
        return readFile(dir() / L"mods" / L".arcanic-version", v) ? trim(v) : "";
    }
    // Puts the mods of `to` into mods/ (and the ones there away for their version)
    inline bool switchMods(const string& to) {
        fs::path mods = modsDir(), mark = mods / L".arcanic-version";
        string from = modsOwner();
        if (from.empty()) {
            writeFile(mark, to);
            return true;
        }
        if (from == to) return true;
        fs::path out = modStore(from), in = modStore(to);
        makeDirs(out);
        auto movable = [&](const fs::path& p) {
            string n = S(p.filename());
            return n != ".arcanic-version" && lower(n) != lower(Config::MOD_FILE);
        };
        vector<std::pair<fs::path, fs::path>> done;  // what moved, to undo it if something does not
        auto undo = [&] {
            for (auto it = done.rbegin(); it != done.rend(); ++it) MoveFileExW(it->second.c_str(), it->first.c_str(), MOVEFILE_REPLACE_EXISTING);
        };
        std::error_code ec;
        vector<fs::path> leaving, coming;
        for (auto& e : fs::directory_iterator(mods, ec)) if (movable(e.path())) leaving.push_back(e.path());
        if (isDir(in)) for (auto& e : fs::directory_iterator(in, ec)) coming.push_back(e.path());
        for (auto& p : leaving) {
            fs::path dest = out / p.filename();
            if (!MoveFileExW(p.c_str(), dest.c_str(), MOVEFILE_REPLACE_EXISTING)) {
                Log::warn("mods: " + S(p.filename()) + " would not move (" + std::to_string(GetLastError()) + ") - the version stays " + from);
                undo();
                return false;
            }
            done.push_back({p, dest});
        }
        for (auto& p : coming) {
            fs::path dest = mods / p.filename();
            if (!MoveFileExW(p.c_str(), dest.c_str(), MOVEFILE_REPLACE_EXISTING)) {
                Log::warn("mods: " + S(p.filename()) + " would not come back (" + std::to_string(GetLastError()) + ")");
                undo();
                return false;
            }
            done.push_back({p, dest});
        }
        writeFile(mark, to);
        Log::info("mods: " + std::to_string(leaving.size()) + " put away for " + from + ", " + std::to_string(coming.size()) + " back for " + to);
        return true;
    }
    inline fs::path officialDir() {
        fs::path p = P(envVar(L"APPDATA")) / L".minecraft";
        return isDir(p) ? p : fs::path();
    }

    struct Mod {
        string name;
        fs::path file;
        int64_t size;
        bool on, required;
    };
    struct Pack {
        string name;
        fs::path file;
        int64_t size;
        bool on;
    };

    inline string pretty(const string& fileName) {
        string n = fileName;
        for (const char* ext : {".jar.disabled", ".jar", ".zip"})
            if (ends(n, ext)) {
                n = n.substr(0, n.size() - strlen(ext));
                break;
            }
        // drop the version tail: from the first "-digit" / "_digit" / "-fabric-digit" / "-mc..." on
        for (size_t i = 0; i + 1 < n.size(); i++) {
            if (n[i] != '-' && n[i] != '_') continue;
            size_t j = i + 1;
            if (!n.compare(j, 6, "fabric"))
                j += 6;
            else if (!n.compare(j, 2, "mc"))
                j += 2;
            if (j < n.size() && (n[j] == '-' || n[j] == '_')) j++;
            if (j < n.size() && isdigit((unsigned char)n[j])) {
                n = n.substr(0, i);
                break;
            }
        }
        if (n.empty()) return fileName;
        for (auto& c : n)
            if (c == '-' || c == '_') c = ' ';
        n[0] = (char)toupper((unsigned char)n[0]);
        return n;
    }
    inline bool isRequired(const string& fileName) {
        string n = lower(fileName);
        return starts(n, "fabric-api") || starts(n, "arcanic-client");
    }

    inline vector<Mod> mods() {
        vector<Mod> out;
        std::error_code ec;
        fs::path d = dir() / L"mods";
        if (!isDir(d)) return out;
        for (auto& e : fs::directory_iterator(d, ec)) {
            string n = S(e.path().filename());
            if (!ends(n, ".jar") && !ends(n, ".jar.disabled")) continue;
            out.push_back({starts(n, "arcanic-client") ? "Arcanic Visuals" : pretty(n), e.path(), fileSize(e.path()),
                           ends(n, ".jar"), isRequired(n)});
        }
        std::sort(out.begin(), out.end(), [](const Mod& a, const Mod& b) { return a.file < b.file; });
        return out;
    }
    // Switches a mod by renaming it to / from .disabled; returns the file's new path
    inline fs::path setMod(const Mod& m, bool on) {
        string n = S(m.file.filename()), to = on ? (ends(n, ".disabled") ? n.substr(0, n.size() - 9) : n)
                                                 : (ends(n, ".disabled") ? n : n + ".disabled");
        fs::path dest = m.file.parent_path() / P(to);
        if (to != n) MoveFileExW(m.file.c_str(), dest.c_str(), MOVEFILE_REPLACE_EXISTING);
        return dest;
    }

    inline fs::path optionsFile() {
        return dir() / L"options.txt";
    }
    inline vector<string> enabledPacks() {
        vector<string> out;
        string text;
        if (!readFile(optionsFile(), text)) return out;
        for (auto& line : split(text, "\r\n"))
            if (starts(line, "resourcePacks:")) {
                Json j = Json::parse(line.substr(14));
                for (auto& x : j.a)
                    if (x.type == Json::Str) out.push_back(x.s);
            }
        return out;
    }
    inline vector<Pack> packs() {
        vector<Pack> out;
        std::error_code ec;
        fs::path d = dir() / L"resourcepacks";
        if (!isDir(d)) return out;
        vector<string> on = enabledPacks();
        for (auto& e : fs::directory_iterator(d, ec)) {
            string n = S(e.path().filename());
            bool folder = isDir(e.path());
            if (!folder && !ends(n, ".zip")) continue;
            out.push_back({pretty(n), e.path(), folder ? 0 : fileSize(e.path()),
                           std::find(on.begin(), on.end(), "file/" + n) != on.end()});
        }
        std::sort(out.begin(), out.end(), [](const Pack& a, const Pack& b) { return a.file < b.file; });
        return out;
    }
    inline void setPack(const Pack& p, bool on) {
        vector<string> list = enabledPacks();
        if (list.empty()) list.push_back("vanilla");
        string id = "file/" + S(p.file.filename());
        list.erase(std::remove(list.begin(), list.end(), id), list.end());
        if (on) list.push_back(id);
        Json arr = Json::array();
        for (auto& s : list) arr.a.push_back(Json::of(s));
        string line = "resourcePacks:" + arr.dump(), text, out;
        bool done = false;
        readFile(optionsFile(), text);
        for (auto& l : split(text, "\r\n")) {
            if (starts(l, "resourcePacks:")) {
                out += line;
                done = true;
            } else
                out += l;
            out += "\n";
        }
        if (!done) out += line + "\n";
        writeFile(optionsFile(), out);
    }
    // The picture a mod or a pack ships with (the icon named in fabric.mod.json, or pack.png), as raw image bytes
    inline string icon(const fs::path& file) {
        string out;
        if (isDir(file)) {
            readFile(file / L"pack.png", out);
            return out;
        }
        Zip z;
        if (!z.open(file)) return out;
        string entry = "pack.png", meta;
        if (z.read("fabric.mod.json", meta)) {
            Json m = Json::parse(meta);
            const Json& ic = m["icon"];
            if (ic.type == Json::Str)
                entry = ic.s;
            else if (ic.type == Json::Obj && !ic.o.empty()) {
                int best = -1;
                for (auto& kv : ic.o)
                    if (kv.second.type == Json::Str && atoi(kv.first.c_str()) > best) {
                        best = atoi(kv.first.c_str());
                        entry = kv.second.s;
                    }
            }  // several sizes: the largest
            else
                return out;
        }
        z.read(entry, out);
        return out;
    }
    inline string sizeText(int64_t b) {
        if (b <= 0) return "папка";
        if (b < (1 << 20)) return std::to_string(std::max<int64_t>(1, b / 1024)) + " КБ";
        return fmt("%.1f МБ", b / 1048576.0);
    }
    // Moves the game to another (empty or new) folder and makes it the game folder
    inline string moveTo(const fs::path& target) {
        std::error_code ec;
        fs::path from = dir();
        if (fs::weakly_canonical(from, ec) == fs::weakly_canonical(target, ec)) return "";
        makeDirs(target);
        if (!fs::is_empty(target, ec)) return "Папка должна быть пустой";
        if (isDir(from))
            for (auto& e : fs::directory_iterator(from, ec)) {
                fs::path to = target / e.path().filename();
                std::error_code e2;
                fs::rename(e.path(), to, e2);
                if (e2) {
                    fs::copy(e.path(), to, fs::copy_options::recursive | fs::copy_options::overwrite_existing, e2);
                    if (e2) return "Не удалось перенести: " + e2.message();
                    fs::remove_all(e.path(), e2);
                }
            }
        Config::gameDir = S(fs::absolute(target, ec));
        Config::save();
        return "";
    }
}  // namespace Game

// ================================================================================================ the Modrinth catalogue
namespace Modrinth {
    inline const char* API = "https://api.modrinth.com/v2";
    inline const char* RECOMMENDED[] = {
        "sodium",    "lithium",      "ferrite-core",          "entityculling", "immediatelyfast",   "dynamic-fps",
        "iris",      "sodium-extra", "reeses-sodium-options", "modmenu",       "lambdynamiclights", "zoomify",
        "appleskin", "fabric-api"};
    // One project of the catalogue
    struct Hit {
        string id, slug, title, description, iconUrl, author;
        int64_t downloads = 0;
        string kind = "mod";
        vector<string> gallery;
        string source = "modrinth", webpage;
        bool direct = true;  // false: the author lets the file be downloaded only on the site itself
    };
    struct NoVersion : std::runtime_error {
        NoVersion() : std::runtime_error("no build") {}
    };
    struct Category {
        const char* id;
        const char* name;
    };
    inline const Category MOD_CATEGORIES[] = {{"", "Все"},
                                              {"optimization", "Оптимизация"},
                                              {"utility", "Утилиты"},
                                              {"decoration", "Декор"},
                                              {"adventure", "Приключения"},
                                              {"equipment", "Снаряжение"},
                                              {"library", "Библиотеки"}};
    inline const Category PACK_CATEGORIES[] = {{"", "Все"},
                                               {"combat", "PvP"},
                                               {"vanilla-like", "Ванильные"},
                                               {"realistic", "Реализм"},
                                               {"simplistic", "Простые"},
                                               {"themed", "Тематические"},
                                               {"tweaks", "Твики"},
                                               {"utility", "Утилиты"}};

    inline string searchFacets(const string& mc, const string& kind, const string& category = "") {
        if (kind != "mod" && kind != "resourcepack") throw std::invalid_argument("Unsupported project type");
        return "[[\"project_type:" + kind + "\"]," + (kind == "mod" ? "[\"categories:fabric\"]," : "") +
            (category.empty() ? "" : "[\"categories:" + category + "\"],") + "[\"versions:" + mc + "\"]]";
    }
    inline vector<Hit> searchResults(const Json& res, const string& kind) {
        vector<Hit> out;
        for (auto& h : res["hits"].a) {
            if (h.str("project_type") != kind) continue;
            Hit x{h.str("project_id"), h.str("slug"),   h.str("title"),     h.str("description"),
                  h.str("icon_url"),   h.str("author"), h.num("downloads"), kind};
            string first = h.str("featured_gallery");
            if (starts(first, "https://")) x.gallery.push_back(first);
            for (auto& g : h["gallery"].a)
                if (g.type == Json::Str && starts(g.s, "https://") && g.s != first && x.gallery.size() < 8)
                    x.gallery.push_back(g.s);
            out.push_back(x);
        }
        return out;
    }
    // One page of 30: nothing typed - the most downloaded; `offset` pages through, `total` says how many there are
    inline vector<Hit> search(const string& query, const string& mc, const string& kind = "mod", const string& category = "",
                              int offset = 0, int* total = nullptr) {
        string url = string(API) + "/search?limit=30&offset=" + std::to_string(std::max(0, offset)) +
            (query.empty() ? "&index=downloads" : "") + "&query=" + Net::enc(query) +
            "&facets=" + Net::enc(searchFacets(mc,kind,category));
        Json res = Json::parse(Net::text(url, 8));
        if (total) *total = (int)res.num("total_hits", 0);
        return searchResults(res, kind);
    }
    inline vector<Hit> recommended(const string& mc) {
        string ids = "[";
        for (auto s : RECOMMENDED) ids += string(ids.size() > 1 ? "," : "") + "\"" + s + "\"";
        Json projects = Json::parse(Net::text(string(API) + "/projects?ids=" + Net::enc(ids + "]"),8));
        vector<Hit> out;
        for (auto slug : RECOMMENDED)
            for (auto& p : projects.a) {
                if (p.str("project_type") != "mod" || p.str("slug") != slug || !p["game_versions"].contains(mc) || !p["loaders"].contains("fabric"))
                    continue;
                Hit x{p.str("id"),       slug, p.str("title"),    p.str("description"),
                      p.str("icon_url"), "",   p.num("downloads")};
                for (auto& g : p["gallery"].a) {
                    string u = g.str("url");
                    if (starts(u, "https://") && x.gallery.size() < 8) {
                        if (g.flag("featured"))
                            x.gallery.insert(x.gallery.begin(), u);
                        else
                            x.gallery.push_back(u);
                    }
                }
                out.push_back(x);
            }
        return out;
    }
    // A mod's name without its version: "sodium-fabric-0.8.14+mc1.21.11.jar" -> "sodium"
    inline string baseName(const string& fileName) {
        string n = lower(fileName);
        for (const char* ext : {".jar.disabled", ".jar"})
            if (ends(n, ext)) {
                n = n.substr(0, n.size() - strlen(ext));
                break;
            }
        for (size_t i = 0; i + 1 < n.size(); i++) {
            if (!strchr("-_+ ", n[i])) continue;
            size_t j = i + 1;
            if (!n.compare(j, 6, "fabric"))
                j += 6;
            else if (!n.compare(j, 2, "mc"))
                j += 2;
            if (j < n.size() && strchr("-_+", n[j])) j++;
            if (j < n.size() && n[j] == 'v') j++;
            if (j < n.size() && isdigit((unsigned char)n[j])) {
                n = n.substr(0, i);
                break;
            }
        }
        for (const char* tail : {"-fabric", "_fabric", " fabric", "-mc", "_mc"})
            if (ends(n, tail)) n = n.substr(0, n.size() - strlen(tail));
        return n;
    }
    inline string plain(const string& s) {
        string o;
        for (char c : lower(s))
            if (isalnum((unsigned char)c)) o += c;
        return o;
    }
    inline bool installed(const string& slug, const vector<string>& fileNames) {
        string want = plain(slug);
        for (auto& f : fileNames) {
            string b = plain(baseName(f));
            if (!b.empty() && (b == want || b == want + "fabric" || want == b + "fabric")) return true;
        }
        return false;
    }
    // Puts one mod file into mods/ in place of its older builds. False when there is nothing to do: a required mod
    // that is already here stays as it is, and the launcher's own copy of a mod is never replaced.
    inline bool placeJar(const fs::path& mods, const string& filename, const string& url, const string& sha1, bool wanted) {
        string base = baseName(filename);
        if (!safeFileName(filename) || !ends(lower(filename), ".jar") || url.empty()) {
            if (wanted) throw NoVersion();
            return false;
        }
        vector<fs::path> same;
        std::error_code ec;
        for (auto& e : fs::directory_iterator(mods, ec)) {
            string n = S(e.path().filename());
            if ((ends(n, ".jar") || ends(n, ".jar.disabled")) && baseName(n) == base) same.push_back(e.path());
        }
        if (!wanted && !same.empty()) return false;
        std::set<string> managed;
        string list;
        if (readFile(mods / L".arcanic-managed", list))
            for (auto& l : split(list, "\r\n")) managed.insert(trim(l));
        bool keep = false;
        for (auto& p : same)
            if (managed.count(S(p.filename()))) keep = true;
        if (!keep) {
            for (auto& p : same)
                if (S(p.filename()) != filename) removeFile(p);
            Net::file(url, mods / P(filename), sha1, nullptr);
        }
        return true;
    }
    inline void installOne(const string& projectId, const string& mc, const fs::path& mods, std::set<string>& seen,
                           bool wanted) {
        if (!seen.insert(projectId).second || seen.size() > 12) return;
        Json versions = Json::parse(Net::text(string(API) + "/project/" + projectId + "/version?game_versions=" +
                                              Net::enc("[\"" + mc + "\"]") + "&loaders=" + Net::enc("[\"fabric\"]")));
        const Json* pick = nullptr;
        for (auto& v : versions.a)
            if (v.str("version_type") == "release") {
                pick = &v;
                break;
            }
        if (!pick && !versions.a.empty()) pick = &versions.a[0];
        if (!pick) {
            if (wanted) throw NoVersion();
            return;
        }
        const Json* file = nullptr;
        for (auto& f : (*pick)["files"].a) {
            if (f.flag("primary")) {
                file = &f;
                break;
            }
            if (!file) file = &f;
        }
        if (!file) {
            if (wanted) throw NoVersion();
            return;
        }
        if (!placeJar(mods, file->str("filename"), file->str("url"), (*file)["hashes"].str("sha1"), wanted)) return;
        for (auto& d : (*pick)["dependencies"].a)
            if (d.str("dependency_type") == "required" && d.has("project_id"))
                installOne(d.str("project_id"), mc, mods, seen, false);
    }
    // Downloads the newest build of the mod into mods/, and after it every mod it requires that is not there yet
    inline void install(const string& projectId, const string& mc, const fs::path& mods) {
        makeDirs(mods);
        std::set<string> seen;
        installOne(projectId, mc, mods, seen, true);
    }

    // A newer build of an installed mod: what to download instead of the file the player has
    struct Update {
        string version, url, filename, sha1;
    };
    inline std::map<string, Update> updates(const vector<string>& sha1s, const string& mc) {
        std::map<string, Update> out;
        if (sha1s.empty()) return out;
        Json body = Json::object(), hashes = Json::array(), loaders = Json::array(), versions = Json::array();
        for (auto& s : sha1s) hashes.a.push_back(Json::of(lower(s)));
        loaders.a.push_back(Json::of("fabric"));
        versions.a.push_back(Json::of(mc));
        body.set("hashes", hashes).set("algorithm", Json::of("sha1")).set("loaders", loaders).set("game_versions", versions);
        Net::Response r = Net::request("POST", string(API) + "/version_files/update", {"Content-Type: application/json"}, body.dump(), nullptr, 15);
        if (r.status != 200) throw std::runtime_error(r.status ? "Modrinth: HTTP " + std::to_string(r.status) : "Modrinth недоступен");
        Json j = Json::parse(r.body);
        auto primary = [](const Json& v) {
            const Json* file = nullptr;
            for (auto& f : v["files"].a) {
                if (f.flag("primary")) return &f;
                if (!file) file = &f;
            }
            return file;
        };
        vector<string> unstable;
        for (auto& s : sha1s) {
            const Json& v = j[lower(s).c_str()];
            if (v.type == Json::Obj && v.str("version_type", "release") != "release") unstable.push_back(lower(s));
        }
        Json current;
        if (!unstable.empty()) {
            Json q = Json::object(), hs = Json::array();
            for (auto& s : unstable) hs.a.push_back(Json::of(s));
            q.set("hashes", hs).set("algorithm", Json::of("sha1"));
            Net::Response c = Net::request("POST", string(API) + "/version_files", {"Content-Type: application/json"}, q.dump(), nullptr, 15);
            if (c.status == 200) current = Json::parse(c.body);
        }
        for (auto& s : sha1s) {
            string key = lower(s);
            const Json* v = &j[key.c_str()];
            if (v->type != Json::Obj) continue;
            Json release;
            if (v->str("version_type", "release") != "release") {
                const Json& now = current[key.c_str()];
                if (now.type != Json::Obj) continue;
                if (now.str("version_type", "release") == "release") {
                    string url = string(API) + "/project/" + now.str("project_id") + "/version?game_versions=" +
                                 Net::enc("[\"" + mc + "\"]") + "&loaders=" + Net::enc("[\"fabric\"]");
                    Net::Response lr = Net::request("GET", url, {}, "", nullptr, 15);
                    if (lr.status != 200) continue;
                    Json list = Json::parse(lr.body);
                    for (auto& cand : list.a)
                        if (cand.str("version_type") == "release") { release = cand; break; }
                    if (release.type != Json::Obj) continue;
                    v = &release;
                }
            }
            const Json* file = primary(*v);
            if (!file) continue;
            string fresh = lower(file->str("filename")), sha = lower((*file)["hashes"].str("sha1"));
            if (sha.empty() || sha == key || !safeFileName(file->str("filename")) || !ends(fresh, ".jar")) continue;
            out[key] = {v->str("version_number"), file->str("url"), file->str("filename"), sha};
        }
        return out;
    }

    // ---- resource packs
    inline std::map<string, string> packFiles(const fs::path& dir) {
        std::map<string, string> out;
        string list;
        if (readFile(dir / L".arcanic-packs", list))
            for (auto& l : split(list, "\r\n")) {
                size_t tab = l.find('\t');
                if (tab != string::npos && isFile(dir / P(l.substr(tab + 1))))
                    out[l.substr(0, tab)] = l.substr(tab + 1);
            }
        return out;
    }
    inline void installPack(const string& projectId, const string& mc, const fs::path& dir) {
        makeDirs(dir);
        Json versions = Json::parse(Net::text(string(API) + "/project/" + projectId +
                                              "/version?game_versions=" + Net::enc("[\"" + mc + "\"]") +
                                              "&loaders=" + Net::enc("[\"minecraft\"]")));
        const Json* pick = nullptr;
        for (auto& v : versions.a)
            if (v.str("version_type") == "release") {
                pick = &v;
                break;
            }
        if (!pick && !versions.a.empty()) pick = &versions.a[0];
        const Json* file = nullptr;
        if (pick)
            for (auto& f : (*pick)["files"].a) {
                if (!ends(lower(f.str("filename")), ".zip")) continue;
                if (f.flag("primary")) {
                    file = &f;
                    break;
                }
                if (!file) file = &f;
            }
        if (!file) throw NoVersion();
        string filename = file->str("filename");
        if (!safeFileName(filename)) throw NoVersion();
        auto known = packFiles(dir);
        Net::file(file->str("url"), dir / P(filename), (*file)["hashes"].str("sha1"), nullptr);
        auto was = known.find(projectId);
        if (was != known.end() && was->second != filename) removeFile(dir / P(was->second));
        known[projectId] = filename;
        string list;
        for (auto& kv : known) list += kv.first + "\t" + kv.second + "\n";
        writeFile(dir / L".arcanic-packs", list);
    }
}  // namespace Modrinth

// ================================================================================================ CurseForge
// Fabric mods from CurseForge, read through our server (/api/v1/curseforge keeps the API key and caches the answers).
// The files come straight from CurseForge's CDN, checked by their SHA-1.
namespace CurseForge {
    struct OnlyOnSite : std::runtime_error {
        OnlyOnSite() : std::runtime_error("only on the site") {}
    };
    inline bool page(const string& url) {
        return starts(url, "https://www.curseforge.com/minecraft/") && url.find_first_of("\r\n\" ") == string::npos;
    }
    inline Json ask(const string& path) {
        auto r = Net::request("GET", Config::base + "/api/v1/curseforge/" + path, {}, "", nullptr, 12);
        if (r.status == 429) throw std::runtime_error(Lang::en ? "CurseForge: too many requests, try again in a minute" : "CurseForge: слишком много запросов, попробуй через минуту");
        if (r.status != 200) throw std::runtime_error(Lang::en ? "CurseForge is unavailable right now" : "CurseForge сейчас недоступен, попробуй позже");
        Json j = Json::parse(r.body);
        if (!j.has("data")) throw std::runtime_error("CurseForge returned an invalid catalog");
        return j;
    }
    // One page of 30, like Modrinth::search; ids are "cf:<number>"
    inline vector<Modrinth::Hit> search(const string& query, const string& mc, const string& category, int offset, int* total) {
        Json j = ask("search?mc=" + Net::enc(mc) + "&index=" + std::to_string(std::max(0, offset)) +
                     (category.empty() ? "" : "&category=" + Net::enc(category)) + (query.empty() ? "" : "&q=" + Net::enc(query)));
        if (total) *total = (int)j.num("total", 0);
        vector<Modrinth::Hit> out;
        for (auto& m : j["data"].a) {
            int64_t id = m.num("id", 0);
            if (id <= 0) continue;
            Modrinth::Hit h;
            h.id = "cf:" + std::to_string(id); h.slug = m.str("slug"); h.title = m.str("name");
            h.description = m.str("summary"); h.author = m.str("author"); h.downloads = m.num("downloads");
            h.iconUrl = starts(m.str("icon"), "https://") ? m.str("icon") : "";
            h.source = "curseforge"; h.webpage = page(m.str("web")) ? m.str("web") : "";
            h.direct = m.flag("direct");
            for (auto& g : m["gallery"].a)
                if (g.type == Json::Str && starts(g.s, "https://") && h.gallery.size() < 8) h.gallery.push_back(g.s);
            out.push_back(h);
        }
        return out;
    }
    inline void installOne(const string& modId, const string& mc, const fs::path& mods, std::set<string>& seen, bool wanted) {
        if (modId.empty() || modId.find_first_not_of("0123456789") != string::npos) return;
        if (!seen.insert(modId).second || seen.size() > 12) return;
        Json files = ask("files?mod=" + modId + "&mc=" + Net::enc(mc));
        const Json* pick = nullptr;
        for (auto& f : files["data"].a)
            if (f.flag("release")) { pick = &f; break; }
        if (!pick && !files["data"].a.empty()) pick = &files["data"].a[0];
        if (!pick) {
            if (wanted) throw Modrinth::NoVersion();
            return;
        }
        if (pick->str("url").empty()) {
            if (wanted) throw OnlyOnSite();
            return;
        }
        if (!Modrinth::placeJar(mods, pick->str("name"), pick->str("url"), pick->str("sha1"), wanted)) return;
        for (auto& d : (*pick)["requires"].a)
            if (d.type == Json::Num) installOne(std::to_string((int64_t)d.n), mc, mods, seen, false);
    }
    // Downloads the newest Fabric build of the mod for this game version, then the mods it needs that are not here yet
    inline void install(const string& id, const string& mc, const fs::path& mods) {
        makeDirs(mods);
        std::set<string> seen;
        installOne(starts(id, "cf:") ? id.substr(3) : id, mc, mods, seen, true);
    }
}  // namespace CurseForge

// ================================================================================================ Discord: "Playing Arcanic Visuals"
// While the game runs, the player's Discord profile shows the application's name, our logo and the time played - like any
// game. It is Discord's own local protocol (a named pipe of the Discord app on this PC), nothing is sent to our server.
// It needs the Application ID of our application on https://discord.com/developers/applications: the application's NAME is
// what Discord writes after "Playing", and the picture is its Rich Presence art asset with the key "logo".
namespace Discord {
    inline const char* APP_ID = "";  // <- the Application ID (digits). Empty: the status is off.
    inline string appId = APP_ID;
    inline string site = "https://arcanic.su", invite;

    inline bool frame(HANDLE pipe, int op, const string& json) {
        string out(8, '\0');
        int32_t o = op, len = (int32_t)json.size();
        memcpy(&out[0], &o, 4);
        memcpy(&out[4], &len, 4);
        out += json;
        DWORD done = 0;
        return WriteFile(pipe, out.data(), (DWORD)out.size(), &done, nullptr) && done == out.size();
    }
    // Waits (a few seconds at most) for one frame from Discord; false if it hung up or said no
    inline bool reply(HANDLE pipe, int& op, string& body, int waitMs = 3000) {
        for (int waited = 0;; waited += 50) {
            DWORD avail = 0;
            if (!PeekNamedPipe(pipe, nullptr, 0, nullptr, &avail, nullptr)) return false;
            if (avail >= 8) break;
            if (waited >= waitMs) return false;
            Sleep(50);
        }
        char head[8];
        DWORD got = 0;
        if (!ReadFile(pipe, head, 8, &got, nullptr) || got != 8) return false;
        int32_t o, len;
        memcpy(&o, head, 4);
        memcpy(&len, head + 4, 4);
        if (len < 0 || len > 1 << 20) return false;
        body.assign((size_t)len, '\0');
        size_t at = 0;
        while (at < body.size()) {
            if (!ReadFile(pipe, &body[at], (DWORD)(body.size() - at), &got, nullptr) || !got) return false;
            at += got;
        }
        op = o;
        return true;
    }
    // The pipe of a running Discord that accepted our application, or INVALID_HANDLE_VALUE (no Discord, wrong ID)
    inline HANDLE connect(const string& app) {
        for (int i = 0; i < 10; i++) {
            HANDLE pipe = CreateFileW((L"\\\\.\\pipe\\discord-ipc-" + std::to_wstring(i)).c_str(),
                                      GENERIC_READ | GENERIC_WRITE, 0, nullptr, OPEN_EXISTING, 0, nullptr);
            if (pipe == INVALID_HANDLE_VALUE) continue;
            Json hello = Json::object();
            hello.set("v", Json::of((double)1));
            hello.set("client_id", Json::of(app));
            int op = 0;
            string body;
            if (frame(pipe, 0, hello.dump()) && reply(pipe, op, body) && op == 1) return pipe;
            CloseHandle(pipe);
        }
        return INVALID_HANDLE_VALUE;
    }
    inline bool activity(HANDLE pipe, DWORD pid, int64_t since, const string& mc) {
        Json stamps = Json::object();
        stamps.set("start", Json::of((double)since));
        Json assets = Json::object();
        assets.set("large_image", Json::of(string("logo")));
        assets.set("large_text", Json::of(string("Arcanic Visuals")));
        Json act = Json::object();
        act.set("details", Json::of("Minecraft " + mc));
        act.set("state", Json::of(string("arcanic.su")));
        act.set("timestamps", stamps);
        act.set("assets", assets);
        Json buttons = Json::array();
        auto button = [&](const string& label, const string& url) {
            if (!starts(url, "https://")) return;
            Json b = Json::object();
            b.set("label", Json::of(label));
            b.set("url", Json::of(url));
            buttons.a.push_back(b);
        };
        button("Arcanic", site);
        button("Discord", invite);
        if (!buttons.a.empty()) act.set("buttons", buttons);
        Json args = Json::object();
        args.set("pid", Json::of((double)pid));
        args.set("activity", act);
        Json cmd = Json::object();
        cmd.set("cmd", Json::of(string("SET_ACTIVITY")));
        cmd.set("args", args);
        cmd.set("nonce", Json::of(std::to_string(GetTickCount64())));
        return frame(pipe, 1, cmd.dump());
    }
    inline void whilePlaying(HANDLE game, const string& mc) {
        string app;
        for (char c : appId)
            if (isdigit((unsigned char)c)) app += c;
        if (app.size() < 15) {
            WaitForSingleObject(game, INFINITE);
            return;
        }
        HANDLE pipe = INVALID_HANDLE_VALUE;
        int64_t since = (int64_t)time(nullptr);
        ULONGLONG nextTry = 0;
        for (;;) {
            if (pipe == INVALID_HANDLE_VALUE && GetTickCount64() >= nextTry) {
                pipe = connect(app);
                if (pipe != INVALID_HANDLE_VALUE && !activity(pipe, GetProcessId(game), since, mc)) {
                    CloseHandle(pipe);
                    pipe = INVALID_HANDLE_VALUE;
                }
                nextTry = GetTickCount64() + 15000;
            }
            if (WaitForSingleObject(game, 2000) != WAIT_TIMEOUT) break;
            if (pipe != INVALID_HANDLE_VALUE) {  // read what Discord answers, notice when it is gone
                DWORD avail = 0;
                if (!PeekNamedPipe(pipe, nullptr, 0, nullptr, &avail, nullptr)) {
                    CloseHandle(pipe);
                    pipe = INVALID_HANDLE_VALUE;
                } else if (avail >= 8) {
                    int op;
                    string body;
                    if (!reply(pipe, op, body, 0) || op == 2) {
                        CloseHandle(pipe);
                        pipe = INVALID_HANDLE_VALUE;
                    }
                }
            }
        }
        if (pipe != INVALID_HANDLE_VALUE) CloseHandle(pipe);  // the line closes: the status is gone
    }
}  // namespace Discord
// ================================================================================================ installing and starting the game
namespace Installer {
    inline std::atomic<bool> busyUnpacking{false};
    // Trust mode: the client starts as visuals only (no modules that act for the player, no Lua)
    inline std::atomic<bool> trust{false};
    using Progress = std::function<void(float, const string&, const string&)>;
    // What prepare() had to put right on the way; Fix sets this on its own thread to tell the player
    struct Repairs {
        bool javaDownloaded = false, javaBroken = false, describedAgain = false, fresh = false;
        int missing = 0, damaged = 0, major = 0;
    };
    inline thread_local Repairs* repairs = nullptr;
    struct Plan {
        vector<string> command;
        fs::path workDir;
        string token;
        string loader;  // the Fabric Loader version it starts
    };
    struct Item {
        string url;
        fs::path dest;
        string sha1;
        int64_t size;
        fs::path seed;
    };

    inline string mb(int64_t b) {
        return fmt("%.1f МБ", b / 1048576.0);
    }
    inline string archName() {
        SYSTEM_INFO si;
        GetNativeSystemInfo(&si);
        return si.wProcessorArchitecture == PROCESSOR_ARCHITECTURE_ARM64   ? "arm64"
               : si.wProcessorArchitecture == PROCESSOR_ARCHITECTURE_AMD64 ? "x86_64"
                                                                           : "x86";
    }

    // Half of the machine's memory, between 2 and 4 GB
    inline int totalRamMb() {
        MEMORYSTATUSEX m = {sizeof m};
        GlobalMemoryStatusEx(&m);
        return (int)(m.ullTotalPhys >> 20);
    }
    inline int defaultRam() {
        int t = totalRamMb();
        return std::max(2048, std::min(4096, t / 2 / 512 * 512));
    }

    // True when the list of rules allows the thing it guards (no rules at all means allowed)
    inline bool allowed(const Json& rules) {
        if (rules.type != Json::Arr || rules.a.empty()) return true;
        bool res = false;
        for (auto& r : rules.a) {
            if (r.has("features")) continue;  // no optional launcher features are on
            const Json& os = r["os"];
            bool applies = true;
            if (os.type == Json::Obj) {
                string name = os.str("name"), arch = os.str("arch");
                if (!name.empty() && name != "windows") applies = false;
                if (!arch.empty() && arch != archName()) applies = false;
            }
            if (applies) res = r.str("action") == "allow";
        }
        return res;
    }
    // group:artifact:version[:classifier] -> relative path inside a maven repository
    inline string mavenPath(const string& name) {
        vector<string> p = split(name, ":");
        string ext = "jar";
        if (p.size() < 3) return name;
        string& last = p.back();
        size_t at = last.find('@');
        if (at != string::npos) {
            ext = last.substr(at + 1);
            last = last.substr(0, at);
        }
        string group = replaceAll(p[0], ".", "/");
        return group + "/" + p[1] + "/" + p[2] + "/" + p[1] + "-" + p[2] + (p.size() > 3 ? "-" + p[3] : "") + "." + ext;
    }
    // "size|last write time" of a file: if either changes, the file is checked again
    inline string fileStamp(const fs::path& f) {
        WIN32_FILE_ATTRIBUTE_DATA d;
        if (!GetFileAttributesExW(f.c_str(), GetFileExInfoStandard, &d)) return "";
        uint64_t n = ((uint64_t)d.nFileSizeHigh << 32) | d.nFileSizeLow,
                 t = ((uint64_t)d.ftLastWriteTime.dwHighDateTime << 32) | d.ftLastWriteTime.dwLowDateTime;
        return std::to_string(n) + "|" + std::to_string(t);
    }
    // SHA-1 of a file, read a megabyte at a time (lower-case hex; empty if it cannot be read)
    inline string sha1File(const fs::path& f) {
        HANDLE h = CreateFileW(f.c_str(), GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_DELETE, nullptr, OPEN_EXISTING,
                               FILE_FLAG_SEQUENTIAL_SCAN, nullptr);
        if (h == INVALID_HANDLE_VALUE) return "";
        Hasher hs(BCRYPT_SHA1_ALGORITHM);
        vector<char> buf(1 << 20);
        DWORD got = 0;
        while (ReadFile(h, buf.data(), (DWORD)buf.size(), &got, nullptr) && got) hs.add(buf.data(), got);
        CloseHandle(h);
        return lower(hs.doneHex());
    }
    inline bool valid(const fs::path& f, int64_t size) {
        int64_t n = fileSize(f);
        return n >= 0 && isFile(f) && (size <= 0 ? n > 0 : n == size);
    }
    inline bool fromSeed(const Item& it) {
        if (it.seed.empty() || !valid(it.seed, it.size)) return false;
        makeDirs(it.dest.parent_path());
        if (CreateHardLinkW(it.dest.c_str(), it.seed.c_str(), nullptr)) return true;
        return CopyFileW(it.seed.c_str(), it.dest.c_str(), FALSE) != 0;
    }

    // ---- Java: the configured one, our own download, the official launcher's runtimes, JAVA_HOME, the usual folders
    struct Found {
        fs::path exe;
        int major = 0;
        string arch;
    };
    inline Found inspect(const fs::path& home) {
        Found f;
        fs::path exe = home / L"bin" / L"javaw.exe";
        string rel;
        if (!isFile(exe) || !readFile(home / L"release", rel)) return f;
        for (auto& line : split(rel, "\r\n"))
            if (starts(line, "JAVA_VERSION=")) {
                string v = replaceAll(line.substr(13), "\"", "");
                if (starts(v, "1.")) v = v.substr(2);
                int major = atoi(v.c_str());
                if (major > 0) {
                    f.exe = exe;
                    f.major = major;
                }
            } else if (starts(line, "OS_ARCH="))
                f.arch = lower(replaceAll(line.substr(8), "\"", ""));
        return f;
    }
    inline fs::path ownRuntime(int major) {
        return Config::stateDir() / L"runtime" / P("jre-" + std::to_string(major));
    }
    inline fs::path findJava(int required) {
        vector<fs::path> homes;
        std::error_code ec;
        if (!Config::javaPath.empty()) {
            fs::path p = P(Config::javaPath);
            homes.push_back(isDir(p) ? p : p.parent_path().parent_path());
        }
        homes.push_back(ownRuntime(required));
        fs::path off = Game::officialDir();
        if (!off.empty() && isDir(off / L"runtime")) {
            for (auto it = fs::recursive_directory_iterator(off / L"runtime", ec);
                 !ec && it != fs::recursive_directory_iterator(); it.increment(ec)) {
                if (it.depth() > 3) {
                    it.disable_recursion_pending();
                    continue;
                }
                if (it->path().filename() == L"release" && isFile(it->path()))
                    homes.push_back(it->path().parent_path());
            }
        }
        if (hasEnv(L"JAVA_HOME")) homes.push_back(P(envVar(L"JAVA_HOME")));
        for (const wchar_t* root :
             {L"C:\\Program Files\\Eclipse Adoptium", L"C:\\Program Files\\Java", L"C:\\Program Files\\Microsoft",
              L"C:\\Program Files\\Zulu", L"C:\\Program Files\\BellSoft", L"C:\\Program Files\\Amazon Corretto"}) {
            if (isDir(root))
                for (auto& e : fs::directory_iterator(root, ec)) homes.push_back(e.path());
        }
        bool first = !Config::javaPath.empty();
        for (auto& h : homes) {
            Found f = inspect(h);
            bool chosen = first;
            first = false;
            if (f.exe.empty()) continue;
            bool bits = f.arch.empty() || archName() == "x86" || f.arch == "x86_64" || f.arch == "amd64" ||
                        (archName() == "arm64" && f.arch == "aarch64");
            if (bits && (f.major == required || (chosen && f.major > required))) return f.exe;
        }
        return {};
    }
    // Does this Java start at all? ("java -version" within 20 s; a half-deleted or broken Java fails here, not in the game)
    inline bool javaRuns(const fs::path& javaw) {
        fs::path exe = javaw.parent_path() / L"java.exe";
        if (!isFile(exe)) exe = javaw;
        wstring line = L"\"" + exe.wstring() + L"\" -version";
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
    // Downloads a Temurin JRE of the given major version into ~/.arcanic/runtime and unpacks it
    inline fs::path downloadJava(int major, const Progress& progress) {
        string arch = archName() == "arm64" ? "aarch64" : archName() == "x86" ? "x86" : "x64";
        string url = "https://api.adoptium.net/v3/binary/latest/" + std::to_string(major) + "/ga/windows/" + arch +
                     "/jre/hotspot/normal/eclipse";
        fs::path zip = Config::stateDir() / L"runtime" / P("jre-" + std::to_string(major) + ".zip");
        std::atomic<int64_t> got{0};
        int64_t approx = 55ll << 20;
        string sum;
        bool exact = false;
        try {  // the release list names the exact file, its size and its SHA-256
            Json pkg = Json::parse(Net::text(
                "https://api.adoptium.net/v3/assets/latest/" + std::to_string(major) + "/hotspot?architecture=" + arch +
                "&image_type=jre&os=windows&vendor=eclipse"))[(size_t)0]["binary"]["package"];
            if (starts(pkg.str("link"), "https://")) {
                url = pkg.str("link");
                sum = lower(pkg.str("checksum"));
                if (pkg.num("size", 0) > 0) {
                    approx = (int64_t)pkg.num("size", 0);
                    exact = true;
                }
            }
        } catch (...) {}
        Net::file(url, zip, "", [&](int64_t n) {
            got += n;
            progress(.2f * std::min(.95f, got / (float)approx), "Загружаем Java…",
                     mb(got) + (exact ? " / " : " / ~") + mb(approx));
        });
        progress(.19f, "Распаковываем Java…", "");
        if (!sum.empty()) {
            string all;
            if (!readFile(zip, all) || lower(sha256Hex(all)) != sum) {
                removeFile(zip);
                throw std::runtime_error("Java не найдена и не скачалась");
            }
        }
        Zip z;
        if (!z.open(zip)) throw std::runtime_error("Java не найдена и не скачалась");
        // unpacked beside and moved into place whole: a download cut short never leaves half a Java that looks ready
        fs::path place = ownRuntime(major), target = place;
        target += L".part";
        std::error_code ec;
        fs::remove_all(target, ec);
        fs::remove_all(place, ec);
        for (auto& e : z.entries) {
            size_t slash = e.name.find('/');
            if (slash == string::npos) continue;  // the archive's single top folder is dropped
            string rel = e.name.substr(slash + 1);
            if (rel.empty() || rel.find("..") != string::npos) continue;
            fs::path out = target / P(rel);
            if (ends(e.name, "/")) {
                makeDirs(out);
                continue;
            }
            string data;
            if (!z.read(e, data) || !writeFile(out, data)) throw std::runtime_error("Java не найдена и не скачалась");
        }
        z = Zip();
        removeFile(zip);
        fs::rename(target, place, ec);
        fs::path exe = inspect(place).exe;
        if (exe.empty()) throw std::runtime_error("Java не найдена и не скачалась");
        return exe;
    }

    inline string subst(const string& s, const std::map<string, string>& vars) {
        string out;
        for (size_t i = 0; i < s.size();) {
            size_t a = s.find("${", i);
            if (a == string::npos) {
                out += s.substr(i);
                break;
            }
            size_t b = s.find('}', a);
            if (b == string::npos) {
                out += s.substr(i);
                break;
            }
            out += s.substr(i, a - i);
            auto it = vars.find(s.substr(a + 2, b - a - 2));
            out += it != vars.end() ? it->second : s.substr(a, b - a + 1);
            i = b + 1;
        }
        return out;
    }
    inline void expand(const Json& args, const std::map<string, string>& vars, vector<string>& out) {
        for (auto& a : args.a) {
            if (a.type == Json::Str) {
                out.push_back(subst(a.s, vars));
                continue;
            }
            if (!allowed(a["rules"])) continue;
            const Json& v = a["value"];
            if (v.type == Json::Str)
                out.push_back(subst(v.s, vars));
            else
                for (auto& e : v.a)
                    if (e.type == Json::Str) out.push_back(subst(e.s, vars));
        }
    }
    inline string offlineUuid(const string& name) {
        string src = "OfflinePlayer:" + name;
        Hasher h(BCRYPT_MD5_ALGORITHM);
        h.add(src.data(), src.size());
        auto d = h.done();
        if (d.size() < 16) d.resize(16);
        d[6] = (d[6] & 0x0f) | 0x30;
        d[8] = (d[8] & 0x3f) | 0x80;
        return hex(d.data(), 16);
    }
    inline string offlineName(const string& raw) {
        string b;
        for (unsigned char c : raw)
            if (b.size() < 16 && (isalnum(c) || c == '_') && c < 128) b += (char)c;
        while (b.size() < 3) b += '_';
        return b;
    }

    // Gets everything the game needs into its folder and works out the command that starts it
    // loaderOk: which Fabric Loader versions the installed mods accept (the doctor works it out); empty - any
    inline Plan prepare(const fs::path& game, const string& playerName, const string& syncToken,
                        const Progress& progress, const std::function<bool(const string&)>& loaderOk = nullptr) {
        const string mc = Config::mcVersion;
        makeDirs(game);
        progress(0, "Проверяем версию игры…", "Minecraft " + mc + " · Fabric");
        fs::path off = Game::officialDir();
        std::error_code ec;
        if (!off.empty() && fs::weakly_canonical(off, ec) == fs::weakly_canonical(game, ec)) off.clear();
        auto seed = [&](const string& rel) { return off.empty() ? fs::path() : off / P(rel); };

        // ---- metadata
        auto described = [](const string& json, std::initializer_list<const char*> keys) {
            try {
                Json j = Json::parse(json);
                for (auto k : keys)
                    if (!j.has(k)) return false;
                return true;
            } catch (...) {
                return false;
            }
        };
        fs::path vf = game / L"versions" / P(mc) / P(mc + ".json");
        string text;
        auto fetchVanilla = [&] {
            Json manifest = Json::parse(Net::text("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"));
            string url;
            for (auto& v : manifest["versions"].a)
                if (v.str("id") == mc) {
                    url = v.str("url");
                    break;
                }
            if (url.empty()) throw std::runtime_error("Minecraft " + mc + " is not known to Mojang");
            writeFile(vf, Net::text(url));
        };
        auto wholeVanilla = [&] {
            text.clear();
            readFile(vf, text);
            return described(text, {"libraries", "mainClass", "downloads", "assetIndex"});
        };
        if (!isFile(vf)) {
            fs::path s = seed("versions/" + mc + "/" + mc + ".json");
            if (!s.empty() && isFile(s)) {
                makeDirs(vf.parent_path());
                CopyFileW(s.c_str(), vf.c_str(), FALSE);
            } else
                fetchVanilla();
        }
        if (!wholeVanilla()) {
            if (repairs) repairs->describedAgain = true;
            removeFile(vf);
            Net::preferMirror = true;
            fetchVanilla();
            if (!wholeVanilla()) {
                removeFile(vf);
                throw std::runtime_error("Описание Minecraft скачалось не целиком — нажми «Играть» ещё раз");
            }
        }
        Json vanilla = Json::parse(text);
        string loader;
        try {
            Json all = Json::parse(Net::text("https://meta.fabricmc.net/v2/versions/loader/" + mc));
            // the newest stable Loader the mods accept, else the newest they accept, else the newest stable
            auto ok = [&](const Json& o) { return !loaderOk || loaderOk(o["loader"].str("version")); };
            for (int pass = 0; pass < 3 && loader.empty(); pass++)
                for (auto& o : all.a)
                    if ((pass == 1 || o["loader"].flag("stable")) && (pass == 2 || ok(o))) {
                        loader = o["loader"].str("version");
                        break;
                    }
            if (loader.empty() && !all.a.empty()) loader = all.a[0]["loader"].str("version");
        } catch (...) {}
        if (loader.empty() && isDir(game / L"versions")) {  // offline: fall back to what is already installed
            string best;
            for (auto& e : fs::directory_iterator(game / L"versions", ec)) {
                string n = S(e.path().filename());
                if (starts(n, "fabric-loader-") && ends(n, "-" + mc) && n > best) best = n;
            }
            if (!best.empty()) loader = best.substr(14, best.size() - 14 - mc.size() - 1);
        }
        if (loader.empty()) throw std::runtime_error("Нет связи с сервером");
        string fabricId = "fabric-loader-" + loader + "-" + mc;
        fs::path ff = game / L"versions" / P(fabricId) / P(fabricId + ".json");
        auto fetchFabric = [&] {
            writeFile(ff,
                      Net::text("https://meta.fabricmc.net/v2/versions/loader/" + mc + "/" + loader + "/profile/json"));
        };
        auto wholeFabric = [&] {
            text.clear();
            readFile(ff, text);
            return described(text, {"libraries", "mainClass"});
        };
        if (!isFile(ff)) fetchFabric();
        if (!wholeFabric()) {
            if (repairs) repairs->describedAgain = true;
            removeFile(ff);
            Net::preferMirror = true;
            fetchFabric();
            if (!wholeFabric()) {
                removeFile(ff);
                throw std::runtime_error("Описание Fabric скачалось не целиком — нажми «Играть» ещё раз");
            }
        }
        Json fabric = Json::parse(text);

        // ---- Java
        int major = (int)vanilla["javaVersion"].num("majorVersion", 21);
        if (major <= 0) major = 21;
        progress(.03f, "Проверяем Java…", "Java " + std::to_string(major));
        if (repairs) repairs->major = major;
        fs::path java = findJava(major);
        if (!java.empty() && !javaRuns(java)) {  // there, but it does not start: ours is put in its place
            Log::warn("java does not start: " + S(java));
            if (repairs) repairs->javaBroken = true;
            std::error_code jec;
            if (java.wstring().rfind(ownRuntime(major).wstring(), 0) == 0) fs::remove_all(ownRuntime(major), jec);
            if (!Config::javaPath.empty()) {
                Config::javaPath.clear();
                Config::save();
            }
            java = findJava(major);
            if (!java.empty() && !javaRuns(java)) java.clear();
        }
        if (java.empty()) {
            java = downloadJava(major, progress);
            if (repairs) repairs->javaDownloaded = true;
        }
        if (!javaRuns(java)) throw std::runtime_error(Lang::en ? "Java does not start on this computer" : "Java не запускается на этом компьютере");

        // ---- everything that must exist on disk
        vector<Item> items;
        fs::path clientJar = game / L"versions" / P(mc) / P(mc + ".jar");
        const Json& client = vanilla["downloads"]["client"];
        items.push_back({client.str("url"), clientJar, client.str("sha1"), client.num("size"),
                         seed("versions/" + mc + "/" + mc + ".jar")});
        vector<fs::path> classpath;
        std::set<string> seen;
        auto addLibs = [&](const Json& libs) {
            for (auto& lib : libs.a) {
                if (!allowed(lib["rules"])) continue;
                string name = lib.str("name");
                vector<string> p = split(name, ":");
                if (p.size() < 3 || !seen.insert(p[0] + ":" + p[1] + (p.size() > 3 ? ":" + p[3] : "")).second) continue;
                string path, url, sha1;
                int64_t size = 0;
                const Json& art = lib["downloads"]["artifact"];
                if (art.type == Json::Obj) {
                    path = art.str("path");
                    url = art.str("url");
                    sha1 = art.str("sha1");
                    size = art.num("size");
                } else if (!lib.has("downloads")) {
                    path = mavenPath(name);
                    string b = lib.str("url", "https://libraries.minecraft.net/");
                    url = (ends(b, "/") ? b : b + "/") + path;
                    sha1 = lib.str("sha1");
                    size = lib.num("size");
                } else
                    continue;
                fs::path dest = game / L"libraries" / P(path);
                items.push_back({url, dest, sha1, size, seed("libraries/" + path)});
                classpath.push_back(dest);
            }
        };
        addLibs(fabric["libraries"]);
        addLibs(vanilla["libraries"]);
        classpath.push_back(clientJar);

        const Json& ai = vanilla["assetIndex"];
        string assetId = ai.str("id");
        fs::path indexFile = game / L"assets" / L"indexes" / P(assetId + ".json");
        if (fileSize(indexFile) != ai.num("size")) {
            fs::path s = seed("assets/indexes/" + assetId + ".json");
            if (!s.empty() && fileSize(s) == ai.num("size")) {
                makeDirs(indexFile.parent_path());
                CopyFileW(s.c_str(), indexFile.c_str(), FALSE);
            } else
                Net::file(ai.str("url"), indexFile, ai.str("sha1"), nullptr);
        }
        readFile(indexFile, text);
        {
            Json index = Json::parse(text);
            for (auto& kv : index["objects"].o) {
                string hash = kv.second.str("hash");
                if (hash.size() < 2) continue;
                string pre = hash.substr(0, 2);
                items.push_back({"https://resources.download.minecraft.net/" + pre + "/" + hash,
                                 game / L"assets" / L"objects" / P(pre) / P(hash), hash, kv.second.num("size"),
                                 seed("assets/objects/" + pre + "/" + hash)});
            }
        }

        // ---- mods the client needs alongside itself: the server's own set first, Modrinth when it hands out none
        fs::path mods = game / L"mods";
        makeDirs(mods);
        vector<string> wanted;
        for (auto& m : Api::serverMods().a) {
            string fn = m.str("filename");
            if (!safeFileName(fn)) continue;
            wanted.push_back(fn);
            items.push_back({m.str("url"), mods / P(fn), m.str("sha1"), m.num("size", -1), {}});
        }
        if (wanted.empty())
            for (auto& slug : split(Config::extraMods, ", \t")) {
                try {
                    Json versions = Json::parse(Net::text(string(Modrinth::API) + "/project/" + slug +
                                                          "/version?game_versions=" + Net::enc("[\"" + mc + "\"]") +
                                                          "&loaders=" + Net::enc("[\"fabric\"]")));
                    const Json* pick = nullptr;
                    for (auto& v : versions.a)
                        if (v.str("version_type") == "release") {
                            pick = &v;
                            break;
                        }
                    if (!pick && !versions.a.empty()) pick = &versions.a[0];
                    if (!pick) continue;
                    const Json* file = nullptr;
                    for (auto& f : (*pick)["files"].a) {
                        if (f.flag("primary")) {
                            file = &f;
                            break;
                        }
                        if (!file) file = &f;
                    }
                    if (!file || !safeFileName(file->str("filename"))) continue;
                    wanted.push_back(file->str("filename"));
                    items.push_back({file->str("url"),
                                     mods / P(file->str("filename")),
                                     (*file)["hashes"].str("sha1"),
                                     file->num("size"),
                                     {}});
                } catch (...) {}  // offline: whatever is installed stays
            }
        {
            string list;
            std::set<string> keep(wanted.begin(), wanted.end());
            if (readFile(mods / L".arcanic-managed", list))
                for (auto& l : split(list, "\r\n"))
                    if (safeFileName(trim(l)) && !keep.count(trim(l))) removeFile(mods / P(trim(l)));
            if (!wanted.empty()) {
                string out;
                for (auto& k : keep) out += k + "\n";
                writeFile(mods / L".arcanic-managed", out);
            }
        }
        if (!isFile(game / L"options.txt")) {  // skip Mojang's first-run screens
            wchar_t loc[16] = L"";
            GetUserDefaultLocaleName(loc, 16);
            writeFile(game / L"options.txt",
                      string("onboardAccessibility:false\nlang:") + (!wcsncmp(loc, L"ru", 2) ? "ru_ru" : "en_us") +
                          "\ntutorialStep:none\nskipMultiplayerWarning:true\njoinedFirstServer:true\n");
        }

        // ---- integrity, the way Lunar does it: every file is held against its published hash
        if (repairs) repairs->fresh = !isFile(clientJar);
        for (auto& it : items)
            if (!valid(it.dest, it.size)) fromSeed(it);
        fs::path cacheFile = Config::stateDir() / L"verified.txt";
        std::map<string, string> verified;  // file -> "size|write time|sha1"
        {
            string t;
            if (readFile(cacheFile, t))
                for (auto& l : split(t, "\r\n")) {
                    size_t tab = l.find('\t');
                    if (tab != string::npos) verified[l.substr(0, tab)] = trim(l.substr(tab + 1));
                }
        }
        vector<size_t> check;
        vector<char> bad(items.size(), 0);
        for (size_t i = 0; i < items.size(); i++) {
            const Item& it = items[i];
            if (!valid(it.dest, it.size)) {
                bad[i] = 1;
                continue;
            }                               // missing: it is simply downloaded
            if (it.sha1.empty()) continue;  // nothing to hold it against
            auto v = verified.find(S(it.dest));
            if (v != verified.end() && v->second == fileStamp(it.dest) + "|" + lower(it.sha1)) continue;
            check.push_back(i);
        }
        std::atomic<int> damaged{0};
        if (!check.empty()) {
            std::atomic<size_t> next{0}, checked{0};
            std::mutex vm;
            vector<std::thread> pool;
            for (int t = 0; t < 8; t++)
                pool.emplace_back([&] {
                    for (;;) {
                        size_t k = next++;
                        if (k >= check.size()) break;
                        const Item& it = items[check[k]];
                        string got = sha1File(it.dest), st = fileStamp(it.dest);
                        {
                            std::lock_guard<std::mutex> l(vm);
                            if (got == lower(it.sha1))
                                verified[S(it.dest)] = st + "|" + got;
                            else {
                                bad[check[k]] = 1;
                                damaged++;
                            }
                        }
                        checked++;
                    }
                });
            for (;;) {
                size_t d = checked;
                int bd = damaged;
                progress(.2f + .35f * d / check.size(), "Проверяем целостность файлов…",
                         std::to_string(d) + " / " + std::to_string(check.size()) +
                             (bd ? " · повреждено: " + std::to_string(bd) : ""));
                if (d >= check.size()) break;
                Sleep(100);
            }
            for (auto& t : pool) t.join();
        }

        // ---- download what is missing or damaged, eight files at a time
        vector<Item> todo;
        int64_t total = 0;
        for (size_t i = 0; i < items.size(); i++) {
            if (!bad[i]) continue;
            if (isFile(items[i].dest)) removeFile(items[i].dest);
            todo.push_back(items[i]);
            total += items[i].size > 0 ? items[i].size : 300000;
        }
        if (repairs) {
            repairs->damaged = damaged;
            repairs->missing = (int)todo.size() - damaged;
        }
        string fetchCaption = damaged > 0 || todo.size() < 400 ? "Докачиваем файлы…" : "Загружаем игру…";
        if (!todo.empty()) {
            std::sort(todo.begin(), todo.end(), [](const Item& a, const Item& b) { return a.size > b.size; });
            std::atomic<int64_t> done{0};
            std::atomic<size_t> next{0};
            std::atomic<int> alive{0};
            std::atomic<bool> failed{false};
            std::mutex em;
            string error;
            vector<std::thread> pool;
            for (int t = 0; t < 8; t++) {
                alive++;
                pool.emplace_back([&] {
                    for (;;) {
                        size_t i = next++;
                        if (i >= todo.size() || failed) break;
                        try {
                            Net::file(todo[i].url, todo[i].dest, todo[i].sha1, [&](int64_t n) { done += n; });
                        } catch (std::exception& e) {
                            std::lock_guard<std::mutex> l(em);
                            if (error.empty()) error = string("Не удалось: ") + e.what();
                            failed = true;
                        }
                    }
                    alive--;
                });
            }
            int64_t lastBytes = 0;
            ULONGLONG lastAt = GetTickCount64();
            double speed = 0;
            while (alive > 0) {
                int64_t d = std::max<int64_t>(0, std::min<int64_t>(done, total));
                ULONGLONG now = GetTickCount64();
                if (now - lastAt > 400) {
                    double inst = (d - lastBytes) / ((now - lastAt) / 1000.0);
                    speed = speed == 0 ? inst : speed * .6 + inst * .4;
                    lastBytes = d;
                    lastAt = now;
                }
                progress(.55f + .42f * (d / (float)total), fetchCaption,
                         mb(d) + " / " + mb(total) +
                             (speed > 50000 ? " · " + fmt("%.1f МБ/с", speed / 1048576.0) : ""));
                Sleep(150);
            }
            for (auto& t : pool) t.join();
            if (!error.empty()) throw std::runtime_error(error);
            for (auto& it : todo)
                if (!it.sha1.empty() && valid(it.dest, it.size))
                    verified[S(it.dest)] = fileStamp(it.dest) + "|" + lower(it.sha1);
        }
        {
            string out;
            for (auto& kv : verified)
                if (isFile(P(kv.first))) out += kv.first + "\t" + kv.second + "\n";
            makeDirs(cacheFile.parent_path());
            writeFile(cacheFile, out);
        }

        // ---- the command
        fs::path natives = game / L"natives" / P(fabricId);
        makeDirs(natives);
        string cp;
        for (auto& c : classpath) {
            if (!cp.empty()) cp += ";";
            cp += S(fs::absolute(c, ec));
        }
        std::map<string, string> vars = {{"auth_player_name", playerName},
                                         {"auth_uuid", offlineUuid(playerName)},
                                         {"auth_access_token", "0"},
                                         {"auth_xuid", "0"},
                                         {"clientid", "0"},
                                         {"user_type", "legacy"},
                                         {"version_name", fabricId},
                                         {"version_type", "release"},
                                         {"game_directory", S(fs::absolute(game, ec))},
                                         {"assets_root", S(fs::absolute(game / L"assets", ec))},
                                         {"assets_index_name", assetId},
                                         {"natives_directory", S(fs::absolute(natives, ec))},
                                         {"launcher_name", "Arcanic"},
                                         {"launcher_version", Config::VERSION},
                                         {"classpath", cp}};
        int ram = Config::ramMb > 0 ? Config::ramMb : defaultRam();
        if (ram > totalRamMb() * 3 / 4) {  // more than the computer can give: Java would not start
            Log::warn(fmt("memory %d MB is too much for %d MB in total; using %d MB", ram, totalRamMb(), defaultRam()));
            ram = defaultRam();
        }
        Plan plan;
        plan.loader = loader;
        plan.workDir = game;
        vector<string>& cmd = plan.command;
        cmd.push_back(S(java));
        cmd.push_back("-Xmx" + std::to_string(ram) + "M");
        cmd.push_back("-Xms" + std::to_string(std::min(ram, 1024)) + "M");
        wchar_t launcher[32768];
        DWORD ln = GetModuleFileNameW(nullptr, launcher, 32768);
        if (ln > 0 && ln < 32768) cmd.push_back("-Darcanic.launcher=" + U(wstring(launcher, ln)));
        cmd.push_back("-Darcanic.helpers=media");        // the client may start us with --media for its music player
        cmd.push_back("-Darcanic.api=" + Config::base);  // the client syncs emotes and pets through our server
        if (trust) cmd.push_back("-Darcanic.trust=true");
        plan.token = syncToken;
        if (lower(Config::jvmArgs).find("gc") ==
            string::npos)
            for (const char* a : {"-XX:+UnlockExperimentalVMOptions", "-XX:+UseG1GC", "-XX:G1NewSizePercent=20",
                                  "-XX:G1ReservePercent=20", "-XX:MaxGCPauseMillis=50", "-XX:G1HeapRegionSize=32M"})
                cmd.push_back(a);
        {
            string cur;
            for (char ch : Config::jvmArgs + " ") {
                if (ch == ' ' || ch == '\t') {
                    if (!cur.empty()) cmd.push_back(cur);
                    cur.clear();
                } else
                    cur += ch;
            }
        }
        expand(vanilla["arguments"]["jvm"], vars, cmd);
        expand(fabric["arguments"]["jvm"], vars, cmd);
        cmd.push_back(fabric.str("mainClass", vanilla.str("mainClass")));
        expand(vanilla["arguments"]["game"], vars, cmd);
        expand(fabric["arguments"]["game"], vars, cmd);
        progress(1, "Запускаем игру…", "");
        return plan;
    }

    // Replace the actual game identity only after a successful Microsoft refresh
    inline void license(Plan& plan, const Microsoft::Account& account) {
        const std::map<string, string> values = {
            {"--username", account.name}, {"--uuid", account.uuid}, {"--accessToken", account.access},
            {"--userType", "msa"},        {"--xuid", account.xuid}, {"--clientId", Microsoft::CLIENT_ID}};
        for (const auto& kv : values) {
            bool found = false;
            for (size_t i = 1; i + 1 < plan.command.size(); i++)
                if (plan.command[i] == kv.first) {
                    plan.command[i + 1] = kv.second;
                    found = true;
                }
            if (!found) {
                plan.command.push_back(kv.first);
                plan.command.push_back(kv.second);
            }
        }
    }
    // Windows' rules for one argument on a command line
    inline wstring quoteArg(const wstring& a) {
        if (!a.empty() && a.find_first_of(L" \t\"") == wstring::npos) return a;
        wstring out = L"\"";
        int slashes = 0;
        for (wchar_t c : a) {
            if (c == L'\\') {
                slashes++;
                continue;
            }
            if (c == L'"') {
                out.append(slashes * 2 + 1, L'\\');
                out += c;
                slashes = 0;
                continue;
            }
            out.append(slashes, L'\\');
            slashes = 0;
            out += c;
        }
        out.append(slashes * 2, L'\\');
        out += L'"';
        return out;
    }
    // Starts the game with its output going to logs/launcher.log; returns the process handle (null on failure)
    inline HANDLE start(const Plan& plan) {
        fs::path log = plan.workDir / L"logs" / L"launcher.log";
        makeDirs(log.parent_path());
        SECURITY_ATTRIBUTES sa = {sizeof sa, nullptr, TRUE};
        HANDLE out = CreateFileW(log.c_str(), GENERIC_WRITE, FILE_SHARE_READ, &sa, CREATE_ALWAYS, 0, nullptr);
        wstring line;
        for (auto& a : plan.command) {
            if (!line.empty()) line += L' ';
            line += quoteArg(W(a));
        }
        STARTUPINFOW si = {sizeof si};
        PROCESS_INFORMATION pi = {};
        si.dwFlags = STARTF_USESTDHANDLES;
        si.hStdOutput = out;
        si.hStdError = out;
        si.hStdInput = nullptr;
        // the Arcanic token goes in the environment: a command line can be read by any program on the computer
        if (!plan.token.empty()) SetEnvironmentVariableW(L"ARCANIC_TOKEN", W(plan.token).c_str());
        BOOL ok = CreateProcessW(nullptr, line.data(), nullptr, nullptr, TRUE, CREATE_NO_WINDOW, nullptr,
                                 plan.workDir.c_str(), &si, &pi);
        SetEnvironmentVariableW(L"ARCANIC_TOKEN", nullptr);
        if (out != INVALID_HANDLE_VALUE) CloseHandle(out);
        if (!ok) return nullptr;
        CloseHandle(pi.hThread);
        return pi.hProcess;
    }
    // Puts the decrypted client into mods/ (clearing older copies), returns where
    inline fs::path deploy(const string& jar) {
        fs::path mods = Game::modsDir();
        std::error_code ec;
        for (auto& e : fs::directory_iterator(mods, ec)) {
            string n = lower(S(e.path().filename()));
            if (ends(n, ".jar") && (starts(n, "arcanicvisuals") || starts(n, "arcanic-client"))) removeFile(e.path());
        }
        fs::path target = mods / P(Config::MOD_FILE), tmp = target;
        tmp += L".part";
        if (!writeFile(tmp, jar) || !MoveFileExW(tmp.c_str(), target.c_str(), MOVEFILE_REPLACE_EXISTING))
            throw std::runtime_error("Не удалось: mods/" + string(Config::MOD_FILE));
        return target;
    }
}  // namespace Installer

// ================================================================================================ skins: the face on an account
namespace Skins {
    inline string defaultSkin(const string& name) {
        string id = Installer::offlineUuid(name);
        if (id.size() < 32) return "";
        uint64_t hi = strtoull(id.substr(0, 16).c_str(), nullptr, 16),
                 lo = strtoull(id.substr(16, 16).c_str(), nullptr, 16), x = hi ^ lo;
        int32_t hash = (int32_t)(x >> 32) ^ (int32_t)x;
        int idx = ((hash % 18) + 18) % 18;
        static const char* who[] = {"alex", "ari", "efe", "kai", "makena", "noor", "steve", "sunny", "zuri"};
        string path = string("assets/minecraft/textures/entity/player/") + (idx < 9 ? "slim/" : "wide/") +
                      who[idx % 9] + ".png",
               bytes;
        Zip z;
        if (z.open(Game::dir() / L"versions" / P(Config::mcVersion) / P(Config::mcVersion + ".jar")))
            z.read(path, bytes);
        return bytes;
    }
    inline bool png(const string& b) {
        return b.size() > 64 && !b.compare(0, 4, "\x89PNG");
    }
    // A texture straight from the installed game's jar
    inline string gameTexture(const string& item) {
        string bytes;
        Zip z;
        if (z.open(Game::dir() / L"versions" / P(Config::mcVersion) / P(Config::mcVersion + ".jar")))
            z.read("assets/minecraft/textures/" + item + ".png", bytes);
        return png(bytes) ? bytes : "";
    }
    inline string skin(const string& name) {
        fs::path cache = Config::stateDir() / L"cache" / L"heads" / P(lower(name) + ".png");
        string bytes, kept;
        std::error_code ec;
        bool have = isFile(cache) && readFile(cache, kept);
        bool fresh = have && std::chrono::duration_cast<std::chrono::hours>(fs::file_time_type::clock::now() -
                                                                            fs::last_write_time(cache, ec))
                                     .count() < 24;
        if (!fresh) {
            bool answered = false;
            try {
                Net::Response r = Net::request(
                    "GET", "https://api.mojang.com/users/profiles/minecraft/" + Net::enc(name), {}, "", nullptr, 10);
                if (r.status == 404 || r.status == 204)
                    answered = true;  // nobody owns this nick
                else if (r.status == 200) {
                    string id = Json::parse(r.body).str("id");
                    Net::Response p = Net::request(
                        "GET", "https://sessionserver.mojang.com/session/minecraft/profile/" + id, {}, "", nullptr, 10);
                    if (p.status == 200) {
                        answered = true;
                        Json profile = Json::parse(p.body);
                        for (auto& prop : profile["properties"].a) {
                            string decoded;
                            if (prop.str("name") != "textures" || !base64(prop.str("value"), decoded)) continue;
                            string url = Json::parse(decoded)["textures"]["SKIN"].str("url");
                            if (starts(url, "http://")) url = "https://" + url.substr(7);
                            if (!starts(url, "https://textures.minecraft.net/")) continue;
                            Net::Response s = Net::request("GET", url, {}, "", nullptr, 15);
                            if (s.status == 200 && png(s.body))
                                bytes = s.body;
                            else
                                answered = false;
                        }
                    }
                }
            } catch (...) {}
            if (answered) {
                makeDirs(cache.parent_path());
                writeFile(cache, bytes);
                kept = bytes;
            }
        }
        if (png(kept)) return kept;
        return defaultSkin(name);
    }
}  // namespace Skins
