/**
 * Shared HUD rendering helpers reused across the display contexts: {@link com.uxplima.uxmessentials.shared.adapter.outbound.hud.HudText}
 * lifts the per-viewer PlaceholderAPI bridge plus MiniMessage parse so the scoreboard sidebar and the tablist
 * header/footer render operator content through the identical transform, and {@link com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationRegistry}
 * (built from {@link com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationDef}s parsed by the shared
 * {@code AnimationDef.parseAll}) expands {@code %anim_<name>%} tokens to the current animation frame for both modules,
 * so the animation catalog and its config grammar live in one place.
 */
@NullMarked
package com.uxplima.uxmessentials.shared.adapter.outbound.hud;

import org.jspecify.annotations.NullMarked;
