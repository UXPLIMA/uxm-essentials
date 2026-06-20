package com.uxplima.uxmessentials.shared.adapter.inbound.gui.property;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.gui.anvil.AnvilInput;
import com.uxplima.uxmlib.gui.anvil.AnvilResult;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * A property whose click opens a uxmLib {@link AnvilInput} text prompt, validates the typed line, and hands
 * the accepted value to a setter. The prompt's hint is a catalog line resolved through {@link GuiText}; the
 * validator turns the raw text into the accepted value or rejects it (an empty {@link Optional}), so a module
 * can trim, length-check, or pattern-match without the framework knowing the rules. An accepted value is
 * written through the caller's setter off the tick thread via the shared {@link Scheduler}; on submit (valid
 * or not) and on cancel the editor is redrawn so the viewer lands back where they were.
 *
 * <p>The setter is the module's existing application use case wrapped as a {@link Consumer}; this property
 * holds no domain logic. The anvil callback fires on the player's region thread, so the redraw and the
 * scheduler hop are both safe.
 */
@NullMarked
public final class TextProperty implements EditableProperty {

    private final MessageKey label;
    private final MessageKey promptHint;
    private final Material icon;
    private final GuiText guiText;
    private final Supplier<String> current;
    private final Function<String, Optional<String>> validator;
    private final Consumer<String> setter;
    private final AnvilInput anvil;
    private final Scheduler scheduler;

    public TextProperty(
            MessageKey label,
            MessageKey promptHint,
            Material icon,
            GuiText guiText,
            Supplier<String> current,
            Function<String, Optional<String>> validator,
            Consumer<String> setter,
            AnvilInput anvil,
            Scheduler scheduler) {
        this.label = Objects.requireNonNull(label, "label");
        this.promptHint = Objects.requireNonNull(promptHint, "promptHint");
        this.icon = Objects.requireNonNull(icon, "icon");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.current = Objects.requireNonNull(current, "current");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.setter = Objects.requireNonNull(setter, "setter");
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
        return current.get();
    }

    @Override
    public void onClick(ClickContext context) {
        Objects.requireNonNull(context, "context");
        ItemStack prompt = ItemBuilder.of(icon)
                .name(guiText.text(context.viewer(), promptHint))
                .build();
        anvil.open(context.player(), prompt, result -> handle(context, result));
    }

    private void handle(ClickContext context, AnvilResult result) {
        if (result instanceof AnvilResult.Submitted submitted) {
            applyInput(context, submitted.text());
        } else {
            context.reopen().run();
        }
    }

    /**
     * Apply a submitted line: validate it, and on acceptance write it through the setter off-thread and redraw;
     * on rejection redraw without writing. The anvil callback delegates here; it is also the seam a test drives
     * to pin the validate-then-set behaviour without opening a live anvil, the same pattern the home rename
     * editor uses.
     */
    public void applyInput(ClickContext context, String raw) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(raw, "raw");
        Optional<String> accepted = validator.apply(raw);
        if (accepted.isEmpty()) {
            context.reopen().run();
            return;
        }
        String value = accepted.get();
        scheduler.async(() -> {
            setter.accept(value);
            scheduler.onEntity(context.viewer(), context.reopen());
        });
    }
}
