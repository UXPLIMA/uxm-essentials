package com.uxplima.uxmessentials.custommenus.adapter.convert;

import java.nio.file.Path;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.Logger;

/**
 * The one-shot admin service behind {@code /menu convert zmenu <path>}: it reads a zMenu inventory YAML (or every
 * {@code .yml} in a directory), runs each through {@link ZMenuConverter}, and writes the result as a
 * {@code menus/<name>.conf} the engine loads on the next {@code /menu reload}. The shared file-walk, write-and-tally
 * and never-reload / never-crash contract live in {@link AbstractMenuConvertService}; this service supplies only the
 * zMenu converter.
 */
public final class ZMenuConvertService extends AbstractMenuConvertService {

    private final ZMenuConverter converter;

    public ZMenuConvertService(Path menusDir, ZMenuConverter converter, Logger log) {
        super(menusDir, log);
        this.converter = Objects.requireNonNull(converter, "converter");
    }

    @Override
    protected ConvertedMenu convertSource(String raw) {
        ZMenuConverter.ConversionResult result = converter.convert(raw);
        return new ConvertedMenu(result.hocon(), result.warnings());
    }
}
