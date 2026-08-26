package com.redhat.migrationtoolkit.rhcl.client;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@QuarkusTestResource(value = WireMockThreeScaleResource.class, restrictToAnnotatedClass = true)
class ThreeScaleClientTest {

  private static final String ACCESS_TOKEN = "test-token";

  @Inject
  @RestClient
  ThreeScaleClient client;

  @BeforeEach
  void resetStubs() {
    WireMockThreeScaleResource.server().resetAll();
  }

  @Test
  void getServices_returnsDeserializedList() {
    WireMockThreeScaleResource.server().stubFor(
        get(urlPathEqualTo("/admin/api/services.json"))
            .withQueryParam("access_token", equalTo(ACCESS_TOKEN))
            .withQueryParam("page", equalTo("1"))
            .withQueryParam("per_page", equalTo("500"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"services":[{"id":1,"system_name":"echo-api"}]}
                    """)));

    Map<String, Object> result = client.getServices(ACCESS_TOKEN, 1, 500);
    assertNotNull(result.get("services"));
    @SuppressWarnings("unchecked")
    var services = (List<Map<String, Object>>) result.get("services");
    assertEquals(1, services.size());
    assertEquals("echo-api", services.get(0).get("system_name"));
  }

  @Test
  void getService_returnsDeserializedService() {
    WireMockThreeScaleResource.server().stubFor(
        get(urlPathEqualTo("/admin/api/services/42.json"))
            .withQueryParam("access_token", equalTo(ACCESS_TOKEN))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"service":{"id":42,"system_name":"echo-api"}}
                    """)));

    Map<String, Object> result = client.getService("42", ACCESS_TOKEN);
    @SuppressWarnings("unchecked")
    var service = (Map<String, Object>) result.get("service");
    assertEquals(42, service.get("id"));
    assertEquals("echo-api", service.get("system_name"));
  }

  @Test
  void getServices_unauthorized_returnsWebApplicationException() {
    WireMockThreeScaleResource.server().stubFor(
        get(urlPathEqualTo("/admin/api/services.json"))
            .willReturn(aResponse().withStatus(401).withBody("Unauthorized")));

    var ex = org.junit.jupiter.api.Assertions.assertThrows(
        WebApplicationException.class, () -> client.getServices(ACCESS_TOKEN, 1, 500));
    assertEquals(401, ex.getResponse().getStatus());
  }

  @Test
  void getService_notFound_returnsWebApplicationException() {
    WireMockThreeScaleResource.server().stubFor(
        get(urlPathEqualTo("/admin/api/services/99.json"))
            .willReturn(aResponse().withStatus(404).withBody("Not found")));

    var ex = org.junit.jupiter.api.Assertions.assertThrows(
        WebApplicationException.class, () -> client.getService("99", ACCESS_TOKEN));
    assertEquals(404, ex.getResponse().getStatus());
  }

  @Test
  void getApplicationPlans_serverError_returnsWebApplicationException() {
    WireMockThreeScaleResource.server().stubFor(
        get(urlPathEqualTo("/admin/api/services/42/application_plans.json"))
            .withQueryParam("access_token", equalTo(ACCESS_TOKEN))
            .withQueryParam("page", equalTo("1"))
            .withQueryParam("per_page", equalTo("500"))
            .willReturn(aResponse().withStatus(500).withBody("Internal error")));

    var ex = org.junit.jupiter.api.Assertions.assertThrows(
        WebApplicationException.class, () -> client.getApplicationPlans("42", ACCESS_TOKEN, 1, 500));
    assertEquals(500, ex.getResponse().getStatus());
  }

  @Test
  void getApplicationPlans_returnsDeserializedPlans() {
    WireMockThreeScaleResource.server().stubFor(
        get(urlPathEqualTo("/admin/api/services/42/application_plans.json"))
            .withQueryParam("access_token", equalTo(ACCESS_TOKEN))
            .withQueryParam("page", equalTo("1"))
            .withQueryParam("per_page", equalTo("500"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"application_plans":[{"id":7,"name":"default"}]}
                    """)));

    Map<String, Object> result = client.getApplicationPlans("42", ACCESS_TOKEN, 1, 500);
    @SuppressWarnings("unchecked")
    var plans = (List<Map<String, Object>>) result.get("application_plans");
    assertEquals(1, plans.size());
    assertEquals("default", plans.get(0).get("name"));
  }

  @Test
  void getBackendUsages_returnsDeserializedList() {
    WireMockThreeScaleResource.server().stubFor(
        get(urlPathEqualTo("/admin/api/services/42/backend_usages.json"))
            .withQueryParam("access_token", equalTo(ACCESS_TOKEN))
            .withQueryParam("page", equalTo("1"))
            .withQueryParam("per_page", equalTo("500"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    [{"backend_id":10,"path":"/api"}]
                    """)));

    List<Map<String, Object>> usages = client.getBackendUsages("42", ACCESS_TOKEN, 1, 500);
    assertEquals(1, usages.size());
    assertEquals("/api", usages.get(0).get("path"));
    assertInstanceOf(Integer.class, usages.get(0).get("backend_id"));
  }
}
