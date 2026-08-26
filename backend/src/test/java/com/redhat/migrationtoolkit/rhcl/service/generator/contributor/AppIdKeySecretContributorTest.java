package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.Application;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ContributorTestFixtures;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppIdKeySecretContributorTest {

    @Test
    void shouldContribute_true() {
        ApiService service = ContributorTestFixtures.apiServiceWithAuth("appIdKey");
        service.applications.add(ContributorTestFixtures.application("app-1", "key-1"));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        SecretBuilder builder = ContributorTestFixtures.secretBuilder(ctx);

        new AppIdKeySecretContributor().contribute(builder, ctx);

        assertTrue(builder.hasSecret());
    }

    @Test
    void shouldContribute_false() {
        ApiService service = ContributorTestFixtures.apiServiceWithAuth("apiKey");
        ConversionContext ctx = ContributorTestFixtures.context(service);
        SecretBuilder builder = ContributorTestFixtures.secretBuilder(ctx);

        new AppIdKeySecretContributor().contribute(builder, ctx);

        assertFalse(builder.hasSecret());
    }

    @Test
    void contribute_viaContributor_withApplications() {
        ApiService service = ContributorTestFixtures.apiServiceWithAuth("appIdKey");
        service.applications.add(ContributorTestFixtures.application("id-1", "key-1"));
        service.applications.add(ContributorTestFixtures.application("id-2"));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        SecretBuilder builder = ContributorTestFixtures.secretBuilder(ctx);

        new AppIdKeySecretContributor().contribute(builder, ctx);
        String yaml = builder.build();

        assertTrue(yaml.contains("app_id_1: \"id-1\""));
        assertTrue(yaml.contains("app_key_1: \"key-1\""));
        assertTrue(yaml.contains("app_id_2: \"id-2\""));
        assertFalse(yaml.contains("app_key_2:"));
    }

    @Test
    void generate_appIdOnly_emitsWarning() {
        ApiService service = ContributorTestFixtures.apiServiceWithAuth("appIdKey");
        service.applications.add(ContributorTestFixtures.application("only-id"));
        String yaml = AppIdKeySecretContributor.generateAppIdKeySecret(
                "demo-api", ContributorTestFixtures.NAMESPACE, service);
        assertTrue(yaml.contains("app_id_1: \"only-id\""));
        assertTrue(yaml.contains("WARNING: App IDs present but application keys missing"));
    }

    @Test
    void generate_keyOnly_skipsBlankKeys() {
        ApiService service = ContributorTestFixtures.apiServiceWithAuth("appIdKey");
        Application app = ContributorTestFixtures.application("id-with-key", "  ");
        app.keys = List.of("  ", "real-key");
        service.applications.add(app);
        String yaml = AppIdKeySecretContributor.generateAppIdKeySecret(
                "demo-api", ContributorTestFixtures.NAMESPACE, service);
        assertTrue(yaml.contains("app_key_1: \"real-key\""));
    }

    @Test
    void contribute_addsExpectedFragments() {
        ApiService service = ContributorTestFixtures.apiServiceWithAuth("appIdKey");
        service.applications.add(ContributorTestFixtures.application("my-app-id", "my-app-key"));
        String yaml = AppIdKeySecretContributor.generateAppIdKeySecret(
                "demo-api", ContributorTestFixtures.NAMESPACE, service);

        assertTrue(yaml.contains("name: demo-api-app-id-keys"));
        assertTrue(yaml.contains("app_id_1: \"my-app-id\""));
        assertTrue(yaml.contains("app_key_1: \"my-app-key\""));
    }

    @Test
    void contribute_emptySecret_whenNoCredentials() {
        ApiService service = ContributorTestFixtures.apiServiceWithAuth("appIdKey");
        String yaml = AppIdKeySecretContributor.generateAppIdKeySecret(
                "demo-api", ContributorTestFixtures.NAMESPACE, service);

        assertTrue(yaml.contains("WARNING: No App ID/App Key credentials"));
        assertTrue(yaml.contains("stringData: {}"));
    }
}
