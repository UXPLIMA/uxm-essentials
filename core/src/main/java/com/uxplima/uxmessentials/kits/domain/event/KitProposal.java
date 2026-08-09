package com.uxplima.uxmessentials.kits.domain.event;

import com.uxplima.uxmessentials.shared.domain.DomainProposal;

/** The kit actions something outside the plugin may refuse. */
public sealed interface KitProposal extends DomainProposal permits KitClaiming {}
