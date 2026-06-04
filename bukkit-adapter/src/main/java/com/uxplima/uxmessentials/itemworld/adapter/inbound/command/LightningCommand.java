package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

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
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /lightning [player]} (alias {@code /smite}): strike lightning at a player, or at the block the caller
 * is looking at when no player is given. An admin-fun verb (audit-logged): a cosmetic-but-abusable effect, so
 * the strike is recorded with actor and target. A named target must resolve online (else
 * {@link ItemworldMessageKey#UNKNOWN_TARGET}).
 *
 * <p>The strike is region-bound, so it runs on the target's region thread through the kernel {@code Scheduler};
 * the result is reported through {@link ItemworldMessageKey#LIGHTNING_STRUCK} and audited.
 */
@NullMarked
public final class LightningCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.lightning.use";
    private static final int REACH = 64;

    public LightningCommand(ItemworldServices services) {
        super(services, "lightning", SubFeatureGroup.ADMIN_FUN, "Strike lightning.");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(ctx -> run(ctx, Optional.empty()))
                .then(CommandSuggestions.playerArgument("player")
                        .executes(ctx -> run(ctx, Optional.of(StringArgumentType.getString(ctx, "player")))))
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    @Override
    public java.util.List<String> aliases() {
        return java.util.List.of("smite");
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
            strikeAt(ctx, self, target, target.getName());
        } else {
            strikeAt(ctx, self, self, self.getName());
        }
        return Command.SINGLE_SUCCESS;
    }

    private void strikeAt(CommandContext<CommandSourceStack> ctx, Player actor, Player at, String label) {
        PlayerRef actorRef = ref(actor);
        Optional<PlayerRef> targetRef = at == actor ? Optional.empty() : Optional.of(BukkitRefs.toRef(at));
        services.kernel().scheduler().onEntity(ref(at), () -> {
            Location where = resolveStrike(at);
            at.getWorld().strikeLightning(where);
            reply(ctx, ItemworldMessageKey.LIGHTNING_STRUCK, Map.of("target", label));
            services.audit().struckLightning(actorRef, targetRef);
        });
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
