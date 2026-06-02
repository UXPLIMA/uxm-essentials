package com.uxplima.uxmessentials.playerstate.adapter.outbound;

import java.util.Objects;
import java.util.function.BiConsumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.playerstate.application.port.InventoryViewer;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link InventoryViewer} implementation for {@code /invsee} and {@code /endersee}. The open targets the
 * viewer's own screen, so it must run on the viewer's owning entity thread through the injected
 * {@link Scheduler} port; both players are resolved on that thread and the open is skipped when either has gone
 * offline. Mirrors {@link BukkitPlayerEffects}' {@code onEntity} guard, but keyed to the viewer rather than the
 * subject because the open belongs to the viewer.
 */
@NullMarked
public final class BukkitInventoryViewer implements InventoryViewer {

    private final Scheduler scheduler;

    public BukkitInventoryViewer(Scheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public void viewInventory(PlayerRef viewer, PlayerRef subject) {
        open(viewer, subject, (looker, target) -> looker.openInventory(target.getInventory()));
    }

    @Override
    public void viewEnderChest(PlayerRef viewer, PlayerRef subject) {
        open(viewer, subject, (looker, target) -> looker.openInventory(target.getEnderChest()));
    }

    private void open(PlayerRef viewer, PlayerRef subject, BiConsumer<Player, Player> action) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(subject, "subject");
        scheduler.onEntity(viewer, () -> {
            Player looker = Bukkit.getPlayer(viewer.uuid());
            Player target = Bukkit.getPlayer(subject.uuid());
            if (looker != null && looker.isOnline() && target != null && target.isOnline()) {
                action.accept(looker, target);
            }
        });
    }
}
