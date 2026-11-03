// Arcanic media helper, compiled on the player's PC by the .NET Framework's own csc.exe (no launcher needed).
// It reads the Windows media session (Spotify, Yandex Music, browsers: whatever the volume flyout shows) and prints
// one line per update in the launcher's --media format:
//   status \t artist \t title \t position ms \t duration ms \t artwork id \t sampled at (unix ms)
// The cover goes to the artwork path as PNG; "toggle", "next" and "prev" lines in the commands file control playback.
using System;
using System.Diagnostics;
using System.IO;
using System.Text;
using System.Threading;
using Windows.Foundation;
using Windows.Media.Control;
using Windows.Storage.Streams;

static class MediaHelper {
    static string lastKey = "";
    static string coverId = "";
    static int commandsRead;
    // the cover is read again a few times after a track starts: players often post the new title before its
    // thumbnail, or no thumbnail at all for the first moment
    static readonly int[] CoverRetryMs = { 700, 1600, 3200, 6000, 10000 };
    static readonly Stopwatch trackClock = new Stopwatch();
    static int coverTries;
    // our own timeline: apps stamp their position differently, see Position()
    static readonly Stopwatch anchorClock = Stopwatch.StartNew();
    static long anchorPosition, lastRaw = -1, lastStamp = -1;

    // System.Runtime.WindowsRuntime's AsTask needs the unified Windows.winmd, which only the SDK has: wait by hand
    static T Await<T>(IAsyncOperation<T> op, int ms) {
        var clock = Stopwatch.StartNew();
        while (op.Status == AsyncStatus.Started) {
            if (clock.ElapsedMilliseconds > ms) {
                op.Cancel();
                throw new TimeoutException();
            }
            Thread.Sleep(4);
        }
        if (op.Status != AsyncStatus.Completed) throw new InvalidOperationException(op.Status.ToString());
        return op.GetResults();
    }

    static int Main(string[] args) {
        if (args.Length < 3) return 2;
        string artwork = args[0], commands = args[1];
        int parent;
        int.TryParse(args[2], out parent);
        // a window-less exe has no console to set an encoding on: write UTF-8 to the pipe directly
        var stdout = new StreamWriter(Console.OpenStandardOutput(), new UTF8Encoding(false));
        stdout.AutoFlush = true;
        Console.SetOut(stdout);
        GlobalSystemMediaTransportControlsSessionManager manager;
        try {
            manager = Await(GlobalSystemMediaTransportControlsSessionManager.RequestAsync(), 5000);
        } catch (Exception e) {
            Console.WriteLine("arcanic-media-fail manager " + e.Message);
            return 1;
        }
        while (true) {
            if (parent > 0 && !Alive(parent)) return 0;
            GlobalSystemMediaTransportControlsSession session = Pick(manager);
            Commands(commands, session);
            string line = "none\t\t\t0\t0\t";
            if (session != null) {
                try { line = Describe(session, artwork); } catch { }
            }
            Console.WriteLine(line);
            Console.Out.Flush();
            Thread.Sleep(250);
        }
    }

    static bool Alive(int pid) {
        try { return !Process.GetProcessById(pid).HasExited; } catch { return false; }
    }

    // the session that plays wins over the one Windows calls current: a paused tab must not hide Spotify
    static GlobalSystemMediaTransportControlsSession Pick(GlobalSystemMediaTransportControlsSessionManager manager) {
        try {
            foreach (var s in manager.GetSessions()) {
                if (s.GetPlaybackInfo().PlaybackStatus == GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing) return s;
            }
            return manager.GetCurrentSession();
        } catch { return null; }
    }

    static void Commands(string path, GlobalSystemMediaTransportControlsSession session) {
        try {
            if (!File.Exists(path)) return;
            string[] lines = File.ReadAllLines(path);
            for (int i = commandsRead; i < lines.Length; i++) {
                if (session == null) continue;
                switch (lines[i].Trim()) {
                    case "toggle": Await(session.TryTogglePlayPauseAsync(), 3000); break;
                    case "next": Await(session.TrySkipNextAsync(), 3000); break;
                    case "prev": Await(session.TrySkipPreviousAsync(), 3000); break;
                }
            }
            commandsRead = lines.Length;
        } catch { }
    }

