package com.uxplima.uxmessentials.shared.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ClickContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.gui.ConfirmMenu;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.SimpleGui;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A reusable, config-driven property grid for editing one entity of type {@code T}: a chest laid out from an
 * {@link EntityEditorLayout}, with one button per {@link EditableProperty} drawn at its configured slot
 * (label as the name, current value as the lore), a back button, and an optional delete button gated behind a
 * uxmLib {@link ConfirmMenu}. The properties for an entity are supplied by the caller; the editor renders
 * them, routes a click on a property slot to that property's {@link EditableProperty#onClick}, and hands it a
 * {@link ClickContext} whose reopen hook redraws this editor so a value change shows.
 *
 * <p>The view holds no module logic: the property list, the title, and the back/delete callbacks are all
 * supplied by the caller, and a property mutates through the module's own use case. The menu is a uxmLib
 * {@link SimpleGui} whose holder is itself, so click routing flows through the installed menu listener.
 * {@link #open} shows the inventory in the viewer's screen, so it is scheduled on the viewer's entity thread
 * through the shared {@link Scheduler}. The same {@link EntityEditorLayout#propertySlots} drives both the
 * render and {@link #propertyAt}, so a clicked slot always resolves to the property drawn there.
 *
 * @param <T> the edited entity type
 */
@NullMarked
public final class EntityEditorView<T> {

    private final GuiText guiText;
    private final Scheduler scheduler;
    private final EntityEditorLayout layout;
    private final BiFunction<PlayerRef, T, Component> title;
    private final MessageKey valueLore;
    private final MessageKey backName;
    private final @Nullable MessageKey deleteName;
    private final @Nullable MessageKey deleteConfirmTitle;
    private final Function<T, List<EditableProperty>> properties;
    private final BiConsumer<Player, PlayerRef> onBack;
    private final @Nullable BiConsumer<Player, T> onDelete;

    private EntityEditorView(Builder<T> builder) {
        this.guiText = Objects.requireNonNull(builder.guiText, "guiText");
        this.scheduler = Objects.requireNonNull(builder.scheduler, "scheduler");
        this.layout = Objects.requireNonNull(builder.layout, "layout");
        this.title = Objects.requireNonNull(builder.title, "title");
        this.valueLore = Objects.requireNonNull(builder.valueLore, "valueLore");
        this.backName = Objects.requireNonNull(builder.backName, "backName");
        this.deleteName = builder.deleteName;
        this.deleteConfirmTitle = builder.deleteConfirmTitle;
        this.properties = Objects.requireNonNull(builder.properties, "properties");
        this.onBack = Objects.requireNonNull(builder.onBack, "onBack");
        this.onDelete = builder.onDelete;
    }

    /** Start building an editor view; required fields are validated at {@link Builder#build}. */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /** Open the editor for {@code entity}, scheduled on the viewer's entity thread. */
    public void open(Player player, PlayerRef viewer, T entity) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(entity, "entity");
        scheduler.onEntity(viewer, () -> build(player, viewer, entity).open(player));
    }

    /**
     * The property drawn at {@code slot} for {@code entity}, or empty when the slot carries no property. The
     * editor uses this internally to turn a click into the property to edit; it is public so a module's tests
     * (or a bespoke listener) can assert the slot↔property mapping without firing a click.
     */
    public Optional<EditableProperty> propertyAt(int slot, T entity) {
        Objects.requireNonNull(entity, "entity");
        List<EditableProperty> props = properties.apply(entity);
        int index = layout.propertySlots().indexOf(slot);
        return index >= 0 && index < props.size() ? Optional.of(props.get(index)) : Optional.empty();
    }

    private SimpleGui build(Player player, PlayerRef viewer, T entity) {
        SimpleGui gui = Guis.gui()
                .title(title.apply(viewer, entity))
                .rows(layout.rows())
                .build();
        fill(gui);
        Runnable reopen = () -> open(player, viewer, entity);
        List<EditableProperty> props = properties.apply(entity);
        List<Integer> slots = layout.propertySlots();
        for (int i = 0; i < props.size() && i < slots.size(); i++) {
            EditableProperty property = props.get(i);
            gui.set(slots.get(i), propertyButton(viewer, property, reopen));
        }
        gui.set(layout.backSlot(), backButton(viewer, player));
        deleteButton(gui, player, viewer, entity);
        return gui;
    }

    private GuiItem propertyButton(PlayerRef viewer, EditableProperty property, Runnable reopen) {
        ItemStack icon = ItemBuilder.of(property.icon())
                .name(guiText.text(viewer, property.label()))
                .lore(guiText.text(viewer, valueLore, Map.of("value", property.valueLore(viewer))))
                .build();
        return GuiItem.button(icon, event -> property.onClick(ClickContext.from(event, viewer, reopen)));
    }

    private GuiItem backButton(PlayerRef viewer, Player player) {
        ItemStack back = ItemBuilder.of(layout.backIcon())
                .name(guiText.text(viewer, backName))
                .build();
        return GuiItem.button(back, event -> onBack.accept(player, viewer));
    }

    private void deleteButton(SimpleGui gui, Player player, PlayerRef viewer, T entity) {
        if (onDelete == null
                || deleteName == null
                || deleteConfirmTitle == null
                || layout.deleteSlot().isEmpty()) {
            return;
        }
        ItemStack delete = ItemBuilder.of(layout.deleteIcon())
                .name(guiText.text(viewer, deleteName))
                .build();
        gui.set(layout.deleteSlot().getAsInt(), GuiItem.button(delete, event -> confirmDelete(player, viewer, entity)));
    }

    private void confirmDelete(Player player, PlayerRef viewer, T entity) {
        Component confirmTitle = guiText.text(viewer, Objects.requireNonNull(deleteConfirmTitle));
        BiConsumer<Player, T> delete = Objects.requireNonNull(onDelete);
        ConfirmMenu.of(confirmTitle, () -> delete.accept(player, entity), () -> open(player, viewer, entity))
                .open(player);
    }

    private void fill(SimpleGui gui) {
        ItemStack filler =
                ItemBuilder.of(layout.filler()).name(Component.empty()).build();
        for (int slot = 0; slot < layout.rows() * 9; slot++) {
            gui.set(slot, GuiItem.display(filler));
        }
    }

    /** Fluent builder so a module names only the parts it uses; delete is optional. */
    @NullMarked
    public static final class Builder<T> {
        private @Nullable GuiText guiText;
        private @Nullable Scheduler scheduler;
        private @Nullable EntityEditorLayout layout;
        private @Nullable BiFunction<PlayerRef, T, Component> title;
        private @Nullable MessageKey valueLore;
        private @Nullable MessageKey backName;
        private @Nullable MessageKey deleteName;
        private @Nullable MessageKey deleteConfirmTitle;
        private @Nullable Function<T, List<EditableProperty>> properties;
        private @Nullable BiConsumer<Player, PlayerRef> onBack;
        private @Nullable BiConsumer<Player, T> onDelete;

        private Builder() {}

        public Builder<T> guiText(GuiText guiText) {
            this.guiText = Objects.requireNonNull(guiText, "guiText");
            return this;
        }

        public Builder<T> scheduler(Scheduler scheduler) {
            this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
            return this;
        }

        public Builder<T> layout(EntityEditorLayout layout) {
            this.layout = Objects.requireNonNull(layout, "layout");
            return this;
        }

        /** The editor title, resolved per viewer and entity (a module wraps the entity name in {@code <value>}). */
        public Builder<T> title(BiFunction<PlayerRef, T, Component> title) {
            this.title = Objects.requireNonNull(title, "title");
            return this;
        }

        /** The catalog line each property's current value is rendered into (carries a {@code {value}} placeholder). */
        public Builder<T> valueLore(MessageKey valueLore) {
            this.valueLore = Objects.requireNonNull(valueLore, "valueLore");
            return this;
        }

        public Builder<T> backName(MessageKey backName) {
            this.backName = Objects.requireNonNull(backName, "backName");
            return this;
        }

        public Builder<T> properties(Function<T, List<EditableProperty>> properties) {
            this.properties = Objects.requireNonNull(properties, "properties");
            return this;
        }

        public Builder<T> onBack(BiConsumer<Player, PlayerRef> onBack) {
            this.onBack = Objects.requireNonNull(onBack, "onBack");
            return this;
        }

        /** Wire the optional delete button: its name, the confirm title, and the delete handler. */
        public Builder<T> onDelete(
                MessageKey deleteName, MessageKey deleteConfirmTitle, BiConsumer<Player, T> onDelete) {
            this.deleteName = Objects.requireNonNull(deleteName, "deleteName");
            this.deleteConfirmTitle = Objects.requireNonNull(deleteConfirmTitle, "deleteConfirmTitle");
            this.onDelete = Objects.requireNonNull(onDelete, "onDelete");
            return this;
        }

        /** Build the view; the constructor validates that every required field was set. */
        public EntityEditorView<T> build() {
            return new EntityEditorView<>(this);
        }
    }
}
