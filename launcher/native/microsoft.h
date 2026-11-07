#pragma once
// Included after Config, Lang and Net. Microsoft credentials never go through Arcanic's server.
#include <wincrypt.h>
#include <stdexcept>

namespace Microsoft {
    inline const string CLIENT_ID = "54fd49e4-2103-4044-9603-2b028c814ec3";
    inline const string AUTHORITY = "https://login.microsoftonline.com/consumers/oauth2/v2.0/";
    inline const string SCOPE = "XboxLive.signin offline_access";
    inline string message(const char* ru, const char* en) {
        return Lang::en ? en : ru;
    }
    struct Account {
        string name, uuid, access, refresh, xuid;
    };
    inline Json json(const Account& a, bool secrets = false) {
        Json j = Json::object();
        j.set("name", Json::of(a.name));
        j.set("uuid", Json::of(a.uuid));
        j.set("xuid", Json::of(a.xuid));
        if (secrets) {
            j.set("access", Json::of(a.access));
            j.set("refresh", Json::of(a.refresh));
        }
        return j;
    }
    inline Account account(const Json& j) {
        return {j.str("name"), j.str("uuid"), j.str("access"), j.str("refresh"), j.str("xuid")};
    }
    inline bool valid(const Account& a) {
        return a.uuid.size() == 32 && a.uuid.find_first_not_of("0123456789abcdefABCDEF") == string::npos &&
               a.name.size() >= 3 && a.name.size() <= 16 &&
               a.name.find_first_not_of("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_") ==
                   string::npos;
    }
    struct Lock {
        HANDLE h = nullptr;
        explicit Lock(const wchar_t* suffix, DWORD wait = 30000) {
            wstring name = L"Local\\ArcanicMicrosoft-" + W(sha256Hex(S(Config::stateDir())).substr(0, 24)) + suffix;
            h = CreateMutexW(nullptr, FALSE, name.c_str());
            DWORD r = h ? WaitForSingleObject(h, wait) : WAIT_FAILED;
            if (r != WAIT_OBJECT_0 && r != WAIT_ABANDONED) {
                if (h) CloseHandle(h);
                h = nullptr;
                throw std::runtime_error(
                    message("Вход Microsoft уже выполняется в другом окне. Завершите его и повторите.",
                            "Microsoft sign-in is already running in another window. Finish it and retry."));
            }
        }
        ~Lock() {
            if (h) {
                ReleaseMutex(h);
                CloseHandle(h);
            }
        }
        Lock(const Lock&) = delete;
    };
    inline fs::path storeFile() {
        return Config::stateDir() / L"microsoft-accounts.dat";
    }
    inline vector<Account> readStore() {
        string encrypted;
        if (!isFile(storeFile())) return {};
        if (!readFile(storeFile(), encrypted) || encrypted.empty())
            throw std::runtime_error("Cannot read Microsoft account storage");
        DATA_BLOB in{(DWORD)encrypted.size(), (BYTE*)encrypted.data()}, out{};
        if (!CryptUnprotectData(&in, nullptr, nullptr, nullptr, nullptr, CRYPTPROTECT_UI_FORBIDDEN, &out))
            throw std::runtime_error(
                message("Windows не смог расшифровать аккаунты Microsoft. Используйте исходную учётную запись Windows.",
                        "Windows could not decrypt Microsoft accounts. Use the original Windows user account."));
        string plain((char*)out.pbData, out.cbData);
        SecureZeroMemory(out.pbData, out.cbData);
        LocalFree(out.pbData);
        Json root = Json::parse(plain);
        SecureZeroMemory(plain.data(), plain.size());
        if (root.str("clientId") != CLIENT_ID || root["accounts"].type != Json::Arr)
            throw std::runtime_error("Invalid Microsoft account storage");
        vector<Account> result;
        for (const auto& j : root["accounts"].a) {
            Account a = account(j);
            if (!valid(a)) throw std::runtime_error("Invalid saved Microsoft profile");
            result.push_back(a);
        }
        return result;
    }
    inline void writeStore(const vector<Account>& accounts) {
        Json root = Json::object(), arr = Json::array();
        for (auto& a : accounts) arr.a.push_back(json(a, true));
        root.set("clientId", Json::of(CLIENT_ID));
        root.set("accounts", arr);
        string plain = root.dump();
        DATA_BLOB in{(DWORD)plain.size(), (BYTE*)plain.data()}, out{};
        BOOL ok = CryptProtectData(&in, L"Arcanic Microsoft accounts", nullptr, nullptr, nullptr,
                                   CRYPTPROTECT_UI_FORBIDDEN, &out);
        SecureZeroMemory(plain.data(), plain.size());
        if (!ok) throw std::runtime_error("Windows could not protect Microsoft accounts");
        fs::path temp = storeFile();
        temp += L".tmp";
        bool written = writeFile(temp, out.pbData, out.cbData);
        SecureZeroMemory(out.pbData, out.cbData);
        LocalFree(out.pbData);
        if (!written ||
            !MoveFileExW(temp.c_str(), storeFile().c_str(), MOVEFILE_REPLACE_EXISTING | MOVEFILE_WRITE_THROUGH)) {
            removeFile(temp);
            throw std::runtime_error("Cannot save Microsoft accounts");
        }
    }
    inline vector<Account> list() {
        Lock l(L"store");
        auto result = readStore();
        for (auto& a : result) {
            a.access.clear();
            a.refresh.clear();
        }
        return result;
    }
    inline void save(const Account& a) {
        Lock l(L"store");
        auto all = readStore();
        for (auto& old : all)
            if (old.uuid == a.uuid) {
                old = a;
                writeStore(all);
                return;
            }
        if (all.size() >= 3)
            throw std::runtime_error(message("Сохранено 3 аккаунта Microsoft. Удалите один, чтобы добавить новый.",
                                             "Three Microsoft accounts are saved. Remove one before adding another."));
        all.push_back(a);
        writeStore(all);
    }
    inline void remove(const string& uuid) {
        Lock flow(L"flow", 0);
        Lock l(L"store");
        auto all = readStore();
        all.erase(std::remove_if(all.begin(), all.end(), [&](const Account& a) { return a.uuid == uuid; }), all.end());
        writeStore(all);
    }
    // Injectable transport for offline protocol tests; production disables redirects and uses only fixed HTTPS endpoints.
    using Transport = std::function<Net::Response(const string&, const string&, const vector<string>&, const string&)>;
    inline Transport transport = [](const string& method, const string& url, const vector<string>& headers,
                                    const string& body) {
        return Net::request(method, url, headers, body, nullptr, 20, false);
    };
    inline Json checked(const Net::Response& r, const string& step) {
        Json j = Json::parse(r.body);
        if (r.status == 200 && r.error.empty() && j.type == Json::Obj) return j;
        // Do not include provider response bodies: they can contain credentials and authorization codes.
        if (step == "Minecraft" && r.status == 403)
            throw std::runtime_error(message(
                "Minecraft отклонил приложение Arcanic (403). Проверьте одобрение client ID для Minecraft API.",
                "Minecraft rejected Arcanic (403). Check that its client ID is approved for the Minecraft API."));
        if (step == "Profile" && r.status == 404)
            throw std::runtime_error(message("У аккаунта нет доступного профиля Minecraft Java. Проверьте лицензию и "
                                             "создайте Java-профиль на minecraft.net.",
                                             "No Minecraft Java profile is available. Check your license and create a "
                                             "Java profile at minecraft.net."));
        if (j.str("error") == "invalid_grant")
            throw std::runtime_error(
                message("Авторизация Microsoft истекла или отозвана. Добавьте этот аккаунт заново.",
                        "Microsoft authorization expired or was revoked. Add this account again."));
        if (j.num("XErr") == 2148916233LL)
            throw std::runtime_error(message("Сначала создайте профиль Xbox для этого аккаунта на xbox.com.",
                                             "Create an Xbox profile for this account at xbox.com first."));
        if (j.num("XErr") >= 2148916236LL && j.num("XErr") <= 2148916238LL)
            throw std::runtime_error(message("Xbox требует настройки возраста или семейного аккаунта.",
                                             "Xbox requires age verification or family account setup."));
        if (j.num("XErr") == 2148916235LL)
            throw std::runtime_error(message("Xbox Live недоступен для региона этого аккаунта.",
                                             "Xbox Live is unavailable for this account's region."));
        throw std::runtime_error(step + message(": ошибка авторизации (HTTP ", ": authentication failed (HTTP ") +
                                 std::to_string(r.status) + "). " +
                                 message("Проверьте сеть и настройки приложения Microsoft.",
                                         "Check your network and Microsoft app settings."));
    }
    inline Json form(const string& body) {
        return checked(
            transport("POST", AUTHORITY + "token", {"Content-Type: application/x-www-form-urlencoded"}, body),
            "Microsoft");
    }
    inline Json postJson(const string& url, const Json& body, const string& step) {
        return checked(
            transport("POST", url, {"Content-Type: application/json", "Accept: application/json"}, body.dump()), step);
    }
    inline string required(const Json& j, const char* key) {
        string s = j.str(key);
        if (s.empty()) throw std::runtime_error("Incomplete authentication response");
        return s;
    }
    inline Account exchange(const Json& tokens) {
        string msa = required(tokens, "access_token");
        Json props = Json::object();
        props.set("AuthMethod", Json::of("RPS"));
        props.set("SiteName", Json::of("user.auth.xboxlive.com"));
        props.set("RpsTicket", Json::of("d=" + msa));
        Json request = Json::object();
        request.set("Properties", props);
        request.set("RelyingParty", Json::of("http://auth.xboxlive.com"));
        request.set("TokenType", Json::of("JWT"));
        Json xbox = postJson("https://user.auth.xboxlive.com/user/authenticate", request, "Xbox Live");
        Json userTokens = Json::array();
        userTokens.a.push_back(Json::of(required(xbox, "Token")));
        props = Json::object();
        props.set("SandboxId", Json::of("RETAIL"));
        props.set("UserTokens", userTokens);
        request.set("Properties", props);
        request.set("RelyingParty", Json::of("rp://api.minecraftservices.com/"));
        Json xsts = postJson("https://xsts.auth.xboxlive.com/xsts/authorize", request, "Xbox XSTS");
        const Json& claim = xsts["DisplayClaims"]["xui"][(size_t)0];
        Json identity = Json::object();
        identity.set("identityToken", Json::of("XBL3.0 x=" + required(claim, "uhs") + ";" + required(xsts, "Token")));
        Json mc = postJson("https://api.minecraftservices.com/authentication/login_with_xbox", identity, "Minecraft");
        string access = required(mc, "access_token");
        Json profile = checked(transport("GET", "https://api.minecraftservices.com/minecraft/profile",
                                         {"Authorization: Bearer " + access}, ""),
                               "Profile");
        Account a{profile.str("name"), profile.str("id"), access, required(tokens, "refresh_token"),
                  claim.str("xid", "0")};
        if (!valid(a)) throw std::runtime_error("Invalid Minecraft profile");
        return a;
    }
    inline string randomHex() {
        unsigned char bytes[32];
        if (BCryptGenRandom(nullptr, bytes, sizeof bytes, BCRYPT_USE_SYSTEM_PREFERRED_RNG) < 0)
            throw std::runtime_error("Secure random generator failed");
        return hex(bytes, sizeof bytes);
    }
    inline string challenge(const string& verifier) {
        Hasher h(BCRYPT_SHA256_ALGORITHM);
        if (!h.hash) throw std::runtime_error("SHA256 unavailable");
        h.add(verifier.data(), verifier.size());
        auto bytes = h.done();
        DWORD size = 0;
        CryptBinaryToStringA(bytes.data(), (DWORD)bytes.size(), CRYPT_STRING_BASE64 | CRYPT_STRING_NOCRLF, nullptr,
                             &size);
        string out(size, '\0');
        if (!CryptBinaryToStringA(bytes.data(), (DWORD)bytes.size(), CRYPT_STRING_BASE64 | CRYPT_STRING_NOCRLF,
                                  out.data(), &size))
            throw std::runtime_error("PKCE encoding failed");
        out.resize(size);
        while (!out.empty() && (out.back() == '=' || out.back() == '\0')) out.pop_back();
        return replaceAll(replaceAll(out, "+", "-"), "/", "_");
    }
    inline string decode(const string& s) {
        string out;
        for (size_t i = 0; i < s.size(); i++) {
            if (s[i] == '+')
                out += ' ';
            else if (s[i] == '%') {
                if (i + 2 >= s.size() || !isxdigit((unsigned char)s[i + 1]) || !isxdigit((unsigned char)s[i + 2]))
                    throw std::runtime_error("Invalid callback encoding");
                out += (char)strtoul(s.substr(i + 1, 2).c_str(), nullptr, 16);
                i += 2;
            } else
                out += s[i];
        }
        return out;
    }
    inline string callbackCode(const string& target, const string& state) {
        if (!starts(target, "/?")) return "";
        std::map<string, string> params;
        for (auto& pair : split(target.substr(2), "&")) {
            auto at = pair.find('=');
            if (at == string::npos) continue;
            string k = decode(pair.substr(0, at));
            if (params.count(k)) return "";
            params[k] = decode(pair.substr(at + 1));
        }
        if (params["state"] != state) return "";
        if (!params["error"].empty())
            throw std::runtime_error(
                message("Вход отменён или отклонён Microsoft.", "Sign-in was cancelled or rejected by Microsoft."));
        return params["code"];
    }
    struct Socket {
        SOCKET s = INVALID_SOCKET;
        ~Socket() {
            if (s != INVALID_SOCKET) closesocket(s);
        }
    };
    struct Winsock {
        Winsock() {
            WSADATA data;
            if (WSAStartup(MAKEWORD(2, 2), &data)) throw std::runtime_error("Windows sockets unavailable");
        }
        ~Winsock() { WSACleanup(); }
    };
    inline Account login(std::atomic<bool>& cancel, const std::function<void(const string&)>& progress) {
        Lock flow(L"flow", 0);
        Winsock ws;
        Socket listener;
        listener.s = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
        sockaddr_in address{};
        address.sin_family = AF_INET;
        address.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
        address.sin_port = 0;
        BOOL exclusive = TRUE;
        setsockopt(listener.s, SOL_SOCKET, SO_EXCLUSIVEADDRUSE, (char*)&exclusive, sizeof exclusive);
        if (listener.s == INVALID_SOCKET || bind(listener.s, (sockaddr*)&address, sizeof address) ||
            listen(listener.s, 4))
            throw std::runtime_error("Cannot open local Microsoft callback");
        int len = sizeof address;
        getsockname(listener.s, (sockaddr*)&address, &len);
        string redirect = "http://localhost:" + std::to_string(ntohs(address.sin_port)) + "/", state = randomHex(),
               verifier = randomHex();
        string url = AUTHORITY + "authorize?client_id=" + CLIENT_ID + "&response_type=code&scope=" + Net::enc(SCOPE) +
                     "&redirect_uri=" + Net::enc(redirect) + "&state=" + state +
                     "&code_challenge=" + challenge(verifier) + "&code_challenge_method=S256&prompt=select_account";
        if ((INT_PTR)ShellExecuteW(nullptr, L"open", W(url).c_str(), nullptr, nullptr, SW_SHOWNORMAL) <= 32)
            throw std::runtime_error(
                message("Не удалось открыть браузер по умолчанию.", "Could not open the default browser."));
        progress(message("Завершите вход Microsoft в браузере. Повторное нажатие отменит вход.",
                         "Complete Microsoft sign-in in your browser. Click again to cancel."));
        ULONGLONG deadline = GetTickCount64() + 10 * 60 * 1000;
        string code;
        while (!cancel && GetTickCount64() < deadline && code.empty()) {
            fd_set read;
            FD_ZERO(&read);
            FD_SET(listener.s, &read);
            timeval timeout{0, 200000};
            if (select(0, &read, nullptr, nullptr, &timeout) <= 0) continue;
            Socket client;
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
            string error;
            if (starts(request, "GET ")) {
                size_t end = request.find(' ', 4);
                if (end != string::npos) try {
                        code = callbackCode(request.substr(4, end - 4), state);
                    } catch (const std::exception& e) {
                        error = e.what();
                    }
            }
            string page = string("<!doctype html><meta charset=utf-8><title>Arcanic</title><body "
                                 "style='background:#17171e;color:#eee;font:18px "
                                 "system-ui;padding:10vh;text-align:center'><h1>Arcanic Launcher</h1><p>") +
                          (code.empty() ? "This sign-in response was not accepted. Return to Arcanic."
                                        : "Microsoft response received. Return to Arcanic to finish signing in.") +
                          "</p><script>history.replaceState(null,'','/')</script>";
            string response = string("HTTP/1.1 ") + (code.empty() ? "400 Bad Request" : "200 OK") +
                              "\r\nContent-Type: text/html; charset=utf-8\r\nCache-Control: "
                              "no-store\r\nReferrer-Policy: no-referrer\r\nConnection: close\r\nContent-Length: " +
                              std::to_string(page.size()) + "\r\n\r\n" + page;
            send(client.s, response.data(), (int)response.size(), 0);
            if (!error.empty()) throw std::runtime_error(error);
        }
        if (cancel) throw std::runtime_error(message("Вход отменён.", "Sign-in cancelled."));
        if (code.empty())
            throw std::runtime_error(
                message("Время ожидания входа истекло. Попробуйте снова.", "Sign-in timed out. Try again."));
        progress(message("Проверяем профиль Minecraft…", "Checking your Minecraft profile…"));
        Json tokens =
            form("client_id=" + CLIENT_ID + "&grant_type=authorization_code&redirect_uri=" + Net::enc(redirect) +
                 "&code=" + Net::enc(code) + "&code_verifier=" + Net::enc(verifier) + "&scope=" + Net::enc(SCOPE));
        Account a = exchange(tokens);
        if (cancel) throw std::runtime_error(message("Вход отменён.", "Sign-in cancelled."));
        save(a);
        return a;
    }
    inline Account refresh(const string& uuid) {
        Lock flow(L"flow", 0);
        Account saved;
        {
            Lock store(L"store");
            for (auto& a : readStore())
                if (a.uuid == uuid) saved = a;
        }
        if (!valid(saved) || saved.refresh.empty())
            throw std::runtime_error(
                message("Добавьте аккаунт Microsoft заново.", "Add your Microsoft account again."));
        Json tokens =
            form("client_id=" + CLIENT_ID + "&grant_type=refresh_token&refresh_token=" + Net::enc(saved.refresh) +
                 "&scope=" + Net::enc(SCOPE));
        if (tokens.str("refresh_token").empty()) tokens.set("refresh_token", Json::of(saved.refresh));
        // Persist token rotation even if an Xbox or Minecraft service is temporarily unavailable.
        saved.refresh = tokens.str("refresh_token");
        save(saved);
        Account next = exchange(tokens);
        if (next.uuid != uuid) throw std::runtime_error("Microsoft profile changed unexpectedly");
        save(next);
        return next;
    }
    inline int helper(int argc, LPWSTR* argv) {
        if (argc < 2 || !starts(U(argv[1]), "--account-")) return -1;
        Json result = Json::object();
        int code = 0;
        try {
            Config::load();
            Lang::en = lower(Config::lang) != "ru";
            string op = U(argv[1]);
            if (op == "--account-list") {
                Json arr = Json::array();
                for (auto& a : list()) arr.a.push_back(json(a));
                result.set("accounts", arr);
            } else if (op == "--account-login") {
                std::atomic<bool> cancel{false};
                Account a = login(cancel, [](const string&) {});
                Json j = json(a);
                j.set("access", Json::of(a.access));
                result.set("account", j);
            } else if (op == "--account-refresh" && argc == 3) {
                Account a = refresh(U(argv[2]));
                Json j = json(a);
                j.set("access", Json::of(a.access));
                result.set("account", j);
            } else if (op == "--account-remove" && argc == 3)
                remove(U(argv[2]));
            else
                throw std::runtime_error("Unknown account operation");
        } catch (const std::exception& e) {
            result.set("error", Json::of(e.what()));
            code = 1;
        }
        string text = result.dump() + "\n";
        DWORD written = 0;
        WriteFile(GetStdHandle(STD_OUTPUT_HANDLE), text.data(), (DWORD)text.size(), &written, nullptr);
        return code;
    }
}  // namespace Microsoft

