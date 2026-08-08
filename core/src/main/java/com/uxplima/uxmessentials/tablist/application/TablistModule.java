package com.uxplima.uxmessentials.tablist.application;

import com.uxplima.uxmessentials.shared.application.module.AbstractHudModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.jspecify.annotations.NullMarked;

/**
 * The tablist bounded context as a first-class feature module: a per-player tablist header and footer rendered from
 * operator-authored MiniMessage content under {@code modules/tablist/config.conf} through the placeholder pipeline. It
 * owns the self-rescheduling render timer on the {@code Scheduler} port that re-renders every viewer each configured
 * refresh interval; the render/join/quit machinery is Bukkit-facing and lands with the adapter wiring.
 *
 * <p><b>Ships disabled by default.</b> The tab list is a surface a dedicated tab plugin also draws, and two of them
 * fight over it. A fresh install still bundles an example header/footer authored with the built-in
 * {@code {online}}/{@code {max_players}} tokens (no PlaceholderAPI required), so
 * {@code modules.tablist.enabled = true} shows a working tab straight away, ready to brand.
 *
 * <p>The tablist is always-on for every viewer when enabled, so this module supplies only its id: the shared enable
 * gate, the empty command/listener/migration surfaces, and the running/drain lifecycle come from
 * {@link AbstractHudModule}.
 */
@NullMarked
public final class TablistModule extends AbstractHudModule {

    private static final ModuleId ID = ModuleId.of("tablist");

    @Override
    public ModuleId id() {
        return ID;
    }
}
