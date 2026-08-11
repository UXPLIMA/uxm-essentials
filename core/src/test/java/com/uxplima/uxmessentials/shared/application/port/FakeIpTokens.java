package com.uxplima.uxmessentials.shared.application.port;

import java.util.Objects;

/**
 * A deterministic stand-in for the keyed hash the adapter applies to a connecting address. It keeps the shape the
 * production tokens have (one address always maps to one opaque token, and the token never contains the address in
 * a form a lookup could reverse by accident) without needing a key file, so use-case tests can seed a
 * {@link FakeIpHistoryStore} with the token an address would have produced.
 */
public final class FakeIpTokens implements IpTokens {

    /** The token {@code address} hashes to, for tests that seed the store directly. */
    public static String token(String address) {
        return "token-" + Objects.requireNonNull(address, "address");
    }

    @Override
    public String tokenFor(String address) {
        return token(address);
    }
}
