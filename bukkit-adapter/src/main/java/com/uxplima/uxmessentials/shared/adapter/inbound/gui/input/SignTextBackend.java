package com.uxplima.uxmessentials.shared.adapter.inbound.gui.input;

import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.gui.input.InputType;
import com.uxplima.uxmlib.gui.input.PlayerInput;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The sign backend of the text-input seam: opens a transient sign through uxmLib's {@link PlayerInput} (the same
 * native, packet-free mechanism its {@code SIGN} backend uses) and reports the typed lines. A sign cannot be
 * pre-seeded from the prompt, so {@code initialText} is ignored here, exactly as the chat backend ignores it. The
 * uxmLib {@link com.uxplima.uxmlib.gui.input.InputResult outcome} maps one-to-one onto {@link InputResult}; the
 * cancel-keyword check and the entity-thread hop live upstream in {@link TextInput}, so this stays a thin adapter.
 */
@NullMarked
final class SignTextBackend implements TextInputBackend {

    private final PlayerInput playerInput;

    SignTextBackend(PlayerInput playerInput) {
        this.playerInput = Objects.requireNonNull(playerInput, "playerInput");
    }

    @Override
    public void open(
            Player player,
            PlayerRef viewer,
            Component prompt,
            @Nullable String initialText,
            Consumer<InputResult> outcome) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(outcome, "outcome");
        playerInput.open(player, InputType.SIGN, prompt, result -> outcome.accept(map(result)));
    }

    private static InputResult map(com.uxplima.uxmlib.gui.input.InputResult result) {
        if (result instanceof com.uxplima.uxmlib.gui.input.InputResult.Submitted submitted) {
            return new InputResult.Submitted(submitted.text());
        }
        return InputResult.Cancelled.INSTANCE;
    }
}
