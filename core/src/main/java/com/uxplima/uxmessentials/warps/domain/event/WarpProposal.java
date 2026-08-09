package com.uxplima.uxmessentials.warps.domain.event;

import com.uxplima.uxmessentials.shared.domain.DomainProposal;

/** The server-warp actions something outside the plugin may refuse. */
public sealed interface WarpProposal extends DomainProposal permits WarpCreating, WarpDeleting {}
