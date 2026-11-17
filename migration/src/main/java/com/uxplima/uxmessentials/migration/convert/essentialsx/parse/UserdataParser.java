package com.uxplima.uxmessentials.migration.convert.essentialsx.parse;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * Parses one EssentialsX {@code userdata/<uuid>.yml} into an {@link EssXUserdata}. The uuid comes from
 * the file name (EssentialsX keys each player's file by uuid); the node tree supplies the name, homes,
 * balance, and mail. Unknown keys are ignored and missing keys fall back to defaults, so a minor schema
 * variation never aborts a record. This class only reads value nodes, the YAML
 * codec lives in {@link YamlSource}.
 */
@NullMarked
public final class UserdataParser {

    private final JailMuteParser jailMute = new JailMuteParser();

    /** Parse the file at {@code file}, taking the uuid from the file name stem. */
    public EssXUserdata parse(java.nio.file.Path file) throws IOException {
        UUID uuid = uuidFromFileName(file.getFileName().toString());
        return parse(uuid, YamlSource.load(file));
    }

    /** Parse from a reader with an explicit uuid: the form the fixtures and golden-file tests drive. */
    public EssXUserdata parse(UUID uuid, Reader reader) throws IOException {
        return parse(uuid, YamlSource.load(reader));
    }

    private EssXUserdata parse(UUID uuid, ConfigurationNode root) {
        // The nickname is what other players saw, often coloured or spaced; it is not a name anybody logs in under,
        // so a file without lastAccountName is named by its uuid until the plan finds the account's name elsewhere.
        String name = nonBlank(root.node("lastAccountName").getString()).orElse(uuid.toString());
        ConfigurationNode timestamps = root.node("timestamps");
        long lastSeen = Math.max(
                timestamps.node("logout").getLong(0L), timestamps.node("login").getLong(0L));
        return new EssXUserdata(
                uuid,
                name,
                homes(root.node("homes")),
                balance(root),
                mail(root.node("mail")),
                jailMute.parseUser(root),
                kitTimestamps(timestamps.node("kits")),
                epochMillis(lastSeen));
    }

    private Map<String, Long> kitTimestamps(ConfigurationNode kitsNode) {
        Map<String, Long> claims = new LinkedHashMap<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                kitsNode.childrenMap().entrySet()) {
            // EssentialsX writes 0 for a kit the player never claimed, and looks a kit up by its lower-case name.
            long claimedAt = entry.getValue().getLong(0L);
            if (claimedAt > 0L) {
                claims.put(String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT), claimedAt);
            }
        }
        return claims;
    }

    private Map<String, EssXLocation> homes(ConfigurationNode homesNode) {
        Map<String, EssXLocation> homes = new LinkedHashMap<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                homesNode.childrenMap().entrySet()) {
            String homeName = String.valueOf(entry.getKey());
            EssXLocation location = LocationNodes.read(entry.getValue());
            if (location != null) {
                homes.put(homeName, location);
            }
        }
        return homes;
    }

    private Optional<BigDecimal> balance(ConfigurationNode root) {
        String raw = root.node("money").getString();
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(raw.strip()));
        } catch (NumberFormatException malformed) {
            // A non-numeric money value is treated as "no balance" rather than a failed record; the
            // user still imports their homes and mail.
            return Optional.empty();
        }
    }

    private List<EssXMail> mail(ConfigurationNode mailNode) {
        List<EssXMail> entries = new ArrayList<>();
        for (ConfigurationNode entry : mailNode.childrenList()) {
            mailEntry(entry).ifPresent(entries::add);
        }
        return entries;
    }

    private Optional<EssXMail> mailEntry(ConfigurationNode entry) {
        if (!entry.isMap()) {
            return nonBlank(entry.getString()).map(text -> EssXMail.legacy(text, false));
        }
        Optional<String> message = nonBlank(entry.node("message").getString());
        if (message.isEmpty()) {
            return Optional.empty();
        }
        boolean read = entry.node("read").getBoolean(false);
        // EssentialsX reads an entry without the flag as legacy and then ignores its sender and times, so the
        // same entry imports the same way here.
        if (entry.node("legacy").getBoolean(true)) {
            return Optional.of(EssXMail.legacy(message.get(), read));
        }
        return Optional.of(new EssXMail(
                message.get(),
                nonBlank(entry.node("sender-name").getString()),
                uuid(entry.node("sender-uuid").getString()),
                epochMillis(entry.node("timestamp").getLong(0L)),
                epochMillis(entry.node("expire").getLong(0L)),
                read));
    }

    private static Optional<String> nonBlank(@Nullable String raw) {
        return raw == null || raw.isBlank() ? Optional.empty() : Optional.of(raw.strip());
    }

    private static Optional<UUID> uuid(@Nullable String raw) {
        // EssentialsX writes the text "null" for a console sender.
        if (raw == null || raw.isBlank() || "null".equals(raw.strip())) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw.strip()));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    private static Optional<Instant> epochMillis(long raw) {
        // EssentialsX writes 0 for a time it does not know or an expiry that never comes.
        return raw > 0L ? Optional.of(Instant.ofEpochMilli(raw)) : Optional.empty();
    }

    private static UUID uuidFromFileName(String fileName) {
        String stem = fileName.endsWith(".yml") ? fileName.substring(0, fileName.length() - 4) : fileName;
        return UUID.fromString(stem);
    }
}
