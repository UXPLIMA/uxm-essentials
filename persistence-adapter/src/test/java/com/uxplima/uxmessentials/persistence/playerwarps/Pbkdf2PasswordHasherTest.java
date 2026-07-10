package com.uxplima.uxmessentials.persistence.playerwarps;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.playerwarps.application.port.PasswordHasher;
import com.uxplima.uxmessentials.playerwarps.domain.PasswordHash;
import org.junit.jupiter.api.Test;

class Pbkdf2PasswordHasherTest {

    private final PasswordHasher hasher = new Pbkdf2PasswordHasher();

    @Test
    void hashThenVerifyRoundTripsTheSamePassword() {
        PasswordHash digest = hasher.hash("hunter2");
        assertThat(hasher.verify("hunter2", digest)).isTrue();
    }

    @Test
    void verifyRejectsTheWrongPassword() {
        PasswordHash digest = hasher.hash("hunter2");
        assertThat(hasher.verify("hunter3", digest)).isFalse();
    }

    @Test
    void twoHashesOfTheSamePasswordDifferBySaltYetBothVerify() {
        PasswordHash first = hasher.hash("same");
        PasswordHash second = hasher.hash("same");

        assertThat(first.salt()).isNotEqualTo(second.salt());
        assertThat(first.hash()).isNotEqualTo(second.hash());
        assertThat(hasher.verify("same", first)).isTrue();
        assertThat(hasher.verify("same", second)).isTrue();
    }

    @Test
    void aTamperedDigestNoLongerVerifies() {
        PasswordHash digest = hasher.hash("hunter2");
        PasswordHash tampered = new PasswordHash(digest.algorithm(), digest.salt(), flipFirstChar(digest.hash()));
        assertThat(hasher.verify("hunter2", tampered)).isFalse();
    }

    @Test
    void recordsThePbkdf2AlgorithmSoAFutureCostBumpIsDetectablePerRecord() {
        assertThat(hasher.hash("hunter2").algorithm()).isEqualTo("PBKDF2WithHmacSHA256");
    }

    private static String flipFirstChar(String base64) {
        char head = base64.charAt(0);
        char replacement = head == 'A' ? 'B' : 'A';
        return replacement + base64.substring(1);
    }
}
