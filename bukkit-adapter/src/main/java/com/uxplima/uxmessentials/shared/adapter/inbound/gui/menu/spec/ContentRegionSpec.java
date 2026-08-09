package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec;

import java.util.Objects;

/**
 * A block of slots the engine hands to a feature rather than drawing itself: a menu's {@code content { }} region.
 * The chrome around it (frame, buttons, title) stays ordinary spec items an operator may re-skin; the slots named
 * here hold whatever the feature puts in them, which is how a screen that shows or edits real item stacks (a trade
 * offer, an inventory mirror, a kit's contents) can still be laid out in a conf file.
 *
 * <p>The {@code id} is the id a {@code ContentProvider} is registered under, so a spec names its feature by that id
 * and nothing else links the two. A region whose provider is not registered stays empty and refuses every click, so
 * a typo can never open a hole into a live inventory.
 *
 * <p>{@code editable} is the region's own gate: {@code false} means the engine cancels every click on those slots
 * exactly as it does for a chrome slot, so a read-only mirror needs no provider logic to stay read-only.
 * {@code true} only lifts the blanket cancel; each individual click is still put to the provider, which is what
 * decides whether that one movement is allowed.
 *
 * <p>Bukkit-free by design, like every {@code spec/} type: the {@code ItemStack}s that fill the region and the
 * click gestures that move them live in the runtime, which is the only place allowed to touch Bukkit.
 *
 * @param id the id the region's provider is registered under
 * @param slots the slots the provider owns, in declared order (index 0 of its contents fills the first slot)
 * @param editable whether the viewer may move items in these slots at all, subject to the provider's own answer
 */
public record ContentRegionSpec(String id, SlotSet slots, boolean editable) {

    public ContentRegionSpec {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("content region id must not be blank");
        }
        Objects.requireNonNull(slots, "slots");
        if (slots.slots().isEmpty()) {
            throw new IllegalArgumentException("content region '" + id + "' declares no slots");
        }
    }

    /** Whether {@code slot} belongs to this region. */
    public boolean covers(int slot) {
        return slots.slots().contains(slot);
    }

    /** The position of {@code slot} within the region's declared order, or {@code -1} when it is not covered. */
    public int indexOf(int slot) {
        return slots.slots().indexOf(slot);
    }
}
