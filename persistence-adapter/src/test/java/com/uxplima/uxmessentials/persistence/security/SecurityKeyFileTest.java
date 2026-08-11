package com.uxplima.uxmessentials.persistence.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins the key-file contract: a first run writes a fresh 256-bit key, a later run reads exactly that key back, and
 * the file is created readable by nobody but its owner. The permissions matter as much as the bytes: this key
 * decrypts every stored TOTP secret and keys every IP token, so a moment of world-readability is a real leak.
 */
class SecurityKeyFileTest {

    @Test
    void createsA256BitKeyOnFirstUseAndReadsTheSameKeyBack(@TempDir Path folder) {
        Path keyFile = folder.resolve("modules/security/secret.key");

        byte[] created = SecurityKeyFile.loadOrCreate(keyFile);

        assertThat(created).hasSize(32);
        assertThat(SecurityKeyFile.loadOrCreate(keyFile)).isEqualTo(created);
    }

    @Test
    void theKeyFileIsOwnerOnlyFromTheMomentItExists(@TempDir Path folder) throws IOException {
        Path keyFile = folder.resolve("secret.key");
        assumeThat(keyFile.getFileSystem().supportedFileAttributeViews()).contains("posix");

        SecurityKeyFile.loadOrCreate(keyFile);

        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(keyFile);
        assertThat(PosixFilePermissions.toString(permissions)).isEqualTo("rw-------");
    }
}
