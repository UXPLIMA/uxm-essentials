package com.uxplima.uxmessentials.economy.application.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.uxplima.uxmessentials.economy.application.FakeCurrencyBackend;
import org.junit.jupiter.api.Test;

class CurrencyBackendRegistryTest {

    @Test
    void findsABackendByItsId() {
        CurrencyBackendRegistry registry = CurrencyBackendRegistry.of(List.of(new FakeCurrencyBackend("native")));
        assertThat(registry.find("native")).isPresent();
    }

    @Test
    void anUnknownIdIsEmptyRatherThanASilentDefault() {
        CurrencyBackendRegistry registry = CurrencyBackendRegistry.of(List.of(new FakeCurrencyBackend("native")));
        assertThat(registry.find("playerpoints")).isEmpty();
    }

    @Test
    void idsIterateInRegistrationOrderNotAlphabetical() {
        CurrencyBackendRegistry registry = CurrencyBackendRegistry.of(List.of(
                new FakeCurrencyBackend("vault"),
                new FakeCurrencyBackend("native"),
                new FakeCurrencyBackend("playerpoints"),
                new FakeCurrencyBackend("coins")));
        assertThat(registry.ids()).containsExactly("vault", "native", "playerpoints", "coins");
    }

    @Test
    void duplicateIdsFailLoudlyAtConstruction() {
        assertThatThrownBy(() -> CurrencyBackendRegistry.of(
                        List.of(new FakeCurrencyBackend("vault"), new FakeCurrencyBackend("vault"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vault");
    }
}
