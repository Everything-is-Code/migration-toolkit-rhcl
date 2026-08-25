package com.redhat.migrationtoolkit.rhcl.controller;

import com.redhat.migrationtoolkit.rhcl.entity.AppSettingsEntity;
import com.redhat.migrationtoolkit.rhcl.exception.NotFoundException;
import com.redhat.migrationtoolkit.rhcl.exception.ValidationException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

@Path("/api/settings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SettingsController {

    @GET
    @Path("/{key}")
    public Response get(@PathParam("key") String key) {
        AppSettingsEntity entity = AppSettingsEntity.findById(key);
        if (entity == null) {
            throw new NotFoundException("SETTINGS_NOT_FOUND", "Setting '" + key + "' not found");
        }
        return Response.ok(Map.of("key", entity.key, "value", entity.value)).build();
    }

    @PUT
    @Path("/{key}")
    @Transactional
    public Response put(@PathParam("key") String key, Map<String, String> body) {
        String value = body.get("value");
        if (value == null) {
            throw new ValidationException("value is required");
        }
        AppSettingsEntity entity = AppSettingsEntity.findById(key);
        if (entity == null) {
            entity = new AppSettingsEntity();
            entity.key = key;
        }
        entity.value = value;
        entity.persist();
        return Response.ok(Map.of("key", entity.key, "value", entity.value)).build();
    }
}
