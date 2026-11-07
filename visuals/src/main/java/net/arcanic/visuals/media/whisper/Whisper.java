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
package net.arcanic.visuals.media.whisper;

import com.sun.management.HotSpotDiagnosticMXBean;
import io.github.givimad.whisperjni.WhisperContext;
import io.github.givimad.whisperjni.WhisperFullParams;
import io.github.givimad.whisperjni.WhisperJNI;
import io.github.givimad.whisperjni.WhisperSamplingStrategy;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hears the words of the song playing on the PC, after EvoVisuals' Whisper transcriber: the system audio is taken
 * down to 16 kHz mono into a 30 s ring, cut into 4–7 s chunks at the quietest moment, and each chunk is run through
 * whisper.cpp on a worker thread. Recognised phrases come out as timed segments. The worker unloads the model 20 s
 * after the last request, or at once when stopped.
 */
public final class Whisper {
    public enum State {
        IDLE,
        DOWNLOADING,
        LOADING,
        LISTENING,
        FAILED,
        UNSUPPORTED_CPU
    }

    /** One recognised phrase, stamped with the System.nanoTime() span it was heard in. */
    public record Segment(long sequence, String trackKey, String text, long startNanos, long endNanos) {}

    private record Chunk(String trackKey, int length, long startNanos) {}

    private static final Logger LOG = LoggerFactory.getLogger("ArcanicWhisper");
    private static final int SAMPLE_RATE = 16_000;
    private static final int RING_SAMPLES = SAMPLE_RATE * 30;
    private static final int MIN_CHUNK_SAMPLES = 64_000;
    private static final int MAX_CHUNK_SAMPLES = 112_000;
    private static final int CUT_FRAME_SAMPLES = 1333;
    private static final int RESAMPLE_SCRATCH = 8192;
    private static final long IDLE_UNLOAD_NANOS = 20_000_000_000L;
    private static final long WAIT_MS = 120;
    private static final float SILENCE_RMS = 0.006F;
    private static final float TARGET_PEAK = 0.9F;
    private static final float MAX_GAIN = 8.0F;
    private static final float NO_SPEECH_THRESHOLD = 0.6F;
    private static final int MAX_QUEUED_SEGMENTS = 32;
    private static final int MAX_WORKER_THREADS = 4;
    private static final int RESERVED_CORES = 2;
    private static final String AUTO = "auto";
    private static final long RETRY_AFTER_FAILURE_NANOS = 30_000_000_000L;

    private static final Object LOCK = new Object();
    private static final float[] RING = new float[RING_SAMPLES];
    private static final float[] RESAMPLED = new float[RESAMPLE_SCRATCH];
    private static final float[] CHUNK = new float[MAX_CHUNK_SAMPLES];
    private static final Resampler RESAMPLER = new Resampler(SAMPLE_RATE);
    private static final Deque<Segment> SEGMENTS = new ArrayDeque<>();
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final boolean CPU_OK = probeCpu();
    private static boolean nativeLoaded;
    private static long written, consumed, lastSampleNanos, sequence;
    private static String trackKey = "";
    private static volatile State state = State.IDLE;
    private static volatile float downloadProgress;
    private static volatile long lastRequestNanos;
    private static volatile long failedAtNanos;
    private static volatile boolean stopped;
    private static volatile Thread worker;
    private static volatile WhisperModel requestedModel = WhisperModel.BASE;
    private static volatile String requestedLanguage = AUTO;

    private Whisper() {}

    public static State state() {
        return state;
    }

    public static float downloadProgress() {
        return downloadProgress;
    }

