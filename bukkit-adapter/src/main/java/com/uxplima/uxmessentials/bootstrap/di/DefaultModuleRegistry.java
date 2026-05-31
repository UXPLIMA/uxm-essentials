package com.uxplima.uxmessentials.bootstrap.di;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.application.EconomyModule;
import com.uxplima.uxmessentials.homes.application.HomesModule;
import com.uxplima.uxmessentials.itemworld.application.ItemworldModule;
import com.uxplima.uxmessentials.kits.application.KitsModule;
import com.uxplima.uxmessentials.messaging.application.MessagingModule;
import com.uxplima.uxmessentials.moderation.application.ModerationModule;
import com.uxplima.uxmessentials.playerstate.application.PlayerstateModule;
import com.uxplima.uxmessentials.presence.application.PresenceModule;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ListModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.module.ModuleRegistry;
import com.uxplima.uxmessentials.teleport.application.TeleportModule;
import com.uxplima.uxmessentials.warps.application.WarpsModule;
import org.jspecify.annotations.NullMarked;

/**
 * The single registration site for every {@link FeatureModule}.
 *
 * <p>Registration is explicit and ordered dependency-first (prerequisites before dependents):
 * {@code economy} before {@code warps} and {@code kits} because a warp or kit may charge a cost through
 * the economy provider, {@code teleport} before {@code homes} and {@code warps} because they delegate
 * teleport execution. Each context adds exactly one {@code register(...)} line at its dependency-correct
 * position when it lands. The registry stays the greppable, authoritative answer to "which contexts
 * exist?".
 */
@NullMarked
public final class DefaultModuleRegistry implements ModuleRegistry {

    private final ModuleRegistry delegate;

    public DefaultModuleRegistry() {
        this.delegate = new ListModuleRegistry();
        // Feature modules register here in dependency-first order as each context lands. teleport owns
        // all movement orchestration, so it lands before the homes/warps contexts that delegate here.
        // economy registers before warps because a warp may charge a per-warp cost through the economy
        // provider; the economy WarpEconomy bridge is captured during economy wiring and handed to warps.
        delegate.register(new TeleportModule());
        delegate.register(new HomesModule());
        delegate.register(new EconomyModule());
        delegate.register(new WarpsModule());
        // kits registers after economy because a kit may charge a per-kit cost through the economy provider;
        // the economy KitEconomy bridge is captured during economy wiring and handed to kits.
        delegate.register(new KitsModule());
        // playerstate is self-contained — transient in-memory snapshots, no DB and no cross-context bridge —
        // so its position is not dependency-constrained; it lands after kits.
        delegate.register(new PlayerstateModule());
        // messaging soft-couples to moderation (mute-gated sending) and presence (vanish-aware /msg
        // resolution); both gates degrade gracefully when the other module is off, so messaging carries no
        // hard dependency edge and lands here independently of either.
        delegate.register(new MessagingModule());
        // presence owns the vanish state that messaging's /msg resolution and teleport's /tpa listing read
        // through the canSee graph; that coupling is soft (both degrade to "fully visible" without presence),
        // so presence carries no hard dependency edge and lands after the contexts it informs.
        delegate.register(new PresenceModule());
        // moderation provides the real MutePolicy (messaging) and JailGate (teleport) the placeholder NEVER
        // bindings stand in for until it lands; both couplings are soft, so moderation carries no hard
        // dependency edge and lands after the contexts it informs (messaging + teleport were wired first).
        delegate.register(new ModerationModule());
        // itemworld is stateless and ACL-thin (no DB, no persistence) and carries no hard dependency edge — its
        // verbs mutate the live item/entity/world directly — so it lands after the contexts wired above, ahead
        // of vaults, matching the dependency-first ordering documented in docs/10-feature-modules.md §3.
        delegate.register(new ItemworldModule());
        //   … through VaultsModule. The shared kernel is not a module and never appears here.
    }

    @Override
    public ModuleRegistry register(FeatureModule module) {
        return delegate.register(module);
    }

    @Override
    public List<FeatureModule> all() {
        return delegate.all();
    }

    @Override
    public Optional<FeatureModule> byId(ModuleId id) {
        return delegate.byId(id);
    }

    @Override
    public List<FeatureModule> enabledModules(com.uxplima.uxmessentials.shared.application.port.ConfigStore config) {
        return delegate.enabledModules(config);
    }
}
