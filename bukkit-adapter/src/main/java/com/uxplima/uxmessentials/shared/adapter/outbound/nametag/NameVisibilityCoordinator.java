package com.uxplima.uxmessentials.shared.adapter.outbound.nametag;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import org.jspecify.annotations.NullMarked;

/**
 * Hides the vanilla above-head player name for wearers who carry an active custom nametag, by parking their name in a
 * dedicated {@code uxm-namehide} scoreboard {@link Team} whose name-tag visibility is {@code NEVER}. The custom nametag
 * is a packet text-display floating above the head; without this coordinator the vanilla name shows alongside it (a
 * double name), so the nametags context was shipped disabled until this plumbing existed.
 *
 * <h2>Coexisting with per-player scoreboards</h2>
 *
 * The scoreboard context hands each player a fresh per-player Bukkit {@link Scoreboard} and re-{@code setScoreboard}s on
 * every sidebar create. {@code setScoreboard} resets the client's team registry, so a name-hiding team must live on the
 * player's <em>current</em> board and be re-applied after every board switch — that is exactly what the scoreboard
 * module's board-switch callback drives into {@link #reapply}. When the scoreboard module is off the player stays on the
 * main board and {@link #hide} applies the team there once; either way the same {@link #reapply} reconciles the team and
 * the player's membership against the current board.
 *
 * <h2>Threading</h2>
 *
 * Every method that touches a live {@link Scoreboard}/{@link Team} ({@link #hide}, {@link #show}, {@link #reapply},
 * {@link #clear(Player)}) assumes it runs on the player's region/entity thread — every caller (the presenter's
 * show/update on the wearer's entity thread, the scoreboard board-switch callback, which fires from the per-player
 * render hop, and the quit handler, which fires on the quitting player's region thread) is already there.
 * {@link #clear(UUID)} is a pure map mutation and is safe from any thread, for an offline or cross-thread caller that
 * cannot reach the board. The hidden set is the project's per-player concurrent map.
 */
@NullMarked
public final class NameVisibilityCoordinator {

    /** The team that parks hidden wearers' names so the vanilla above-head name never renders for them. */
    public static final String TEAM_NAME = "uxm-namehide";

    /**
     * The wearers whose vanilla name should be hidden, by UUID. A {@link ConcurrentHashMap}-backed set keeps the
     * project's "every player-keyed map is concurrent" convention; a membership decides whether {@link #reapply} adds or
     * removes the player's entry from the hide-team on the board it is reconciling.
     */
    private final Set<UUID> hidden = ConcurrentHashMap.newKeySet();

    /** Mark {@code player} hidden and apply the hide-team to their current board. Run on the player's region thread. */
    public void hide(Player player) {
        Objects.requireNonNull(player, "player");
        hidden.add(player.getUniqueId());
        reapply(player, player.getScoreboard());
    }

    /** Unmark {@code player} and drop their name from the hide-team on their current board. Run on the region thread. */
    public void show(Player player) {
        Objects.requireNonNull(player, "player");
        hidden.remove(player.getUniqueId());
        reapply(player, player.getScoreboard());
    }

    /**
     * Reconcile the hide-team on {@code board} with {@code player}'s hidden state: ensure the {@code uxm-namehide} team
     * exists with name-tag visibility {@code NEVER}, then add the player's name as an entry when they are hidden or drop
     * it when they are not. Idempotent, so the scoreboard board-switch callback may call it every switch to survive the
     * {@code setScoreboard} team-registry reset. Run on the player's region thread.
     */
    public void reapply(Player player, Scoreboard board) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(board, "board");
        Team team = teamOn(board);
        String entry = player.getName();
        if (hidden.contains(player.getUniqueId())) {
            if (!team.hasEntry(entry)) {
                team.addEntry(entry);
            }
        } else if (team.hasEntry(entry)) {
            team.removeEntry(entry);
        }
    }

    /**
     * Drop {@code uuid} from the hidden set without touching any board. For an offline or cross-thread caller that holds
     * only the UUID — it cannot safely reach the player's board, so it clears the bookkeeping only and relies on the
     * relog re-evaluation. A caller that still holds the live player on its region thread should prefer
     * {@link #clear(Player)}, which also drops the stranded team entry: the hide-team on the main shared board is a
     * server-lifetime singleton, so its entries do <em>not</em> die when the player leaves.
     */
    public void clear(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        hidden.remove(uuid);
    }

    /**
     * Drop {@code player} from the hidden set and remove their name from the hide-team on their current board. Run on
     * the player's region thread (it touches a live {@link Team}); the quit handler is the intended caller, where the
     * player is still online and the board/entry are still valid. The board mutation is needed because the main shared
     * board is a server-lifetime singleton whose team entries survive the quit — leaving the entry would strand the
     * vanilla name hidden and leak a dead name string into the team over uptime.
     */
    public void clear(Player player) {
        Objects.requireNonNull(player, "player");
        hidden.remove(player.getUniqueId());
        Team team = player.getScoreboard().getTeam(TEAM_NAME);
        String entry = player.getName();
        if (team != null && team.hasEntry(entry)) {
            team.removeEntry(entry);
        }
    }

    /** Whether {@code uuid} is currently marked hidden. Test/observability seam. */
    public boolean isHidden(UUID uuid) {
        return hidden.contains(uuid);
    }

    /**
     * The {@code uxm-namehide} team on {@code board}, registering it if absent. A re-register throws, so the lookup
     * guards the registration; the team is (re-)set to never show name-tags so a freshly registered team and a board
     * that somehow carried a stale team both end up with the invariant the coordinator relies on. The non-deprecated
     * {@code setOption(NAME_TAG_VISIBILITY, NEVER)} is used because {@code setNameTagVisibility} is deprecated on the
     * Paper line this targets.
     */
    private static Team teamOn(Scoreboard board) {
        Team existing = board.getTeam(TEAM_NAME);
        Team team = existing != null ? existing : board.registerNewTeam(TEAM_NAME);
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        return team;
    }
}
