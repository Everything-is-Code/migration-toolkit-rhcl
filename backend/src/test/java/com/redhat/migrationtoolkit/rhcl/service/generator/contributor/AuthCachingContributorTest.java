package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.model.kuadrant.CacheConfig;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ContributorTestFixtures;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AuthCachingContributorTest {

    private static final ManifestSerializer SERIALIZER = new ManifestSerializer();

    @Test
    void shouldContribute_true() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(ContributorTestFixtures.policy("caching", true,
                Map.of("caching_type", "strict")));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        AuthPolicyBuilder builder = ContributorTestFixtures.authPolicyBuilder(ctx);

        new AuthCachingContributor().contribute(builder, ctx);

        assertNotNull(builder.cacheConfig());
    }

    @Test
    void shouldContribute_false() {
        ConversionContext ctx = ContributorTestFixtures.context(ContributorTestFixtures.apiService());
        AuthPolicyBuilder builder = ContributorTestFixtures.authPolicyBuilder(ctx);

        new AuthCachingContributor().contribute(builder, ctx);

        assertNull(builder.cacheConfig());
    }

    @Test
    void contribute_withApiKeyAuth_interpolatesCacheConfigInBuiltYaml() {
        ApiService service = ContributorTestFixtures.apiServiceWithAuth("apiKey");
        service.policies.add(ContributorTestFixtures.policy("caching", true,
                Map.of("caching_type", "allow")));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        AuthPolicyBuilder builder = ContributorTestFixtures.authPolicyBuilder(ctx);

        new AuthCachingContributor().contribute(builder, ctx);
        new ApiKeyAuthenticationContributor().contribute(builder, ctx);
        String yaml = SERIALIZER.toYaml(builder.build());

        assertTrue(yaml.contains("api-key-auth:"));
        assertTrue(yaml.contains("cache:"));
        assertTrue(yaml.contains("ttl: 300"));
    }

    @Test
    void contribute_cachingPolicy_setsCacheConfigOnBuilder() {
        ApiService service = ContributorTestFixtures.apiService();
        service.policies.add(ContributorTestFixtures.policy("3scale_auth_caching", true,
                Map.of("caching_type", "allow")));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        AuthPolicyBuilder builder = ContributorTestFixtures.authPolicyBuilder(ctx);

        new AuthCachingContributor().contribute(builder, ctx);

        CacheConfig cache = builder.cacheConfig();
        assertNotNull(cache);
        assertEquals(300, cache.ttl());
    }

    @Test
    void contribute_addsExpectedFragments() {
        Policy strict = ContributorTestFixtures.policy("3scale_auth_caching", true,
                Map.of("caching_type", "strict"));
        Policy allow = ContributorTestFixtures.policy("caching", true,
                Map.of("caching_type", "allow"));
        Policy resilient = ContributorTestFixtures.policy("caching", true,
                Map.of("caching_type", "resilient"));

        assertEquals(60, AuthCachingContributor.buildCacheConfig(strict).ttl());
        assertEquals(300, AuthCachingContributor.buildCacheConfig(allow).ttl());
        assertEquals(600, AuthCachingContributor.buildCacheConfig(resilient).ttl());
        assertNull(AuthCachingContributor.buildCacheConfig(null));
    }
}
