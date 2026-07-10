package com.uxplima.uxmessentials.economy.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CurrencyBackendIdTest {

    @Test
    void defaultsToTheNativeLedger() {
        Currency coins = Currency.builder(CurrencyId.of("coins")).build();
        assertThat(coins.backendId()).isEqualTo("native");
    }

    @Test
    void carriesTheConfiguredBackend() {
        Currency gold = Currency.builder(CurrencyId.of("gold"))
                .backendId("coinsengine:gold")
                .build();
        assertThat(gold.backendId()).isEqualTo("coinsengine:gold");
    }

    @Test
    void rejectsABlankBackend() {
        assertThatThrownBy(() -> Currency.builder(CurrencyId.of("x")).backendId("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
