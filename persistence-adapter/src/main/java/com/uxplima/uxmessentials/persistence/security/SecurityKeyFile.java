package com.uxplima.uxmessentials.persistence.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * Loads — or, on first run, creates — the AES key the {@link TotpSecretCipher} encrypts TOTP secrets under. The key
 * is 256 bits of {@link SecureRandom}, Base64-encoded into a small key-file beside the plugin's data (e.g.
 * {@code modules/security/secret.key}), and read back verbatim on later starts, so every server run decrypts the
 * same stored secrets. Keeping the key in a file rather than the config or the database is deliberate: an operator
 * backs up and permissions it separately from the world data a rollback would touch.
 *
 * <p>The freshly written file is restricted to owner read/write on a POSIX filesystem; on a non-POSIX one (Windows)
 * it relies on the directory ACL. The key itself is never logged, and the file is created atomically with
 * {@code CREATE_NEW} so two starts cannot race two different keys into place.
 */
public final class SecurityKeyFile {

    /** 256-bit AES key. */
    private static final int KEY_BYTES = 32;

    private SecurityKeyFile() {}

    /** The key bytes from {@code keyFile}, generating and persisting a new key there if the file is absent. */
    public static byte[] loadOrCreate(Path keyFile) {
        Objects.requireNonNull(keyFile, "keyFile");
        try {
            if (Files.exists(keyFile)) {
                return readExisting(keyFile);
            }
            return createNew(keyFile);
        } catch (IOException failure) {
            throw new IllegalStateException("could not load or create the security key-file at " + keyFile, failure);
        }
    }

    private static byte[] readExisting(Path keyFile) throws IOException {
        byte[] key = Base64.getDecoder()
                .decode(Files.readString(keyFile, StandardCharsets.UTF_8).trim());
        if (key.length != 16 && key.length != 24 && key.length != 32) {
            throw new IllegalStateException("security key-file holds an invalid AES key length: " + key.length);
        }
        return key;
    }

    private static byte[] createNew(Path keyFile) throws IOException {
        byte[] key = new byte[KEY_BYTES];
        new SecureRandom().nextBytes(key);
        Path parent = keyFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(
                keyFile,
                Base64.getEncoder().encodeToString(key),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
        restrictToOwner(keyFile);
        return key;
    }

    private static void restrictToOwner(Path keyFile) throws IOException {
        PosixFileAttributeView view = Files.getFileAttributeView(keyFile, PosixFileAttributeView.class);
        if (view == null) {
            // Non-POSIX filesystem (Windows): the OS/directory ACL governs access; there is nothing to tighten here.
            return;
        }
        Files.setPosixFilePermissions(keyFile, PosixFilePermissions.fromString("rw-------"));
    }
}
