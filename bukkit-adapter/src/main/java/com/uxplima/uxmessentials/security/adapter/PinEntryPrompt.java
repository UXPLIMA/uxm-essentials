package com.uxplima.uxmessentials.security.adapter;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * How a player who cannot use the keypad window enters their PIN: by typing it into one prompt.
 *
 * <p>The keypad is a chest that redraws itself after every tap. A Bedrock player is shown a menu as a native form,
 * and a form is sent once and gone after one button, so a pad that relies on redrawing closes after the first digit
 * and the player is left frozen with nothing to press. Typing the whole entry into a text field is what a Bedrock
 * client does well, so those players get that instead, through the same text-input seam the authenticator prompt
 * already uses.
 */
@NullMarked
public interface PinEntryPrompt {

    /** Which entry the prompt asks for, so the player reads the right question. */
    enum Step {
        /** A PIN or an authenticator code, to prove the account at join or before a protected command. */
        VERIFY,
        /** The first entry of a new PIN. */
        CREATE,
        /** The same new PIN again, to catch a typing mistake before it is stored. */
        CONFIRM
    }

    /** Whether {@code player} types their entry here rather than tapping the keypad window. */
    boolean typesEntry(Player player);

    /**
     * Ask {@code player} for one entry. Exactly one of {@code onSubmit} (with the typed line) or {@code onCancel} runs,
     * on the player's own region thread. {@code current} says whether the prompt is still the one the player is meant
     * to answer, so a prompt taken away on purpose ends without telling the player they cancelled it.
     */
    void prompt(
            Player player,
            PlayerRef viewer,
            Step step,
            Consumer<String> onSubmit,
            Runnable onCancel,
            BooleanSupplier current);
}
