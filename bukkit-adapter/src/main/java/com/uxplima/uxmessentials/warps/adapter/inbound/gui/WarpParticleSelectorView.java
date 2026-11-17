package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.Tiles;
import com.uxplima.uxmessentials.shared.application.message.TrustedMarkup;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.message.SharedMessageKey;
import com.uxplima.uxmessentials.warps.domain.WarpsMessageKey;
import com.uxplima.uxmlib.gui.input.InputRequest;
import com.uxplima.uxmlib.gui.input.TextInput;
import com.uxplima.uxmlib.item.ItemBuilder;
import com.uxplima.uxmlib.menu.Menus;
import com.uxplima.uxmlib.menu.property.SelectorButton;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The teleport-particle selector a warp editor opens to pick a warp's departure or arrival particle: a three-row
 * picker with one icon per particle the warps file lists ({@link WarpPresets}), a custom-name button, a back button to
 * the editor, and a remove button. Clicking a particle runs the editor's own set. The pick is saved through the editor's
 * per-warp queue off the tick, then the viewer is returned to the warp editor.
 *
 * <p>The window draws through the menu engine's selector runtime ({@link Menus#openSelector}), so it is a
 * holder-backed engine selector routed and torn down by the one menu listener and one {@code closeMenu}. The
 * selector serves both server and player warps: the edit names the warp and its (nullable) owner, so the single picker covers either kind. Every visible string resolves
 * from the warps catalog, except a particle's name, which the warps file writes per language.
 */
@NullMarked
public final class WarpParticleSelectorView {

    private static final int ROWS = 3;
    private static final int OPTION_LIMIT = 18;
    private static final int CUSTOM_SLOT = 18;
    private static final int BACK_SLOT = 22;
    private static final int REMOVE_SLOT = 26;
    private static final Material FILLER = Material.GRAY_STAINED_GLASS_PANE;

    private final Messages messages;
    private final Menus menus;
    private final WarpEditorView editorView;
    private final TextInput textInput;
    private final WarpPresets presets;

