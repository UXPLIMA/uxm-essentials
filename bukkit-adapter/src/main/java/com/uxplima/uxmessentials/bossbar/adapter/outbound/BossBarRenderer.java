package com.uxplima.uxmessentials.bossbar.adapter.outbound;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.bossbar.application.port.BossBarVisibilityStore;
import com.uxplima.uxmessentials.bossbar.domain.BossBarConfig;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.BuiltinTokens;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.HudText;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import com.uxplima.uxmessentials.shared.display.ConditionContext;
import org.jspecify.annotations.NullMarked;

/**
 * Keeps each player's boss bars in step with the written ones. Every refresh works out the bars a player sees, then
 * changes the ones already on their screen in place and adds or takes away the rest. The client stacks bars in the
 * order they were shown, so when the list itself changes, the bars are taken down and shown again top first.
 *
 * <p>Each source is read for its reader: a catalogue line in their language, animations (whose frame may be a
 * catalogue line too), the built-in tokens, {@code {remaining}} for a timed or announced bar, then PlaceholderAPI.
 * Must run on the player's own thread.
 */
@NullMarked
public final class BossBarRenderer {

    private static final String REMAINING = "{remaining}";
    private static final String TIME_PROGRESS = "time";

    private final Supplier<BossBarConfig> bars;
    private final BossBarVisibilityStore visibility;
    private final AnimationRegistry animations;
    private final BossBarAnnouncements announcements;
    private final BiFunction<UUID, Duration, String> remaining;
    private final Supplier<Instant> clock;
    private final Map<UUID, List<Drawn>> drawn = new ConcurrentHashMap<>();

    /**
     * @param remaining a time left in one reader's words
     * @param clock the time now
     */
    public BossBarRenderer(
            Supplier<BossBarConfig> bars,
            BossBarVisibilityStore visibility,
            AnimationRegistry animations,
            BossBarAnnouncements announcements,
            BiFunction<UUID, Duration, String> remaining,
            Supplier<Instant> clock) {
        this.bars = Objects.requireNonNull(bars, "bars");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.animations = Objects.requireNonNull(animations, "animations");
        this.announcements = Objects.requireNonNull(announcements, "announcements");
        this.remaining = Objects.requireNonNull(remaining, "remaining");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Draw or bring up to date one player's bars. */
    public void renderFor(Player player) {
        Objects.requireNonNull(player, "player");
        UUID viewer = player.getUniqueId();
        if (visibility.hidden(BukkitRefs.toRef(player))) {
            clear(player);
            return;
        }
        Instant now = clock.get();
        long tick = animations.tick();
        List<BossBarConfig.Shown> shown =
                bars.get().shownTo(conditionContext(player), now, id -> announcements.windowFor(viewer, id));
        List<Drawn> before = drawn.getOrDefault(viewer, List.of());
        List<Drawn> after = new ArrayList<>(shown.size());
        for (BossBarConfig.Shown one : shown) {
            BossBar bar = before.stream()
                    .filter(old -> old.id().equals(one.bar().id()))
                    .map(Drawn::bar)
                    .findFirst()
                    .orElseGet(() ->
                            BossBar.bossBar(Component.empty(), 1.0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS));
            paint(player, bar, one, now, tick);
            after.add(new Drawn(one.bar().id(), bar));
        }
        if (!ids(before).equals(ids(after))) {
            before.forEach(old -> player.hideBossBar(old.bar()));
            after.forEach(next -> player.showBossBar(next.bar()));
        }
        if (after.isEmpty()) {
            drawn.remove(viewer);
        } else {
            drawn.put(viewer, List.copyOf(after));
        }
    }

    /** Take every bar off a player's screen. */
    public void clear(Player player) {
        Objects.requireNonNull(player, "player");
        List<Drawn> before = drawn.remove(player.getUniqueId());
        if (before != null) {
            before.forEach(old -> player.hideBossBar(old.bar()));
        }
    }

    /** Forget a player who left; their connection is gone, so nothing is sent. */
    public void forget(UUID player) {
        drawn.remove(Objects.requireNonNull(player, "player"));
    }

    /**
     * The time left on bar {@code id} for {@code player}: an announcement made to them or to everybody, else the bar's
     * own window, while either runs. Empty for a bar with no clock running, or one the file does not write.
     */
    public Optional<Duration> remaining(UUID player, String id) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(id, "id");
        Instant now = clock.get();
        return bars.get()
                .byId(id)
                .flatMap(bar -> announcements
                        .windowFor(player, bar.id())
                        .filter(window -> window.activeAt(now))
                        .or(() -> bar.window().filter(window -> window.activeAt(now))))
                .map(window -> window.remaining(now));
    }

    /** {@link #remaining} in the player's own words, for a placeholder. */
    public Optional<String> remainingWords(UUID player, String id) {
        return remaining(player, id).map(left -> remaining.apply(player, left));
    }

    /** The ids of the bars on a player's screen, top first. */
    public List<String> shownIds(UUID player) {
        return ids(drawn.getOrDefault(Objects.requireNonNull(player, "player"), List.of()));
    }

    private void paint(Player player, BossBar bar, BossBarConfig.Shown shown, Instant now, long tick) {
        String left = shown.clock()
                .map(window -> remaining.apply(player.getUniqueId(), window.remaining(now)))
                .orElse("");
        bar.name(HudText.parse(expand(player, shown.bar().text(), tick, left)));
        bar.color(color(expand(player, shown.bar().color(), tick, left)));
        bar.overlay(overlay(expand(player, shown.bar().style(), tick, left)));
        String progress = shown.bar().progress().strip();
        bar.progress(
                TIME_PROGRESS.equalsIgnoreCase(progress)
                        ? (float) shown.clock()
                                .map(window -> window.fractionLeft(now))
                                .orElse(1.0)
                                .doubleValue()
                        : percent(expand(player, progress, tick, left)));
    }

    private String expand(Player player, String source, long tick, String left) {
        // A frame an animation picks may itself be a catalogue line, which is how one bar turns through texts in
        // each reader's language, so the line is read again once the animation has chosen.
        UUID reader = player.getUniqueId();
        String animated = HudText.lineFor(reader, animations.resolve(HudText.lineFor(reader, source), tick));
        String builtins = BuiltinTokens.apply(player, animated).replace(REMAINING, left);
        return PlaceholderApiSupport.messageBridge(reader).apply(builtins);
    }

    private static BossBar.Color color(String name) {
        try {
            return BossBar.Color.valueOf(name.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return BossBar.Color.WHITE;
        }
    }

    private static BossBar.Overlay overlay(String name) {
        try {
            return BossBar.Overlay.valueOf(name.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return BossBar.Overlay.PROGRESS;
        }
    }

    /** A 0 to 100 source as the 0 to 1 a bar holds; anything that is not a number fills the bar. */
    private static float percent(String value) {
        try {
            double read = Double.parseDouble(value.strip().replace(',', '.'));
            return (float) Math.max(0.0, Math.min(1.0, read / 100.0));
        } catch (NumberFormatException notANumber) {
            return 1.0f;
        }
    }

    private static List<String> ids(List<Drawn> list) {
        return list.stream().map(Drawn::id).toList();
    }

    private static ConditionContext conditionContext(Player player) {
        return new ConditionContext(
                player::hasPermission,
                player.getWorld().getName(),
                player.getGameMode().name(),
                PlaceholderApiSupport.messageBridge(player.getUniqueId()));
    }

    private record Drawn(String id, BossBar bar) {}
}
