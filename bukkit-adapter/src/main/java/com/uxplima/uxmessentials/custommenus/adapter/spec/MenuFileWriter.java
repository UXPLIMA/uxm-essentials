package com.uxplima.uxmessentials.custommenus.adapter.spec;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.custommenus.adapter.inbound.command.OpenCommandSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ArgumentSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpecException;
import com.uxplima.uxmlib.menu.spec.MenuSpecWriter;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * Writes a whole menu file: the menu itself plus the {@code command {}} block this plugin puts beside it. The menu
 * half is uxmLib's {@link MenuSpecWriter}, which is the inverse of the engine's own loader and belongs to whoever owns
 * that grammar. The command half is the inverse of {@code CustomMenuLoader.parseOpenCommand}, which is ours: no other
 * consumer of the engine has an open-command block, so the library neither reads nor writes one.
 *
 * <p>Two things stay on this side of that line, and they are the same thing said twice. The header is the block of
 * comments that opens the shipped {@code menus/example.conf}, and it names our commands and our folder, so the library
 * takes it as verbatim text through its constructor rather than deciding what a file says. It is a bundled resource,
 * {@code /menus/header.txt}, read once here and handed over. The command block is the other: our grammar, written by
 * us, appended after the menu as a further top-level key.
 *
 * <p>Model faithful, not byte faithful, exactly as the library states. A save keeps the header and loses everything
 * else the model does not hold: an operator's own comments, the order they wrote their keys in, and the loader's
 * shorthands, which come back in the canonical form of the model they produced. The header tells the operator so.
 *
 * <p>Bukkit-free like the spec model it walks, so it is exercised by plain JUnit.
 */
public final class MenuFileWriter {

    /** The bundled header, re-emitted at the top of every file this class writes. */
    private static final String HEADER_RESOURCE = "/menus/header.txt";

    /**
     * Read once, at class load, so a jar that lost the resource fails at the first save with the name of the file it
     * wanted rather than writing menus with no header for the rest of the run.
     */
    private static final String HEADER = readHeader();

    /** The library's writer, holding our header: one instance, because it is immutable and stateless. */
    private final MenuSpecWriter menu = new MenuSpecWriter(HEADER);

    /** Serialize a menu with no {@code command {}} block. */
    public String write(MenuSpec spec) {
        return write(spec, null);
    }

    /**
     * Serialize {@code spec}, and {@code command} when the menu declares one, into one menu file's HOCON. Re-loading
     * the result through {@code MenuSpecLoader} (and {@code parseOpenCommand} for the command block) reproduces an
     * equal {@link MenuSpec} and {@link OpenCommandSpec}.
     *
     * <p>The command block is appended after the menu rather than merged into it. HOCON reads a file as one root
     * object, so a further top-level key at the end is the same document as one written in the middle, and appending
     * keeps the library's rendering of the menu byte for byte what the library's own round-trip test pins.
     *
     * @throws MenuSpecException when the spec holds a shape the file grammar cannot express
     */
    public String write(MenuSpec spec, @Nullable OpenCommandSpec command) {
        Objects.requireNonNull(spec, "spec");
        String hocon = menu.write(spec);
        return command == null ? hocon : hocon + renderCommand(command);
    }

    /** Emit the top-level {@code command {}} open-command block, the inverse of {@code parseOpenCommand}. */
    private static String renderCommand(OpenCommandSpec command) {
        CommentedConfigurationNode root = CommentedConfigurationNode.root();
        try {
            CommentedConfigurationNode node = root.node("command");
            node.node("name").set(command.name());
            writeStringsIfAny(node.node("aliases"), command.aliases());
            setIfPresent(node.node("permission"), command.permission().orElse(null));
            setIfPresent(node.node("deny-message"), command.denyMessage().orElse(null));
            setIfTrue(node.node("console"), command.consoleAllowed());
            for (ArgumentSpec argument : command.arguments()) {
                writeArgument(node.node("arguments").appendListNode(), argument);
            }
            setIfPresent(node.node("usage"), command.usage().orElse(null));
        } catch (SerializationException failure) {
            throw new MenuSpecException("failed to build the open-command node", failure);
        }
        return render(root);
    }

    private static void writeArgument(CommentedConfigurationNode node, ArgumentSpec argument)
            throws SerializationException {
        node.node("name").set(argument.name());
        node.node("type").set(argTypeToken(argument.type()));
        setIfTrue(node.node("greedy"), argument.greedy());
    }

    /** The config token an {@link ArgumentSpec.ArgType} parses back from, the inverse of {@code ArgType.parse}. */
    private static String argTypeToken(ArgumentSpec.ArgType type) {
        return switch (type) {
            case STRING -> "string";
            case INT -> "int";
            case DOUBLE -> "double";
            case BOOL -> "bool";
            case MATERIAL -> "material";
            case WORLD -> "world";
            case ONLINE_PLAYER -> "online-player";
            case PLAYER -> "player";
        };
    }

    private static void writeStringsIfAny(CommentedConfigurationNode node, List<String> values)
            throws SerializationException {
        if (!values.isEmpty()) {
            node.setList(String.class, values);
        }
    }

    private static void setIfTrue(CommentedConfigurationNode node, boolean value) throws SerializationException {
        if (value) {
            node.set(true);
        }
    }

    private static void setIfPresent(CommentedConfigurationNode node, @Nullable String value)
            throws SerializationException {
        if (value != null) {
            node.set(value);
        }
    }

    /** Render the populated node to HOCON text through the same save path the menu converters use. */
    private static String render(CommentedConfigurationNode root) {
        StringWriter writer = new StringWriter();
        HoconConfigurationLoader loader = HoconConfigurationLoader.builder()
                .sink(() -> new BufferedWriter(writer))
                .build();
        try {
            loader.save(root);
        } catch (ConfigurateException failure) {
            throw new MenuSpecException("failed to render the open-command block", failure);
        }
        return writer.toString();
    }

    private static String readHeader() {
        try (InputStream bundled = MenuFileWriter.class.getResourceAsStream(HEADER_RESOURCE)) {
            if (bundled == null) {
                throw new IllegalStateException(HEADER_RESOURCE + " is not in the jar");
            }
            return new String(bundled.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("failed to read " + HEADER_RESOURCE, failure);
        }
    }
}
