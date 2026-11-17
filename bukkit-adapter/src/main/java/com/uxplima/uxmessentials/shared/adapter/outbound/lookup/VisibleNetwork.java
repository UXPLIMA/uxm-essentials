package com.uxplima.uxmessentials.shared.adapter.outbound.lookup;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.ToIntFunction;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.networkpresence.NetworkPlayers;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The players on the other backends of the network a command sender may see, for the lists, pickers and suggestions
 * that name the players online. The players on this backend stay with each caller, judged by Bukkit's own
 * {@code canSee}; this adds the others, judged by the sender's vanish see level against what each backend announced.
 * The console sees everyone. Memory and a permission read only, fit for the tick and for a keystroke.
 */
@NullMarked
public final class VisibleNetwork {

    /** No other backends: a server off the network, and the answer for tests that need none. */
    public static final VisibleNetwork NONE = new VisibleNetwork(null, player -> 0);

    private final @Nullable NetworkPlayers players;
    private final ToIntFunction<PlayerRef> seeLevel;

    private VisibleNetwork(@Nullable NetworkPlayers players, ToIntFunction<PlayerRef> seeLevel) {
        this.players = players;
        this.seeLevel = Objects.requireNonNull(seeLevel, "seeLevel");
    }

    /**
     * The other backends' players, each judged against a viewer's see level.
     *
     * @param players the players of the other backends
     * @param seeLevel the vanish see level of a player on this backend
     */
    public static VisibleNetwork of(NetworkPlayers players, ToIntFunction<PlayerRef> seeLevel) {
        return new VisibleNetwork(Objects.requireNonNull(players, "players"), seeLevel);
    }

    /** The players on other backends {@code viewer} may see, ordered by name; the console sees all of them. */
    public List<OnlinePlayer> elsewhere(@Nullable CommandSender viewer) {
        return players == null ? List.of() : players.elsewhere(levelOf(viewer));
    }

    /** The names of {@link #elsewhere}. */
    public List<String> namesElsewhere(@Nullable CommandSender viewer) {
        List<String> names = new ArrayList<>();
        for (OnlinePlayer online : elsewhere(viewer)) {
            names.add(online.player().name());
        }
        return names;
    }

    /** The staff on other backends {@code viewer} may see, ordered by name. */
    public List<OnlinePlayer> staffElsewhere(@Nullable CommandSender viewer) {
        return players == null ? List.of() : players.staffElsewhere(levelOf(viewer));
    }

    /** How many staff are on other backends, hidden ones too. */
    public int staffElsewhereCount() {
        return players == null ? 0 : players.staffElsewhereCount();
    }

    /** The player on another backend under {@code name} that {@code viewer} may see, if any. */
    public Optional<OnlinePlayer> findElsewhere(String name, @Nullable CommandSender viewer) {
        Objects.requireNonNull(name, "name");
        return players == null ? Optional.empty() : players.findElsewhere(name, levelOf(viewer));
    }

    /** Every player on another backend, hidden ones too: the audience of something sent to everyone online. */
    public List<PlayerRef> everyoneElsewhere() {
        List<PlayerRef> everyone = new ArrayList<>();
        if (players != null) {
            for (OnlinePlayer online : players.elsewhere(NetworkPlayers.SEES_ALL)) {
                everyone.add(online.player());
            }
        }
        return everyone;
    }

    private int levelOf(@Nullable CommandSender viewer) {
        return viewer instanceof Player player
                ? seeLevel.applyAsInt(BukkitRefs.toRef(player))
                : NetworkPlayers.SEES_ALL;
    }
}
