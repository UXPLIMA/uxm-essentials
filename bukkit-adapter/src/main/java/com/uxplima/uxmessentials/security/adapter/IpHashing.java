package com.uxplima.uxmessentials.security.adapter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

import org.jspecify.annotations.NullMarked;

/**
 * Hashes a connecting player's IP address into the opaque token the device-trust store keys on, so the raw address
 * never reaches the database. It is a plain SHA-256 over the address text rendered as lower-case hex — enough to make
 * two connections from the same address collide (which is all the trust check needs) while storing no reversible
 * network data. This is not a password hash: the input space is tiny, so this is a keying token, not a secret, which
 * is exactly why the store never treats a match as proof of identity on its own — it only skips the keypad.
 */
@NullMarked
public final class IpHashing {

    private IpHashing() {}

    /** The lower-case hex SHA-256 of {@code ip}, the token the trust store keys a device on. */
    public static String hash(String ip) {
        Objects.requireNonNull(ip, "ip");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(ip.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated on every JDK, so this is unreachable; rethrow rather than swallow so a broken
            // provider fails loud instead of silently disabling device trust.
            throw new IllegalStateException("JDK is missing the mandatory SHA-256 provider", e);
        }
    }
}
