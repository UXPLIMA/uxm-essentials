package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /nuke [player]}: rain lightning over the target area — the block the caller is looking at, or a named
 * player's position. An admin-fun verb (audit-logged): a destructive, abusable effect, so each storm is
 * recorded with actor and target. A named target must resolve online (else
 * {@link ItemworldMessageKey#UNKNOWN_TARGET}).
 *
 * <p>The storm is region-bound, so it runs on the target's region thread through the kernel {@code Scheduler}.
 * To stay bounded it strikes a fixed set of offset points around the centre rather than every block in the
 * area; the result is reported through {@link ItemworldMessageKey#NUKE_DONE} and audited.
 */
@NullMarked
public final class NukeCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.nuke.use";
    private static final int REACH = 64;
    // A bounded plus/diagonal pattern around the centre: thirteen strikes spanning a five-block radius. Fixed
    // offsets keep this well clear of a per-block O(n^2) storm while still reading as an area effect.
    private static final int[][] OFFSETS = {
        {0, 0}, {3, 0}, {-3, 0}, {0, 3}, {0, -3}, {2, 2}, {-2, 2}, {2, -2}, {-2, -2}, {5, 0}, {-5, 0}, {0, 5}, {0, -5}
    };

    public NukeCommand(ItemworldServices services) {
        super(services, "nuke", SubFeatureGroup.ADMIN_FUN, "Rain lightning over an area.");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(ctx -> run(ctx, Optional.empty()))
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> run(ctx, Optional.of(StringArgumentType.getString(ctx, "player")))))
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    private int run(CommandContext<CommandSourceStack> ctx, Optional<String> name) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Player self = player(ctx);
        if (self == null) {
            return Command.SINGLE_SUCCESS;
        }
        if (name.isPresent()) {
            Player target = self.getServer().getPlayerExact(name.get());
            if (target == null) {
                reply(ctx, ItemworldMessageKey.UNKNOWN_TARGET, Map.of("player", name.get()));
                return Command.SINGLE_SUCCESS;
            }
            nukeAt(ctx, self, target, target.getName());
        } else {
            nukeAt(ctx, self, self, self.getName());
        }
        return Command.SINGLE_SUCCESS;
    }

    private void nukeAt(CommandContext<CommandSourceStack> ctx, Player actor, Player at, String label) {
        PlayerRef actorRef = ref(actor);
        Optional<PlayerRef> targetRef = at == actor ? Optional.empty() : Optional.of(BukkitRefs.toRef(at));
        services.kernel().scheduler().onEntity(ref(at), () -> {
            Location centre = resolveStrike(at);
            for (Location point : strikePoints(centre)) {
                at.getWorld().strikeLightning(point);
            }
            reply(ctx, ItemworldMessageKey.NUKE_DONE, Map.of("target", label));
            services.audit().nuked(actorRef, targetRef);
        });
    }

    private static List<Location> strikePoints(Location centre) {
        java.util.List<Location> points = new java.util.ArrayList<>(OFFSETS.length);
        for (int[] offset : OFFSETS) {
            points.add(centre.clone().add(offset[0], 0, offset[1]));
        }
        return points;
    }

    private static Location resolveStrike(Player at) {
        // Paper marks Player#getLocation() nullable (null only for an entity with no world, which a connected
        // player never is), so the fallback is requireNonNull rather than a nullable return.
        org.bukkit.block.@org.jspecify.annotations.Nullable Block targetBlock = at.getTargetBlockExact(REACH);
        if (targetBlock == null) {
            return java.util.Objects.requireNonNull(at.getLocation(), "player location");
        }
        return targetBlock.getLocation();
    }
}
