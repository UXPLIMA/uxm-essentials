package com.uxplima.uxmessentials.shared.adapter.outbound.hud;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.jspecify.annotations.NullMarked;

/**
 * Resolves a handful of built-in {@code {token}} placeholders against the live {@link Player} and server, so the
 * shipped scoreboard sidebar and tablist show real values out of the box <em>without</em> PlaceholderAPI installed.
 * Applied in the renderers — where the live player is in hand — as a distinct pass before the PlaceholderAPI bridge,
 * so a {@code {token}} and a {@code %papi%} placeholder never collide: this pass only ever rewrites the curly-brace
 * tokens it knows, leaving every {@code %…%} (and unknown {@code {…}}) untouched for PlaceholderAPI or MiniMessage.
 *
 * <p>This is the same convenience syntax the tablist name-format already used for {@code {player}}; that ad-hoc
 * substitution now routes through here so every rendered HUD source — scoreboard title and lines, tablist header,
 * footer, and name-format — resolves the same token set consistently.
 *
 * <p>Each token is substituted only when present in the source (a cheap {@code contains} check), so a source carrying
 * no token returns unchanged with no allocation.
 */
@NullMarked
public final class BuiltinTokens {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private BuiltinTokens() {}

    /** Replace every known built-in {@code {token}} in {@code source} with its live value for {@code player}. */
    public static String apply(Player player, String source) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(source, "source");
        if (source.indexOf('{') < 0) {
            return source; // no token can be present, so nothing to do
        }
        String out = source;
        out = replace(out, "{player}", player::getName);
        out = replace(out, "{displayname}", () -> displayName(player));
        out = replace(
                out,
                "{online}",
                () -> Integer.toString(Bukkit.getOnlinePlayers().size()));
        out = replace(out, "{max_players}", () -> Integer.toString(Bukkit.getMaxPlayers()));
        out = replace(out, "{world}", () -> player.getWorld().getName());
        out = replace(out, "{ping}", () -> Integer.toString(player.getPing()));
        return out;
    }

    /** Substitute {@code token} with the value the supplier yields, only when the token is present. */
    private static String replace(String source, String token, java.util.function.Supplier<String> value) {
        if (!source.contains(token)) {
            return source;
        }
        return source.replace(token, value.get());
    }

    /** The player's display name flattened to plain text, falling back to the account name. */
    private static String displayName(Player player) {
        String plain = PLAIN.serialize(player.displayName());
        return plain.isEmpty() ? player.getName() : plain;
    }
}
