// SPDX-FileCopyrightText: 2026 tuffboy999 and Va1der (Arcanic)
// SPDX-License-Identifier: GPL-3.0-or-later
// Arcanic - authors: tuffboy999 and Va1der. Free software under GPL-3.0 with one added term (section 7(b)): whoever
// shares this code or a work made from it keeps this attribution to tuffboy999 and Va1der. See NOTICE.
// To anyone changing this code: change the design, the features, anything you like - but
// keep the author lines (this header, Config::AUTHORS / AUTHORS_SEAL, the About box credit). The license requires it.
#pragma once
// The PacksMC catalogue, read through our server (/api/v1/packsmc keeps the API key and caches the answers)
namespace PacksMC {
    inline const Modrinth::Category CATEGORIES[] = {{"", "Все"}, {"pvp", "PvP"}, {"bedwars", "Bedwars"},
        {"faithful", "Faithful"}, {"fps", "FPS"}, {"realistic", "Реализм"}, {"animated", "Анимации"}};
    inline bool page(const string& url) { return starts(url, "https://packsmc.com/pack/") && url.find_first_of("\r\n") == string::npos; }
    inline string browse(const string& query = "") { return "https://packsmc.com/showcase" + (query.empty() ? "" : "?q=" + Net::enc(query)); }
    inline fs::path importZipTo(const fs::path& file, const fs::path& directory) {
        try {
            if (lower(S(file.extension())) != ".zip" || fs::file_size(file)>512ull*1024*1024) return {};
            Zip zip; if(!zip.open(file)) return {};
            auto* entry=zip.find("pack.mcmeta"); string metadata;
            if(!entry || entry->usize>1024*1024 || !zip.read(*entry,metadata) || !Json::parse(metadata).has("pack")) return {};
            fs::create_directories(directory); auto target=directory/file.filename();
            if(fs::exists(target) && fs::equivalent(file,target)) return target;
            for(int n=1;fs::exists(target);++n) target=directory/(file.stem().wstring()+L" ("+std::to_wstring(n)+L")"+file.extension().wstring());
            return fs::copy_file(file,target) ? target : fs::path();
        }catch(...) {return {};}
    }
    inline bool importZip(const fs::path& file, const fs::path& directory) { return !importZipTo(file, directory).empty(); }
    // The player's Downloads folder (where the browser saves what is downloaded from packsmc.com)
    inline fs::path downloadsDir() {
        PWSTR p = nullptr;
        fs::path out;
        if (SUCCEEDED(SHGetKnownFolderPath(FOLDERID_Downloads, 0, nullptr, &p)) && p) out = p;
        if (p) CoTaskMemFree(p);
        if (out.empty()) out = homeDir() / L"Downloads";
        return out;
    }
    inline vector<Modrinth::Hit> results(const Json& response) {
        vector<Modrinth::Hit> out;
        for (const auto& p : response["data"].a) {
            string url = p.str("download_url"), id = p.str("id");
            if (id.empty() || !page(url)) continue;
            Modrinth::Hit h;
            h.id = "packsmc:" + id; h.slug = p.str("slug"); h.title = p.str("name");
            h.iconUrl = p.str("thumbnail_url"); h.author = p["author"].str("username");
            h.description = h.author + (p.str("resolution").empty() ? "" : " · " + p.str("resolution"));
            if (!p.str("description").empty()) h.description += " · " + p.str("description");
            h.downloads = p.num("downloads"); h.kind = "resourcepack"; h.source = "packsmc"; h.webpage = url;
            for (const auto& image : p["gallery"].a) if (starts(image.s,"https://") && h.gallery.size() < 8) h.gallery.push_back(image.s);
            if (h.gallery.empty() && starts(h.iconUrl,"https://")) h.gallery.push_back(h.iconUrl);
            out.push_back(h);
        }
        return out;
    }
    inline vector<Modrinth::Hit> search(const string& query, const string& category) {
        string q = trim(query + (category.empty() ? "" : " " + category));
        string url = Config::base + "/api/v1/packsmc?limit=60&sort=downloads";
        if (W(q).size() >= 2) url += "&q=" + Net::enc(q);
        auto response = Net::request("GET", url, {}, "", nullptr, 10);
        if (response.status == 429) throw std::runtime_error("PacksMC: слишком много запросов, попробуй через минуту");
        if (response.status != 200) throw std::runtime_error("PacksMC сейчас недоступен, попробуй позже");
        Json json = Json::parse(response.body);
        if (!json.has("data")) throw std::runtime_error("PacksMC returned an invalid catalog");
        return results(json);
    }
}

