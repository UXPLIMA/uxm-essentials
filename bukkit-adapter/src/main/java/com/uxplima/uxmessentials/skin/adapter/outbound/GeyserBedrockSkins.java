package com.uxplima.uxmessentials.skin.adapter.outbound;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.uxplima.uxmessentials.shared.adapter.outbound.skin.HttpFetcher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import com.uxplima.uxmessentials.skin.application.port.BedrockSkins;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The Bedrock skin of a player, read from the Geyser skin service by the Xbox id the server knows them under.
 *
 * <p>Two halves, deliberately kept apart. The xuid comes from whatever names Bedrock players here, handed in as a
 * lookup so this class never probes Floodgate itself (the presence guard lives in the Bedrock detector, which is
 * the one seam allowed to name that plugin). The texture comes from {@code /v2/skin/<xuid>}, whose answer carries
 * the same signed {@code value} and {@code signature} pair a Mojang profile does.
 *
 * <p>Fail-soft throughout: a Java player is never looked up at all, a player Geyser has no skin for is an empty
 * answer, and a failed request is retried a bounded number of times before being given up. Nothing here throws
 * into the login path.
 */
@NullMarked
public final class GeyserBedrockSkins implements BedrockSkins {

    /** The documented Geyser skin endpoint; the xuid is appended. */
    public static final String SKIN_ENDPOINT = "https://api.geysermc.org/v2/skin/";

    private final XuidLookup xuids;
    private final HttpFetcher fetcher;
    private final Logger log;
    private final int retries;
    private final boolean available;

    /**
     * @param xuids how a player's Xbox id is resolved; {@link Optional#empty()} for a Java player
     * @param fetcher the HTTP seam
     * @param log where a failed lookup is reported
     * @param retries how many times a failed request is retried before it is given up
     * @param available whether a Bedrock backend is installed at all
     */
    public GeyserBedrockSkins(XuidLookup xuids, HttpFetcher fetcher, Logger log, int retries, boolean available) {
        this.xuids = Objects.requireNonNull(xuids, "xuids");
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
        this.log = Objects.requireNonNull(log, "log");
        if (retries < 0) {
            throw new IllegalArgumentException("retries must not be negative: " + retries);
        }
        this.retries = retries;
        this.available = available;
    }

    @Override
    public Optional<SkinTexture> byPlayer(UUID player) {
        Objects.requireNonNull(player, "player");
        if (!available) {
            return Optional.empty();
        }
        return xuids.xuidOf(player).flatMap(this::fetch);
    }

    @Override
    public boolean available() {
        return available;
    }

    /** The signed texture Geyser holds for {@code xuid}, retried a bounded number of times. */
    private Optional<SkinTexture> fetch(String xuid) {
        URI uri = URI.create(SKIN_ENDPOINT + xuid);
        for (int attempt = 0; attempt <= retries; attempt++) {
            Optional<String> body = fetcher.get(uri);
            if (body.isPresent()) {
                Optional<SkinTexture> texture = parse(body.get());
                if (texture.isPresent()) {
                    return texture;
                }
                // A 200 with no texture is an answer, not a failure: Geyser simply holds no skin for this player.
                return Optional.empty();
            }
        }
        log.warn("skin: the Geyser skin lookup for xuid {} failed after {} attempts", xuid, retries + 1);
        return Optional.empty();
    }

    /** The {@code value} / {@code signature} pair in a Geyser skin response, or empty when it carries none. */
    private Optional<SkinTexture> parse(String body) {
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonObject()) {
                return Optional.empty();
            }
            JsonObject object = root.getAsJsonObject();
            String value = string(object, "value");
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            String signature = string(object, "signature");
            return Optional.of(new SkinTexture(value, signature == null || signature.isBlank() ? null : signature));
        } catch (RuntimeException malformed) {
            log.warn("skin: a Geyser skin response could not be read ({})", malformed.toString());
            return Optional.empty();
        }
    }

    private static @Nullable String string(JsonObject parent, String name) {
        JsonElement element = parent.get(name);
        return element == null || !element.isJsonPrimitive() ? null : element.getAsString();
    }

    /** How a player's Xbox id is resolved, so this class never names the plugin that answers. */
    @FunctionalInterface
    public interface XuidLookup {

        /** The Xbox id {@code player} connected under, or empty when they are not a Bedrock player. */
        Optional<String> xuidOf(UUID player);

        /** A lookup built from any function, which is how the wiring hands over the Bedrock detector's method. */
        static XuidLookup of(Function<UUID, Optional<String>> lookup) {
            Objects.requireNonNull(lookup, "lookup");
            return lookup::apply;
        }
    }
}
