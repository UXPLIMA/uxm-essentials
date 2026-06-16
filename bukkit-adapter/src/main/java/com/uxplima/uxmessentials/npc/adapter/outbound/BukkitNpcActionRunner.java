package com.uxplima.uxmessentials.npc.adapter.outbound;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import com.google.common.base.Splitter;
import com.uxplima.uxmessentials.npc.adapter.inbound.listener.NpcCommandRunner;
import com.uxplima.uxmessentials.npc.adapter.outbound.NpcActionGates.Verdict;
import com.uxplima.uxmessentials.npc.application.port.NpcEconomy;
import com.uxplima.uxmessentials.npc.domain.NpcAction;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRegistryKeys;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.BuiltinTokens;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.HudText;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The Bukkit/Adventure {@link NpcActionRunner}: it filters an NPC's action chain by click trigger and runs the
 * matching actions as an ordered <em>sequence</em>. An effect action (console/player command, message/action-bar/
 * title, sound, connect, give) performs its effect and the sequence moves on, fail-soft — one bad effect logs a
 * one-line warning and is skipped, the chain continues. A gate action (chance, permission, condition, cost)
 * decides whether the rest of the chain runs at all through {@link NpcActionGates}: a denied gate stops the
 * remaining actions, a malformed gate spec is skipped (never aborting). A {@code DELAY} parks the rest of the
 * chain: the tail is re-scheduled after the stated tick count through the {@link Scheduler} port (ticks converted
 * at the fixed 50&nbsp;ms cadence) and then hops back onto the viewer's entity region to resume — a tiny
 * in-adapter sequencer rather than a single straight loop. A viewer who logs off during the wait aborts the rest
 * silently (the entity hop no-ops on a despawned entity).
 *
 * <p>Command dispatch (console/player) reuses the same {@link NpcCommandRunner} and {@code [console]}/{@code
 * [player]}/{@code {player}} convention the single click command uses; message/action-bar/title text is resolved
 * through the shared built-in tokens then the PlaceholderAPI + MiniMessage transform, so an action value may
 * embed {@code {player}}, built-in {@code {token}}s, and {@code %papi%} placeholders; a sound value is {@code
 * KEY[:vol[:pitch]]}; a connect value is a target server name. The whole interaction is cooldown-gated at the
 * listener, so a delayed continuation never re-arms the cooldown.
 */
@NullMarked
public final class BukkitNpcActionRunner implements NpcActionRunner {

    private static final String CONSOLE_PREFIX = "[console]";
    private static final String PLAYER_PREFIX = "[player]";
    private static final String PLAYER_TOKEN = "{player}";
    private static final long MILLIS_PER_TICK = 50L;

    private final NpcCommandRunner commandRunner;
    private final NpcServerConnector connector;
    private final Scheduler scheduler;
    private final NpcActionGates gates;
    private final NpcGifts gifts;
    private final Logger log;

    public BukkitNpcActionRunner(
            NpcCommandRunner commandRunner,
            NpcServerConnector connector,
            Scheduler scheduler,
            Permissions permissions,
            Optional<NpcEconomy> economy,
            Messages messages,
            Logger log) {
        this.commandRunner = Objects.requireNonNull(commandRunner, "commandRunner");
        this.connector = Objects.requireNonNull(connector, "connector");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.gates = new NpcActionGates(
                Objects.requireNonNull(permissions, "permissions"),
                Objects.requireNonNull(economy, "economy"),
                new CommandFeedback(Objects.requireNonNull(messages, "messages")),
                log);
        this.gifts = new NpcGifts(log);
    }