    private WarpParticleSelectorView(
            Messages messages, Menus menus, WarpEditorView editorView, TextInput textInput, WarpPresets presets) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.editorView = Objects.requireNonNull(editorView, "editorView");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.presets = Objects.requireNonNull(presets, "presets");
    }

    /**
     * Build the particle selector over the warps wiring's collaborators. Its saves go through the editor view, which
     * loads the warp the same way the editor does. Mirrors {@code WarpSoundMenu.create}.
     */
    public static WarpParticleSelectorView create(
            Messages messages, Menus menus, WarpEditorView editorView, TextInput textInput, WarpPresets presets) {
        return new WarpParticleSelectorView(messages, menus, editorView, textInput, presets);
    }

    /** Open the particle selector for {@code warpName} (server warp when {@code warpOwner} is null), returning to the editor on pick. */
    public void open(
            Player player, PlayerRef viewer, String warpName, @Nullable PlayerRef warpOwner, boolean isDeparture) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(warpName, "warpName");
        WarpsMessageKey titleKey = isDeparture
                ? WarpsMessageKey.WARP_EDITOR_PARTICLE_SELECTOR_TITLE_DEPARTURE
                : WarpsMessageKey.WARP_EDITOR_PARTICLE_SELECTOR_TITLE_ARRIVAL;
        menus.openSelector(
                player,
                text(viewer, titleKey),
                ROWS,
                FILLER,
                buttons(player, viewer, warpName, warpOwner, isDeparture));
    }

    private List<SelectorButton> buttons(
            Player player, PlayerRef viewer, String name, @Nullable PlayerRef owner, boolean isDeparture) {
        List<SelectorButton> buttons = new ArrayList<>();
        List<ParticleOption> options = getOptions();
        for (int i = 0; i < Math.min(options.size(), OPTION_LIMIT); i++) {
            ParticleOption opt = options.get(i);
            ItemStack icon = ItemBuilder.of(opt.material())
                    .name(Tiles.blankName())
                    .lore(Tiles.titled(
                            text(
                                    viewer,
                                    WarpsMessageKey.WARP_EDITOR_PARTICLE_SELECTOR_ENTRY_NAME,
                                    Map.of("particle", TrustedMarkup.of(nameOf(opt, viewer)))),
                            List.of(text(
                                    viewer,
                                    WarpsMessageKey.WARP_EDITOR_PARTICLE_SELECTOR_ENTRY_LORE,
                                    Map.of("particle", opt.particleName())))))
                    .build();
            buttons.add(SelectorButton.of(
                    i, icon, () -> pick(player, viewer, name, owner, isDeparture, Optional.of(opt.particleName()))));
        }
        buttons.add(SelectorButton.of(
                CUSTOM_SLOT, customIcon(viewer), () -> custom(player, viewer, name, owner, isDeparture)));
        buttons.add(SelectorButton.of(BACK_SLOT, backIcon(viewer), () -> editorView.open(player, viewer, name, owner)));
        buttons.add(SelectorButton.of(
                REMOVE_SLOT,
                removeIcon(viewer),
                () -> pick(player, viewer, name, owner, isDeparture, Optional.empty())));
        return buttons;
    }

    /** Set the chosen particle on the warp's departure or arrival side, then reopen the editor: the old click's effect. */
    private void pick(
            Player player,
            PlayerRef viewer,
            String name,
            @Nullable PlayerRef owner,
            boolean isDeparture,
            Optional<String> particle) {
        editorView.edit(
                name,
                owner,
                player,
                viewer,
                warp -> {
                    if (isDeparture) {
                        warp.setDepartureParticle(particle);
                    } else {
                        warp.setArrivalParticle(particle);
                    }
                },
                () -> editorView.open(player, viewer, name, owner));
    }

    /** Prompt for a custom particle name through the shared input seam. */
    private void custom(Player player, PlayerRef viewer, String name, @Nullable PlayerRef owner, boolean isDeparture) {
        player.closeInventory();
        MessageKey promptKey = isDeparture
                ? WarpsMessageKey.WARP_EDITOR_PARTICLE_DEPARTURE_PROMPT
                : WarpsMessageKey.WARP_EDITOR_PARTICLE_ARRIVAL_PROMPT;
        textInput.prompt(
                player,
                InputRequest.of("warp.particle", promptKey.key()),
                input -> pick(player, viewer, name, owner, isDeparture, Optional.of(input.toUpperCase(Locale.ROOT))),
                () -> open(player, viewer, name, owner, isDeparture));
    }

    private ItemStack customIcon(PlayerRef viewer) {
        return ItemBuilder.of(Material.ANVIL)
                .name(Tiles.blankName())
                .lore(Tiles.titled(
                        text(viewer, WarpsMessageKey.WARP_EDITOR_PARTICLE_SELECTOR_CUSTOM_NAME),
                        List.of(text(viewer, WarpsMessageKey.WARP_EDITOR_PARTICLE_SELECTOR_CUSTOM_LORE))))
                .build();
    }

    private ItemStack backIcon(PlayerRef viewer) {
        return ItemBuilder.of(Material.FEATHER)
                .name(text(viewer, WarpsMessageKey.WARP_EDITOR_SELECTOR_BACK))
                .build();
    }

    private ItemStack removeIcon(PlayerRef viewer) {
        return ItemBuilder.of(Material.LAVA_BUCKET)
                .name(Tiles.blankName())
                .lore(Tiles.titled(
                        text(viewer, WarpsMessageKey.WARP_EDITOR_PARTICLE_SELECTOR_REMOVE_NAME),
                        List.of(text(viewer, WarpsMessageKey.WARP_EDITOR_PARTICLE_SELECTOR_REMOVE_LORE))))
                .build();
    }

    private Component text(PlayerRef viewer, MessageKey key) {
        return StyledText.render(messages.resolve(viewer, key, Map.of()));
    }

    private Component text(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return StyledText.render(messages.resolve(viewer, key, placeholders));
    }

    /** The preset particles the selector grids, as the warps file lists them. */
    public List<ParticleOption> getOptions() {
        return presets.particles().stream().map(ParticleOption::new).toList();
    }

    /**
     * The name {@code viewer} reads for {@code option}, in the language their catalogue is written in. It is markup: a
     * catalogue line, or a name the operator gave the preset.
     */
    public String nameOf(ParticleOption option, PlayerRef viewer) {
        Objects.requireNonNull(option, "option");
        Objects.requireNonNull(viewer, "viewer");
        WarpPresets.Preset preset = option.preset();
        if (preset.namedByLine()) {
            String line = preset.line();
            return messages.resolve(viewer, () -> line, Map.of()).strip();
        }
        return preset.nameIn(
                messages.resolve(viewer, SharedMessageKey.LANG_CODE, Map.of()).strip());
    }

    /** One particle of the grid: the preset it shows. */
    public record ParticleOption(WarpPresets.Preset preset) {

        public ParticleOption {
            Objects.requireNonNull(preset, "preset");
        }

        public String particleName() {
            return preset.effect();
        }

        public Material material() {
            return preset.icon();
        }
    }
}
