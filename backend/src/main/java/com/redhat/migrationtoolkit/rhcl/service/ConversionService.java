package com.redhat.migrationtoolkit.rhcl.service;

import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.service.conversion.BackendResolver;
import com.redhat.migrationtoolkit.rhcl.service.conversion.BackendType;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionYamlSupport;
import com.redhat.migrationtoolkit.rhcl.service.conversion.HttpRouteSupport;
import com.redhat.migrationtoolkit.rhcl.service.conversion.PolicyConfigSupport;
import com.redhat.migrationtoolkit.rhcl.service.conversion.RateLimitSupport;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ResolvedBackend;
import com.redhat.migrationtoolkit.rhcl.service.generator.ResourceGeneratorRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ConversionService {

    private static final Logger LOG = Logger.getLogger(ConversionService.class);

    @Inject
    PolicyFinder injectedPolicyFinder;

    @Inject
    BackendResolver injectedBackendResolver;

    @Inject
    PolicyConfigSupport injectedPolicyConfigSupport;

    @Inject
    ConversionYamlSupport injectedConversionYamlSupport;

    @Inject
    ResourceGeneratorRegistry injectedResourceGeneratorRegistry;

    @Inject
    RateLimitSupport injectedRateLimitSupport;

    private volatile ResourceGeneratorRegistry manualResourceGeneratorRegistry;

    PolicyFinder policyFinder() {
        return injectedPolicyFinder != null ? injectedPolicyFinder : new PolicyFinder();
    }

    BackendResolver backendResolver() {
        return injectedBackendResolver != null ? injectedBackendResolver : new BackendResolver();
    }

    PolicyConfigSupport policyConfigSupport() {
        return injectedPolicyConfigSupport != null ? injectedPolicyConfigSupport : new PolicyConfigSupport();
    }

    ConversionYamlSupport yamlSupport() {
        return injectedConversionYamlSupport != null ? injectedConversionYamlSupport : new ConversionYamlSupport();
    }

    ResourceGeneratorRegistry resourceGeneratorRegistry() {
        if (injectedResourceGeneratorRegistry != null) {
            return injectedResourceGeneratorRegistry;
        }
        if (manualResourceGeneratorRegistry == null) {
            manualResourceGeneratorRegistry = ResourceGeneratorRegistry.manual();
        }
        return manualResourceGeneratorRegistry;
    }

    RateLimitSupport rateLimitSupport() {
        return injectedRateLimitSupport != null ? injectedRateLimitSupport : RateLimitSupport.forManual();
    }


    public Map<String, String> convert(ApiService service, String namespace) {
        return convert(service, namespace, null, new ConversionOptions());
    }

    public Map<String, String> convert(ApiService service, String namespace, String backendUrl) {
        return convert(service, namespace, backendUrl, new ConversionOptions());
    }

    public Map<String, String> convert(ApiService service, String namespace,
            String backendUrl, String loggingTarget) {
        ConversionOptions opts = new ConversionOptions();
        opts.loggingTarget = loggingTarget;
        return convert(service, namespace, backendUrl, opts);
    }

    public Map<String, String> convert(ApiService service, String namespace,
            String backendUrl, String loggingTarget, String anonymousTarget) {
        ConversionOptions opts = new ConversionOptions();
        opts.loggingTarget = loggingTarget;
        opts.anonymousTarget = anonymousTarget;
        return convert(service, namespace, backendUrl, opts);
    }

    public Map<String, String> convert(ApiService service, String namespace,
            String backendUrl, String loggingTarget, String anonymousTarget,
            boolean includeMigratedFromLabel) {
        ConversionOptions opts = new ConversionOptions();
        opts.loggingTarget = loggingTarget;
        opts.anonymousTarget = anonymousTarget;
        opts.includeMigratedFromLabel = includeMigratedFromLabel;
        return convert(service, namespace, backendUrl, opts);
    }

    public Map<String, String> convert(ApiService service, String namespace,
            String backendUrl, ConversionOptions opts) {
        ConversionContext ctx = ConversionContext.build(
                service, namespace, backendUrl, opts, backendResolver());

        Map<String, String> files = new LinkedHashMap<>(resourceGeneratorRegistry().generateAll(ctx));

        if (ctx.options.includeDnsPolicy && !ctx.emitDnsPolicy()) {
            LOG.warnf(
                    "includeDnsPolicy set but dnsHostname blank/null; skipping dnspolicy.yaml and Gateway hostname");
        }

        if (!ctx.includeMigratedFromLabel) {
            files.replaceAll((fileName, content) -> yamlSupport().stripMigratedFromLabel(content));
        }
        files.replaceAll((fileName, content) -> ConversionYamlSupport.normalizeLineEndings(content));
        return files;
    }

    /** Delegates for unit tests without CDI. */
    BackendType detectBackendType(String url) {
        return backendResolver().detectBackendType(url);
    }

    List<ResolvedBackend> resolveBackends(ApiService service, String productName,
            String backendUrl, boolean overrideIgnored) {
        return backendResolver().resolveBackends(service, productName, backendUrl, overrideIgnored);
    }

    static String normalizeMountPath(String path) {
        return BackendResolver.normalizeMountPath(path);
    }

    static boolean emitDnsPolicy(ConversionOptions opts) {
        return ConversionContext.emitDnsPolicy(opts);
    }

    static String normalizeYamlLineEndings(String content) {
        return ConversionYamlSupport.normalizeLineEndings(content);
    }

    static String yamlDoubleQuoted(String value) {
        return HttpRouteSupport.yamlDoubleQuoted(value);
    }
}
