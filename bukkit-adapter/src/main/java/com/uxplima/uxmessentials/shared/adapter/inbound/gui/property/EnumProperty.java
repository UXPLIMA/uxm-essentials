package com.uxplima.uxmessentials.shared.adapter.inbound.gui.property;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.SimpleGui;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * A property whose click opens a small selector sub-menu listing the options, one button per option drawn into
 * the configured option slots; clicking an option hands it to the setter and returns to the editor. The
 * selector's title, the per-option name (resolved from the option through the caller's display function), and
 * its geometry (rows, option slots, option-icon and filler materials) all come from the caller — nothing is
 * hardcoded. The currently-selected option is highlighted with an enchant glint so the viewer can see where
 * they are.
 *
 * <p>The chosen option is written through the caller's setter off the tick thread via the shared
 * {@link Scheduler}, then the editor is redrawn. The setter is the module's existing application use case
 * wrapped as a {@link Consumer}; this property holds no domain logic. The selector is opened on the player's
 * region thread (the click already runs there) and is a uxmLib {@link SimpleGui}, so click routing flows
 * through the installed menu listener like every other framework menu.
 *
 * @param <E> the option type (typically an enum)
 */
@NullMarked
public final class EnumProperty<E> implements EditableProperty {

    private final MessageKey label;
    private final MessageKey selectorTitle;
    private final Material icon;
    private final GuiText guiText;
    private final List<E> options;
    private final Supplier<E> current;
    private final BiFunction<PlayerRef, E, String> display;
    private final Consumer<E> setter;
    private final Material optionIcon;
    private final Material fillerIcon;
    private final List<Integer> optionSlots;
    private final int rows;
    private final Scheduler scheduler;

    public EnumProperty(
            MessageKey label,
            MessageKey selectorTitle,
            Material icon,
            GuiText guiText,
            List<E> options,
            Supplier<E> current,
            BiFunction<PlayerRef, E, String> display,
            Consumer<E> setter,
            Material optionIcon,
            Material fillerIcon,
            List<Integer> optionSlots,
            int rows,
            Scheduler scheduler) {
        this.label = Objects.requireNonNull(label, "label");
        this.selectorTitle = Objects.requireNonNull(selectorTitle, "selectorTitle");
        this.icon = Objects.requireNonNull(icon, "icon");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.options = List.copyOf(Objects.requireNonNull(options, "options"));
        if (this.options.isEmpty()) {
            throw new IllegalArgumentException("an enum property needs at least one option");
        }
        this.current = Objects.requireNonNull(current, "current");
        this.display = Objects.requireNonNull(display, "display");
        this.setter = Objects.requireNonNull(setter, "setter");
        this.optionIcon = Objects.requireNonNull(optionIcon, "optionIcon");
        this.fillerIcon = Objects.requireNonNull(fillerIcon, "fillerIcon");
        this.optionSlots = List.copyOf(Objects.requireNonNull(optionSlots, "optionSlots"));
        if (this.optionSlots.isEmpty()) {
            throw new IllegalArgumentException("optionSlots must not be empty");
        }
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be 1..6, was " + rows);
        }
        this.rows = rows;
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public MessageKey label() {
        return label;
    }

    @Override
    public Material icon() {
        return icon;
    }

    @Override
    public String valueLore(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        return display.apply(viewer, current.get());
    }

    @Override
    public void onClick(ClickContext context) {
        Objects.requireNonNull(context, "context");
        scheduler.onEntity(context.viewer(), () -> openSelector(context));
    }

    private void openSelector(ClickContext context) {
        SimpleGui selector = Guis.gui()
                .title(guiText.text(context.viewer(), selectorTitle))
                .rows(rows)
                .build();
        fill(selector);
        E selected = current.get();
        for (int i = 0; i < options.size() && i < optionSlots.size(); i++) {
            E option = options.get(i);
            selector.set(
                    optionSlots.get(i),
                    GuiItem.button(optionIcon(context.viewer(), option, selected), e -> choose(context, option)));
        }
        selector.open(context.player());
    }

    private void choose(ClickContext context, E option) {
        scheduler.async(() -> {
            setter.accept(option);
            scheduler.onEntity(context.viewer(), context.reopen());
        });
    }

    private ItemStack optionIcon(PlayerRef viewer, E option, E selected) {
        // The option name is a plain value string; wrap it in the value token so it picks up the canon accent.
        ItemBuilder builder = ItemBuilder.of(optionIcon)
                .name(StyledText.render("<value>" + display.apply(viewer, option) + "</value>"));
        if (Objects.equals(option, selected)) {
            // A glint marks the live option; HIDE_ENCHANTS keeps the enchant invisible in the lore.
            builder.enchant(Enchantment.UNBREAKING, 1).flags(ItemFlag.HIDE_ENCHANTS);
        }
        return builder.build();
    }

    private void fill(SimpleGui selector) {
        ItemStack filler = ItemBuilder.of(fillerIcon).name(Component.empty()).build();
        for (int slot = 0; slot < rows * 9; slot++) {
            selector.set(slot, GuiItem.display(filler));
        }
    }
}
