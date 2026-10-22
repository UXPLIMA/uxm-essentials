package com.uxplima.uxmessentials.holograms.adapter.inbound.listener;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.LongSupplier;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.holograms.adapter.outbound.HologramPageCycler;
import com.uxplima.uxmessentials.holograms.adapter.outbound.PacketHologramView;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.ClickActionRunner;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * What a click on a hologram's box does. The box is drawn with the hologram, and a click on it reaches this on the
 * clicking player's own thread, where running their command and turning their page are safe.
 *
 * <p>Both clicks act, mirroring the npc context's precedence: the hologram's single click command runs on a right
 * click only, otherwise a paged hologram turns the clicking player's page, and then the action chain runs, filtering
 * itself by each action's trigger. A player who clicks twice inside {@link #COOLDOWN_MS} is answered once.
 */
@NullMarked
public final class HologramClickHandler implements PacketHologramView.ClickHandler {

    private static final long COOLDOWN_MS = 250L;
    // A click older than the cooldown answers nothing, so past this many remembered clicks the old ones go. Below
    // it a sweep is not worth its walk; above it the map holds only the clicks of the last quarter second.
    private static final int SWEEP_ABOVE = 64;

    private final Function<HologramName, Optional<Hologram>> holograms;
    private final HologramPageCycler pageCycler;
    private final ClickActionRunner actionRunner;
    private final LongSupplier clock;
    private final ConcurrentHashMap<UUID, Long> lastClick = new ConcurrentHashMap<>();

    /**
     * @param holograms the stored hologram by name, read from memory so a click never waits on the database
     * @param clock the current time in milliseconds
     */
    public HologramClickHandler(
            Function<HologramName, Optional<Hologram>> holograms,
            HologramPageCycler pageCycler,
            ClickActionRunner actionRunner,
            LongSupplier clock) {
        this.holograms = Objects.requireNonNull(holograms, "holograms");
        this.pageCycler = Objects.requireNonNull(pageCycler, "pageCycler");
        this.actionRunner = Objects.requireNonNull(actionRunner, "actionRunner");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void clicked(Player player, HologramName name, boolean attack) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(name, "name");
        if (onCooldown(player.getUniqueId())) {
            return;
        }
        Optional<Hologram> found = holograms.apply(name);
        if (found.isEmpty()) {
            return;
        }
        Hologram hologram = found.get();
        switch (primaryBehaviour(hologram.clickCommand(), hologram.isMultiPage(), attack)) {
            case RUN_COMMAND -> player.performCommand(Objects.requireNonNull(hologram.clickCommand(), "clickCommand"));
            case CYCLE_PAGE -> pageCycler.cyclePage(player, hologram.name());
            case NONE -> {
                // No command and no second page: the action chain below is the only behaviour.
            }
        }
        // The chain always runs after the primary, so a hologram may have both; it filters itself by trigger, so a
        // left click and a right click each fire only their own actions.
        if (hologram.hasActions()) {
            actionRunner.run(player, hologram.actions(), attack);
        }
    }

    /** The single-click behaviour, decided apart from the action chain, which always runs after it. */
    enum Primary {
        RUN_COMMAND,
        CYCLE_PAGE,
        NONE
    }

    /**
     * Decide the single-click behaviour. A non-blank click command runs on a right click only, as an NPC's single
     * click command does; otherwise a paged hologram turns its page on either click.
     */
    static Primary primaryBehaviour(@Nullable String clickCommand, boolean multiPage, boolean attack) {
        boolean hasCommand = clickCommand != null && !clickCommand.isBlank();
        if (hasCommand && !attack) {
            return Primary.RUN_COMMAND;
        }
        if (multiPage) {
            return Primary.CYCLE_PAGE;
        }
        return Primary.NONE;
    }

    /** How many clicks this handler is holding on to. */
    int remembered() {
        return lastClick.size();
    }

    /** True when this player clicked inside the cooldown; records the click time otherwise. */
    private boolean onCooldown(UUID player) {
        long now = clock.getAsLong();
        Long previous = lastClick.put(player, now);
        if (lastClick.size() > SWEEP_ABOVE) {
            lastClick.values().removeIf(at -> now - at >= COOLDOWN_MS);
        }
        return previous != null && now - previous < COOLDOWN_MS;
    }
}
