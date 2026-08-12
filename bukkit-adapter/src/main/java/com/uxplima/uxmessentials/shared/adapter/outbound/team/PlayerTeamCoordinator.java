package com.uxplima.uxmessentials.shared.adapter.outbound.team;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import net.kyori.adventure.text.format.NamedTextColor;

import com.uxplima.uxmessentials.playerstate.domain.GlowColor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The one place a player is put into a scoreboard {@link Team}, because vanilla ties two unrelated features to team
 * membership and a player can only belong to one team per board:
 *
 * <ul>
 *   <li><b>Name hiding.</b> A wearer carrying a custom nametag (a packet text-display above their head) must have their
 *       vanilla above-head name suppressed, which is a team whose name-tag visibility is {@code NEVER}.
 *   <li><b>Glow colour.</b> {@code /glow <colour>} draws the outline in the colour of the player's team, so a coloured
 *       outline is a team carrying that colour.
 * </ul>
 *
 * <p>Both states are held here and the team is derived from the pair, so a hidden wearer can still glow red. The
 * hidden-and-uncoloured case keeps the historical {@code uxm-namehide} team name: the main board is persisted in
 * {@code scoreboard.dat}, so renaming it would strand entries and leave those players nameless forever.
 *
 * <h2>Coexisting with per-player scoreboards</h2>
 *
 * The scoreboard context hands each player a fresh per-player Bukkit {@link Scoreboard} and re-{@code setScoreboard}s on
 * every sidebar create. {@code setScoreboard} resets the client's team registry, so membership must live on the
 * player's <em>current</em> board and be re-applied after every board switch, which is what the scoreboard module's
 * board-switch callback drives into {@link #reapply}. When the scoreboard module is off the player stays on the main
 * board and the team is applied there once; either way {@link #reapply} reconciles the team and the player's membership
 * against the current board.
 *
 * <h2>Threading</h2>
 *
 * Every method that touches a live {@link Scoreboard}/{@link Team} ({@link #hide}, {@link #show}, {@link #colour},
 * {@link #reapply}, {@link #clear(Player)}) assumes it runs on the player's region/entity thread, which every caller
 * already is: the nametag presenter's show/update, the scoreboard board-switch callback, the glow effect adapter (which
 * hops to the entity thread), and the join/quit handlers. {@link #clear(UUID)} is a pure map mutation and is safe from
 * any thread, for an offline or cross-thread caller that cannot reach the board.
 */
@NullMarked
public final class PlayerTeamCoordinator {

    /** The team a hidden wearer with no glow colour sits in. Unchanged since before colours existed. */
    public static final String TEAM_NAME = "uxm-namehide";

    /** Every team this coordinator owns starts with this prefix, so a foreign team is never touched. */
    private static final String PREFIX = "uxm-";

    /** The prefix of the coloured hidden teams, as {@code uxm-namehide-red}. */
    private static final String HIDDEN_COLOUR_PREFIX = TEAM_NAME + "-";

    /** The prefix of the visible coloured teams, as {@code uxm-glow-red}. */
    private static final String COLOUR_PREFIX = "uxm-glow-";

    /** Adventure's named colours, in the enum's own order, so a constant maps to the colour a team can carry. */
    private static final Map<GlowColor, NamedTextColor> COLOURS = Map.ofEntries(
            Map.entry(GlowColor.BLACK, NamedTextColor.BLACK),
            Map.entry(GlowColor.DARK_BLUE, NamedTextColor.DARK_BLUE),
            Map.entry(GlowColor.DARK_GREEN, NamedTextColor.DARK_GREEN),
            Map.entry(GlowColor.DARK_AQUA, NamedTextColor.DARK_AQUA),
            Map.entry(GlowColor.DARK_RED, NamedTextColor.DARK_RED),
            Map.entry(GlowColor.DARK_PURPLE, NamedTextColor.DARK_PURPLE),
            Map.entry(GlowColor.GOLD, NamedTextColor.GOLD),
            Map.entry(GlowColor.GRAY, NamedTextColor.GRAY),
            Map.entry(GlowColor.DARK_GRAY, NamedTextColor.DARK_GRAY),
            Map.entry(GlowColor.BLUE, NamedTextColor.BLUE),
            Map.entry(GlowColor.GREEN, NamedTextColor.GREEN),
            Map.entry(GlowColor.AQUA, NamedTextColor.AQUA),
            Map.entry(GlowColor.RED, NamedTextColor.RED),
            Map.entry(GlowColor.LIGHT_PURPLE, NamedTextColor.LIGHT_PURPLE),
            Map.entry(GlowColor.YELLOW, NamedTextColor.YELLOW),
            Map.entry(GlowColor.WHITE, NamedTextColor.WHITE));

    /**
     * The wearers whose vanilla above-head name should be hidden, by UUID. A {@link ConcurrentHashMap}-backed set keeps
     * the project's "every player-keyed map is concurrent" convention.
     */
    private final Set<UUID> hidden = ConcurrentHashMap.newKeySet();

    /**
     * The glow colour each player wears, by UUID. {@link GlowColor#DEFAULT} is never stored: it is the absence of a
     * colour, so clearing removes the entry rather than writing a sentinel.
     */
    private final Map<UUID, GlowColor> colours = new ConcurrentHashMap<>();

    /** Mark {@code player} hidden and apply their team on their current board. Run on the player's region thread. */
    public void hide(Player player) {
        Objects.requireNonNull(player, "player");
        hidden.add(player.getUniqueId());
        reapply(player, player.getScoreboard());
    }

    /** Unmark {@code player} and reconcile their team on their current board. Run on the region thread. */
    public void show(Player player) {
        Objects.requireNonNull(player, "player");
        hidden.remove(player.getUniqueId());
        reapply(player, player.getScoreboard());
    }

    /**
     * Draw {@code player}'s glowing outline in {@code colour}, or drop the colour for {@link GlowColor#DEFAULT}. The
     * outline itself is the entity's glowing flag, which the effects adapter owns; this only decides its colour. Run on
     * the player's region thread.
     */
    public void colour(Player player, GlowColor colour) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(colour, "colour");
        if (colour == GlowColor.DEFAULT) {
            colours.remove(player.getUniqueId());
        } else {
            colours.put(player.getUniqueId(), colour);
        }
        reapply(player, player.getScoreboard());
    }

    /** The colour {@code uuid} currently wears, or {@link GlowColor#DEFAULT} when they wear none. */
    public GlowColor colourOf(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        return colours.getOrDefault(uuid, GlowColor.DEFAULT);
    }

    /**
     * Reconcile {@code player}'s membership on {@code board}: drop them from any team of ours that is not the one their
     * current (hidden, colour) pair calls for, then add them to that team, registering it if the board does not carry it
     * yet. A player who is neither hidden nor coloured ends up in no team of ours at all. Idempotent, so the board-switch
     * callback may call it on every switch to survive the {@code setScoreboard} team-registry reset. Run on the player's
     * region thread.
     */
    public void reapply(Player player, Scoreboard board) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(board, "board");
        String entry = player.getName();
        @Nullable String wanted = teamNameFor(player.getUniqueId());
        for (Team team : board.getTeams()) {
            if (team.getName().startsWith(PREFIX) && !team.getName().equals(wanted) && team.hasEntry(entry)) {
                team.removeEntry(entry);
            }
        }
        if (wanted == null) {
            return;
        }
        Team team = teamOn(board, wanted, hidden.contains(player.getUniqueId()), colourOf(player.getUniqueId()));
        if (!team.hasEntry(entry)) {
            team.addEntry(entry);
        }
    }

    /**
     * Drop {@code uuid}'s hidden mark and glow colour without touching any board. For an offline or cross-thread caller
     * that holds only the UUID: it cannot safely reach the player's board, so it clears the bookkeeping only and relies
     * on the relog re-evaluation. A caller that still holds the live player on its region thread should prefer
     * {@link #clear(Player)}, which also drops the stranded team entry: the teams on the main shared board are
     * server-lifetime, so their entries do <em>not</em> die when the player leaves.
     */
    public void clear(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        hidden.remove(uuid);
        colours.remove(uuid);
    }

    /**
     * Drop {@code player}'s hidden mark and glow colour and remove their entry from every team of ours on their current
     * board. Run on the player's region thread; the join and quit handlers are the intended callers. On quit this stops
     * a dead name stranding in a persisted team; on join it clears a name that a previous uptime left behind, since
     * neither the glowing flag nor the colour survives a restart.
     */
    public void clear(Player player) {
        Objects.requireNonNull(player, "player");
        clear(player.getUniqueId());
        String entry = player.getName();
        for (Team team : player.getScoreboard().getTeams()) {
            if (team.getName().startsWith(PREFIX) && team.hasEntry(entry)) {
                team.removeEntry(entry);
            }
        }
    }

    /** Whether {@code uuid} is currently marked hidden. Test/observability seam. */
    public boolean isHidden(UUID uuid) {
        return hidden.contains(uuid);
    }

    /** The team {@code uuid}'s current state calls for, or {@code null} when they belong in none of ours. */
    private @Nullable String teamNameFor(UUID uuid) {
        boolean isHidden = hidden.contains(uuid);
        GlowColor colour = colourOf(uuid);
        if (colour == GlowColor.DEFAULT) {
            return isHidden ? TEAM_NAME : null;
        }
        return (isHidden ? HIDDEN_COLOUR_PREFIX : COLOUR_PREFIX) + colour.id();
    }

    /**
     * The named team on {@code board}, registering it if absent. A re-register throws, so the lookup guards the
     * registration; the options are (re-)set every time so a freshly registered team and a board that carried a stale
     * one both end up with the invariants membership relies on. The non-deprecated
     * {@code setOption(NAME_TAG_VISIBILITY, ...)} is used because {@code setNameTagVisibility} is deprecated on the
     * Paper line this targets.
     */
    private static Team teamOn(Scoreboard board, String name, boolean hidesNames, GlowColor colour) {
        Team existing = board.getTeam(name);
        Team team = existing != null ? existing : board.registerNewTeam(name);
        team.setOption(
                Team.Option.NAME_TAG_VISIBILITY, hidesNames ? Team.OptionStatus.NEVER : Team.OptionStatus.ALWAYS);
        team.color(COLOURS.get(colour));
        return team;
    }
}
