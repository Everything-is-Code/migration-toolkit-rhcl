package com.redhat.migrationtoolkit.rhcl.service;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Arrays;

/**
 * Central lookup for enabled 3scale policies on an {@link ApiService}.
 */
@ApplicationScoped
public class PolicyFinder {

    /**
     * First enabled policy whose name matches {@code policyName} (case-insensitive).
     */
    public Policy findEnabled(ApiService service, String policyName) {
        return findFirstMatching(service, policyName, true);
    }

    /**
     * First enabled policy whose name matches {@code policyName} exactly (case-sensitive).
     */
    public Policy findEnabledExact(ApiService service, String policyName) {
        return findFirstMatching(service, policyName, false);
    }

    /**
     * First enabled policy matching any of the given names.
     *
     * @param ignoreCase when true, compares names with {@link String#equalsIgnoreCase}
     */
    public Policy findEnabledAny(ApiService service, boolean ignoreCase, String... policyNames) {
        if (service == null || service.policies == null || policyNames == null || policyNames.length == 0) {
            return null;
        }
        return service.policies.stream()
                .filter(p -> Boolean.TRUE.equals(p.enabled) && p.name != null)
                .filter(p -> Arrays.stream(policyNames).anyMatch(name -> matchesName(p.name, name, ignoreCase)))
                .findFirst()
                .orElse(null);
    }

    private Policy findFirstMatching(ApiService service, String policyName, boolean ignoreCase) {
        if (service == null || service.policies == null || policyName == null) {
            return null;
        }
        return service.policies.stream()
                .filter(p -> Boolean.TRUE.equals(p.enabled)
                        && p.name != null
                        && matchesName(p.name, policyName, ignoreCase))
                .findFirst()
                .orElse(null);
    }

    private static boolean matchesName(String actual, String expected, boolean ignoreCase) {
        return ignoreCase ? actual.equalsIgnoreCase(expected) : actual.equals(expected);
    }
}
