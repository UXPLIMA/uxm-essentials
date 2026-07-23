package com.uxplima.uxmessentials.velocity.commandcontrol;

import java.util.Objects;
import java.util.Optional;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;

/**
 * The operator-configured deny and spam messages for the proxy command-control layer, read from the
 * {@code command-control.messages} block and rendered with MiniMessage. Each value is optional: a blank
 * string means "send nothing" for that case, mirroring how the backend leaves a message key unset. The
 * proxy has no {@code MessageKey} catalog (that is the backend's i18n surface), so these are plain
 * operator strings, not inline literals in code.
 *
 * @param unknownCommand the vanilla-style deny line shown when {@code use-unknown-command-message} is on
 * @param noPermission the "you cannot use that" deny line shown when {@code use-unknown-command-message} is off
 * @param pluginHidden the deny line shown when a hidden proxy command is blocked from executing
 * @param spamKick the disconnect reason for the command-spam KICK action
 * @param spamBlocked the in-chat notice for the command-spam BLOCK action (the command was cancelled)
 * @param spamWarn the nudge for the command-spam WARN action (the command still ran)
 */
public record ProxyCommandMessages(
        String unknownCommand,
        String noPermission,
        String pluginHidden,
        String spamKick,
        String spamBlocked,
        String spamWarn) {

    public ProxyCommandMessages {
        Objects.requireNonNull(unknownCommand, "unknownCommand");
        Objects.requireNonNull(noPermission, "noPermission");
        Objects.requireNonNull(pluginHidden, "pluginHidden");
        Objects.requireNonNull(spamKick, "spamKick");
        Objects.requireNonNull(spamBlocked, "spamBlocked");
        Objects.requireNonNull(spamWarn, "spamWarn");
    }

    /** Read the {@code messages} block from {@code config}, falling back to sensible defaults per key. */
    public static ProxyCommandMessages from(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        return new ProxyCommandMessages(
                config.getString("messages.unknown-command", "<red>Unknown command."),
                config.getString("messages.no-permission", "<red>You do not have permission to use that command."),
                config.getString("messages.plugin-hidden", "<red>Unknown command."),
                config.getString("messages.spam-kick", "<red>You are sending commands too quickly."),
                config.getString(
                        "messages.spam-blocked", "<red>You are sending commands too quickly. Please slow down."),
                config.getString("messages.spam-warn", "<yellow>You are sending commands quickly, please slow down."));
    }

    /** The deny line to show on a blocked command, per {@code use-unknown-command-message}. */
    public Optional<Component> deny(boolean useUnknownCommandMessage) {
        return render(useUnknownCommandMessage ? unknownCommand : noPermission);
    }

    public Optional<Component> pluginHiddenComponent() {
        return render(pluginHidden);
    }

    public Optional<Component> spamKickComponent() {
        return render(spamKick);
    }

    public Optional<Component> spamBlockedComponent() {
        return render(spamBlocked);
    }

    public Optional<Component> spamWarnComponent() {
        return render(spamWarn);
    }

    /** Render a MiniMessage string, or {@link Optional#empty()} when it is blank (nothing to send). */
    private static Optional<Component> render(String raw) {
        if (raw.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(MiniMessage.miniMessage().deserialize(raw));
    }
}
