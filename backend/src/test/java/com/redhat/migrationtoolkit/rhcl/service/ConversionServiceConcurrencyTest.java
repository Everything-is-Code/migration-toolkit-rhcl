package com.redhat.migrationtoolkit.rhcl.service;

import com.redhat.migrationtoolkit.rhcl.model.ApiService;
import com.redhat.migrationtoolkit.rhcl.model.Authentication;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ConversionServiceConcurrencyTest {

    @Inject
    ConversionService conversionService;

    @Test
    void parallelConversions_doNotShareMutableState() throws Exception {
        List<Callable<Map<String, String>>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            final int index = i;
            tasks.add(() -> {
                ApiService svc = new ApiService();
                svc.id = "svc-" + index;
                svc.name = "API " + index;
                svc.systemName = "api-" + index;
                svc.authentication = new Authentication();
                svc.authentication.type = index % 2 == 0 ? "jwt" : "apiKey";
                return conversionService.convert(svc, "ns-" + index);
            });
        }

        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<Map<String, String>>> futures = executor.invokeAll(tasks);
            for (int i = 0; i < futures.size(); i++) {
                Map<String, String> files = futures.get(i).get();
                assertNotNull(files);
                assertTrue(files.containsKey("gateway.yaml"));
                String expectedPrefix = "api-" + i;
                assertTrue(files.get("gateway.yaml").contains(expectedPrefix + "-gateway"),
                        "gateway for index " + i);
                if (i % 2 == 0) {
                    assertTrue(!files.containsKey("apikey.yaml"),
                            "jwt service must not emit apikey.yaml");
                } else {
                    assertTrue(files.containsKey("apikey.yaml"),
                            "apiKey service must emit apikey.yaml");
                }
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(8, tasks.size());
    }
}
