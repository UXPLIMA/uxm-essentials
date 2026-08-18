package com.uxplima.uxmessentials.skin.adapter.outbound;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.adapter.outbound.skin.MineSkinService;
import com.uxplima.uxmessentials.shared.adapter.outbound.skin.SignedSkin;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import com.uxplima.uxmessentials.skin.application.port.SkinUploads;
import com.uxplima.uxmessentials.skin.domain.SkinModel;
import org.jspecify.annotations.NullMarked;

/**
 * Turns an image into a signed texture through the shared MineSkin client, which the npc context uses as well, so
 * a server configures one API key and shares one rate limit.
 *
 * <p>A url goes straight to the service. A file is read off the server's own disk and handed over as a base64
 * data url, which the v2 generate endpoint documents its {@code url} field as accepting, so no second transport
 * is needed for the two cases.
 *
 * <p>The file leg is deliberately narrow: only a {@code .png} directly inside the configured folder is readable,
 * and a name that climbs out of it (or points anywhere else) is refused before anything is opened. Every failure
 * is an empty result, logged with the name that caused it.
 */
@NullMarked
public final class MineSkinUploads implements SkinUploads {

    /** What a png data url is prefixed with, per the data-url form the generate endpoint accepts. */
    private static final String DATA_URL_PREFIX = "data:image/png;base64,";

    /** A skin image is 8 KiB at most in practice; the cap keeps a stray large file out of memory and off the wire. */
    private static final long MAX_FILE_BYTES = 512L * 1024L;

    private final MineSkinService service;
    private final Path folder;
    private final Logger log;

    public MineSkinUploads(MineSkinService service, Path folder, Logger log) {
        this.service = Objects.requireNonNull(service, "service");
        this.folder = Objects.requireNonNull(folder, "folder").toAbsolutePath().normalize();
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public Optional<SkinTexture> fromUrl(String url, SkinModel model) {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(model, "model");
        return service.generateNow(url, variant(model)).map(SignedSkin::texture);
    }

    @Override
    public Optional<SkinTexture> fromFile(String fileName, SkinModel model) {
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(model, "model");
        return readable(fileName)
                .flatMap(this::dataUrl)
                .flatMap(dataUrl -> service.generateNow(dataUrl, variant(model)))
                .map(SignedSkin::texture);
    }

    /** The png {@code fileName} names inside the skin folder, or empty when it is not one we may read. */
    private Optional<Path> readable(String fileName) {
        Path file = folder.resolve(fileName + ".png").toAbsolutePath().normalize();
        if (!file.startsWith(folder)) {
            log.warn("skin: refused the skin file {}, which points outside {}", fileName, folder);
            return Optional.empty();
        }
        if (!Files.isRegularFile(file)) {
            log.warn("skin: there is no skin file {} in {}", fileName, folder);
            return Optional.empty();
        }
        return Optional.of(file);
    }

    /** {@code file} as the base64 data url the generate endpoint accepts, or empty when it cannot be read. */
    private Optional<String> dataUrl(Path file) {
        try {
            if (Files.size(file) > MAX_FILE_BYTES) {
                log.warn("skin: the skin file {} is far larger than a skin image and was refused", file);
                return Optional.empty();
            }
            return Optional.of(DATA_URL_PREFIX + Base64.getEncoder().encodeToString(Files.readAllBytes(file)));
        } catch (IOException | UncheckedIOException unreadable) {
            log.warn("skin: the skin file {} could not be read ({})", file, unreadable.toString());
            return Optional.empty();
        }
    }

    /** The variant name the service takes, so an uploaded image is cut for the arm the uploader drew. */
    private static String variant(SkinModel model) {
        return model.slim() ? "slim" : "classic";
    }
}
