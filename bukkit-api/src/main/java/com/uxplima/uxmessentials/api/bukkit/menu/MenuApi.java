package com.uxplima.uxmessentials.api.bukkit.menu;

import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import org.jspecify.annotations.NullMarked;

/**
 * Teaches the uxmEssentials menu engine new vocabulary, so operators can write your plugin's ids in their own menu
 * files. Obtain it from the front door:
 *
 * <pre>{@code
 * UxmEssentialsApi.whenReady(api -> api.menus().registerAction("my-award", click -> click.player().giveExp(100)));
 * }</pre>
 *
 * <p>Once registered, an id becomes usable in any menu spec: {@link #registerAction} adds an action a
 * {@code click { left { click = ["my-award"] } }} or an open-action can fire (a custom button is simply a menu item
 * whose click runs a registered action, so this one method covers buttons); {@link #registerRequirement} adds a
 * {@code view} or {@code click} condition written {@code my-cond:<value>}; {@link #registerPlaceholder} adds a
 * {@code %my-token%} the renderer expands; {@link #registerListSource} adds a {@code list { source = my-src }}; and
 * {@link #registerIconProvider} adds a {@code material = "myplugin:<id>"} prefix.
 *
 * <h2>Register once, on your enable</h2>
 * Each id is registered exactly once: a duplicate throws {@link IllegalStateException} so a wiring mistake fails
 * loudly rather than letting a second handler silently win. Registration is thread-safe against an already-running
 * engine, so a plugin that enables after uxmEssentials is still seen.
 *
 * <h2>Load or reload the menu after registering</h2>
 * A menu spec naming a custom id is validated when it loads, and an unknown id is a loud load-time failure rather
 * than a broken menu a player meets. Register your ids before the menu that uses them loads. A menu already on disk
 * when your plugin enables must therefore be re-validated afterwards: run {@code /uxmess reload} or
 * {@code /menu reload} once your registrations are in place.
 *
 * <h2>Threading</h2>
 * Action and requirement handlers run on the viewer's own region thread, which on Folia is that entity's region
 * rather than one main thread, so they may act on the viewing player inline. A list source is queried off the tick
 * thread and must not touch the Bukkit API at all.
 */
@NullMarked
public interface MenuApi {

    /**
     * Register a custom action under {@code id}. A menu spec fires it by naming the id in a click gesture's action
     * list ({@code left { click = ["my-award"] }}) or in a menu's open or close actions; an {@code id:value} form
     * arrives on the handler as {@link MenuClick#arg()}.
     *
     * @throws IllegalStateException if an action is already registered under {@code id}
     */
    void registerAction(String id, Consumer<MenuClick> handler);

    /**
     * Register a custom requirement under {@code id}. A menu spec gates an item's {@code view} block or a click on
     * it by naming the id, optionally valued as {@code id:<value>}; the value and any other tokens arrive as the
     * handler's map argument. Return {@code true} to pass the gate.
     *
     * @throws IllegalStateException if a requirement is already registered under {@code id}
     */
    void registerRequirement(String id, BiPredicate<MenuView, Map<String, String>> handler);

    /**
     * Register a custom placeholder under {@code id}. The renderer expands {@code %id%} in a menu title, item name
     * or lore line to the string the handler returns for the current view. Return an empty string to render nothing.
     *
     * @throws IllegalStateException if a placeholder is already registered under {@code id}
     */
    void registerPlaceholder(String id, Function<MenuView, String> handler);

    /**
     * Register a custom list source under {@code id}. A menu spec pages over it with {@code list { source = id }},
     * and the engine renders one templated item per element the handler returns. Read your elements back in a
     * placeholder or action through {@link MenuView#entry(Class)}.
     *
     * <p>The handler is called off the tick thread, so it must not touch the Bukkit API.
     *
     * @throws IllegalStateException if a list source is already registered under {@code id}
     */
    void registerListSource(String id, Function<MenuView, List<?>> handler);

    /**
     * Register a custom icon source, so a menu item written {@code material = "myplugin:foo"} renders through your
     * plugin. Providers are consulted after every built-in one, so yours adds a prefix and can never shadow the
     * engine's own resolution.
     */
    void registerIconProvider(MenuIconProvider provider);

    /**
     * Render a menu-styled item outside a menu: the {@code material} spec resolves through the icon providers, the
     * name and lore expand every {@code %placeholder%} against {@code viewer}, and the result is the stack that
     * player would see in a menu slot. Handy for a hotbar item or a hand-built inventory that should match the
     * menus without re-implementing the pipeline.
     *
     * <p>{@code name} and each lore line accept the same text a menu spec does: a literal, a {@code %placeholder%},
     * or a mix. Pass an empty list for no lore. Placeholders that only make sense inside a menu (a list entry, a
     * page number) resolve as they would on the first page of a menu with no list.
     */
    ItemStack buildItem(String material, String name, List<String> lore, Player viewer);
}
