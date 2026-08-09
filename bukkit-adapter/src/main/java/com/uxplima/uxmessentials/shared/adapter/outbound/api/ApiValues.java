package com.uxplima.uxmessentials.shared.adapter.outbound.api;

import com.uxplima.uxmessentials.api.view.UxmLocation;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * The kernel value types as the published API sees them.
 *
 * <p>One place for every such conversion, so a change to a kernel type is a change to one method rather than to
 * however many bridge classes happened to inline it. The direction is one-way on purpose: the API never converts
 * back into a kernel type, because an event is a fact that has already been recorded.
 */
@NullMarked
public final class ApiValues {

    private ApiValues() {}

    /** A kernel position as an API location: the world by name, since the world may not be loaded. */
    public static UxmLocation location(Position position) {
        return new UxmLocation(
                position.world().name(), position.x(), position.y(), position.z(), position.yaw(), position.pitch());
    }
}
