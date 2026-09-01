package com.redhat.migrationtoolkit.rhcl.service.generator.contributor;

import jakarta.annotation.Priority;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Regression guard for @Priority ordering (#262 task 4.6). */
class HttpRouteContributorOrderTest {

  private static final List<Class<?>> EXPECTED_ORDER = List.of(
      HttpRouteAnnotationsContributor.class,
      HeaderModContributor.class,
      CorsFiltersContributor.class,
      TimeoutsContributor.class,
      RetryContributor.class,
      RoutingContributor.class,
      UpstreamContributor.class,
      MappingRulesContributor.class,
      CorsOptionsContributor.class);

  @Test
  void contributorPriorities_matchPhase4Baseline() {
    List<Integer> priorities = EXPECTED_ORDER.stream()
        .map(HttpRouteContributorOrderTest::priorityOf)
        .toList();

    List<Integer> sorted = priorities.stream().sorted().toList();
    assertEquals(sorted, priorities, "Priorities must be strictly increasing: " + priorities);
    assertEquals(100, priorityOf(HttpRouteAnnotationsContributor.class));
    assertEquals(500, priorityOf(CorsOptionsContributor.class));
  }

  private static int priorityOf(Class<?> type) {
    Priority annotation = type.getAnnotation(Priority.class);
    if (annotation == null) {
      throw new IllegalStateException("Missing @Priority on " + type.getSimpleName());
    }
    return annotation.value();
  }
}
