/**
 * The nametags context's outbound adapters: the per-wearer above-head presenter over uxmLib's packet nametag renderer
 * ({@link com.uxplima.uxmessentials.nametags.adapter.outbound.PacketNametagPresenter}), the self-rescheduling
 * reconcile/animation timer on the {@code Scheduler} port
 * ({@link com.uxplima.uxmessentials.nametags.adapter.outbound.NametagRenderTask}), the appearance mapper that turns the
 * core {@code NametagAppearance} into uxmLib's packet {@code Appearance}
 * ({@link com.uxplima.uxmessentials.nametags.adapter.outbound.NametagAppearanceMapper}), and the {@code canSee}-based
 * vanish gate ({@link com.uxplima.uxmessentials.nametags.adapter.outbound.CanSeeNametagVanish}).
 */
@NullMarked
package com.uxplima.uxmessentials.nametags.adapter.outbound;

import org.jspecify.annotations.NullMarked;
