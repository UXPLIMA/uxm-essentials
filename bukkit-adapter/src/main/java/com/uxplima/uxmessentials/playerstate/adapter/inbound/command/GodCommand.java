package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.playerstate.application.PlayerstateMessageKey;
import com.uxplima.uxmessentials.playerstate.application.port.PlayerStateStore;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.PlayerTargets;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.command.Sender;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /god [player]} ({@code uxmessentials.god.use}): toggle damage immunity for yourself, or for another
 * player when the {@code uxmessentials.god.others} (or the cross-cutting {@code uxmessentials.playerstate.others}) node is held. The {@code ToggleGod} use case owns
 * the snapshot mutation, the reconciliation, the event, and the feedback; this handler maps the arguments and
 * resolves the optional target.
 */
@NullMarked
public final class GodCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.god.use";
    private static final String GUARD_BYPASS = "uxmessentials.guard.bypass";

    private final PlayerStateStore store;
    private final RegionBehaviourGate regions;

    public GodCommand(
            PlayerStateServices services, Messages messages, PlayerStateStore store, RegionBehaviourGate regions) {
        super(services, messages);
        this.store = Objects.requireNonNull(store, "store");
        this.regions = Objects.requireNonNull(regions, "regions");
    }

    /** Targeting somebody else takes this node, or the cross-cutting playerstate one. */
    @Override
    String othersNode() {
        return "uxmessentials.god.others";
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("god")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::toggle)
                .then(PlayerTargets.players("player").executes(this::toggle))
                .build();
    }

    @Override
    public String description() {
        return "Toggle damage immunity.";
    }

    private int toggle(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = Sender.audience(ctx.getSource());
        List<PlayerRef> targets = resolveTargets(ctx, sender);
        if (targets.isEmpty()) {
            return 0;
        }
        for (PlayerRef target : targets) {
            // A guard region with god = deny (an arena) refuses turning god mode on; turning it off is always fine.
            Player live = Bukkit.getPlayer(target.uuid());
            if (live != null
                    && !store.current(target).god()
                    && !live.hasPermission(GUARD_BYPASS)
                    && regions.denies(live, "god")) {
                feedback.send(sender, PlayerstateMessageKey.GOD_REGION_DISABLED, Map.of());
                continue;
            }
            services.toggleGod().toggleFor(actor(ctx), target);
        }
        return Command.SINGLE_SUCCESS;
    }
}
