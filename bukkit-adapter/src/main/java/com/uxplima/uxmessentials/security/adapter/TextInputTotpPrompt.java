package com.uxplima.uxmessentials.security.adapter;

import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.security.domain.SecurityMessageKey;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The production {@link TotpPrompt}: it asks the {@link SecureEntryInput} seam to capture the player's authenticator
 * code through whichever backend the operator configured for the {@code security.verify-totp} input point (anvil by
 * default, or sign / dialog; chat is refused, because a code typed into chat can reach chat loggers). The seam hops the
 * callbacks onto the player's region thread, so the controller's submit and cancel handlers run where they can safely
 * touch the player. The typed code is normalised the way a typed PIN is, so fullwidth digits read as the ones meant.
 */
@NullMarked
public final class TextInputTotpPrompt implements TotpPrompt {

    /** The text-input point key the operator config keys this prompt's mode off. */
    private static final String INPUT_KEY = "security.verify-totp";

    private final SecureEntryInput input;

    public TextInputTotpPrompt(SecureEntryInput input) {
        this.input = Objects.requireNonNull(input, "input");
    }

    @Override
    public void prompt(Player player, PlayerRef viewer, Consumer<String> onSubmit, Runnable onCancel) {
        Objects.requireNonNull(onSubmit, "onSubmit");
        input.prompt(
                player,
                viewer,
                INPUT_KEY,
                SecurityMessageKey.SECURITY_VERIFY_TOTP_PROMPT.key(),
                typed -> onSubmit.accept(SecureEntryInput.normalise(typed)),
                onCancel,
                () -> true);
    }
}
