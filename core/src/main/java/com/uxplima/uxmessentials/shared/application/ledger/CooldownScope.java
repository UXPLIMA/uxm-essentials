package com.uxplima.uxmessentials.shared.application.ledger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * The stored name of a cooldown stamp. The PDC copy can only hold {@code [a-z0-9._-]} in a key, so the database copy
 * stores the same folded name: a stamp carried from PDC into the database then lands under the exact scope the
 * network cooldowns read.
 */
public final class CooldownScope {

    /** Five bytes, ten hex characters: enough that two names a server holds do not meet by chance. */
    private static final int HASH_BYTES = 5;

    private CooldownScope() {}

    /** {@code name} lower cased, with every character outside {@code [a-z0-9._-]} folded to {@code _}. */
    public static String normalize(String name) {
        Objects.requireNonNull(name, "name");
        String lower = name.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            boolean legal = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-';
            out.append(legal ? c : '_');
        }
        return out.toString();
    }

    /**
     * {@code name} as a stored stamp name that two different names never share. A name {@link #normalize} keeps as it
     * is stays as it is; any other is folded and given a short hash of the name as typed, since folding alone would
     * turn {@code my shop} and {@code my!shop}, or two names in another alphabet of the same length, into one stamp.
     * The hash adds eleven characters, so a folded name stays well inside the stored column.
     */
    public static String distinct(String name) {
        String folded = normalize(name);
        return folded.equals(name) ? name : folded + "-" + shortHash(name);
    }

    private static String shortHash(String name) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(name.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, HASH_BYTES);
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("every Java runtime ships SHA-256", missing);
        }
    }
}
