package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WelcomeMessage;
import org.jspecify.annotations.NullMarked;

/**
 * A uniform editable view over a server {@link Warp} and a {@link PlayerWarp} so the warp editor's click
 * handler can act on either through one code path. Both domain types carry the identical set of {@code withX}
 * copy methods and a save; the two records simply don't share a Java supertype, so this adapter bridges them.
 * Each mutator loads the current warp, applies the change and saves through the owning repository — the editor
 * never branches on "server vs player" itself.
 */
@NullMarked
interface EditableWarp {

    boolean isLocked();

    List<WelcomeMessage> welcomeMessages();

    void setIconMaterial(Optional<String> material);

    void setLocked(boolean locked);

    void setPassword(Optional<String> password);

    void clearSounds();

    void clearParticles();

    void setWarmupOverride(Optional<Double> seconds);

    void setCooldownOverride(Optional<Double> seconds);

    void setDepartureSound(Optional<String> sound);

    void setArrivalSound(Optional<String> sound);

    void setDepartureParticle(Optional<String> particle);

    void setArrivalParticle(Optional<String> particle);

    void setWelcomeMessages(List<WelcomeMessage> messages);

    /** Wrap a loaded server warp; every change is written back through {@code repository}. */
    static EditableWarp ofServer(Warp warp, WarpRepository repository) {
        return new EditableWarp() {
            private Warp current = warp;

            @Override
            public boolean isLocked() {
                return current.isLocked();
            }

            @Override
            public List<WelcomeMessage> welcomeMessages() {
                return current.welcomeMessages();
            }

            @Override
            public void setIconMaterial(Optional<String> material) {
                save(current.withIconMaterial(material));
            }

            @Override
            public void setLocked(boolean locked) {
                save(current.withLocked(locked));
            }

            @Override
            public void setPassword(Optional<String> password) {
                save(current.withPassword(password));
            }

            @Override
            public void clearSounds() {
                save(current.withDepartureSound(Optional.empty()).withArrivalSound(Optional.empty()));
            }

            @Override
            public void clearParticles() {
                save(current.withDepartureParticle(Optional.empty()).withArrivalParticle(Optional.empty()));
            }

            @Override
            public void setWarmupOverride(Optional<Double> seconds) {
                save(current.withWarmupOverride(seconds));
            }

            @Override
            public void setCooldownOverride(Optional<Double> seconds) {
                save(current.withCooldownOverride(seconds));
            }

            @Override
            public void setDepartureSound(Optional<String> sound) {
                save(current.withDepartureSound(sound));
            }

            @Override
            public void setArrivalSound(Optional<String> sound) {
                save(current.withArrivalSound(sound));
            }

            @Override
            public void setDepartureParticle(Optional<String> particle) {
                save(current.withDepartureParticle(particle));
            }

            @Override
            public void setArrivalParticle(Optional<String> particle) {
                save(current.withArrivalParticle(particle));
            }

            @Override
            public void setWelcomeMessages(List<WelcomeMessage> messages) {
                save(current.withWelcomeMessages(messages));
            }

            private void save(Warp updated) {
                current = updated;
                repository.save(updated);
            }
        };
    }

    /** Wrap a loaded player warp; every change is written back through {@code repository}. */
    static EditableWarp ofPlayer(PlayerWarp warp, PlayerWarpRepository repository) {
        return new EditableWarp() {
            private PlayerWarp current = warp;

            @Override
            public boolean isLocked() {
                return current.isLocked();
            }

            @Override
            public List<WelcomeMessage> welcomeMessages() {
                return current.welcomeMessages();
            }

            @Override
            public void setIconMaterial(Optional<String> material) {
                save(current.withIconMaterial(material));
            }

            @Override
            public void setLocked(boolean locked) {
                save(current.withLocked(locked));
            }

            @Override
            public void setPassword(Optional<String> password) {
                save(current.withPassword(password));
            }

            @Override
            public void clearSounds() {
                save(current.withDepartureSound(Optional.empty()).withArrivalSound(Optional.empty()));
            }

            @Override
            public void clearParticles() {
                save(current.withDepartureParticle(Optional.empty()).withArrivalParticle(Optional.empty()));
            }

            @Override
            public void setWarmupOverride(Optional<Double> seconds) {
                save(current.withWarmupOverride(seconds));
            }

            @Override
            public void setCooldownOverride(Optional<Double> seconds) {
                save(current.withCooldownOverride(seconds));
            }

            @Override
            public void setDepartureSound(Optional<String> sound) {
                save(current.withDepartureSound(sound));
            }

            @Override
            public void setArrivalSound(Optional<String> sound) {
                save(current.withArrivalSound(sound));
            }

            @Override
            public void setDepartureParticle(Optional<String> particle) {
                save(current.withDepartureParticle(particle));
            }

            @Override
            public void setArrivalParticle(Optional<String> particle) {
                save(current.withArrivalParticle(particle));
            }

            @Override
            public void setWelcomeMessages(List<WelcomeMessage> messages) {
                save(current.withWelcomeMessages(messages));
            }

            private void save(PlayerWarp updated) {
                current = updated;
                repository.save(updated);
            }
        };
    }
}
