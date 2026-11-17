package com.uxplima.uxmessentials.security.adapter;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.security.domain.SecurityMessageKey;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The production {@link PinEntryPrompt}: every Bedrock player types their entry through the {@link SecureEntryInput}
 * seam, and every Java player keeps the keypad.
 *
 * <p>The test is "is this a Bedrock player", not "would a menu reach them as a form". With Floodgate the seam shows
 * them a native input form. With Geyser alone today a menu still reaches them as a chest, but the moment the library
 * learns to send forms through Geyser too that chest becomes a form and the keypad breaks again; asking the detector
 * keeps the PIN usable through that change. Until then the seam falls back to the operator's mode for the input
 * point (anvil, sign or dialog; never chat), each of which a Geyser client can answer.
 */
@NullMarked
public final class TextInputPinPrompt implements PinEntryPrompt {

    /** The input point for proving an existing factor, at join and before a protected command. */
    static final String VERIFY_KEY = "security.verify-pin";

    /** The input point for both entries of a new PIN. */
    static final String CREATE_KEY = "security.create-pin";

    private final SecureEntryInput input;

    public TextInputPinPrompt(SecureEntryInput input) {
        this.input = Objects.requireNonNull(input, "input");
    }

    @Override
    public boolean typesEntry(Player player) {
        return input.isBedrock(player);
    }

    @Override
    public void prompt(
            Player player,
            PlayerRef viewer,
            Step step,
            Consumer<String> onSubmit,
            Runnable onCancel,
            BooleanSupplier current) {
        Objects.requireNonNull(step, "step");
        switch (step) {
            case VERIFY ->
                input.prompt(
                        player,
                        viewer,
                        VERIFY_KEY,
                        SecurityMessageKey.SECURITY_VERIFY_ENTRY_PROMPT.key(),
                        onSubmit,
                        onCancel,
                        current);
            case CREATE ->
                input.prompt(
                        player,
                        viewer,
                        CREATE_KEY,
                        SecurityMessageKey.SECURITY_PIN_CREATE_ENTRY.key(),
                        onSubmit,
                        onCancel,
                        current);
            case CONFIRM ->
                input.prompt(
                        player,
                        viewer,
                        CREATE_KEY,
                        SecurityMessageKey.SECURITY_PIN_CONFIRM_ENTRY.key(),
                        onSubmit,
                        onCancel,
                        current);
        }
    }
}
