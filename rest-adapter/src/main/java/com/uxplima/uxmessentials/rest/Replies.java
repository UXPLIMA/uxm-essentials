package com.uxplima.uxmessentials.rest;

import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

import org.bukkit.command.CommandSender;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;

/**
 * The {@code /uxmapi} replies: lines of the host's catalogue when the host can read them, plain English otherwise.
 *
 * <p>The add-on updates separately from the host, and not every host version has the developer API call that reads
 * the catalogue. Whether this host has it is looked up once, when the command is built, and a host without it gets
 * the English lines below; otherwise an operator on that host could make a token and never see its secret, which is
 * shown only once. The look-up asks for the method by name rather than waiting for a call to fail, so a fault inside a
 * host that does have it is not mistaken for an old host.
 */
final class Replies {

    /** How a line is asked of the host: the key and its placeholder values. */
    @FunctionalInterface
    interface Host {
        Component message(String key, Map<String, String> placeholders);
    }

    // The words an older host reads, the English catalogue lines in the colours they used before the catalogue held
    // them.
    private static final Map<String, String> ENGLISH = Map.ofEntries(
            Map.entry("admin.rest.created", "<green>Token <label> created. It is shown once:"),
            Map.entry("admin.rest.exists", "<red>A token named <label> already exists."),
            Map.entry("admin.rest.no-scope", "<red>No scope in <scopes>. The scopes are <known>."),
            Map.entry("admin.rest.bad-label", "<red>A token needs a label."),
            Map.entry(
                    "admin.rest.list-empty",
                    "<gray>No API tokens yet. Make one with /uxmapi token create and a label."),
            Map.entry("admin.rest.list-header", "<green><count> API token(s):"),
            Map.entry("admin.rest.list-entry", "<gray>  <label>  [<scopes>]  <created>"),
            Map.entry("admin.rest.revoked", "<green>Token <label> revoked."),
            Map.entry("admin.rest.not-found", "<red>No token named <label>."),
            Map.entry("admin.rest.off", "<red>The REST API is not listening. Turn it on in <file> and restart."),
            Map.entry("admin.rest.listening", "<green>REST API listening on <bind>:<port>"),
            Map.entry("admin.rest.connections", "<gray>  <tokens> token(s), <streams> event stream(s) open"));

    private static final String SECRET = "admin.rest.secret";

    private final boolean hostSpeaks;

    /**
     * @param hostSpeaks whether the host has the call that reads its catalogue, as {@link #hostHasMessages} answers
     * @param log where an older host is reported, once
     */
    Replies(boolean hostSpeaks, Logger log) {
        Objects.requireNonNull(log, "log");
        this.hostSpeaks = hostSpeaks;
        if (!hostSpeaks) {
            log.info("uxmEssentials is older than this add-on's message lines, so /uxmapi replies in English."
                    + " Update uxmEssentials to read them in each operator's language.");
        }
    }

    /** Whether the running host's developer API has the call that reads its catalogue. */
    static boolean hostHasMessages() {
        try {
            UxmEssentialsApi.class.getMethod("message", CommandSender.class, String.class, Map.class);
            return true;
        } catch (NoSuchMethodException olderHost) {
            return false;
        }
    }

    /** {@code key} as the host reads it, or in English when the host is too old to. */
    Component line(Host host, String key, Map<String, String> placeholders) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(placeholders, "placeholders");
        return hostSpeaks ? host.message(key, placeholders) : english(key, placeholders);
    }

    private static Component english(String key, Map<String, String> placeholders) {
        if (key.equals(SECRET)) {
            String secret = placeholders.getOrDefault("secret", "");
            return Component.text(secret, NamedTextColor.YELLOW)
                    .clickEvent(ClickEvent.copyToClipboard(secret))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to copy")));
        }
        TagResolver.Builder values = TagResolver.builder();
        placeholders.forEach((name, value) -> values.resolver(Placeholder.unparsed(name, value)));
        return MiniMessage.miniMessage().deserialize(ENGLISH.getOrDefault(key, key), values.build());
    }
}
