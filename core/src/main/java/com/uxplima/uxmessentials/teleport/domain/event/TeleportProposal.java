package com.uxplima.uxmessentials.teleport.domain.event;

import com.uxplima.uxmessentials.shared.domain.DomainProposal;

/** The teleport actions something outside the plugin may refuse. */
public sealed interface TeleportProposal extends DomainProposal permits PlayerTeleporting {}
