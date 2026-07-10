package com.uxplima.uxmessentials.playerwarps.domain;

import java.util.Objects;

/**
 * The persisted form of a player-warp's password: the digest that guards a locked warp, never the plaintext.
 * A warp owner can lock their warp behind a password; only this one-way digest is stored, so a leaked database
 * or a rolled-back world never yields a usable password. {@link #salt} and {@link #hash} are Base64 text so the
 * value survives as plain columns on every backend, and {@link #algorithm} travels with the record so a future
 * cost bump can be detected and re-hashed per warp rather than forcing a global reset.
 *
 * <p>{@link #toString()} deliberately redacts the salt and the digest. A {@code PasswordHash} is routinely near
 * log statements and exception messages during teleport gating; letting the raw digest render there would hand
 * an offline attacker the exact material to mount a dictionary attack, so the redaction is a hard requirement,
 * not a nicety.
 *
 * @param algorithm the key-derivation algorithm that produced {@link #hash} (e.g. {@code PBKDF2WithHmacSHA256})
 * @param salt the per-warp random salt, Base64-encoded
 * @param hash the derived digest, Base64-encoded
 */
public record PasswordHash(String algorithm, String salt, String hash) {

    public PasswordHash {
        algorithm = requireNonBlank(algorithm, "algorithm");
        salt = requireNonBlank(salt, "salt");
        hash = requireNonBlank(hash, "hash");
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    @Override
    public String toString() {
        return "PasswordHash[algorithm=" + algorithm + ", salt=***, hash=***]";
    }
}
