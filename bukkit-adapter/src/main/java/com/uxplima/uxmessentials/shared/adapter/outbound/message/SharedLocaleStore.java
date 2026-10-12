package com.uxplima.uxmessentials.shared.adapter.outbound.message;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import com.uxplima.uxmessentials.shared.application.port.LocaleStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.text.language.FilePlayerLanguages;
import com.uxplima.uxmlib.text.language.HeldChoice;
import com.uxplima.uxmlib.text.language.SharedLanguage;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@link LocaleStore}: a player's {@code /lang} choice, kept on the player's own data under
 * {@code uxmlib:language}, the key every plugin of ours reads. A choice made here is the language the others
 * write in, and a choice made in one of them is the language this plugin writes in.
 *
 * <p>It used to live under this plugin's own key, {@code uxmessentials:lang-override}. The first time a player
 * is read on their own thread, a choice under the old key is moved to the shared one and the old key is
 * removed. A shared choice already there is newer, so it wins and the old one is only removed.
 *
 * <p>The server writes a player's data only while they are on it. A choice an operator makes for a player who
 * is away waits in {@code languages.conf} and is applied by {@link #settle} when they join.
 *
 * <h2>Concurrency</h2>
 * The player's data is touched only on the thread that owns the player. Anywhere else the last answer read
 * there is used, and a write waits for that thread: {@link SharedLanguage} keeps both rules.
 */
@NullMarked
public final class SharedLocaleStore implements LocaleStore {

    private final SharedLanguage shared;

    private final NamespacedKey oldKey;

    private final FilePlayerLanguages held;

    private final Consumer<Runnable> offThread;

    /**
     * @param oldKey where this plugin kept a choice before it was shared
     * @param held where a choice for a player who is away waits
     * @param offThread runs the write of {@code held} to disk away from the thread that asked for it
     */
    public SharedLocaleStore(
            SharedLanguage shared, NamespacedKey oldKey, FilePlayerLanguages held, Consumer<Runnable> offThread) {
        this.shared = Objects.requireNonNull(shared, "shared");
        this.oldKey = Objects.requireNonNull(oldKey, "oldKey");
        this.held = Objects.requireNonNull(held, "held");
        this.offThread = Objects.requireNonNull(offThread, "offThread");
    }

    @Override
    public Optional<Locale> override(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        Player online = Bukkit.getPlayer(player.uuid());
        if (online == null) {
            return Optional.empty();
        }
        moveTheOldChoice(online);
        return shared.read(online).flatMap(mark -> Optional.ofNullable(mark.locale()));
    }

    @Override
    public void setOverride(PlayerRef player, Locale locale) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(locale, "locale");
        share(player.uuid(), locale);
    }

    @Override
    public void clearOverride(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        share(player.uuid(), null);
    }

    /**
     * Apply a choice that waited for this player, and move one kept under the old key. Called when the player
     * joins, on the thread that owns them.
     */
    public void settle(Player player) {
        Objects.requireNonNull(player, "player");
        if (!shared.owns(player)) {
            return;
        }
        UUID id = player.getUniqueId();
        held.held(id).ifPresent(choice -> {
            shared.write(player, choice.locale());
            held.release(id);
            save();
        });
        moveTheOldChoice(player);
        shared.read(player);
    }

    /** Write the choices that wait for absent players, for a plugin that is stopping. */
    public void flush() {
        held.save();
    }

    private void share(UUID id, @Nullable Locale locale) {
        Player online = Bukkit.getPlayer(id);
        if (online == null) {
            held.hold(id, new HeldChoice(locale));
            save();
            return;
        }
        if (held.held(id).isPresent()) {
            held.release(id);
            save();
        }
        shared.write(online, locale);
    }

    private void moveTheOldChoice(Player player) {
        if (!shared.owns(player)) {
            return;
        }
        PersistentDataContainer data = player.getPersistentDataContainer();
        String old = data.get(oldKey, PersistentDataType.STRING);
        if (old == null) {
            return;
        }
        data.remove(oldKey);
        Locale locale = Locale.forLanguageTag(old.strip());
        if (shared.read(player).isEmpty() && !locale.getLanguage().isEmpty()) {
            shared.write(player, locale);
        }
    }

    private void save() {
        offThread.accept(held::save);
    }
}
