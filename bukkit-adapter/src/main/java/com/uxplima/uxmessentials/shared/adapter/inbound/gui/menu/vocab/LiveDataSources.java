package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;

/**
 * Two ready-made, read-only roster sources every custom menu can page without a line of code: {@code online-players}
 * (each online player, auto-skinned when the template uses {@code %online_player_skull%}) and {@code worlds} (each
 * loaded world, with {@code %worlds_icon%} auto-picking an environment block). Each source is a list handler plus the
 * per-entry {@code %token%} placeholders a template reads to draw one row, all registered once at startup into the
 * shared {@link MenuBindings} — a spec's {@code list { source = online-players, template { … } }} then resolves the
 * same way a code-registered feature source does.
 *
 * <p>The Folia constraint shapes the whole design. A list source runs on an async thread — {@code Menus} resolves a
 * spec's lists off the tick thread — and there the entity/world API is off-limits: {@code player.getWorld()},
 * {@code getPing()}, {@code getGameMode()}, {@code world.getPlayers()} all touch region state that is unsafe off the
 * owning region thread. So each source snapshots live server state on the global region thread into immutable value
 * records and hands those records back; nothing Bukkit-live crosses to the async thread, and the per-entry
 * placeholders read only the captured record.
 */
public final class LiveDataSources {

    /** How long the async list-resolver waits for the global-thread snapshot before serving an empty roster. */
    private static final long SNAPSHOT_TIMEOUT_SECONDS = 2;

    private LiveDataSources() {}

    /**
     * Register the {@code online-players} and {@code worlds} sources and their per-entry placeholders into
     * {@code bindings}. Each source snapshots on the global region thread through {@code scheduler}, so the same
     * {@link Scheduler} the engine already opens menus on is passed here (a duplicate source/placeholder id throws
     * loudly, which is why these ids are registered exactly once).
     */
    public static void register(MenuBindings bindings, Scheduler scheduler) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(scheduler, "scheduler");
        bindings.list("online-players", ctx -> snapshot(scheduler, LiveDataSources::onlinePlayers));
        bindings.list("worlds", ctx -> snapshot(scheduler, LiveDataSources::loadedWorlds));
        registerOnlinePlayerPlaceholders(bindings);
        registerWorldPlaceholders(bindings);
    }

    /** The {@code %online_player_*%} placeholders, each reading one field off the bound player entry. */
    private static void registerOnlinePlayerPlaceholders(MenuBindings bindings) {
        bindings.placeholder("online_player_name", ctx -> online(ctx, OnlinePlayerEntry::name));
        bindings.placeholder(
                "online_player_uuid", ctx -> online(ctx, entry -> entry.uuid().toString()));
        bindings.placeholder("online_player_world", ctx -> online(ctx, OnlinePlayerEntry::world));
        bindings.placeholder("online_player_ping", ctx -> online(ctx, entry -> String.valueOf(entry.ping())));
        bindings.placeholder("online_player_gamemode", ctx -> online(ctx, OnlinePlayerEntry::gameMode));
        // A convenience so a template can write material = "%online_player_skull%" and get a UUID-skinned head with no
        // name lookup: it yields the skull:<uuid> form the skull icon provider turns into a PLAYER_HEAD.
        bindings.placeholder("online_player_skull", ctx -> online(ctx, entry -> "skull:" + entry.uuid()));
    }

    /** The {@code %worlds_*%} placeholders, each reading one field off the bound world entry. */
    private static void registerWorldPlaceholders(MenuBindings bindings) {
        bindings.placeholder("worlds_name", ctx -> world(ctx, WorldEntry::name));
        bindings.placeholder("worlds_environment", ctx -> world(ctx, WorldEntry::environment));
        bindings.placeholder("worlds_players", ctx -> world(ctx, entry -> String.valueOf(entry.players())));
        // A convenience so a template can write material = "%worlds_icon%" and auto-pick the environment's block.
        bindings.placeholder("worlds_icon", ctx -> world(ctx, entry -> environmentIcon(entry.environment())));
    }

    /**
     * Take a snapshot of live server state on the global region thread and return it to the async caller. When the
     * caller already owns the global thread (the deadlock guard — a global-thread invocation, or a test scheduler
     * that reports itself global), the supplier runs inline, because scheduling then blocking on the same thread
     * would deadlock. Otherwise the supplier runs on the global thread and this thread waits on a latch for it.
     *
     * <p>That wait is on the async list-resolution thread, never the main thread, so it is legal: the forbidden rule
     * is blocking the <em>main</em> thread, and this bounded off-tick wait keeps a hung global thread from stalling a
     * menu open forever — an empty roster is served on timeout or interruption instead. No {@code CompletableFuture}
     * is used; a bare {@link CountDownLatch} plus {@link AtomicReference} makes the hand-off unambiguous.
     */
    private static <T> List<T> snapshot(Scheduler scheduler, Supplier<List<T>> supplier) {
        if (scheduler.onGlobalThread()) {
            return supplier.get();
        }
        AtomicReference<List<T>> holder = new AtomicReference<>(List.of());
        CountDownLatch done = new CountDownLatch(1);
        scheduler.onGlobal(() -> {
            holder.set(supplier.get());
            done.countDown();
        });
        try {
            if (!done.await(SNAPSHOT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                return List.of();
            }
            return Objects.requireNonNullElse(holder.get(), List.of());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    /** One entry per online player, captured on the global thread where the entity API is safe to touch. */
    private static List<OnlinePlayerEntry> onlinePlayers() {
        List<OnlinePlayerEntry> entries = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            entries.add(new OnlinePlayerEntry(
                    player.getName(),
                    player.getUniqueId(),
                    player.getWorld().getName(),
                    player.getPing(),
                    player.getGameMode().name()));
        }
        return entries;
    }

    /** One entry per loaded world, captured on the global thread where the world API is safe to touch. */
    private static List<WorldEntry> loadedWorlds() {
        List<WorldEntry> entries = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            entries.add(new WorldEntry(
                    world.getName(),
                    world.getEnvironment().name(),
                    world.getPlayers().size(),
                    true));
        }
        return entries;
    }

    /** Read one field off the bound online-player entry, or empty when the placeholder is used off such a list. */
    private static String online(MenuContext ctx, Function<OnlinePlayerEntry, String> field) {
        return ctx.entry()
                .filter(OnlinePlayerEntry.class::isInstance)
                .map(OnlinePlayerEntry.class::cast)
                .map(field)
                .orElse("");
    }

    /** Read one field off the bound world entry, or empty when the placeholder is used off such a list. */
    private static String world(MenuContext ctx, Function<WorldEntry, String> field) {
        return ctx.entry()
                .filter(WorldEntry.class::isInstance)
                .map(WorldEntry.class::cast)
                .map(field)
                .orElse("");
    }

    /** The block material name standing in for a world's environment, defaulting to the overworld's grass. */
    static String environmentIcon(String environment) {
        return switch (environment) {
            case "NETHER" -> "NETHERRACK";
            case "THE_END" -> "END_STONE";
            default -> "GRASS_BLOCK";
        };
    }

    /** An immutable snapshot of one online player, safe to read off any thread once captured. */
    public record OnlinePlayerEntry(String name, UUID uuid, String world, int ping, String gameMode) {

        public OnlinePlayerEntry {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(uuid, "uuid");
            Objects.requireNonNull(world, "world");
            Objects.requireNonNull(gameMode, "gameMode");
        }
    }

    /** An immutable snapshot of one loaded world, safe to read off any thread once captured. */
    public record WorldEntry(String name, String environment, int players, boolean loaded) {

        public WorldEntry {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(environment, "environment");
        }
    }
}
