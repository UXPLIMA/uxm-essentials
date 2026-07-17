package com.uxplima.uxmessentials.persistence.security;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;

import com.uxplima.uxmessentials.persistence.playerwarps.Pbkdf2PasswordHasher;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRepository;
import org.jspecify.annotations.NullMarked;

/**
 * Factory for the security context's persistence adapter, so the consuming bukkit-adapter wires a
 * {@link TwoFactorRepository} from the {@link Persistence} handle and a key-file path without ever naming a jOOQ
 * type (jOOQ is an {@code implementation} dependency kept off the consumer's compile classpath). The returned
 * repository hashes the PIN with the shared PBKDF2 hasher and encrypts the TOTP secret with an AES-GCM cipher keyed
 * from {@code keyFile}, stamping each write with the system clock.
 */
@NullMarked
public final class TwoFactorRepositories {

    private TwoFactorRepositories() {}

    /**
     * A jOOQ {@link TwoFactorRepository} over the shared persistence DSL, encrypting TOTP secrets under the key in
     * {@code keyFile} (created with a fresh random key on first use).
     */
    public static TwoFactorRepository jooq(Persistence persistence, Path keyFile) {
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(keyFile, "keyFile");
        TotpSecretCipher cipher = new TotpSecretCipher(SecurityKeyFile.loadOrCreate(keyFile));
        return new JooqTwoFactorRepository(persistence.dsl(), new Pbkdf2PasswordHasher(), cipher, Clock.systemUTC());
    }
}
