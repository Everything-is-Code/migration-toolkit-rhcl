package com.redhat.migrationtoolkit.rhcl.controller;

import com.redhat.migrationtoolkit.rhcl.entity.AppSettingsEntity;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
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
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(Map.of("key", entity.key, "value", entity.value)).build();
    }

    @PUT
    @Path("/{key}")
    @Transactional
    public Response put(@PathParam("key") String key, Map<String, String> body) {
        String value = body.get("value");
        if (value == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("message", "value is required")).build();
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
