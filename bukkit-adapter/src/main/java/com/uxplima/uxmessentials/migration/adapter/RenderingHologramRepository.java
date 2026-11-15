package com.uxplima.uxmessentials.migration.adapter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.application.port.HologramView;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import org.jspecify.annotations.NullMarked;

/**
 * The holograms module's repository as the importer sees it: every call goes to the module's own repository, and a
 * saved or deleted hologram is also drawn or taken down on this server. The module renders only what its use cases
 * hand the renderer, so a hologram stored by anything else would sit in the cache with nothing in the world. The
 * renderer hops onto the hologram's region thread itself, so calling it from the importer's async thread is the
 * same route the cross-server reload already takes.
 */
@NullMarked
final class RenderingHologramRepository implements HologramRepository {

    private final HologramRepository delegate;
    private final HologramView renderer;

    RenderingHologramRepository(HologramRepository delegate, HologramView renderer) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    @Override
    public Optional<Hologram> find(HologramName name) {
        return delegate.find(name);
    }

    @Override
    public List<Hologram> all() {
        return delegate.all();
    }

    @Override
    public boolean exists(HologramName name) {
        return delegate.exists(name);
    }

    @Override
    public void save(Hologram hologram) {
        delegate.save(hologram);
        renderer.render(hologram);
    }

    @Override
    public void delete(HologramName name) {
        delegate.delete(name);
        renderer.despawn(name);
    }

    @Override
    public Set<UUID> manualViewers(HologramName name) {
        return delegate.manualViewers(name);
    }

    @Override
    public void showTo(HologramName name, UUID viewer) {
        delegate.showTo(name, viewer);
        renderer.applyManualViewer(name, viewer, true);
    }

    @Override
    public void hideFrom(HologramName name, UUID viewer) {
        delegate.hideFrom(name, viewer);
        renderer.applyManualViewer(name, viewer, false);
    }

    @Override
    public Set<UUID> blacklisted(HologramName name) {
        return delegate.blacklisted(name);
    }

    @Override
    public void addToBlacklist(HologramName name, UUID viewer) {
        delegate.addToBlacklist(name, viewer);
    }

    @Override
    public void removeFromBlacklist(HologramName name, UUID viewer) {
        delegate.removeFromBlacklist(name, viewer);
    }
}
