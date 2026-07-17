package com.uxplima.uxmessentials.security.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Pins {@link TwoFactorSecret}: Base32 normalisation, key-byte decoding, redaction, and the generator round-trip. */
class TwoFactorSecretTest {

    @Test
    void normalisesToCanonicalUpperCaseUnpaddedBase32() {
        TwoFactorSecret secret = new TwoFactorSecret("gezd gnbv gy3t qojq");

        assertThat(secret.value()).isEqualTo("GEZDGNBVGY3TQOJQ");
    }

    @Test
    void decodesToTheRawHmacKeyBytes() {
        // ASCII "1234567890" is the first half of the RFC seed; Base32 GEZDGNBVGY3TQOJQ decodes back to it.
        TwoFactorSecret secret = new TwoFactorSecret("GEZDGNBVGY3TQOJQ");

        assertThat(new String(secret.keyBytes(), java.nio.charset.StandardCharsets.US_ASCII))
                .isEqualTo("1234567890");
    }

    @Test
    void rejectsANonBase32Secret() {
        assertThatThrownBy(() -> new TwoFactorSecret("not-base32!")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsABlankSecret() {
        assertThatThrownBy(() -> new TwoFactorSecret("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void redactsItselfInToStringSoItNeverLeaksToALog() {
        TwoFactorSecret secret = new TwoFactorSecret("GEZDGNBVGY3TQOJQ");

        assertThat(secret.toString()).doesNotContain("GEZDGNBVGY3TQOJQ").contains("***");
    }

    @Test
    void generatesAFreshValidSecretEachTime() {
        SecretGenerator generator = new SecretGenerator();

        TwoFactorSecret first = generator.generate();
        TwoFactorSecret second = generator.generate();

        // 160 bits Base32-encode to 32 characters; two draws differ; each is a well-formed, decodable secret.
        assertThat(first.value()).hasSize(32);
        assertThat(first.keyBytes()).hasSize(20);
        assertThat(first).isNotEqualTo(second);
    }
}
