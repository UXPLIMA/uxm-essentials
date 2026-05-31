package com.uxplima.uxmessentials.presence.adapter.outbound;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.presence.application.port.VisibilityApplier;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link VisibilityApplier} implementation: it drives Bukkit's {@code hidePlayer} / {@code showPlayer}
 * graph so a vanished player disappears from everyone who lacks the vanish-see node and reappears on unvanish.
 * This is the same graph the messaging {@code /msg} and teleport {@code /tpa} contexts read through
 * {@code canSee} — driving it here is what makes a vanished player vanish from their target resolution without
 * those contexts importing presence.
 *
 * <p>Every mutation hops to the affected player's owning region/entity thread through the injected
 * {@link Scheduler} port — {@code hidePlayer}/{@code showPlayer} are per-viewer entity operations valid only on
 * the owning thread on Folia. An offline player on either side is a silent no-op. The vanish-see node is checked
 * per viewer so staff keep seeing one another.
 *
 * <p>{@link #reconcileOnJoin} re-hides a vanished player from everyone the moment they relog (persisted
 * vanish); re-hiding already-vanished <em>others</em> from a fresh joiner is the join listener's job, which
 * re-applies each currently-vanished player's {@link #hide} so this adapter holds no vanish roster of its own.
 */
@NullMarked
public final class BukkitVisibilityApplier implements VisibilityApplier {

    private static final String SEE_NODE = "uxmessentials.vanish.see";

    private final Plugin plugin;
    private final Scheduler scheduler;

    public BukkitVisibilityApplier(Plugin plugin, Scheduler scheduler) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public void hide(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        scheduler.onEntity(who, () -> applyHide(who));
    }

    @Override
    public void reveal(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        scheduler.onEntity(who, () -> applyReveal(who));
    }

    @Override
    public void reconcileOnJoin(PlayerRef who, boolean vanished) {
        Objects.requireNonNull(who, "who");
        if (vanished) {
            scheduler.onEntity(who, () -> applyHide(who));
        }
    }

    private void applyHide(PlayerRef who) {
        Player target = Bukkit.getPlayer(who.uuid());
        if (target == null || !target.isOnline()) {
            return;
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(target) && !viewer.hasPermission(SEE_NODE)) {
                viewer.hidePlayer(plugin, target);
            }
        }
    }

    private void applyReveal(PlayerRef who) {
        Player target = Bukkit.getPlayer(who.uuid());
        if (target == null || !target.isOnline()) {
            return;
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(target)) {
                viewer.showPlayer(plugin, target);
            }
        }
    }
}
