package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import com.redhat.migrationtoolkit.rhcl.dto.ConversionOptions;
import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Application;
import com.redhat.migrationtoolkit.rhcl.service.conversion.BackendResolver;
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
        populateAppIdKeySecret(builder, ctx.service);
    }

    static String generateAppIdKeySecret(String name, String namespace, ApiService service) {
        ApiService copy = service;
        if (service.systemName == null) {
            copy = new ApiService();
            copy.id = service.id;
            copy.name = service.name;
            copy.systemName = name;
            copy.authentication = service.authentication;
            copy.applications = service.applications;
        }
        ConversionContext ctx = ConversionContext.build(
                copy, namespace, null, new ConversionOptions(), new BackendResolver());
        SecretBuilder builder = new SecretBuilder(ctx);
        populateAppIdKeySecret(builder, copy);
        return builder.build();
    }

    private static void populateAppIdKeySecret(SecretBuilder builder, ApiService service) {
        List<Application> apps = service.applications != null ? service.applications : List.of();
        int index = 1;
        int pairs = 0;
        boolean hasAnyEntry = false;

        builder.beginOpaqueSecret(builder.name() + "-app-id-keys");
        builder.addLabel("auth-type", "app-id-key");
        builder.addLabel("authorino.kuadrant.io/managed-by", "authorino");

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
                builder.addStringData("app_id_" + index, appId);
                hasAnyEntry = true;
            }
            if (appKey != null) {
                builder.addStringData("app_key_" + index, appKey);
                pairs++;
                hasAnyEntry = true;
            } else if (appId != null) {
                LOG.warnf("App ID %s for service %s has no application keys from Admin API",
                        appId, service.id);
            }
            index++;
        }

        if (pairs == 0 && !hasAnyEntry) {
            builder.setYamlCommentPrefix(
                    "# WARNING: No App ID/App Key credentials fetched from 3scale Admin API — "
                            + "Secret left empty; do not invent keys\n");
            LOG.warnf("No App ID/App Key credentials for service %s; emitting empty Secret with warning",
                    service.id);
        } else if (pairs == 0) {
            builder.setYamlCommentPrefix(
                    "# WARNING: App IDs present but application keys missing from Admin API\n");
        }
    }
}
