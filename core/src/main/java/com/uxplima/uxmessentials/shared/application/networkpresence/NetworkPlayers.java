package com.uxplima.uxmessentials.shared.application.networkpresence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.PresenceTraits;

/**
 * The players on the other backends of the network that a viewer may see, for the lists and suggestions that name the
 * players online. A player on this backend is judged here, by this backend's own vanish; this answers only for the
 * others, from the traits each backend announced with its players.
 *
 * <p>A viewer is described by their see level: a player vanished deeper than it is left out. A player whose backend
 * announced no traits counts as hidden at {@link PresenceTraits#UNKNOWN_LEVEL}, so only a viewer who sees every
 * vanished player sees them. Reads memory only, fit for the tick and for a keystroke.
 */
public final class NetworkPlayers {

    /** The see level of a viewer who sees every player, the console. */
    public static final int SEES_ALL = Integer.MAX_VALUE;

    private static final Comparator<OnlinePlayer> BY_NAME =
            Comparator.comparing(online -> online.player().name().toLowerCase(Locale.ROOT));

    private final NetworkPresence presence;

    public NetworkPlayers(NetworkPresence presence) {
        this.presence = Objects.requireNonNull(presence, "presence");
    }

    /** The players on other backends a viewer at {@code seeLevel} sees, one per account, ordered by name. */
    public List<OnlinePlayer> elsewhere(int seeLevel) {
        List<OnlinePlayer> seen = new ArrayList<>();
        for (OnlinePlayer online : remote()) {
            if (traits(online).visibleTo(seeLevel)) {
                seen.add(online);
            }
        }
        seen.sort(BY_NAME);
        return List.copyOf(seen);
    }

    /** The staff on other backends a viewer at {@code seeLevel} sees, ordered by name. */
    public List<OnlinePlayer> staffElsewhere(int seeLevel) {
        List<OnlinePlayer> staff = new ArrayList<>();
        for (OnlinePlayer online : elsewhere(seeLevel)) {
            if (traits(online).staff()) {
                staff.add(online);
            }
        }
        return List.copyOf(staff);
    }

    /** How many staff are on other backends, hidden ones too, as a count of this backend's staff counts them. */
    public int staffElsewhereCount() {
        int count = 0;
        for (OnlinePlayer online : remote()) {
            if (traits(online).staff()) {
                count++;
            }
        }
        return count;
    }

    /** The player on another backend with this name that a viewer at {@code seeLevel} sees, if any. */
    public Optional<OnlinePlayer> findElsewhere(String name, int seeLevel) {
        Objects.requireNonNull(name, "name");
        return presence.findByName(name)
                .filter(online -> !online.server().equals(presence.localServer()))
                .filter(online -> traits(online).visibleTo(seeLevel));
    }

    private List<OnlinePlayer> remote() {
        String here = presence.localServer();
        List<OnlinePlayer> remote = new ArrayList<>();
        for (OnlinePlayer online : presence.online()) {
            if (!online.server().equals(here)) {
                remote.add(online);
            }
        }
        return remote;
    }

    private PresenceTraits traits(OnlinePlayer online) {
        return presence.traits(online.player().uuid()).orElse(PresenceTraits.UNKNOWN);
    }
}
