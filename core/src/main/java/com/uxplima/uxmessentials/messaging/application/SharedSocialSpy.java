package com.uxplima.uxmessentials.messaging.application;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.messaging.application.port.SocialSpyStore;
import com.uxplima.uxmessentials.messaging.application.port.SpyTargetStore;
import com.uxplima.uxmessentials.shared.application.permission.SocialPermissions;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.settings.HeldPlayerState;
import com.uxplima.uxmessentials.shared.application.settings.HeldSettings;
import com.uxplima.uxmessentials.shared.application.settings.PlayerSwitches;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The {@link SocialSpyStore} every backend shares: the {@code /socialspy} switch is one of the shared
 * {@link PlayerSwitches}, and the players a staff member watches are held the same way (see {@link HeldPlayerState}),
 * so a spy keeps both on every backend and across a relog. Each backend shows a private message to the spies online
 * on it; a message between players elsewhere reaches it over the network.
 *
 * <p>Since spying outlasts the session, a spy is shown a message only while they still hold the social spy node: a
 * staff member whose node was taken away stops seeing private messages at once, without anyone clearing their switch.
 */
public final class SharedSocialSpy implements SocialSpyStore, HeldSettings {

    private final PlayerSwitches switches;
    private final HeldPlayerState<Set<UUID>> targets;
    private final PlayerLookup players;
    private final Permissions permissions;

    /** @param changed told, on the async lane, that a spy's watched players were written */
    public SharedSocialSpy(
            PlayerSwitches switches,
            SpyTargetStore store,
            PlayerLookup players,
            Permissions permissions,
            Scheduler scheduler,
            Logger log,
            Consumer<UUID> changed) {
        this.switches = Objects.requireNonNull(switches, "switches");
        Objects.requireNonNull(store, "store");
        this.players = Objects.requireNonNull(players, "players");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.targets = new HeldPlayerState<>(
                "spy-targets",
                new HeldPlayerState.Storage<>() {
                    @Override
                    public Set<UUID> read(UUID spy) {
                        return Set.copyOf(store.load(spy));
                    }

                    @Override
                    public void write(UUID spy, Set<UUID> before, Set<UUID> after) {
                        Set<UUID> added = new HashSet<>(after);
                        added.removeAll(before);
                        Set<UUID> removed = new HashSet<>(before);
                        removed.removeAll(after);
                        if (!added.isEmpty() || !removed.isEmpty()) {
                            store.change(spy, added, removed);
                        }
                    }
                },
                Set.of(),
                scheduler,
                log,
                changed);
    }

    @Override
    public boolean isSpying(PlayerRef who) {
        return switches.isOn(who.uuid(), MessagingSwitches.SOCIAL_SPY, false);
    }

    @Override
    public boolean toggle(PlayerRef who) {
        return switches.flip(who.uuid(), MessagingSwitches.SOCIAL_SPY, false).orElseGet(() -> isSpying(who));
    }

    @Override
    public boolean ready(PlayerRef who) {
        return switches.ready(who.uuid()) && targets.holds(who.uuid());
    }

    @Override
    public boolean toggleTarget(PlayerRef spy, PlayerRef target) {
        Objects.requireNonNull(spy, "spy");
        UUID watched = Objects.requireNonNull(target, "target").uuid();
        return targets.update(spy.uuid(), current -> {
                    Set<UUID> next = new HashSet<>(current);
                    if (!next.remove(watched)) {
                        next.add(watched);
                    }
                    return Set.copyOf(next);
                })
                .orElseGet(() -> targets.get(spy.uuid()))
                .contains(watched);
    }

    @Override
    public List<PlayerRef> activeSpies() {
        return switches.holding(MessagingSwitches.SOCIAL_SPY).stream()
                .map(this::observer)
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public Set<PlayerRef> observersOf(PlayerRef sender, PlayerRef target) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(target, "target");
        Set<UUID> spies = new HashSet<>(switches.holding(MessagingSwitches.SOCIAL_SPY));
        for (Map.Entry<UUID, Set<UUID>> entry : targets.all().entrySet()) {
            if (entry.getValue().contains(sender.uuid()) || entry.getValue().contains(target.uuid())) {
                spies.add(entry.getKey());
            }
        }
        Set<PlayerRef> observers = new HashSet<>();
        for (UUID spy : spies) {
            observer(spy).ifPresent(observers::add);
        }
        return observers;
    }

    /** The spy as an observer: online here and still holding the social spy node. */
    private Optional<PlayerRef> observer(UUID spy) {
        if (!players.isOnline(spy)) {
            return Optional.empty();
        }
        return players.findByUuid(spy).filter(ref -> permissions.has(ref, SocialPermissions.MSG_SOCIALSPY));
    }

    @Override
    public boolean holds(UUID player) {
        return targets.holds(player);
    }

    @Override
    public void load(UUID player) {
        targets.load(player);
    }

    @Override
    public void forget(UUID player) {
        targets.forget(player);
    }

    @Override
    public void refresh(UUID player) {
        targets.refresh(player);
    }
}
