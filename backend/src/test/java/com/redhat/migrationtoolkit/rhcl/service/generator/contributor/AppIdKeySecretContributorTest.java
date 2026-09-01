package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.Application;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ContributorTestFixtures;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.support.YamlAssertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        Map<String, Object> parsed = YamlAssertions.parse(yaml);
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) parsed.get("metadata");
        @SuppressWarnings("unchecked")
        Map<String, String> stringData = (Map<String, String>) parsed.get("stringData");

        assertEquals("demo-api-app-id-keys", metadata.get("name"));
        assertEquals("my-app-id", stringData.get("app_id_1"));
        assertEquals("my-app-key", stringData.get("app_key_1"));
    }

    @Test
    void contribute_skipsWhenBuilderAlreadyHasSecret() {
        ApiService service = ContributorTestFixtures.apiServiceWithAuth("appIdKey");
        ConversionContext ctx = ContributorTestFixtures.context(service);
        SecretBuilder builder = ContributorTestFixtures.secretBuilder(ctx);
        builder.beginOpaqueSecret("existing-secret");

        new AppIdKeySecretContributor().contribute(builder, ctx);

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) YamlAssertions.parse(builder.build()).get("metadata");
        assertEquals("existing-secret", metadata.get("name"));
    }

    @Test
    void contribute_skipsWhenAuthenticationMissing() {
        ApiService service = ContributorTestFixtures.apiService();
        ConversionContext ctx = ContributorTestFixtures.context(service);
        SecretBuilder builder = ContributorTestFixtures.secretBuilder(ctx);

        new AppIdKeySecretContributor().contribute(builder, ctx);

        assertFalse(builder.hasSecret());
    }

    @Test
    void generate_nullSystemName_usesProvidedName() {
        ApiService service = ContributorTestFixtures.apiServiceWithAuth("appIdKey");
        service.systemName = null;
        service.applications.add(ContributorTestFixtures.application("id-1", "key-1"));

        String yaml = AppIdKeySecretContributor.generateAppIdKeySecret(
                "fallback-api", ContributorTestFixtures.NAMESPACE, service);

        assertTrue(yaml.contains("fallback-api-app-id-keys"));
    }

    @Test
    void contribute_skipsBlankApplicationEntries() {
        ApiService service = ContributorTestFixtures.apiServiceWithAuth("appIdKey");
        Application blank = new Application();
        blank.appId = "  ";
        blank.keys = List.of("  ");
        service.applications.add(blank);
        service.applications.add(ContributorTestFixtures.application("id-1", "key-1"));
        ConversionContext ctx = ContributorTestFixtures.context(service);
        SecretBuilder builder = ContributorTestFixtures.secretBuilder(ctx);

        new AppIdKeySecretContributor().contribute(builder, ctx);
        String yaml = builder.build();

        assertTrue(yaml.contains("app_id_1: \"id-1\""));
        assertFalse(yaml.contains("app_id_2:"));
    }

    @Test
    void contribute_emptySecret_whenNoCredentials() {
        ApiService service = ContributorTestFixtures.apiServiceWithAuth("appIdKey");
        String yaml = AppIdKeySecretContributor.generateAppIdKeySecret(
                "demo-api", ContributorTestFixtures.NAMESPACE, service);
        Map<String, Object> parsed = YamlAssertions.parse(yaml);

        assertTrue(yaml.contains("WARNING: No App ID/App Key credentials"));
        Object stringData = parsed.get("stringData");
        assertTrue(stringData == null || (stringData instanceof Map<?, ?> map && map.isEmpty()));
    }
}
