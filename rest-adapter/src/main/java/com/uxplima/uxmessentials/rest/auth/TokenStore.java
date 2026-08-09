package com.uxplima.uxmessentials.rest.auth;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

/**
 * The issued tokens, kept in {@code tokens.json} beside the config.
 *
 * <p>What is written is a hash. The secret is 32 random bytes from {@link SecureRandom}, shown once to whoever ran
 * the command and never again: a file an operator can read is a file a backup can leak, and there is nothing worth
 * storing that a revoke-and-reissue does not solve better.
 *
 * <p>Lookup is by hash, so presenting a token is a map read rather than a walk over every issued one. Comparing
 * 256-bit digests of high-entropy secrets is not a timing problem: there is nothing to narrow down one byte at a
 * time when the space cannot be walked at all.
 *
 * <p>Reads come from the listener's threads and writes from a command on the server thread, so the map is
 * concurrent and every write saves the whole file under one lock. The file is small and written rarely.
 */
public final class TokenStore {

    /** How many random bytes a secret is. */
    private static final int SECRET_BYTES = 32;

    /** What every issued secret starts with, so one is recognisable in a log or a config an operator pasted it into. */
    public static final String PREFIX = "uxm_";

    private static final String FILE_NAME = "tokens.json";
    private static final Gson GSON = new Gson();

    private final Path file;
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentMap<String, ApiToken> byHash = new ConcurrentHashMap<>();
    private final Object writeLock = new Object();

    private TokenStore(Path file) {
        this.file = file;
    }

    /** Open the store under {@code dataFolder}, reading whatever is already there. */
    public static TokenStore open(Path dataFolder) {
        Objects.requireNonNull(dataFolder, "dataFolder");
        TokenStore store = new TokenStore(dataFolder.resolve(FILE_NAME));
        store.load();
        return store;
    }

    /**
     * Issue a token named {@code label} with these scopes, and return the secret.
     *
     * <p>The returned string is the only copy. Show it once.
     *
     * @throws IllegalStateException when a token already carries that label, since two tokens answering to one
     *     name would make revoking either of them ambiguous
     */
    public String create(String label, Set<String> scopes) {
        String name = normalise(label);
        if (find(name).isPresent()) {
            throw new IllegalStateException("a token named " + name + " already exists");
        }
        byte[] secret = new byte[SECRET_BYTES];
        random.nextBytes(secret);
        String plaintext = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        ApiToken token = new ApiToken(name, hash(plaintext), scopes, Instant.now());
        byHash.put(token.hash(), token);
        save();
        return plaintext;
    }

    /** Revoke the token with this label, answering whether there was one. */
    public boolean revoke(String label) {
        String name = normalise(label);
        Optional<ApiToken> existing = find(name);
        existing.ifPresent(token -> {
            byHash.remove(token.hash());
            save();
        });
        return existing.isPresent();
    }

    /** Every issued token, oldest first. */
    public List<ApiToken> list() {
        List<ApiToken> all = new ArrayList<>(byHash.values());
        all.sort(Comparator.comparing(ApiToken::createdAt).thenComparing(ApiToken::label));
        return List.copyOf(all);
    }

    /** The token this secret belongs to, or empty when nobody issued it. */
    public Optional<ApiToken> authenticate(String presented) {
        Objects.requireNonNull(presented, "presented");
        return Optional.ofNullable(byHash.get(hash(presented)));
    }

    private Optional<ApiToken> find(String label) {
        return byHash.values().stream()
                .filter(token -> token.label().equals(label))
                .findFirst();
    }

    private static String normalise(String label) {
        String name = label.trim().toLowerCase(Locale.ROOT);
        if (name.isBlank()) {
            throw new IllegalArgumentException("a token label must not be blank");
        }
        return name;
    }

    /** The SHA-256 of a secret, hex, which is what the file holds. */
    static String hash(String plaintext) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("this JVM has no SHA-256", impossible);
        }
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            JsonElement parsed = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonElement.class);
            if (parsed == null || !parsed.isJsonArray()) {
                return;
            }
            for (JsonElement element : parsed.getAsJsonArray()) {
                readToken(element).ifPresent(token -> byHash.put(token.hash(), token));
            }
        } catch (IOException | JsonSyntaxException unreadable) {
            // A store that cannot be read is a store with no tokens in it, which means every request is refused.
            // That is the safe direction, and the operator sees it the first time they try one.
            throw new UncheckedIOException(
                    new IOException("could not read " + file + ": " + unreadable.getMessage(), unreadable));
        }
    }

    private static Optional<ApiToken> readToken(JsonElement element) {
        if (!element.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject object = element.getAsJsonObject();
        Set<String> scopes = new java.util.TreeSet<>();
        object.getAsJsonArray("scopes").forEach(scope -> scopes.add(scope.getAsString()));
        return Optional.of(new ApiToken(
                object.get("label").getAsString(),
                object.get("hash").getAsString(),
                scopes,
                Instant.ofEpochSecond(object.get("created-at").getAsLong())));
    }

    private void save() {
        synchronized (writeLock) {
            JsonArray all = new JsonArray();
            for (ApiToken token : list()) {
                JsonObject object = new JsonObject();
                object.addProperty("label", token.label());
                object.addProperty("hash", token.hash());
                object.addProperty("created-at", token.createdAt().getEpochSecond());
                JsonArray scopes = new JsonArray();
                token.scopes().stream().sorted().forEach(scopes::add);
                object.add("scopes", scopes);
                all.add(object);
            }
            try {
                Path parent = file.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(file, GSON.toJson(all), StandardCharsets.UTF_8);
            } catch (IOException failure) {
                throw new UncheckedIOException("could not write " + file, failure);
            }
        }
    }
}
