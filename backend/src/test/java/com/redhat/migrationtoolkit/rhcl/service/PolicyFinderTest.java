package com.redhat.migrationtoolkit.rhcl.service;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PolicyFinderTest {

    private PolicyFinder finder;

    @BeforeEach
    void setUp() {
        finder = new PolicyFinder();
    }

    @Test
    void findEnabled_presentPolicy_returnsPolicy() {
        Policy cors = policy("cors", true);
        ApiService service = serviceWith(cors);

        assertEquals(cors, finder.findEnabled(service, "cors"));
    }

    @Test
    void findEnabled_caseInsensitive() {
        Policy cors = policy("CORS", true);
        ApiService service = serviceWith(cors);

        assertEquals(cors, finder.findEnabled(service, "cors"));
    }

    @Test
    void findEnabled_disabled_returnsNull() {
        Policy cors = policy("cors", false);
        ApiService service = serviceWith(cors);

        assertNull(finder.findEnabled(service, "cors"));
    }

    @Test
    void findEnabled_absent_returnsNull() {
        ApiService service = serviceWith(policy("jwt", true));

        assertNull(finder.findEnabled(service, "cors"));
    }

    @Test
    void findEnabledExact_requiresExactName() {
        Policy logging = policy("Logging", true);
        ApiService service = serviceWith(logging);

        assertNull(finder.findEnabledExact(service, "logging"));
        assertEquals(logging, finder.findEnabledExact(service, "Logging"));
    }

    @Test
    void findEnabledAny_matchesFirstAlias() {
        Policy caching = policy("caching", true);
        ApiService service = serviceWith(caching);

        assertEquals(caching, finder.findEnabledAny(service, true, "3scale_auth_caching", "caching"));
    }

    @Test
    void findEnabledAny_anonymousExactNames() {
        Policy anon = policy("anonymous_access", true);
        ApiService service = serviceWith(anon);

        assertEquals(anon, finder.findEnabledAny(service, false, "default_credentials", "anonymous_access"));
    }

    @Test
    void findEnabledAny_contentLimitsAliases() {
        Policy limits = policy("payload_limits", true);
        ApiService service = serviceWith(limits);

        assertEquals(limits, finder.findEnabledAny(service, true, "content_limits", "payload_limits"));
    }

    private static ApiService serviceWith(Policy... policies) {
        ApiService service = new ApiService();
        service.policies = new ArrayList<>(List.of(policies));
        return service;
    }

    private static Policy policy(String name, boolean enabled) {
        Policy policy = new Policy();
        policy.name = name;
        policy.enabled = enabled;
        return policy;
    }
}
