package com.uxplima.uxmessentials.presence.adapter.inbound.command;

import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The online player a query names for {@code /realname} and {@code /whois}, among those the sender may see. An account
 * name wins over a display name, so a nick never hides the player whose name it copies: the name spelled exactly
 * first, then the name in another case, and only then a display name. Read on the global thread, where the online set
 * may be iterated.
 */
@NullMarked
final class VisibleNameMatch {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private VisibleNameMatch() {}

    /** The online player {@code query} names that {@code sender} may see, else {@code null}. */
    static @Nullable Player find(CommandSender sender, String query) {
        Player viewer = sender instanceof Player player ? player : null;
        @Nullable Player caseless = null;
        @Nullable Player displayed = null;
        String lower = query.toLowerCase(Locale.ROOT);
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (viewer != null && !viewer.canSee(target)) {
                continue;
            }
            String name = target.getName();
            if (name.equals(query)) {
                return target;
            }
            if (caseless == null && name.toLowerCase(Locale.ROOT).equals(lower)) {
                caseless = target;
            } else if (displayed == null
                    && PLAIN.serialize(target.displayName())
                            .toLowerCase(Locale.ROOT)
                            .equals(lower)) {
                displayed = target;
            }
        }
        return caseless != null ? caseless : displayed;
    }
}
