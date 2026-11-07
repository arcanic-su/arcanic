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
package net.arcanic.visuals.core;

import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * The line to the sync service: one TLS connection straight to the server (not through the CDN in front of the site,
 * which cuts long connections from many networks after a few kilobytes), one JSON message per text line. A thread
 * reads, a thread writes, so neither side waits for the other and nothing waits for the game.
 */
final class SyncLine {
    /** The name on the server's certificate: the site's own, checked like a browser checks it. */
    private static final String NAME = "arcanic.su";
    /** Nothing read for this long (the service answers every ping, sent every 5 s): the line is dead. */
    private static final int SILENCE = 15000;

    private final SSLSocket socket;
    private final LinkedBlockingQueue<String> out = new LinkedBlockingQueue<>(2000);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Consumer<String> onText;
    private final Consumer<SyncLine> onClose;

    private SyncLine(SSLSocket socket, Consumer<String> onText, Consumer<SyncLine> onClose) {
        this.socket = socket;
        this.onText = onText;
        this.onClose = onClose;
    }

    /** Connects to the first host that answers (a name, then an address), signs in and starts reading. */
    static SyncLine open(
            List<String> hosts, int port, String token, Consumer<String> onText, Consumer<SyncLine> onClose)
            throws IOException {
        IOException last = null;
        for (String host : hosts) {
            Socket raw = new Socket();
            try {
                raw.connect(new InetSocketAddress(host, port), 8000);
                raw.setTcpNoDelay(true);
                raw.setKeepAlive(true);
                SSLSocket ssl = (SSLSocket)
                        ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(raw, NAME, port, true);
                SSLParameters p = ssl.getSSLParameters();
                p.setEndpointIdentificationAlgorithm("HTTPS"); // the certificate must be arcanic.su's
                // no server name in the hello: VPN nodes that read it would send the line to the CDN behind that name
                p.setServerNames(List.of());
                ssl.setSSLParameters(p);
                ssl.setSoTimeout(SILENCE);
                ssl.startHandshake();
                SyncLine line = new SyncLine(ssl, onText, onClose);
                JsonObject auth = new JsonObject();
                auth.addProperty("t", "auth");
                auth.addProperty("token", token);
                auth.addProperty("client", "Arcanic Visuals");
                line.out.offer(auth.toString());
                line.start();
                return line;
            } catch (IOException e) {
                last = e;
                try {
                    raw.close();
                } catch (IOException ignored) {
                    // it was not open
                }
            }
        }
        throw last != null ? last : new IOException("no host");
    }

    private void start() {
        Thread reader = new Thread(this::read, "Arcanic line reader");
        reader.setDaemon(true);
        reader.start();
        Thread writer = new Thread(this::write, "Arcanic line writer");
        writer.setDaemon(true);
        writer.start();
    }

    private void read() {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(this.socket.getInputStream(), StandardCharsets.UTF_8), 1 << 16)) {
            String text;
            while (!this.closed.get() && (text = in.readLine()) != null) {
                try {
                    this.onText.accept(text);
                } catch (Throwable e) {
                    // one broken message never stops the line
                }
            }
        } catch (SocketTimeoutException e) {
            Sync.log("Arcanic Social line silent for 15 s, reconnecting");
        } catch (IOException e) {
            if (!this.closed.get()) Sync.log("Arcanic Social line broke: " + e);
        } finally {
            this.close();
        }
    }

    private void write() {
        try {
            OutputStream o = this.socket.getOutputStream();
            while (!this.closed.get()) {
                String text = this.out.poll(1, TimeUnit.SECONDS);
                if (text == null) continue;
                o.write((text + "\n").getBytes(StandardCharsets.UTF_8));
                if (this.out.isEmpty()) o.flush();
            }
        } catch (IOException | InterruptedException e) {
            if (!this.closed.get()) Sync.log("Arcanic Social send failed: " + e);
        } finally {
            this.close();
        }
    }

    /** Queued for the writer; a line that stopped taking messages is closed and replaced. */
    void send(String text) {
        if (!this.closed.get() && !this.out.offer(text)) this.close();
    }

    boolean open() {
        return !this.closed.get();
    }

    void close() {
        if (!this.closed.compareAndSet(false, true)) return;
        try {
            this.socket.close();
        } catch (IOException ignored) {
            // already gone
        }
        this.onClose.accept(this);
    }
}
