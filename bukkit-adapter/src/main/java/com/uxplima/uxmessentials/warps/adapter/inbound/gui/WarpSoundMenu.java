package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecException;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.RefreshSpec;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpSoundSelectorView.SoundOption;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import org.jspecify.annotations.NullMarked;

/**
 * Registers the warp sound selector with the menu engine and opens it. This is the pilot proving the engine
 * end-to-end on real domain code, and the shape every feature menu follows when Phase 3 migrates the rest: a small
 * class that registers a list source, the placeholders its entries need, and the click actions, then loads the
 * {@code warp-sounds} spec and hands it to {@link Menus}.
 *
 * <p>The selector serves a server warp; the menu opens with a {@link WarpSoundEdit} subject carrying the warp and
 * whether the click sets its departure or arrival sound, so the single spec covers both editor buttons. The option
 * grid is the same preset list the original fixed view drew, so a player sees an identical menu — only the
 * machinery behind it changed.
 */
@NullMarked
public final class WarpSoundMenu {

    /** The engine spec id this menu registers and opens under. */
    static final String SPEC_ID = "warp-sounds";

    /** Disk-first then bundled, mirroring the GUI-layout loader, so an operator edit to the spec takes effect. */
    private static final String SPEC_RESOURCE = "modules/menu/specs/warp-sounds.conf";

    private final Menus menus;
    private final WarpSoundSelectorView optionSource;
    private final EditableWarpLoader loader;
    private final WarpEditorView editorView;
    private final TextInput textInput;

    private WarpSoundMenu(
            Menus menus,
            WarpSoundSelectorView optionSource,
            EditableWarpLoader loader,
            WarpEditorView editorView,
            TextInput textInput) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.optionSource = Objects.requireNonNull(optionSource, "optionSource");
        this.loader = Objects.requireNonNull(loader, "loader");
        this.editorView = Objects.requireNonNull(editorView, "editorView");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
    }

    /**
     * Build the sound menu over the warps wiring's collaborators. The editable-warp loader is built here from the
     * server-warp repository and the editor view (the same pair the editor listener loads through), so the warps
     * wiring needs only the public collaborators it already holds.
     */
    public static WarpSoundMenu create(
            Menus menus,
            WarpSoundSelectorView optionSource,
            WarpRepository repository,
            WarpEditorView editorView,
            TextInput textInput) {
        EditableWarpLoader loader = new EditableWarpLoader(repository, editorView);
        return new WarpSoundMenu(menus, optionSource, loader, editorView, textInput);
    }

    /** Register the bindings the spec names and the spec itself; called once at warps wiring time. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list("warp:sound-options", ctx -> optionSource.getOptions());
        bindings.placeholder("sound", ctx -> ctx.entry(SoundOption.class).displayName());
        bindings.placeholder(
                "sound_material", ctx -> ctx.entry(SoundOption.class).material().name());
        bindings.action("warp:set-sound", this::setSound);
        bindings.action("warp:custom-sound", this::customSound);
        bindings.action("warp:remove-sound", this::removeSound);
        bindings.action("warp:edit-back", this::back);
        menus.registerSpec(SPEC_ID, loadSpec(dataFolder, log));
    }

    /** Open the sound selector for a server warp on the side the {@code edit} subject names. */
    public void open(PlayerRef viewer, WarpSoundEdit edit) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(edit, "edit");
        menus.open(viewer, SPEC_ID, edit);
    }

    /** Set the clicked preset sound on the warp's departure or arrival side, then reopen the editor. */
    private void setSound(MenuActionContext ctx) {
        SoundOption option = ctx.entry(SoundOption.class);
        saveSound(ctx, Optional.of(option.soundName()));
        reopenEditor(ctx);
    }

    /** Prompt for a custom sound name through the shared text-input seam, exactly as the old custom button did. */
    private void customSound(MenuActionContext ctx) {
        WarpSoundEdit edit = ctx.subject(WarpSoundEdit.class);
        Player player = ctx.player();
        PlayerRef viewer = ctx.viewer();
        String name = edit.warp().value();
        WarpsMessageKey promptKey = edit.departure()
                ? WarpsMessageKey.WARP_EDITOR_SOUND_DEPARTURE_PROMPT
                : WarpsMessageKey.WARP_EDITOR_SOUND_ARRIVAL_PROMPT;
        player.closeInventory();
        textInput.prompt(
                player,
                viewer,
                InputRequest.of("warp.sound", promptKey),
                input -> {
                    save(edit, Optional.of(input.toLowerCase(Locale.ROOT)));
                    editorView.open(player, viewer, name, null);
                },
                () -> open(viewer, edit));
    }

    /** Clear the warp's sound on the subject's side, then reopen the editor. */
    private void removeSound(MenuActionContext ctx) {
        saveSound(ctx, Optional.empty());
        reopenEditor(ctx);
    }

    /** The back button: reopen the warp editor for this warp. */
    private void back(MenuActionContext ctx) {
        reopenEditor(ctx);
    }

    private void saveSound(MenuActionContext ctx, Optional<String> sound) {
        save(ctx.subject(WarpSoundEdit.class), sound);
    }

    private void save(WarpSoundEdit edit, Optional<String> sound) {
        EditableWarp warp = loader.load(edit.warp().value(), null);
        if (warp == null) {
            return;
        }
        if (edit.departure()) {
            warp.setDepartureSound(sound);
        } else {
            warp.setArrivalSound(sound);
        }
    }

    private void reopenEditor(MenuActionContext ctx) {
        editorView.open(
                ctx.player(),
                ctx.viewer(),
                ctx.subject(WarpSoundEdit.class).warp().value(),
                null);
    }

    /**
     * Load the spec, preferring an operator's edit on disk over the bundled resource and finally a built-in empty
     * fallback, so a typo or a missing file degrades to a closeable empty window rather than aborting warps wiring.
     * Resolution mirrors {@code GuiLayouts}: disk first, then the classpath default.
     */
    private MenuSpec loadSpec(Path dataFolder, Logger log) {
        MenuSpecLoader specLoader = new MenuSpecLoader();
        Path onDisk = dataFolder.resolve(SPEC_RESOURCE);
        if (Files.isRegularFile(onDisk)) {
            try {
                return specLoader.load(onDisk);
            } catch (MenuSpecException malformed) {
                log.error("failed to load menu spec " + onDisk + ", using bundled default", malformed);
            }
        }
        return loadBundledSpec(specLoader, log);
    }

    private MenuSpec loadBundledSpec(MenuSpecLoader specLoader, Logger log) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(SPEC_RESOURCE)) {
            if (in == null) {
                log.warn("bundled menu spec {} is missing from the jar", SPEC_RESOURCE);
                return emptySpec();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return specLoader.parse(reader.lines().collect(Collectors.joining("\n")));
            }
        } catch (IOException | MenuSpecException failure) {
            log.error("could not read bundled menu spec " + SPEC_RESOURCE, failure);
            return emptySpec();
        }
    }

    /** A minimal valid spec used only when the real one cannot be read, so warps still wires cleanly. */
    private static MenuSpec emptySpec() {
        return new MenuSpec("", 3, new RefreshSpec(false, 0), List.of(), List.of(), List.of(), Map.of());
    }
}
