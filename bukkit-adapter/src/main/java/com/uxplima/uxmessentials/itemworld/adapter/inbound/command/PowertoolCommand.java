package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.Map;
import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.adapter.outbound.PdcPowertoolStore;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.application.PowertoolPolicy;
import com.uxplima.uxmessentials.itemworld.domain.ItemWorldError;
import com.uxplima.uxmessentials.itemworld.domain.PowertoolBinding;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.domain.Result;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /powertool <command...>} (alias {@code /pt}): bind a command to the held item so a later interact runs
 * it. With no command on a bound item the binding is cleared (the standard powertool semantics). The held item
 * is identified by its normalised {@code namespace:path} id; the validated {@link PowertoolBinding} is shaped by
 * the domain {@link PowertoolPolicy} and stamped onto the item PDC by {@link PdcPowertoolStore}, so the binding
 * travels with the item and the module keeps no persistence.
 *
 * <p>The command text is validated through the policy before any write: an empty hand replies
 * {@link ItemworldMessageKey#POWERTOOL_NO_ITEM}; a blank command line clears the binding and replies
 * {@link ItemworldMessageKey#POWERTOOL_CLEARED}; a bound command replies {@link ItemworldMessageKey#POWERTOOL_BOUND}.
 */
@NullMarked
public final class PowertoolCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.powertool.use";

    private final PowertoolPolicy policy;
    private final PdcPowertoolStore store;

    public PowertoolCommand(ItemworldServices services, PowertoolPolicy policy, PdcPowertoolStore store) {
        super(services, "powertool", SubFeatureGroup.POWERTOOL, "Bind a command to the held item.");
        this.policy = java.util.Objects.requireNonNull(policy, "policy");
        this.store = java.util.Objects.requireNonNull(store, "store");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(ctx -> run(ctx, ""))
                .then(Commands.argument("command", StringArgumentType.greedyString())
                        .executes(ctx -> run(ctx, StringArgumentType.getString(ctx, "command"))))
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    @Override
    public java.util.List<String> aliases() {
        return java.util.List.of("pt");
    }

    private int run(CommandContext<CommandSourceStack> ctx, String rawCommand) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Player player = player(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        Optional<ItemStack> held = heldItem(ctx, player);
        if (held.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        bind(ctx, player, held.get(), rawCommand);
        return Command.SINGLE_SUCCESS;
    }

    private void bind(CommandContext<CommandSourceStack> ctx, Player player, ItemStack hand, String rawCommand) {
        Optional<String> itemKey = Optional.of(hand.getType().getKey().toString());
        Result<PowertoolBinding, ItemWorldError> built = policy.bind(itemKey, rawCommand);
        if (built.isErr()) {
            reply(ctx, ItemworldMessageKey.POWERTOOL_NO_ITEM);
            return;
        }
        PowertoolBinding binding = built.orElseThrow();
        services.kernel().scheduler().onEntity(ref(player), () -> {
            store.apply(hand, binding);
            if (binding.isClear()) {
                reply(ctx, ItemworldMessageKey.POWERTOOL_CLEARED);
            } else {
                reply(
                        ctx,
                        ItemworldMessageKey.POWERTOOL_BOUND,
                        Map.of("command", binding.firstCommand().orElse("")));
            }
        });
    }
}
