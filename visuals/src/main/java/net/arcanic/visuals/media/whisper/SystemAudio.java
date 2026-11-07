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

import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Guid.CLSID;
import com.sun.jna.platform.win32.Guid.IID;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * What the PC is playing, as mono PCM, after EvoVisuals' audio tap: a WASAPI loopback capture of the default output
 * device on its own thread. It runs while someone keeps asking for it and stops itself 3.5 s after the last request.
 */
public final class SystemAudio {
    /** Receives mono samples (-1..1) at the device's sample rate. */
    @FunctionalInterface
    public interface Sink {
        void accept(float[] samples, int count, int sampleRate);
    }

    private static final String CLSID_MM_DEVICE_ENUMERATOR = "{BCDE0395-E52F-467C-8E3D-C4579291692E}";
    private static final String IID_IMM_DEVICE_ENUMERATOR = "{A95664D2-9614-4F35-A746-DE8DB63617E6}";
    private static final String IID_IAUDIO_CLIENT = "{1CB9AD4C-DBFA-4C32-B178-C2F568A703B2}";
    private static final String IID_IAUDIO_CAPTURE_CLIENT = "{C8ADBD64-E71E-48A0-A4DE-185C395CD317}";
    private static final int CLSCTX_ALL = 7;
    private static final int DATA_FLOW_RENDER = 0;
    private static final int ROLE_CONSOLE = 0;
    private static final int SHAREMODE_SHARED = 0;
    private static final int STREAMFLAGS_LOOPBACK = 0x20000;
    private static final int BUFFERFLAGS_SILENT = 2;
    private static final long BUFFER_DURATION = 10_000_000L;
    private static final int VTBL_RELEASE = 2;
    private static final int VTBL_ENUM_GET_DEFAULT_ENDPOINT = 4;
    private static final int VTBL_DEVICE_ACTIVATE = 3;
    private static final int VTBL_CLIENT_INITIALIZE = 3;
    private static final int VTBL_CLIENT_GET_MIX_FORMAT = 8;
    private static final int VTBL_CLIENT_START = 10;
    private static final int VTBL_CLIENT_STOP = 11;
    private static final int VTBL_CLIENT_GET_SERVICE = 14;
    private static final int VTBL_CAPTURE_GET_BUFFER = 3;
    private static final int VTBL_CAPTURE_RELEASE_BUFFER = 4;
    private static final int VTBL_CAPTURE_GET_NEXT_PACKET = 5;
    private static final short FORMAT_IEEE_FLOAT = 3;
    private static final short FORMAT_EXTENSIBLE = -2;
    private static final long CAPTURE_SLEEP_MS = 15;
    private static final long RECONNECT_SLEEP_MS = 1000;
    private static final long IDLE_SHUTDOWN_MS = 3500;

    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("win");
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static volatile Sink sink;
    private static volatile long lastRequestMs;
    private static volatile boolean capturing;
    private static float[] tap = new float[1024];

    private SystemAudio() {}

    /** Loopback capture needs Windows. */
    public static boolean supported() {
        return WINDOWS;
    }

    public static boolean capturing() {
        return capturing;
    }

    public static void sink(Sink s) {
        sink = s;
    }

