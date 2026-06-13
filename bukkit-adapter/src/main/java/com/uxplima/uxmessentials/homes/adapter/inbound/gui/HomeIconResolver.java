package com.uxplima.uxmessentials.homes.adapter.inbound.gui;

import java.util.Optional;

import org.bukkit.Material;

import com.uxplima.uxmessentials.homes.domain.HomeIcon;
import org.jspecify.annotations.NullMarked;

/**
 * Resolves a home's optional {@link HomeIcon} (an UPPER_SNAKE material name kept platform-free in the domain)
 * to a Bukkit {@link Material} for rendering, falling back to a supplied default when the home has no custom
 * icon or names a material this server does not know. The {@link Material#matchMaterial} parse is the only
 * platform lookup; it happens once per icon render, never on a domain path.
 */
@NullMarked
final class HomeIconResolver {

    private HomeIconResolver() {}

    /** The custom icon's material, or {@code fallback} when none is set or the name does not resolve. */
    static Material resolve(Optional<HomeIcon> icon, Material fallback) {
        if (icon.isEmpty()) {
            return fallback;
        }
        Material matched = Material.matchMaterial(icon.get().materialName());
        return matched != null && !matched.isAir() ? matched : fallback;
    }
}
