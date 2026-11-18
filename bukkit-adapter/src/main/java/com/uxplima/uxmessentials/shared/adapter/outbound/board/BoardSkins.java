package com.uxplima.uxmessentials.shared.adapter.outbound.board;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import com.uxplima.uxmlib.board.Board;
import com.uxplima.uxmlib.board.BoardPeriod;
import com.uxplima.uxmlib.board.Leaderboard;
import com.uxplima.uxmlib.board.StoredSkin;

/**
 * The skins board heads are drawn from, held in memory because a head is answered while a line is drawn.
 *
 * <p>Only an account Mojang did not make is kept: an offline-mode one (a version 3 uuid) or a Bedrock one through
 * Floodgate (version 0). The client draws a Mojang account by its name, but would draw one of these as whoever holds
 * the name at Mojang, or as nobody, so its own skin is the only way to show it.
 *
 * <p>Two things fill it. A player here is held with the skin they wear, as they join and whenever it changes. A
 * player a board shows who is not here is read from the skin store by {@link #warm}, on the async lane after each
 * refresh. Whoever is neither here nor shown is let go, so the memory follows the boards rather than every player
 * who ever joined.
 */
public final class BoardSkins {

    private static final int MOJANG_UUID_VERSION = 4;

    private final Map<UUID, StoredSkin> held = new ConcurrentHashMap<>();
    private final Set<UUID> here = ConcurrentHashMap.newKeySet();
    private volatile Set<UUID> shown = Set.of();

    /** The skin held for {@code player}, read from memory alone, so it can answer while a line is drawn. */
    public Optional<StoredSkin> of(UUID player) {
        return Optional.ofNullable(held.get(Objects.requireNonNull(player, "player")));
    }

    /** {@code player} joined wearing {@code worn}, read off their profile, or nothing when it carries no skin. */
    public void arrived(UUID player, Optional<SkinTexture> worn) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(worn, "worn");
        here.add(player);
        worn.ifPresent(texture -> wears(player, texture));
    }

    /** {@code player} wears {@code texture} from now on: their skin changed while they are here. */
    public void wears(UUID player, SkinTexture texture) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(texture, "texture");
        if (drawnBySkin(player)) {
            held.put(player, new StoredSkin(texture.value(), texture.signature()));
        }
    }

    /** {@code player} left. Their skin stays while a board shows them, since the store may not hold it. */
    public void left(UUID player) {
        Objects.requireNonNull(player, "player");
        here.remove(player);
        if (!shown.contains(player)) {
            held.remove(player);
        }
    }

    /**
     * Hold the stored skin of every player in {@code holders} who is not here, and let go of whoever is neither here
     * nor among them. A holder here keeps the skin they wear, and one the store holds nothing for keeps the skin they
     * last wore here, if any.
     *
     * @param holders the players the boards show, as {@link #shownOn} reads them
     * @param stored the skin the store holds for a player; it may block, so this runs on the async lane
     */
    public void warm(Set<UUID> holders, Function<UUID, Optional<SkinTexture>> stored) {
        Objects.requireNonNull(holders, "holders");
        Objects.requireNonNull(stored, "stored");
        Set<UUID> kept = Set.copyOf(holders);
        shown = kept;
        for (UUID holder : kept) {
            if (drawnBySkin(holder) && !here.contains(holder)) {
                stored.apply(holder)
                        .ifPresent(texture -> held.put(holder, new StoredSkin(texture.value(), texture.signature())));
            }
        }
        held.keySet().removeIf(player -> !kept.contains(player) && !here.contains(player));
    }

    /** Every player that {@code leaderboard} shows in the first {@code size} places of a board in a running period. */
    public static Set<UUID> shownOn(Leaderboard leaderboard, int size) {
        Objects.requireNonNull(leaderboard, "leaderboard");
        Set<UUID> shown = new HashSet<>();
        for (Board board : leaderboard.boards()) {
            for (BoardPeriod period : BoardPeriod.values()) {
                if (!leaderboard.runs(board, period)) {
                    continue;
                }
                for (int place = 1; place <= size; place++) {
                    Optional<Leaderboard.Row> row = leaderboard.at(board, period, place);
                    if (row.isEmpty()) {
                        break;
                    }
                    shown.add(row.get().player());
                }
            }
        }
        return shown;
    }

    /** Whether a head of {@code player} needs their skin: Mojang did not make the account. */
    private static boolean drawnBySkin(UUID player) {
        return player.version() != MOJANG_UUID_VERSION;
    }
}
