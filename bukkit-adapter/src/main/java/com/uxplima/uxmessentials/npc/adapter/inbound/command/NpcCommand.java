package com.uxplima.uxmessentials.npc.adapter.inbound.command;

import java.util.Collection;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.npc.adapter.NpcServices;
import com.uxplima.uxmessentials.npc.adapter.outbound.BukkitNpcSkins;
import com.uxplima.uxmessentials.npc.application.NearbyNpcs;
import com.uxplima.uxmessentials.npc.application.NpcMessageKey;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /npc <create|delete|list|movehere|command|lookatplayer|skin|type|equip|glow|pose|scale|action|data>}:
 * the single operator command for NPCs. The root keeps the lifecycle subcommands — {@code create} (reads the
 * operator's position and defaults the skin to their own Bukkit profile, or skinless when unavailable),
 * {@code delete}, {@code list}, {@code movehere} (reads the operator's position), {@code command} (the bound
 * click command), and {@code lookatplayer} (whether the NPC turns to face nearby players) — and attaches the skin
 * (name/player/url/texture), appearance (type/equip/glow/pose/scale), data (set/clear/list) and action
 * (add/list/remove/clear) sub-handlers under the one {@code /npc} literal, each contributing argument nodes
 * rather than a new literal. The base {@code uxmessentials.npc.admin} node guards the whole command.
 */
@NullMarked
public final class NpcCommand extends NpcCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.npc.admin";

    private final NpcSkinCommands skinCommands;
    private final NpcAppearanceCommands appearanceCommands;
    private final NpcDataCommands dataCommands;
    private final NpcActionCommands actionCommands;
    private final NpcStateCommands stateCommands;

    public NpcCommand(
            NpcServices services,
            Supplier<? extends Collection<String>> npcNames,
            NpcSkinByName skinByName,
            Messages messages) {
        super(services, npcNames, messages);
        this.skinCommands = new NpcSkinCommands(services, npcNames, skinByName, messages);
        this.appearanceCommands = new NpcAppearanceCommands(services, npcNames, messages);
        this.dataCommands = new NpcDataCommands(services, npcNames, messages);
        this.actionCommands = new NpcActionCommands(services, npcNames, messages);
        this.stateCommands = new NpcStateCommands(services, npcNames, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("npc")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(name("create", this::create))
                .then(name("delete", this::delete))
                .then(Commands.literal("list")
                        .executes(this::list)
                        .then(Commands.argument("type", StringArgumentType.word())
                                .executes(this::listFiltered)))
                .then(Commands.literal("help").executes(this::help))
                .then(Commands.literal("nearby")
                        .executes(ctx -> nearby(ctx, NearbyNpcs.DEFAULT_RADIUS))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, NearbyNpcs.MAX_RADIUS))
                                .executes(ctx -> nearby(ctx, ctx.getArgument("radius", Integer.class)))))
                .then(name("movehere", this::move))
                .then(name("info", this::info))
                .then(name("teleport", this::teleport))
                .then(Commands.literal("copy")
                        .then(nameArgument()
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .executes(this::copy))))
                .then(name("center", this::center))
                .then(name("fix", this::fix))
                .then(greedy("command", this::command))
                .then(bool("lookatplayer", this::lookAtPlayer))
                .then(skinCommands.node())
                .then(dataCommands.node())
                .then(actionCommands.node());
        for (LiteralArgumentBuilder<CommandSourceStack> appearance : appearanceCommands.nodes()) {
            root.then(appearance);
        }
        for (LiteralArgumentBuilder<CommandSourceStack> stateNode : stateCommands.nodes()) {
            root.then(stateNode);
        }
        return root.build();
    }

    @Override
    public String description() {
        return "Create and manage fake-player NPCs.";
    }

    private int create(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        NpcSkin skin = BukkitNpcSkins.of(sender).orElse(null);
        services.create().create(ref(sender), nameArg(ctx), position(sender), skin);
        return Command.SINGLE_SUCCESS;
    }

    private int delete(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.delete().delete(ref(sender), nameArg(ctx));
        return Command.SINGLE_SUCCESS;
    }

    private int list(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.list().list(ref(sender));
        return Command.SINGLE_SUCCESS;
    }

    private int listFiltered(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.list().list(ref(sender), ctx.getArgument("type", String.class));
        return Command.SINGLE_SUCCESS;
    }

    private int help(CommandContext<CommandSourceStack> ctx) {
        feedback.send(ctx.getSource().getSender(), NpcMessageKey.NPC_HELP, java.util.Map.of());
        return Command.SINGLE_SUCCESS;
    }

    private int nearby(CommandContext<CommandSourceStack> ctx, int radius) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.nearby().nearby(ref(sender), position(sender), radius);
        return Command.SINGLE_SUCCESS;
    }

    private int move(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.move().move(ref(sender), nameArg(ctx), position(sender));
        return Command.SINGLE_SUCCESS;
    }

    private int info(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.info().describe(ref(sender), nameArg(ctx));
        return Command.SINGLE_SUCCESS;
    }

    private int teleport(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.teleport().teleport(ref(sender), nameArg(ctx));
        return Command.SINGLE_SUCCESS;
    }

    private int copy(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        NpcName target = NpcName.of(ctx.getArgument("target", String.class));
        services.copy().copy(ref(sender), nameArg(ctx), target, position(sender));
        return Command.SINGLE_SUCCESS;
    }

    private int center(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.center().center(ref(sender), nameArg(ctx));
        return Command.SINGLE_SUCCESS;
    }

    private int fix(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.fix().fix(ref(sender), nameArg(ctx));
        return Command.SINGLE_SUCCESS;
    }

    private int command(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.command().setCommand(ref(sender), nameArg(ctx), value(ctx));
        return Command.SINGLE_SUCCESS;
    }

    private int lookAtPlayer(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.look().setLookAtPlayer(ref(sender), nameArg(ctx), ctx.getArgument("value", Boolean.class));
        return Command.SINGLE_SUCCESS;
    }
}
