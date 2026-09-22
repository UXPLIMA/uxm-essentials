package com.uxplima.uxmessentials.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The words another plugin of ours owns are reserved only while that plugin is installed.
 *
 * <p>Installed rather than enabled, because the other plugin may enable after this one and the catalogue is
 * resolved once, while this one enables.
 */
final class FamilyCommandWordsTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a server with none of the other plugins reserves nothing")
    void nothingInstalledReservesNothing() {
        assertThat(FamilyCommandWords.reservedOn(server.getPluginManager())).isEmpty();
    }

    @Test
    @DisplayName("an installed shop reserves its two words and nobody else's")
    void anInstalledShopReservesItsWords() {
        MockBukkit.createMockPlugin("uxmShop");

        assertThat(FamilyCommandWords.reservedOn(server.getPluginManager()))
                .containsExactlyInAnyOrderEntriesOf(java.util.Map.of("sell", "uxmShop", "worth", "uxmShop"));
    }
}
