package com.openmanus.saa.agent;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class IdAccessPolicy {

    private final CapabilityAccessMode mode;
    private final Set<String> ids;

    public IdAccessPolicy(CapabilityAccessMode mode, Set<String> ids) {
        this.mode = mode == null ? CapabilityAccessMode.ALLOW_LIST : mode;
        this.ids = ids == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(ids));
    }

    public CapabilityAccessMode getMode() {
        return mode;
    }

    public Set<String> getIds() {
        return ids;
    }

    public boolean isDenied() {
        return mode == CapabilityAccessMode.DENY_ALL;
    }

    public Set<String> resolveAllowed(Collection<String> availableIds) {
        if (availableIds == null || availableIds.isEmpty()) {
            return Set.of();
        }
        return switch (mode) {
            case ALLOW_ALL -> Set.copyOf(new LinkedHashSet<>(availableIds));
            case DENY_ALL -> Set.of();
            case ALLOW_LIST -> {
                Set<String> allowed = new LinkedHashSet<>();
                for (String availableId : availableIds) {
                    if (ids.contains(availableId)) {
                        allowed.add(availableId);
                    }
                }
                yield Set.copyOf(allowed);
            }
        };
    }

    public boolean allows(String id) {
        return switch (mode) {
            case ALLOW_ALL -> true;
            case DENY_ALL -> false;
            case ALLOW_LIST -> ids.contains(id);
        };
    }
}
