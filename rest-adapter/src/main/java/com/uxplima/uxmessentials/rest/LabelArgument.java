package com.uxplima.uxmessentials.rest;

import io.papermc.paper.command.brigadier.argument.CustomArgumentType;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import org.jspecify.annotations.NullMarked;

/**
 * A token's label: everything up to the next space. Brigadier's {@code word()} refused any character outside
 * {@code 0-9 A-Z a-z _ - . +}, so an operator could not label a token {@code yönetim}. The host reads its own
 * arguments this way through uxmLib; this add-on cannot reach the host's relocated copy, so it carries the same
 * few lines. The client is still told the argument is a word.
 */
@NullMarked
final class LabelArgument implements CustomArgumentType<String, String> {

    static final LabelArgument INSTANCE = new LabelArgument();

    private LabelArgument() {}

    @Override
    public String parse(StringReader reader) {
        int start = reader.getCursor();
        while (reader.canRead() && reader.peek() != ' ') {
            reader.skip();
        }
        return reader.getString().substring(start, reader.getCursor());
    }

    @Override
    public ArgumentType<String> getNativeType() {
        return StringArgumentType.word();
    }
}
