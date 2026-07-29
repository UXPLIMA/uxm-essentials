package com.uxplima.uxmessentials.shared.adapter.outbound.integration;

import java.util.Objects;

import org.jspecify.annotations.NullMarked;

/**
 * One third-party plugin uxmEssentials integrates with, as a fact rather than as wiring: which plugin, what it
 * is for, which production class owns its present-guard, and what an operator gets by installing it.
 *
 * @param plugin the Bukkit plugin name, spelled exactly as {@code paper-plugin.yml} declares it and exactly as
 *     the present-guard asks the plugin manager for it (plugin names are case-sensitive, and {@code dynmap},
 *     {@code squaremap} and {@code floodgate} really are lower case)
 * @param family what the integration is for, used to group the doctor report
 * @param seam the simple file name of the one production class that owns the present-guard, which is the class
 *     to open when the integration misbehaves
 * @param purpose one operator-facing clause naming what installing the plugin turns on
 */
@NullMarked
public record Integration(String plugin, IntegrationFamily family, String seam, String purpose) {

    public Integration {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(seam, "seam");
        Objects.requireNonNull(purpose, "purpose");
    }
}
