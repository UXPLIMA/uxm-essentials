package com.uxplima.uxmessentials.shared.adapter.inbound.gui.property;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import org.jspecify.annotations.NullMarked;

/**
 * The catalog keys a {@link ListProperty}'s sub-menu raises: the menu title, the per-entry name (with an
 * {@code {entry}} placeholder) and its action-hint lore, the add button name and its anvil prompt, the edit
 * anvil prompt (also {@code {entry}}), the remove confirm title, and the back button name. Bundling them keeps
 * the {@link ListProperty} constructor short and lets a module declare its list-editor wording in one place,
 * every string still resolving through the message catalog (never an inline literal).
 *
 * @param title the sub-menu title
 * @param entryName the per-entry button name; carries an {@code {entry}} placeholder
 * @param entryHints the per-entry lore describing the click actions (edit / move / remove)
 * @param addName the add button name
 * @param addPrompt the anvil hint shown when adding a new entry
 * @param editPrompt the anvil hint shown when editing an entry; carries an {@code {entry}} placeholder
 * @param removeConfirm the confirm-menu title shown before removing an entry
 * @param backName the back button name
 */
@NullMarked
public record ListPropertyText(
        MessageKey title,
        MessageKey entryName,
        MessageKey entryHints,
        MessageKey addName,
        MessageKey addPrompt,
        MessageKey editPrompt,
        MessageKey removeConfirm,
        MessageKey backName) {

    public ListPropertyText {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(entryName, "entryName");
        Objects.requireNonNull(entryHints, "entryHints");
        Objects.requireNonNull(addName, "addName");
        Objects.requireNonNull(addPrompt, "addPrompt");
        Objects.requireNonNull(editPrompt, "editPrompt");
        Objects.requireNonNull(removeConfirm, "removeConfirm");
        Objects.requireNonNull(backName, "backName");
    }
}
