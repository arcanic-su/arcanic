/*
 * This file is part of Arcanic Visuals (https://github.com/arcanic-su/arcanic)
 *
 * Copyright (c) 2026 Arcanic
 *
 * Arcanic Visuals is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Arcanic Visuals is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Arcanic Visuals. If not, see <https://www.gnu.org/licenses/>.
 */
package net.arcanic.visuals.fx.lyrics;

/**
 * A smooth song position for the lyrics. The media session's reports always arrive late, and late by a different
 * amount each time: the reader polls, some players stamp their position only in whole steps, a report waits in the
 * pipe. So a report is never ahead of the song, only behind it by something between nothing and a few hundred ms.
 * This clock runs at real speed on its own and keeps, over the last few seconds, the report that was least late (the
 * one furthest ahead of our clock); it eases toward that one at most 30% faster or slower than real time, so the
 * words neither lag nor rush and never jerk. A seek, a new track, a pause or a resume moves it at once.
 */
public final class SongClock {
    private static final double SEEK_MS = 1000.0;
    private static final long WINDOW_NS = 3_000_000_000L;
    private static final double MAX_RATE_CHANGE = 0.3;
    private static final double CATCH_UP_MS = 500.0;
    private static final int SAMPLES = 64;

    private String key = "";
    private boolean playing;
    /** Song position minus our monotonic clock, in ms: the song position is {@code now + offset}. */
    private double offset;

    private final long[] sampleAt = new long[SAMPLES];
    private final double[] sample = new double[SAMPLES];
    private int samples;
    private int next;
    private double lastSeen = Double.NaN;
    private boolean jumped;

    /** Advances to {@code now} given the raw reported position; returns the smoothed position in ms. */
    public long update(String trackKey, boolean isPlaying, long reported, long now) {
        jumped = false;
        double local = now / 1_000_000.0;
        if (!trackKey.equals(key) || isPlaying != playing) {
            key = trackKey;
            playing = isPlaying;
            jump(reported, local);
            return reported;
        }
        if (!isPlaying) {
            if (Math.abs(reported - (local + offset)) > 1) jump(reported, local);
            return reported;
        }
        double seen = reported - local;
        if (Math.abs(seen - offset) > SEEK_MS) {
            jump(reported, local);
            return reported;
        }
        // the extrapolated report stands still against our clock between two reports: keep each new one once
        if (Double.isNaN(lastSeen) || Math.abs(seen - lastSeen) > 1.0) {
            lastSeen = seen;
            sampleAt[next] = now;
            sample[next] = seen;
            next = (next + 1) % SAMPLES;
            samples = Math.min(samples + 1, SAMPLES);
        }
        double best = seen;
        for (int i = 0; i < samples; i++) {
            if (now - sampleAt[i] <= WINDOW_NS) best = Math.max(best, sample[i]);
        }
        double frame = Math.min(100.0, Math.max(0.0, local - lastLocal));
        lastLocal = local;
        double step = (best - offset) * Math.min(1.0, frame / CATCH_UP_MS);
        double limit = frame * MAX_RATE_CHANGE;
        offset += Math.max(-limit, Math.min(limit, step));
        return Math.round(local + offset);
    }

    private double lastLocal;

    private void jump(long to, double local) {
        offset = to - local;
        lastLocal = local;
        samples = 0;
        lastSeen = Double.NaN;
        jumped = true;
    }

    /** Whether the last update moved the clock at once (new track, seek, pause or resume). */
    public boolean jumped() {
        return jumped;
    }
}
