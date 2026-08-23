package com.uxplima.uxmessentials.scoreboard.adapter.outbound;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.scoreboard.application.port.ScoreboardVisibilityStore;
import com.uxplima.uxmessentials.scoreboard.domain.DisplayContent;
import com.uxplima.uxmessentials.scoreboard.domain.SidebarBoard;
import com.uxplima.uxmessentials.scoreboard.domain.SidebarConfig;
import com.uxplima.uxmessentials.scoreboard.domain.SidebarLine;
import com.uxplima.uxmessentials.scoreboard.domain.SidebarNumberFormat;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.BuiltinTokens;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.HudText;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import com.uxplima.uxmessentials.shared.display.ConditionContext;
import com.uxplima.uxmessentials.shared.display.ConditionParser;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.packet.scoreboard.ScoreboardDisplaySlot;
import com.uxplima.uxmlib.packet.scoreboard.ScoreboardNumberFormat;
import com.uxplima.uxmlib.packet.scoreboard.ScoreboardObjective;
import com.uxplima.uxmlib.packet.scoreboard.ScoreboardPacketEvent;
import com.uxplima.uxmlib.packet.scoreboard.ScoreboardPackets;
import com.uxplima.uxmlib.packet.scoreboard.ScoreboardScore;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Packet-native, ownership-aware modern sidebar renderer. It never replaces {@link Player#getScoreboard()}, so
 * nametag, glow and other plugins' server-side scoreboard state remain intact. Stable line ids are used as protocol
 * holders while the visible text travels independently in the modern score packet; conditional lines can therefore
 * move without changing identity and literal empty spacer rows remain distinct.
 *
 * <p>The authored catalog may contain up to 128 candidates. Conditions and optional empty-value suppression run per
 * viewer first, then the first 15 visible rows form the frame. Reconciliation emits only objective/title/row changes
 * and bundles a frame into one outbound write. A foreign sidebar display packet puts the session into yielded state;
 * rendering pauses until that objective is cleared or removed, at which point the caller schedules one fresh paint.
 */
@NullMarked
public final class ScoreboardRenderer {

    public static final String OBJECTIVE_NAME = "uxmsb";
    private static final int MAX_VISIBLE_LINES = 15;
    private static final String CONDITION_SEPARATOR = " | ";

    private final ScoreboardPackets packets;
    private final ScoreboardVisibilityStore visibility;
    private final Supplier<SidebarConfig> boards;
    private final AnimationRegistry animations;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, String> appliedBoard = new ConcurrentHashMap<>();
    private final Map<UUID, String> foreignObjectives = new ConcurrentHashMap<>();

    public ScoreboardRenderer(
            ScoreboardPackets packets,
            ScoreboardVisibilityStore visibility,
            Supplier<SidebarConfig> boards,
            AnimationRegistry animations) {
        this.packets = Objects.requireNonNull(packets, "packets");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.boards = Objects.requireNonNull(boards, "boards");
        this.animations = Objects.requireNonNull(animations, "animations");
    }

    public Optional<String> appliedBoard(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return Optional.ofNullable(appliedBoard.get(who.uuid()));
    }

    /** Render or reconcile one player's sidebar. Must run on the player's owning thread. */
    public void renderFor(Player player) {
        Objects.requireNonNull(player, "player");
        UUID uuid = player.getUniqueId();
        PlayerRef who = BukkitRefs.toRef(player);
        ConditionContext context = conditionContext(player);
        Optional<SidebarBoard> selected = boards.get().select(context);
        if (selected.isEmpty() || visibility.hidden(who)) {
            clear(player);
            return;
        }
        SidebarBoard board = selected.orElseThrow();
        DisplayContent content = board.content();
        if (content.isBlank() || content.suppressedIn(player.getWorld().getName())) {
            clear(player);
            return;
        }
        Session previous = sessions.get(uuid);
        if (foreignObjectives.containsKey(uuid)) {
            return;
        }
        ScoreboardFrame desired = frame(player, content, context, animations.tick());
        apply(player, previous, desired);
        sessions.put(uuid, new Session(desired, null, false));
        appliedBoard.put(uuid, board.name());
    }

    /** Clear only this renderer's client-side objective; a yielded foreign sidebar is never disturbed. */
    public void clear(Player player) {
        Objects.requireNonNull(player, "player");
        UUID uuid = player.getUniqueId();
        Session previous = sessions.remove(uuid);
        appliedBoard.remove(uuid);
        if (previous == null) {
            return;
        }
        List<Object> updates = new ArrayList<>();
        if (!foreignObjectives.containsKey(uuid)) {
            updates.add(packets.clearDisplay(ScoreboardDisplaySlot.SIDEBAR));
        }
        updates.add(packets.removeObjective(OBJECTIVE_NAME));
        packets.sendPackets(player, updates);
    }

    /** Forget disconnected client state without sending packets to its closed connection. */
    public void forget(Player player) {
        Objects.requireNonNull(player, "player");
        sessions.remove(player.getUniqueId());
        appliedBoard.remove(player.getUniqueId());
        foreignObjectives.remove(player.getUniqueId());
    }

    /**
     * Observe one ownership-relevant outbound event on the Netty thread. Returns true when a region-thread repaint
     * should be scheduled. This method mutates only concurrent immutable session state and never touches Bukkit.
     */
    public boolean observe(UUID viewer, ScoreboardPacketEvent event) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(event, "event");
        if (event instanceof ScoreboardPacketEvent.Display display && display.slot() == ScoreboardDisplaySlot.SIDEBAR) {
            Optional<String> objective = display.objectiveName();
            if (objective.filter(OBJECTIVE_NAME::equals).isPresent()) {
                return false;
            }
            if (objective.isPresent()) {
                foreignObjectives.put(viewer, objective.orElseThrow());
                appliedBoard.remove(viewer);
                sessions.computeIfPresent(viewer, (ignored, state) -> state.withForeign(objective.orElseThrow()));
                return false;
            }
            return resume(viewer, null);
        }
        if (event instanceof ScoreboardPacketEvent.Objective objective
                && objective.action() == com.uxplima.uxmlib.packet.scoreboard.ScoreboardObjectiveAction.REMOVE) {
            return resume(viewer, objective.objectiveName());
        }
        return false;
    }

    public boolean yielded(UUID viewer) {
        return foreignObjectives.containsKey(Objects.requireNonNull(viewer, "viewer"));
    }

    private boolean resume(UUID viewer, @Nullable String removedObjective) {
        @Nullable String foreign = foreignObjectives.get(viewer);
        if (foreign == null || (removedObjective != null && !removedObjective.equals(foreign))) {
            return false;
        }
        if (!foreignObjectives.remove(viewer, foreign)) {
            return false;
        }
        sessions.computeIfPresent(viewer, (ignored, state) -> {
            return state.resume();
        });
        return true;
    }

    private void apply(Player player, @Nullable Session previousSession, ScoreboardFrame desired) {
        @Nullable ScoreboardFrame previous = previousSession == null ? null : previousSession.frame();
        List<Object> updates = new ArrayList<>();
        if (previous == null) {
            updates.add(packets.createObjective(objective(desired.title())));
            addChangedRows(updates, Map.of(), desired);
            updates.add(packets.displayObjective(ScoreboardDisplaySlot.SIDEBAR, OBJECTIVE_NAME));
        } else {
            if (!previous.title().equals(desired.title())) {
                updates.add(packets.updateObjective(objective(desired.title())));
            }
            Map<String, ScoreboardFrame.Line> desiredById = byId(desired.lines());
            for (ScoreboardFrame.Line oldLine : previous.lines()) {
                if (!desiredById.containsKey(oldLine.id())) {
                    updates.add(packets.removeScore(OBJECTIVE_NAME, oldLine.holder()));
                }
            }
            addChangedRows(updates, indexed(previous), desired);
            if (previousSession != null && previousSession.redisplay()) {
                updates.add(packets.displayObjective(ScoreboardDisplaySlot.SIDEBAR, OBJECTIVE_NAME));
            }
        }
        packets.sendPackets(player, updates);
    }

    private void addChangedRows(List<Object> updates, Map<String, PositionedLine> previous, ScoreboardFrame desired) {
        int lineCount = desired.lines().size();
        for (int index = 0; index < lineCount; index++) {
            ScoreboardFrame.Line line = desired.lines().get(index);
            int score = lineCount - index;
            PositionedLine old = previous.get(line.id());
            if (old == null || old.score() != score || !old.line().equals(line)) {
                updates.add(packets.setScore(
                        new ScoreboardScore(OBJECTIVE_NAME, line.holder(), score, line.text(), line.numberFormat())));
            }
        }
    }

    private ScoreboardFrame frame(Player player, DisplayContent content, ConditionContext context, long tick) {
        Component title = content.title()
                .map(source -> component(expand(player, source, tick)))
                .orElse(Component.empty());
        List<ScoreboardFrame.Line> lines = new ArrayList<>();
        for (SidebarLine candidate : content.lineDefinitions()) {
            if (!candidate.condition().matches(context)) {
                continue;
            }
            String expanded = expand(player, candidate.text(), tick);
            if (candidate.hideWhenEmpty() && expanded.isBlank()) {
                continue;
            }
            lines.add(new ScoreboardFrame.Line(
                    candidate.id(), component(expanded), numberFormat(player, candidate.numberFormat(), tick)));
            if (lines.size() == MAX_VISIBLE_LINES) {
                break;
            }
        }
        return new ScoreboardFrame(title, lines);
    }

    private String expand(Player player, String source, long tick) {
        String animated = animations.resolve(source, tick);
        String builtins = BuiltinTokens.apply(player, animated);
        return PlaceholderApiSupport.messageBridge(player.getUniqueId()).apply(builtins);
    }

    private static Component component(String expanded) {
        return HudText.parse(expanded);
    }

    private ScoreboardNumberFormat numberFormat(Player player, SidebarNumberFormat format, long tick) {
        return switch (format) {
            case SidebarNumberFormat.Default ignored -> ScoreboardNumberFormat.defaultFormat();
            case SidebarNumberFormat.Blank ignored -> ScoreboardNumberFormat.blank();
            case SidebarNumberFormat.Fixed fixed ->
                ScoreboardNumberFormat.fixed(component(expand(player, fixed.source(), tick)));
        };
    }

    private static ScoreboardObjective objective(Component title) {
        return new ScoreboardObjective(OBJECTIVE_NAME, title, ScoreboardNumberFormat.defaultFormat());
    }

    private static Map<String, ScoreboardFrame.Line> byId(List<ScoreboardFrame.Line> lines) {
        Map<String, ScoreboardFrame.Line> result = new LinkedHashMap<>();
        for (ScoreboardFrame.Line line : lines) {
            result.put(line.id(), line);
        }
        return result;
    }

    private static Map<String, PositionedLine> indexed(ScoreboardFrame frame) {
        Map<String, PositionedLine> result = new LinkedHashMap<>();
        int lineCount = frame.lines().size();
        for (int index = 0; index < lineCount; index++) {
            ScoreboardFrame.Line line = frame.lines().get(index);
            result.put(line.id(), new PositionedLine(line, lineCount - index));
        }
        return result;
    }

    private static ConditionContext conditionContext(Player player) {
        return new ConditionContext(
                player::hasPermission,
                player.getWorld().getName(),
                player.getGameMode().name(),
                PlaceholderApiSupport.messageBridge(player.getUniqueId()));
    }

    static List<String> visibleLines(List<String> sources, ConditionContext context) {
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(context, "context");
        List<String> visible = new ArrayList<>(sources.size());
        for (String source : sources) {
            int separator = source.indexOf(CONDITION_SEPARATOR);
            if (separator < 0) {
                visible.add(source);
                continue;
            }
            String conditionPart = source.substring(0, separator);
            String text = source.substring(separator + CONDITION_SEPARATOR.length());
            if (ConditionParser.parse(conditionPart).matches(context)) {
                visible.add(text);
            }
        }
        return visible;
    }

    private record PositionedLine(ScoreboardFrame.Line line, int score) {}

    private record Session(ScoreboardFrame frame, @Nullable String foreignObjective, boolean redisplay) {
        private Session withForeign(@Nullable String value) {
            return new Session(frame, value, false);
        }

        private Session resume() {
            return new Session(frame, null, true);
        }
    }
}