    /** Keeps recognition going for the track, starting the audio tap and the worker if needed. */
    public static void request(String key, WhisperModel model, String language) {
        if (!CPU_OK) {
            state = State.UNSUPPORTED_CPU;
            return;
        }
        stopped = false;
        lastRequestNanos = System.nanoTime();
        requestedLanguage = language != null && !language.isEmpty() ? language : AUTO;
        synchronized (LOCK) {
            if (!key.equals(trackKey)) {
                trackKey = key;
                consumed = written;
                RESAMPLER.reset();
            }
        }
        if (model != requestedModel) {
            requestedModel = model;
            if (RUNNING.get()) state = State.LOADING;
        }
        // A failed start (no network, broken model) is retried after a pause rather than every frame.
        if (state == State.FAILED && System.nanoTime() - failedAtNanos < RETRY_AFTER_FAILURE_NANOS) return;
        SystemAudio.sink(Whisper::accept);
        SystemAudio.request();
        if (RUNNING.compareAndSet(false, true)) {
            Thread t = new Thread(Whisper::run, "arcanic-whisper");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY + 1);
            worker = t;
            t.start();
        }
    }

    /** Stops listening and unloads the model (used when the module or recognition is switched off). */
    public static void stop() {
        stopped = true;
        SystemAudio.sink(null);
        SystemAudio.stop();
        Thread t = worker;
        if (t != null && state == State.DOWNLOADING) t.interrupt();
    }

    /** The first segment newer than {@code after}, or null. */
    public static Segment poll(long after) {
        synchronized (LOCK) {
            for (Segment s : SEGMENTS) if (s.sequence() > after) return s;
            return null;
        }
    }

    private static void accept(float[] samples, int count, int rate) {
        if (!RUNNING.get() || rate <= 0) return;
        long now = System.nanoTime();
        for (int off = 0; off < count; ) {
            int n = Math.min(count - off, RESAMPLE_SCRATCH);
            int out = RESAMPLER.process(samples, off, n, rate, RESAMPLED, 0, RESAMPLE_SCRATCH);
            synchronized (LOCK) {
                for (int i = 0; i < out; i++) RING[(int) (written++ % RING_SAMPLES)] = RESAMPLED[i];
                if (written - consumed > RING_SAMPLES) consumed = written - RING_SAMPLES;
                lastSampleNanos = now;
            }
            off += n;
        }
    }

    private static void run() {
        WhisperJNI jni = null;
        WhisperContext ctx = null;
        WhisperModel loaded = null;
        try {
            while (!stopped && System.nanoTime() - lastRequestNanos < IDLE_UNLOAD_NANOS) {
                WhisperModel want = requestedModel;
                if (ctx == null || want != loaded) {
                    if (ctx != null) {
                        jni.free(ctx);
                        ctx = null;
                    }
                    Path file = prepare(want);
                    state = State.LOADING;
                    loadNative();
                    jni = new WhisperJNI();
                    ctx = jni.init(file);
                    loaded = want;
                    state = State.LISTENING;
                    LOG.info("Whisper model {} ready", want.label);
                }
                Chunk chunk = takeChunk();
                if (chunk == null) Thread.sleep(WAIT_MS);
                else transcribe(jni, ctx, chunk);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            state = State.FAILED;
            failedAtNanos = System.nanoTime();
            LOG.warn("Whisper could not start", t);
        } finally {
            if (jni != null && ctx != null) jni.free(ctx);
            SystemAudio.sink(null);
            if (state != State.FAILED) state = State.IDLE;
            worker = null;
            RUNNING.set(false);
        }
    }

    private static synchronized void loadNative() throws Exception {
        if (!nativeLoaded) {
            WhisperJNI.loadLibrary();
            nativeLoaded = true;
        }
    }

    private static Path prepare(WhisperModel model) throws Exception {
        if (model.installed()) return model.file();
        state = State.DOWNLOADING;
        downloadProgress = 0;
        return model.ensure(read -> downloadProgress = (float) read / model.sizeBytes);
    }

    /** The next 7 s of heard audio, cut at its quietest point after the first 4 s; null until there is enough. */
    private static Chunk takeChunk() {
        synchronized (LOCK) {
            if (written - consumed < MAX_CHUNK_SAMPLES) return null;
            long start = consumed;
            long end = start + quietestCut(start);
            int length = (int) (end - start);
            for (int i = 0; i < length; i++) CHUNK[i] = RING[(int) ((start + i) % RING_SAMPLES)];
            long startNanos = lastSampleNanos - (written - start) * 1_000_000_000L / SAMPLE_RATE;
            consumed = end;
            return new Chunk(trackKey, length, startNanos);
        }
    }

    private static int quietestCut(long start) {
        int best = MAX_CHUNK_SAMPLES;
        double quietest = Double.MAX_VALUE;
        for (int cut = MIN_CHUNK_SAMPLES; cut + CUT_FRAME_SAMPLES <= MAX_CHUNK_SAMPLES; cut += CUT_FRAME_SAMPLES) {
            double energy = 0;
            for (int i = 0; i < CUT_FRAME_SAMPLES; i++) {
                float v = RING[(int) ((start + cut + i) % RING_SAMPLES)];
                energy += v * v;
            }
            if (energy < quietest) {
                quietest = energy;
                best = cut + CUT_FRAME_SAMPLES / 2;
            }
        }
        return best;
    }

    private static void transcribe(WhisperJNI jni, WhisperContext ctx, Chunk chunk) {
        double sum = 0;
        float peak = 0;
        for (int i = 0; i < chunk.length(); i++) {
            float v = CHUNK[i];
            sum += v * v;
            peak = Math.max(peak, Math.abs(v));
        }
        if (Math.sqrt(sum / chunk.length()) < SILENCE_RMS) return;
        float gain = Math.min(MAX_GAIN, TARGET_PEAK / Math.max(peak, 1.0E-6F));
        for (int i = 0; i < chunk.length(); i++) CHUNK[i] *= gain;

        WhisperFullParams p = new WhisperFullParams(WhisperSamplingStrategy.GREEDY);
        p.nThreads =
                Math.max(1, Math.min(MAX_WORKER_THREADS, Runtime.getRuntime().availableProcessors() - RESERVED_CORES));
        p.language = requestedLanguage;
        p.noContext = true;
        p.singleSegment = false;
        p.suppressBlank = true;
        p.suppressNonSpeechTokens = true;
        p.printProgress = false;
        p.printRealtime = false;
        p.printTimestamps = false;
        p.printSpecial = false;
        p.temperature = 0.0F;
        p.noSpeechThold = NO_SPEECH_THRESHOLD;
        int code = jni.full(ctx, p, CHUNK, chunk.length());
        if (code != 0) {
            LOG.warn("Whisper chunk failed with code {}", code);
            return;
        }
        int n = jni.fullNSegments(ctx);
        for (int i = 0; i < n; i++) {
            String text = clean(jni.fullGetSegmentText(ctx, i));
            if (text.isEmpty()) continue;
            // whisper.cpp stamps are in centiseconds from the chunk start
            long from = chunk.startNanos() + jni.fullGetSegmentTimestamp0(ctx, i) * 10_000_000L;
            long to = chunk.startNanos() + jni.fullGetSegmentTimestamp1(ctx, i) * 10_000_000L;
            publish(chunk.trackKey(), text, from, Math.max(to, from));
        }
    }

    private static void publish(String key, String text, long from, long to) {
        synchronized (LOCK) {
            Segment last = SEGMENTS.peekLast();
            if (last != null && last.trackKey().equals(key) && last.text().equalsIgnoreCase(text)) return;
            SEGMENTS.addLast(new Segment(++sequence, key, text, from, to));
            while (SEGMENTS.size() > MAX_QUEUED_SEGMENTS) SEGMENTS.removeFirst();
        }
    }

    // ---- hallucination filter ----

    private static final Pattern BLOCKED = Pattern.compile(
            "(?iu)субтитр|продолжение следует|редактор|корректор|подпис(ывай|ать)ся|ставьте лайк|спасибо за просмотр"
                    + "|thanks? for watching|subscribe|subtitles? by|amara\\.org|www\\.|\\.com|\\.ru|dimatorzok");
    private static final Pattern NON_SPEECH = Pattern.compile("^[\\s\\p{Punct}♪♫♬*…\\-–—]*$");
    private static final Pattern BRACKETED = Pattern.compile("^\\s*[\\[(*♪].*[\\])*♪]\\s*$");
    private static final Pattern WORDS = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final int MIN_LETTERS = 2;
    private static final int MAX_REPEAT_RUN = 4;

    /** Drops what Whisper makes up over music: credits, sound tags, punctuation and stuck repeats. */
    static String clean(String raw) {
        if (raw == null) return "";
        String s = raw.replace('\n', ' ').strip();
        if (s.isEmpty()
                || NON_SPEECH.matcher(s).matches()
                || BRACKETED.matcher(s).matches()) return "";
        if (BLOCKED.matcher(s).find()) return "";
        int letters = 0;
        for (int i = 0; i < s.length(); i++) if (Character.isLetter(s.charAt(i))) letters++;
        if (letters < MIN_LETTERS || repeats(s)) return "";
        return s;
    }

    private static boolean repeats(String s) {
        Matcher m = WORDS.matcher(s.toLowerCase());
        String prev = null;
        int run = 1;
        while (m.find()) {
            String w = m.group();
            if (w.equals(prev)) {
                if (++run > MAX_REPEAT_RUN) return true;
            } else {
                run = 1;
                prev = w;
            }
        }
        return false;
    }

    /** whisper.cpp's x86 build needs AVX2. */
    private static boolean probeCpu() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (!arch.equals("amd64") && !arch.equals("x86_64")) return true;
        try {
            HotSpotDiagnosticMXBean bean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
            int avx = Integer.parseInt(bean.getVMOption("UseAVX").getValue());
            if (avx < 2) {
                LOG.warn("Whisper disabled: CPU lacks AVX2 (UseAVX={})", avx);
                return false;
            }
            return true;
        } catch (Throwable t) {
            LOG.warn("Whisper CPU probe failed", t);
            return false;
        }
    }

    /** Box-averaging downsampler to the model's 16 kHz. */
    private static final class Resampler {
        private final int target;
        private int source;
        private double step, phase, sum;
        private int count;

        Resampler(int target) {
            this.target = target;
        }

        int process(float[] in, int off, int len, int rate, float[] out, int outOff, int max) {
            if (rate != source) {
                source = rate;
                step = (double) rate / target;
                phase = 0;
                sum = 0;
                count = 0;
            }
            int n = 0;
            for (int i = 0; i < len && n < max; i++) {
                sum += in[off + i];
                count++;
                phase++;
                if (phase >= step) {
                    phase -= step;
                    out[outOff + n++] = (float) (sum / count);
                    sum = 0;
                    count = 0;
                }
            }
            return n;
        }

        void reset() {
            phase = 0;
            sum = 0;
            count = 0;
        }
    }
}
