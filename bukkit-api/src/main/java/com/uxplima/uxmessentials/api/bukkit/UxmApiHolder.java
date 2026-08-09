package com.uxplima.uxmessentials.api.bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The static plumbing behind {@link UxmEssentialsApi#get()} and {@link UxmEssentialsApi#whenReady}. Only
 * uxmEssentials' own bootstrap calls {@link #install} and {@link #uninstall}; a consumer never touches this class.
 *
 * <h2>Why the callbacks outlive a disable</h2>
 * The waiting list is kept when the API is withdrawn, so a consumer's callback runs again the next time
 * uxmEssentials enables. That is the whole point of the callback form: a reload rebuilds the engine's registries,
 * and anything registered inside the callback would otherwise be silently gone while the consumer plugin, still
 * enabled, had no way to know it had to register again.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>guarded-by-lock</b>. Both the reference and the list are guarded by {@link #LOCK}, because a
 * consumer may register from its own enable on another thread while ours is installing, and holding the lock across
 * the whole of each operation is what stops a callback running twice in that overlap. These are enable-time
 * operations, never a hot path, so the coarse lock costs nothing.
 */
@NullMarked
public final class UxmApiHolder {

    private static final Object LOCK = new Object();

    private static final List<Consumer<UxmEssentialsApi>> WAITING = new ArrayList<>();

    private static @Nullable UxmEssentialsApi current;

    private UxmApiHolder() {}

    /** Publish the API and run every waiting callback. Called by uxmEssentials on enable. */
    public static void install(UxmEssentialsApi api) {
        Objects.requireNonNull(api, "api");
        List<Consumer<UxmEssentialsApi>> waiting;
        synchronized (LOCK) {
            current = api;
            waiting = List.copyOf(WAITING);
        }
        // Outside the lock: a callback registers menu bindings and listeners of its own, and one that reached back
        // into this class while we held the lock would deadlock itself.
        for (Consumer<UxmEssentialsApi> consumer : waiting) {
            consumer.accept(api);
        }
    }

    /** Withdraw the API, keeping the waiting callbacks so the next enable restores them. */
    public static void uninstall() {
        synchronized (LOCK) {
            current = null;
        }
    }

    /** Drop the waiting callbacks. Package-private: the holder's tests need a clean slate, consumers do not. */
    static void forgetWaiting() {
        synchronized (LOCK) {
            WAITING.clear();
        }
    }

    static @Nullable UxmEssentialsApi current() {
        synchronized (LOCK) {
            return current;
        }
    }

    static void whenReady(Consumer<UxmEssentialsApi> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        UxmEssentialsApi api;
        synchronized (LOCK) {
            WAITING.add(consumer);
            api = current;
        }
        if (api != null) {
            consumer.accept(api);
        }
    }
}