    static string Clean(string s) {
        return s == null ? "" : s.Replace('\t', ' ').Replace('\r', ' ').Replace('\n', ' ').Trim();
    }

    static string Describe(GlobalSystemMediaTransportControlsSession session, string artwork) {
        var props = Await(session.TryGetMediaPropertiesAsync(), 2000);
        var info = session.GetPlaybackInfo();
        var timeline = session.GetTimelineProperties();
        string status = info.PlaybackStatus == GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing ? "playing"
                : info.PlaybackStatus == GlobalSystemMediaTransportControlsSessionPlaybackStatus.Paused ? "paused" : "none";
        string artist = Clean(props.Artist);
        if (artist.Length == 0) artist = Clean(props.AlbumArtist);
        string title = Clean(props.Title);
        // a browser tab without metadata reports only "<browser> is playing media": not a track
        if (artist.Length == 0 && (title.EndsWith("is playing media") || title.EndsWith("воспроизводит медиа"))) status = "none";
        long duration = (long)(timeline.EndTime - timeline.StartTime).TotalMilliseconds;
        string key = artist + "|" + title;
        bool changed = key != lastKey;
        long position = Position(timeline, status == "playing", changed);
        position = duration > 0 ? Math.Max(0, Math.Min(position, duration)) : 0;
        if (changed) {
            lastKey = key;
            coverId = "";
            coverTries = 0;
            trackClock.Restart();
            TryCover(props, artwork, key);
        } else if (coverTries < CoverRetryMs.Length && trackClock.ElapsedMilliseconds >= CoverRetryMs[coverTries]) {
            coverTries++;
            TryCover(props, artwork, key);
        }
        long sampled = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
        return status + "\t" + artist + "\t" + title + "\t" + position + "\t" + duration + "\t" + coverId + "\t" + sampled;
    }

    static void TryCover(GlobalSystemMediaTransportControlsSessionMediaProperties props, string artwork, string key) {
        try { if (SaveCover(props, artwork)) coverId = ((uint)key.GetHashCode()).ToString(); } catch { }
    }

    // Where the song is now. An app that stamps its position (LastUpdatedTime moves with it) is measured from its
    // stamp; an app that moves the position under an old stamp is taken as fresh the moment we see it change, or
    // adding the time since the stale stamp would run the song ahead; between changes our own clock runs on.
    static long Position(GlobalSystemMediaTransportControlsSessionTimelineProperties timeline, bool playing, bool newTrack) {
        long raw = (long)timeline.Position.TotalMilliseconds;
        long stamp = timeline.LastUpdatedTime.Ticks;
        if (newTrack || stamp != lastStamp) {
            double since = stamp > 0 ? (DateTimeOffset.Now - timeline.LastUpdatedTime).TotalMilliseconds : 0;
            anchorPosition = raw + (playing && since > 0 && since < 600000 ? (long)since : 0);
            anchorClock.Restart();
        } else if (raw != lastRaw || !playing) {
            anchorPosition = raw;
            anchorClock.Restart();
        }
        lastRaw = raw;
        lastStamp = stamp;
        return anchorPosition + (playing ? anchorClock.ElapsedMilliseconds : 0);
    }

    static bool SaveCover(GlobalSystemMediaTransportControlsSessionMediaProperties props, string path) {
        if (props.Thumbnail == null || string.IsNullOrEmpty(path)) return false;
        IRandomAccessStreamWithContentType stream = Await(props.Thumbnail.OpenReadAsync(), 3000);
        try {
            uint size = (uint)Math.Min(stream.Size, 8u << 20);
            if (size == 0) return false;
            var reader = new DataReader(stream.GetInputStreamAt(0));
            uint got = Await(reader.LoadAsync(size), 3000);
            byte[] bytes = new byte[got];
            reader.ReadBytes(bytes);
            using (var memory = new MemoryStream(bytes))
            using (var image = System.Drawing.Image.FromStream(memory)) {
                string tmp = path + ".tmp";
                image.Save(tmp, System.Drawing.Imaging.ImageFormat.Png);
                if (File.Exists(path)) File.Delete(path);
                File.Move(tmp, path);
            }
        } finally {
            stream.Dispose();
        }
        return true;
    }
}
