package com.redhat.migrationtoolkit.rhcl.client;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import java.util.List;
import java.util.Map;

@RegisterRestClient(configKey = "threescale-api")
@Produces(MediaType.APPLICATION_JSON)
public interface ThreeScaleClient {

    @GET
    @Path("/admin/api/services.json")
    Map<String, Object> getServices(@QueryParam("access_token") String accessToken,
                                     @QueryParam("page") @DefaultValue("1") int page,
                                     @QueryParam("per_page") @DefaultValue("500") int perPage);

    @GET
    @Path("/admin/api/services/{serviceId}.json")
    Map<String, Object> getService(@PathParam("serviceId") String serviceId,
                                    @QueryParam("access_token") String accessToken);

    @GET
    @Path("/admin/api/backends.json")
    Map<String, Object> getBackends(@QueryParam("access_token") String accessToken,
                                     @QueryParam("page") @DefaultValue("1") int page,
                                     @QueryParam("per_page") @DefaultValue("500") int perPage);

    @GET
    @Path("/admin/api/services/{serviceId}/backend_usages.json")
    List<Map<String, Object>> getBackendUsages(@PathParam("serviceId") String serviceId,
                                                @QueryParam("access_token") String accessToken);

    @GET
    @Path("/admin/api/backend_apis/{backendId}.json")
    Map<String, Object> getBackend(@PathParam("backendId") String backendId,
                                    @QueryParam("access_token") String accessToken);

    @GET
    @Path("/admin/api/services/{serviceId}/proxy/configs/production/latest.json")
    Map<String, Object> getProxyConfig(@PathParam("serviceId") String serviceId,
                                        @QueryParam("access_token") String accessToken);

    @GET
    @Path("/admin/api/services/{serviceId}/proxy/policies.json")
    Map<String, Object> getPolicies(@PathParam("serviceId") String serviceId,
                                     @QueryParam("access_token") String accessToken);

    @GET
    @Path("/admin/api/services/{serviceId}/proxy/mapping_rules.json")
    Map<String, Object> getMappingRules(@PathParam("serviceId") String serviceId,
                                         @QueryParam("access_token") String accessToken);

    @GET
    @Path("/admin/api/services/{serviceId}/metrics.json")
    Map<String, Object> getMetrics(@PathParam("serviceId") String serviceId,
                                    @QueryParam("access_token") String accessToken);

    @GET
    @Path("/admin/api/services/{serviceId}/applications.json")
    Map<String, Object> getApplications(@PathParam("serviceId") String serviceId,
                                         @QueryParam("access_token") String accessToken,
                                         @QueryParam("page") @DefaultValue("1") int page,
                                         @QueryParam("per_page") @DefaultValue("500") int perPage);

    @GET
    @Path("/admin/api/applications/{applicationId}/keys.json")
    Map<String, Object> getApplicationKeys(@PathParam("applicationId") String applicationId,
                                            @QueryParam("access_token") String accessToken);

    @GET
    @Path("/admin/api/services/{serviceId}/application_plans.json")
    Map<String, Object> getApplicationPlans(@PathParam("serviceId") String serviceId,
                                             @QueryParam("access_token") String accessToken);
}
