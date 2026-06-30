package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.outbound.hooks.HeadQuery;

/**
 * The ordered chain of {@link IconProvider}s the renderer consults before falling back to a plain material.
 * {@link #resolve} hands the spec to each provider in turn and returns the first non-empty icon; an empty
 * result from every provider means no provider claimed the spec, so the renderer treats it as a material name.
 *
 * <p>Two shapes are wired at the composition root. {@link #defaults()} carries only the providers that need no
 * external plugin — skull sources and viewer equipment — and is what the plain {@code ItemRenderer(GuiText,
 * PlaceholderRegistry)} constructor uses, so every existing call site gains skulls and equipment for free.
 * {@link #withHeadDatabase(HeadQuery)} additionally chains the HeadDatabase provider, built from the Phase-0
 * {@link HeadQuery} hook; bootstrap uses it so {@code hdb:<id>} resolves when HeadDatabase is installed and
 * degrades to a plain head (the material fallback) when it is not.
 */
public final class IconProviders {

    private final List<IconProvider> providers;

    /**
     * The chain in priority order. Constructed directly only by the factories below and by integrations that
     * extend the default set with their own provider; the list is defensively copied so the chain is immutable
     * once built.
     */
    public IconProviders(List<IconProvider> providers) {
        this.providers = List.copyOf(Objects.requireNonNull(providers, "providers"));
    }

    /** Skull sources plus viewer equipment — the providers that need no external plugin. */
    public static IconProviders defaults() {
        return new IconProviders(List.of(new SkullIconProvider(), new EquipmentIconProvider()));
    }

    /**
     * The {@link #defaults()} chain plus the HeadDatabase provider over {@code headQuery}. When HeadDatabase is
     * absent the query is the no-op {@link HeadQuery#ABSENT}, so {@code hdb:<id>} resolves to empty and falls
     * through to the material fallback (a plain head/stone) rather than failing the render.
     */
    public static IconProviders withHeadDatabase(HeadQuery headQuery) {
        Objects.requireNonNull(headQuery, "headQuery");
        return new IconProviders(
                List.of(new SkullIconProvider(), new EquipmentIconProvider(), new HeadDatabaseIconProvider(headQuery)));
    }

    /**
     * The first icon any provider returns for {@code spec}, or {@link Optional#empty()} when no provider claims
     * it — in which case the renderer reads {@code spec} as a material name. The spec is the material string
     * with its {@code %placeholder%} already resolved, so {@code %head%} → {@code skull:Notch} reaches the
     * skull provider here.
     */
    public Optional<ItemStack> resolve(String spec, MenuContext ctx) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(ctx, "ctx");
        for (IconProvider provider : providers) {
            Optional<ItemStack> icon = provider.icon(spec, ctx);
            if (icon.isPresent()) {
                return icon;
            }
        }
        return Optional.empty();
    }
}