    /** Keeps the capture running for another few seconds, starting it if needed. */
    public static void request() {
        lastRequestMs = System.currentTimeMillis();
        if (WINDOWS && RUNNING.compareAndSet(false, true)) {
            Thread t = new Thread(SystemAudio::run, "arcanic-audio-tap");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 3);
            t.start();
        }
    }

    /** Stops the capture within a frame of its loop. */
    public static void stop() {
        lastRequestMs = 0;
    }

    private static void run() {
        HRESULT init = Ole32.INSTANCE.CoInitializeEx(null, 0);
        try {
            while (RUNNING.get() && System.currentTimeMillis() - lastRequestMs <= IDLE_SHUTDOWN_MS) session();
        } catch (Throwable t) {
            System.err.println("[ArcanicVisuals] system audio capture failed: " + t);
        } finally {
            capturing = false;
            RUNNING.set(false);
            if (init != null && init.intValue() == 0) Ole32.INSTANCE.CoUninitialize();
        }
    }

    private static Function method(Pointer object, int index) {
        Pointer vtbl = object.getPointer(0);
        return Function.getFunction(vtbl.getPointer((long) index * Native.POINTER_SIZE), Function.ALT_CONVENTION);
    }

    private static IID iid(String s) {
        IID id = new IID(s);
        id.write();
        return id;
    }

    /** One connection to the default output device, until it fails or nobody asks any more. */
    private static void session() {
        PointerByReference ref = new PointerByReference();
        HRESULT hr = Ole32.INSTANCE.CoCreateInstance(
                new CLSID(CLSID_MM_DEVICE_ENUMERATOR), null, CLSCTX_ALL, new IID(IID_IMM_DEVICE_ENUMERATOR), ref);
        if (hr == null || hr.intValue() != 0) {
            sleep(RECONNECT_SLEEP_MS);
            return;
        }
        Pointer enumerator = ref.getValue(), device = null, client = null, capture = null;
        try {
            PointerByReference out = new PointerByReference();
            if (method(enumerator, VTBL_ENUM_GET_DEFAULT_ENDPOINT)
                            .invokeInt(new Object[] {enumerator, DATA_FLOW_RENDER, ROLE_CONSOLE, out})
                    != 0) {
                sleep(RECONNECT_SLEEP_MS);
                return;
            }
            device = out.getValue();

            IID clientId = iid(IID_IAUDIO_CLIENT);
            PointerByReference clientRef = new PointerByReference();
            if (method(device, VTBL_DEVICE_ACTIVATE)
                            .invokeInt(new Object[] {device, clientId.getPointer(), CLSCTX_ALL, null, clientRef})
                    != 0) {
                sleep(RECONNECT_SLEEP_MS);
                return;
            }
            client = clientRef.getValue();

            PointerByReference formatRef = new PointerByReference();
            if (method(client, VTBL_CLIENT_GET_MIX_FORMAT).invokeInt(new Object[] {client, formatRef}) != 0) {
                sleep(RECONNECT_SLEEP_MS);
                return;
            }
            Pointer format = formatRef.getValue();
            short tag = format.getShort(0);
            short channels = format.getShort(2);
            int sampleRate = format.getInt(4);
            short blockAlign = format.getShort(12);
            short bits = format.getShort(14);
            short extra = format.getShort(16);
            short sub = tag;
            if (tag == FORMAT_EXTENSIBLE && extra >= 8) sub = format.getShort(24);
            boolean floats = sub == FORMAT_IEEE_FLOAT || bits == 32;

            if (method(client, VTBL_CLIENT_INITIALIZE).invokeInt(new Object[] {
                        client, SHAREMODE_SHARED, STREAMFLAGS_LOOPBACK, BUFFER_DURATION, 0L, format, null
                    })
                    != 0) {
                sleep(RECONNECT_SLEEP_MS);
                return;
            }

            IID captureId = iid(IID_IAUDIO_CAPTURE_CLIENT);
            PointerByReference captureRef = new PointerByReference();
            if (method(client, VTBL_CLIENT_GET_SERVICE)
                            .invokeInt(new Object[] {client, captureId.getPointer(), captureRef})
                    != 0) {
                sleep(RECONNECT_SLEEP_MS);
                return;
            }
            capture = captureRef.getValue();

            method(client, VTBL_CLIENT_START).invokeInt(new Object[] {client});
            Function nextPacket = method(capture, VTBL_CAPTURE_GET_NEXT_PACKET);
            Function getBuffer = method(capture, VTBL_CAPTURE_GET_BUFFER);
            Function releaseBuffer = method(capture, VTBL_CAPTURE_RELEASE_BUFFER);
            IntByReference packet = new IntByReference();
            PointerByReference data = new PointerByReference();
            IntByReference frames = new IntByReference();
            IntByReference flags = new IntByReference();
            int bytesPerSample = bits / 8;
            capturing = true;

            while (RUNNING.get() && System.currentTimeMillis() - lastRequestMs <= IDLE_SHUTDOWN_MS) {
                sleep(CAPTURE_SLEEP_MS);
                if (nextPacket.invokeInt(new Object[] {capture, packet}) != 0) break;
                while (packet.getValue() > 0) {
                    if (getBuffer.invokeInt(new Object[] {capture, data, frames, flags, null, null}) != 0) break;
                    int n = frames.getValue();
                    Pointer pcm = data.getValue();
                    Sink s = sink;
                    if (s != null) {
                        if (tap.length < n) tap = new float[Math.max(n, tap.length * 2)];
                        if ((flags.getValue() & BUFFERFLAGS_SILENT) == 0 && pcm != null) {
                            for (int i = 0; i < n; i++) {
                                long at = (long) i * blockAlign;
                                if (floats) {
                                    float l = pcm.getFloat(at);
                                    float r = channels > 1 ? pcm.getFloat(at + bytesPerSample) : l;
                                    tap[i] = (l + r) / 2.0F;
                                } else {
                                    short l = pcm.getShort(at);
                                    short r = channels > 1 ? pcm.getShort(at + bytesPerSample) : l;
                                    tap[i] = (l + r) / 2.0F / 32768.0F;
                                }
                            }
                        } else {
                            Arrays.fill(tap, 0, n, 0.0F);
                        }
                        s.accept(tap, n, sampleRate);
                    }
                    releaseBuffer.invokeInt(new Object[] {capture, n});
                    if (nextPacket.invokeInt(new Object[] {capture, packet}) != 0) break;
                }
            }
            method(client, VTBL_CLIENT_STOP).invokeInt(new Object[] {client});
        } finally {
            capturing = false;
            release(capture);
            release(client);
            release(device);
            release(enumerator);
        }
    }

    private static void release(Pointer object) {
        if (object == null) return;
        try {
            method(object, VTBL_RELEASE).invokeInt(new Object[] {object});
        } catch (Throwable ignored) {
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
