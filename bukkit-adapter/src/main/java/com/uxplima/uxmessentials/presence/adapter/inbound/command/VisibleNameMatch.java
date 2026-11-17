package com.uxplima.uxmessentials.presence.adapter.inbound.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.outbound.lookup.VisibleNetwork;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The player a query names for {@code /realname} and {@code /whois}, among those the sender may see here and on the
 * other backends. An account name wins over a display name, so a nick never hides the player whose name it copies: the
 * name spelled exactly here, then elsewhere, then the name in another case here, then elsewhere, and only then a
 * display name here (the display names of other backends stay there). Read on the global thread, where the online set
 * may be iterated.
 */
@NullMarked
final class VisibleNameMatch {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    /** Who a query named. */
    sealed interface Found {}

    /**
     * A player online here.
     *
     * @param player the player
     */
    record Here(Player player) implements Found {}

    /**
     * A player online on another backend.
     *
     * @param online the player and the backend they are on
     */
    record Elsewhere(OnlinePlayer online) implements Found {}

    private VisibleNameMatch() {}

    /** Who {@code query} names among the players {@code sender} may see, else {@code null}. */
    static @Nullable Found find(CommandSender sender, String query, VisibleNetwork network) {
        Objects.requireNonNull(network, "network");
        Player viewer = sender instanceof Player player ? player : null;
        List<Player> visible = new ArrayList<>();
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (viewer == null || viewer.canSee(target)) {
                visible.add(target);
            }
        }
        for (Player target : visible) {
            if (target.getName().equals(query)) {
                return new Here(target);
            }
        }
        Optional<OnlinePlayer> elsewhere = network.findElsewhere(query, sender);
        if (elsewhere.isPresent() && elsewhere.get().player().name().equals(query)) {
            return new Elsewhere(elsewhere.get());
        }
        String lower = query.toLowerCase(Locale.ROOT);
        for (Player target : visible) {
            if (target.getName().toLowerCase(Locale.ROOT).equals(lower)) {
                return new Here(target);
            }
        }
        if (elsewhere.isPresent()) {
            return new Elsewhere(elsewhere.get());
        }
        for (Player target : visible) {
            if (PLAIN.serialize(target.displayName()).toLowerCase(Locale.ROOT).equals(lower)) {
                return new Here(target);
            }
        }
        return null;
    }
}
