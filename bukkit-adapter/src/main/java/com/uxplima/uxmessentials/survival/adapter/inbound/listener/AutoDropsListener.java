package com.uxplima.uxmessentials.survival.adapter.inbound.listener;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.survival.adapter.inbound.listener.AutoDropsPipeline.Stages;
import org.jspecify.annotations.NullMarked;

/**
 * The {@code BlockBreakEvent} entry point for the three break-drop auto-* mechanics (auto-pickup, auto-smelt,
 * auto-sell). It is a thin adapter over the shared {@link AutoDropsPipeline}: it resolves which stages are active for
 * the breaker, and when at least one is, takes ownership of the vanilla ground drops ({@code setDropItems(false)}),
 * computes the item set against the breaker's tool (so fortune and silk touch are honoured), and hands the items to the
 * pipeline. The listener is only registered when at least one of the three is enabled.
 *
 * <p>A break in creative mode, or one whose drops another plugin has already suppressed ({@code isDropItems()} is
 * false), is left entirely to vanilla: the auto-* mechanics never fire, so a creative break drops and pockets nothing.
 *
 * <p>The one thing that is genuinely event-shaped stays here: transferring the block's dropped experience straight to
 * the player when auto-pickup and {@code transfer-xp} are both on. Everything else, the smelt/sell/route transform, is
 * the pipeline's, shared verbatim with the tree-feller and veinminer cascades.
 *
 * <h2>Deferred routing</h2>
 * The route is scheduled a tick later, on the broken block's region, rather than run inline. Two reasons: the vanilla
 * break has not yet removed the origin block when the event fires, so dropping at the block's own location inline would
 * eject the item out of the still-solid block (the fling / lodge-in-neighbour behaviour); and mutating or reading the
 * block mid-event would race the harvesting listeners on the same break, which read the origin's type to decide whether
 * to fell or vein. By the time the deferred task runs the origin is air, so the drop spawns into empty space. The
 * cascade needs no such deferral: it clears each felled block to air itself before routing.
 *
 * <h2>Folia</h2>
 * The break event runs on the region owning the broken block; the deferred route is hopped to that same region through
 * the injected {@link Scheduler}, so the inventory, world drops, and wallet credit all run on the owning thread, a tick
 * after the break, mirroring how the tree-feller replant is deferred.
 */
@NullMarked
public final class AutoDropsListener implements Listener {

    private final AutoDropsPipeline pipeline;
    private final Scheduler scheduler;
    private final boolean transferXp;

    public AutoDropsListener(AutoDropsPipeline pipeline, Scheduler scheduler, boolean transferXp) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.transferXp = transferXp;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || !event.isDropItems()) {
            return; // a creative or drop-suppressed break does the vanilla nothing: no auto-drops
        }
        Stages stages = pipeline.stagesFor(player);
        if (!stages.anyActive()) {
            return;
        }
        Block block = event.getBlock();
        List<ItemStack> drops =
                new ArrayList<>(block.getDrops(player.getInventory().getItemInMainHand()));
        event.setDropItems(false);
        if (stages.pickup() && transferXp && event.getExpToDrop() > 0) {
            player.giveExp(event.getExpToDrop());
            event.setExpToDrop(0);
        }
        // Route a tick later, once the vanilla break has cleared the origin, so the ground drop spawns into now-air
        // instead of being flung out of the still-solid block (and so we never touch the block mid-event).
        Location where = block.getLocation();
        Position region = BukkitRefs.toPosition(where);
        scheduler.onRegion(region, () -> pipeline.route(player, where, drops, stages));
    }
}
