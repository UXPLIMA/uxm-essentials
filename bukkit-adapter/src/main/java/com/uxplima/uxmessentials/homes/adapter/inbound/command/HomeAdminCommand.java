package com.uxplima.uxmessentials.homes.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.homes.adapter.HomeServices;
import com.uxplima.uxmessentials.homes.application.HomesMessageKey;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /homeadmin <player> list | del <slot> | tp <slot> | set [slot] | clear | info <slot>}:
 * full admin management over another player's homes as an explicit, audit-logged verb. The
 * {@link com.uxplima.uxmessentials.homes.application.HomeAdmin} use case reuses the same aggregate
 * transitions the player-facing grid does; this handler resolves the target player and dispatches the
 * verb.
 *
 * <p>Target resolution uses the offline-capable {@code findByName} so list, del, clear, info, and set
 * all work even when the owner is offline. Only {@code tp} requires the actor to be online (the actor
 * teleports). Repository I/O in set, clear, and info runs off the tick thread via the injected
 * {@link Scheduler}; {@code set} captures the actor's current position on the entity thread first.
 */
@NullMarked
public final class HomeAdminCommand extends HomeCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.home.admin";

    private final Scheduler scheduler;

    public HomeAdminCommand(HomeServices services, Messages messages, Scheduler scheduler) {
        super(services, messages);
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("homeadmin")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(CommandSuggestions.playerArgument("player")
                        .then(Commands.literal("list").executes(this::runList))
                        .then(withSlot(Commands.literal("del"), this::runDelete))
                        .then(withSlot(Commands.literal("tp"), this::runTeleport))
                        .then(withOptionalSlot(Commands.literal("set"), this::runSetWithSlot, this::runSetDefaultSlot))
                        .then(Commands.literal("clear").executes(this::runClear))
                        .then(withSlot(Commands.literal("info"), this::runInfo)))
                .build();
    }

    @Override
    public String description() {
        return "Manage another player's homes.";
    }

    private LiteralArgumentBuilder<CommandSourceStack> withSlot(
            LiteralArgumentBuilder<CommandSourceStack> verb, Command<CommandSourceStack> action) {
        RequiredArgumentBuilder<CommandSourceStack, Integer> slot =
                Commands.argument("slot", IntegerArgumentType.integer(1)).executes(action);
        return verb.then(slot);
    }

    /**
     * Builds a verb node that accepts an optional {@code <slot>} argument: with the slot argument it
     * executes {@code withSlot}, without it executes {@code withoutSlot}.
     */
    private LiteralArgumentBuilder<CommandSourceStack> withOptionalSlot(
            LiteralArgumentBuilder<CommandSourceStack> verb,
            Command<CommandSourceStack> withSlot,
            Command<CommandSourceStack> withoutSlot) {
        RequiredArgumentBuilder<CommandSourceStack, Integer> slot =
                Commands.argument("slot", IntegerArgumentType.integer(1)).executes(withSlot);
        return verb.executes(withoutSlot).then(slot);
    }

    private int runList(CommandContext<CommandSourceStack> ctx) {
        return dispatchOfflineTarget(ctx, target -> services.homeAdmin().list(refOf(ctx), target));
    }

    private int runDelete(CommandContext<CommandSourceStack> ctx) {
        return dispatchOfflineTarget(ctx, target -> services.homeAdmin().delete(refOf(ctx), target, slotArg(ctx)));
    }

    private int runTeleport(CommandContext<CommandSourceStack> ctx) {
        // tp requires the actor to be online — the actor teleports.
        return dispatchOnlineTarget(ctx, target -> services.homeAdmin().teleport(refOf(ctx), target, slotArg(ctx)));
    }

    private int runSetWithSlot(CommandContext<CommandSourceStack> ctx) {
        return runSet(ctx, slotArg(ctx));
    }

    /**
     * {@code set} without an explicit slot: load the target's set off-thread and use the first free
     * slot (index 0 when the set is empty, otherwise one past the highest occupied index). Because the
     * load itself is async, the slot is computed inside the async block after the load.
     */
    private int runSetDefaultSlot(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        // Capture position on the entity thread before hopping off.
        Position at = BukkitRefs.toPosition(Objects.requireNonNull(sender.getLocation(), "location"));
        PlayerRef actor = ref(sender);
        String name = ctx.getArgument("player", String.class);
        Optional<PlayerRef> resolved = services.players().findByName(name);
        if (resolved.isEmpty()) {
            feedback.send(sender, HomesMessageKey.HOME_ADMIN_TARGET_UNKNOWN, Map.of("player", name));
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef target = resolved.get();
        scheduler.async(() -> {
            HomeSet set = services.repository().load(target);
            // First slot index not occupied by the target — 0 when empty, otherwise max+1.
            java.util.OptionalInt maxOpt =
                    set.all().stream().mapToInt(h -> h.slot().index()).max();
            int nextIndex = maxOpt.isPresent() ? maxOpt.getAsInt() + 1 : 0;
            HomeSlot slot = HomeSlot.of(nextIndex);
            services.homeAdmin().setHome(actor, target, slot, at);
        });
        return Command.SINGLE_SUCCESS;
    }

    private int runSet(CommandContext<CommandSourceStack> ctx, HomeSlot slot) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        // Capture position on the entity thread before hopping off.
        Position at = BukkitRefs.toPosition(Objects.requireNonNull(sender.getLocation(), "location"));
        PlayerRef actor = ref(sender);
        String name = ctx.getArgument("player", String.class);
        Optional<PlayerRef> resolved = services.players().findByName(name);
        if (resolved.isEmpty()) {
            feedback.send(sender, HomesMessageKey.HOME_ADMIN_TARGET_UNKNOWN, Map.of("player", name));
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef target = resolved.get();
        scheduler.async(() -> services.homeAdmin().setHome(actor, target, slot, at));
        return Command.SINGLE_SUCCESS;
    }

    private int runClear(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef actor = ref(sender);
        String name = ctx.getArgument("player", String.class);
        Optional<PlayerRef> resolved = services.players().findByName(name);
        if (resolved.isEmpty()) {
            feedback.send(sender, HomesMessageKey.HOME_ADMIN_TARGET_UNKNOWN, Map.of("player", name));
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef target = resolved.get();
        scheduler.async(() -> services.homeAdmin().clearAll(actor, target));
        return Command.SINGLE_SUCCESS;
    }

    private int runInfo(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef actor = ref(sender);
        HomeSlot slot = slotArg(ctx);
        String name = ctx.getArgument("player", String.class);
        Optional<PlayerRef> resolved = services.players().findByName(name);
        if (resolved.isEmpty()) {
            feedback.send(sender, HomesMessageKey.HOME_ADMIN_TARGET_UNKNOWN, Map.of("player", name));
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef target = resolved.get();
        scheduler.async(() -> services.homeAdmin().info(actor, target, slot));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Resolve target via {@link com.uxplima.uxmessentials.shared.application.port.PlayerLookup#findByName}
     * (offline-capable) then execute {@code verb} on the async thread. Used by all verbs that do not need
     * the sender to be online at the destination.
     */
    private int dispatchOfflineTarget(
            CommandContext<CommandSourceStack> ctx, java.util.function.Consumer<PlayerRef> verb) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String name = ctx.getArgument("player", String.class);
        Optional<PlayerRef> resolved = services.players().findByName(name);
        if (resolved.isEmpty()) {
            feedback.send(sender, HomesMessageKey.HOME_ADMIN_TARGET_UNKNOWN, Map.of("player", name));
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef target = resolved.get();
        scheduler.async(() -> verb.accept(target));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Resolve target online-only (for verbs like {@code tp} where the actor is the one moving).
     */
    private int dispatchOnlineTarget(
            CommandContext<CommandSourceStack> ctx, java.util.function.Consumer<PlayerRef> verb) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String name = ctx.getArgument("player", String.class);
        Optional<PlayerRef> target = services.players().findOnlineByName(name);
        if (target.isEmpty()) {
            feedback.send(sender, HomesMessageKey.HOME_ADMIN_TARGET_UNKNOWN, Map.of("player", name));
            return Command.SINGLE_SUCCESS;
        }
        scheduler.async(() -> verb.accept(target.get()));
        return Command.SINGLE_SUCCESS;
    }

    private PlayerRef refOf(CommandContext<CommandSourceStack> ctx) {
        return ref((Player) ctx.getSource().getSender());
    }

    private static HomeSlot slotArg(CommandContext<CommandSourceStack> ctx) {
        // The admin types the 1-based number /homeadmin list prints; the aggregate keys on the zero-based slot.
        return HomeSlot.of(ctx.getArgument("slot", Integer.class) - 1);
    }
}
