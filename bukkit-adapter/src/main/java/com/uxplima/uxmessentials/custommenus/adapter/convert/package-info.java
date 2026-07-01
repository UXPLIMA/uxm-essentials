/**
 * Converters that turn a competitor menu plugin's config into a uxmEssentials HOCON menu spec, so an operator
 * migrating off that plugin keeps their menus. The first (and, for now, only) converter reads DeluxeMenus menu YAML:
 * {@link com.uxplima.uxmessentials.custommenus.adapter.convert.DeluxeMenusConverter} does the pure config-to-config
 * mapping, and {@link com.uxplima.uxmessentials.custommenus.adapter.convert.DeluxeMenusConvertService} is the
 * file-facing one-shot behind {@code /menu convert deluxemenus <path>}. Both are Configurate-only and touch no engine
 * internals — a converted spec re-enters the engine through the ordinary {@code menus/} loader on the next reload.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.custommenus.adapter.convert;
