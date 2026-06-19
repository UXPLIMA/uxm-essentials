package com.uxplima.uxmessentials.worlds.adapter.inbound.gui;

import java.util.Map;
import java.util.Objects;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Resolves a {@link MessageKey} into an Adventure {@link Component} in the viewer's locale, rendering the
 * catalog entry through {@link StyledText} so the world-editor item names, lore, and titles pick up the same
 * style tokens the chat sink uses. The world-editor views share one instance so a line reads identically
 * wherever it is raised — there is exactly one place the world admin GUIs turn a key into text.
 */
@NullMarked
public final class WorldEditorText {

    private final Messages messages;

    public WorldEditorText(Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    public Component text(PlayerRef viewer, MessageKey key) {
        return text(viewer, key, Map.of());
    }

    public Component text(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return StyledText.render(messages.resolve(viewer, key, placeholders));
    }
}
