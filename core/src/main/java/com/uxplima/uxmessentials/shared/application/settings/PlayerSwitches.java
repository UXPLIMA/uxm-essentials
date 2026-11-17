package com.uxplima.uxmessentials.shared.application.settings;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PlayerSwitchStore;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;

/**
 * The on/off switches one module lets a player set for themselves, shared by every backend: held for the players
 * online here and written back when they change (see {@link HeldPlayerState}). A switch the player never set reads as
 * the default its caller names. Only the switches named here are read and written, so two modules keeping their
 * switches in the same table never write over each other.
 */
public final class PlayerSwitches implements HeldSettings {

    private final Set<String> names;
    private final HeldPlayerState<Map<String, Boolean>> state;

    /**
     * @param names the switches this module owns
     * @param changed told, on the async lane, that a player's switches were written
     */
    public PlayerSwitches(
            Set<String> names, PlayerSwitchStore store, Scheduler scheduler, Logger log, Consumer<UUID> changed) {
        this.names = Set.copyOf(names);
        Objects.requireNonNull(store, "store");
        this.state = new HeldPlayerState<>(
                "switches",
                new HeldPlayerState.Storage<>() {
                    @Override
                    public Map<String, Boolean> read(UUID player) {
                        return Map.copyOf(store.load(player, PlayerSwitches.this.names));
                    }

                    @Override
                    public void write(UUID player, Map<String, Boolean> switches) {
                        store.save(player, switches);
                    }
                },
                Map.of(),
                scheduler,
                log,
                changed);
    }

    /** Whether {@code name} is on for {@code player}, or {@code byDefault} when they never set it. */
    public boolean isOn(UUID player, String name, boolean byDefault) {
        return state.get(player).getOrDefault(owned(name), byDefault);
    }

    /** Turn {@code name} the other way for {@code player}; whether it is on now. */
    public boolean flip(UUID player, String name, boolean byDefault) {
        String owned = owned(name);
        return state.update(player, current -> with(current, owned, !current.getOrDefault(owned, byDefault)))
                .getOrDefault(owned, byDefault);
    }

    /**
     * Take {@code on} as {@code player}'s setting of {@code name} when they have not set it yet: a setting kept by an
     * earlier version on one server only, brought into the shared one.
     */
    public void adopt(UUID player, String name, boolean on) {
        String owned = owned(name);
        if (!state.get(player).containsKey(owned)) {
            state.update(player, current -> current.containsKey(owned) ? current : with(current, owned, on));
        }
    }

    /** The players held here who turned {@code name} on. */
    public Set<UUID> holding(String name) {
        String owned = owned(name);
        Set<UUID> on = new HashSet<>();
        state.all().forEach((player, switches) -> {
            if (Boolean.TRUE.equals(switches.get(owned))) {
                on.add(player);
            }
        });
        return on;
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

    @Override
    public void refreshAll() {
        state.refreshAll();
    }

    private String owned(String name) {
        if (!names.contains(Objects.requireNonNull(name, "name"))) {
            throw new IllegalArgumentException("not a switch of this module: " + name);
        }
        return name;
    }

    private static Map<String, Boolean> with(Map<String, Boolean> current, String name, boolean on) {
        Map<String, Boolean> next = new HashMap<>(current);
        next.put(name, on);
        return Map.copyOf(next);
    }
}
