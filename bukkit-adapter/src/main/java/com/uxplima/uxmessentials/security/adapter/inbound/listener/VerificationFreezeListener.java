package com.uxplima.uxmessentials.security.adapter.inbound.listener;

import java.util.Map;
import java.util.Objects;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import io.papermc.paper.event.player.AsyncChatEvent;

import com.uxplima.uxmessentials.security.adapter.FreezeTeleports;
import com.uxplima.uxmessentials.security.adapter.VerificationSessions;
import com.uxplima.uxmessentials.security.application.SecurityMessageKey;
import com.uxplima.uxmessentials.security.domain.FreezeRestriction;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Pins a frozen player in place until they verify: while a player is a pending {@link VerificationSessions} entry,
 * each action listed in {@code join-verification.restrictions} is cancelled, and the only thing that answers is the
 * keypad. A relog re-runs the join decision and a successful verification clears the pending entry, so this listener
 * naturally stops cancelling the moment the player is no longer frozen.
 *
 * <p>Half of the deny-list is restraint (movement, commands, chat, edits) and half is protection. A player looking at
 * a keypad cannot fight back, run, or eat, so {@link FreezeRestriction#DAMAGE_TAKEN} and
 * {@link FreezeRestriction#MOB_TARGETING} keep the world from killing an account holder while they prove they own the
 * account, {@link FreezeRestriction#HUNGER} keeps a slow verification from starving them, and
 * {@link FreezeRestriction#TELEPORT} keeps anyone else from moving them. Because these are protections, the listener
 * runs at {@link EventPriority#LOWEST} for them and does <em>not</em> set {@code ignoreCancelled}: it is cancelling,
 * never un-cancelling, so nothing downstream loses a decision it had already made.
 *
 * <p>An attempted command or chat line, a deliberate act unlike incidental movement, draws the "verify first" nudge so
 * the player understands why nothing happened; the silent physical cancels do not spam it. Head-only movement
 * (yaw/pitch) is always left alone so a frozen player can still look around, and the inventory guard deliberately
 * ignores the keypad window itself, which the menu engine already governs.
 */
@NullMarked
public final class VerificationFreezeListener implements Listener {

    private final VerificationSessions sessions;
    private final Restrictions restrictions;
    private final FreezeTeleports ownTeleports;
    private final MenuWindows windows;
    private final Messages messages;
    private final MessageSink sink;

    public VerificationFreezeListener(
            VerificationSessions sessions,
            Restrictions restrictions,
            FreezeTeleports ownTeleports,
            MenuWindows windows,
            Messages messages,
            MessageSink sink) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.restrictions = Objects.requireNonNull(restrictions, "restrictions");
        this.ownTeleports = Objects.requireNonNull(ownTeleports, "ownTeleports");
        this.windows = Objects.requireNonNull(windows, "windows");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    /** The configured deny-list, narrowed to the one question this listener asks of it. */
    @FunctionalInterface
    public interface Restrictions {

        /** Whether a frozen player is stopped from {@code restriction}. */
        boolean restricts(FreezeRestriction restriction);
    }

    /**
     * Whether the window a player has open belongs to the menu engine, asked through the engine's own facade rather
     * than by recognising its holder type, which is engine-private. While a player is frozen the only menu they can
     * have open is the keypad, so this is the "do not swallow the buttons the freeze is waiting for" test.
     */
    @FunctionalInterface
    public interface MenuWindows {

        /** Whether {@code player}'s open window is a menu-engine window. */
        boolean isMenuOpen(Player player);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (blocked(event.getPlayer(), FreezeRestriction.MOVE) && movedBlock(event)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (blocked(event.getPlayer(), FreezeRestriction.COMMANDS)) {
            event.setCancelled(true);
            nudge(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        // The TOTP-via-chat capture runs at LOWEST and cancels first, so ignoreCancelled skips it here; any other
        // chat line from a frozen player is swallowed and nudged.
        if (blocked(event.getPlayer(), FreezeRestriction.CHAT)) {
            event.setCancelled(true);
            nudge(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (blocked(event.getPlayer(), FreezeRestriction.INTERACT)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (blocked(event.getPlayer(), FreezeRestriction.INTERACT)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (blocked(event.getPlayer(), FreezeRestriction.DROP)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (blocked(event.getPlayer(), FreezeRestriction.BLOCK_EDIT)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (blocked(event.getPlayer(), FreezeRestriction.BLOCK_EDIT)) {
            event.setCancelled(true);
        }
    }

    /** Stop a frozen player picking items up, so they cannot be walked into a drop pile and used as a container. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && blocked(player, FreezeRestriction.PICKUP)) {
            event.setCancelled(true);
        }
    }

    /**
     * Keep a frozen player alive. This covers every damage source, mob, player, fall, fire and drowning alike, so
     * nobody dies at a keypad they were not allowed to walk away from. It also catches the outgoing half through
     * {@link EntityDamageByEntityEvent}, so the freeze cannot be used as a place to hit from with impunity.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && blocked(player, FreezeRestriction.DAMAGE_TAKEN)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageDealt(EntityDamageByEntityEvent event) {
        if (dealer(event.getDamager()) instanceof Player player && blocked(player, FreezeRestriction.DAMAGE_DEALT)) {
            event.setCancelled(true);
        }
    }

    /** Stop mobs choosing a frozen player at all, the companion to cancelling the damage they would have dealt. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onTarget(EntityTargetEvent event) {
        if (event.getTarget() instanceof Player player && blocked(player, FreezeRestriction.MOB_TARGETING)) {
            event.setCancelled(true);
        }
    }

    /**
     * Stop anything moving a frozen player, whether that is a command, a plugin, a portal or a thrown pearl. The one
     * exception is the module's own move into and out of the holding area, which announces itself beforehand; a
     * teleport event carries a cause and not an author, so being told is the only way to tell those two apart from
     * everybody else's.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!blocked(event.getPlayer(), FreezeRestriction.TELEPORT)) {
            return;
        }
        if (ownTeleports.consume(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
    }

    /**
     * Freeze the player's own inventory. The keypad window is skipped: the menu engine already cancels every click
     * and drag on it, and cancelling here as well would swallow the button presses the whole freeze is waiting for.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player
                && blocked(player, FreezeRestriction.INVENTORY)
                && !windows.isMenuOpen(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player
                && blocked(player, FreezeRestriction.INVENTORY)
                && !windows.isMenuOpen(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (blocked(event.getPlayer(), FreezeRestriction.CONSUME)) {
            event.setCancelled(true);
        }
    }

    /** Hold the hunger bar still, so a player who has to find their authenticator app does not starve doing it. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player
                && blocked(player, FreezeRestriction.HUNGER)
                && event.getFoodLevel() < player.getFoodLevel()) {
            event.setCancelled(true);
        }
    }

    private boolean blocked(Player player, FreezeRestriction restriction) {
        return sessions.isPending(player.getUniqueId()) && restrictions.restricts(restriction);
    }

    /**
     * The damaging entity to attribute a hit to: a thrown or fired projectile is attributed to whoever launched it,
     * so a frozen player cannot shoot out of the freeze by using an arrow rather than a sword.
     */
    private static @Nullable Entity dealer(Entity damager) {
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            return shooter;
        }
        return damager;
    }

    private void nudge(Player player) {
        PlayerRef ref = BukkitRefs.toRef(player);
        sink.deliver(ref, messages.resolve(ref, SecurityMessageKey.SECURITY_VERIFY_MUST_VERIFY, Map.of()));
    }

    private static boolean movedBlock(PlayerMoveEvent event) {
        var from = event.getFrom();
        var to = event.getTo();
        return from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ();
    }
}
