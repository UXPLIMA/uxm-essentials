package com.uxplima.uxmessentials.shared.adapter.inbound.gui.property;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.gui.ConfirmMenu;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.SimpleGui;
import com.uxplima.uxmlib.gui.anvil.AnvilInput;
import com.uxplima.uxmlib.gui.anvil.AnvilResult;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * A property whose click opens a sub-menu for editing a list of string entries — add, remove (confirm-gated),
 * reorder, and edit each line — backed by a single {@code List<String>} the caller reads and writes through a
 * use case (e.g. a hologram's text lines). The sub-menu draws one button per entry into the configured entry
 * slots: left-click edits the line through an anvil, right-click moves it down, shift-right-click moves it up,
 * and a separate remove click is confirm-gated. An add button opens an anvil for a new line. Each mutation
 * rewrites the whole list through the setter off the tick thread via the shared {@link Scheduler}, then
 * re-opens the sub-menu so the change shows.
 *
 * <p>Every label, hint, and the sub-menu title are catalog keys resolved through {@link GuiText}; the slots
 * and materials come from the caller (the editor layout conf), so nothing is hardcoded. The setter is the
 * module's existing application use case wrapped as a {@link Consumer}; this property holds no domain logic.
 */
@NullMarked
public final class ListProperty implements EditableProperty {

    private final MessageKey label;
    private final Material icon;
    private final GuiText guiText;
    private final Supplier<List<String>> current;
    private final Consumer<List<String>> setter;
    private final ListPropertyText keys;
    private final ListPropertyLayout layout;
    private final AnvilInput anvil;
    private final Scheduler scheduler;

    public ListProperty(
            MessageKey label,
            Material icon,
            GuiText guiText,
            Supplier<List<String>> current,
            Consumer<List<String>> setter,
            ListPropertyText keys,
            ListPropertyLayout layout,
            AnvilInput anvil,
            Scheduler scheduler) {
        this.label = Objects.requireNonNull(label, "label");
        this.icon = Objects.requireNonNull(icon, "icon");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.current = Objects.requireNonNull(current, "current");
        this.setter = Objects.requireNonNull(setter, "setter");
        this.keys = Objects.requireNonNull(keys, "keys");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.anvil = Objects.requireNonNull(anvil, "anvil");
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
        return Integer.toString(current.get().size());
    }

    @Override
    public void onClick(ClickContext context) {
        Objects.requireNonNull(context, "context");
        scheduler.onEntity(context.viewer(), () -> openList(context));
    }

    private void openList(ClickContext context) {
        SimpleGui menu = Guis.gui()
                .title(guiText.text(context.viewer(), keys.title()))
                .rows(layout.rows())
                .build();
        fill(menu);
        List<String> entries = current.get();
        List<Integer> slots = layout.entrySlots();
        for (int i = 0; i < entries.size() && i < slots.size(); i++) {
            int index = i;
            menu.set(slots.get(i), entryButton(context, entries.get(i), index));
        }
        menu.set(layout.addSlot(), addButton(context));
        menu.set(layout.backSlot(), backButton(context));
        menu.open(context.player());
    }

    private com.uxplima.uxmlib.gui.item.GuiItem entryButton(ClickContext context, String entry, int index) {
        ItemStack icon = ItemBuilder.of(layout.entryIcon())
                .name(guiText.text(context.viewer(), keys.entryName(), Map.of("entry", entry)))
                .lore(guiText.text(context.viewer(), keys.entryHints()))
                .build();
        return com.uxplima.uxmlib.gui.item.GuiItem.button(icon, event -> {
            if (event.isShiftClick() && event.isRightClick()) {
                move(context, index, -1);
            } else if (event.isRightClick()) {
                move(context, index, 1);
            } else if (event.isLeftClick() && event.isShiftClick()) {
                confirmRemove(context, index);
            } else {
                edit(context, index, entry);
            }
        });
    }

    private com.uxplima.uxmlib.gui.item.GuiItem addButton(ClickContext context) {
        ItemStack add = ItemBuilder.of(layout.addIcon())
                .name(guiText.text(context.viewer(), keys.addName()))
                .build();
        return com.uxplima.uxmlib.gui.item.GuiItem.button(add, event -> add(context));
    }

    private com.uxplima.uxmlib.gui.item.GuiItem backButton(ClickContext context) {
        ItemStack back = ItemBuilder.of(layout.backIcon())
                .name(guiText.text(context.viewer(), keys.backName()))
                .build();
        return com.uxplima.uxmlib.gui.item.GuiItem.button(
                back, event -> context.reopen().run());
    }

    private void add(ClickContext context) {
        ItemStack prompt = ItemBuilder.of(layout.addIcon())
                .name(guiText.text(context.viewer(), keys.addPrompt()))
                .build();
        anvil.open(context.player(), prompt, result -> {
            if (result instanceof AnvilResult.Submitted submitted
                    && !submitted.text().isBlank()) {
                List<String> next = new ArrayList<>(current.get());
                next.add(submitted.text());
                save(context, next);
            } else {
                scheduler.onEntity(context.viewer(), () -> openList(context));
            }
        });
    }

    private void edit(ClickContext context, int index, String existing) {
        ItemStack prompt = ItemBuilder.of(layout.entryIcon())
                .name(guiText.text(context.viewer(), keys.editPrompt(), Map.of("entry", existing)))
                .build();
        anvil.open(context.player(), prompt, result -> {
            if (result instanceof AnvilResult.Submitted submitted
                    && !submitted.text().isBlank()) {
                List<String> next = new ArrayList<>(current.get());
                if (index < next.size()) {
                    next.set(index, submitted.text());
                    save(context, next);
                    return;
                }
            }
            scheduler.onEntity(context.viewer(), () -> openList(context));
        });
    }

    private void confirmRemove(ClickContext context, int index) {
        Component title = guiText.text(context.viewer(), keys.removeConfirm());
        ConfirmMenu.of(title, () -> remove(context, index), () -> reopenList(context))
                .open(context.player());
    }

    private void remove(ClickContext context, int index) {
        List<String> next = new ArrayList<>(current.get());
        if (index >= 0 && index < next.size()) {
            next.remove(index);
            save(context, next);
        } else {
            reopenList(context);
        }
    }

    private void move(ClickContext context, int index, int direction) {
        List<String> next = new ArrayList<>(current.get());
        int target = index + direction;
        if (index >= 0 && index < next.size() && target >= 0 && target < next.size()) {
            String moved = next.remove(index);
            next.add(target, moved);
            save(context, next);
        } else {
            reopenList(context);
        }
    }

    private void save(ClickContext context, List<String> next) {
        scheduler.async(() -> {
            setter.accept(List.copyOf(next));
            scheduler.onEntity(context.viewer(), () -> openList(context));
        });
    }

    private void reopenList(ClickContext context) {
        scheduler.onEntity(context.viewer(), () -> openList(context));
    }

    private void fill(SimpleGui menu) {
        ItemStack filler =
                ItemBuilder.of(layout.fillerIcon()).name(Component.empty()).build();
        for (int slot = 0; slot < layout.rows() * 9; slot++) {
            menu.set(slot, com.uxplima.uxmlib.gui.item.GuiItem.display(filler));
        }
    }
}
