package com.uxplima.uxmessentials.shared.adapter.inbound.gui.input;

import java.nio.file.Path;
import java.util.Objects;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmlib.gui.anvil.AnvilInput;

/**
 * Test helper that assembles a real {@link TextInput} for unit tests, living in the seam's package so it can reach the
 * package-private backends and {@link InputSettings}. Most view tests only drive a migrated site's package-private
 * apply seam (e.g. {@code applyInput}, {@code addByName}, {@code handleRenameInput}) and never open a live prompt, so
 * they just need a non-null, wired {@link TextInput}; this builds one over a real (but un-installed) anvil and a chat
 * backend, reading whatever config sits under {@code inputDir} (an absent file yields the anvil-everywhere default).
 *
 * <p>{@link #create} does NOT register the chat listener, so it is safe for tests that only need a non-null seam to
 * hand to a view (including tests that mock the {@link Plugin} without booting MockBukkit). A test that fires an
 * {@code AsyncChatEvent} to complete a chat-mode prompt must use {@link #install} instead, which registers the chat
 * backend so the event routes through it; let MockBukkit unmock drop the listener, or call the returned teardown hook.
 */
public final class TextInputTestKit {

    private TextInputTestKit() {}

    /**
     * Build a {@link TextInput} WITHOUT installing the chat listener, reading config from {@code inputDir/config.conf}
     * (absent → anvil default). Use this when a test only drives a migrated site's package-private apply seam and never
     * fires a chat event; it needs no live server, so a {@code mock(Plugin.class)} is fine.
     */
    public static TextInput create(Plugin plugin, GuiText guiText, Scheduler scheduler, Path inputDir, Logger log) {
        return build(plugin, guiText, scheduler, inputDir, log).textInput();
    }

    /**
     * As {@link #create}, but registers the chat backend as a listener (requires a live MockBukkit server) and returns
     * the seam plus its teardown hook, so a test that fires an {@code AsyncChatEvent} routes through the chat backend.
     */
    public static Installed install(Plugin plugin, GuiText guiText, Scheduler scheduler, Path inputDir, Logger log) {
        Installed built = build(plugin, guiText, scheduler, inputDir, log);
        built.chatBackend().install();
        return built;
    }

    private static Installed build(Plugin plugin, GuiText guiText, Scheduler scheduler, Path inputDir, Logger log) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(guiText, "guiText");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(inputDir, "inputDir");
        Objects.requireNonNull(log, "log");
        InputSettings settings = new InputSettings(inputDir.resolve("text-input.conf"), log);
        AnvilTextBackend anvilBackend = new AnvilTextBackend(new AnvilInput(plugin));
        ChatTextBackend chatBackend = new ChatTextBackend(plugin);
        TextInput textInput = new TextInput(settings, guiText, scheduler, anvilBackend, chatBackend, log);
        return new Installed(textInput, chatBackend);
    }

    /** The wired seam plus its chat backend (whose {@code uninstall} unregisters the listener). */
    public record Installed(TextInput textInput, ChatTextBackend chatBackend) {
        public Installed {
            Objects.requireNonNull(textInput, "textInput");
            Objects.requireNonNull(chatBackend, "chatBackend");
        }

        public Runnable uninstall() {
            return chatBackend::uninstall;
        }
    }
}
