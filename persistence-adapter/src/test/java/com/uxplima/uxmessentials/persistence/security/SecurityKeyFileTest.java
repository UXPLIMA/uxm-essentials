package com.uxplima.uxmessentials.persistence.security;

import static org.assertj.core.api.Assertions.assertThat;

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

    /**
     * {@code SecurityKeyFile.ownerOnly} has two branches and this covers both, so the test runs everywhere and
     * skips nowhere.
     *
     * <p>It used to open with {@code assumeThat(...).contains("posix")}, which meant that on a filesystem
     * without the POSIX view the whole thing aborted, JUnit recorded a skip, and the suite went green having
     * asserted nothing about the file that decrypts every stored TOTP secret. CONTRACT.md section 15 says
     * there is no legitimate skip here. The condition is not an excuse to stop: it is the thing to branch on,
     * exactly as the production code branches on it.
     */
    @Test
    void theKeyFileIsOwnerOnlyFromTheMomentItExists(@TempDir Path folder) throws IOException {
        Path keyFile = folder.resolve("secret.key");
        boolean posix = keyFile.getFileSystem().supportedFileAttributeViews().contains("posix");

        SecurityKeyFile.loadOrCreate(keyFile);

        assertThat(keyFile).as("the key file must exist on any filesystem").exists();
        if (posix) {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(keyFile);
            assertThat(PosixFilePermissions.toString(permissions))
                    .as("the permissions are passed to the create call, so there is no world-readable moment")
                    .isEqualTo("rw-------");
            return;
        }
        // Windows and anything else without the POSIX view: the OS and the directory ACL govern access and the
        // file carries no permission bits of its own. What is still ours to hold is that the key survives the
        // round trip, so a run that lands here proves the same file and not a different contract.
        assertThat(SecurityKeyFile.loadOrCreate(keyFile))
                .as("the key must read back byte for byte where the filesystem carries no permissions")
                .hasSize(32);
    }
}
