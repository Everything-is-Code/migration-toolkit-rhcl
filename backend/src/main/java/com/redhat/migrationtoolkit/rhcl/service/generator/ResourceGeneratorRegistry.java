package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.service.conversion.ContributorOrdering;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ResourceGeneratorRegistry {

    private static final Logger LOG = Logger.getLogger(ResourceGeneratorRegistry.class);

    private final Instance<ResourceGenerator> cdiGenerators;
    private final List<ResourceGenerator> manualGenerators;

    @Inject
    public ResourceGeneratorRegistry(Instance<ResourceGenerator> generators) {
        this.cdiGenerators = generators;
        this.manualGenerators = null;
    }

  /** Manual wiring for unit tests without CDI ({@code new ConversionService()}). */
    ResourceGeneratorRegistry(List<ResourceGenerator> manualGenerators) {
        this.cdiGenerators = null;
        this.manualGenerators = manualGenerators;
    }

    public static ResourceGeneratorRegistry manual() {
        return new ResourceGeneratorRegistry(ManualResourceGeneratorFactory.create());
    }

    public Map<String, String> generateAll(ConversionContext ctx) {
        List<ResourceGenerator> sorted = new ArrayList<>(activeGenerators());
        sorted.sort(Comparator.comparingInt(ContributorOrdering::priorityOf));

        Map<String, String> files = new LinkedHashMap<>();
        for (ResourceGenerator generator : sorted) {
            if (!generator.applies(ctx)) {
                continue;
            }
            String content = generator.generate(ctx);
            if (content == null || content.isBlank()) {
                continue;
            }
            String key = generator.outputKey();
            String existing = files.get(key);
            if (existing != null) {
                LOG.warnf("Duplicate outputKey '%s' from %s — concatenating with YAML doc separator",
                        key, generator.getClass().getSimpleName());
                files.put(key, existing + "---\n" + content);
            } else {
                files.put(key, content);
            }
        }
        return files;
    }

    private List<ResourceGenerator> activeGenerators() {
        if (manualGenerators != null) {
            return manualGenerators;
        }
        List<ResourceGenerator> generators = new ArrayList<>();
        for (ResourceGenerator generator : cdiGenerators) {
            generators.add(generator);
        }
        return generators;
    }

    private static int priorityOf(ResourceGenerator generator) {
        return ContributorOrdering.priorityOf(generator);
    }
}
