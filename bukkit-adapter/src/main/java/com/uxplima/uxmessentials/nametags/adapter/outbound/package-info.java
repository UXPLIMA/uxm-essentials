/**
 * The nametags context's outbound adapters: the per-wearer above-head renderer over uxmLib's native-display hologram
 * stack ({@link com.uxplima.uxmessentials.nametags.adapter.outbound.NametagRenderer}), the self-rescheduling render
 * timer on the {@code Scheduler} port
 * ({@link com.uxplima.uxmessentials.nametags.adapter.outbound.NametagRenderTask}), the appearance mapper that turns the
 * core {@code NametagAppearance} into uxmLib's {@code Appearance}/{@code Transform}
 * ({@link com.uxplima.uxmessentials.nametags.adapter.outbound.NametagAppearanceMapper}), and the {@code canSee}-based
 * vanish gate ({@link com.uxplima.uxmessentials.nametags.adapter.outbound.CanSeeNametagVanish}).
 */
@NullMarked
package com.uxplima.uxmessentials.nametags.adapter.outbound;

import org.jspecify.annotations.NullMarked;
