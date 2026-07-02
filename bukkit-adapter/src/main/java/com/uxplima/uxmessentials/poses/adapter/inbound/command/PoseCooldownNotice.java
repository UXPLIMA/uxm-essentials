package com.uxplima.uxmessentials.poses.adapter.inbound.command;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import org.bukkit.command.CommandSender;

import com.uxplima.uxmessentials.poses.application.PoseCooldown;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The shared {@code ON_COOLDOWN} feedback every pose entry point (the commands, the seat/player interact listeners,
 * and the settings-panel quick-starts) renders when a start use case turns a player away for still being inside the
 * pose cooldown window. It re-reads the live remaining wait from the same {@link PoseCooldown} the use case gated
 * against and renders {@link SharedMessageKey#COOLDOWN_ACTIVE} with the seconds folded in, so the "wait N s" line is
 * written in exactly one place rather than duplicated across the six pose surfaces.
 */
@NullMarked
public final class PoseCooldownNotice {

    private final PoseCooldown cooldown;
    private final CommandFeedback feedback;

    public PoseCooldownNotice(PoseCooldown cooldown, Messages messages) {
        this.cooldown = Objects.requireNonNull(cooldown, "cooldown");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    /** Tell {@code sender} how many whole seconds remain before they may pose again. */
    public void send(CommandSender sender, PlayerRef who) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(who, "who");
        long seconds = Math.max(1, cooldown.remaining(who).orElse(Duration.ZERO).toSeconds());
        feedback.send(sender, SharedMessageKey.COOLDOWN_ACTIVE, Map.of("seconds", Long.toString(seconds)));
    }
}
