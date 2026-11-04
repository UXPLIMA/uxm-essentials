package com.uxplima.uxmessentials.guard.adapter.inbound.listener;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.guard.adapter.GuardRuntime;
import com.uxplima.uxmessentials.guard.domain.ActionId;
import com.uxplima.uxmessentials.guard.domain.ActionTree;
import com.uxplima.uxmessentials.guard.domain.engine.GuardSnapshot;
import com.uxplima.uxmessentials.guard.domain.engine.WorldIndex;

/**
 * Registers a listener family only while some rule needs it, and takes it away when none does (B02): a server
 * whose regions only deny pvp never hears a hopper event from the guard.
 *
 * <p>The families that guard borders (nature, explosions, hoppers) are on whenever any world has a region with a
 * rule or is locked, because the border test protects every region with rules, not only those naming
 * {@code world.*}.
 */
public final class ListenerRegistry {

    /**
     * One listener family and the action roots that need it.
     *
     * @param name the family name, for status
     * @param roots the action subtrees whose rules need the family
     * @param guardsBorders true when the family carries the border test and so runs whenever any rule exists
     * @param factory builds the listener
     */
    public record Family(
            String name, List<String> roots, boolean guardsBorders, Function<GuardRuntime, Listener> factory) {

        public Family {
            roots = List.copyOf(roots);
        }

        boolean needed(GuardSnapshot snapshot) {
            ActionTree tree = snapshot.tree();
            for (WorldIndex index : snapshot.worlds().values()) {
                if (index.locked() || (guardsBorders && index.anyRule())) {
                    return true;
                }
            }
            for (String root : roots) {
                for (int action : tree.subtree(ActionId.of(root))) {
                    if (snapshot.anyWorldSets(action)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /** Every family. */
    public static final List<Family> FAMILIES = List.of(
            new Family(
                    "block",
                    List.of(
                            "break",
                            "place",
                            "item.use",
                            "hanging.place",
                            "vehicle.place",
                            "use.sign",
                            "interact.animal"),
                    false,
                    BlockListener::new),
            new Family("interact", List.of("use", "interact", "trample", "break"), false, InteractListener::new),
            new Family(
                    "damage",
                    List.of("damage", "hanging.break", "vehicle.destroy", "item.use.potion"),
                    false,
                    DamageListener::new),
            new Family("item", List.of("item"), false, ItemListener::new),
            new Family(
                    "nature",
                    List.of("world", "trample", "place", "break", "item.use.bone-meal"),
                    true,
                    NatureListener::new),
            new Family("explosion", List.of("world.explosion", "break"), true, ExplosionListener::new),
            new Family("hopper", List.of("world.hopper"), true, HopperListener::new));

    private final Plugin plugin;
    private final GuardRuntime guard;
    private final Map<String, Listener> registered = new java.util.LinkedHashMap<>();

    public ListenerRegistry(Plugin plugin, GuardRuntime guard) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.guard = Objects.requireNonNull(guard, "guard");
    }

    /** Bring the registered families in line with a snapshot. Call on the global thread. */
    public synchronized void sync(GuardSnapshot snapshot) {
        for (Family family : FAMILIES) {
            boolean needed = family.needed(snapshot);
            Listener current = registered.get(family.name());
            if (needed && current == null) {
                Listener listener = family.factory.apply(guard);
                plugin.getServer().getPluginManager().registerEvents(listener, plugin);
                registered.put(family.name(), listener);
            } else if (!needed && current != null) {
                HandlerList.unregisterAll(current);
                registered.remove(family.name());
            }
        }
    }

    /** The families registered now, for status. */
    public synchronized List<String> active() {
        return List.copyOf(registered.keySet());
    }

    /** Unregister everything. */
    public synchronized void close() {
        registered.values().forEach(HandlerList::unregisterAll);
        registered.clear();
    }
}
