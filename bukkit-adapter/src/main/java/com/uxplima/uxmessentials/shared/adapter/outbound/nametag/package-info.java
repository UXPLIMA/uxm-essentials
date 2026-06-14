/**
 * Shared name-visibility plumbing the nametags and scoreboard contexts both lean on:
 * {@link com.uxplima.uxmessentials.shared.adapter.outbound.nametag.NameVisibilityCoordinator} parks the names of
 * wearers carrying an active custom nametag in a dedicated {@code uxm-namehide} scoreboard team so the vanilla
 * above-head name never renders for them, re-applying after every per-player scoreboard switch. It lives in the shared
 * adapter layer because it touches the Bukkit {@code Team}/{@code Scoreboard} API directly while serving two contexts.
 */
@NullMarked
package com.uxplima.uxmessentials.shared.adapter.outbound.nametag;

import org.jspecify.annotations.NullMarked;
