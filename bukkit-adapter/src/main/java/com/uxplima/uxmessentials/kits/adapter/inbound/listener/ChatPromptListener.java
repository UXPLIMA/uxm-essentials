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
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.kits.application.KitsMessageKey;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Temporary listener that prompts a player for text input via chat, intercepting
 * their next message. Used by the in-game Kit Settings/Manager GUI. The prompt {@link Component} is already
 * resolved and styled by the caller (it carries its own {@code <tag:'KIT'>} prefix), so it is sent verbatim.
 */
@NullMarked
public final class ChatPromptListener implements Listener {

    private final Messages messages;
    private final Map<UUID, Consumer<String>> activePrompts = new ConcurrentHashMap<>();

    public ChatPromptListener(Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /**
     * Request input from a player.
     *
     * @param player the player to prompt
     * @param message the message explaining what to enter, already resolved and styled by the caller
     * @param callback the action to run once input is received
     */
    public void prompt(Player player, Component message, Consumer<String> callback) {
        player.sendMessage(message);
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
                String resolved = messages.resolve(viewer, KitsMessageKey.KIT_EDITOR_PROMPT_CANCELLED, Map.of());
                player.sendMessage(StyledText.render(resolved));
                return;
            }
            callback.accept(text);
        }
    }
}
