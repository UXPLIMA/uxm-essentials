package com.uxplima.uxmessentials.communication.adapter.inbound.listener;

import java.util.Locale;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.communication.domain.DeathCause;
import com.uxplima.uxmessentials.communication.domain.PlaceholderBindings;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the {@link PlaceholderBindings} a connection template is substituted against from a live Bukkit player
 * and event, on the player's region thread where the event fires. The tokens are runtime content: the player's
 * name and display name, the online count, the world. Never plugin {@code MessageKey} strings, so nothing here
 * is parity-checked.
 *
 * <p>Every value is escaped for MiniMessage before it is bound, because the template is parsed with the values in it
 * and a value can hold text a player wrote: a display name a nick or another plugin set, the name of a renamed weapon,
 * the vanilla death line with the item and entity names in it. Flattening a name to plain text keeps its own styling
 * out, but its words can still be a tag, so without the escape an anvil-renamed sword could put a click into a line
 * every player reads. The bare {@code {player}} carries the account name, escaped the same way. The translation from a
 * live {@code Player} to tokens lives here so the listener stays focused on the policy decision.
 */
@NullMarked
final class ConnectionPlaceholders {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private ConnectionPlaceholders() {}

    /** Tokens for a join or quit: {@code player}, {@code displayname}, {@code count}, {@code world}. */
    static PlaceholderBindings connection(Player player, int onlineCount) {
        return connection(
                player.getName(),
                PLAIN.serialize(player.displayName()),
                onlineCount,
                player.getWorld().getName());
    }

    /** {@link #connection(Player, int)} over the values themselves, each escaped as it is bound. */
    static PlaceholderBindings connection(String name, String displayName, int onlineCount, String world) {
        return PlaceholderBindings.empty()
                .with("player", StyledText.escape(name))
                .with("displayname", StyledText.escape(displayName))
                .with("count", Integer.toString(onlineCount))
                .with("world", StyledText.escape(world));
    }

    /**
     * Tokens for a death: the connection tokens plus the rendered vanilla death line as {@code message}, the
     * killer's name as {@code killer}, the killer's weapon as {@code killer_weapon}, and the classified death
     * {@code cause} as its lowercased key ({@code fall}, {@code pvp}, {@code lava}, …) so a template can read the
     * cause even under the default policy. {@code killer} and {@code killer_weapon} are the empty string when no
     * player dealt the blow / no weapon applies, so neither token ever renders raw in a template.
     */
    static PlaceholderBindings death(
            Player player, int onlineCount, DeathCause cause, String deathMessage, String killer, String killerWeapon) {
        return withDeath(connection(player, onlineCount), cause, deathMessage, killer, killerWeapon);
    }

    /** The death tokens on top of {@code connection}, each escaped as it is bound. */
    static PlaceholderBindings withDeath(
            PlaceholderBindings connection, DeathCause cause, String deathMessage, String killer, String killerWeapon) {
        return connection
                .with("message", StyledText.escape(deathMessage))
                .with("killer", StyledText.escape(killer))
                .with("killer_weapon", StyledText.escape(killerWeapon))
                .with("cause", cause.name().toLowerCase(Locale.ROOT));
    }
}
