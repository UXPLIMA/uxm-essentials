package com.uxplima.uxmessentials.kits.adapter.inbound.gui;

import java.util.Map;
import java.util.Objects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Resolves a {@link MessageKey} into an Adventure {@link Component} in the viewer's locale, deserialising the
 * catalog entry through MiniMessage with the {@code prefix} placeholder bound to the catalog's prefix entry.
 * The kit-editor listener and its category-editing collaborator share one instance so a prompt or error line
 * reads identically wherever it is raised — there is exactly one place the kit admin GUIs turn a key into text.
 */
@NullMarked
final class KitEditorText {

    private final Messages messages;
    private final MiniMessage miniMessage;

    KitEditorText(Messages messages, MiniMessage miniMessage) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.miniMessage = Objects.requireNonNull(miniMessage, "miniMessage");
    }

    Component text(PlayerRef viewer, MessageKey key) {
        return text(viewer, key, Map.of());
    }

    Component text(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        String prefixStr = messages.resolve(viewer, () -> "prefix", Map.of());
        TagResolver prefix = Placeholder.component("prefix", miniMessage.deserialize(prefixStr));
        String resolved = messages.resolve(viewer, key, placeholders);
        return miniMessage.deserialize(resolved, prefix);
    }
}
