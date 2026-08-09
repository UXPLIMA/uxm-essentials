package com.uxplima.uxmessentials.playerwarps.domain.event;

import com.uxplima.uxmessentials.shared.domain.DomainProposal;

/** The player-warp actions something outside the plugin may refuse. */
public sealed interface PlayerWarpProposal extends DomainProposal permits PlayerWarpCreating, PlayerWarpDeleting {}