    @Override
    public void run(Player viewer, List<NpcAction> actions, boolean attack) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(actions, "actions");
        List<NpcAction> matching = new ArrayList<>();
        for (NpcAction action : actions) {
            if (action.trigger().matches(attack)) {
                matching.add(action);
            }
        }
        runFrom(viewer, List.copyOf(matching), 0);
    }

    /** Run the already-trigger-filtered {@code actions} from {@code index} onward, honouring gates and delays. */
    private void runFrom(Player viewer, List<NpcAction> actions, int index) {
        for (int at = index; at < actions.size(); at++) {
            NpcAction action = actions.get(at);
            switch (action.type()) {
                case DELAY -> {
                    delayThenContinue(viewer, actions, at, action.value());
                    return; // the tail resumes from the scheduler; this invocation is done
                }
                case CHANCE, PERMISSION, CONDITION, COST -> {
                    if (gate(viewer, action) == Verdict.DENY) {
                        return; // a denied gate stops the rest of the chain
                    }
                }
                default -> effect(viewer, action);
            }
        }
    }

    private Verdict gate(Player viewer, NpcAction action) {
        return switch (action.type()) {
            case CHANCE -> gates.chance(action.value());
            case PERMISSION -> gates.permission(viewer, action.value());
            case CONDITION -> gates.condition(viewer, action.value());
            case COST -> gates.cost(viewer, action.value());
            default -> Verdict.PASS;
        };
    }

    /** Park the remaining actions for {@code ticks}, then resume on the viewer's entity region thread. */
    private void delayThenContinue(Player viewer, List<NpcAction> actions, int index, String raw) {
        long ticks = parseTicks(raw);
        if (ticks <= 0L) {
            runFrom(viewer, actions, index + 1);
            return;
        }
        var ref = BukkitRefs.toRef(viewer);
        scheduler.asyncAfter(
                Duration.ofMillis(ticks * MILLIS_PER_TICK),
                () -> scheduler.onEntity(ref, () -> resume(ref, actions, index + 1)));
    }

    /** Resume the parked chain for the still-online viewer, or abort silently when they have logged off. */
    private void resume(PlayerRef ref, List<NpcAction> actions, int index) {
        Player viewer = org.bukkit.Bukkit.getPlayer(ref.uuid());
        if (viewer == null || !viewer.isOnline()) {
            return; // logged off during the wait — abort the rest of the chain
        }
        runFrom(viewer, actions, index);
    }

    /** Perform one effect action; never throws, so a single bad effect skips rather than aborting the chain. */
    private void effect(Player viewer, NpcAction action) {
        try {
            dispatch(viewer, action);
        } catch (RuntimeException failure) {
            // Fail-soft: a single malformed effect must not abort the rest of the chain.
            log.warn("event=npc_action_failed type={} value={}", action.type().name(), action.value());
        }
    }

    private void dispatch(Player viewer, NpcAction action) {
        String value = action.value();
        switch (action.type()) {
            case RUN_CONSOLE -> runConsole(viewer, value);
            case RUN_PLAYER -> commandRunner.runAsPlayer(viewer, withPlayer(viewer, stripPrefix(value)));
            case MESSAGE -> viewer.sendMessage(component(viewer, value));
            case ACTIONBAR -> viewer.sendActionBar(component(viewer, value));
            case TITLE -> showTitle(viewer, value);
            case SOUND -> playSound(viewer, value);
            case CONNECT -> connector.connect(viewer, value.strip());
            case GIVE -> gifts.give(viewer, value);
            default -> {
                // Gate and delay types are handled by the sequencer before dispatch; nothing to do here.
            }
        }
    }

    private long parseTicks(String raw) {
        try {
            return Long.parseLong(raw.strip());
        } catch (NumberFormatException notANumber) {
            log.warn("event=npc_action_bad_delay value={}", raw);
            return 0L; // a bad delay is treated as no delay — the chain continues immediately
        }
    }

    private void runConsole(Player viewer, String value) {
        commandRunner.runAsConsole(withPlayer(viewer, stripPrefix(value)));
    }

    private void showTitle(Player viewer, String value) {
        int split = value.indexOf('|');
        String titleSource = split < 0 ? value : value.substring(0, split);
        String subtitleSource = split < 0 ? "" : value.substring(split + 1);
        Component title = component(viewer, titleSource);
        Component subtitle = subtitleSource.isEmpty() ? Component.empty() : component(viewer, subtitleSource);
        viewer.showTitle(Title.title(title, subtitle));
    }

    private void playSound(Player viewer, String value) {
        SoundSpec spec = SoundSpec.parse(value);
        Sound sound = BukkitRegistryKeys.resolveSound(spec.key());
        if (sound == null) {
            log.warn("event=npc_action_unknown_sound value={}", value);
            return;
        }
        viewer.playSound(
                Objects.requireNonNull(viewer.getLocation(), "viewer location"), sound, spec.volume(), spec.pitch());
    }

    /** Resolve a value to a component for the viewer: built-in tokens, then the PAPI + MiniMessage transform. */
    private static Component component(Player viewer, String source) {
        String withTokens = BuiltinTokens.apply(viewer, source);
        return HudText.render(viewer.getUniqueId(), withTokens);
    }

    private static String withPlayer(Player viewer, String command) {
        return command.replace(PLAYER_TOKEN, viewer.getName());
    }

    /** Drop a leading {@code [console]}/{@code [player]} routing prefix; the type already chose the dispatcher. */
    private static String stripPrefix(String value) {
        String stripped = value.strip();
        String lower = stripped.toLowerCase(Locale.ROOT);
        if (lower.startsWith(CONSOLE_PREFIX)) {
            return stripped.substring(CONSOLE_PREFIX.length()).strip();
        }
        if (lower.startsWith(PLAYER_PREFIX)) {
            return stripped.substring(PLAYER_PREFIX.length()).strip();
        }
        return stripped;
    }

    /**
     * A parsed {@code KEY[:volume[:pitch]]} sound value. The key may itself be namespaced ({@code
     * minecraft:entity.player.levelup}), so volume and pitch are read as the trailing one or two numeric
     * {@code :}-separated segments and everything before them is the key — splitting on the first colon would
     * eat a namespace. A missing volume/pitch defaults to {@code 1.0}.
     */
    record SoundSpec(String key, float volume, float pitch) {

        static SoundSpec parse(String value) {
            // The trailing numeric segments are, left to right, volume then pitch. Peel them off the right
            // (so a single trailing number is the volume), and whatever remains — rejoined on ':' — is the key,
            // so a namespaced key keeps its own colon instead of being eaten by a split-on-first-colon.
            List<String> parts = new ArrayList<>(Splitter.on(':').trimResults().splitToList(value));
            Float last = trailingFloat(parts);
            if (last == null) {
                return new SoundSpec(String.join(":", parts), 1.0f, 1.0f);
            }
            Float secondLast = trailingFloat(parts);
            if (secondLast == null) {
                // One trailing number: it is the volume; pitch defaults.
                return new SoundSpec(String.join(":", parts), last, 1.0f);
            }
            // Two trailing numbers: volume then pitch in left-to-right order.
            return new SoundSpec(String.join(":", parts), secondLast, last);
        }

        /**
         * If the last segment is a float and is not the only segment left (the key must survive), remove and
         * return it; otherwise leave the list untouched and return {@code null}.
         */
        private static @Nullable Float trailingFloat(List<String> parts) {
            if (parts.size() <= 1) {
                return null;
            }
            String last = parts.get(parts.size() - 1);
            try {
                float parsed = Float.parseFloat(last);
                parts.remove(parts.size() - 1);
                return parsed;
            } catch (NumberFormatException notANumber) {
                return null;
            }
        }
    }
}
