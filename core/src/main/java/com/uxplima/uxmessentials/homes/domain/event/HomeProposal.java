package com.uxplima.uxmessentials.homes.domain.event;

import com.uxplima.uxmessentials.shared.domain.DomainProposal;

/**
 * The homes context's sealed family of proposals: the home changes another plugin may refuse.
 *
 * <p>One per action worth vetoing, and no more. Creating, moving and deleting a home are the three that change where
 * a player can teleport to, so they are the three a claim or protection plugin has a reason to block; renaming a home
 * or changing its icon changes nothing anyone else can care about, and asking about them would cost a listener check
 * on every edit for no benefit.
 *
 * <p>Names are present progressive, the tense of something under way: {@code HomeCreating} precedes the
 * {@link HomeCreated} fact it turns into once it is allowed.
 */
public sealed interface HomeProposal extends DomainProposal permits HomeCreating, HomeDeleting, HomeRelocating {}
