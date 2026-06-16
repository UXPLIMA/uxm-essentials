package com.uxplima.uxmessentials.npc.adapter.outbound;

import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Extracts the signed texture value/signature from a MineSkin generate response, isolated here so the
 * {@link MineSkinService} orchestration stays free of gson and the parsing is testable on its own. The public
 * MineSkin API has carried the texture pair under a few shapes across versions — {@code data.texture.value} /
 * {@code data.texture.signature} (the classic generate-from-url response), a top-level {@code texture.value}, and
 * a {@code texture.data.value} nesting — so the texture object is located defensively by walking those candidate
 * paths and the strings are read wherever they sit. Every method is fail-soft: a body that does not parse, or
 * that lacks the texture/value, yields an empty {@link Optional} rather than throwing, because a malformed or
 * partial response is just another "no skin" the service caches as a miss.
 *
 * <p>gson is on the server classpath at runtime (the plugin loader provisions it), so this carries no shaded
 * dependency.
 */
@NullMarked
final class MineSkinJson {

    private MineSkinJson() {}

    /** The signed {@link NpcSkin} from a generate response, or empty when it carries no usable texture value. */
    static Optional<NpcSkin> skin(String body) {
        Optional<JsonObject> root = object(body);
        if (root.isEmpty()) {
            return Optional.empty();
        }
        return texture(root.get()).flatMap(MineSkinJson::fromTexture);
    }

    /**
     * Locate the texture object across the response shapes the API has used: {@code data.texture}, a top-level
     * {@code texture}, or {@code texture.data}. Returns the first object found, or empty when none is present.
     */
    private static Optional<JsonObject> texture(JsonObject root) {
        JsonObject data = child(root, "data");
        Optional<JsonObject> underData =
                (data == null) ? Optional.empty() : Optional.ofNullable(child(data, "texture"));
        if (underData.isPresent()) {
            return underData;
        }
        JsonObject texture = child(root, "texture");
        if (texture == null) {
            return Optional.empty();
        }
        JsonObject nested = child(texture, "data");
        return Optional.of(nested != null ? nested : texture);
    }

    /** Build an {@link NpcSkin} from a texture object, or empty when its {@code value} is absent or blank. */
    private static Optional<NpcSkin> fromTexture(JsonObject texture) {
        String value = string(texture, "value");
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String signature = string(texture, "signature");
        return Optional.of(new NpcSkin(value, (signature == null || signature.isBlank()) ? null : signature));
    }

    private static @Nullable JsonObject child(JsonObject parent, String name) {
        JsonElement element = parent.get(name);
        return (element != null && element.isJsonObject()) ? element.getAsJsonObject() : null;
    }

    private static @Nullable String string(JsonObject parent, String name) {
        JsonElement element = parent.get(name);
        return (element != null && element.isJsonPrimitive()) ? element.getAsString() : null;
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
