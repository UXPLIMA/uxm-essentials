package com.uxplima.uxmessentials.shared.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.PaginatedGui;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A reusable, config-driven paginated management list over an entity type {@code T}. It draws one icon per
 * entity into the configured content slots (paging when there are more entities than fit), a previous/next
 * button at the layout's nav slots, an optional create button, and a glass-filler background — all geometry,
 * materials, and slots from an {@link EntityListLayout}, every visible string from a {@link MessageKey}. A
 * click on an entity icon invokes {@code onSelect} (a module wires that to open its {@link EntityEditorView});
 * a click on the create button invokes the optional {@code onCreate}.
 *
 * <p>The view holds no module logic: the entity supplier, the per-entity icon renderer, and the callbacks are
 * all supplied by the caller. The menu is a uxmLib {@link PaginatedGui} whose holder is itself, so click
 * routing flows through the installed menu listener — there is no bespoke listener. {@link #open} builds and
 * shows the inventory in the viewer's screen, so it is scheduled on their entity thread through the shared
 * {@link Scheduler}; if the entity list read is expensive a caller passes a supplier that has already been
 * resolved off-thread.
 *
 * <p>A caller may also wire one optional <em>action button</em> at a fixed slot — a non-entity control such as a
 * settings opener — through {@code onAction}. It is drawn over the filler at its slot and its click runs the
 * supplied handler; the list stays the same paginated entity browser otherwise.
 *
 * @param <T> the managed entity type
 */
@NullMarked
public final class EntityListView<T> {

    private final GuiText guiText;
    private final Scheduler scheduler;
    private final EntityListLayout layout;
    private final MessageKey title;
    private final MessageKey prevName;
    private final MessageKey nextName;
    private final @Nullable MessageKey createName;
    private final Supplier<List<T>> entities;
    private final BiFunction<PlayerRef, T, ItemStack> iconRenderer;
    private final BiConsumer<Player, T> onSelect;
    private final @Nullable Consumer<Player> onCreate;
    private final OptionalInt actionSlot;
    private final Material actionIcon;
    private final @Nullable MessageKey actionName;
    private final @Nullable Consumer<Player> onAction;

    private EntityListView(Builder<T> builder) {
        this.guiText = Objects.requireNonNull(builder.guiText, "guiText");
        this.scheduler = Objects.requireNonNull(builder.scheduler, "scheduler");
        this.layout = Objects.requireNonNull(builder.layout, "layout");
        this.title = Objects.requireNonNull(builder.title, "title");
        this.prevName = Objects.requireNonNull(builder.prevName, "prevName");
        this.nextName = Objects.requireNonNull(builder.nextName, "nextName");
        this.createName = builder.createName;
        this.entities = Objects.requireNonNull(builder.entities, "entities");
        this.iconRenderer = Objects.requireNonNull(builder.iconRenderer, "iconRenderer");
        this.onSelect = Objects.requireNonNull(builder.onSelect, "onSelect");
        this.onCreate = builder.onCreate;
        this.actionSlot = builder.actionSlot;
        this.actionIcon = builder.actionIcon;
        this.actionName = builder.actionName;
        this.onAction = builder.onAction;
    }

    /** Start building a list view; the required fields are validated at {@link Builder#build}. */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /** Open the list for {@code viewer} on its first page, scheduled on the viewer's entity thread. */
    public void open(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        scheduler.onEntity(viewer, () -> build(player, viewer).open(player));
    }

    private PaginatedGui build(Player player, PlayerRef viewer) {
        Component titleText = guiText.text(viewer, title);
        PaginatedGui gui = Guis.paginated()
                .title(titleText)
                .rows(layout.rows())
                .contentSlots(contentSlots())
                .build();
        fill(gui);
        for (T entity : entities.get()) {
            gui.addPageItem(GuiItem.button(iconRenderer.apply(viewer, entity), e -> onSelect.accept(player, entity)));
        }
        navigation(gui, player, viewer);
        return gui;
    }

    private void navigation(PaginatedGui gui, Player player, PlayerRef viewer) {
        gui.set(layout.base().prevSlot(), GuiItem.button(navButton(viewer, prevName), e -> gui.previousPage()));
        gui.set(layout.base().nextSlot(), GuiItem.button(navButton(viewer, nextName), e -> gui.nextPage()));
        if (onCreate != null && createName != null && layout.createSlot().isPresent()) {
            ItemStack create = ItemBuilder.of(layout.createIcon())
                    .name(guiText.text(viewer, createName))
                    .build();
            gui.set(layout.createSlot().getAsInt(), GuiItem.button(create, e -> onCreate.accept(player)));
        }
        if (onAction != null && actionName != null && actionSlot.isPresent()) {
            ItemStack action = ItemBuilder.of(actionIcon)
                    .name(guiText.text(viewer, actionName))
                    .build();
            gui.set(actionSlot.getAsInt(), GuiItem.button(action, e -> onAction.accept(player)));
        }
    }

    private ItemStack navButton(PlayerRef viewer, MessageKey name) {
        return ItemBuilder.of(layout.base().navIcon())
                .name(guiText.text(viewer, name))
                .build();
    }

    private void fill(PaginatedGui gui) {
        ItemStack filler =
                ItemBuilder.of(layout.filler()).name(Component.empty()).build();
        List<Integer> content = contentSlots();
        for (int slot = 0; slot < layout.rows() * 9; slot++) {
            if (!content.contains(slot)) {
                gui.set(slot, GuiItem.display(filler));
            }
        }
    }

    private List<Integer> contentSlots() {
        return layout.explicitContentSlots().orElseGet(() -> {
            List<Integer> defaults = new ArrayList<>();
            int limit = (layout.rows() - 1) * 9;
            for (int slot = 0; slot < limit; slot++) {
                defaults.add(slot);
            }
            return defaults;
        });
    }

    /** Fluent builder so a module names only the parts it uses; create is optional. */
    @NullMarked
    public static final class Builder<T> {
        private @Nullable GuiText guiText;
        private @Nullable Scheduler scheduler;
        private @Nullable EntityListLayout layout;
        private @Nullable MessageKey title;
        private @Nullable MessageKey prevName;
        private @Nullable MessageKey nextName;
        private @Nullable MessageKey createName;
        private @Nullable Supplier<List<T>> entities;
        private @Nullable BiFunction<PlayerRef, T, ItemStack> iconRenderer;
        private @Nullable BiConsumer<Player, T> onSelect;
        private @Nullable Consumer<Player> onCreate;
        private OptionalInt actionSlot = OptionalInt.empty();
        private Material actionIcon = Material.COMPARATOR;
        private @Nullable MessageKey actionName;
        private @Nullable Consumer<Player> onAction;

        private Builder() {}

        public Builder<T> guiText(GuiText guiText) {
            this.guiText = Objects.requireNonNull(guiText, "guiText");
            return this;
        }

        public Builder<T> scheduler(Scheduler scheduler) {
            this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
            return this;
        }

        public Builder<T> layout(EntityListLayout layout) {
            this.layout = Objects.requireNonNull(layout, "layout");
            return this;
        }

        public Builder<T> title(MessageKey title) {
            this.title = Objects.requireNonNull(title, "title");
            return this;
        }

        /** The previous- and next-page button names. */
        public Builder<T> navNames(MessageKey prevName, MessageKey nextName) {
            this.prevName = Objects.requireNonNull(prevName, "prevName");
            this.nextName = Objects.requireNonNull(nextName, "nextName");
            return this;
        }

        public Builder<T> entities(Supplier<List<T>> entities) {
            this.entities = Objects.requireNonNull(entities, "entities");
            return this;
        }

        public Builder<T> iconRenderer(BiFunction<PlayerRef, T, ItemStack> iconRenderer) {
            this.iconRenderer = Objects.requireNonNull(iconRenderer, "iconRenderer");
            return this;
        }

        public Builder<T> onSelect(BiConsumer<Player, T> onSelect) {
            this.onSelect = Objects.requireNonNull(onSelect, "onSelect");
            return this;
        }

        /** Wire the optional create button: its name and the click handler. The layout supplies the slot/icon. */
        public Builder<T> onCreate(MessageKey createName, Consumer<Player> onCreate) {
            this.createName = Objects.requireNonNull(createName, "createName");
            this.onCreate = Objects.requireNonNull(onCreate, "onCreate");
            return this;
        }

        /**
         * Wire one optional action button at {@code slot}: a non-entity control (e.g. a settings opener) drawn over
         * the filler with {@code icon} and {@code name}, whose click runs {@code onAction}.
         */
        public Builder<T> onAction(int slot, Material icon, MessageKey name, Consumer<Player> onAction) {
            this.actionSlot = OptionalInt.of(slot);
            this.actionIcon = Objects.requireNonNull(icon, "icon");
            this.actionName = Objects.requireNonNull(name, "name");
            this.onAction = Objects.requireNonNull(onAction, "onAction");
            return this;
        }

        /** Build the view; the constructor validates that every required field was set. */
        public EntityListView<T> build() {
            return new EntityListView<>(this);
        }
    }
}
