package com.uxplima.uxmessentials.shared.application.networkpresence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.domain.NameCase;
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
    private final Supplier<NameCase> names;

    /** @param names how names are told apart, asked on every lookup since it can change while the server runs */
    public NetworkPlayers(NetworkPresence presence, Supplier<NameCase> names) {
        this.presence = Objects.requireNonNull(presence, "presence");
        this.names = Objects.requireNonNull(names, "names");
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

    /**
     * The player on another backend with this name that a viewer at {@code seeLevel} sees, if any. The name spelled
     * exactly wins over any other case. Where names differ by case only between accounts ({@link NameCase#SENSITIVE})
     * only the exact spelling answers, since another spelling is another player. Otherwise a name has one owner in any
     * case: a player here under it holds it, so nobody elsewhere answers, and a lone player elsewhere under the name in
     * another case does, while several are no answer.
     */
    public Optional<OnlinePlayer> findElsewhere(String name, int seeLevel) {
        Objects.requireNonNull(name, "name");
        String wanted = name.strip();
        if (wanted.isEmpty()) {
            return Optional.empty();
        }
        boolean sensitive = names.get() == NameCase.SENSITIVE;
        if (!sensitive
                && presence.findByName(wanted)
                        .filter(online -> online.server().equals(presence.localServer()))
                        .isPresent()) {
            return Optional.empty();
        }
        List<OnlinePlayer> remote = remote();
        for (OnlinePlayer online : remote) {
            if (online.player().name().equals(wanted)) {
                return seen(online, seeLevel);
            }
        }
        if (sensitive) {
            return Optional.empty();
        }
        OnlinePlayer caseless = null;
        for (OnlinePlayer online : remote) {
            if (online.player().name().equalsIgnoreCase(wanted)) {
                if (caseless != null
                        && !caseless.player().uuid().equals(online.player().uuid())) {
                    return Optional.empty();
                }
                caseless = online;
            }
        }
        return caseless == null ? Optional.empty() : seen(caseless, seeLevel);
    }

    private Optional<OnlinePlayer> seen(OnlinePlayer online, int seeLevel) {
        return traits(online).visibleTo(seeLevel) ? Optional.of(online) : Optional.empty();
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
