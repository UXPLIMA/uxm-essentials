package com.uxplima.uxmessentials.shared.adapter.outbound.skin;

import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import org.jspecify.annotations.NullMarked;

/**
 * Parses the two tiny Mojang JSON responses {@link MojangSkins} fetches, isolated here so the service's
 * orchestration stays free of gson and the parsing is testable on its own. Every method is fail-soft: a body
 * that does not parse, or that lacks the field being read, yields an empty {@link Optional} rather than
 * throwing, because a malformed or partial response is just another "no skin" the service caches as a miss.
 *
 * <p>gson is on the server classpath at runtime (the plugin loader provisions it), so this carries no shaded
 * dependency.
 */
@NullMarked
public final class MojangProfileJson {

    private static final String TEXTURES_PROPERTY = "textures";

    private MojangProfileJson() {}

    /** The {@code id} (undashed profile uuid) from a name→uuid response, or empty when absent/unparseable. */
    public static Optional<String> uuid(String body) {
        return object(body)
                .map(json -> json.get("id"))
                .filter(JsonElement::isJsonPrimitive)
                .map(JsonElement::getAsString)
                .filter(id -> !id.isBlank());
    }

    /** The signed {@link SkinTexture} from a session-profile response, or empty when it carries no textures property. */
    public static Optional<SkinTexture> skin(String body) {
        Optional<JsonObject> root = object(body);
        if (root.isEmpty()) {
            return Optional.empty();
        }
        JsonElement properties = root.get().get("properties");
        if (properties == null || !properties.isJsonArray()) {
            return Optional.empty();
        }
        return textureProperty(properties.getAsJsonArray());
    }

    private static Optional<SkinTexture> textureProperty(JsonArray properties) {
        for (JsonElement element : properties) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject property = element.getAsJsonObject();
            if (isTextures(property) && property.has("value")) {
                String value = property.get("value").getAsString();
                if (!value.isBlank()) {
                    return Optional.of(new SkinTexture(value, signature(property)));
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isTextures(JsonObject property) {
        JsonElement name = property.get("name");
        return name != null && name.isJsonPrimitive() && TEXTURES_PROPERTY.equals(name.getAsString());
    }

    private static @org.jspecify.annotations.Nullable String signature(JsonObject property) {
        JsonElement signature = property.get("signature");
        if (signature == null || !signature.isJsonPrimitive()) {
            return null;
        }
        String value = signature.getAsString();
        return value.isBlank() ? null : value;
    }

    private static Optional<JsonObject> object(String body) {
        try {
            JsonElement parsed = JsonParser.parseString(body);
            return parsed.isJsonObject() ? Optional.of(parsed.getAsJsonObject()) : Optional.empty();
        } catch (RuntimeException malformed) {
            // gson throws JsonSyntaxException (a RuntimeException) on a malformed body; an unparseable response
            // is just a miss the caller caches, so swallow it here rather than propagate.
            return Optional.empty();
        }
    }
}
