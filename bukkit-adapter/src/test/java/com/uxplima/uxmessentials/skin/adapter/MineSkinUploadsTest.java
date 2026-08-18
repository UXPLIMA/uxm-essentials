package com.uxplima.uxmessentials.skin.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.adapter.outbound.skin.HttpFetcher;
import com.uxplima.uxmessentials.shared.adapter.outbound.skin.HttpResponseView;
import com.uxplima.uxmessentials.shared.adapter.outbound.skin.MineSkinService;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import com.uxplima.uxmessentials.skin.adapter.outbound.MineSkinUploads;
import com.uxplima.uxmessentials.skin.domain.SkinModel;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The upload leg of the skin module, against a fake HTTP seam: no test ever reaches MineSkin.
 *
 * <p>Two things are worth pinning here. The file leg has to stay inside the folder the operator named, since it
 * reads the server's own disk on a player's say-so. And every failure has to be an empty answer rather than an
 * exception, because the caller is a command that must always have something to say.
 */
class MineSkinUploadsTest {

    private static final String GENERATED =
            "{\"skin\":{\"texture\":{\"data\":{\"value\":\"dGV4dA==\",\"signature\":\"c2ln\"}}}}";

    @Test
    void aUrlBecomesASignedTexture(@TempDir Path folder) {
        FakeSeam seam = new FakeSeam(HttpResponseView.of(200, GENERATED));

        Optional<SkinTexture> texture = uploads(seam, folder).fromUrl("https://i.imgur.com/a.png", SkinModel.CLASSIC);

        assertThat(texture).contains(new SkinTexture("dGV4dA==", "c2ln"));
        assertThat(seam.bodies).singleElement().asString().contains("https://i.imgur.com/a.png");
    }

    @Test
    void theModelAskedForTravelsWithTheRequest() {
        // MineSkin cuts the image for the arm it is told about, so a slim upload has to say so.
        FakeSeam seam = new FakeSeam(HttpResponseView.of(200, GENERATED));

        uploads(seam, Path.of("skins")).fromUrl("https://i.imgur.com/a.png", SkinModel.SLIM);

        assertThat(seam.bodies).singleElement().asString().contains("\"variant\":\"slim\"");
    }

    @Test
    void aFileInTheFolderIsUploadedAsADataUrl(@TempDir Path folder) throws IOException {
        Files.write(folder.resolve("pirate.png"), new byte[] {1, 2, 3});
        FakeSeam seam = new FakeSeam(HttpResponseView.of(200, GENERATED));

        Optional<SkinTexture> texture = uploads(seam, folder).fromFile("pirate", SkinModel.CLASSIC);

        assertThat(texture).contains(new SkinTexture("dGV4dA==", "c2ln"));
        assertThat(seam.bodies).singleElement().asString().contains("data:image/png;base64,AQID");
    }

    @Test
    void aFileOutsideTheFolderIsRefusedWithoutReadingAnything(@TempDir Path folder) throws IOException {
        Files.write(Objects.requireNonNull(folder.getParent()).resolve("secret.png"), new byte[] {9});
        FakeSeam seam = new FakeSeam(HttpResponseView.of(200, GENERATED));

        assertThat(uploads(seam, folder).fromFile("../secret", SkinModel.CLASSIC))
                .isEmpty();
        assertThat(seam.bodies).isEmpty();
    }

    @Test
    void aMissingFileIsAnEmptyAnswerRatherThanAnException(@TempDir Path folder) {
        FakeSeam seam = new FakeSeam(HttpResponseView.of(200, GENERATED));

        assertThat(uploads(seam, folder).fromFile("nothing", SkinModel.CLASSIC)).isEmpty();
        assertThat(seam.bodies).isEmpty();
    }

    @Test
    void aServiceOutageIsAnEmptyAnswer(@TempDir Path folder) {
        FakeSeam seam = new FakeSeam(HttpResponseView.transportError());

        assertThat(uploads(seam, folder).fromUrl("https://i.imgur.com/a.png", SkinModel.CLASSIC))
                .isEmpty();
    }

    @Test
    void aGarbageUrlNeverReachesTheService(@TempDir Path folder) {
        FakeSeam seam = new FakeSeam(HttpResponseView.of(200, GENERATED));

        assertThat(uploads(seam, folder).fromUrl("not a url", SkinModel.CLASSIC))
                .isEmpty();
        assertThat(seam.bodies).isEmpty();
    }

    private MineSkinUploads uploads(FakeSeam seam, Path folder) {
        MineSkinService service =
                new MineSkinService(new InlineScheduler(), new NoopLogger(), seam, null, Duration.ZERO);
        return new MineSkinUploads(service, folder, new NoopLogger());
    }

    /** An HTTP seam answering every exchange with one scripted response, recording the bodies it was posted. */
    private static final class FakeSeam implements HttpFetcher {

        private final HttpResponseView response;
        private final List<String> bodies = new ArrayList<>();

        private FakeSeam(HttpResponseView response) {
            this.response = response;
        }

        @Override
        public Optional<String> get(URI uri) {
            return Optional.empty();
        }

        @Override
        public HttpResponseView exchange(URI uri, String body, @Nullable String authToken) {
            bodies.add(body);
            return response;
        }
    }

    /** A scheduler that runs everything on the calling thread, so a test never waits. */
    private static final class InlineScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position where, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef who, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }

    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
