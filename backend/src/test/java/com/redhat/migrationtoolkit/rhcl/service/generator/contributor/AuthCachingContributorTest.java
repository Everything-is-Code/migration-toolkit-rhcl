package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ContributorTestFixtures;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthCachingContributorTest {

    @Test
    void shouldContribute_true() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(ContributorTestFixtures.policy("caching", true,
                Map.of("caching_type", "strict")));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        AuthPolicyBuilder builder = ContributorTestFixtures.authPolicyBuilder(ctx);

        new AuthCachingContributor().contribute(builder, ctx);

        assertTrue(builder.authCacheBlock().contains("cache:"));
    }

    @Test
    void shouldContribute_false() {
        ConversionContext ctx = ContributorTestFixtures.context(ContributorTestFixtures.apiService());
        AuthPolicyBuilder builder = ContributorTestFixtures.authPolicyBuilder(ctx);

        new AuthCachingContributor().contribute(builder, ctx);

        assertTrue(builder.authCacheBlock().isEmpty());
    }

    @Test
    void contribute_withApiKeyAuth_interpolatesCacheBlockInBuiltYaml() {
        ApiService service = ContributorTestFixtures.apiServiceWithAuth("apiKey");
        service.policies.add(ContributorTestFixtures.policy("caching", true,
                Map.of("caching_type", "allow")));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        AuthPolicyBuilder builder = ContributorTestFixtures.authPolicyBuilder(ctx);

        new AuthCachingContributor().contribute(builder, ctx);
        new ApiKeyAuthenticationContributor().contribute(builder, ctx);
        String yaml = builder.build();

        assertTrue(yaml.contains("api-key-auth:"));
        assertTrue(yaml.contains("cache:"));
        assertTrue(yaml.contains("ttl: 300"));
    }

    @Test
    void contribute_cachingPolicy_setsBlockOnBuilder() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(ContributorTestFixtures.policy("3scale_auth_caching", true,
                Map.of("caching_type", "allow")));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        AuthPolicyBuilder builder = ContributorTestFixtures.authPolicyBuilder(ctx);

        new AuthCachingContributor().contribute(builder, ctx);

        assertTrue(builder.authCacheBlock().contains("ttl: 300"));
    }

    @Test
    void contribute_addsExpectedFragments() {
        Policy strict = ContributorTestFixtures.policy("3scale_auth_caching", true,
                Map.of("caching_type", "strict"));
        Policy allow = ContributorTestFixtures.policy("caching", true,
                Map.of("caching_type", "allow"));
        Policy resilient = ContributorTestFixtures.policy("caching", true,
                Map.of("caching_type", "resilient"));

        assertTrue(AuthCachingContributor.buildAuthCacheBlock(strict).contains("ttl: 60"));
        assertTrue(AuthCachingContributor.buildAuthCacheBlock(allow).contains("ttl: 300"));
        assertTrue(AuthCachingContributor.buildAuthCacheBlock(resilient).contains("ttl: 600"));
        assertEquals("", AuthCachingContributor.buildAuthCacheBlock(null));
    }
}
