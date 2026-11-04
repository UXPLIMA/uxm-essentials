package com.uxplima.uxmessentials.guard.adapter;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.guard.application.GuardConfig;
import com.uxplima.uxmessentials.guard.application.GuardMessageKey;
import com.uxplima.uxmessentials.guard.application.GuardService;
import com.uxplima.uxmessentials.guard.application.GuardStats;
import com.uxplima.uxmessentials.guard.application.port.GuardStore;
import com.uxplima.uxmessentials.guard.domain.ActionId;
import com.uxplima.uxmessentials.guard.domain.BehaviourKey;
import com.uxplima.uxmessentials.guard.domain.Explanation;
import com.uxplima.uxmessentials.guard.domain.GuardSubject;
import com.uxplima.uxmessentials.guard.domain.Region;
import com.uxplima.uxmessentials.guard.domain.Verdict;
import com.uxplima.uxmessentials.guard.domain.engine.GuardSnapshot;
import com.uxplima.uxmessentials.guard.domain.engine.RegionSet;
import com.uxplima.uxmessentials.guard.domain.engine.WorldIndex;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.Nullable;

/**
 * What every guard listener asks: may this player do this here, may nature do this here, may this effect cross
 * from there to here. It also owns the feedback around a denial: the message with its cooldown, the bypass toggle,
 * the inspect mode, staff alerts and the denial log buffer.
 *
 * <p>The fast path reads the current snapshot, returns at once when the world has no rule for the action, and
 * allocates nothing for an allowed check.
 */
public final class GuardRuntime {

    /** Lets staff toggle bypass. */
    public static final String BYPASS_PERMISSION = "uxmessentials.guard.bypass";
    /** Lets staff change locked regions and build in them. */
    public static final String LOCK_PERMISSION = "uxmessentials.guard.lock";
    /** Gets alerts and locked world notices. */
    public static final String NOTIFY_PERMISSION = "uxmessentials.guard.notify";

    private final GuardService service;
    private final Messages messages;
    private final MessageSink sink;
    private final Clock clock;
    private final Consumer<String> audit;
    private final GuardStore store;
    private final org.bukkit.plugin.Plugin plugin;
    private final com.uxplima.uxmessentials.shared.application.port.Scheduler scheduler;
    private volatile GuardStats stats;
    private final Set<UUID> bypassing = ConcurrentHashMap.newKeySet();
    private final Set<UUID> inspecting = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastDenyMessage = new ConcurrentHashMap<>();
    private final Map<UUID, ArrayDeque<Long>> recentDenials = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastAlert = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<GuardStore.Denial> pendingLog = new ConcurrentLinkedQueue<>();
    private final Map<String, Set<String>> parsedLists = new ConcurrentHashMap<>();

