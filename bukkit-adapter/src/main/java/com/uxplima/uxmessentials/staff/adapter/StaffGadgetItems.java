package com.uxplima.uxmessentials.staff.adapter;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.outbound.hud.HudText;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Builds the staff-mode gadget items and reads the gadget tag back off them. One {@link NamespacedKey} is
 * created once in the constructor and reused on every build and every interact read, so no hot path allocates
 * one. The tag carries the stable {@link StaffGadget#tag()} value (not the operator's
 * re-skinnable display name), so the right-click listener resolves the gadget regardless of how the item is
 * styled in config.
 *
 * <p>The display name comes from {@code modules/staff/config.conf}. The shipped names are {@code @staff.gadget.*}
 * references, read in the language of the staff member the hotbar is built for; a name an operator wrote in their own
 * words is MiniMessage used as written. Either way it is parsed at build time with the shared {@link StyleTags}
 * palette resolver so it can use the project tokens ({@code <accent>}, {@code <h:'…'>}, …). {@code ItemBuilder.name}
 * defaults the name to upright (non-italic).
 */
@NullMarked
public final class StaffGadgetItems {

    private final NamespacedKey key;

    public StaffGadgetItems(Plugin plugin) {
        this.key = new NamespacedKey(Objects.requireNonNull(plugin, "plugin"), "staff-gadget");
    }

    /** Build {@code holder}'s hotbar item for {@code spec}, tagged with its gadget kind so a right-click resolves it. */
    public ItemStack build(StaffSettings.GadgetSpec spec, UUID holder) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(holder, "holder");
        Component name = StyledText.render(HudText.lineFor(holder, spec.name()));
        return ItemBuilder.of(spec.material())
                .name(name)
                .editPersistentData(container -> container.set(
                        key, PersistentDataType.STRING, spec.gadget().tag()))
                .build();
    }

    /** The gadget tagged on {@code item}, or empty when it carries no staff-gadget tag. */
    public Optional<StaffGadget> gadgetOf(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        String tag = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (tag == null || tag.isBlank()) {
            return Optional.empty();
        }
        return StaffGadget.fromTag(tag);
    }

    /** Whether {@code item} is any staff gadget: the drop/move guard's cheap predicate. */
    public boolean isGadget(@Nullable ItemStack item) {
        return gadgetOf(item).isPresent();
    }
}
