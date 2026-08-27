package com.redhat.migrationtoolkit.rhcl.service.conversion;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadmeSupportTest {

    @Test
    void build_internalBackend_includesCoreSections() {
        ApiService service = ConversionSupportTestFixtures.apiService("demo-api");
        service.backends.add(ConversionSupportTestFixtures.backend(
                "primary", "http://my-svc.demo.svc:8080", "/"));
        ConversionContext ctx = ConversionSupportTestFixtures.context(service, "demo-ns");

        String readme = ReadmeSupport.build(
                ctx,
                new ReadmeNotes(),
                new com.redhat.migrationtoolkit.rhcl.service.PolicyFinder(),
                new PolicyConfigSupport(),
                RateLimitSupport.forManual());

        assertTrue(readme.contains("## Internal Backend (Service within OpenShift)"));
        assertTrue(readme.contains("**Backend type:** Internal OpenShift Service"));
    }

    @Test
    void build_externalBackendAndMultiBackendSections() {
        ApiService service = ConversionSupportTestFixtures.richService();
        ConversionContext ctx = ConversionSupportTestFixtures.context(service, "demo-ns");

        String readme = ReadmeSupport.build(
                ctx,
                new ReadmeNotes(),
                new com.redhat.migrationtoolkit.rhcl.service.PolicyFinder(),
                new PolicyConfigSupport(),
                RateLimitSupport.forManual());

        assertTrue(readme.contains("## External Backend (External HTTPS Service)"));
        assertTrue(readme.contains("Multiple backends (path-first)"));
        assertTrue(readme.contains("envoyfilter-content-limits.yaml"));
    }

    @Test
    void build_activeMaintenance_listsFileAndOverlayNote() {
        ApiService service = ConversionSupportTestFixtures.apiService("demo-api");
        service.backends.add(ConversionSupportTestFixtures.backend(
                "primary", "http://my-svc.demo.svc:8080", "/"));
        Policy maintenance = ConversionSupportTestFixtures.policy("maintenance_mode", true, Map.of(
                "enabled", true,
                "status", 503,
                "message", "Under maintenance",
                "message_content_type", "text/plain"));
        service.policies.add(maintenance);
        ConversionContext ctx = ConversionSupportTestFixtures.context(service, "demo-ns");

        String readme = ReadmeSupport.build(
                ctx,
                new ReadmeNotes(),
                new PolicyFinder(),
                new PolicyConfigSupport(),
                RateLimitSupport.forManual());

        assertTrue(readme.contains("envoyfilter-maintenance.yaml"),
                "Files table must list envoyfilter-maintenance.yaml");
        assertTrue(readme.toLowerCase().contains("overlay")
                        || readme.toLowerCase().contains("precedence"),
                "README must note EnvoyFilter overlay / precedence");
        assertTrue(readme.toLowerCase().contains("directresponse")
                        || readme.toLowerCase().contains("direct response"),
                "README must note deferred DirectResponse");
    }

    @Test
    void build_maintenancePolicyWithoutConfigEnabled_omitsMaintenanceSection() {
        ApiService service = ConversionSupportTestFixtures.apiService("demo-api");
        service.backends.add(ConversionSupportTestFixtures.backend(
                "primary", "http://my-svc.demo.svc:8080", "/"));
        // Policy enabled at 3scale row level, but config.enabled missing → not active
        service.policies.add(ConversionSupportTestFixtures.policy(
                "maintenance_mode", true, new java.util.HashMap<>()));
        ConversionContext ctx = ConversionSupportTestFixtures.context(service, "demo-ns");

        String readme = ReadmeSupport.build(
                ctx,
                new ReadmeNotes(),
                new PolicyFinder(),
                new PolicyConfigSupport(),
                RateLimitSupport.forManual());

        assertFalse(readme.contains("envoyfilter-maintenance.yaml"));
        assertFalse(readme.contains("## Maintenance Mode"));
    }

    @Test
    void build_upstreamGaps_includesWarningSectionWithoutSyntheticSeClaim() {
        ApiService service = ConversionSupportTestFixtures.apiService("demo-api");
        service.backends.add(ConversionSupportTestFixtures.backend(
                "primary", "http://api.example.com:8080", "/"));
        service.policies.add(ConversionSupportTestFixtures.policy("upstream", true, Map.of(
                "rules", List.of(
                        Map.of("regex", "^/ok", "url", "https://ok.example.com"),
                        Map.of("regex", "^/api(?=!)", "url", "https://skip.example.com")))));
        ConversionContext ctx = ConversionSupportTestFixtures.context(service, "demo-ns");

        String readme = ReadmeSupport.build(
                ctx,
                new ReadmeNotes(),
                new PolicyFinder(),
                new PolicyConfigSupport(),
                RateLimitSupport.forManual());

        assertTrue(readme.contains("WARNING: Upstream conversion gaps"));
        assertTrue(readme.contains("ServiceEntry"));
        assertTrue(readme.contains("not auto-generated") || readme.contains("manual"),
                "README must say ServiceEntry is manual for upstream overrides");
        assertTrue(readme.toLowerCase().contains("scheme") || readme.contains("HTTP"));
    }

    @Test
    void build_upstreamHappyPath_listsUpstreamSection() {
        ApiService service = ConversionSupportTestFixtures.apiService("demo-api");
        service.backends.add(ConversionSupportTestFixtures.backend(
                "primary", "http://upstream-svc:8080", "/"));
        service.policies.add(ConversionSupportTestFixtures.upstreamPolicy(Map.of(
                "rules", List.of(
                        Map.of("regex", "^/v1", "url", "http://upstream-svc:8080")))));
        ConversionContext ctx = ConversionSupportTestFixtures.context(service, "demo-ns");

        String readme = ReadmeSupport.build(
                ctx,
                new ReadmeNotes(),
                new PolicyFinder(),
                new PolicyConfigSupport(),
                RateLimitSupport.forManual());

        assertTrue(readme.contains("## Upstream"));
        assertFalse(readme.contains("## WARNING: Upstream"));
    }

    @Test
    void build_routingJwtAndExternalOverride_includesWarningNotes() {
        ApiService service = ConversionSupportTestFixtures.apiService("demo-api");
        service.backends.add(ConversionSupportTestFixtures.backend(
                "primary", "http://api.example.com:8080", "/"));
        Map<String, Object> jwtOp = Map.of(
                "match", "jwt_claim",
                "jwt_claim_name", "role",
                "op", "==",
                "value", "admin");
        Map<String, Object> headerOp = Map.of(
                "match", "header",
                "header_name", "X-Route",
                "op", "==",
                "value", "yes");
        Map<String, Object> condition = Map.of(
                "combine_op", "and",
                "operations", List.of(jwtOp, headerOp));
        Map<String, Object> rule = Map.of(
                "url", "https://routing-override.example.com",
                "condition", condition);
        service.policies.add(ConversionSupportTestFixtures.policy(
                "routing", true, Map.of("rules", List.of(rule))));
        ConversionContext ctx = ConversionSupportTestFixtures.context(service, "demo-ns");

        String readme = ReadmeSupport.build(
                ctx,
                new ReadmeNotes(),
                new PolicyFinder(),
                new PolicyConfigSupport(),
                RateLimitSupport.forManual());

        assertTrue(readme.contains("WARNING: Routing conversion gaps"));
        assertTrue(readme.toLowerCase().contains("jwt"), "README must document jwt_claim gap");
        assertTrue(readme.contains("ServiceEntry"));
        assertTrue(readme.contains("routing-override.example.com")
                        || readme.contains("not auto-generated")
                        || readme.contains("manual"),
                "README must say ServiceEntry is manual for routing overrides");
    }
}
