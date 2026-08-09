package com.uxplima.uxmessentials.api.bukkit;

import java.util.function.Consumer;

import com.uxplima.uxmessentials.api.bukkit.menu.MenuApi;
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
