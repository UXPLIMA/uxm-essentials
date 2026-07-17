package com.uxplima.uxmessentials.security.adapter.inbound.gui;

import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.security.application.SecurityMessageKey;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The fixed slot map and item dressing of the verification keypad — a six-row chest laid out as a phone-style numeric
 * pad: the masked entry display across the top, digits {@code 1}-{@code 9} in a 3×3 block, {@code 0} on the bottom row
 * between the clear and submit buttons, and (for a player with an authenticator factor) a "type a code" button that
 * hands off to the text-input seam. Every other slot is a divider pane so the pad reads cleanly and a stray click
 * lands on nothing.
 *
 * <p>The layout is pure geometry and rendering: {@link #buttonAt(int)} decodes a raw slot into the button it carries,
 * and the render methods paint items. It holds no per-player state — that lives in the {@link PinKeypadHolder} — so
 * one instance serves every open keypad.
 */
@NullMarked
final class PinKeypadLayout {

    static final int SIZE = 54;

    /** The masked-entry display slot, centred on the top row. */
    private static final int ENTRY_SLOT = 4;

    private static final int CLEAR_SLOT = 38;
    private static final int SUBMIT_SLOT = 42;
    private static final int TOTP_SLOT = 49;

    /** The raw slot each digit sits at, indexed by the digit value ({@code DIGIT_SLOTS[7]} is where {@code 7} is). */
    private static final int[] DIGIT_SLOTS = {40, 11, 13, 15, 20, 22, 24, 29, 31, 33};

    /** The kind of button a keypad slot carries; {@link #digit} is the value for a {@link Kind#DIGIT}, else -1. */
    record Button(Kind kind, int digit) {
        static final Button NONE = new Button(Kind.NONE, -1);
    }

    enum Kind {
        DIGIT,
        CLEAR,
        SUBMIT,
        TOTP,
        NONE
    }

    /** Decode {@code rawSlot} into the button it carries, or {@link Button#NONE} for a divider or bottom-inventory slot. */
    Button buttonAt(int rawSlot) {
        for (int digit = 0; digit <= 9; digit++) {
            if (DIGIT_SLOTS[digit] == rawSlot) {
                return new Button(Kind.DIGIT, digit);
            }
        }
        return switch (rawSlot) {
            case CLEAR_SLOT -> new Button(Kind.CLEAR, -1);
            case SUBMIT_SLOT -> new Button(Kind.SUBMIT, -1);
            case TOTP_SLOT -> new Button(Kind.TOTP, -1);
            default -> Button.NONE;
        };
    }

    /** Paint the whole pad into {@code inv} for {@code viewer}: dividers, digits, controls, and the entry display. */
    void render(Inventory inv, Messages messages, PlayerRef viewer, int entryLength, boolean totpEnabled) {
        Objects.requireNonNull(inv, "inv");
        ItemStack divider = filler();
        for (int slot = 0; slot < SIZE; slot++) {
            inv.setItem(slot, divider.clone());
        }
        for (int digit = 0; digit <= 9; digit++) {
            inv.setItem(DIGIT_SLOTS[digit], digitButton(messages, viewer, digit));
        }
        inv.setItem(
                CLEAR_SLOT,
                named(Material.RED_CONCRETE, messages, viewer, SecurityMessageKey.SECURITY_VERIFY_KEYPAD_CLEAR));
        inv.setItem(
                SUBMIT_SLOT,
                named(Material.LIME_CONCRETE, messages, viewer, SecurityMessageKey.SECURITY_VERIFY_KEYPAD_SUBMIT));
        inv.setItem(TOTP_SLOT, totpEnabled ? totpButton(messages, viewer) : filler());
        renderEntry(inv, messages, viewer, entryLength);
    }

    /** Repaint only the masked entry display — the light update after a digit or clear keystroke. */
    void renderEntry(Inventory inv, Messages messages, PlayerRef viewer, int entryLength) {
        Component name = StyledText.render(messages.resolve(
                viewer, SecurityMessageKey.SECURITY_VERIFY_KEYPAD_ENTRY, Map.of("entry", mask(entryLength))));
        inv.setItem(ENTRY_SLOT, item(Material.PAPER, name));
    }

    private ItemStack digitButton(Messages messages, PlayerRef viewer, int digit) {
        Component name = StyledText.render(messages.resolve(
                viewer, SecurityMessageKey.SECURITY_VERIFY_KEYPAD_DIGIT, Map.of("digit", Integer.toString(digit))));
        return item(Material.LIGHT_GRAY_CONCRETE, name);
    }

    private ItemStack totpButton(Messages messages, PlayerRef viewer) {
        return named(Material.WRITABLE_BOOK, messages, viewer, SecurityMessageKey.SECURITY_VERIFY_KEYPAD_TOTP);
    }

    private ItemStack named(Material material, Messages messages, PlayerRef viewer, SecurityMessageKey key) {
        return item(material, StyledText.render(messages.resolve(viewer, key, Map.of())));
    }

    private ItemStack filler() {
        return item(Material.GRAY_STAINED_GLASS_PANE, Component.empty());
    }

    private static ItemStack item(Material material, Component name) {
        return ItemBuilder.of(material).name(name).build();
    }

    /** Render {@code length} asterisks so the display shows how many digits are entered without revealing them. */
    private static String mask(int length) {
        return "*".repeat(Math.max(0, length));
    }
}
