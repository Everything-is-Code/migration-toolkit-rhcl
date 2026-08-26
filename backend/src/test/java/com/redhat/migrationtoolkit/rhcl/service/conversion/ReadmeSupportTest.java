package com.redhat.migrationtoolkit.rhcl.service.conversion;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import org.junit.jupiter.api.Test;

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
}
