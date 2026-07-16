package com.uxplima.uxmessentials.survival.adapter.inbound.listener;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.survival.adapter.inbound.listener.AutoDropsPipeline.Stages;
import org.jspecify.annotations.NullMarked;

/**
 * The {@code BlockBreakEvent} entry point for the three break-drop auto-* mechanics (auto-pickup, auto-smelt,
 * auto-sell). It is a thin adapter over the shared {@link AutoDropsPipeline}: it resolves which stages are active for
 * the breaker, and when at least one is, takes ownership of the vanilla ground drops ({@code setDropItems(false)}),
 * computes the item set against the breaker's tool (so fortune and silk touch are honoured), and hands it to the
 * pipeline. The listener is only registered when at least one of the three is enabled.
 *
 * <p>The one thing that is genuinely event-shaped stays here: transferring the block's dropped experience straight to
 * the player when auto-pickup and {@code transfer-xp} are both on. Everything else — the smelt/sell/route transform —
 * is the pipeline's, shared verbatim with the tree-feller and veinminer cascades.
 *
 * <h2>Folia</h2>
 * The break event runs on the region owning the broken block; the pipeline applies the inventory, the world drops, and
 * the wallet credit inline on that thread, so no scheduler hop is needed.
 */
@NullMarked
public final class AutoDropsListener implements Listener {

    private final AutoDropsPipeline pipeline;
    private final boolean transferXp;

    public AutoDropsListener(AutoDropsPipeline pipeline, boolean transferXp) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        this.transferXp = transferXp;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
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
        pipeline.route(player, block.getLocation(), drops, stages);
    }
}
