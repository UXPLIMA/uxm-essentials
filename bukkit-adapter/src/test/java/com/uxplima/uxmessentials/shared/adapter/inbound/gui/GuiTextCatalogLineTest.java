package com.uxplima.uxmessentials.shared.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.outbound.message.CatalogMessages;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * A menu line written as {@code @key name=value} hands the engine the whole reference as its key. The arguments are
 * the operator's, and they fill the entry's {@code {name}} tokens the way a hologram or board line does, so one entry
 * serves every tile that differs only in a value.
 */
class GuiTextCatalogLineTest {

    /** A catalogue that fills its {@code {name}} tokens the way the real one does. */
    private static final GuiText GUI_TEXT = new GuiText((viewer, key, arguments) -> CatalogMessages.fill(
            Map.of("custom.online", "online {server}", "custom.warp", "warp {warp}")
                    .getOrDefault(key.key(), key.key()),
            arguments));

    @Test
    void theArgumentsAfterTheKeyFillTheEntry() {
        assertThat(plain(GUI_TEXT.text(viewer(), "custom.online server=skyblock", Map.of())))
                .isEqualTo("online skyblock");
    }

    @Test
    void anArgumentWrittenOnTheLineWinsOverTheEnginesOwn() {
        assertThat(plain(GUI_TEXT.text(viewer(), "custom.online server=bedwars", Map.of("server", "engine"))))
                .isEqualTo("online bedwars");
        assertThat(plain(GUI_TEXT.text(viewer(), "custom.online", Map.of("server", "engine"))))
                .isEqualTo("online engine");
    }

    /**
     * An engine value may be text a player typed, a warp name for one. It goes into the line after the placeholders
     * have run, so a {@code %placeholder%} in it is shown as typed rather than answered on the viewer's screen. With
     * no server running here, asking the placeholder bridge at all would throw.
     */
    @Test
    void anEngineValueIsNeverReadForPlaceholders() {
        assertThat(plain(GUI_TEXT.text(viewer(), "custom.warp", Map.of("warp", "%player_ip%"))))
                .isEqualTo("warp %player_ip%");
    }

    private static Player viewer() {
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        Mockito.when(player.getName()).thenReturn("Viewer");
        return player;
    }

    private static String plain(net.kyori.adventure.text.Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
