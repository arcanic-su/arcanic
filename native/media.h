// What is playing in Windows, for the music player on the client's Dynamic Island.
// The client starts "Arcanic.exe --media <cover> <commands> <pid>" and reads one line a second from stdout:
//   status \t artist \t title \t position ms \t duration ms \t cover path (empty until the picture is saved)
// Commands (next, prev, toggle) come in the other way, one per line appended to <commands>.
// We exit when the game does.
#pragma once
#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Foundation.Collections.h>
#include <winrt/Windows.Media.Control.h>
#include <winrt/Windows.Storage.Streams.h>

namespace Media {
    using namespace winrt::Windows::Media::Control;
    using namespace winrt::Windows::Storage::Streams;
    using Session = GlobalSystemMediaTransportControlsSession;
    using Status = GlobalSystemMediaTransportControlsSessionPlaybackStatus;

    inline void say(const string& line) {
        string l = line + "\n";
        DWORD written = 0;
        WriteFile(GetStdHandle(STD_OUTPUT_HANDLE), l.data(), (DWORD)l.size(), &written, nullptr);
    }
    inline string field(const winrt::hstring& s) {
        string out = U(wstring(s));
        for (char& c : out)
            if (c == '\t' || c == '\r' || c == '\n') c = ' ';
        return out;
    }
    inline int64_t ms(winrt::Windows::Foundation::TimeSpan t) {
        return std::chrono::duration_cast<std::chrono::milliseconds>(t).count();
    }
    inline bool playing(const Session& s) {
        return s.GetPlaybackInfo().PlaybackStatus() == Status::Playing;
    }

    // keeps to the app it showed last while that app plays; otherwise whatever plays now, then the system's pick
    inline Session pick(const GlobalSystemMediaTransportControlsSessionManager& manager, const winrt::hstring& last) {
        Session kept{nullptr}, first{nullptr};
        for (const auto& s : manager.GetSessions()) {
            if (s.SourceAppUserModelId() == last) kept = s;
            if (!first && playing(s)) first = s;
        }
        if (kept && playing(kept)) return kept;
        if (first) return first;
        return kept ? kept : manager.GetCurrentSession();
    }

    inline void control(const Session& s, const string& command) {
        auto info = s.GetPlaybackInfo();
        auto can = info.Controls();
        if (command == "next" && can.IsNextEnabled())
            s.TrySkipNextAsync().get();
        else if (command == "prev" && can.IsPreviousEnabled())
            s.TrySkipPreviousAsync().get();
        else if (command == "toggle") {
            if (can.IsPlayPauseToggleEnabled())
                s.TryTogglePlayPauseAsync().get();
            else if (playing(s)) {
                if (can.IsPauseEnabled()) s.TryPauseAsync().get();
            } else if (can.IsPlayEnabled())
                s.TryPlayAsync().get();
        }
    }

    /** -1 when the command line is not ours; otherwise runs until the game closes. */
    inline int helper(int argc, LPWSTR* argv) {
        if (argc != 5 || wcscmp(argv[1], L"--media")) return -1;
        fs::path cover = argv[2], commands = argv[3];
        HANDLE game = OpenProcess(SYNCHRONIZE, FALSE, (DWORD)_wtoi(argv[4]));
        if (!game) return 1;
        winrt::init_apartment();
        GlobalSystemMediaTransportControlsSessionManager manager{nullptr};
        try {
            manager = GlobalSystemMediaTransportControlsSessionManager::RequestAsync().get();
        } catch (...) {
            say("arcanic-media-fail stage=request");
            return 1;
        }

        winrt::hstring last;
        string lastKey;
        bool haveCover = false;
        int coverTries = 0;
        size_t read = 0;
        while (WaitForSingleObject(game, 0) != WAIT_OBJECT_0) {
            Session session{nullptr};
            string line;
            try {
                session = pick(manager, last);
                if (session) {
                    last = session.SourceAppUserModelId();
                    auto props = session.TryGetMediaPropertiesAsync().get();
                    auto time = session.GetTimelineProperties();
                    string artist = field(props.Artist()), title = field(props.Title()), key = artist + "|" + title;
                    if (key != lastKey) {
                        lastKey = key;
                        haveCover = false;
                        coverTries = 0;
                    }
                    if (!haveCover && coverTries++ < 5 && props.Thumbnail()) {
                        auto stream = props.Thumbnail().OpenReadAsync().get();
                        uint64_t size = stream.Size();
                        if (size > 0 && size <= 4 << 20) {
                            Buffer buffer((uint32_t)size);
                            auto got = stream.ReadAsync(buffer, (uint32_t)size, InputStreamOptions::None).get();
                            haveCover = writeFile(cover, got.data(), got.Length());
                        }
                    }
                    auto status = session.GetPlaybackInfo().PlaybackStatus();
                    line = string(status == Status::Playing  ? "Playing"
                                  : status == Status::Paused ? "Paused"
                                                             : "Stopped") +
                           "\t" + artist + "\t" + title + "\t" + std::to_string(ms(time.Position())) + "\t" +
                           std::to_string(ms(time.EndTime() - time.StartTime())) + "\t" + (haveCover ? S(cover) : "");
                }
            } catch (...) {
                line.clear();
            }
            say(line);

            // a second for a command from the client, then the next report
            string command;
            ULONGLONG until = GetTickCount64() + 1000;
            while (command.empty() && GetTickCount64() < until) {
                string text;
                if (readFile(commands, text)) {
                    auto lines = split(text, "\r\n");
                    if (read < lines.size()) command = trim(lines[read++]);
                }
                if (command.empty()) Sleep(40);
            }
            if (!command.empty() && session) {
                try {
                    control(session, command);
                } catch (...) {}
                Sleep(150);
            }
        }
        return 0;
    }
}  // namespace Media

