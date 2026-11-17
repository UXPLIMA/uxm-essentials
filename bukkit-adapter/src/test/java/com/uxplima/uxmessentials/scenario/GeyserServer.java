package com.uxplima.uxmessentials.scenario;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.geysermc.api.Geyser;
import org.geysermc.api.GeyserApiBase;
import org.geysermc.api.connection.Connection;
import org.geysermc.cumulus.form.Form;
import org.geysermc.cumulus.form.util.FormBuilder;
import org.jspecify.annotations.Nullable;

/**
 * Geyser's API as a server running Geyser without Floodgate answers it, for the scenarios. Paired with a mock plugin
 * named {@code Geyser-Spigot}, it names the Bedrock players and takes the forms the plugin sends them, which a
 * scenario reads and answers the way a Bedrock client would.
 *
 * <p>Geyser holds one registered API for the whole JVM and refuses a second, so every scenario shares this one and
 * {@link #reset()}s it.
 */
final class GeyserServer implements GeyserApiBase {

    private static final GeyserServer INSTANCE = register();

    private final Set<UUID> bedrock = ConcurrentHashMap.newKeySet();

    /** Every form sent, oldest first, with the player it went to. */
    private final List<Sent> sent = new ArrayList<>();

    private GeyserServer() {}

    /** The one Geyser this JVM runs. */
    static GeyserServer installed() {
        return INSTANCE;
    }

    private static GeyserServer register() {
        GeyserServer geyser = new GeyserServer();
        Geyser.set(geyser);
        return geyser;
    }

    /** Make {@code player} a Bedrock player connected through Geyser. */
    void connect(UUID player) {
        bedrock.add(player);
    }

    /** Forget every Bedrock player and every form, so the next scenario starts with none. */
    void reset() {
        bedrock.clear();
        synchronized (sent) {
            sent.clear();
        }
    }

    /** The forms sent so far, oldest first. */
    List<Sent> sent() {
        synchronized (sent) {
            return List.copyOf(sent);
        }
    }

    @Override
    public boolean isBedrockPlayer(UUID uuid) {
        return bedrock.contains(uuid);
    }

    /** No connection details, so the plugin reads no Xbox id and an empty client for everyone. */
    @Override
    public @Nullable Connection connectionByUuid(UUID uuid) {
        return null;
    }

    @Override
    public @Nullable Connection connectionByXuid(String xuid) {
        return null;
    }

    @Override
    public boolean sendForm(UUID uuid, Form form) {
        if (!bedrock.contains(uuid)) {
            return false;
        }
        synchronized (sent) {
            sent.add(new Sent(uuid, form));
        }
        return true;
    }

    @Override
    public boolean sendForm(UUID uuid, FormBuilder<?, ?, ?> builder) {
        return sendForm(uuid, builder.build());
    }

    @Override
    public boolean transfer(UUID uuid, String address, int port) {
        return false;
    }

    @Override
    public List<? extends Connection> onlineConnections() {
        return List.of();
    }

    @Override
    public int onlineConnectionsCount() {
        return bedrock.size();
    }

    /** Without Floodgate a Bedrock player signs in with a Java account, so no name carries a prefix. */
    @Override
    public String usernamePrefix() {
        return "";
    }

    /** One form, and the player it was sent to. */
    record Sent(UUID player, Form form) {}
}
