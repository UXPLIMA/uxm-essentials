package com.uxplima.uxmessentials.shared.adapter.inbound.window;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import org.bukkit.entity.Player;

import com.uxplima.uxmlib.bedrock.BedrockButton;
import com.uxplima.uxmlib.bedrock.BedrockScreen;
import com.uxplima.uxmlib.bedrock.BedrockWidget;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The menu engine's Bedrock screen, reporting each menu form it sends. A Bedrock player is shown a menu as a form
 * instead of a chest, which raises no inventory open event, so the form going out is the only sign the menu reached
 * them. Only the simple and custom forms a menu is drawn as are reported; the modal and input forms of a prompt are
 * not menus.
 */
@NullMarked
public final class FormReportingScreen implements BedrockScreen {

    private final BedrockScreen screen;
    /** Replaced by a reload of the menus module, read on whichever thread sends a form. */
    private volatile Consumer<Player> listener = player -> {};

    public FormReportingScreen(BedrockScreen screen) {
        this.screen = Objects.requireNonNull(screen, "screen");
    }

    /** Tell {@code sent} of every menu form sent from now on, replacing whoever was told before. */
    public void listen(Consumer<Player> sent) {
        listener = Objects.requireNonNull(sent, "sent");
    }

    @Override
    public boolean available() {
        return screen.available();
    }

    @Override
    public void sendSimpleForm(
            Player player, String title, @Nullable String content, List<BedrockButton> buttons, IntConsumer onClick) {
        screen.sendSimpleForm(player, title, content, buttons, onClick);
        listener.accept(player);
    }

    @Override
    public void sendSimpleForm(
            Player player,
            String title,
            @Nullable String content,
            List<BedrockButton> buttons,
            IntConsumer onClick,
            Runnable onClose) {
        screen.sendSimpleForm(player, title, content, buttons, onClick, onClose);
        listener.accept(player);
    }

    @Override
    public void sendModalForm(
            Player player,
            String title,
            @Nullable String content,
            String yes,
            String no,
            Runnable onYes,
            Runnable onNo) {
        screen.sendModalForm(player, title, content, yes, no, onYes, onNo);
    }

    @Override
    public void sendInputForm(
            Player player,
            String title,
            String label,
            @Nullable String placeholder,
            Consumer<String> onSubmit,
            Runnable onCancel) {
        screen.sendInputForm(player, title, label, placeholder, onSubmit, onCancel);
    }

    @Override
    public void sendCustomForm(
            Player player,
            String title,
            @Nullable String content,
            List<BedrockWidget> widgets,
            Consumer<Map<String, String>> onSubmit,
            Runnable onClose) {
        screen.sendCustomForm(player, title, content, widgets, onSubmit, onClose);
        listener.accept(player);
    }
}
