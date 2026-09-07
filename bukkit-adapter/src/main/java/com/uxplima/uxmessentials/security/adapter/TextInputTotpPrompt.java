package com.uxplima.uxmessentials.security.adapter;

import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.security.application.SecurityMessageKey;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.gui.input.InputRequest;
import com.uxplima.uxmlib.gui.input.TextInput;
import org.jspecify.annotations.NullMarked;

/**
 * The production {@link TotpPrompt}: it asks the shared {@link TextInput} seam to capture the player's authenticator
 * code through whichever backend the operator configured for the {@code security.verify-totp} input point (anvil by
 * default, or chat / sign / dialog). The seam already hops the callbacks onto the player's region thread, so the
 * controller's submit and cancel handlers run where they can safely touch the player.
 */
@NullMarked
public final class TextInputTotpPrompt implements TotpPrompt {

    /** The text-input point key the operator config keys this prompt's anvil/chat mode off. */
    private static final String INPUT_KEY = "security.verify-totp";

    private final TextInput textInput;

    public TextInputTotpPrompt(TextInput textInput) {
        this.textInput = Objects.requireNonNull(textInput, "textInput");
    }

    @Override
    public void prompt(Player player, PlayerRef viewer, Consumer<String> onSubmit, Runnable onCancel) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(onSubmit, "onSubmit");
        Objects.requireNonNull(onCancel, "onCancel");
        textInput.prompt(
                player,
                InputRequest.of(INPUT_KEY, SecurityMessageKey.SECURITY_VERIFY_TOTP_PROMPT.key()),
                onSubmit,
                onCancel);
    }
}
