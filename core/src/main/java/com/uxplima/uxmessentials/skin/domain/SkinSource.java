package com.uxplima.uxmessentials.skin.domain;

import java.util.Objects;

import org.jspecify.annotations.NullMarked;

/**
 * Where a skin came from, kept beside the texture it resolved to.
 *
 * <p>The texture is what a login applies; the source is what {@code /skin update} re-resolves and what
 * {@code /skin info} reports. Each source carries exactly one value, which is how a stored row can hold any of
 * them in a type column and a value column.
 */
@NullMarked
public sealed interface SkinSource {

    /** The single value this source carries, as it is written to the stored row. */
    String value();

    /** The skin worn by the account called {@code username}, resolved through Mojang. */
    record ByName(String username) implements SkinSource {

        public ByName {
            require(username, "username");
        }

        @Override
        public String value() {
            return username;
        }
    }

    /** A skin image published at {@code url}, uploaded to a skin service to be signed. */
    record ByUrl(String url) implements SkinSource {

        public ByUrl {
            require(url, "url");
        }

        @Override
        public String value() {
            return url;
        }
    }

    /** A skin image the operator dropped into the plugin's own skin folder. */
    record ByFile(String fileName) implements SkinSource {

        public ByFile {
            require(fileName, "fileName");
        }

        @Override
        public String value() {
            return fileName;
        }
    }

    /** The skin a Bedrock player wears, keyed by the xuid Floodgate knows them by. */
    record Bedrock(String xuid) implements SkinSource {

        public Bedrock {
            require(xuid, "xuid");
        }

        @Override
        public String value() {
            return xuid;
        }
    }

    /** A skin from the configured default pool, worn by a player who chose nothing and has none of their own. */
    record Fallback(String name) implements SkinSource {

        public Fallback {
            require(name, "name");
        }

        @Override
        public String value() {
            return name;
        }
    }

    private static void require(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
