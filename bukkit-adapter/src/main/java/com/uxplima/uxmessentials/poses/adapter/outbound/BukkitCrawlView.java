package com.uxplima.uxmessentials.poses.adapter.outbound;

import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.poses.application.port.CrawlView;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@link CrawlView} over Paper's client-only block change — {@code player.sendBlockChange}, the classic
 * fake-block crawl trick. To hold a player crawling, an invisible {@link Material#BARRIER} is sent to <em>only</em>
 * that player's connection at the block their head would occupy when standing (a block above their feet); the
 * server never places a real block, so there is no server-side block update and no real suffocation. The client
 * sees a solid block where it would stand, so it stays in the low swimming pose the {@code BukkitPacketPosePort}
 * broadcasts.
 *
 * <p>Ghost-prevention lives in {@link #restoreRealBlock}: it resends the world's actual block data at the fake
 * position, so the client sees reality again. The crawl move-follow restores the block just vacated and shows a new
 * one at the new head each block, and every crawl exit restores the last fake block — so no phantom block is ever
 * left on a player's screen. Restoring a block the client already sees correctly is a harmless no-op, which keeps
 * the restore safe to send on every exit path.
 *
 * <p>The fake block is placed at the head block, not engulfing the crawling body: a crawling player is about
 * 0.6 blocks tall and occupies only the feet block, so the barrier a block above sits at the top boundary above
 * their eyes rather than inside the hitbox — the client renders no suffocation or darkness overlay. When a real
 * solid block already fills the head space the player is already in a one-block gap, so the fake block is simply
 * not sent there ({@link Block#isPassable()} is false); the swimming pose still carries the crawl look.
 *
 * <p>Every call runs on the caller's thread — the {@code /crawl} command, the move listener, and {@code StopPose}
 * all invoke it on the player's own region thread, where the player's block column is owned — so a single-player
 * client packet and a block read at the player's own head need no scheduler hop. The player and world are resolved
 * from the refs; an offline player or unloaded world is a clean no-op.
 */
@NullMarked
public final class BukkitCrawlView implements CrawlView {

    private final Server server;
    // The invisible solid block the client believes it cannot stand into. Built once — never on the move hot path.
    private final BlockData fakeBlock;

    public BukkitCrawlView(Server server) {
        this.server = Objects.requireNonNull(server, "server");
        this.fakeBlock = Material.BARRIER.createBlockData();
    }

    @Override
    public void showFakeBlockAbove(PlayerRef who, Position headBlock) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(headBlock, "headBlock");
        Player player = server.getPlayer(who.uuid());
        Block block = blockAt(player, headBlock);
        if (player == null || block == null || !block.isPassable()) {
            // Offline/unloaded, or a real solid block already fills the head space (a one-block gap): no fake needed.
            return;
        }
        player.sendBlockChange(block.getLocation(), fakeBlock);
    }

    @Override
    public void restoreRealBlock(PlayerRef who, Position headBlock) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(headBlock, "headBlock");
        Player player = server.getPlayer(who.uuid());
        Block block = blockAt(player, headBlock);
        if (player == null || block == null) {
            return;
        }
        // Resend the world's true block data so the client drops any fake block it still shows at this position.
        player.sendBlockChange(block.getLocation(), block.getBlockData());
    }

    private @Nullable Block blockAt(@Nullable Player player, Position headBlock) {
        if (player == null) {
            return null;
        }
        World world = server.getWorld(headBlock.world().uid());
        if (world == null) {
            return null;
        }
        return world.getBlockAt(headBlock.blockX(), headBlock.blockY(), headBlock.blockZ());
    }
}
