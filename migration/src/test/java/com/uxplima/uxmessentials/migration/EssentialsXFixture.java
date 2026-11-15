package com.uxplima.uxmessentials.migration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.migration.convert.essentialsx.map.UserdataMapper;
import com.uxplima.uxmessentials.migration.convert.essentialsx.parse.UserdataParser;
import com.uxplima.uxmessentials.migration.convert.map.ImportedUser;
import com.uxplima.uxmessentials.shared.domain.WorldRef;

/** The EssentialsX userdata file under {@code src/test/resources}, read and mapped the way the import does. */
public final class EssentialsXFixture {

    public static final UUID STEVE = UUID.fromString("5d1f0c1e-3b4a-4f7e-9a51-2a8c6f0e7b10");
    public static final UUID ALEX = UUID.fromString("8b0c7a52-6f1e-4d3a-9c2b-0e4f5a6b7c8d");

    private static final WorldRef WORLD =
            new WorldRef(UUID.fromString("00000000-0000-0000-0000-00000000a001"), "world");

    private EssentialsXFixture() {}

    /** Steve's userdata, with every world name known to the server. */
    public static ImportedUser steve() {
        String resource = "/essentialsx/userdata/" + STEVE + ".yml";
        URL url = Objects.requireNonNull(EssentialsXFixture.class.getResource(resource), resource);
        try {
            return new UserdataMapper(name -> Optional.of(WORLD)).map(new UserdataParser().parse(Path.of(url.toURI())));
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        } catch (URISyntaxException badUrl) {
            throw new IllegalStateException(badUrl);
        }
    }
}
