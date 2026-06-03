package com.uxplima.uxmessentials.vaults.adapter.inbound.command;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.vaults.adapter.VaultServices;
import com.uxplima.uxmessentials.vaults.application.VaultNotifier;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultError;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /vault}, {@code /vault <n>}, {@code /vault <player> [n]} (docs/10-feature-modules.md §15.11). The
 * no-argument form opens the player's default vault when they own one (or allocate the first within quota) and
 * lists their vault numbers when several exist; {@code <n>} opens the Nth; {@code <player> [n]} is the
 * audit-logged staff override, gated by {@code uxmessentials.vault.others} on that branch so a non-staff player
 * never sees it. The owner branch is gated by {@code uxmessentials.vault.use} on the root.
 *
 * <p>This handler maps the Bukkit source to the kernel value objects and hands off to the use cases; the GUI
 * open is entity-bound, so it is scheduled on the viewer's region thread through the kernel {@code Scheduler}.
 * Resolving the vault (a DB read) and opening the window are both done in that scheduled step, after the use
 * case has produced the {@link Vault}.
 */
@NullMarked
public final class VaultCommand implements CommandRegistration {

    private static final String USE = "uxmessentials.vault.use";
    private static final String OTHERS = "uxmessentials.vault.others";
    private static final int DEFAULT_INDEX = 1;

    private final VaultServices services;
    private final VaultNotifier notifier;
    private final PlayerLookup players;

    public VaultCommand(VaultServices services) {
        this.services = Objects.requireNonNull(services, "services");
        this.notifier = services.notifier();
        this.players = services.kernel().playerLookup();
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("vault")
                .requires(src -> src.getSender().hasPermission(USE))
                .executes(this::openDefaultOrList)
                .then(Commands.literal("info").executes(this::info))
                .then(Commands.argument("n", IntegerArgumentType.integer(1))
                        .executes(ctx -> openOwn(ctx, ctx.getArgument("n", Integer.class))))
                .then(Commands.argument("player", StringArgumentType.word())
                        .requires(src -> src.getSender().hasPermission(OTHERS))
                        .executes(ctx -> openOther(ctx, DEFAULT_INDEX))
                        .then(Commands.argument("idx", IntegerArgumentType.integer(1))
                                .executes(ctx -> openOther(ctx, ctx.getArgument("idx", Integer.class)))))
                .build();
    }

    @Override
    public String description() {
        return "Open one of your vaults, or audit another player's vault.";
    }

    private int openDefaultOrList(CommandContext<CommandSourceStack> ctx) {
        Player player = playerOrReject(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef viewer = BukkitRefs.toRef(player);
        List<Integer> owned = services.listVaults().list(viewer).asValue().orElse(List.of());
        if (owned.size() > 1) {
            notifier.list(viewer, owned);
            return Command.SINGLE_SUCCESS;
        }
        return openOwn(ctx, owned.isEmpty() ? DEFAULT_INDEX : owned.get(0));
    }

    private int info(CommandContext<CommandSourceStack> ctx) {
        Player player = playerOrReject(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef viewer = BukkitRefs.toRef(player);
        List<Integer> owned = services.listVaults().list(viewer).asValue().orElse(List.of());
        notifier.showInfo(
                viewer,
                owned.size(),
                services.amountQuota().resolve(viewer),
                services.sizeQuota().resolve(viewer));
        return Command.SINGLE_SUCCESS;
    }

    private int openOwn(CommandContext<CommandSourceStack> ctx, int index) {
        Player player = playerOrReject(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef viewer = BukkitRefs.toRef(player);
        Result<Vault, VaultError> resolved = services.openVault().open(viewer, index);
        if (resolved.isErr()) {
            rejectOwn(viewer, index, resolved.errorOrThrow());
            return Command.SINGLE_SUCCESS;
        }
        openWindow(player, viewer, viewer, resolved.orElseThrow());
        return Command.SINGLE_SUCCESS;
    }

    private int openOther(CommandContext<CommandSourceStack> ctx, int index) {
        Player staff = playerOrReject(ctx);
        if (staff == null) {
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef actor = BukkitRefs.toRef(staff);
        String name = ctx.getArgument("player", String.class);
        Optional<PlayerRef> owner = players.findOnlineByName(name);
        if (owner.isEmpty()) {
            notifier.unknownTarget(actor, name);
            return Command.SINGLE_SUCCESS;
        }
        Vault vault = services.openAdminVault().open(actor, owner.get(), index);
        openWindow(staff, actor, owner.get(), vault);
        notifier.adminOpened(actor, owner.get(), index);
        return Command.SINGLE_SUCCESS;
    }

    private void openWindow(Player player, PlayerRef viewer, PlayerRef owner, Vault vault) {
        services.kernel().scheduler().onEntity(viewer, () -> {
            services.view().open(player, viewer, owner, vault);
            if (viewer.uuid().equals(owner.uuid())) {
                notifier.opened(viewer, vault.index());
            }
        });
    }

    private void rejectOwn(PlayerRef viewer, int index, VaultError error) {
        switch (error) {
            case AMOUNT_EXCEEDED -> notifier.amountExceeded(viewer, index);
            case NONE_OWNED -> notifier.noneOwned(viewer);
        }
    }

    private @Nullable Player playerOrReject(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (sender instanceof Player player) {
            return player;
        }
        notifier.playersOnly(new PlayerRef(new java.util.UUID(0L, 0L), sender.getName()));
        return null;
    }
}
