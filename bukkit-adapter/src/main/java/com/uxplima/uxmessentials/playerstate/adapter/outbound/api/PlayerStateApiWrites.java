package com.uxplima.uxmessentials.playerstate.adapter.outbound.api;

import java.util.Objects;

import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.playerstate.application.Feed;
import com.uxplima.uxmessentials.playerstate.application.Heal;
import com.uxplima.uxmessentials.playerstate.application.SetGamemode;
import com.uxplima.uxmessentials.playerstate.application.SetSpeed;
import com.uxplima.uxmessentials.playerstate.application.ToggleFly;
import com.uxplima.uxmessentials.playerstate.application.ToggleGod;
import org.jspecify.annotations.NullMarked;

/**
 * The six player-state use cases the published API runs, out of the two dozen the module assembles.
 *
 * <p>The very instances behind the commands: what a plugin sets and what a staff member sets are one write, seen by
 * the same listeners. Named as its own value so the published surface says which six it can reach, rather than
 * being handed everything and being trusted to take only part of it.
 *
 * @param god {@code /god}
 * @param fly {@code /fly}
 * @param gameMode {@code /gamemode}
 * @param speed {@code /speed}, {@code /walkspeed}, {@code /flyspeed}
 * @param heal {@code /heal}
 * @param feed {@code /feed}
 */
@NullMarked
public record PlayerStateApiWrites(
        ToggleGod god, ToggleFly fly, SetGamemode gameMode, SetSpeed speed, Heal heal, Feed feed) {

    public PlayerStateApiWrites {
        Objects.requireNonNull(god, "god");
        Objects.requireNonNull(fly, "fly");
        Objects.requireNonNull(gameMode, "gameMode");
        Objects.requireNonNull(speed, "speed");
        Objects.requireNonNull(heal, "heal");
        Objects.requireNonNull(feed, "feed");
    }

    /** The six as the module built them. */
    public static PlayerStateApiWrites of(PlayerStateServices services) {
        Objects.requireNonNull(services, "services");
        return new PlayerStateApiWrites(
                services.toggleGod(),
                services.toggleFly(),
                services.setGamemode(),
                services.setSpeed(),
                services.heal(),
                services.feed());
    }
}
