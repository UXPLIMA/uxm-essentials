package com.uxplima.uxmessentials.regions.adapter.inbound.command;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import org.bukkit.Location;
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
import com.uxplima.uxmessentials.regions.adapter.inbound.gui.RegionFlagEditorView;
import com.uxplima.uxmessentials.regions.adapter.inbound.gui.RegionListView;
import com.uxplima.uxmessentials.regions.application.RegionsMessageKey;
import com.uxplima.uxmessentials.regions.application.port.RegionService;
import com.uxplima.uxmessentials.regions.domain.RegionRef;
import com.uxplima.uxmessentials.regions.domain.RegionServiceException;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /regions} — the WorldGuard region management surface. The base command and each subcommand degrade to a
 * "WorldGuard not installed" line when the bound {@link RegionService} is the no-op fallback (WorldGuard absent), so
 * the whole surface stays inert on a server without WorldGuard.
 *
 * <ul>
 *   <li>{@code /regions [world]} ({@code uxmessentials.regions.list}) — open the region list for a world.
 *   <li>{@code /regions pos1|pos2} / {@code /regions create <id>} ({@code uxmessentials.regions.create}) — mark the
 *       two corners of a manual selection and define a cuboid region from the WorldEdit selection or those corners.
 *   <li>{@code /regions flags <id>} ({@code uxmessentials.regions.flags}) — open the flag editor for a region.
 * </ul>
 *
 * <p>Every WorldGuard read and every mutation runs on the global region thread through the injected {@link Scheduler}
 * (never a viewer's region thread); the command validates its inputs and hops off the tick thread, and the region set
 * / roster stays out of the command body.
 */
@NullMarked
public final class RegionsCommand implements CommandRegistration {

    private static final String LIST_PERMISSION = "uxmessentials.regions.list";
    private static final String CREATE_PERMISSION = "uxmessentials.regions.create";
    private static final String FLAGS_PERMISSION = "uxmessentials.regions.flags";

    private final RegionService service;
    private final RegionListView listView;
    private final RegionFlagEditorView flagEditor;
    private final RegionSelection selection;
    private final Scheduler scheduler;
    private final Server server;
    private final CommandFeedback feedback;

    public RegionsCommand(
            RegionService service,
            RegionListView listView,
            RegionFlagEditorView flagEditor,
            RegionSelection selection,
            Scheduler scheduler,
            Server server,
            Messages messages) {
        this.service = Objects.requireNonNull(service, "service");
        this.listView = Objects.requireNonNull(listView, "listView");
        this.flagEditor = Objects.requireNonNull(flagEditor, "flagEditor");
        this.selection = Objects.requireNonNull(selection, "selection");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.server = Objects.requireNonNull(server, "server");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("regions")
                .requires(p(LIST_PERMISSION))
                .executes(this::openCurrentWorld)
                .then(Commands.literal("pos1")
                        .requires(p(CREATE_PERMISSION))
                        .executes(ctx -> markCorner(ctx, RegionSelection.Corner.FIRST)))
                .then(Commands.literal("pos2")
                        .requires(p(CREATE_PERMISSION))
                        .executes(ctx -> markCorner(ctx, RegionSelection.Corner.SECOND)))
                .then(Commands.literal("create")
                        .requires(p(CREATE_PERMISSION))
                        .then(Commands.argument("id", StringArgumentType.word()).executes(this::create)))
                .then(Commands.literal("flags")
                        .requires(p(FLAGS_PERMISSION))
                        .then(Commands.argument("id", StringArgumentType.word()).executes(this::openFlags)))
                .then(Commands.argument("world", StringArgumentType.word())
                        .suggests(CommandSuggestions.fromStrings(() ->
                                server.getWorlds().stream().map(World::getName).toList()))
                        .executes(this::openNamedWorld))
                .build();
    }

    @Override
    public String description() {
        return "Manage WorldGuard regions: list, create, and edit flags.";
    }

    private int openCurrentWorld(CommandContext<CommandSourceStack> ctx) {
        Player staff = playerOrReject(ctx);
        if (staff == null) {
            return 0;
        }
        return openList(staff, staff.getWorld());
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
        return openList(staff, world);
    }

    /** Gate on WorldGuard being present, then hand the world to the list view. */
    private int openList(Player staff, World world) {
        if (!service.available()) {
            feedback.send(staff, RegionsMessageKey.REGIONS_NO_WORLDGUARD);
            return Command.SINGLE_SUCCESS;
        }
        listView.open(BukkitRefs.toRef(staff), BukkitRefs.toRef(world));
        return Command.SINGLE_SUCCESS;
    }

    private int markCorner(CommandContext<CommandSourceStack> ctx, RegionSelection.Corner corner) {
        Player staff = playerOrReject(ctx);
        if (staff == null) {
            return 0;
        }
        selection.mark(staff, corner);
        Location at = Objects.requireNonNull(staff.getLocation(), "location");
        feedback.send(
                staff,
                RegionsMessageKey.REGIONS_POS_SET,
                Map.of(
                        "corner", Integer.toString(corner == RegionSelection.Corner.FIRST ? 1 : 2),
                        "x", Integer.toString(at.getBlockX()),
                        "y", Integer.toString(at.getBlockY()),
                        "z", Integer.toString(at.getBlockZ())));
        return Command.SINGLE_SUCCESS;
    }

    private int create(CommandContext<CommandSourceStack> ctx) {
        Player staff = playerOrReject(ctx);
        if (staff == null) {
            return 0;
        }
        if (!service.available()) {
            feedback.send(staff, RegionsMessageKey.REGIONS_NO_WORLDGUARD);
            return Command.SINGLE_SUCCESS;
        }
        Optional<RegionBounds> bounds = selection.boundsFor(staff);
        if (bounds.isEmpty()) {
            feedback.send(staff, RegionsMessageKey.REGIONS_CREATE_NO_SELECTION);
            return Command.SINGLE_SUCCESS;
        }
        String id = StringArgumentType.getString(ctx, "id").toLowerCase(Locale.ROOT);
        WorldRef world = BukkitRefs.toRef(staff.getWorld());
        scheduler.onGlobal(() -> createOnGlobal(staff, world, id, bounds.get()));
        return Command.SINGLE_SUCCESS;
    }

    /** On the global region thread: reject a duplicate id, else create the region and report the outcome. */
    private void createOnGlobal(Player staff, WorldRef world, String id, RegionBounds bounds) {
        PlayerRef ref = BukkitRefs.toRef(staff);
        if (service.region(world, id).isPresent()) {
            scheduler.onEntity(
                    ref, () -> feedback.send(staff, RegionsMessageKey.REGIONS_CREATE_DUPLICATE, Map.of("id", id)));
            return;
        }
        try {
            service.create(world, id, bounds.min(), bounds.max());
            scheduler.onEntity(
                    ref, () -> feedback.send(staff, RegionsMessageKey.REGIONS_CREATE_DONE, Map.of("id", id)));
        } catch (RegionServiceException failure) {
            scheduler.onEntity(
                    ref, () -> feedback.send(staff, RegionsMessageKey.REGIONS_CREATE_FAILED, Map.of("id", id)));
        }
    }

    private int openFlags(CommandContext<CommandSourceStack> ctx) {
        Player staff = playerOrReject(ctx);
        if (staff == null) {
            return 0;
        }
        if (!service.available()) {
            feedback.send(staff, RegionsMessageKey.REGIONS_NO_WORLDGUARD);
            return Command.SINGLE_SUCCESS;
        }
        String id = StringArgumentType.getString(ctx, "id").toLowerCase(Locale.ROOT);
        WorldRef world = BukkitRefs.toRef(staff.getWorld());
        scheduler.onGlobal(() -> openFlagsOnGlobal(staff, world, id));
        return Command.SINGLE_SUCCESS;
    }

    /** On the global region thread: resolve the region, then either open its flag editor or refuse an unknown id. */
    private void openFlagsOnGlobal(Player staff, WorldRef world, String id) {
        PlayerRef ref = BukkitRefs.toRef(staff);
        Optional<RegionRef> region = service.region(world, id);
        if (region.isEmpty()) {
            scheduler.onEntity(
                    ref, () -> feedback.send(staff, RegionsMessageKey.REGIONS_UNKNOWN_REGION, Map.of("id", id)));
            return;
        }
        flagEditor.open(ref, region.get());
    }

    private @Nullable Player playerOrReject(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (sender instanceof Player player) {
            return player;
        }
        feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY);
        return null;
    }

    private static Predicate<CommandSourceStack> p(String permission) {
        return src -> src.getSender().hasPermission(permission);
    }
}
