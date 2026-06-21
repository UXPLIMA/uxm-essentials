package com.uxplima.uxmessentials.shared.adapter.inbound.gui;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A per-player settings panel: a config-driven grid of toggle/enum buttons, each bound to a get/set of one of
 * the opening player's own preferences. Unlike {@link EntityEditorView}, which edits an arbitrary entity, the
 * panel always edits the viewer themselves — so the "entity" is the viewer's own {@link PlayerRef}, and the
 * properties close over it. There is no list around it and no delete button: a player opens their own panel and
 * flips their own switches, every flip routing through the module's existing per-player use case.
 *
 * <p>This is a thin wrapper over {@link EntityEditorView} so a settings panel reads as what it is rather than as
 * a one-of-one entity editor: the geometry and materials come from a {@code modules/<m>/gui/<name>.conf} loaded
 * the same way every management GUI loads its layout (the delete slot in that conf, if any, is ignored — a
 * settings panel never deletes), all text is a {@link MessageKey}, and every build/flip hops through the shared
 * {@link Scheduler} on the viewer's thread with the write off it. The back button closes the menu.
 */
@NullMarked
public final class SettingsPanelView {

    private final EntityEditorView<PlayerRef> editor;

    private SettingsPanelView(Builder builder) {
        GuiText guiText = Objects.requireNonNull(builder.guiText, "guiText");
        MessageKey title = Objects.requireNonNull(builder.title, "title");
        this.editor = EntityEditorView.<PlayerRef>builder()
                .guiText(guiText)
                .scheduler(Objects.requireNonNull(builder.scheduler, "scheduler"))
                .layout(Objects.requireNonNull(builder.layout, "layout"))
                .title((viewer, who) -> guiText.text(viewer, title))
                .valueLore(Objects.requireNonNull(builder.valueLore, "valueLore"))
                .backName(Objects.requireNonNull(builder.backName, "backName"))
                .properties(Objects.requireNonNull(builder.properties, "properties"))
                .onBack(Objects.requireNonNull(builder.onBack, "onBack"))
                .build();
    }

    /** Start building a settings panel; required fields are validated at {@link Builder#build}. */
    public static Builder builder() {
        return new Builder();
    }

    /** Open the panel for {@code player}, scheduled on the viewer's entity thread. */
    public void open(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        editor.open(player, viewer, viewer);
    }

    /**
     * The setting drawn at {@code slot} for {@code viewer}, or empty when the slot carries none. Exposed so a
     * module's tests can assert the slot↔toggle mapping and fire a flip without opening a live menu.
     */
    public java.util.Optional<EditableProperty> settingAt(int slot, PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        return editor.propertyAt(slot, viewer);
    }

    /** Fluent builder: a module names its layout, its title/value/back keys, and its per-viewer settings. */
    @NullMarked
    public static final class Builder {
        private @Nullable GuiText guiText;
        private @Nullable Scheduler scheduler;
        private @Nullable EntityEditorLayout layout;
        private @Nullable MessageKey title;
        private @Nullable MessageKey valueLore;
        private @Nullable MessageKey backName;
        private @Nullable Function<PlayerRef, List<EditableProperty>> properties;
        private @Nullable BiConsumer<Player, PlayerRef> onBack;

        private Builder() {}

        public Builder guiText(GuiText guiText) {
            this.guiText = Objects.requireNonNull(guiText, "guiText");
            return this;
        }

        public Builder scheduler(Scheduler scheduler) {
            this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
            return this;
        }

        public Builder layout(EntityEditorLayout layout) {
            this.layout = Objects.requireNonNull(layout, "layout");
            return this;
        }

        /** The panel title, resolved per viewer (a settings panel has no entity name to wrap). */
        public Builder title(MessageKey title) {
            this.title = Objects.requireNonNull(title, "title");
            return this;
        }

        /** The catalog line each setting's current value is rendered into (carries a {@code {value}} placeholder). */
        public Builder valueLore(MessageKey valueLore) {
            this.valueLore = Objects.requireNonNull(valueLore, "valueLore");
            return this;
        }

        public Builder backName(MessageKey backName) {
            this.backName = Objects.requireNonNull(backName, "backName");
            return this;
        }

        /** The settings for a viewer, re-read each open: one {@link EditableProperty} per toggle. */
        public Builder settings(Function<PlayerRef, List<EditableProperty>> settings) {
            this.properties = Objects.requireNonNull(settings, "settings");
            return this;
        }

        /** What the back button does (e.g. close the menu, or reopen the hub). */
        public Builder onBack(BiConsumer<Player, PlayerRef> onBack) {
            this.onBack = Objects.requireNonNull(onBack, "onBack");
            return this;
        }

        /** Build the panel; the constructor validates that every required field was set. */
        public SettingsPanelView build() {
            return new SettingsPanelView(this);
        }
    }
}
