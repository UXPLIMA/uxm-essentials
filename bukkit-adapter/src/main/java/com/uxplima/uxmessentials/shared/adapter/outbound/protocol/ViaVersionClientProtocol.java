package com.uxplima.uxmessentials.shared.adapter.outbound.protocol;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.port.ClientProtocol;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@link ClientProtocol} backed by ViaVersion, reached <b>entirely by reflection</b>: no
 * {@code com.viaversion} type is named here, only string class names, so a server without ViaVersion loads
 * none of its classes and this class is safe to construct anywhere.
 *
 * <p>The call is {@code Via.getAPI().getPlayerVersion(uuid)}. Two answers are not versions and both mean the
 * same thing here: {@code -1}, which ViaVersion returns for a player it never tracked (usually one that
 * disconnected during the handshake), and an {@code IllegalArgumentException}, which it throws when its
 * platform has not finished loading or when another plugin has shaded a second copy of the Via API. Both map
 * to {@link ClientProtocol#UNKNOWN} rather than to a guess.
 *
 * <p>{@link #bind} is the factory the composition root calls: it returns this reader only when ViaVersion is
 * actually enabled and otherwise {@link ClientProtocol#UNAVAILABLE}, so the decision costs one plugin-manager
 * lookup at startup instead of one per query.
 */
@NullMarked
public final class ViaVersionClientProtocol implements ClientProtocol {

    private static final String VIAVERSION = "ViaVersion";
    private static final String VIA_CLASS = "com.viaversion.viaversion.api.Via";

    private final Logger log;

    private @Nullable Method getApi;
    private @Nullable Method getPlayerVersion;
    private boolean unavailableLogged;

    public ViaVersionClientProtocol(Logger log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    /** The protocol source for this server: the ViaVersion reader when it is installed, else the unknown one. */
    public static ClientProtocol bind(Server server, Logger log) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(log, "log");
        Plugin via = server.getPluginManager().getPlugin(VIAVERSION);
        if (via == null || !via.isEnabled()) {
            return ClientProtocol.UNAVAILABLE;
        }
        log.info("event=client_protocol_source source=viaversion");
        return new ViaVersionClientProtocol(log);
    }

    @Override
    public int protocolVersion(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        try {
            return read(who.uuid());
        } catch (Throwable t) {
            return degrade(t);
        }
    }

    private int read(UUID player) throws ReflectiveOperationException {
        if (getApi == null) {
            getApi = Class.forName(VIA_CLASS).getMethod("getAPI");
        }
        Object api = getApi.invoke(null);
        if (api == null) {
            return UNKNOWN;
        }
        if (getPlayerVersion == null) {
            getPlayerVersion = api.getClass().getMethod("getPlayerVersion", UUID.class);
        }
        Object version = getPlayerVersion.invoke(api, player);
        return version instanceof Number number ? number.intValue() : UNKNOWN;
    }

    /**
     * One warning per source for an unreachable or incompatible ViaVersion, then silence. This is read on
     * render paths, so a repeated failure must not fill the console.
     */
    private int degrade(Throwable cause) {
        if (!unavailableLogged) {
            unavailableLogged = true;
            log.warn("event=client_protocol_lookup_failed source=viaversion reason={}", cause);
        }
        return UNKNOWN;
    }
}
