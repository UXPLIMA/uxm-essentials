package com.uxplima.uxmessentials.shared.application.port;

/**
 * Turns a raw connecting address into the opaque token the {@link IpHistoryStore} keys associations by. The
 * implementation is a keyed HMAC over the server's own secret key, so a token cannot be swept back to an address
 * by anyone who only has the database, and two servers never produce the same token for the same address.
 *
 * <p>Application code holds this port rather than an address: a use case that needs to ask "who else uses this
 * address" tokenises first and queries by token.
 */
public interface IpTokens {

    /** The token for {@code address} (a raw textual IPv4/IPv6 address). */
    String tokenFor(String address);
}
