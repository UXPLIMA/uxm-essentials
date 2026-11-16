package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Only a secret that is hard to guess signs frames: not an example copied from the docs, and not a run of few keys. */
class BusSecretTest {

    @Test
    void aGeneratedSecretIsUsable() {
        BusSecret secret = BusSecret.of("9f2c4e7a1b8d03f6e5c2a9147b6d8e0f3a5c7e9b1d2f4a6c8e0b3d5f7a9c1e2b");

        assertThat(secret.usable()).isTrue();
        assertThat(secret.toString()).doesNotContain("9f2c4e7a");
    }

    @Test
    void theExampleFromTheDocumentationIsNotASecret() {
        assertThat(BusSecret.of("<output of openssl rand -hex 32>").state()).isEqualTo(BusSecret.State.PLACEHOLDER);
        assertThat(BusSecret.of("paste 32 or more random characters here").state())
                .isEqualTo(BusSecret.State.PLACEHOLDER);
        assertThat(BusSecret.of("changeme-changeme-changeme-changeme").usable()).isFalse();
    }

    @Test
    void aLongSecretOfFewDifferentCharactersIsTooEasyToGuess() {
        assertThat(BusSecret.of("a".repeat(64)).state()).isEqualTo(BusSecret.State.WEAK);
        assertThat(BusSecret.of("abcabcabcabcabcabcabcabcabcabcabcabc").state()).isEqualTo(BusSecret.State.WEAK);
        assertThat(BusSecret.of("1234567891234567891234567891234567").state()).isEqualTo(BusSecret.State.WEAK);
    }

    @Test
    void anEmptyOrShortSecretSaysWhich() {
        assertThat(BusSecret.of("   ").state()).isEqualTo(BusSecret.State.BLANK);
        assertThat(BusSecret.of("9f2c4e7a1b8d03f6").state()).isEqualTo(BusSecret.State.SHORT);
    }
}
