package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.Map;
import java.util.Optional;

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
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /kill [player]}: kill a target — the named player, or the invoking player when no name is given. An
 * abusable verb (audit-logged): killing another player is a moderation-adjacent action, so it is recorded with
 * actor and target. The named target must resolve online (else {@link ItemworldMessageKey#UNKNOWN_TARGET}).
 *
 * <p>Setting health is entity-bound, so it runs on the victim's region thread through the kernel
 * {@code Scheduler}; the kill is reported through {@link ItemworldMessageKey#KILL_DONE} and audited.
 */
@NullMarked
public final class KillCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.kill.use";

    public KillCommand(ItemworldServices services) {
        super(services, "kill", SubFeatureGroup.MOB_ENTITY, "Kill a target.");
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

    private int run(CommandContext<CommandSourceStack> ctx, Optional<String> name) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Player self = player(ctx);
        if (self == null) {
            return Command.SINGLE_SUCCESS;
        }
        Player victim = name.map(n -> self.getServer().getPlayerExact(n)).orElse(self);
        if (victim == null) {
            reply(ctx, ItemworldMessageKey.UNKNOWN_TARGET, Map.of("player", name.orElse(self.getName())));
            return Command.SINGLE_SUCCESS;
        }
        kill(ctx, self, victim);
        return Command.SINGLE_SUCCESS;
    }

    private void kill(CommandContext<CommandSourceStack> ctx, Player actor, Player victim) {
        PlayerRef actorRef = ref(actor);
        String targetName = victim.getName();
        services.kernel().scheduler().onEntity(ref(victim), () -> {
            victim.setHealth(0.0);
            reply(ctx, ItemworldMessageKey.KILL_DONE, Map.of("target", targetName));
            services.audit().killed(actorRef, targetName);
        });
    }
}
