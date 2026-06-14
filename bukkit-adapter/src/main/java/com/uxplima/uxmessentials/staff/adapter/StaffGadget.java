package com.uxplima.uxmessentials.staff.adapter;

import java.util.Optional;

import org.jspecify.annotations.NullMarked;

/**
 * The kinds of gadget a staff member carries on the staff-mode hotbar. Each value is tagged onto its hotbar
 * item through the staff PDC key so the right-click listener can tell which gadget was used without matching on
 * material or display name (which an operator may freely re-skin in config).
 *
 * <p>STAFF-MODE ONLY: the gadgets orchestrate the existing presence and playerstate modules — VANISH toggles
 * vanish, EXAMINE opens an online player's inventory — and never carry a sanction.
 */
@NullMarked
public enum StaffGadget {

    /** Toggle the staff member's own vanish state. */
    VANISH("vanish"),

    /** Pick an online player and open their inventory. */
    EXAMINE("examine");

    private final String tag;

    StaffGadget(String tag) {
        this.tag = tag;
    }

    /** The stable PDC tag value written onto the gadget item; never the localized display name. */
    public String tag() {
        return tag;
    }

    /** Resolve a gadget from its stored PDC {@code tag}, or empty when it names no known gadget. */
    public static Optional<StaffGadget> fromTag(String tag) {
        for (StaffGadget gadget : values()) {
            if (gadget.tag.equals(tag)) {
                return Optional.of(gadget);
            }
        }
        return Optional.empty();
    }
}
