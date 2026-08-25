package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.util.ConversionConstants;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@Priority(900)
public class DefaultCredentialsSecretContributor implements SecretContributor {

    @Override
    public void contribute(SecretBuilder builder, ConversionContext ctx) {
        if (builder.hasSecret()) {
            return;
        }
        String name = builder.name();
        String namespace = builder.namespace();
        builder.setSecretYaml("""
apiVersion: v1
kind: Secret
metadata:
  name: %s-credentials
  namespace: %s
  labels:
    app: %s
    migrated-from: 3scale
type: Opaque
stringData:
  client-id: "%s"
  client-secret: "%s"
""".formatted(name, namespace, name,
                ConversionConstants.CREDENTIAL_PLACEHOLDER,
                ConversionConstants.CREDENTIAL_PLACEHOLDER));
    }
}
