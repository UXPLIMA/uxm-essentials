package com.uxplima.uxmessentials.skin.application.port;

import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import com.uxplima.uxmessentials.skin.domain.SkinModel;
import org.jspecify.annotations.NullMarked;

/**
 * Turns an image into a signed profile texture, through whichever skin service the adapter wires.
 *
 * <p>An unsigned texture renders wrongly for other players on some clients, which is why an image is uploaded to
 * be signed rather than encoded locally. Both methods block on the network, so a caller reaches them only off a
 * tick thread; an outage, a refused file or an unreadable image is an empty result, never an exception.
 */
@NullMarked
public interface SkinUploads {

    /** The signed texture for the image published at {@code url}, or empty when it could not be made. */
    Optional<SkinTexture> fromUrl(String url, SkinModel model);

    /** The signed texture for {@code fileName} in the configured skin folder, or empty when it could not be made. */
    Optional<SkinTexture> fromFile(String fileName, SkinModel model);
}
