package com.uxplima.uxmessentials.api.bukkit;

import java.util.Optional;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.api.bukkit.menu.MenuApi;
import com.uxplima.uxmessentials.api.query.UxmEconomyQuery;
import com.uxplima.uxmessentials.api.query.UxmHomesQuery;
import com.uxplima.uxmessentials.api.query.UxmKitsQuery;
import com.uxplima.uxmessentials.api.query.UxmModerationQuery;
import com.uxplima.uxmessentials.api.query.UxmPlayerWarpsQuery;
import com.uxplima.uxmessentials.api.query.UxmVaultsQuery;
import com.uxplima.uxmessentials.api.query.UxmWarpsQuery;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The uxmEssentials developer API, and the one place a consumer starts.
 *
 * <p>There are two ways in, because the two idioms fail differently. When your plugin is guaranteed to enable after
 * uxmEssentials, the Bukkit service registry is enough:
 *
 * <pre>{@code
 * UxmEssentialsApi api = getServer().getServicesManager().load(UxmEssentialsApi.class);
 * if (api == null) {
 *     getLogger().info("uxmEssentials is absent; running without it");
 *     return;
 * }
 * }</pre>
 *
 * <p>When load order is not guaranteed, or you want your registrations restored after uxmEssentials reloads, use
 * the callback form instead. It runs immediately when the API is already up, queues otherwise, and runs again after
 * a reload:
 *
 * <pre>{@code
 * UxmEssentialsApi.whenReady(api -> {
 *     getServer().getPluginManager().registerEvents(new MyListener(), this);
 *     api.menus().registerAction("my-award", click -> click.player().giveExp(100));
 * });
 * }</pre>
 *
 * <h2>Events</h2>
 * Listening needs nothing from this interface: the event classes under {@code com.uxplima.uxmessentials.api.bukkit
 * .event} are ordinary Bukkit events you register a listener for. Everything the plugin does publishes a
 * notification {@code Uxm<Thing><Action>Event}, delivered on the tick thread that owns its subject once the action
 * has happened. The operations that can be refused with nothing half-done also publish a cancellable
 * {@code Uxm<Thing>Pre<Action>Event} beforehand, fired on whichever thread the operation is on; there are nine of
 * them, and they are listed in the developer documentation.
 *
 * <h2>Disabled modules</h2>
 * A disabled module fires no events and answers no queries. Ask {@link #isModuleEnabled(String)} rather than
 * inferring it from silence; nothing here throws merely because a module is off.
 */
@NullMarked
public interface UxmEssentialsApi {

    /** The running uxmEssentials version, for a consumer that would rather feature-detect than hard-fail. */
    String version();

    /**
     * Whether the module with this id is enabled. The id is the one the operator writes in {@code modules.conf},
     * for example {@code homes} or {@code economy}.
     */
    boolean isModuleEnabled(String moduleId);

    /** The menu registration surface: custom actions, requirements, placeholders, list sources and icons. */
    MenuApi menus();

    /**
     * Reading a player's homes, or empty when the homes module is switched off.
     *
     * <p>Empty means the module is off, which is not the same thing as a player having no homes and is worth telling
     * apart: one is a server that will never answer, the other is a player who has not set one yet.
     */
    Optional<UxmHomesQuery> homes();

    /** Reading the server's warps, or empty when the warps module is switched off. */
    Optional<UxmWarpsQuery> warps();

    /** Reading the warps players own, or empty when the player-warps module is switched off. */
    Optional<UxmPlayerWarpsQuery> playerWarps();

    /** Reading balances and the leaderboard, or empty when the economy module is switched off. */
    Optional<UxmEconomyQuery> economy();

    /** Reading the kit catalogue and what a player may claim, or empty when the kits module is switched off. */
    Optional<UxmKitsQuery> kits();

    /** Reading a player's vaults, or empty when the vaults module is switched off. */
    Optional<UxmVaultsQuery> vaults();

    /** Reading punishments and history, or empty when the moderation module is switched off. */
    Optional<UxmModerationQuery> moderation();

    /** The API, or {@code null} when uxmEssentials is absent, still loading, or shutting down. */
    static @Nullable UxmEssentialsApi get() {
        return UxmApiHolder.current();
    }

    /**
     * Run {@code consumer} as soon as the API is available: immediately when it already is, otherwise once
     * uxmEssentials finishes enabling. The callback runs again after uxmEssentials reloads, so registrations made
     * inside it are restored rather than quietly lost.
     */
    static void whenReady(Consumer<UxmEssentialsApi> consumer) {
        UxmApiHolder.whenReady(consumer);
    }
}
