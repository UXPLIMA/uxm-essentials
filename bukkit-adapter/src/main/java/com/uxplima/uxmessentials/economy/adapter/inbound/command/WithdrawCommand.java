package com.uxplima.uxmessentials.economy.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import net.kyori.adventure.text.minimessage.MiniMessage;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.economy.adapter.EconomyServices;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.domain.Banknote;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * Handles `/withdraw <amount> [currency]` to convert virtual money to physical banknote item.
 */
@NullMarked
public final class WithdrawCommand extends EconomyCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.economy.withdraw";

    private final NamespacedKey valueKey;
    private final NamespacedKey currencyKey;
    private final NamespacedKey tokenKey;
    private final MiniMessage miniMessage;
    private final Messages messages;

    public WithdrawCommand(Plugin plugin, EconomyServices services, Messages messages) {
        super(services, messages);
        Objects.requireNonNull(plugin, "plugin");
        this.valueKey = new NamespacedKey(plugin, "banknote_value");
        this.currencyKey = new NamespacedKey(plugin, "banknote_currency");
        this.tokenKey = new NamespacedKey(plugin, "banknote_token");
        this.miniMessage = MiniMessage.miniMessage();
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("withdraw")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("amount", StringArgumentType.word())
                        .executes(this::run)
                        .then(currencyArgument().executes(this::run)))
                .build();
    }

    @Override
    public String description() {
        return "Withdraw virtual money into a physical banknote item.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        Optional<Currency> currency = currency(ctx);
        if (currency.isEmpty()) {
            rejectUnknownCurrency(ref(sender));
            return Command.SINGLE_SUCCESS;
        }
        Optional<Money> money = amount(ctx.getArgument("amount", String.class), currency.get(), ref(sender));
        if (money.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        offTick(() -> withdraw(sender, ref(sender), money.get()));
        return Command.SINGLE_SUCCESS;
    }

    void withdraw(Player player, PlayerRef owner, Money amount) {
        if (amount.currency().isPhysical()) {
            services.notifier().send(owner, EconomyMessageKey.WITHDRAW_PHYSICAL_NOT_ALLOWED);
            return;
        }

        if (amount.amount().signum() <= 0) {
            services.notifier().send(owner, EconomyMessageKey.WITHDRAW_INVALID_AMOUNT);
            return;
        }

        Result<Unit, ?> result = services.provider().debit(owner, amount);
        if (result.isErr()) {
            services.notifier()
                    .send(
                            owner,
                            EconomyMessageKey.WITHDRAW_INSUFFICIENT,
                            Map.of("amount", services.notifier().amount(amount)));
            return;
        }

        UUID token = UUID.randomUUID();
        // Register in database for anti-dupe
        services.banknoteStore().register(new Banknote(token, amount, System.currentTimeMillis()));

        services.scheduler().onEntity(owner, () -> {
            ItemStack banknote = ItemBuilder.of(Material.PAPER)
                    .name(itemText(owner, EconomyMessageKey.BANKNOTE_ITEM_NAME, Map.of()))
                    .lore(List.of(
                            itemText(
                                    owner,
                                    EconomyMessageKey.BANKNOTE_ITEM_LORE_VALUE,
                                    Map.of(
                                            "value",
                                            amount.amount().toPlainString(),
                                            "currency",
                                            amount.currency().id().value())),
                            itemText(
                                    owner,
                                    EconomyMessageKey.BANKNOTE_ITEM_LORE_SIGNATORY,
                                    Map.of("signatory", owner.name())),
                            itemText(owner, EconomyMessageKey.BANKNOTE_ITEM_LORE_HINT, Map.of())))
                    .build();

            ItemMeta meta = banknote.getItemMeta();
            if (meta != null) {
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                pdc.set(valueKey, PersistentDataType.STRING, amount.amount().toPlainString());
                pdc.set(
                        currencyKey,
                        PersistentDataType.STRING,
                        amount.currency().id().value());
                pdc.set(tokenKey, PersistentDataType.STRING, token.toString());
                banknote.setItemMeta(meta);
            }

            Map<Integer, ItemStack> remaining = player.getInventory().addItem(banknote);
            if (!remaining.isEmpty()) {
                for (ItemStack left : remaining.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), left);
                }
            }

            services.notifier()
                    .send(
                            owner,
                            EconomyMessageKey.BANKNOTE_WITHDRAWN,
                            Map.of("amount", services.notifier().amount(amount)));
        });
    }

    /** Resolve an item name/lore line in the owner's locale and parse it to a non-italic Component. */
    private net.kyori.adventure.text.Component itemText(
            PlayerRef owner, EconomyMessageKey key, Map<String, String> placeholders) {
        return miniMessage
                .deserialize(messages.resolve(owner, key, placeholders))
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
    }
}
