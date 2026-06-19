package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.ExchangeRate;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.FixedMenuLayout;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the /exchange dashboard icons (the source/target currency pickers, the rate-info and no-rate buttons,
 * and the close button), resolving every line through a {@code MessageKey} in the viewer's locale. Extracted
 * from {@link ExchangeGuiView} so each class stays within the size budget; this class holds no GUI or click
 * logic, only icon rendering. The currency-picker icon material comes from the per-currency {@code icon-material}
 * (via {@link CurrencyIcons}); the info/no-rate/close materials come from the {@link FixedMenuLayout}.
 */
@NullMarked
final class ExchangeIcons {

    private final Messages messages;
    private final EconomyNotifier notifier;
    private final FixedMenuLayout layout;

    ExchangeIcons(Messages messages, EconomyNotifier notifier, FixedMenuLayout layout) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.layout = Objects.requireNonNull(layout, "layout");
    }

    ItemStack currency(PlayerRef viewer, Currency shown, Money balance, boolean isSource) {
        EconomyMessageKey nameKey =
                isSource ? EconomyMessageKey.EXCHANGE_GUI_SOURCE_NAME : EconomyMessageKey.EXCHANGE_GUI_TARGET_NAME;
        EconomyMessageKey loreKey =
                isSource ? EconomyMessageKey.EXCHANGE_GUI_SOURCE_LORE : EconomyMessageKey.EXCHANGE_GUI_TARGET_LORE;
        return ItemBuilder.of(CurrencyIcons.materialFor(shown, Material.PAPER))
                .name(text(viewer, nameKey, Map.of("currency", shown.plural())))
                .lore(textLoreLines(viewer, loreKey, Map.of("balance", notifier.amount(balance))))
                .build();
    }

    ItemStack rate(PlayerRef viewer, Currency source, Currency target, ExchangeRate rate) {
        String rateStr = rate.rate().toPlainString();
        String feeStr = rate.feePercent()
                .multiply(BigDecimal.valueOf(100))
                .stripTrailingZeros()
                .toPlainString();
        return ItemBuilder.of(layout.material("info"))
                .name(text(viewer, EconomyMessageKey.EXCHANGE_GUI_INFO_NAME, Map.of()))
                .lore(textLoreLines(
                        viewer,
                        EconomyMessageKey.EXCHANGE_GUI_INFO_LORE,
                        Map.of("source", source.plural(), "target", target.plural(), "rate", rateStr, "fee", feeStr)))
                .build();
    }

    ItemStack noRate(PlayerRef viewer, Currency source, Currency target) {
        return ItemBuilder.of(layout.material("no-rate"))
                .name(text(viewer, EconomyMessageKey.EXCHANGE_GUI_NO_RATE_NAME, Map.of()))
                .lore(List.of(text(
                        viewer,
                        EconomyMessageKey.EXCHANGE_GUI_NO_RATE_LORE,
                        Map.of("source", source.plural(), "target", target.plural()))))
                .build();
    }

    ItemStack close(PlayerRef viewer) {
        return ItemBuilder.of(layout.material("close"))
                .name(text(viewer, EconomyMessageKey.BALTOP_GUI_CLOSE, Map.of()))
                .build();
    }

    ItemStack filler() {
        return ItemBuilder.of(layout.fillerMaterial()).name(Component.empty()).build();
    }

    private Component text(PlayerRef viewer, EconomyMessageKey key, Map<String, String> placeholders) {
        return StyledText.render(messages.resolve(viewer, key, placeholders));
    }

    /**
     * Renders a {@code <newline>}-joined lore block as one styled component; {@code ItemBuilder.lore} splits it
     * into separate lines on the embedded newlines, so the canon lore rhythm renders one line per beat.
     */
    private List<Component> textLoreLines(PlayerRef viewer, EconomyMessageKey key, Map<String, String> placeholders) {
        return List.of(StyledText.render(messages.resolve(viewer, key, placeholders)));
    }
}
