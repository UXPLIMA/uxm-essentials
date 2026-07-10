package com.uxplima.uxmessentials.playerwarps.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PasswordHashTest {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String SALT = "c2FsdHktc2FsdA==";
    private static final String HASH = "ZGlnZXN0LXZhbHVl";

    @Test
    void keepsEveryFieldItWasGiven() {
        PasswordHash digest = new PasswordHash(ALGORITHM, SALT, HASH);
        assertThat(digest.algorithm()).isEqualTo(ALGORITHM);
        assertThat(digest.salt()).isEqualTo(SALT);
        assertThat(digest.hash()).isEqualTo(HASH);
    }

    @Test
    @SuppressWarnings("NullAway") // verifies the compact constructor rejects a literal null in each field
    void rejectsNullFields() {
        assertThatNullPointerException().isThrownBy(() -> new PasswordHash(null, SALT, HASH));
        assertThatNullPointerException().isThrownBy(() -> new PasswordHash(ALGORITHM, null, HASH));
        assertThatNullPointerException().isThrownBy(() -> new PasswordHash(ALGORITHM, SALT, null));
    }

    @Test
    void rejectsBlankFields() {
        assertThatThrownBy(() -> new PasswordHash("  ", SALT, HASH)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PasswordHash(ALGORITHM, "  ", HASH)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PasswordHash(ALGORITHM, SALT, "  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toStringRedactsSaltAndHashSoADigestNeverLeaksIntoALogLine() {
        String rendered = new PasswordHash(ALGORITHM, SALT, HASH).toString();
        assertThat(rendered).contains(ALGORITHM);
        assertThat(rendered).doesNotContain(SALT);
        assertThat(rendered).doesNotContain(HASH);
    }
}
