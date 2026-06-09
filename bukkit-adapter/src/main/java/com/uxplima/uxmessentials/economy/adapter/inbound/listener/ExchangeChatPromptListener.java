package com.uxplima.uxmessentials.economy.adapter.inbound.listener;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class ExchangeChatPromptListener implements Listener {

    private static final Duration PROMPT_EXPIRY = Duration.ofMinutes(2);

    private final Messages messages;
    private final MiniMessage miniMessage;
    private final PromptCancel promptCancel;
    private final PromptRegistry prompts = new PromptRegistry(PROMPT_EXPIRY);

    public ExchangeChatPromptListener(Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.miniMessage = MiniMessage.miniMessage();
        this.promptCancel = new PromptCancel(messages);
    }

    public void prompt(Player player, Component message, Consumer<String> callback) {
        prompts.register(player.getUniqueId(), callback);
        player.sendMessage(message);
    }

    /** Drop every pending prompt; called when the economy module stops so no callback survives teardown. */
    public void clear() {
        prompts.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        prompts.take(player.getUniqueId()).ifPresent(callback -> {
            event.setCancelled(true);
            String text = PlainTextComponentSerializer.plainText()
                    .serialize(event.message())
                    .trim();
            PlayerRef viewer = new PlayerRef(player.getUniqueId(), player.getName());
            if (promptCancel.isCancel(viewer, text)) {
                String prefixStr = messages.resolve(viewer, () -> "prefix", Map.of());
                Component prefix = miniMessage.deserialize(prefixStr);
                String resolved = messages.resolve(viewer, EconomyMessageKey.EXCHANGE_PROMPT_CANCEL, Map.of());
                player.sendMessage(prefix.append(miniMessage.deserialize(resolved)));
                return;
            }
            callback.accept(text);
        });
    }
}
