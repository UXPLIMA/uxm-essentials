package com.uxplima.uxmessentials.economy.adapter.inbound.listener;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The shared cancel-token policy for the economy chat prompts (exchange, bank, loan). The accepted cancel
 * keywords are not hardcoded — they come from the {@code eco.prompt.cancel-tokens} catalog entry, a
 * comma-separated list resolved in the viewer's locale, so an English client cancels with {@code cancel} and a
 * Turkish client with {@code iptal} (or whatever the operator's translation lists) without either keyword living
 * in code. The cancellation acknowledgement is the {@code eco.prompt.cancelled} entry, rendered with the shared
 * prefix folded in.
 */
@NullMarked
public final class PromptCancel {

    private final Messages messages;
    private final MiniMessage miniMessage;

    public PromptCancel(Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.miniMessage = MiniMessage.miniMessage();
    }

    /** True when {@code input} matches one of the viewer's configured cancel tokens (case-insensitive). */
    public boolean isCancel(PlayerRef viewer, String input) {
        String trimmed = input.trim().toLowerCase(Locale.ROOT);
        String tokens = messages.resolve(viewer, EconomyMessageKey.PROMPT_CANCEL_TOKENS, Map.of());
        for (String token : com.google.common.base.Splitter.on(',')
                .trimResults()
                .omitEmptyStrings()
                .split(tokens)) {
            if (trimmed.equals(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** Send the localised cancellation acknowledgement, with the shared prefix folded in. */
    public void sendCancelled(Player player, PlayerRef viewer) {
        Component prefix = miniMessage.deserialize(messages.resolve(viewer, () -> "prefix", Map.of()));
        Component body =
                miniMessage.deserialize(messages.resolve(viewer, EconomyMessageKey.PROMPT_CANCELLED, Map.of()));
        player.sendMessage(prefix.append(body));
    }
}
