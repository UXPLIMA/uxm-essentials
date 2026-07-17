package com.uxplima.uxmessentials.villagers.adapter.inbound.command;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;

import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.villagers.adapter.outbound.PdcVillagerFlags;
import com.uxplima.uxmessentials.villagers.application.VillagersMessageKey;
import com.uxplima.uxmessentials.villagers.domain.VillagerProtectionPolicy;
import org.jspecify.annotations.NullMarked;

/**
 * Flips a villager's per-villager protection ("saver") mark for the {@code /villager protect} command. The write mutates
 * the live villager's PDC, so it is hopped onto the villager's own region thread through the {@link Scheduler} port
 * (the villager sits within reach of the commanding player, so its region is the player's); there the mark is flipped,
 * a villager that just became protected is immediately kept loaded when the no-despawn gate is on, and the player is
 * told the new state in their own locale.
 */
@NullMarked
public final class VillagerProtectToggle {

    private final Scheduler scheduler;
    private final PdcVillagerFlags flags;
    private final VillagerProtectionPolicy policy;
    private final CommandFeedback feedback;

    public VillagerProtectToggle(
            Scheduler scheduler, PdcVillagerFlags flags, VillagerProtectionPolicy policy, Messages messages) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.flags = Objects.requireNonNull(flags, "flags");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    /** Toggle {@code villager}'s protection mark for {@code player}, scheduled on the villager's region. */
    public void toggle(Player player, PlayerRef ref, Villager villager) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(villager, "villager");
        scheduler.onEntity(ref, () -> apply(player, villager));
    }

    private void apply(Player player, Villager villager) {
        boolean nowProtected = !flags.isProtected(villager);
        flags.setProtected(villager, nowProtected);
        if (nowProtected && policy.protectsDespawn(true)) {
            // The persistent flag is the despawn guard (see VillagerProtectionListener#applyDespawnGuard).
            villager.setPersistent(true);
        }
        if (player.isOnline()) {
            feedback.send(
                    player,
                    nowProtected
                            ? VillagersMessageKey.VILLAGERS_PROTECT_ENABLED
                            : VillagersMessageKey.VILLAGERS_PROTECT_DISABLED);
        }
    }
}
