package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /fireball}: launch a fireball from the caller's eye in their facing direction. An admin-fun verb
 * (audit-logged). Launching an entity is region-bound, so it runs on the caller's region thread through the
 * kernel {@code Scheduler}; the launch is reported through {@link ItemworldMessageKey#FIREBALL_LAUNCHED} and
 * audited.
 */
@NullMarked
public final class FireballCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.fireball.use";
    private static final double SPEED = 2.0;

    public FireballCommand(ItemworldServices services) {
        super(services, "fireball", SubFeatureGroup.ADMIN_FUN, "Launch a fireball.");
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
        PlayerRef actor = ref(player);
        services.kernel().scheduler().onEntity(actor, () -> {
            Fireball fireball = player.launchProjectile(Fireball.class);
            fireball.setDirection(player.getEyeLocation().getDirection());
            fireball.setVelocity(player.getEyeLocation().getDirection().multiply(SPEED));
            reply(ctx, ItemworldMessageKey.FIREBALL_LAUNCHED);
            services.audit().launchedFireball(actor);
        });
        return Command.SINGLE_SUCCESS;
    }
}
