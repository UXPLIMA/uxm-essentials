package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /editsign}: open the sign editor for the sign the player is looking at. The target block is the one in
 * the player's line of sight; it must be a {@link Sign} (else
 * {@link ItemworldMessageKey#EDITSIGN_NOT_A_SIGN}). Build access is respected at the boundary: a
 * {@link BlockBreakEvent} is fired for the target sign and its cancellation by a protection plugin or
 * spawn-protection is honoured ({@link ItemworldMessageKey#EDITSIGN_NO_ACCESS}), so a player cannot rewrite a
 * sign they could not break. With access, the client's sign-editing UI is opened on the side facing the player
 * ({@link ItemworldMessageKey#EDITSIGN_OPENED}); the player types and confirms the lines, which the vanilla
 * sign-change path then validates.
 *
 * <p>Reading the block state and opening the editor are region-bound, so they run on the block's region thread
 * through the kernel {@code Scheduler}.
 */
@NullMarked
public final class EditSignCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.editsign.use";
    private static final int REACH = 8;

    public EditSignCommand(ItemworldServices services) {
        super(services, "editsign", SubFeatureGroup.ITEM_UTILS, "Edit the sign you are looking at.");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Player player = player(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        open(ctx, player);
        return Command.SINGLE_SUCCESS;
    }

    private void open(CommandContext<CommandSourceStack> ctx, Player player) {
        services.kernel().scheduler().onEntity(ref(player), () -> {
            Block target = player.getTargetBlockExact(REACH);
            if (target == null || !(target.getState() instanceof Sign sign)) {
                reply(ctx, ItemworldMessageKey.EDITSIGN_NOT_A_SIGN);
                return;
            }
            if (!canBuildAt(player, target)) {
                reply(ctx, ItemworldMessageKey.EDITSIGN_NO_ACCESS);
                return;
            }
            Side side = sign.getInteractableSideFor(player);
            player.openSign(sign, side);
            reply(ctx, ItemworldMessageKey.EDITSIGN_OPENED);
        });
    }

    private static boolean canBuildAt(Player player, Block block) {
        BlockBreakEvent probe = new BlockBreakEvent(block, player);
        probe.setDropItems(false);
        player.getServer().getPluginManager().callEvent(probe);
        return !probe.isCancelled();
    }
}
