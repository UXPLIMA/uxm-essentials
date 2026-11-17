package com.uxplima.uxmessentials.shared.application.settings;

import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PlayerSwitchStore;
import com.uxplima.uxmessentials.shared.application.port.PlayerSwitchStore.Switch;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;

/**
 * The on/off switches one module lets a player set for themselves, shared by every backend: held for the players
 * online here and written back when they change (see {@link HeldPlayerState}). A switch the player never set reads as
 * the default its caller names. Only the switches named here are read, and only the switches that changed are
 * written, each with when it was set, so two modules keeping their switches in the same table never write over each
 * other and two backends writing the same switch keep the newer setting.
 */
public final class PlayerSwitches implements HeldSettings {

    private final Set<String> names;
    private final Clock clock;
    private final HeldPlayerState<Map<String, Switch>> state;

    /**
     * @param names the switches this module owns
     * @param changed told, on the async lane, that a player's switches were written
     */
    public PlayerSwitches(
            Set<String> names,
            PlayerSwitchStore store,
            Clock clock,
            Scheduler scheduler,
            Logger log,
            Consumer<UUID> changed) {
        this.names = Set.copyOf(names);
        this.clock = Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(store, "store");
        this.state = new HeldPlayerState<>(
                "switches",
                new HeldPlayerState.Storage<>() {
                    @Override
                    public Map<String, Switch> read(UUID player) {
                        return Map.copyOf(store.load(player, PlayerSwitches.this.names));
                    }

                    @Override
                    public void write(UUID player, Map<String, Switch> before, Map<String, Switch> after) {
                        Map<String, Switch> differ = new HashMap<>();
                        after.forEach((name, now) -> {
                            if (!now.equals(before.get(name))) {
                                differ.put(name, now);
                            }
                        });
                        if (!differ.isEmpty()) {
                            store.save(player, differ);
                        }
                    }
                },
                Map.of(),
                scheduler,
                log,
                changed);
    }

    /** Whether the switches of {@code player} were read here, so a change can be made to them. */
    public boolean ready(UUID player) {
        return state.holds(player);
    }

    /** Whether {@code name} is on for {@code player}, or {@code byDefault} when they never set it. */
    public boolean isOn(UUID player, String name, boolean byDefault) {
        Switch set = state.get(player).get(owned(name));
        return set == null ? byDefault : set.on();
    }

    /**
     * Turn {@code name} the other way for {@code player}; whether it is on now, or empty when their switches were not
     * read, so the change is refused.
     */
    public Optional<Boolean> flip(UUID player, String name, boolean byDefault) {
        String owned = owned(name);
        long now = clock.millis();
        return state.update(player, current -> {
                    Switch set = current.get(owned);
                    boolean on = set == null ? byDefault : set.on();
                    return with(current, owned, new Switch(!on, now));
                })
                .map(switches -> Objects.requireNonNull(switches.get(owned)).on());
    }

    /**
     * Take {@code on} as {@code player}'s setting of {@code name} when they have not set it yet: a setting kept by an
     * earlier version on one server only, brought into the shared one. Nothing is taken for a player whose switches
     * were not read.
     */
    public void adopt(UUID player, String name, boolean on) {
        String owned = owned(name);
        long now = clock.millis();
        if (ready(player) && !state.get(player).containsKey(owned)) {
            var unused = state.update(
                    player,
                    current -> current.containsKey(owned) ? current : with(current, owned, new Switch(on, now)));
        }
    }

    /** Whether the database was last known to hold {@code player}'s setting of {@code name}. */
    public boolean stored(UUID player, String name) {
        String owned = owned(name);
        return state.stored(player).map(switches -> switches.containsKey(owned)).orElse(false);
    }

    /** The players held here who turned {@code name} on. */
    public Set<UUID> holding(String name) {
        String owned = owned(name);
        Set<UUID> on = new HashSet<>();
        state.all().forEach((player, switches) -> {
            Switch set = switches.get(owned);
            if (set != null && set.on()) {
                on.add(player);
            }
        });
        return on;
    }

    @Override
    public boolean holds(UUID player) {
        return state.holds(player);
    }

    @Override
    public void load(UUID player) {
        state.load(player);
    }

    @Override
    public void forget(UUID player) {
        state.forget(player);
    }

    @Override
    public void refresh(UUID player) {
        state.refresh(player);
    }

    private String owned(String name) {
        if (!names.contains(Objects.requireNonNull(name, "name"))) {
            throw new IllegalArgumentException("not a switch of this module: " + name);
        }
        return name;
    }

    private static Map<String, Switch> with(Map<String, Switch> current, String name, Switch set) {
        Map<String, Switch> next = new HashMap<>(current);
        next.put(name, set);
        return Map.copyOf(next);
    }
}
