package com.uxplima.uxmessentials.moderation.adapter.inbound.command;

import java.util.List;

import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the moderation context's Brigadier command surface (docs/10-feature-modules.md §15.9) as
 * {@link CommandRegistration}s over the constructed {@link ModerationServices}. Collected in one greppable
 * table so the literal/permission pairing matches the permissions reference and the kernel's
 * {@code ModerationCommandSurface}; the plugin's {@code LifecycleEvents.COMMANDS} handler registers each. The
 * single-argument verbs ({@code /unmute /unjail /freeze /unfreeze /seen /seenip /warns}) share the
 * {@link SimpleTargetCommand} shape, each binding its own node and use-case action.
 */
@NullMarked
public final class ModerationCommands {

    private ModerationCommands() {}

    /** Every moderation command, in surface order. */
    public static List<CommandRegistration> all(ModerationServices services, Messages messages, MessageSink sink) {
        return List.of(
                new MuteCommand(services, messages, sink),
                new TempmuteCommand(services, messages, sink),
                target(
                        "unmute",
                        "uxmessentials.moderation.unmute",
                        "Lift a player's mute",
                        services,
                        messages,
                        sink,
                        (a, t) -> services.unmute().unmute(a, t)),
                new JailCommand(services, messages, sink),
                target(
                        "unjail",
                        "uxmessentials.moderation.unjail",
                        "Release a jailed player",
                        services,
                        messages,
                        sink,
                        (a, t) -> services.unjail().unjail(a, t)),
                new TempbanCommand(services, messages, sink),
                new KickCommand(services, messages, sink),
                new KickallCommand(services, messages, sink),
                new WarnCommand(services, messages, sink),
                target(
                        "warns",
                        "uxmessentials.moderation.warn",
                        "Review a player's warnings",
                        services,
                        messages,
                        sink,
                        (a, t) -> services.reviewWarns().review(a, t)),
                new BanipCommand(services, messages, sink),
                new UnbanipCommand(services, messages, sink),
                target(
                        "freeze",
                        "uxmessentials.moderation.freeze",
                        "Freeze a player in place",
                        services,
                        messages,
                        sink,
                        (a, t) -> services.freeze().freeze(a, t)),
                target(
                        "unfreeze",
                        "uxmessentials.moderation.freeze",
                        "Release a frozen player",
                        services,
                        messages,
                        sink,
                        (a, t) -> services.freeze().unfreeze(a, t)),
                target(
                        "seen",
                        "uxmessentials.moderation.seen",
                        "Show a player's last-seen",
                        services,
                        messages,
                        sink,
                        (a, t) -> services.seen().seen(a, t)),
                target(
                        "seenip",
                        "uxmessentials.moderation.seen",
                        "Show a player's last IP and alts",
                        services,
                        messages,
                        sink,
                        (a, t) -> services.seen().seenIp(a, t)));
    }

    private static SimpleTargetCommand target(
            String literal,
            String permission,
            String description,
            ModerationServices services,
            Messages messages,
            MessageSink sink,
            java.util.function.BiConsumer<
                            com.uxplima.uxmessentials.shared.domain.PlayerRef,
                            com.uxplima.uxmessentials.shared.domain.PlayerRef>
                    action) {
        return new SimpleTargetCommand(literal, permission, description, action, services, messages, sink);
    }
}
