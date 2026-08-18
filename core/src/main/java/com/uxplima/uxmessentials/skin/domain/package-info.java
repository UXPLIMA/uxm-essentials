/**
 * The skin context's domain: the {@link com.uxplima.uxmessentials.skin.domain.PlayerSkin} a player wears, the
 * {@link com.uxplima.uxmessentials.skin.domain.SkinSource} it was resolved from, the
 * {@link com.uxplima.uxmessentials.skin.domain.SkinModel} it was cut for, and the
 * {@link com.uxplima.uxmessentials.skin.domain.SkinPolicy} that decides whether a player may wear a given skin at
 * all. The texture itself is the shared kernel's {@code SkinTexture}, so one fetch serves every context that
 * dresses something in a player skin. Pure Java: no Bukkit, Paper, Kyori, or SLF4J.
 */
@NullMarked
package com.uxplima.uxmessentials.skin.domain;

import org.jspecify.annotations.NullMarked;
