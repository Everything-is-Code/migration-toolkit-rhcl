package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ContributorTestFixtures;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.support.YamlAssertions;
import com.redhat.migrationtoolkit.rhcl.util.ConversionConstants;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultCredentialsSecretContributorTest {

    @Test
    void shouldContribute_true() {
        ConversionContext ctx = ContributorTestFixtures.context(ContributorTestFixtures.apiService());
        SecretBuilder builder = ContributorTestFixtures.secretBuilder(ctx);

        new DefaultCredentialsSecretContributor().contribute(builder, ctx);

        assertTrue(builder.hasSecret());
    }

    @Test
    void shouldContribute_false_whenSecretAlreadySet() {
        ConversionContext ctx = ContributorTestFixtures.context(ContributorTestFixtures.apiService());
        SecretBuilder builder = ContributorTestFixtures.secretBuilder(ctx);
        builder.beginOpaqueSecret("existing-secret");
        builder.addStringData("token", "existing");

        new DefaultCredentialsSecretContributor().contribute(builder, ctx);

        assertFalse(builder.build().contains(ConversionConstants.CREDENTIAL_PLACEHOLDER));
    }

    @Test
    void contribute_addsExpectedFragments() {
        ConversionContext ctx = ContributorTestFixtures.context(ContributorTestFixtures.apiService());
        SecretBuilder builder = ContributorTestFixtures.secretBuilder(ctx);

        new DefaultCredentialsSecretContributor().contribute(builder, ctx);
        String yaml = builder.build();
        Map<String, Object> parsed = YamlAssertions.parse(yaml);
        @SuppressWarnings("unchecked")
        Map<String, String> stringData = (Map<String, String>) parsed.get("stringData");

        assertEquals(ConversionConstants.CREDENTIAL_PLACEHOLDER, stringData.get("client-id"));
        assertEquals(ConversionConstants.CREDENTIAL_PLACEHOLDER, stringData.get("client-secret"));
    }
}
