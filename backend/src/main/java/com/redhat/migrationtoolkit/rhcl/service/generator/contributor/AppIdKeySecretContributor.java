package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Application;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
@Priority(300)
public class AppIdKeySecretContributor implements SecretContributor {

    private static final Logger LOG = Logger.getLogger(AppIdKeySecretContributor.class);

    @Override
    public void contribute(SecretBuilder builder, ConversionContext ctx) {
        if (builder.hasSecret()) {
            return;
        }
        String authType = ctx.service.authentication != null ? ctx.service.authentication.type : "none";
        if (!"appIdKey".equals(authType)) {
            return;
        }
        builder.setSecretYaml(generateAppIdKeySecret(builder.name(), builder.namespace(), ctx.service));
    }

    static String generateAppIdKeySecret(String name, String namespace, ApiService service) {
        List<Application> apps = service.applications != null ? service.applications : List.of();
        StringBuilder stringData = new StringBuilder();
        String warning;
        int index = 1;
        int pairs = 0;
        for (Application app : apps) {
            String appId = app.appId != null && !app.appId.isBlank() ? app.appId : null;
            String appKey = null;
            if (app.keys != null) {
                for (String k : app.keys) {
                    if (k != null && !k.isBlank()) {
                        appKey = k;
                        break;
                    }
                }
            }
            if (appId == null && appKey == null) {
                continue;
            }
            if (appId != null) {
                stringData.append(String.format("  app_id_%d: \"%s\"%n", index, appId));
            }
            if (appKey != null) {
                stringData.append(String.format("  app_key_%d: \"%s\"%n", index, appKey));
                pairs++;
            } else {
                LOG.warnf("App ID %s for service %s has no application keys from Admin API",
                        appId, service.id);
            }
            index++;
        }

        if (pairs == 0 && stringData.length() == 0) {
            warning = "# WARNING: No App ID/App Key credentials fetched from 3scale Admin API — "
                    + "Secret left empty; do not invent keys\n";
            LOG.warnf("No App ID/App Key credentials for service %s; emitting empty Secret with warning",
                    service.id);
            return """
apiVersion: v1
kind: Secret
metadata:
  name: %s-app-id-keys
  namespace: %s
  labels:
    app: %s
    auth-type: app-id-key
    migrated-from: 3scale
    authorino.kuadrant.io/managed-by: authorino
type: Opaque
%sstringData: {}
""".formatted(name, namespace, name, warning);
        } else if (pairs == 0) {
            warning = "# WARNING: App IDs present but application keys missing from Admin API\n";
        } else {
            warning = "";
        }

        return """
apiVersion: v1
kind: Secret
metadata:
  name: %s-app-id-keys
  namespace: %s
  labels:
    app: %s
    auth-type: app-id-key
    migrated-from: 3scale
    authorino.kuadrant.io/managed-by: authorino
type: Opaque
%sstringData:
%s""".formatted(name, namespace, name, warning, stringData);
    }
}
