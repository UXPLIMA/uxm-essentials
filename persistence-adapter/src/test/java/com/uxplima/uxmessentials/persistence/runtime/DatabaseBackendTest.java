package com.uxplima.uxmessentials.persistence.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The word an operator writes under {@code storage.type}, read into a backend or refused. */
final class DatabaseBackendTest {

    @Test
    @DisplayName("every word the configuration documents names its backend")
    void theDocumentedWordsAreRead() {
        assertThat(DatabaseBackend.fromToken("sqlite")).isEqualTo(DatabaseBackend.SQLITE);
        assertThat(DatabaseBackend.fromToken(" MySQL ")).isEqualTo(DatabaseBackend.MARIADB);
        assertThat(DatabaseBackend.fromToken("mariadb")).isEqualTo(DatabaseBackend.MARIADB);
        assertThat(DatabaseBackend.fromToken("postgres")).isEqualTo(DatabaseBackend.POSTGRES);
        assertThat(DatabaseBackend.fromToken("postgresql")).isEqualTo(DatabaseBackend.POSTGRES);
    }

    @Test
    @DisplayName("a word nobody recognises stops the plugin rather than fall back to a file")
    void anUnknownWordIsRefused() {
        // A network server that fell back to SQLite would start on an empty file of its own and keep the
        // players' data apart from the rest of the network, with nothing but a log line to say so.
        assertThatThrownBy(() -> DatabaseBackend.fromToken("mysqll"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mysqll")
                .hasMessageContaining("sqlite");
    }
}
