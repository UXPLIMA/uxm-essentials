package com.uxplima.uxmessentials.discord;

import java.util.EnumSet;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.api.link.DiscordLinkConfirmation;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

/**
 * The JDA-backed {@link DiscordGateway}. Builds a light JDA client (no member chunking, no voice) with only
 * the {@code GUILD_MESSAGES} intent needed to post text, and disables every cache flag a send-only bridge
 * does not use. opus-java is excluded at the build level (docs/04-build.md §7.5), so the voice codec is never
 * on the classpath.
 *
 * <h2>Threading</h2>
 * {@link #connect(String)} blocks on {@code awaitReady} and is invoked off the main thread by the bootstrap;
 * {@link #sendToChannel(String, String)} uses JDA's asynchronous {@code queue()}, so the REST round-trip runs
 * on JDA's own pool and never on a server tick. The live client is held in an {@link AtomicReference} so a
 * concurrent {@link #shutdown()} observes a whole client or none.
 */
public final class JdaDiscordGateway implements DiscordGateway {

    private static final long READY_TIMEOUT_SECONDS = 30L;

    private final AtomicReference<JDA> client = new AtomicReference<>();

    @Override
    public void connect(String token) throws DiscordConnectException {
        Objects.requireNonNull(token, "token");
        if (token.isBlank()) {
            throw new DiscordConnectException("discord token is blank");
        }
        try {
            JDA jda = build(token).build().awaitReady();
            client.set(jda);
        } catch (InvalidTokenException badToken) {
            throw new DiscordConnectException("discord token rejected by the gateway", badToken);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new DiscordConnectException("interrupted while connecting to discord", interrupted);
        } catch (RuntimeException failure) {
            throw new DiscordConnectException("failed to connect to discord", failure);
        }
    }

    @Override
    public void sendToChannel(String channelId, String message) {
        Objects.requireNonNull(channelId, "channelId");
        Objects.requireNonNull(message, "message");
        JDA jda = client.get();
        if (jda == null || message.isBlank()) {
            return;
        }
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            return;
        }
        channel.sendMessage(message).queue();
    }

    @Override
    public void enableLinking(DiscordLinkConfirmation confirmation) {
        Objects.requireNonNull(confirmation, "confirmation");
        JDA jda = client.get();
        if (jda == null) {
            return;
        }
        jda.addEventListener(new LinkSlashListener(confirmation));
        jda.upsertCommand(LinkSlashListener.COMMAND_NAME, "Link your Discord account to your Minecraft account")
                .addOption(OptionType.STRING, LinkSlashListener.CODE_OPTION, "The code from /discordlink in game", true)
                .queue();
    }

    @Override
    public boolean isConnected() {
        JDA jda = client.get();
        return jda != null && jda.getStatus() == JDA.Status.CONNECTED;
    }

    @Override
    public void shutdown() {
        JDA jda = client.getAndSet(null);
        if (jda == null) {
            return;
        }
        jda.shutdown();
        try {
            boolean clean = jda.awaitShutdown(READY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!clean) {
                jda.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            jda.shutdownNow();
        }
    }

    private static JDABuilder build(String token) {
        return JDABuilder.createLight(token, EnumSet.of(GatewayIntent.GUILD_MESSAGES))
                .disableCache(EnumSet.allOf(CacheFlag.class));
    }
}
