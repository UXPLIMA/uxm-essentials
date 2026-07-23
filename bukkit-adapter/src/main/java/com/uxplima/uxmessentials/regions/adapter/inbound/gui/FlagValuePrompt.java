package com.uxplima.uxmessentials.regions.adapter.inbound.gui;

import java.util.function.Consumer;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The narrow text-capture capability the flag editor uses to edit a {@code STRING}, {@code INTEGER}, or {@code DOUBLE}
 * flag: it prompts the viewer for a line and runs exactly one of the callbacks on the viewer's entity thread (the typed
 * line on submit, or {@code onCancel}). Production wires it over the shared {@link
 * com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput} seam ({@code textInput::prompt}); a test wires a
 * synchronous fake. Declared here so the view stays decoupled from the concrete input seam and testable without
 * standing up the anvil/chat backends.
 */
@NullMarked
@FunctionalInterface
public interface FlagValuePrompt {

    void prompt(Player player, PlayerRef viewer, InputRequest request, Consumer<String> onSubmit, Runnable onCancel);
}
