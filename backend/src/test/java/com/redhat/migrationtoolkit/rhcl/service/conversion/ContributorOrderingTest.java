package com.redhat.migrationtoolkit.rhcl.service.conversion;

import jakarta.annotation.Priority;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContributorOrderingTest {

    @Test
    void priorityOf_readsPriorityAnnotationFromClassHierarchy() {
        assertEquals(100, ContributorOrdering.priorityOf(new HighPriorityContributor()));
        assertEquals(500, ContributorOrdering.priorityOf(new DefaultPriorityContributor()));
        assertEquals(900, ContributorOrdering.priorityOf(new SubclassContributor()));
        assertEquals(800, ContributorOrdering.priorityOf(new UnannotatedSubclassContributor()));
    }

    @Priority(100)
    static class HighPriorityContributor {
    }

    static class DefaultPriorityContributor {
    }

    @Priority(800)
    static class BasePriorityContributor {
    }

    @Priority(900)
    static class SubclassContributor extends BasePriorityContributor {
    }

    static class UnannotatedSubclassContributor extends BasePriorityContributor {
    }
}
