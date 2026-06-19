package com.uxplima.uxmessentials.kits.adapter.inbound.gui;

import java.util.Map;
import java.util.Objects;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Resolves a {@link MessageKey} into an Adventure {@link Component} in the viewer's locale, deserialising the
 * catalog entry through the project style tokens so a prompt or error line carries the same {@code <tag:'KIT'>}
 * prefix and colours the chat sink applies. The kit-editor listener and its category-editing collaborator share
 * one instance so a line reads identically wherever it is raised — there is exactly one place the kit admin GUIs
 * turn a key into text.
 */
@NullMarked
final class KitEditorText {

    private final Messages messages;

    KitEditorText(Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    Component text(PlayerRef viewer, MessageKey key) {
        return text(viewer, key, Map.of());
    }

    Component text(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return StyledText.render(messages.resolve(viewer, key, placeholders));
    }
}
