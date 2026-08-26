package com.redhat.migrationtoolkit.rhcl.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Map;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * Starts WireMock for {@link ThreeScaleClient} integration tests and points the REST client at it.
 */
public class WireMockThreeScaleResource implements QuarkusTestResourceLifecycleManager {

  private static WireMockServer wireMockServer;

  @Override
  public Map<String, String> start() {
    wireMockServer = new WireMockServer(options().dynamicPort());
    wireMockServer.start();
    return Map.of("quarkus.rest-client.threescale-api.url", wireMockServer.baseUrl());
  }

  @Override
  public void stop() {
    if (wireMockServer != null) {
      wireMockServer.stop();
      wireMockServer = null;
    }
  }

  public static WireMockServer server() {
    return wireMockServer;
  }
}
