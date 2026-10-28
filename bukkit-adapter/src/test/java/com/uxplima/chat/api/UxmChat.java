package com.uxplima.chat.api;

import java.util.Set;

/** Stands in for uxmChat's service in tests: only its class name and activeFeatures matter. */
public final class UxmChat {

    private final Set<String> features;

    public UxmChat(Set<String> features) {
        this.features = Set.copyOf(features);
    }

    public Set<String> activeFeatures() {
        return features;
    }
}
