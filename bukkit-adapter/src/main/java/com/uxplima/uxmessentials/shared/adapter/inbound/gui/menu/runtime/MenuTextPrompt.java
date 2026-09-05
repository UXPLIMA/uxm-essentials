package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime;

import java.util.function.Consumer;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The narrow capability the menu engine's {@code input:} step uses to capture a line of text without depending on the
 * concrete text-input seam: it hands over the viewer, an input-point key (the operator's per-key anvil/chat/sign mode
 * is looked up by it), a resolved prompt label and an optional pre-fill, and gets exactly one callback back — the typed
 * line on submit, or {@code onCancel}. Production wires it over {@code TextInput.promptResolved}; a test wires a
 * synchronous fake. Defined here so {@code MenuListener} stays decoupled from the input package and testable without
 * constructing the whole seam.
 *
 * <p>The viewer is named once. The seam underneath addresses them both as a {@link Player} and as a
 * {@link PlayerRef}, but the ref is the player's own uuid and name, so a caller that passed both could only ever
 * pass one person twice, or pass two and send the prompt to one while running the callbacks on the other's entity
 * thread. The implementation derives the ref.
 *
 * <p>The implementation is responsible for delivering both callbacks on the viewer's entity thread, exactly as the
 * text-input seam already does, so the engine's continuation runs where it can safely touch the player and reopen a
 * menu.
 */
@NullMarked
public interface MenuTextPrompt {

    void prompt(
            Player player,
            String key,
            Component prompt,
            @Nullable String initialText,
            Consumer<String> onSubmit,
            Runnable onCancel);
}
