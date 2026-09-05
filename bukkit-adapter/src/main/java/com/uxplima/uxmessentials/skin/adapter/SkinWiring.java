package com.uxplima.uxmessentials.skin.adapter;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Server;
import org.bukkit.event.Listener;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.persistence.skin.SkinRepositories;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.skin.HttpClientFetcher;
import com.uxplima.uxmessentials.shared.adapter.outbound.skin.MineSkinService;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.skin.adapter.inbound.command.SkinCommand;
import com.uxplima.uxmessentials.skin.adapter.inbound.listener.SkinLoginListener;
import com.uxplima.uxmessentials.skin.adapter.outbound.GeyserBedrockSkins;
import com.uxplima.uxmessentials.skin.adapter.outbound.MineSkinUploads;
import com.uxplima.uxmessentials.skin.adapter.outbound.PaperSkinView;
import com.uxplima.uxmessentials.skin.adapter.outbound.SkinFolderNames;
import com.uxplima.uxmessentials.skin.application.ClearSkin;
import com.uxplima.uxmessentials.skin.application.DescribeSkin;
import com.uxplima.uxmessentials.skin.application.DressLogin;
import com.uxplima.uxmessentials.skin.application.DropSkin;
import com.uxplima.uxmessentials.skin.application.PurgeSkinCache;
import com.uxplima.uxmessentials.skin.application.SetSkin;
import com.uxplima.uxmessentials.skin.application.SkinConfig;
import com.uxplima.uxmessentials.skin.application.UpdateSkin;
import com.uxplima.uxmessentials.skin.application.port.BedrockSkins;
import com.uxplima.uxmessentials.skin.application.port.SkinRepository;
import com.uxplima.uxmessentials.skin.application.port.SkinView;
import com.uxplima.uxmlib.bedrock.BedrockDetector;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the skin context's adapters and use cases over the injected kernel ports and the persistence DSL, and
 * produces what the plugin registers: the {@code /skin} command and the pre-login listener that dresses a joining
 * player.
 *
 * <p>Three outbound services are built here. The stored choice comes from the jOOQ {@link SkinRepository}; an image
 * (a url or a file the operator dropped in the skin folder) is signed by {@link MineSkinService}, shared with the
 * npc context and given its own API key from this module's config; and a Bedrock player's own skin is read from the
 * public Geyser endpoint, keyed by the xuid the resolved {@link BedrockDetector} reports. Floodgate is a soft
 * dependency and is never named here: without it the detector reports no xuid and the Bedrock step simply resolves
 * nothing.
 *
 * <p>The login listener stops dressing anybody the moment the module stops, through the flag the returned stop hook
 * clears, so a disable or a reload cannot leave a half-wired lookup running against a connection.
 */
@NullMarked
public final class SkinWiring {

    private SkinWiring() {}

    /** Build the skin adapters and use cases from {@code ctx}, {@code persistence} and the resolved detector. */
    public static Wired wire(
            ModuleContext ctx, Persistence persistence, Server server, BedrockDetector bedrock, Path dataFolder) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(bedrock, "bedrock");
        Objects.requireNonNull(dataFolder, "dataFolder");
        SkinConfig config = SkinConfig.from(ctx.config());
        KernelPorts kernel = ctx.kernel();
        Clock clock = Clock.systemUTC();

        SkinRepository repository = SkinRepositories.cached(persistence);
        SkinView view = new PaperSkinView(server, kernel.scheduler());
        MineSkinService mineSkins = new MineSkinService(
                kernel.scheduler(),
                kernel.log(),
                new HttpClientFetcher(kernel.log(), MineSkinService.GENERATE_TIMEOUT),
                config.mineskinApiKey());
        Path skinFolder = dataFolder.resolve(config.skinFolder());
        MineSkinUploads uploads = new MineSkinUploads(mineSkins, skinFolder, kernel.log());
        SkinFolderNames skinFiles = new SkinFolderNames(skinFolder, kernel.scheduler(), kernel.log(), clock);
        BedrockSkins bedrockSkins = new GeyserBedrockSkins(
                GeyserBedrockSkins.XuidLookup.of(bedrock::xuid),
                new HttpClientFetcher(kernel.log()),
                kernel.log(),
                config.bedrockRetries(),
                config.bedrockSource() && bedrock != BedrockDetector.NONE);

        DressLogin dressLogin = new DressLogin(repository, kernel.skins(), bedrockSkins, config, kernel.log());
        SetSkin setSkin = new SetSkin(
                repository,
                kernel.skins(),
                uploads,
                view,
                kernel.permissions(),
                kernel.cooldowns(),
                kernel.events(),
                config,
                clock);
        SkinCommand command = new SkinCommand(
                setSkin,
                new ClearSkin(repository, dressLogin, view, kernel.events(), clock),
                new UpdateSkin(repository, kernel.skins(), uploads, view, kernel.events(), clock),
                new DropSkin(repository),
                new DescribeSkin(repository),
                new PurgeSkinCache(kernel.skins()),
                kernel.playerLookup(),
                skinFiles::get,
                kernel.scheduler(),
                kernel.messages());

        AtomicBoolean dressing = new AtomicBoolean(true);
        SkinLoginListener login = new SkinLoginListener(
                dressLogin, kernel.scheduler(), kernel.log(), config.loginTimeout(), dressing::get);

        return new Wired(List.of(command), List.of(login), repository, () -> dressing.set(false));
    }

    /**
     * Everything the skin module contributes once wired.
     *
     * @param commands the Brigadier commands to register
     * @param listeners the Bukkit listeners to register
     * @param repository the skin store the published query reads from
     * @param stop the teardown run on module stop, after which no login is dressed
     */
    public record Wired(
            List<CommandRegistration> commands, List<Listener> listeners, SkinRepository repository, Runnable stop) {
        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(repository, "repository");
            Objects.requireNonNull(stop, "stop");
        }
    }
}
