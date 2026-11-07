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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.function.LongConsumer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The quantised whisper.cpp models World Lyrics can recognise with. They are fetched from the whisper.cpp model page
 * on Hugging Face the first time recognition is used, checked by size and SHA-256, and kept in
 * {@code config/arcanicvisuals/whisper}.
 */
public enum WhisperModel {
    BASE("Base", "ggml-base-q5_1.bin", 59_707_625L, "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898"),
    SMALL(
            "Small",
            "ggml-small-q5_1.bin",
            190_085_487L,
            "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb");

    private static final String DOWNLOAD_ROOT = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/";

    public final String label;
    public final String fileName;
    public final long sizeBytes;
    private final String sha256;

    WhisperModel(String label, String fileName, long sizeBytes, String sha256) {
        this.label = label;
        this.fileName = fileName;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
    }

    public static WhisperModel byLabel(String label) {
        for (WhisperModel m : values()) if (m.label.equals(label)) return m;
        return BASE;
    }

    public Path file() {
        return FabricLoader.getInstance()
                .getConfigDir()
                .resolve("arcanicvisuals")
                .resolve("whisper")
                .resolve(fileName);
    }

    public boolean installed() {
        Path f = file();
        try {
            return Files.isRegularFile(f) && Files.size(f) == sizeBytes;
        } catch (IOException e) {
            return false;
        }
    }

    /** The model file, downloading it first if it is missing; reports the bytes read so far. */
    Path ensure(LongConsumer progress) throws Exception {
        Path target = file();
        if (installed()) return target;
        Files.createDirectories(target.getParent());
        Path part = target.resolveSibling(fileName + ".part");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        HttpURLConnection c =
                (HttpURLConnection) URI.create(DOWNLOAD_ROOT + fileName).toURL().openConnection();
        long read = 0;
        try {
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(10_000);
            c.setReadTimeout(30_000);
            c.setRequestProperty("User-Agent", "ArcanicVisuals");
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("Model download failed: HTTP " + code);
            byte[] buf = new byte[65536];
            try (InputStream in = c.getInputStream();
                    OutputStream out = Files.newOutputStream(part)) {
                int n;
                while ((n = in.read(buf)) >= 0) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    out.write(buf, 0, n);
                    digest.update(buf, 0, n);
                    read += n;
                    progress.accept(read);
                }
            }
        } catch (Exception e) {
            Files.deleteIfExists(part);
            throw e;
        } finally {
            c.disconnect();
        }
        if (read != sizeBytes) {
            Files.deleteIfExists(part);
            throw new IOException("Model size mismatch: expected " + sizeBytes + ", got " + read);
        }
        if (!HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(sha256)) {
            Files.deleteIfExists(part);
            throw new IOException("Model checksum mismatch");
        }
        Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return target;
    }
}
