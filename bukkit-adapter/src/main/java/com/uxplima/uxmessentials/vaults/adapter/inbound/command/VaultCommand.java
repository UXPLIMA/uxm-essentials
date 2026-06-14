package com.uxplima.uxmessentials.vaults.adapter.inbound.command;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.vaults.adapter.VaultServices;
import com.uxplima.uxmessentials.vaults.application.VaultNotifier;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultError;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /vault}, {@code /vault <n>}, {@code /vault <player> [n]}, {@code /vault delete <n>}, and
 * {@code /vault delete <player> <n>} (docs/10-feature-modules.md §15.11). The no-argument form opens the
 * player's default vault when they own one (or allocates the first within quota) and lists their vault numbers
 * when several exist; {@code <n>} opens the Nth; {@code <player> [n]} is the audit-logged staff inspect
 * override, gated by {@code uxmessentials.vault.others} on that branch so a non-staff player never sees it.
 * {@code delete <n>} removes the caller's own vault (refunding the configured amount when economy is on);
 * {@code delete <player> <n>} is the audited staff override, gated by {@code uxmessentials.vault.admin.delete},
 * and pays no refund. Deletion is direct — like PlayerVaultsX it does not prompt for confirmation — and always
 * audited, so a destructive staff override is replayable from the audit channel.
 *
 * <p>This handler maps the Bukkit source to the kernel value objects and hands off to the use cases. The GUI
 * open is entity-bound, so it is scheduled on the viewer's region thread through the kernel {@code Scheduler};
 * the delete touches the database, so it runs off the tick thread through {@link Scheduler#async}. The admin
 * inspect override targets an online player (the GUI opens on their entity); the admin delete accepts an
 * offline owner by name, since deleting a vault opens no window.
 */
@NullMarked
public final class VaultCommand implements CommandRegistration {

    private static final String USE = "uxmessentials.vault.use";
    private static final String OTHERS = "uxmessentials.vault.others";
    private static final String ADMIN_DELETE = "uxmessentials.vault.admin.delete";
    private static final int DEFAULT_INDEX = 1;

    private final VaultServices services;
    private final VaultNotifier notifier;
    private final Scheduler scheduler;

    public VaultCommand(VaultServices services) {
        this.services = Objects.requireNonNull(services, "services");
        this.notifier = services.notifier();
        this.scheduler = services.kernel().scheduler();
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("vault")
                .requires(src -> src.getSender().hasPermission(USE))
                .executes(this::openDefaultOrList)
                .then(Commands.literal("info").executes(this::info))
                .then(Commands.literal("delete")
                        .then(Commands.argument("n", IntegerArgumentType.integer(1))
                                .executes(ctx -> deleteOwn(ctx, ctx.getArgument("n", Integer.class))))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .requires(src -> src.getSender().hasPermission(ADMIN_DELETE))
                                .suggests(onlinePlayerSuggestions())
                                .then(Commands.argument("idx", IntegerArgumentType.integer(1))
                                        .executes(ctx -> deleteOther(ctx, ctx.getArgument("idx", Integer.class))))))
                .then(Commands.argument("n", IntegerArgumentType.integer(1))
                        .executes(ctx -> openOwn(ctx, ctx.getArgument("n", Integer.class))))
                .then(Commands.argument("player", ArgumentTypes.player())
                        .requires(src -> src.getSender().hasPermission(OTHERS))
                        .executes(ctx -> openOther(ctx, DEFAULT_INDEX))
                        .then(Commands.argument("idx", IntegerArgumentType.integer(1))
                                .executes(ctx -> openOther(ctx, ctx.getArgument("idx", Integer.class)))))
                .build();
    }

    @Override
    public String description() {
        return "Open one of your vaults, delete a vault, or audit another player's vault.";
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
        Optional<Player> resolved = resolveTarget(ctx, actor);
        if (resolved.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef owner = BukkitRefs.toRef(resolved.get());
        Vault vault = services.openAdminVault().open(actor, owner, index);
        openWindow(staff, actor, owner, vault);
        notifier.adminOpened(actor, owner, index);
        return Command.SINGLE_SUCCESS;
    }

    private int deleteOwn(CommandContext<CommandSourceStack> ctx, int index) {
        Player player = playerOrReject(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef owner = BukkitRefs.toRef(player);
        // A DB write — run it off the tick thread; the use case notifies the player of the outcome itself.
        scheduler.async(() -> services.deleteVault().delete(owner, index));
        return Command.SINGLE_SUCCESS;
    }

    private int deleteOther(CommandContext<CommandSourceStack> ctx, int index) {
        Player staff = playerOrReject(ctx);
        if (staff == null) {
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef actor = BukkitRefs.toRef(staff);
        String typed = StringArgumentType.getString(ctx, "player");
        Optional<PlayerRef> owner = services.kernel().playerLookup().findByName(typed);
        if (owner.isEmpty()) {
            notifier.unknownTarget(actor, typed);
            return Command.SINGLE_SUCCESS;
        }
        // A DB write — run it off the tick thread; the use case audits the override and notifies the actor.
        scheduler.async(() -> services.deleteVault().deleteOther(actor, owner.get(), index));
        return Command.SINGLE_SUCCESS;
    }

    private Optional<Player> resolveTarget(CommandContext<CommandSourceStack> ctx, PlayerRef actor) {
        try {
            PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
            List<Player> matched = resolver.resolve(ctx.getSource());
            if (matched.isEmpty()) {
                notifier.unknownTarget(actor, typedTarget(ctx));
                return Optional.empty();
            }
            return Optional.of(matched.get(0));
        } catch (CommandSyntaxException unmatched) {
            notifier.unknownTarget(actor, typedTarget(ctx));
            return Optional.empty();
        }
    }

    private static String typedTarget(CommandContext<CommandSourceStack> ctx) {
        return ctx.getNodes().stream()
                .filter(node -> "player".equals(node.getNode().getName()))
                .findFirst()
                .map(node -> node.getRange().get(ctx.getInput()))
                .orElse("");
    }

    private static SuggestionProvider<CommandSourceStack> onlinePlayerSuggestions() {
        return (ctx, builder) -> {
            String prefix = builder.getRemainingLowerCase();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    builder.suggest(online.getName());
                }
            }
            return builder.buildFuture();
        };
    }

    private void openWindow(Player player, PlayerRef viewer, PlayerRef owner, Vault vault) {
        scheduler.onEntity(viewer, () -> {
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
            case CANNOT_AFFORD -> notifier.cannotAfford(
                    viewer, services.chargeSettings().costToCreate().toPlainString());
            case DELETE_UNKNOWN -> notifier.deleteUnknown(viewer, index);
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
