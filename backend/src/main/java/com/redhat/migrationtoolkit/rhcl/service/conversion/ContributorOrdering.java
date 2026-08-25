package com.redhat.migrationtoolkit.rhcl.service.conversion;

import jakarta.annotation.Priority;

/** Resolves {@link Priority} on CDI client proxies (walks superclass chain). */
public final class ContributorOrdering {

    private ContributorOrdering() {
    }

    public static int priorityOf(Object contributor) {
        Class<?> type = contributor.getClass();
        while (type != null && type != Object.class) {
            Priority priority = type.getAnnotation(Priority.class);
            if (priority != null) {
                return priority.value();
            }
            type = type.getSuperclass();
        }
        return 500;
    }
}
