package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.BedrockWidget;
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
        List<BedrockButton> buttons = List.of(new BedrockButton("A", null), new BedrockButton("B", null));
        assertThatCode(() ->
                        BedrockScreen.NONE.sendSimpleForm(player, "Menu", null, buttons, index -> tapped.set(true)))
                .doesNotThrowAnyException();
        assertThat(tapped)
                .as("the no-op screen never invokes the select callback, since it sends no form to tap")
                .isFalse();
    }

    @Test
    void noneSendsNoModalFormAndNeverThrows() {
        AtomicInteger button = new AtomicInteger(0);
        // NONE ignores every argument; the player is a bare mock it never touches, so no MockBukkit server is needed.
        Player player = mock(Player.class);
        assertThatCode(() -> BedrockScreen.NONE.sendModalForm(
                        player, "Confirm", null, "Yes", "No", () -> button.set(1), () -> button.set(2)))
                .doesNotThrowAnyException();
        assertThat(button)
                .as("the no-op screen runs neither button callback, since it sends no form to tap")
                .hasValue(0);
    }

    @Test
    void noneSendsNoInputFormAndNeverThrows() {
        AtomicReference<String> submitted = new AtomicReference<>();
        AtomicBoolean closed = new AtomicBoolean(false);
        // NONE ignores every argument; the player is a bare mock it never touches, so no MockBukkit server is needed.
        Player player = mock(Player.class);
        assertThatCode(() -> BedrockScreen.NONE.sendInputForm(
                        player, "Rename", "New name", "old", submitted::set, () -> closed.set(true)))
                .doesNotThrowAnyException();
        assertThat(submitted)
                .as("the no-op screen never invokes the submit callback, since it sends no form to fill")
                .hasValue(null);
        assertThat(closed)
                .as("the no-op screen never invokes the close callback, since it sends no form to dismiss")
                .isFalse();
    }

    @Test
    void noneSendsNoCustomFormAndNeverThrows() {
        AtomicReference<Map<String, String>> submitted = new AtomicReference<>();
        AtomicBoolean closed = new AtomicBoolean(false);
        // NONE ignores every argument; the player is a bare mock it never touches, so no MockBukkit server is needed.
        Player player = mock(Player.class);
        List<BedrockWidget> widgets = List.of(
                new BedrockWidget.Label("hi"),
                new BedrockWidget.Input("name", "Name", "", ""),
                new BedrockWidget.Toggle("flag", "Flag?", false));
        assertThatCode(() -> BedrockScreen.NONE.sendCustomForm(
                        player, "Create", "content", widgets, submitted::set, () -> closed.set(true)))
                .doesNotThrowAnyException();
        assertThat(submitted)
                .as("the no-op screen never invokes the submit callback, since it sends no form to fill")
                .hasValue(null);
        assertThat(closed)
                .as("the no-op screen never invokes the close callback, since it sends no form to dismiss")
                .isFalse();
    }
}
