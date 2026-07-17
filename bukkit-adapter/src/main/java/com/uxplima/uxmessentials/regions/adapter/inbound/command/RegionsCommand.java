package com.uxplima.uxmessentials.regions.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;

import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.regions.adapter.inbound.gui.RegionListView;
import com.uxplima.uxmessentials.regions.application.RegionsMessageKey;
import com.uxplima.uxmessentials.regions.application.port.RegionService;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /regions [world]} ({@code uxmessentials.regions.list}): opens the WorldGuard region-list GUI for the named
 * world, or the player's current world when no argument is given. The whole command is gated on the list node.
 *
 * <p>Because WorldGuard is a soft dependency, the command first asks the {@link RegionService} whether it is
 * reachable: when the bound service is the no-op fallback (WorldGuard absent) it answers the "WorldGuard not
 * installed" line and opens nothing, so the surface stays inert on a server without WorldGuard. Resolving the region
 * set stays off the tick thread (the {@link RegionListView} reads the region store on the global region thread), so
 * the command itself only validates its inputs and delegates.
 */
@NullMarked
public final class RegionsCommand implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.regions.list";

    private final RegionService service;
    private final RegionListView listView;
    private final Server server;
    private final CommandFeedback feedback;

    public RegionsCommand(RegionService service, RegionListView listView, Server server, Messages messages) {
        this.service = Objects.requireNonNull(service, "service");
        this.listView = Objects.requireNonNull(listView, "listView");
        this.server = Objects.requireNonNull(server, "server");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("regions")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::openCurrentWorld)
                .then(Commands.argument("world", StringArgumentType.word())
                        .suggests(CommandSuggestions.fromStrings(() ->
                                server.getWorlds().stream().map(World::getName).toList()))
                        .executes(this::openNamedWorld))
                .build();
    }

    @Override
    public String description() {
        return "Open the WorldGuard region-list GUI for a world.";
    }

    private int openCurrentWorld(CommandContext<CommandSourceStack> ctx) {
        Player staff = playerOrReject(ctx);
        if (staff == null) {
            return 0;
        }
        return open(staff, staff.getWorld());
    }

    private int openNamedWorld(CommandContext<CommandSourceStack> ctx) {
        Player staff = playerOrReject(ctx);
        if (staff == null) {
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "world");
        World world = server.getWorld(name);
        if (world == null) {
            feedback.send(staff, RegionsMessageKey.REGIONS_UNKNOWN_WORLD, Map.of("world", name));
            return Command.SINGLE_SUCCESS;
        }
        return open(staff, world);
    }

    /** The shared body: gate on WorldGuard being present, then hand the world to the list view. */
    private int open(Player staff, World world) {
        if (!service.available()) {
            feedback.send(staff, RegionsMessageKey.REGIONS_NO_WORLDGUARD);
            return Command.SINGLE_SUCCESS;
        }
        listView.open(BukkitRefs.toRef(staff), BukkitRefs.toRef(world));
        return Command.SINGLE_SUCCESS;
    }

    private @Nullable Player playerOrReject(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (sender instanceof Player player) {
            return player;
        }
        feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY);
        return null;
    }
}
