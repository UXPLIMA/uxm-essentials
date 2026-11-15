package com.uxplima.uxmessentials.velocity.commandcontrol;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.uxmessentials.shared.application.message.CatalogLine;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.Nullable;

/**
 * The deny and spam lines of the proxy command-control layer, read from the {@code command-control.messages} block.
 *
 * <p>Each value is one of three things. Blank sends nothing for that case. A value that is an {@code @key} reference
 * reads that line of the proxy's {@link ProxyCatalog} in the player's own language, which is what every shipped value
 * does. Anything else is MiniMessage sent to everybody as written, so an operator who wrote their own words keeps them.
 * The theme's role tags ({@code <bad>}, {@code <warn>} and the rest) read in the colours the backend ships.
 *
 * @param unknownCommand the vanilla-style deny line shown when {@code use-unknown-command-message} is on
 * @param noPermission the "you cannot use that" deny line shown when {@code use-unknown-command-message} is off
 * @param pluginHidden the deny line shown when a hidden proxy command is blocked from executing
 * @param spamKick the disconnect reason for the command-spam KICK action
 * @param spamBlocked the in-chat notice for the command-spam BLOCK action (the command was cancelled)
 * @param spamWarn the nudge for the command-spam WARN action (the command still ran)
 * @param catalog the lines an {@code @key} value names
 */
public record ProxyCommandMessages(
        String unknownCommand,
        String noPermission,
        String pluginHidden,
        String spamKick,
        String spamBlocked,
        String spamWarn,
        ProxyCatalog catalog) {

    /** The shipped value of each line, a reference into the catalogue. */
    static final String UNKNOWN_COMMAND = "@proxy.command.unknown";

    static final String NO_PERMISSION = "@proxy.command.no-permission";
    static final String PLUGIN_HIDDEN = "@proxy.command.hidden";
    static final String SPAM_KICK = "@proxy.command.spam-kick";
    static final String SPAM_BLOCKED = "@proxy.command.spam-blocked";
    static final String SPAM_WARN = "@proxy.command.spam-warn";

    // The English lines earlier versions shipped, in the config file and as the fallback here. A value still holding
    // one was never edited, so it reads the catalogue line that replaced it rather than English for everybody.
    private static final Map<String, Set<String>> SHIPPED = Map.of(
            UNKNOWN_COMMAND,
                    Set.of("<red>Unknown command.", "<red>Unknown command. Type /help for a list of commands."),
            NO_PERMISSION, Set.of("<red>You do not have permission to use that command."),
            PLUGIN_HIDDEN, Set.of("<red>Unknown command.", "<red>Unknown command. Type /help for a list of commands."),
            SPAM_KICK, Set.of("<red>You are sending commands too quickly."),
            SPAM_BLOCKED, Set.of("<red>You are sending commands too quickly. Please slow down."),
            SPAM_WARN, Set.of("<yellow>You are sending commands quickly, please slow down."));

    // The backend's shipped role colours. The proxy runs on its own machine and cannot read the backend's theme file,
    // so a line written for both reads the same by default.
    private static final TagResolver ROLES = TagResolver.resolver(
            role("accent", 0x38b6ff),
            role("value", 0x8fd9ff),
            role("body", 0xffffff),
            role("muted", 0x93a4b3),
            role("dim", 0x6b7886),
            role("good", 0x5be38c),
            role("bad", 0xff6b6b),
            role("warn", 0xffc93c));

    public ProxyCommandMessages {
        Objects.requireNonNull(unknownCommand, "unknownCommand");
        Objects.requireNonNull(noPermission, "noPermission");
        Objects.requireNonNull(pluginHidden, "pluginHidden");
        Objects.requireNonNull(spamKick, "spamKick");
        Objects.requireNonNull(spamBlocked, "spamBlocked");
        Objects.requireNonNull(spamWarn, "spamWarn");
        Objects.requireNonNull(catalog, "catalog");
    }

    /** Read the {@code messages} block from {@code config}, each key falling back to its catalogue reference. */
    public static ProxyCommandMessages from(ConfigStore config, ProxyCatalog catalog) {
        Objects.requireNonNull(config, "config");
        return new ProxyCommandMessages(
                read(config, "messages.unknown-command", UNKNOWN_COMMAND),
                read(config, "messages.no-permission", NO_PERMISSION),
                read(config, "messages.plugin-hidden", PLUGIN_HIDDEN),
                read(config, "messages.spam-kick", SPAM_KICK),
                read(config, "messages.spam-blocked", SPAM_BLOCKED),
                read(config, "messages.spam-warn", SPAM_WARN),
                catalog);
    }

    /** The deny line to show on a blocked command, per {@code use-unknown-command-message}. */
    public Optional<Component> deny(boolean useUnknownCommandMessage, @Nullable Locale locale) {
        return render(useUnknownCommandMessage ? unknownCommand : noPermission, locale);
    }

    public Optional<Component> pluginHiddenComponent(@Nullable Locale locale) {
        return render(pluginHidden, locale);
    }

    public Optional<Component> spamKickComponent(@Nullable Locale locale) {
        return render(spamKick, locale);
    }

    public Optional<Component> spamBlockedComponent(@Nullable Locale locale) {
        return render(spamBlocked, locale);
    }

    public Optional<Component> spamWarnComponent(@Nullable Locale locale) {
        return render(spamWarn, locale);
    }

    /** {@code raw} as a reader in {@code locale} sees it, or empty when it says nothing. */
    private Optional<Component> render(String raw, @Nullable Locale locale) {
        String source = CatalogLine.parse(raw)
                .filter(reference -> catalog.declares(reference.key()))
                .map(reference -> fill(catalog.line(locale, reference.key()), reference.arguments()))
                .orElse(raw);
        if (source.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(MiniMessage.miniMessage().deserialize(source, ROLES));
    }

    private static String fill(String template, Map<String, String> arguments) {
        String filled = template;
        for (Map.Entry<String, String> argument : arguments.entrySet()) {
            filled = filled.replace("{" + argument.getKey() + "}", argument.getValue());
        }
        return filled;
    }

    private static String read(ConfigStore config, String path, String shipped) {
        String value = config.getString(path, shipped);
        return SHIPPED.getOrDefault(shipped, Set.of()).contains(value) ? shipped : value;
    }

    private static TagResolver role(String name, int rgb) {
        return Placeholder.styling(name, TextColor.color(rgb));
    }
}
