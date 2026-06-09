package com.uxplima.uxmessentials.kits.adapter.inbound.listener;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.kits.application.KitsMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Temporary listener that prompts a player for text input via chat, intercepting
 * their next message. Used by the in-game Kit Settings/Manager GUI.
 */
@NullMarked
public final class ChatPromptListener implements Listener {

    private final Messages messages;
    private final MiniMessage miniMessage;
    private final Map<UUID, Consumer<String>> activePrompts = new ConcurrentHashMap<>();

    public ChatPromptListener(Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.miniMessage = MiniMessage.miniMessage();
    }

    /**
     * Request input from a player.
     *
     * @param player the player to prompt
     * @param message the message explaining what to enter
     * @param callback the action to run once input is received
     */
    public void prompt(Player player, Component message, Consumer<String> callback) {
        PlayerRef viewer = new PlayerRef(player.getUniqueId(), player.getName());
        String prefixStr = messages.resolve(viewer, () -> "prefix", Map.of());
        Component prefix = miniMessage.deserialize(prefixStr);
        player.sendMessage(prefix.append(message));
        activePrompts.put(player.getUniqueId(), callback);
    }

    /** Drop every pending prompt; called on module stop so a leftover callback can never fire after teardown. */
    public void clear() {
        activePrompts.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Consumer<String> callback = activePrompts.remove(player.getUniqueId());
        if (callback != null) {
            event.setCancelled(true);
            String text = PlainTextComponentSerializer.plainText()
                    .serialize(event.message())
                    .trim();
            if (text.equalsIgnoreCase("cancel")) {
                PlayerRef viewer = new PlayerRef(player.getUniqueId(), player.getName());
                String prefixStr = messages.resolve(viewer, () -> "prefix", Map.of());
                TagResolver prefix = Placeholder.component("prefix", miniMessage.deserialize(prefixStr));
                String resolved = messages.resolve(viewer, KitsMessageKey.KIT_EDITOR_PROMPT_CANCELLED, Map.of());
                player.sendMessage(miniMessage.deserialize(resolved, prefix));
                return;
            }
            callback.accept(text);
        }
    }
}
