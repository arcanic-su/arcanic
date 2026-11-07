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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Premium files come sealed for one session: "ARC1", a 16-byte nonce, the bytes XORed with an HMAC-SHA256 keystream,
 * an HMAC-SHA256 tag. The key is SHA-256 of the session token and the nonce, so only this game opens them. They are
 * opened in memory and never written to disk.
 */
final class Seal {
    private Seal() {}

    private static final byte[] MAGIC = "ARC1".getBytes(StandardCharsets.US_ASCII);
    private static final int NONCE = 16, TAG = 32;

    /** The file's bytes; a reply that is not sealed (an older server) comes back as it is, a broken one as null. */
    static byte[] open(byte[] reply, String token) {
        if (reply == null || reply.length < MAGIC.length || !Arrays.equals(reply, 0, 4, MAGIC, 0, 4)) return reply;
        if (reply.length < MAGIC.length + NONCE + TAG) return null;
        try {
            byte[] nonce = Arrays.copyOfRange(reply, 4, 4 + NONCE);
            byte[] body = Arrays.copyOfRange(reply, 4 + NONCE, reply.length - TAG);
            byte[] tag = Arrays.copyOfRange(reply, reply.length - TAG, reply.length);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update("arcanic-seal\0".getBytes(StandardCharsets.US_ASCII));
            sha.update(token.getBytes(StandardCharsets.UTF_8));
            sha.update((byte) 0);
            sha.update(nonce);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(sha.digest(), "HmacSHA256"));
            mac.update("tag".getBytes(StandardCharsets.US_ASCII));
            mac.update(nonce);
            if (!MessageDigest.isEqual(mac.doFinal(body), tag)) return null;
            byte[] out = new byte[body.length];
            ByteBuffer counter = ByteBuffer.allocate(NONCE + 8);
            for (int block = 0, at = 0; at < body.length; block++) {
                counter.clear();
                counter.put(nonce).putLong(block);
                byte[] stream = mac.doFinal(counter.array());
                for (int i = 0; i < stream.length && at < body.length; i++, at++) {
                    out[at] = (byte) (body[at] ^ stream[i]);
                }
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }
}
