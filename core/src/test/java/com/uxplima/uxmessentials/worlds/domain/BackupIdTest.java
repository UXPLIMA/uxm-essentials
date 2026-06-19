package com.uxplima.uxmessentials.worlds.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BackupIdTest {

    @Test
    void acceptsAFilesystemSafeId() {
        assertThat(BackupId.of("20260619-143000").value()).isEqualTo("20260619-143000");
        assertThat(BackupId.of("backup_1.zip").value()).isEqualTo("backup_1.zip");
    }

    @Test
    void rejectsPathTraversalAndSeparators() {
        for (String bad : new String[] {"a/b", "a\\b", "..", ".", "a b", "a:b"}) {
            assertThatThrownBy(() -> BackupId.of(bad)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> BackupId.of("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BackupId.of("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("NullAway") // verifies the compact constructor rejects a literal null value
    void rejectsNull() {
        assertThatThrownBy(() -> BackupId.of(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void factoryRoundTrips() {
        assertThat(BackupId.of("snap-3")).isEqualTo(new BackupId("snap-3"));
    }
}