    public GuardRuntime(
            org.bukkit.plugin.Plugin plugin,
            com.uxplima.uxmessentials.shared.application.port.Scheduler scheduler,
            GuardService service,
            GuardStore store,
            Messages messages,
            MessageSink sink,
            Clock clock,
            Consumer<String> audit) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.service = Objects.requireNonNull(service, "service");
        this.store = Objects.requireNonNull(store, "store");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.stats = new GuardStats(service.tree());
    }

    public org.bukkit.plugin.Plugin plugin() {
        return plugin;
    }

    /** Logged denials, newest first, after writing what is still buffered; read on the async lane. */
    public java.util.concurrent.CompletableFuture<List<GuardStore.Denial>> denials(
            @Nullable String player, @Nullable String region, int offset, int limit) {
        java.util.concurrent.CompletableFuture<List<GuardStore.Denial>> future =
                new java.util.concurrent.CompletableFuture<>();
        scheduler.async(() -> {
            try {
                flushLog();
                future.complete(store.denials(player, region, offset, limit));
            } catch (RuntimeException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public GuardService service() {
        return service;
    }

    public GuardSnapshot snapshot() {
        return service.snapshot();
    }

    public GuardStats stats() {
        GuardStats current = stats;
        if (current.tree() != service.tree()) {
            current = new GuardStats(service.tree());
            stats = current;
        }
        return current;
    }

    public GuardConfig config() {
        return service.config();
    }

    // ---- checks -------------------------------------------------------------------------------------------------

    /** May {@code player} do {@code action} at a block; tells them when not. */
    public boolean allow(Player player, ActionId action, Block block) {
        return allow(player, action, block.getWorld(), block.getX(), block.getY(), block.getZ(), true);
    }

    /** May {@code player} do {@code action} at a location; tells them when not. */
    public boolean allow(Player player, ActionId action, @Nullable Location at) {
        if (at == null) {
            return true;
        }
        return allow(player, action, at.getWorld(), at.getBlockX(), at.getBlockY(), at.getBlockZ(), true);
    }

    /**
     * May {@code player} do {@code action} at a block position. With {@code tell} false the refusal is quiet: for
     * what happens to a player rather than what they try (picking up an item they walk over), so it sends nothing,
     * raises no alert and writes no log row.
     */
    public boolean allow(Player player, ActionId action, @Nullable World world, int x, int y, int z, boolean tell) {
        if (world == null) {
            return true;
        }
        GuardSnapshot snap = service.snapshot();
        WorldIndex index = snap.worlds().get(world.getName());
        if (index == null) {
            return true;
        }
        int node = snap.tree().indexOf(action);
        if (!index.locked() && (node < 0 || !index.sets(node))) {
            return true;
        }
        long started = System.nanoTime();
        Verdict verdict = index.check(x, y, z, node, new BukkitSubject(player));
        long took = System.nanoTime() - started;
        boolean denied = verdict == Verdict.DENY && !bypasses(player, index, x, y, z);
        stats().record(node, took, denied);
        if (denied) {
            denied(player, action, index, x, y, z, tell);
        }
        return !denied;
    }

    /**
     * May {@code player} break or place {@code material} at a block: the rule decision, then the region's block
     * lists ({@code blocks.break.deny} wins, then {@code blocks.break.allow}).
     */
    public boolean allowBlock(Player player, ActionId action, Block block, String material, boolean breaking) {
        GuardSnapshot snap = service.snapshot();
        WorldIndex index = snap.worlds().get(block.getWorld().getName());
        if (index == null) {
            return true;
        }
        RegionSet set = index.locked() ? null : index.at(block.getX(), block.getY(), block.getZ());
        if (set != null && !set.wilderness()) {
            BehaviourKey deny = breaking ? BehaviourKey.BLOCKS_BREAK_DENY : BehaviourKey.BLOCKS_PLACE_DENY;
            BehaviourKey allow = breaking ? BehaviourKey.BLOCKS_BREAK_ALLOW : BehaviourKey.BLOCKS_PLACE_ALLOW;
            String name = material.toLowerCase(Locale.ROOT);
            if (listed(set, deny, name)) {
                if (bypasses(player, index, block.getX(), block.getY(), block.getZ())) {
                    return true;
                }
                denied(player, action, index, block.getX(), block.getY(), block.getZ(), true);
                return false;
            }
            if (listed(set, allow, name)) {
                return true;
            }
        }
        return allow(player, action, block);
    }

    /** May {@code player} use an item of {@code material} here: {@code items.use.deny}, then the rule. */
    public boolean allowItem(Player player, ActionId action, @Nullable Location at, String material) {
        if (at == null) {
            return true;
        }
        GuardSnapshot snap = service.snapshot();
        WorldIndex index =
                at.getWorld() == null ? null : snap.worlds().get(at.getWorld().getName());
        if (index != null && !index.locked()) {
            RegionSet set = index.at(at.getBlockX(), at.getBlockY(), at.getBlockZ());
            if (!set.wilderness()
                    && listed(set, BehaviourKey.ITEMS_USE_DENY, material.toLowerCase(Locale.ROOT))
                    && !bypasses(player, index, at.getBlockX(), at.getBlockY(), at.getBlockZ())) {
                denied(player, action, index, at.getBlockX(), at.getBlockY(), at.getBlockZ(), true);
                return false;
            }
        }
        return allow(player, action, at);
    }

    /** May nature do {@code action} at a block. */
    public boolean allowWorld(ActionId action, World world, int x, int y, int z) {
        GuardSnapshot snap = service.snapshot();
        WorldIndex index = snap.worlds().get(world.getName());
        if (index == null) {
            return true;
        }
        int node = snap.tree().indexOf(action);
        if (!index.locked() && (node < 0 || !index.sets(node))) {
            return true;
        }
        if (!index.locked() && !config().world(world.getName()).highFrequency() && highFrequency(action)) {
            return true;
        }
        long started = System.nanoTime();
        Verdict verdict = index.check(x, y, z, node, GuardSubject.WORLD);
        stats().record(node, System.nanoTime() - started, verdict == Verdict.DENY);
        return verdict != Verdict.DENY;
    }

    /** May nature do {@code action} at a block, and may the effect cross from the source block to it. */
    public boolean allowWorld(ActionId action, Block source, Block target) {
        if (!allowWorld(action, target.getWorld(), target.getX(), target.getY(), target.getZ())) {
            return false;
        }
        return reachable(source, target);
    }

    /** The border test alone. */
    public boolean reachable(Block source, Block target) {
        if (!source.getWorld().equals(target.getWorld())) {
            return true;
        }
        WorldIndex index = service.snapshot().worlds().get(target.getWorld().getName());
        if (index == null || index.size() == 0) {
            return index == null || !index.locked();
        }
        return index.reachable(
                source.getX(), source.getY(), source.getZ(), target.getX(), target.getY(), target.getZ());
    }

    /** True when some region exists in the world; listeners skip whole events otherwise. */
    public boolean active(World world) {
        WorldIndex index = service.snapshot().worlds().get(world.getName());
        return index != null && (index.locked() || index.size() > 0 || index.hasDefaults());
    }

    /** The regions at a location. */
    public @Nullable RegionSet at(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        WorldIndex index = service.snapshot().worlds().get(world.getName());
        return index == null || index.locked()
                ? null
                : index.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    // ---- bypass and inspect -------------------------------------------------------------------------------------

    /** Toggle bypass; answers the new state, or empty when the player lacks the permission. */
    public java.util.Optional<Boolean> toggleBypass(Player player) {
        if (!player.hasPermission(BYPASS_PERMISSION)) {
            return java.util.Optional.empty();
        }
        boolean on = !bypassing.remove(player.getUniqueId());
        if (on) {
            bypassing.add(player.getUniqueId());
        }
        return java.util.Optional.of(on);
    }

    public boolean bypassOn(Player player) {
        return bypassing.contains(player.getUniqueId());
    }

    /** Players with bypass on, for the reminder task. */
    public Set<UUID> bypassing() {
        return Set.copyOf(bypassing);
    }

    /** Toggle inspect mode; answers the new state. */
    public boolean toggleInspect(Player player) {
        boolean on = !inspecting.remove(player.getUniqueId());
        if (on) {
            inspecting.add(player.getUniqueId());
        }
        return on;
    }

    /** Forget a player who left. */
    public void forget(UUID player) {
        bypassing.remove(player);
        inspecting.remove(player);
        lastDenyMessage.remove(player);
        recentDenials.remove(player);
        lastAlert.remove(player);
    }

    private boolean bypasses(Player player, WorldIndex index, int x, int y, int z) {
        if (!bypassing.contains(player.getUniqueId()) || !player.hasPermission(BYPASS_PERMISSION)) {
            return false;
        }
        if (index.locked()) {
            return true;
        }
        for (Region region : index.at(x, y, z).stored()) {
            if (region.locked() && !player.hasPermission(LOCK_PERMISSION)) {
                return false;
            }
        }
        return true;
    }

    // ---- feedback -----------------------------------------------------------------------------------------------

    private void denied(Player player, ActionId action, WorldIndex index, int x, int y, int z, boolean tell) {
        // A quiet refusal (an item walked over, a crop trampled) is not something the player chose to try: it is
        // not told, not shown to an inspector on every tick, not counted towards a staff alert and kept out of the
        // log. /guard why explains one on demand.
        if (!tell) {
            return;
        }
        long now = clock.millis();
        Long last = lastDenyMessage.get(player.getUniqueId());
        if (last == null || now - last >= config().denyCooldown().toMillis()) {
            lastDenyMessage.put(player.getUniqueId(), now);
            if (index.locked()) {
                send(player, GuardMessageKey.DENY_LOCKED_WORLD, Map.of(), config().denyChannel());
            } else {
                send(player, GuardMessageKey.DENY, Map.of("action", action.value()), config().denyChannel());
            }
        }
        String region = index.locked()
                ? "-"
                : index.at(x, y, z).top().map(r -> r.region().id().value()).orElse("-");
        if (inspecting.contains(player.getUniqueId())) {
            inspect(player, player, action, index.world(), x, y, z);
        }
        alertIfRepeated(player, region, now);
        if (config().denialLog().enabled()) {
            pendingLog.add(new GuardStore.Denial(
                    clock.instant(), player.getName(), index.world(), x, y, z, region, action.value()));
        }
    }

    /** Send {@code viewer} the explanation of {@code subject} doing {@code action} at a block. */
    public void inspect(Player viewer, Player subject, ActionId action, String world, int x, int y, int z) {
        Explanation explanation = service.snapshot().explain(world, x, y, z, action, new BukkitSubject(subject));
        for (Explanation.Step step : explanation.steps()) {
            if (step.decisive()) {
                send(
                        viewer,
                        GuardMessageKey.INSPECT_LINE,
                        Map.of(
                                "player", subject.getName(),
                                "action", action.value(),
                                "region", step.region().id().value(),
                                "node", step.node() == null ? "-" : step.node().value(),
                                "rule",
                                        step.rule() == null
                                                ? "-"
                                                : StyledText.escape(step.rule().toString()),
                                "result", step.result().name().toLowerCase(Locale.ROOT)),
                        "chat");
            }
        }
    }

    private void alertIfRepeated(Player player, String region, long now) {
        GuardConfig.Alerts alerts = config().alerts();
        if (!alerts.enabled() || player.hasPermission(NOTIFY_PERMISSION)) {
            return;
        }
        ArrayDeque<Long> times = recentDenials.computeIfAbsent(player.getUniqueId(), id -> new ArrayDeque<>());
        int count;
        synchronized (times) {
            times.addLast(now);
            while (!times.isEmpty() && now - times.peekFirst() > alerts.window().toMillis()) {
                times.removeFirst();
            }
            count = times.size();
        }
        if (count < alerts.threshold()) {
            return;
        }
        Long last = lastAlert.get(player.getUniqueId());
        if (last != null && now - last < alerts.cooldown().toMillis()) {
            return;
        }
        lastAlert.put(player.getUniqueId(), now);
        Map<String, String> values = Map.of(
                "player",
                player.getName(),
                "region",
                region,
                "count",
                String.valueOf(count),
                "seconds",
                String.valueOf(alerts.window().toSeconds()));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(NOTIFY_PERMISSION)) {
                send(staff, GuardMessageKey.ALERT, values, "chat");
            }
        }
        if (alerts.discord()) {
            audit.accept("event=guard_alert player=" + player.getName() + " region=" + region + " count=" + count
                    + " seconds=" + alerts.window().toSeconds());
        }
    }

    /** Write the buffered denials; called from the async lane every few seconds. */
    public void flushLog() {
        if (pendingLog.isEmpty()) {
            return;
        }
        List<GuardStore.Denial> batch = new ArrayList<>();
        for (GuardStore.Denial denial; batch.size() < 500 && (denial = pendingLog.poll()) != null; ) {
            batch.add(denial);
        }
        store.logDenials(batch);
    }

    /** Send a message on a channel: {@code chat} or {@code actionbar}. */
    public void send(Player player, MessageKey key, Map<String, String> values, String channel) {
        String text = messages.resolve(BukkitRefs.toRef(player), key, values);
        if (text.isBlank()) {
            return;
        }
        if (channel.equals("actionbar")) {
            player.sendActionBar(StyledText.render(text));
        } else {
            sink.deliver(BukkitRefs.toRef(player), text);
        }
    }

    /** Send a chat message. */
    public void tell(Player player, MessageKey key, Map<String, String> values) {
        send(player, key, values, "chat");
    }

    private boolean listed(RegionSet set, BehaviourKey key, String material) {
        return set.behaviour(key)
                .map(raw -> parsedLists.computeIfAbsent(raw, r -> Set.copyOf(Arrays.asList(r.split(",", -1)))))
                .map(names -> names.contains(material))
                .orElse(false);
    }

    private static boolean highFrequency(ActionId action) {
        return action.value().startsWith("world.liquid")
                || action.value().startsWith("world.fire.spread")
                || action.value().startsWith("world.grow")
                || action.value().startsWith("world.spread")
                || action.value().startsWith("world.decay")
                || action.value().startsWith("world.form")
                || action.value().startsWith("world.melt")
                || action.value().startsWith("world.fade");
    }
}
