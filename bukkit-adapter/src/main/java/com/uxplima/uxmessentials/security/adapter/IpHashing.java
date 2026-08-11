package com.uxplima.uxmessentials.security.adapter;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.jspecify.annotations.NullMarked;

/**
 * Turns a connecting player's IP address into the opaque token the device-trust and IP/alt stores key on, so the raw
 * address never reaches the database. It is an HMAC-SHA-256 over the address text, keyed by the server's own
 * {@code modules/security/secret.key}, rendered as lower-case hex: two connections from the same address collide
 * (which is all the trust check and the alt lookup need) while the stored value tells a reader nothing.
 *
 * <p>The key is the whole point. A plain digest of an IP address is not one-way in practice: the entire IPv4 space is
 * about four billion values, which any laptop can hash through in minutes, so an unkeyed token is a reversible
 * recording of who connected from where. Keying it means an attacker who walks off with the database, but not the
 * key-file beside it, cannot run that sweep. It is still not a password hash and is never treated as proof of
 * identity on its own: a token match only skips the keypad.
 */
@NullMarked
public final class IpHashing {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] key;

    /** Key the tokens with {@code key}, normally the same server key-file the TOTP secrets are encrypted under. */
    public IpHashing(byte[] key) {
        Objects.requireNonNull(key, "key");
        if (key.length == 0) {
            throw new IllegalArgumentException("the ip-token key must not be empty");
        }
        this.key = key.clone();
    }

    /** The lower-case hex HMAC-SHA-256 of {@code ip}, the token a device or an address is keyed by. */
    public String hash(String ip) {
        Objects.requireNonNull(ip, "ip");
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(ip.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // HmacSHA256 is mandated on every JDK, so this is unreachable; rethrow rather than swallow so a broken
            // provider fails loud instead of silently disabling device trust.
            throw new IllegalStateException("JDK is missing the mandatory " + HMAC_ALGORITHM + " provider", e);
        }
    }
}
