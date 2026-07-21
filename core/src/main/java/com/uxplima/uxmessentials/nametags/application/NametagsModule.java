package com.uxplima.uxmessentials.nametags.application;

import com.uxplima.uxmessentials.shared.application.module.AbstractHudModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.jspecify.annotations.NullMarked;

/**
 * The nametags bounded context as a first-class feature module: a per-player, above-head nametag rendered from
 * operator-authored MiniMessage content under {@code modules/nametags/config.conf} through the placeholder pipeline.
 * The renderer sends a per-viewer packet text-display stack riding each wearer; that Bukkit-facing machinery lands
 * with the adapter wiring.
 *
 * <p><b>Ships enabled by default.</b> The bundled config ships a single plain-name format and the vanilla above-head
 * name is hidden under it through a shared scoreboard-team coordinator, so out of the box every wearer shows one clean
 * custom nametag; an operator edits the format or flips {@code modules.nametags.enabled = false} to turn it off.
 *
 * <p>The nametag is always-on for every eligible wearer when enabled, so this module supplies only its id: the shared
 * enable gate, the empty command/listener/migration surfaces, and the running/drain lifecycle come from
 * {@link AbstractHudModule}.
 */
@NullMarked
public final class NametagsModule extends AbstractHudModule {

    private static final ModuleId ID = ModuleId.of("nametags");

    @Override
    public ModuleId id() {
        return ID;
    }
}
