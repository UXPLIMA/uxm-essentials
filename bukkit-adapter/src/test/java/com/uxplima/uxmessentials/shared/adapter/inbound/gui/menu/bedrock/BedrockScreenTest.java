package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.entity.Player;

import org.junit.jupiter.api.Test;

/**
 * The Java-only {@link BedrockScreen#NONE} default in isolation — a pure no-op that carries no {@code org.geysermc}
 * reference, so it never loads the Cumulus/Floodgate SDK. The Cumulus-backed screen is untestable here: its SDK is a
 * {@code compileOnly} soft-depend absent from the test runtime, exactly like {@code CumulusBedrockScreen}'s
 * detector twin, so instantiating it is out of bounds. The tested contract is that {@code NONE} sends nothing and
 * routes nothing back — it exists so the engine never has to null-check the screen before an open.
 */
class BedrockScreenTest {

    @Test
    void noneSendsNothingAndNeverThrows() {
        AtomicBoolean tapped = new AtomicBoolean(false);
        // NONE ignores every argument; the player is a bare mock it never touches, so no MockBukkit server is needed.
        Player player = mock(Player.class);
        assertThatCode(() -> BedrockScreen.NONE.sendSimpleForm(
                        player, "Menu", null, List.of("A", "B"), index -> tapped.set(true)))
                .doesNotThrowAnyException();
        assertThat(tapped)
                .as("the no-op screen never invokes the select callback, since it sends no form to tap")
                .isFalse();
    }
}
