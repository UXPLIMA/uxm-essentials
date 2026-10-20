package com.uxplima.uxmessentials.shared.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * A menu line written as {@code @key name=value} hands the engine the whole reference as its key. The arguments are
 * the operator's, and they fill the entry's {@code {name}} tokens the way a hologram or board line does, so one entry
 * serves every tile that differs only in a value.
 */
class GuiTextCatalogLineTest {

    @Test
    void theArgumentsAfterTheKeyFillTheEntry() {
        GuiText guiText = new GuiText((viewer, key, placeholders) -> key.key() + " " + placeholders.get("server"));

        String shown = plain(guiText.text(viewer(), "custom.online server=skyblock", Map.of()));

        assertThat(shown).isEqualTo("custom.online skyblock");
    }

    @Test
    void anArgumentWrittenOnTheLineWinsOverTheEnginesOwn() {
        GuiText guiText = new GuiText((viewer, key, placeholders) -> String.valueOf(placeholders.get("server")));

        assertThat(plain(guiText.text(viewer(), "custom.online server=bedwars", Map.of("server", "engine"))))
                .isEqualTo("bedwars");
        assertThat(plain(guiText.text(viewer(), "custom.online", Map.of("server", "engine"))))
                .isEqualTo("engine");
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
