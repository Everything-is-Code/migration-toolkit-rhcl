package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.Policy;
import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.IstioManifestSupport;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import io.fabric8.istio.api.telemetry.v1.Telemetry;
import io.fabric8.istio.api.telemetry.v1.TelemetryBuilder;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;

@ApplicationScoped
@Priority(1000)
public class TelemetryGenerator implements ResourceGenerator {

    @Inject
    PolicyFinder policyFinder;

    @Inject
    ManifestSerializer manifestSerializer;

    @Override
    public String outputKey() {
        return "telemetry.yaml";
    }

    @Override
    public boolean applies(ConversionContext ctx) {
        return policyFinder.findEnabledExact(ctx.service, "logging") != null;
    }

    @Override
    public String generate(ConversionContext ctx) {
        Policy loggingPolicy = policyFinder.findEnabledExact(ctx.service, "logging");
        boolean isGateway = !"workload".equals(ctx.loggingTarget);
        String name = ctx.serviceKebabName;
        String namespace = ctx.namespace;
        Map<String, Object> cfg = loggingPolicy.configuration != null ? loggingPolicy.configuration : Map.of();
        boolean enableJson = Boolean.TRUE.equals(cfg.get("enable_json_logs"));
        boolean enableAccess = !Boolean.FALSE.equals(cfg.get("enable_access_logs"));

        Telemetry telemetry = new TelemetryBuilder()
                .withApiVersion("telemetry.istio.io/v1")
                .withKind("Telemetry")
                .withNewMetadata()
                .withName(name + "-logging")
                .withNamespace(namespace)
                .withLabels(IstioManifestSupport.baseLabels(name, ctx.includeMigratedFromLabel))
                .addToAnnotations("3scale-migration/source", "logging")
                .addToAnnotations("3scale-migration/enable-json", String.valueOf(enableJson))
                .addToAnnotations("3scale-migration/enable-access", String.valueOf(enableAccess))
                .endMetadata()
                .withNewSpec()
                .withNewSelector()
                .withMatchLabels(IstioManifestSupport.loggingWorkloadLabels(name, isGateway))
                .endSelector()
                .addNewAccessLogging()
                .addNewProvider()
                .withName("envoy")
                .endProvider()
                .endAccessLogging()
                .endSpec()
                .build();

        return serializer().toYaml(telemetry);
    }

    void bindManual(PolicyFinder finder) {
        this.policyFinder = finder;
    }

    private ManifestSerializer serializer() {
        return IstioManifestSupport.resolveSerializer(manifestSerializer);
    }
}
