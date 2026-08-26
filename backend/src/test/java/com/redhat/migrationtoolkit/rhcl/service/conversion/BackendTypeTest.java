package com.redhat.migrationtoolkit.rhcl.service.conversion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BackendTypeTest {

    @Test
    void values_containsInternalAndExternal() {
        assertEquals(BackendType.INTERNAL, BackendType.valueOf("INTERNAL"));
        assertEquals(BackendType.EXTERNAL, BackendType.valueOf("EXTERNAL"));
        assertEquals(2, BackendType.values().length);
    }
}
