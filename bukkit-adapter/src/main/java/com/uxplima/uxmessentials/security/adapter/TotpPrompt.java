package com.uxplima.uxmessentials.security.adapter;

import java.util.function.Consumer;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The narrow seam the verification controller uses to ask a frozen player to type an authenticator code, decoupling it
 * from the concrete text-input machinery. The production implementation ({@link TextInputTotpPrompt}) drives the shared
 * anvil/chat {@code TextInput}; a test passes a fake that resolves the callback synchronously.
 */
@NullMarked
public interface TotpPrompt {

    /** Prompt {@code player} for a code; exactly one of {@code onSubmit} (with the typed line) or {@code onCancel} runs. */
    void prompt(Player player, PlayerRef viewer, Consumer<String> onSubmit, Runnable onCancel);
}
