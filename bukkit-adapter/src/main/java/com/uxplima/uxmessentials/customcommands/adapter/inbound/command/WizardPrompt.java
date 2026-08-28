package com.uxplima.uxmessentials.customcommands.adapter.inbound.command;

import java.util.function.Consumer;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * One question and its two answers, which is all the create wizard needs from the shared text-input seam. It is
 * the shape of {@code TextInput.prompt}, named so the wizard depends on the question rather than on the machinery
 * that asks it: the wiring passes the real seam as a method reference, and a test scripts the answers directly.
 */
@FunctionalInterface
@NullMarked
public interface WizardPrompt {

    /** Ask {@code request} of {@code player}, then run exactly one of the two callbacks with their answer. */
    void ask(Player player, PlayerRef viewer, InputRequest request, Consumer<String> onSubmit, Runnable onCancel);
}
