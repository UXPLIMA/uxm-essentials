package com.uxplima.uxmessentials.custommenus.adapter.inbound.gui;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmlib.menu.property.EditableProperty;
import com.uxplima.uxmlib.menu.property.PropertyClick;
import org.jspecify.annotations.NullMarked;

/**
 * The stand-in an editor row draws while the field it edits cannot be changed: same label, same icon and the same
 * current value as the live field, so the row keeps its place in the grid, but the value lore carries the reason and
 * the click is refused with a line in chat rather than going quietly dead. It is the property-editor form of the
 * convention the vault selector already uses for a slot the viewer does not own: the lore says why, the click says it
 * again, and nothing else about the button changes.
 *
 * <p>The menu-property editor draws one for its rows stepper and one for its inventory-type selector while
 * bottom-inventory is on, because a bottom-inventory menu is six rows and chest-only by definition. Refusing here is
 * the point: the writer would refuse the same menu at save time, ten clicks after the interface offered the choice.
 *
 * <p>Both the reason lore and the refusal line come from the caller as message-catalog lookups, so this class names no
 * cause of its own and any other field that becomes unavailable can reuse it with its own two keys.
 */
@NullMarked
final class MenuLockedProperty implements EditableProperty {

    private final MessageKey label;
    private final Material icon;
    private final Supplier<String> value;
    private final BiFunction<Player, String, String> lockedLore;
    private final Consumer<Player> onRefused;

    /**
     * @param label the same label key the live field carries, so the row does not rename itself when it locks
     * @param icon the same icon the live field carries
     * @param value the field's current value, rendered exactly as the live field would render it
     * @param lockedLore wraps that value with the reason the field is locked, resolved for the viewer
     * @param onRefused tells the viewer why the click did nothing
     */
    MenuLockedProperty(
            MessageKey label,
            Material icon,
            Supplier<String> value,
            BiFunction<Player, String, String> lockedLore,
            Consumer<Player> onRefused) {
        this.label = Objects.requireNonNull(label, "label");
        this.icon = Objects.requireNonNull(icon, "icon");
        this.value = Objects.requireNonNull(value, "value");
        this.lockedLore = Objects.requireNonNull(lockedLore, "lockedLore");
        this.onRefused = Objects.requireNonNull(onRefused, "onRefused");
    }

    @Override
    public String label() {
        return label.key();
    }

    @Override
    public Material icon() {
        return icon;
    }

    @Override
    public String valueLore(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        return lockedLore.apply(viewer, value.get());
    }

    /**
     * Refuse the click and say why, then redraw. Nothing is written and nothing is scheduled: the refusal is pure
     * presentation, and it runs on the click thread, which is already the viewer's own.
     */
    @Override
    public void onClick(PropertyClick click) {
        Objects.requireNonNull(click, "click");
        onRefused.accept(click.viewer());
        click.reopen().run();
    }
}
