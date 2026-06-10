package com.uxplima.uxmessentials.shared.adapter.outbound.mapmarker;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import xyz.jpenilla.squaremap.api.Squaremap;

/**
 * Guarded access to the live {@link Squaremap} API handle. squaremap's own
 * {@link xyz.jpenilla.squaremap.api.SquaremapProvider#get()} throws when the API is not yet registered (the
 * plugin is mid-enable, or absent), so the publisher resolves the handle through here and treats a missing
 * handle as "render nothing for now" rather than letting the exception escape onto a tick.
 */
@NullMarked
final class SquaremapProvider {

    private SquaremapProvider() {}

    /** The live squaremap API, or {@code null} when it is not currently available. */
    static @Nullable Squaremap getOrNull(Logger log) {
        try {
            return xyz.jpenilla.squaremap.api.SquaremapProvider.get();
        } catch (IllegalStateException notReady) {
            log.warn("event=squaremap_api_unavailable reason={}", String.valueOf(notReady.getMessage()));
            return null;
        }
    }
}
