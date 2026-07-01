package com.uxplima.uxmessentials.shared.adapter.inbound.gui.input;

import java.nio.file.Path;
import java.util.Objects;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.bedrock.BedrockDetector;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.bedrock.BedrockScreen;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmlib.gui.anvil.AnvilInput;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the text-input seam once at bootstrap: loads {@link InputSettings} from {@code text-input.conf}, wraps the
 * already-installed shared {@link AnvilInput} as the anvil backend, installs the single shared chat backend, and hands
 * back the {@link TextInput} every GUI-using context shares. The chat listener is registered here and torn down through
 * {@link Installed#uninstall()}, so on disable/reload exactly one listener comes and goes — mirroring how the anvil
 * input and the menu listener are installed once in {@code PluginModule}.
 */
@NullMarked
public final class TextInputInstaller {

    private TextInputInstaller() {}

    /**
     * Wire the seam with no Bedrock redirect: every viewer gets the anvil/chat prompt. Kept so tests that only need a
     * working seam stay a delegating call.
     */
    public static Installed install(
            Plugin plugin, Path dataFolder, AnvilInput anvil, GuiText guiText, Scheduler scheduler, Logger log) {
        return install(plugin, dataFolder, anvil, guiText, scheduler, log, BedrockDetector.NONE, BedrockScreen.NONE);
    }

    /**
     * Wire the seam.
     *
     * @param plugin the plugin the chat listener registers against
     * @param dataFolder the data folder; the input config is read from {@code text-input.conf} under it
     * @param anvil the shared, already-installed anvil input the anvil backend delegates to
     * @param guiText the catalog-to-component resolver the prompt labels go through
     * @param scheduler the Folia scheduler the seam hops callbacks onto the viewer's region with
     * @param log the operator logger the config codec warns through
     * @param bedrock the resolved detector; a Bedrock viewer gets a Cumulus CustomForm instead of the anvil/chat prompt
     * @param bedrockScreen the resolved screen the CustomForm is sent through; {@code NONE} on a Java-only server
     */
    public static Installed install(
            Plugin plugin,
            Path dataFolder,
            AnvilInput anvil,
            GuiText guiText,
            Scheduler scheduler,
            Logger log,
            BedrockDetector bedrock,
            BedrockScreen bedrockScreen) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(anvil, "anvil");
        Objects.requireNonNull(guiText, "guiText");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(bedrock, "bedrock");
        Objects.requireNonNull(bedrockScreen, "bedrockScreen");
        InputSettings settings = new InputSettings(dataFolder.resolve("text-input.conf"), log);
        AnvilTextBackend anvilBackend = new AnvilTextBackend(anvil);
        ChatTextBackend chatBackend = new ChatTextBackend(plugin);
        chatBackend.install();
        TextInput textInput =
                new TextInput(settings, guiText, scheduler, anvilBackend, chatBackend, bedrock, bedrockScreen);
        return new Installed(textInput, settings, chatBackend::uninstall);
    }

    /** The wired seam plus its teardown hook. {@code settings} is exposed so a reload can re-read the config. */
    public record Installed(TextInput textInput, InputSettings settings, Runnable uninstall) {

        public Installed {
            Objects.requireNonNull(textInput, "textInput");
            Objects.requireNonNull(settings, "settings");
            Objects.requireNonNull(uninstall, "uninstall");
        }
    }
}
